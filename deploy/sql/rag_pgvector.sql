-- ============================================================
-- RAG / pgvector 增量迁移脚本
-- 已部署过旧版本数据库时，在 postgres 容器中手动执行本文件。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS qa_document_chunk (
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

CREATE INDEX IF NOT EXISTS idx_chunk_document ON qa_document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_category ON qa_document_chunk(category);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON qa_document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
