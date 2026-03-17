# Runtime Architecture

## 1. Общая схема

В runtime проект состоит из трёх основных узлов:

- `Frontend SPA` на React/Vite;
- `Backend API` на Spring Boot;
- база данных.

В production основной контур использует PostgreSQL. Локально доступны два сценария:

- PostgreSQL через `docker-compose.yml`;
- `dev`-профиль backend с H2 fallback.

## 2. Архитектурный стиль

Backend реализован как модульный монолит:

- один deployable backend;
- единая бизнес-логика;
- разделение по сервисам, контроллерам и доменным областям вместо микросервисов.

Для курсового проекта это даёт меньше инфраструктурной сложности и проще объясняется на защите.

## 3. Основные backend-модули

### Аутентификация и безопасность

- `AuthController`
- `AuthService`
- `SecurityConfig`

Функция: логин, demo-login, выпуск JWT и проверка ролей.

### Пользователи и роли

- `UserManagementController`
- `UserManagementService`

Функция: управление пользователями прикладного контура, в том числе созданием директоров магазинов.

### Каталог, склад и адреса

- `ProductController`
- `ProductService`
- `StockMovementController`
- `StockMovementService`
- `DirectorProfileController`
- `DirectorProfileService`
- `GeoController`
- `GeocodingService`

Функция: профиль директора, адреса доставки, каталог товаров, остатки и геокодирование.

### Заказы, таймлайн и логистика

- `OrderController`
- `OrderService`
- `OrderTimelineService`
- `RoadRoutingService`

Функция: создание заказа, смена статусов, назначение водителя, preview транспортной задачи и дорожная маршрутизация через OSRM.

### Аналитика и отчёты

- `DashboardController`
- `DashboardService`
- `ReportController`
- `ReportService`

Функция: агрегаты для менеджерской панели, тренды, категории спроса и экспорт Excel.

### Аудит и realtime

- `AuditController`
- `AuditTrailPublisher`
- `AuditEventListener`
- `NotificationController`
- `NotificationStreamService`

Функция: аудит действий и live-уведомления через SSE.

### Demo-сценарий

- `DataInitializer`
- `DemoTransportScenarioInitializer`
- `DemoScenarioController`
- `DemoScenarioService`

Функция: инициализация demo-данных и reset состояния перед защитой.

## 4. Типовой request flow

1. `Controller` принимает HTTP-запрос и валидирует входные данные.
2. `Security` проверяет JWT и роль.
3. `Service` исполняет бизнес-логику.
4. `Repository` работает с БД через JPA/Hibernate.
5. `DTO` возвращается во frontend.

Ошибки централизованно обрабатываются через `ApiExceptionHandler`.

## 5. Frontend runtime

Frontend после логина сохраняет JWT и ролевой контекст, затем подгружает нужную рабочую область.

Ключевые runtime-части:

- централизованный API-клиент в `frontend/src/api.js`;
- role-based shell;
- ленивые секции и тяжёлые компоненты вроде карты маршрута;
- SSE-подписка для realtime-обновлений.

## 6. Demo reset

Reset очищает накопленное runtime-состояние и пересоздаёт демонстрационные данные, чтобы проект каждый раз стартовал из предсказуемого состояния.

После reset система возвращается не в пустую базу, а в канонический логистический сценарий для защиты:

- `20` точек доставки;
- `30` заказов в статусе `APPROVED`;
- актуальные demo-пользователи и каталог.

Для этого используются:

- backend endpoint `POST /api/demo/reset`;
- PowerShell-скрипт `scripts/demo-reset.ps1`.
