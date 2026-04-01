# Runtime Architecture

## 1. Общая схема

В runtime проект состоит из трёх основных узлов:

- `Frontend SPA` на React/Vite;
- `Backend API` на Spring Boot;
- база данных.

В production основной контур использует PostgreSQL. Локально проект использует тот же тип хранилища:

- PostgreSQL через Docker Desktop и `docker-compose.yml`.

## 2. Архитектурный стиль

Backend реализован как модульный монолит:

- один deployable backend;
- единая бизнес-логика;
- разделение по сервисам, контроллерам и доменным областям вместо микросервисов.

Для курсового проекта это даёт меньше инфраструктурной сложности и проще объясняется на защите.

## 3. Основные backend-модули

### Аутентификация и безопасность

- `AuthController`
- `DemoAuthController`
- `AuthService`
- `SecurityConfig`

Функция: обычный логин, demo-only `seed-login`, выпуск JWT и проверка ролей. `seed-login` регистрируется только при `app.demo.enabled=true`.

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

Функция: аудит действий и live-уведомления через SSE с retained backlog и периодической очисткой устаревших событий.

### Учебный сценарий

- `DataInitializer`
- `DemoTransportScenarioInitializer`
- `DemoScenarioController`
- `DemoScenarioService`

Функция: инициализация предзаполненных данных и reset состояния перед защитой. Demo-логины фиксированы, а сами пароли берутся из локальных переменных окружения, а не из репозитория.

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
- role-based shell синхронизирует выбранную секцию с hash URL без тяжёлого роутера;
- после логина frontend лениво подгружает только нужную рабочую область пользователя;
- ленивые вторичные секции и тяжёлые компоненты вроде канбан-карточек, панели корзины и карты маршрута; карта дополнительно запрашивает дорожную геометрию только для видимого preview;
- SSE-подписка для realtime-обновлений.

## 6. Scenario reset

Reset очищает накопленное runtime-состояние и пересоздаёт предзаполненные данные, чтобы проект каждый раз стартовал из предсказуемого состояния.

Этот контур не является частью обычного production runtime: в `application-prod.yml` demo-режим и startup-seed выключены по умолчанию и включаются только явно через переменные окружения.

После reset система возвращается не в пустую базу, а в канонический логистический сценарий для защиты:

- `30` точек доставки;
- `30` заказов в статусе `APPROVED`;
- суммарный вес `4498 кг`;
- актуальные аккаунты и каталог.

Для этого используются:

- backend endpoint `POST /api/scenario/reset` при `app.demo.enabled=true`;
- PowerShell-скрипт `scripts/scenario-reset.ps1`.
