CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  action_type VARCHAR(64) NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  entity_id VARCHAR(64),
  actor_username VARCHAR(100) NOT NULL,
  actor_user_id BIGINT,
  actor_role VARCHAR(32),
  details VARCHAR(2000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_action_type ON audit_logs(action_type);
