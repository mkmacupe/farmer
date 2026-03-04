DELETE FROM store_addresses
WHERE user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username IN ('mogilevkhim', 'mogilevlift', 'babushkina')
)
  AND id NOT IN (
    SELECT DISTINCT o.delivery_address_id
    FROM orders o
    WHERE o.delivery_address_id IS NOT NULL
  );

UPDATE store_addresses s
SET s.address_line = COALESCE(
        (
          SELECT o.delivery_address_text
          FROM orders o
          WHERE o.delivery_address_id = s.id
          ORDER BY o.created_at DESC
          LIMIT 1
        ),
        s.address_line
    ),
    s.latitude = COALESCE(
        (
          SELECT o.delivery_latitude
          FROM orders o
          WHERE o.delivery_address_id = s.id
          ORDER BY o.created_at DESC
          LIMIT 1
        ),
        s.latitude
    ),
    s.longitude = COALESCE(
        (
          SELECT o.delivery_longitude
          FROM orders o
          WHERE o.delivery_address_id = s.id
          ORDER BY o.created_at DESC
          LIMIT 1
        ),
        s.longitude
    ),
    s.updated_at = CURRENT_TIMESTAMP
WHERE s.user_id IN (
  SELECT u.id
  FROM users u
  WHERE u.username IN ('mogilevkhim', 'mogilevlift', 'babushkina')
);

UPDATE store_addresses s
SET s.label = (
  SELECT CONCAT('МХВ Точка ', LPAD(CONCAT('', r.seq), 2, '0'))
  FROM (
    SELECT s2.id, ROW_NUMBER() OVER (ORDER BY s2.created_at, s2.id) AS seq
    FROM store_addresses s2
    WHERE s2.user_id = (
      SELECT u.id
      FROM users u
      WHERE u.username = 'mogilevkhim'
    )
  ) r
  WHERE r.id = s.id
)
WHERE s.user_id = (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevkhim'
);

UPDATE store_addresses s
SET s.label = (
  SELECT CONCAT('МЛМ Точка ', LPAD(CONCAT('', r.seq), 2, '0'))
  FROM (
    SELECT s2.id, ROW_NUMBER() OVER (ORDER BY s2.created_at, s2.id) AS seq
    FROM store_addresses s2
    WHERE s2.user_id = (
      SELECT u.id
      FROM users u
      WHERE u.username = 'mogilevlift'
    )
  ) r
  WHERE r.id = s.id
)
WHERE s.user_id = (
  SELECT u.id
  FROM users u
  WHERE u.username = 'mogilevlift'
);

UPDATE store_addresses s
SET s.label = (
  SELECT CONCAT('БК Точка ', LPAD(CONCAT('', r.seq), 2, '0'))
  FROM (
    SELECT s2.id, ROW_NUMBER() OVER (ORDER BY s2.created_at, s2.id) AS seq
    FROM store_addresses s2
    WHERE s2.user_id = (
      SELECT u.id
      FROM users u
      WHERE u.username = 'babushkina'
    )
  ) r
  WHERE r.id = s.id
)
WHERE s.user_id = (
  SELECT u.id
  FROM users u
  WHERE u.username = 'babushkina'
);

INSERT INTO store_addresses (user_id, label, address_line, latitude, longitude, created_at, updated_at)
SELECT u.id,
       'МХВ Точка 01',
       'Могилёв, ул. Челюскинцев 105',
       53.8654000,
       30.2905000,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users u
WHERE u.username = 'mogilevkhim'
  AND NOT EXISTS (
    SELECT 1
    FROM store_addresses s
    WHERE s.user_id = u.id
  );

INSERT INTO store_addresses (user_id, label, address_line, latitude, longitude, created_at, updated_at)
SELECT u.id,
       'МЛМ Точка 01',
       'Могилёв, пр-т Мира 42',
       53.8948000,
       30.3312000,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users u
WHERE u.username = 'mogilevlift'
  AND NOT EXISTS (
    SELECT 1
    FROM store_addresses s
    WHERE s.user_id = u.id
  );

INSERT INTO store_addresses (user_id, label, address_line, latitude, longitude, created_at, updated_at)
SELECT u.id,
       'БК Точка 01',
       'Могилёв, ул. Академика Павлова 3',
       53.9342000,
       30.2941000,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users u
WHERE u.username = 'babushkina'
  AND NOT EXISTS (
    SELECT 1
    FROM store_addresses s
    WHERE s.user_id = u.id
  );
