# Краткое и подробное описание курсового проекта

## 1) Тема
**Разработка сетевого приложения для автоматизации работы отдела сбыта фермерского хозяйства.**

## 2) Цель
Реализовать серверно-клиентскую систему, которая покрывает полный цикл:
- формирование заявки директором магазина,
- одобрение менеджером,
- назначение водителя логистом,
- завершение доставки водителем.

## 3) Технологии
- **Frontend:** React + Vite
- **Backend:** Java, Spring Boot 4, Spring Security (JWT), Spring Data JPA
- **БД:** MySQL
- **Документация API:** OpenAPI/Swagger
- **Интеграции:** геокодирование адресов (Nominatim/OpenStreetMap), онлайн-карты (OSM links)

## 4) Роли
- **DIRECTOR**: профиль, адреса магазинов, каталог с категориями/поиском/фильтрами, корзина, заказ, история, повтор заказа.
- **MANAGER**: регистрация директоров, одобрение заявок, карточки товаров, аналитика и отчёты.
- **LOGISTICIAN**: назначение водителей на одобренные заказы.
- **DRIVER**: просмотр только своих заказов и отметка `DELIVERED`.

## 5) Бизнес-статусы заказа
`CREATED -> APPROVED -> ASSIGNED -> DELIVERED`

## 6) Ключевые сущности БД
- `users`
- `store_addresses`
- `products`
- `orders`
- `order_items`
- `order_timeline_events`
- `stock_movements`
- `audit_logs`

## 7) Безопасность и качество
- Пароли хранятся в хеше (BCrypt).
- Валидация и обработка ошибок ввода данных на backend.
- Ролевой контроль доступа на уровне endpoint + бизнес-ограничения.
- Unit/integration тесты backend.
- Unit/e2e тесты frontend.

## 8) API (основные группы)
- Auth: `POST /api/auth/login`
- Director: `/api/director/profile`, `/api/director/addresses`
- Products: `/api/products`, `/api/products/categories`
- Orders: `/api/orders`, `/api/orders/{id}/approve`, `/api/orders/{id}/assign-driver`, `/api/orders/{id}/deliver`
- Users: `/api/users/directors`, `/api/users/drivers`
- Analytics/Reports: `/api/dashboard/summary`, `/api/reports/orders`
- Geo: `/api/geo/lookup`

## 9) Итог
Проект соответствует формату «как следует на курсовой»: многоролевая архитектура, сквозной процесс заказа, работа с БД, безопасность, интеграции карт и геоданных, документация и тесты.
