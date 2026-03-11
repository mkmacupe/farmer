ALTER TABLE products
  ADD CONSTRAINT chk_products_weight_positive CHECK (weight_kg > 0);

ALTER TABLE products
  ADD CONSTRAINT chk_products_volume_positive CHECK (volume_m3 > 0);
