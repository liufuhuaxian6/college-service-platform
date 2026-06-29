-- Incremental migration for older deployments.
-- Fresh databases already include this column in deploy/sql/schema.sql.

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS tutor VARCHAR(50);
