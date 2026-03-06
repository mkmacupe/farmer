CREATE TABLE realtime_notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  order_id BIGINT,
  order_status VARCHAR(20),
  target_roles VARCHAR(255) NOT NULL,
  target_user_ids VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_realtime_notifications_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_realtime_notifications_created ON realtime_notifications(created_at);
