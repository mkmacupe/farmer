# Farm Sales

Курсовой проект: многоролевая система для автоматизации отдела сбыта фермерского хозяйства.

Сценарий сквозной: директор магазина создаёт заказ, менеджер его подтверждает, логист распределяет доставку, водитель завершает маршрут, менеджер получает аналитику и отчёты.

## Что есть в проекте

- роли `DIRECTOR`, `MANAGER`, `LOGISTICIAN`, `DRIVER`;
- полный жизненный цикл заказа: `CREATED -> APPROVED -> ASSIGNED -> DELIVERED`;
- каталог товаров, адреса магазинов и история заказов;
- транспортная задача с preview-маршрута и подтверждением автоназначения;
- realtime-уведомления через SSE;
- Excel-отчёты и менеджерская аналитика;
- reset демо-сценария перед защитой.

## Стек

### Backend

- Java 21
- Spring Boot 3.4
- Spring Security + JWT
- Spring Data JPA + Hibernate
- Flyway
- PostgreSQL 17 в production
- PostgreSQL 17 для локальной разработки через Docker Desktop

### Frontend

- React 19
- Vite 7
- Material UI 7
- Leaflet
- Fetch API + SSE

## Быстрый старт

Основной локальный сценарий запуска:

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

Требование: перед запуском `start-dev.ps1` должен быть поднят Docker Desktop и доступен Docker Engine. Скрипт больше не переключается на локальный H2-контур.

После старта:

- frontend: `http://127.0.0.1:5173`
- backend API: `http://127.0.0.1:8080/api` по умолчанию, либо фактический адрес из строки `Backend API:` в выводе `start-dev.ps1`, если порт был автоматически изменён;

`start-dev.ps1`:

- подготавливает `.env`, если он отсутствует;
- поднимает локальный frontend и backend-контур;
- запускает PostgreSQL и backend через Docker Desktop + `docker compose`;
- автоматически перенастраивает frontend proxy на фактический backend-порт.

## Альтернативный ручной запуск

Если нужен ручной сценарий без `start-dev.ps1`:

```powershell
docker compose up -d
cd frontend
npm install
npm run dev
```
Для ручного сценария backend также остаётся в docker-compose-контуре с PostgreSQL.
Если backend запускается отдельно через Maven или IDE, передайте те же `SPRING_DATASOURCE_*`, `POSTGRES_PORT` и `JWT_SECRET`, что используются в `.env`.

## Demo reset

Новый demo reset для локального и задеплоенного backend одновременно:

```powershell
.\demo-reset.bat
```

Скрипт по умолчанию пытается обновить локальный backend, если он доступен, и публичный backend `https://farm-sales-backend.onrender.com/api`. При необходимости можно передать конкретный `-Base` или URL первым аргументом в `.bat`.

Сбросить только заказы до нуля, оставив точки магазинов:

```powershell
.\demo-clear-orders.bat
```

Reset очищает накопленное runtime-состояние, пересоздаёт demo-пользователей и каталог, а затем поднимает канонический транспортный сценарий для защиты: `30` точек магазинов и `30` заказов в статусе `APPROVED`, по `1` заказу на точку, суммарным весом `4498 кг`. Это оставляет запас в `2 кг` до общего лимита трёх машин на один рейс (`4500 кг`), поэтому дополнительный заказ весом `2+ кг` отправит одного из водителей на второй рейс.

## Демо-аккаунты

| Роль | Логин | Пароль |
|---|---|---|
| Director | `director01` … `director30` | `Dir01Farm2026` … `Dir30Farm2026` |
| Manager | `manager` | `MgrD5v8cN4` |
| Logistician | `logistician` | `LogS7q1wE5` |
| Driver | `driver1` | `Drv1A9k2Z6` |
| Driver | `driver2` | `Drv2B8m3Y7` |
| Driver | `driver3` | `Drv3C7n4X8` |

## Проверка

```powershell
cd frontend
npm run build
npm run test:run
```

Backend:

```powershell
cd backend
mvn test
```

## Ключевые entrypoints

- `start-dev.ps1` — основной локальный запуск;
- `docker-compose.yml` — локальный PostgreSQL и backend-контур;
- `frontend/` — SPA на React/Vite;
- `backend/` — Spring Boot backend;
- `render.yaml` — deploy-конфигурация Render;
- `scripts/demo-reset.ps1` — reset демо-данных на локальном и публичном backend;
- `scripts/demo-clear-orders.ps1` — очистка заказов без удаления точек магазинов;
- `docs/` — обзор, архитектура, защита, deploy.

## Документация

- [Технический обзор](docs/PROJECT_OVERVIEW.md)
- [Runtime architecture](docs/architecture/runtime-architecture.md)
- [Сценарий защиты](docs/defense/demo-scenario.md)
- [Транспортная задача](docs/defense/transport-task.md)
- [Deploy notes](docs/deploy/free-deploy-render-cloudflare.md)
