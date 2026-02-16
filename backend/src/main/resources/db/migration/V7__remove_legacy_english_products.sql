DELETE FROM products
WHERE (name IS NOT NULL AND name REGEXP '[A-Za-z]')
   OR (category IS NOT NULL AND category REGEXP '[A-Za-z]');
