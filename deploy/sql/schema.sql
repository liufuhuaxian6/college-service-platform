-- ============================================================
-- 学院学生综合服务与党团管理平台 - 数据库建表脚本
-- 兼容 PostgreSQL / Kingbase V8
-- ============================================================

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

-- 消息通知表（含模拟短信）
CREATE TABLE sys_notification (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(200),
    content      TEXT,
    type         VARCHAR(20) NOT NULL DEFAULT 'system',
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE sys_notification IS '系统通知与模拟短信';
COMMENT ON COLUMN sys_notification.type IS '类型: sms_sim=模拟短信, system=系统通知, reminder=流程提醒';
CREATE INDEX idx_notify_user ON sys_notification(user_id, is_read);

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

-- 政策文档表
CREATE TABLE qa_document (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50),
    file_path      VARCHAR(500) NOT NULL,
    file_size      BIGINT,
    file_type      VARCHAR(20),
    download_count INT NOT NULL DEFAULT 0,
    status         SMALLINT NOT NULL DEFAULT 1,
    created_by     BIGINT,
    created_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE qa_document IS '政策文档（≤30MB下载）';
CREATE INDEX idx_doc_category ON qa_document(category);

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
    type_id                 BIGINT NOT NULL REFERENCES approval_type(id),
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
CREATE INDEX idx_app_user ON approval_application(user_id);
CREATE INDEX idx_app_status ON approval_application(status);
CREATE INDEX idx_app_type ON approval_application(type_id);

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

-- 默认管理员账号 (密码: admin123, BCrypt 哈希)
INSERT INTO sys_user (student_id, name, password, role_level, status)
VALUES ('admin', '系统管理员', '$2a$10$aCePxF.h9J7hICCzK.1PnugvDiYrSEmrLMUCRTFULFtM5YTgVnuC.', 1, 1);

-- 默认审批类型
INSERT INTO approval_type (name, description, approval_chain) VALUES
('在读证明', '开具在读证明', '2'),
('成绩证明', '开具成绩证明', '2'),
('政审证明', '开具政审证明（需院领导审批）', '2,1'),
('离校证明', '开具离校证明', '2,1');

-- 默认入党流程模板
INSERT INTO party_process_template (name, description, total_steps) VALUES
('入党流程', '标准入党全流程', 8);

INSERT INTO party_process_step (template_id, step_order, name, description, duration_days) VALUES
(1, 1, '递交入党申请书', '向党组织递交书面入党申请书', NULL),
(1, 2, '确定入党积极分子', '经党支部研究同意，确定为入党积极分子', 90),
(1, 3, '积极分子培养考察', '指定培养联系人，进行为期一年以上的培养考察', 365),
(1, 4, '确定发展对象', '经党支部研究同意，列为发展对象', NULL),
(1, 5, '政治审查', '对发展对象进行政治审查', 30),
(1, 6, '短期集中培训', '参加入党前短期集中培训', 7),
(1, 7, '支部大会讨论通过', '召开支部大会讨论接收预备党员', NULL),
(1, 8, '上级审批', '报上级党组织审批', 90);

INSERT INTO party_process_template (name, description, total_steps) VALUES
('入团流程', '标准入团流程', 5);

INSERT INTO party_process_step (template_id, step_order, name, description, duration_days) VALUES
(2, 1, '递交入团申请书', '向团组织递交入团申请书', NULL),
(2, 2, '团组织审查', '团支部对申请人进行审查', 15),
(2, 3, '团课学习', '参加团课学习并通过考核', 30),
(2, 4, '支部大会表决', '团支部大会讨论表决', NULL),
(2, 5, '上级团委审批', '报上级团委审批并颁发团员证', 30);
