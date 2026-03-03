UPDATE store_addresses s
SET s.label = 'Основной склад',
    s.address_line = 'Могилёв, ул. Челюскинцев 105',
    s.latitude = 53.8654000,
    s.longitude = 30.2905000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevkhim'
);

UPDATE store_addresses s
SET s.label = 'Точка отгрузки',
    s.address_line = 'Могилёв, пр-т Мира 42',
    s.latitude = 53.8948000,
    s.longitude = 30.3312000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevlift'
);

UPDATE store_addresses s
SET s.label = 'Центральный магазин',
    s.address_line = 'Могилёв, ул. Академика Павлова 3',
    s.latitude = 53.9342000,
    s.longitude = 30.2941000
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username = 'babushkina'
);
