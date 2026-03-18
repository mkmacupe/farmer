UPDATE users
SET username = 'director01',
    full_name = 'Директор магазина 01',
    phone = '+375291000001',
    legal_entity_name = 'Магазин "Демо 01"'
WHERE username = 'berezka';

UPDATE users
SET username = 'director02',
    full_name = 'Директор магазина 02',
    phone = '+375291000002',
    legal_entity_name = 'Магазин "Демо 02"'
WHERE username = 'kvartal';

UPDATE users
SET username = 'director03',
    full_name = 'Директор магазина 03',
    phone = '+375291000003',
    legal_entity_name = 'Магазин "Демо 03"'
WHERE username = 'yantar';

UPDATE audit_logs SET actor_username = 'director01' WHERE actor_username = 'berezka';
UPDATE audit_logs SET actor_username = 'director02' WHERE actor_username = 'kvartal';
UPDATE audit_logs SET actor_username = 'director03' WHERE actor_username = 'yantar';

UPDATE order_timeline_events SET actor_username = 'director01' WHERE actor_username = 'berezka';
UPDATE order_timeline_events SET actor_username = 'director02' WHERE actor_username = 'kvartal';
UPDATE order_timeline_events SET actor_username = 'director03' WHERE actor_username = 'yantar';

UPDATE stock_movements SET actor_username = 'director01' WHERE actor_username = 'berezka';
UPDATE stock_movements SET actor_username = 'director02' WHERE actor_username = 'kvartal';
UPDATE stock_movements SET actor_username = 'director03' WHERE actor_username = 'yantar';

UPDATE store_addresses
SET label = 'Демо 01 • Центральный'
WHERE label = 'Берёзка • Центральный'
  AND user_id = (SELECT id FROM users WHERE username = 'director01');

UPDATE store_addresses
SET label = 'Демо 02 • Проспект Мира'
WHERE label = 'Квартал • Проспект Мира'
  AND user_id = (SELECT id FROM users WHERE username = 'director02');

UPDATE store_addresses
SET label = 'Демо 03 • Павлова'
WHERE label = 'Янтарь • Павлова'
  AND user_id = (SELECT id FROM users WHERE username = 'director03');
