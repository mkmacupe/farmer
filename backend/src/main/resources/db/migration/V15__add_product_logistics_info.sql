-- Добавление колонок для веса и объема продуктов
ALTER TABLE products ADD COLUMN weight_kg DOUBLE PRECISION NOT NULL DEFAULT 1.0;
ALTER TABLE products ADD COLUMN volume_m3 DOUBLE PRECISION NOT NULL DEFAULT 0.001;

-- Обновление описания колонок (опционально, для документации)
COMMENT ON COLUMN products.weight_kg IS 'Вес продукта в килограммах';
COMMENT ON COLUMN products.volume_m3 IS 'Объем продукта в кубических метрах';
