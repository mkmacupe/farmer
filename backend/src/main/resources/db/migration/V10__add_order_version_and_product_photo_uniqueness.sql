ALTER TABLE orders
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE products p
SET p.photo_url = NULL
WHERE p.photo_url IS NOT NULL
  AND TRIM(p.photo_url) <> ''
  AND p.id <> (
    SELECT MIN(p2.id)
    FROM products p2
    WHERE p2.photo_url = p.photo_url
  );

CREATE UNIQUE INDEX ux_products_photo_url ON products(photo_url);
