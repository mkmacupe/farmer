# Разработка сетевого приложения для автоматизации работы отдела сбыта фермерского хозяйства

Полноценный курсовой проект: многоролевая система для работы отдела сбыта, логистики и доставки с каталогом, заказами, аудитом, аналитикой и интеграцией геоданных.

## Стек
- Frontend: React (Vite)
- Backend: Spring Boot + MySQL
- Auth: JWT + RBAC
- API docs: OpenAPI + Swagger UI
- Realtime: SSE
- Геоинтеграция: Nominatim (OpenStreetMap)

## Деплой в интернет (бесплатно)
- Готовый сценарий `Render (backend) + Cloudflare Pages (frontend)`:
  - `docs/deploy/free-deploy-render-cloudflare.md`

## Роли и процессы
- `MANAGER`:
  - регистрирует директоров магазинов вручную (внутренний процесс),
  - одобряет заявки на доставку,
  - ведет карточки товаров (id, наименование, категория, количество, фото),
  - получает аналитику и отчёты.
- `LOGISTICIAN`:
  - назначает водителей на заказы, одобренные менеджером.
- `DRIVER`:
  - видит только свои назначения,
  - отмечает заказ как `DELIVERED`.
- `DIRECTOR` (директор магазина):
  - ведет профиль (ФИО, телефон),
  - не может менять название юрлица,
  - управляет адресами магазинов с координатами,
  - собирает корзину из каталога с поиском и фильтрами,
  - оформляет заказ на выбранный адрес,
  - видит историю и повторяет заказ в один клик.

## Статусы заказа
- `CREATED` -> `APPROVED` -> `ASSIGNED` -> `DELIVERED`

## Запуск (Docker)
Быстрый старт в Windows:
```powershell
.\start-dev.ps1
```
или двойной клик `start-dev.bat`.
Примечание: `start-dev.bat` вызывает `start-dev.ps1` в dev-режиме (без `-FrontendProduction`).

Frontend в production-режиме (без React dev build):
```powershell
.\start-dev.ps1 -FrontendProduction
```

Скрипт:
- автоматически создаёт `.env` с надёжными секретами,
- подбирает свободные порты, если дефолтные заняты,
- выводит URL запуска и демо-учётки.

Пользовательские порты:
```powershell
.\start-dev.ps1 -MysqlPort 3308 -ApiPort 8081 -FrontendPort 5174
```

Доступ из VPN/другого устройства:
```powershell
.\start-dev.ps1 -FrontendHost 0.0.0.0 -FrontendOrigin http://<VPN-IP>:5173
```
Если адрес меняется часто, можно временно задать `APP_CORS_ALLOWED_ORIGINS=*` в окружении или `.env` (только для локальной разработки; в продакшене используйте явный список доменов).
Если backend ушёл с `8080` на другой порт, скрипт сам запишет его в `frontend/.env.local` для Vite proxy.

Строгий режим (без автоподбора портов):
```powershell
.\start-dev.ps1 -StrictPorts
```

## Ручной запуск
1. Создайте `.env`:
   ```bash
   cp .env.example .env
   ```
2. Поднимите БД + backend:
   ```bash
   docker compose up --build
   ```
3. Запустите frontend:
   ```bash
   cd frontend
   npm install
   # если backend на нестандартном порту:
   # VITE_API_BASE=http://localhost:8081/api
   npm run dev
   ```
4. Откройте `http://localhost:5173`.

## Демо-пользователи
- `mogilevkhim`
- `mogilevlift`
- `babushkina`
- `manager`
- `logistician`
- `driver1`
- `driver2`
- `driver3`
- Базовый пароль: `DEMO_PASSWORD` из `.env`
- Фактический пароль профиля: `<DEMO_PASSWORD>:<username>`
  - Пример для `manager`: `<DEMO_PASSWORD>:manager`

## Основные API
- Auth:
  - `POST /api/auth/login`
  - Саморегистрации нет — директоров создаёт менеджер через `POST /api/users/directors`.
- Каталог:
  - `GET /api/products?category=&q=&page=0&size=24`
  - `GET /api/products/categories`
  - `POST /api/products`
  - `PUT /api/products/{id}`
  - `DELETE /api/products/{id}`
- Профиль директора:
  - `GET /api/director/profile`
  - `PATCH /api/director/profile`
  - `GET /api/director/addresses`
  - `POST /api/director/addresses`
  - `PUT /api/director/addresses/{id}`
  - `DELETE /api/director/addresses/{id}`
- Геоданные:
  - `GET /api/geo/lookup?q=<text>&limit=5`
  - `GET /api/geo/reverse?lat=<lat>&lon=<lon>`
- Заказы:
  - `POST /api/orders`
  - `POST /api/orders/{id}/repeat`
  - `GET /api/orders/my`
  - `GET /api/orders/my/page`
  - `GET /api/orders/assigned`
  - `GET /api/orders/assigned/page`
  - `GET /api/orders`
  - `GET /api/orders/page`
  - `POST /api/orders/{id}/approve`
  - `POST /api/orders/{id}/assign-driver`
  - `POST /api/orders/{id}/deliver`
  - `GET /api/orders/{id}/timeline`
  - `POST /api/orders/auto-assign`
  - `POST /api/orders/auto-assign/preview`
  - `POST /api/orders/auto-assign/approve`
- Пользователи:
  - `POST /api/users/directors`
  - `GET /api/users/directors`
  - `GET /api/users/drivers`
- Отчёты/аналитика:
  - `GET /api/dashboard/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`
  - `GET /api/dashboard/trends?from=YYYY-MM-DD&to=YYYY-MM-DD`
  - `GET /api/dashboard/categories?from=YYYY-MM-DD&to=YYYY-MM-DD`
  - `GET /api/reports/orders?from=YYYY-MM-DD&to=YYYY-MM-DD&status=DELIVERED`
  - `GET /api/stock-movements?productId=1&from=YYYY-MM-DD&to=YYYY-MM-DD`
  - `GET /api/audit/logs`
  - `GET /api/notifications/stream` (Bearer token в заголовке `Authorization`)

## Проверки качества
Backend:
```bash
cd backend
mvn test
```

Frontend:
```bash
cd frontend
npm test -- --run
npm run test:e2e
npm run build
```

Дополнительные проверки UI/UX (по необходимости):
```bash
cd frontend
npm run test:e2e:qa      # a11y + visual smoke
# или все e2e разом:
npm run test:e2e:all
```

## Структура
- `backend/` Spring Boot
- `frontend/` React
- `docker-compose.yml` MySQL + backend
- `docs/security/rbac-matrix.md` матрица доступов
- `docs/architecture/runtime-architecture.md` заметки по runtime-архитектуре
- `docs/postman/farm-sales.postman_collection.json` Postman-коллекция

## Диаграммы (PlantUML)
- `docs/diagrams/er.puml`
- `docs/diagrams/use-case.puml`
- `docs/diagrams/class.puml`
- `docs/diagrams/sequence.puml`
- `docs/diagrams/activity.puml`

Локальный рендер:
```bash
plantuml docs/diagrams/*.puml
```
