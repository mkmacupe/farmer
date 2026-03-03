ALTER TABLE products
  ADD CONSTRAINT chk_products_price_non_negative CHECK (price >= 0);

ALTER TABLE products
  ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0);

ALTER TABLE orders
  ADD CONSTRAINT chk_orders_total_non_negative CHECK (total_amount >= 0);

ALTER TABLE order_items
  ADD CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE order_items
  ADD CONSTRAINT chk_order_items_price_non_negative CHECK (price >= 0);

ALTER TABLE order_items
  ADD CONSTRAINT chk_order_items_line_total_non_negative CHECK (line_total >= 0);
