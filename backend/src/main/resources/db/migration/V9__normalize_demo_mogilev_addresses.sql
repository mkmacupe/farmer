UPDATE store_addresses s
SET label = 'Основной склад',
    address_line = 'Могилёв, ул. Челюскинцев 105',
    latitude = 53.8654000,
    longitude = 30.2905000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevkhim'
);

UPDATE store_addresses s
SET label = 'Точка отгрузки',
    address_line = 'Могилёв, пр-т Мира 42',
    latitude = 53.8948000,
    longitude = 30.3312000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevlift'
);

UPDATE store_addresses s
SET label = 'Центральный магазин',
    address_line = 'Могилёв, ул. Академика Павлова 3',
    latitude = 53.9342000,
    longitude = 30.2941000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'babushkina'
);
