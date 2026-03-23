UPDATE users
SET username = 'diralekseev',
    full_name = 'Андрей Алексеев',
    phone = '+375291000001',
    legal_entity_name = 'ООО "Лавка Полесья"'
WHERE username = 'director01';

UPDATE users
SET username = 'dirbaranova',
    full_name = 'Виктория Баранова',
    phone = '+375291000002',
    legal_entity_name = 'ООО "Сезонный Двор"'
WHERE username = 'director02';

UPDATE users
SET username = 'dirvasilevsky',
    full_name = 'Сергей Василевский',
    phone = '+375291000003',
    legal_entity_name = 'ООО "Усадьба Урожая"'
WHERE username = 'director03';

UPDATE audit_logs SET actor_username = 'diralekseev' WHERE actor_username = 'director01';
UPDATE audit_logs SET actor_username = 'dirbaranova' WHERE actor_username = 'director02';
UPDATE audit_logs SET actor_username = 'dirvasilevsky' WHERE actor_username = 'director03';

UPDATE order_timeline_events SET actor_username = 'diralekseev' WHERE actor_username = 'director01';
UPDATE order_timeline_events SET actor_username = 'dirbaranova' WHERE actor_username = 'director02';
UPDATE order_timeline_events SET actor_username = 'dirvasilevsky' WHERE actor_username = 'director03';

UPDATE stock_movements SET actor_username = 'diralekseev' WHERE actor_username = 'director01';
UPDATE stock_movements SET actor_username = 'dirbaranova' WHERE actor_username = 'director02';
UPDATE stock_movements SET actor_username = 'dirvasilevsky' WHERE actor_username = 'director03';

UPDATE store_addresses
SET label = 'Лавка Полесья • Центральный'
WHERE label = 'Демо 01 • Центральный'
  AND user_id = (SELECT id FROM users WHERE username = 'diralekseev');

UPDATE store_addresses
SET label = 'Сезонный Двор • Проспект Мира'
WHERE label = 'Демо 02 • Проспект Мира'
  AND user_id = (SELECT id FROM users WHERE username = 'dirbaranova');

UPDATE store_addresses
SET label = 'Усадьба Урожая • Павлова'
WHERE label = 'Демо 03 • Павлова'
  AND user_id = (SELECT id FROM users WHERE username = 'dirvasilevsky');
