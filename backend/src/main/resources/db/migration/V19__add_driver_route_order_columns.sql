ALTER TABLE orders
  ADD COLUMN route_trip_number INTEGER;

ALTER TABLE orders
  ADD COLUMN route_stop_sequence INTEGER;

CREATE INDEX idx_orders_assigned_driver_route_sequence
  ON orders (assigned_driver_id, route_stop_sequence, assigned_at, created_at);
