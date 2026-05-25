-- ============================================================
-- RAG 向量维度迁移：384 -> 512
-- 适用：从 local-hash embedding (384维) 切换到 BGE-small-zh-v1.5 (512维)
-- 注意：原 384 维向量不兼容 512 维模型，必须清空并重新索引
-- ============================================================

-- 1) 删除旧索引（不同维度的 ivfflat 索引无法兼容）
DROP INDEX IF EXISTS idx_chunk_embedding;

-- 2) 清空所有向量（必须，因为旧向量是 384 维 hash 伪向量，与新 512 维 BGE 不可混用）
TRUNCATE qa_document_chunk RESTART IDENTITY;

-- 3) 修改列类型为 512 维
ALTER TABLE qa_document_chunk ALTER COLUMN embedding TYPE vector(512);

-- 4) 重建 ivfflat 索引
CREATE INDEX idx_chunk_embedding ON qa_document_chunk
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 完成后请在管理端逐个政策文档点击"重新索引"，由 BGE 模型重新生成 512 维向量。
