-- ============================================================
-- 学院学生综合服务与党团管理平台 - 数据库建表脚本
-- 兼容 PostgreSQL / Kingbase V8
-- ============================================================

-- 向量检索扩展：PostgreSQL 部署请使用 pgvector 镜像；Kingbase 环境如不支持可跳过 RAG 表。
CREATE EXTENSION IF NOT EXISTS vector;

-- ==================== 系统管理 ====================

-- 用户表
CREATE TABLE sys_user (
    id           BIGSERIAL PRIMARY KEY,
    student_id   VARCHAR(20) UNIQUE NOT NULL,
    name         VARCHAR(50) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    role_level   SMALLINT NOT NULL DEFAULT 4,
    grade        VARCHAR(10),
    major        VARCHAR(50),
    class_name   VARCHAR(50),
    phone        VARCHAR(20),
    email        VARCHAR(100),
    id_card_enc  VARCHAR(255),
    origin_enc   VARCHAR(255),
    hukou_enc    VARCHAR(255),
    tutor        VARCHAR(50),
    status       SMALLINT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.student_id IS '学号（登录凭证，唯一）';
COMMENT ON COLUMN sys_user.email IS '邮箱（可选，未填时默认为 学号@ruc.edu.cn）';
COMMENT ON COLUMN sys_user.role_level IS '角色等级: 1=院领导, 2=管理老师/辅导员, 3=班团骨干, 4=普通学生';
COMMENT ON COLUMN sys_user.id_card_enc IS '身份证号（AES加密存储）';
COMMENT ON COLUMN sys_user.origin_enc IS '生源地（AES加密存储）';
COMMENT ON COLUMN sys_user.hukou_enc IS '户籍地（AES加密存储）';
COMMENT ON COLUMN sys_user.status IS '状态: 1=启用, 0=禁用';

CREATE INDEX idx_user_role ON sys_user(role_level);
CREATE INDEX idx_user_grade ON sys_user(grade);
CREATE INDEX idx_user_class ON sys_user(class_name);

-- 操作日志表
CREATE TABLE sys_operation_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    user_name    VARCHAR(50),
    module       VARCHAR(50) NOT NULL,
    action       VARCHAR(200) NOT NULL,
    detail       TEXT,
    ip           VARCHAR(50),
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sys_operation_log IS '管理员操作日志（审计溯源）';
CREATE INDEX idx_log_user ON sys_operation_log(user_id);
CREATE INDEX idx_log_time ON sys_operation_log(created_at);

-- 消息通知表（含模拟短信、群发标签、来源信息）
CREATE TABLE sys_notification (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    title         VARCHAR(200),
    content       TEXT,
    type          VARCHAR(20) NOT NULL DEFAULT 'system',
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    tags          VARCHAR(200),
    source        VARCHAR(50),
    source_url    VARCHAR(500),
    broadcast_id  BIGINT,
    created_at    TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sys_notification IS '系统通知与模拟短信';
COMMENT ON COLUMN sys_notification.type IS '类型: sms_sim=模拟短信, system=系统通知, reminder=流程提醒, email_sim=已发邮件(模拟)';
COMMENT ON COLUMN sys_notification.tags IS '标签（逗号分隔，如 "就业,实习,计算机类"）';
COMMENT ON COLUMN sys_notification.source IS '来源（如 后勤处 / 保卫处 / 就业办 / 学院）';
COMMENT ON COLUMN sys_notification.source_url IS '来源原文链接（公众号文章地址等）';
COMMENT ON COLUMN sys_notification.broadcast_id IS '所属广播任务ID（NULL=单条直发）';
CREATE INDEX idx_notify_user ON sys_notification(user_id, is_read);
CREATE INDEX idx_notify_broadcast ON sys_notification(broadcast_id);
CREATE INDEX idx_notify_tags ON sys_notification(tags);

-- 群发任务表
CREATE TABLE sys_notification_broadcast (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       TEXT NOT NULL,
    tags          VARCHAR(200),
    source        VARCHAR(50),
    source_url    VARCHAR(500),
    target_filter TEXT,
    channels      VARCHAR(100) NOT NULL DEFAULT 'system',
    target_count  INT NOT NULL DEFAULT 0,
    sent_count    INT NOT NULL DEFAULT 0,
    email_sent    INT NOT NULL DEFAULT 0,
    operator_id   BIGINT,
    withdrawn     BOOLEAN NOT NULL DEFAULT FALSE,
    withdrawn_at  TIMESTAMP,
    created_at    TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sys_notification_broadcast IS '管理员群发任务，支持 24h 内撤回';
COMMENT ON COLUMN sys_notification_broadcast.target_filter IS '群发筛选条件 JSON（grade/major/class/role 等）';
COMMENT ON COLUMN sys_notification_broadcast.channels IS '发送渠道，逗号分隔: system/email/sms_sim';
COMMENT ON COLUMN sys_notification_broadcast.email_sent IS '实际成功发送邮件的数量';
CREATE INDEX idx_broadcast_operator ON sys_notification_broadcast(operator_id);
CREATE INDEX idx_broadcast_created ON sys_notification_broadcast(created_at);

-- ==================== 智能问答与知识库 (P0) ====================

-- 知识条目表
CREATE TABLE qa_knowledge (
    id           BIGSERIAL PRIMARY KEY,
    category     VARCHAR(50),
    question     TEXT NOT NULL,
    answer       TEXT NOT NULL,
    keywords     VARCHAR(500),
    source_url   VARCHAR(500),
    sort_order   INT DEFAULT 0,
    status       SMALLINT NOT NULL DEFAULT 1,
    created_by   BIGINT,
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE qa_knowledge IS '知识库标准问答条目';
COMMENT ON COLUMN qa_knowledge.keywords IS '关键词（逗号分隔，用于检索匹配）';
COMMENT ON COLUMN qa_knowledge.source_url IS '官方政策链接';
CREATE INDEX idx_qa_category ON qa_knowledge(category);
CREATE INDEX idx_qa_keywords ON qa_knowledge(keywords);

-- 政策文档 / 办公模板表（doc_type 区分）
CREATE TABLE qa_document (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50),
    doc_type       VARCHAR(20) NOT NULL DEFAULT 'policy',
    file_path      VARCHAR(500) NOT NULL DEFAULT '',
    file_size      BIGINT,
    file_type      VARCHAR(100),
    download_count INT NOT NULL DEFAULT 0,
    status         SMALLINT NOT NULL DEFAULT 1,
    created_by     BIGINT,
    description    VARCHAR(500),
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE qa_document IS '政策文档与办公模板（≤30MB下载）';
COMMENT ON COLUMN qa_document.updated_at IS '最近更新时间（替换文件、修改元信息时刷新）';
COMMENT ON COLUMN qa_document.doc_type IS '文档类型: policy=政策文件, template=办公模板';
COMMENT ON COLUMN qa_document.file_path IS '文件相对路径; 空字符串表示模板占位记录, 待管理员补传';
COMMENT ON COLUMN qa_document.description IS '简要描述（适用范围 / 填写说明）';
CREATE INDEX idx_doc_category ON qa_document(category);
CREATE INDEX idx_doc_type ON qa_document(doc_type, status);

-- 问答记录表
CREATE TABLE qa_chat_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    question     TEXT NOT NULL,
    answer       TEXT,
    source_type  VARCHAR(20),
    matched      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE qa_chat_log IS '用户问答记录（用于优化知识库）';
COMMENT ON COLUMN qa_chat_log.source_type IS '回答来源: knowledge=标准答案, ai=AI生成, manual=人工';
CREATE INDEX idx_chat_user ON qa_chat_log(user_id);
CREATE INDEX idx_chat_matched ON qa_chat_log(matched);

-- 政策文档向量切片表（RAG）
CREATE TABLE qa_document_chunk (
    id             BIGSERIAL PRIMARY KEY,
    document_id    BIGINT NOT NULL REFERENCES qa_document(id) ON DELETE CASCADE,
    title          VARCHAR(200),
    category       VARCHAR(50),
    chunk_index    INT NOT NULL,
    content        TEXT NOT NULL,
    keywords       VARCHAR(500),
    embedding      vector(512) NOT NULL,
    created_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE qa_document_chunk IS '政策文档文本切片与向量索引（RAG检索）';
CREATE INDEX idx_chunk_document ON qa_document_chunk(document_id);
CREATE INDEX idx_chunk_category ON qa_document_chunk(category);
CREATE INDEX idx_chunk_embedding ON qa_document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ==================== 党团事务流程 (P0) ====================

-- 流程模板表
CREATE TABLE party_process_template (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    total_steps  INT NOT NULL,
    status       SMALLINT NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE party_process_template IS '党团流程模板（如入党流程）';

-- 流程步骤定义
CREATE TABLE party_process_step (
    id                 BIGSERIAL PRIMARY KEY,
    template_id        BIGINT NOT NULL REFERENCES party_process_template(id),
    step_order         INT NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        TEXT,
    duration_days      INT,
    required_materials TEXT,
    created_at         TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE party_process_step IS '流程步骤定义';
COMMENT ON COLUMN party_process_step.duration_days IS '预计时长（天），用于时间节点提醒';
CREATE INDEX idx_step_template ON party_process_step(template_id, step_order);

-- 学生流程实例
CREATE TABLE party_process_instance (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES sys_user(id),
    template_id  BIGINT NOT NULL REFERENCES party_process_template(id),
    current_step INT NOT NULL DEFAULT 1,
    start_date   DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE party_process_instance IS '学生流程实例';
COMMENT ON COLUMN party_process_instance.status IS '状态: active/completed/suspended';
CREATE INDEX idx_instance_user ON party_process_instance(user_id);
CREATE INDEX idx_instance_template ON party_process_instance(template_id);

-- 步骤完成记录
CREATE TABLE party_step_record (
    id           BIGSERIAL PRIMARY KEY,
    instance_id  BIGINT NOT NULL REFERENCES party_process_instance(id),
    step_id      BIGINT NOT NULL REFERENCES party_process_step(id),
    completed_at TIMESTAMP,
    remark       TEXT,
    operator_id  BIGINT
);

COMMENT ON TABLE party_step_record IS '步骤完成记录';
CREATE INDEX idx_record_instance ON party_step_record(instance_id);

-- ==================== 电子证明与审批 (P1) ====================

-- 审批类型定义
CREATE TABLE approval_type (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    approval_chain  VARCHAR(50) NOT NULL,
    template_path   VARCHAR(500),
    status          SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE approval_type IS '审批类型定义';
COMMENT ON COLUMN approval_type.approval_chain IS '审批链（角色等级，逗号分隔，如 "2,1"）';

-- 审批申请表 ★核心表
CREATE TABLE approval_application (
    id                      BIGSERIAL PRIMARY KEY,
    app_no                  VARCHAR(50) UNIQUE NOT NULL,
    user_id                 BIGINT NOT NULL REFERENCES sys_user(id),
    type_id                 BIGINT REFERENCES approval_type(id),
    template_doc_id         BIGINT REFERENCES qa_document(id),
    form_data               JSONB,
    status                  VARCHAR(20) NOT NULL DEFAULT 'draft',
    current_approver_level  SMALLINT,
    cert_file_path          VARCHAR(500),
    downloaded_at           TIMESTAMP,
    withdraw_deadline       TIMESTAMP,
    created_at              TIMESTAMP DEFAULT NOW(),
    updated_at              TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE approval_application IS '审批申请表';
COMMENT ON COLUMN approval_application.status IS '状态: draft/pending/approved/rejected/withdrawn/downloaded';
COMMENT ON COLUMN approval_application.downloaded_at IS '下载时间（非空=已锁定，严禁撤回）';
COMMENT ON COLUMN approval_application.withdraw_deadline IS '撤回截止时间（通过后1-2天）';
COMMENT ON COLUMN approval_application.template_doc_id IS '模板 (qa_document.id, doc_type=template), 通过后用此模板生成 PDF';
COMMENT ON COLUMN approval_application.type_id IS '旧的审批类型 ID (保留兼容), 新申请用 template_doc_id';
CREATE INDEX idx_app_user ON approval_application(user_id);
CREATE INDEX idx_app_status ON approval_application(status);
CREATE INDEX idx_app_type ON approval_application(type_id);
CREATE INDEX idx_app_template ON approval_application(template_doc_id);

-- 审批记录表
CREATE TABLE approval_record (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES approval_application(id),
    approver_id     BIGINT NOT NULL REFERENCES sys_user(id),
    approver_level  SMALLINT NOT NULL,
    action          VARCHAR(20) NOT NULL,
    comment         TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE approval_record IS '审批操作记录';
COMMENT ON COLUMN approval_record.action IS '操作: approve/reject/withdraw';
CREATE INDEX idx_record_app ON approval_record(application_id);

-- ==================== 学生画像与荣誉 (P1) ====================

-- 荣誉记录表
CREATE TABLE student_honor (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES sys_user(id),
    honor_name   VARCHAR(200) NOT NULL,
    honor_level  VARCHAR(50),
    award_date   DATE,
    cert_file    VARCHAR(500),
    created_by   BIGINT,
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE student_honor IS '学生荣誉记录（仅管理员可录入）';
COMMENT ON COLUMN student_honor.honor_level IS '级别: 国家级/校级/院级';
CREATE INDEX idx_honor_user ON student_honor(user_id);

-- ==================== 初始数据 ====================

-- 默认初始账号 (密码均为: admin123, BCrypt 哈希)
-- 邮箱和手机号为演示用虚构信息, 部署后请在管理端修改密码
INSERT INTO sys_user (student_id, name, password, role_level, grade, major, class_name, phone, email, status)
VALUES
('admin', '系统管理员', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 1, NULL, NULL, NULL, '13800000001', 'admin.demo@example.edu', 1),
('20240001', '测试学生', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 4, '2024', '计算机科学与技术', '2024级1班', '13800000002', 'student.demo@example.edu', 1),
-- 管理老师 / 辅导员 (无年级/专业/班级)
('T2024001', '张老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001001', 'teacher.zhang@example.edu', 1),
('T2024002', '李老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001002', 'teacher.li@example.edu', 1),
('T2024003', '王老师', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 2, NULL, NULL, NULL, '13800001003', 'teacher.wang@example.edu', 1),
-- 班团骨干 (保留年级/专业/班级, 3 级数据隔离按 class_name 走)
('L2024001', '陈骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2024', '计算机科学与技术', '2024级1班', '13800002001', 'leader.chen@example.edu', 1),
('L2024002', '刘骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2024', '计算机科学与技术', '2024级2班', '13800002002', 'leader.liu@example.edu', 1),
('L2023001', '赵骨干', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 3, '2023', '软件工程',         '2023级1班', '13800002003', 'leader.zhao@example.edu', 1)
ON CONFLICT (student_id) DO NOTHING;

-- 默认审批类型
INSERT INTO approval_type (name, description, approval_chain) VALUES
('在读证明', '开具在读证明', '2'),
('成绩证明', '开具成绩证明', '2'),
('政审证明', '开具政审证明（需院领导审批）', '2,1'),
('离校证明', '开具离校证明', '2,1');

-- 默认入党流程模板（按“发展党员工作程序”）
INSERT INTO party_process_template (name, description, total_steps) VALUES
('入党流程', '发展党员工作程序', 29);

INSERT INTO party_process_step (template_id, step_order, name, description, duration_days) VALUES
(1, 1, '教育引导', '入党积极分子确定', NULL),
(1, 2, '接收入党申请书并派人谈话', '入党积极分子确定', NULL),
(1, 3, '确定入党积极分子并报党委备案', '入党积极分子确定', NULL),
(1, 4, '指定培养联系人并进行培养教育', '入党积极分子确定', NULL),
(1, 5, '考察', '入党积极分子确定', NULL),
(1, 6, '支部委员会听取意见后讨论', '发展对象确定', NULL),
(1, 7, '报党委备案后确定发展对象', '发展对象确定', NULL),
(1, 8, '确定入党介绍人', '发展对象确定', NULL),
(1, 9, '政治审查', '发展对象确定', NULL),
(1, 10, '短期集中培训', '发展对象确定', NULL),
(1, 11, '支部委员会听取意见后讨论', '预备党员接收', NULL),
(1, 12, '报党委预审', '预备党员接收', NULL),
(1, 13, '公示', '预备党员接收', NULL),
(1, 14, '召开支部大会讨论接收预备党员', '预备党员接收', NULL),
(1, 15, '将有关材料报党委', '预备党员接收', NULL),
(1, 16, '党委委员或组织员与发展对象谈话', '预备党员接收', NULL),
(1, 17, '党委审批', '预备党员接收', NULL),
(1, 18, '党委审批结果通知党支部', '预备党员接收', NULL),
(1, 19, '报上级党委组织部门备案', '预备党员接收', NULL),
(1, 20, '编入党支部和党小组', '预备党员教育和转正', NULL),
(1, 21, '入党宣誓', '预备党员教育和转正', NULL),
(1, 22, '教育和考察', '预备党员教育和转正', NULL),
(1, 23, '提交转正申请并征求意见并审查', '预备党员教育和转正', NULL),
(1, 24, '公示', '预备党员教育和转正', NULL),
(1, 25, '召开支部大会讨论预备党员转正', '预备党员教育和转正', NULL),
(1, 26, '将有关材料报党委', '预备党员教育和转正', NULL),
(1, 27, '党委审批', '预备党员教育和转正', NULL),
(1, 28, '党委审批结果通知党支部', '预备党员教育和转正', NULL),
(1, 29, '存档', '正式党员', NULL);

INSERT INTO party_process_template (name, description, total_steps) VALUES
('入团流程', '标准入团流程', 5);

INSERT INTO party_process_step (template_id, step_order, name, description, duration_days) VALUES
(2, 1, '递交入团申请书', '向团组织递交入团申请书', NULL),
(2, 2, '团组织审查', '团支部对申请人进行审查', 15),
(2, 3, '团课学习', '参加团课学习并通过考核', 30),
(2, 4, '支部大会表决', '团支部大会讨论表决', NULL),
(2, 5, '上级团委审批', '报上级团委审批并颁发团员证', 30);

-- 办公模板占位记录（file_path 由 scripts/import-templates.ps1 填充实际路径; 空字符串表示模板尚未上线）
INSERT INTO qa_document (title, category, doc_type, file_path, file_type, description) VALUES
('党员证明模板',     '党团证明', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '面向已发展为正式党员的学生, 用于办理需出具党员身份证明的事项'),
('团员证明模板',     '党团证明', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '面向团员学生, 用于办理需出具团员身份证明的事项'),
('请假条模板',       '请假申请', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '学生请假申请使用; 由辅导员审批后留底备查（待上传）'),
('活动预算表模板',   '活动报销', 'template', '', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',          '学生组织活动经费预算编制使用（待上传）'),
('班会简报模板',     '工作简报', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '班会、团日活动总结汇报使用（待上传）');
