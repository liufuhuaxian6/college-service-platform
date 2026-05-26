-- ============================================================
-- qa_document 表增量迁移: 支持办公模板分类
-- 已部署过旧版数据库时执行本文件（schema.sql 已合并这些改动, 全新部署不需要执行）
-- ============================================================

-- 1) 新增 doc_type 字段 (默认 policy, 不影响存量政策文档)
ALTER TABLE qa_document
    ADD COLUMN IF NOT EXISTS doc_type VARCHAR(20) NOT NULL DEFAULT 'policy';

-- 2) 新增 description 字段（模板的填写说明 / 适用范围）
ALTER TABLE qa_document
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- 3) 放宽 file_path 不能为空的限制, 改为允许空字符串 (占位模板用)
ALTER TABLE qa_document
    ALTER COLUMN file_path DROP NOT NULL;
ALTER TABLE qa_document
    ALTER COLUMN file_path SET DEFAULT '';

-- 4) 复合索引按 doc_type + status 过滤
CREATE INDEX IF NOT EXISTS idx_doc_type ON qa_document(doc_type, status);

-- 5) 加宽 file_type 至 100 字符 (容纳完整 MIME 类型, 例如 .docx 的 application/vnd.openxmlformats-...)
ALTER TABLE qa_document
    ALTER COLUMN file_type TYPE VARCHAR(100);

-- 6) 占位模板记录 (file_path 空, 待管理员通过上传补全)
INSERT INTO qa_document (title, category, doc_type, file_path, file_type, description) VALUES
('党员证明模板',     '党团证明', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '面向已发展为正式党员的学生'),
('团员证明模板',     '党团证明', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '面向团员学生'),
('请假条模板',       '请假申请', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '学生请假申请使用（待上传）'),
('活动预算表模板',   '活动报销', 'template', '', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',          '学生组织活动经费预算（待上传）'),
('班会简报模板',     '工作简报', 'template', '', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', '班会、团日活动总结（待上传）')
ON CONFLICT DO NOTHING;
