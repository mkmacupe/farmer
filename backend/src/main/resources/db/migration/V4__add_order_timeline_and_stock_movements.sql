CREATE TABLE order_timeline_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  actor_user_id BIGINT,
  actor_role VARCHAR(20),
  details VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_timeline_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_order_timeline_order_created ON order_timeline_events(order_id, created_at);

CREATE TABLE stock_movements (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id BIGINT NOT NULL,
  order_id BIGINT,
  movement_type VARCHAR(20) NOT NULL,
  quantity_change INT NOT NULL,
  reason VARCHAR(120) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  actor_user_id BIGINT,
  actor_role VARCHAR(20),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT chk_stock_movements_non_zero CHECK (quantity_change <> 0)
);

CREATE INDEX idx_stock_movements_product_created ON stock_movements(product_id, created_at);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at);
