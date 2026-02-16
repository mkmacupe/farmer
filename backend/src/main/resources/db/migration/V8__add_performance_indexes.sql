CREATE INDEX idx_orders_customer_created_at ON orders(customer_id, created_at);
CREATE INDEX idx_orders_assigned_driver_created_at ON orders(assigned_driver_id, created_at);
CREATE INDEX idx_orders_status_created_at ON orders(status, created_at);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE INDEX idx_products_category_name ON products(category, name);
CREATE INDEX idx_users_role_full_name ON users(role, full_name);
