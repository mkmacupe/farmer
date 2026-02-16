ALTER TABLE users
  ADD COLUMN phone VARCHAR(30),
  ADD COLUMN legal_entity_name VARCHAR(255);

UPDATE users
SET role = 'DIRECTOR'
WHERE role = 'CUSTOMER';

UPDATE users
SET role = 'LOGISTICIAN'
WHERE role = 'PICKER';

UPDATE users
SET legal_entity_name = CONCAT(full_name, ' LLC')
WHERE role = 'DIRECTOR'
  AND (legal_entity_name IS NULL OR TRIM(legal_entity_name) = '');

ALTER TABLE products
  ADD COLUMN category VARCHAR(100) NOT NULL DEFAULT 'General',
  ADD COLUMN photo_url VARCHAR(500);

CREATE INDEX idx_products_category ON products(category);

CREATE TABLE store_addresses (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  label VARCHAR(120) NOT NULL,
  address_line VARCHAR(500) NOT NULL,
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_store_addresses_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_store_addresses_user ON store_addresses(user_id);

ALTER TABLE orders
  ADD COLUMN delivery_address_id BIGINT,
  ADD COLUMN delivery_address_text VARCHAR(500) DEFAULT 'Address is not specified',
  ADD COLUMN delivery_latitude DECIMAL(10,7),
  ADD COLUMN delivery_longitude DECIMAL(10,7),
  ADD COLUMN approved_by_manager_id BIGINT,
  ADD COLUMN approved_at TIMESTAMP NULL,
  ADD COLUMN assigned_driver_id BIGINT,
  ADD COLUMN assigned_by_logistician_id BIGINT,
  ADD COLUMN assigned_at TIMESTAMP NULL,
  ADD COLUMN delivered_at TIMESTAMP NULL;

ALTER TABLE orders
  ADD CONSTRAINT fk_orders_delivery_address FOREIGN KEY (delivery_address_id) REFERENCES store_addresses(id),
  ADD CONSTRAINT fk_orders_approved_by_manager FOREIGN KEY (approved_by_manager_id) REFERENCES users(id),
  ADD CONSTRAINT fk_orders_assigned_driver FOREIGN KEY (assigned_driver_id) REFERENCES users(id),
  ADD CONSTRAINT fk_orders_assigned_by_logistician FOREIGN KEY (assigned_by_logistician_id) REFERENCES users(id);

CREATE INDEX idx_orders_delivery_address ON orders(delivery_address_id);
CREATE INDEX idx_orders_assigned_driver ON orders(assigned_driver_id);
