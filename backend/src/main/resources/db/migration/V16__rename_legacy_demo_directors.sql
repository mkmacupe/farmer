UPDATE users
SET username = 'berezka',
    full_name = 'Ирина Соколова',
    legal_entity_name = 'Магазин "Берёзка"'
WHERE username = 'mogilevkhim';

UPDATE users
SET username = 'kvartal',
    full_name = 'Павел Лаврентьев',
    legal_entity_name = 'Магазин "Квартал"'
WHERE username = 'mogilevlift';

UPDATE users
SET username = 'yantar',
    full_name = 'Наталья Гринько',
    legal_entity_name = 'Магазин "Янтарь"'
WHERE username = 'babushkina';

UPDATE audit_logs SET actor_username = 'berezka' WHERE actor_username = 'mogilevkhim';
UPDATE audit_logs SET actor_username = 'kvartal' WHERE actor_username = 'mogilevlift';
UPDATE audit_logs SET actor_username = 'yantar' WHERE actor_username = 'babushkina';

UPDATE order_timeline_events SET actor_username = 'berezka' WHERE actor_username = 'mogilevkhim';
UPDATE order_timeline_events SET actor_username = 'kvartal' WHERE actor_username = 'mogilevlift';
UPDATE order_timeline_events SET actor_username = 'yantar' WHERE actor_username = 'babushkina';

UPDATE stock_movements SET actor_username = 'berezka' WHERE actor_username = 'mogilevkhim';
UPDATE stock_movements SET actor_username = 'kvartal' WHERE actor_username = 'mogilevlift';
UPDATE stock_movements SET actor_username = 'yantar' WHERE actor_username = 'babushkina';

UPDATE store_addresses
SET label = 'Берёзка • Центральный'
WHERE label = 'МХВ Точка 01'
  AND user_id = (SELECT id FROM users WHERE username = 'berezka');

UPDATE store_addresses
SET label = 'Квартал • Проспект Мира'
WHERE label = 'МЛМ Точка 01'
  AND user_id = (SELECT id FROM users WHERE username = 'kvartal');

UPDATE store_addresses
SET label = 'Янтарь • Павлова'
WHERE label = 'БК Точка 01'
  AND user_id = (SELECT id FROM users WHERE username = 'yantar');
