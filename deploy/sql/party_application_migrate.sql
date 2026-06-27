-- Add party process application table for existing deployments.

CREATE TABLE IF NOT EXISTS party_process_application (
    id             BIGSERIAL PRIMARY KEY,
    app_no         VARCHAR(40) UNIQUE NOT NULL,
    user_id        BIGINT NOT NULL REFERENCES sys_user(id),
    template_id    BIGINT NOT NULL REFERENCES party_process_template(id),
    reason         TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'pending',
    reviewer_id    BIGINT REFERENCES sys_user(id),
    review_comment TEXT,
    reviewed_at    TIMESTAMP,
    instance_id    BIGINT REFERENCES party_process_instance(id),
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE party_process_application IS '党团流程申请表';
COMMENT ON COLUMN party_process_application.status IS '状态: pending/approved/rejected/withdrawn';

CREATE INDEX IF NOT EXISTS idx_party_app_user ON party_process_application(user_id);
CREATE INDEX IF NOT EXISTS idx_party_app_template ON party_process_application(template_id);
CREATE INDEX IF NOT EXISTS idx_party_app_status ON party_process_application(status);
