-- ============================================================================
-- 增量迁移: 证明申请改为模板驱动
--
-- 之前: approval_application.type_id 引用 approval_type 表 (在读/成绩/政审/离校证明)
-- 现在: approval_application.template_doc_id 引用 qa_document.id (doc_type='template')
--      学生从办公模板中选择 → 后端读 docx 替换占位符 → 生成 PDF
--
-- 兼容: type_id 保留 (允许 NULL), 旧申请仍可查询
-- ============================================================================

ALTER TABLE approval_application
    ADD COLUMN IF NOT EXISTS template_doc_id BIGINT;

ALTER TABLE approval_application
    ALTER COLUMN type_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_approval_application_template
    ON approval_application(template_doc_id);

COMMENT ON COLUMN approval_application.template_doc_id IS '模板 (qa_document.id), doc_type=template';
COMMENT ON COLUMN approval_application.type_id IS '旧的审批类型 ID (保留兼容), 新申请用 template_doc_id';
