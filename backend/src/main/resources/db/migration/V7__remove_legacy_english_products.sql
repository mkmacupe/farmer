DELETE FROM products
WHERE (name IS NOT NULL AND name ~ '[A-Za-z]')
   OR (category IS NOT NULL AND category ~ '[A-Za-z]');
