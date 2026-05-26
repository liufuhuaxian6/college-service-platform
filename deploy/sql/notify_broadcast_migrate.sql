-- ============================================================
-- 通知群发模块增量迁移
-- 已部署过旧版数据库时执行；全新部署用 schema.sql 即可
-- ============================================================

-- 1) sys_user 加 email
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS email VARCHAR(100);

-- 2) sys_notification 扩展
ALTER TABLE sys_notification ADD COLUMN IF NOT EXISTS tags         VARCHAR(200);
ALTER TABLE sys_notification ADD COLUMN IF NOT EXISTS source       VARCHAR(50);
ALTER TABLE sys_notification ADD COLUMN IF NOT EXISTS source_url   VARCHAR(500);
ALTER TABLE sys_notification ADD COLUMN IF NOT EXISTS broadcast_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_notify_broadcast ON sys_notification(broadcast_id);
CREATE INDEX IF NOT EXISTS idx_notify_tags      ON sys_notification(tags);

-- 3) 新增群发任务表
CREATE TABLE IF NOT EXISTS sys_notification_broadcast (
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

CREATE INDEX IF NOT EXISTS idx_broadcast_operator ON sys_notification_broadcast(operator_id);
CREATE INDEX IF NOT EXISTS idx_broadcast_created  ON sys_notification_broadcast(created_at);
