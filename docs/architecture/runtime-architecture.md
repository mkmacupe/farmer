# Runtime Architecture

## 1. Общая схема выполнения

В runtime проект состоит из трёх основных узлов:

- `Frontend SPA` на React/Vite
- `Backend API` на Spring Boot
- `PostgreSQL`

В production frontend и backend могут быть разнесены по разным хостингам:

- frontend — Render Static Site или Cloudflare Pages
- backend — Render Web Service
- database — PostgreSQL

## 2. Архитектурный стиль

Backend реализован как `модульный монолит`.

Это означает:

- один deployable артефакт;
- единая кодовая база бизнес-логики;
- разделение по сервисам и контроллерам, а не по отдельным микросервисам.

Для курсового проекта это оптимально, потому что:

- меньше DevOps-сложности;
- проще тестировать и отлаживать;
- проще объяснять архитектуру на защите;
- легче поддерживать транзакции между сущностями заказа, склада, аудита и уведомлений.

## 3. Модули backend

### Аутентификация

- `AuthController`
- `AuthService`
- `SecurityConfig`

Функция: логин, demo-login, выпуск JWT, проверка ролей.

### Каталог и склад

- `ProductController`
- `ProductService`
- `StockMovementController`
- `StockMovementService`

Функция: управление товарами, остатками и историей движений.

### Профиль директора и адреса

- `DirectorProfileController`
- `DirectorProfileService`
- `GeoController`
- `GeocodingService`

Функция: профиль клиента, адреса доставки, геокодирование и reverse-geocoding.

### Заказы и маршрутная логика

- `OrderController`
- `OrderService`
- `OrderTimelineService`

Функция: создание заказа, смена статусов, повтор заказа, назначение водителя, автоназначение.

### Аналитика и отчёты

- `DashboardController`
- `DashboardService`
- `ReportController`
- `ReportService`

Функция: агрегаты для панели менеджера, тренды, категории спроса, экспорт Excel.

### Аудит и realtime

- `AuditController`
- `AuditTrailPublisher`
- `AuditEventListener`
- `NotificationController`
- `NotificationStreamService`

Функция: запись действий в аудит и доставка live-событий в frontend через SSE.

### Управление демонстрационным сценарием

- `DataInitializer`
- `DemoTransportScenarioInitializer`
- `DemoScenarioController`
- `DemoScenarioService`

Функция: начальная инициализация demo-данных и полный reset демонстрационного сценария перед защитой.

## 4. Типовой request flow

Обработка запроса в backend идёт по слоистой схеме:

1. `Controller` принимает HTTP-запрос и валидирует входные данные.
2. `Security` проверяет JWT и роль пользователя.
3. `Service` исполняет бизнес-логику и транзакции.
4. `Repository` обращается к PostgreSQL через JPA/Hibernate.
5. `DTO` возвращается во frontend.

Для ошибок используется единая точка обработки:

- `ApiExceptionHandler`

## 5. Frontend runtime

Frontend — это SPA с ролевой оболочкой:

- после логина сохраняется JWT и контекст пользователя;
- загружается нужная рабочая область по роли;
- API-клиент централизован в `frontend/src/api.js`;
- для free-хостинга реализованы retry и wake-up механизмы на login и ключевых GET-запросах;
- realtime события приходят через SSE.

## 6. Cold start и free hosting

Так как backend хостится на бесплатном плане Render, он может засыпать после простоя. Для runtime-устойчивости добавлено:

- readiness endpoint `/actuator/health/readiness`
- retry логика во frontend API-клиенте
- понятное состояние ожидания на форме логина
- повторный опрос после transient `502/503/504`

Это не делает хостинг always-on, но делает приложение пригодным для публичной демонстрации после пробуждения сервиса.

## 7. Демонстрационный reset

Перед защитой или после серии тестов систему можно вернуть к эталонному состоянию.

Reset очищает:

- заказы и позиции заказов;
- timeline событий;
- движения склада;
- realtime уведомления;
- аудит;
- адреса;
- пользователей;
- товары.

После очистки заново запускается:

- `DataInitializer` — базовые demo-пользователи, адреса и товары;
- `DemoTransportScenarioInitializer` — готовый пул `APPROVED` заказов для логиста.

В результате демонстрация всегда стартует из одинакового состояния.
