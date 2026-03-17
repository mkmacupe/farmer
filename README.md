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
- H2 fallback для локального `dev`-профиля

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

После старта:

- frontend: `http://127.0.0.1:5173`
- backend API: `http://127.0.0.1:8080/api` по умолчанию, либо фактический адрес из строки `Backend API:` в выводе `start-dev.ps1`, если порт был автоматически изменён;

`start-dev.ps1`:

- подготавливает `.env`, если он отсутствует;
- поднимает локальный контур для frontend и backend;
- использует PostgreSQL через Docker, где это нужно;
- совместим с локальным backend `dev`-профилем, где есть H2 fallback.

## Альтернативный ручной запуск

Если нужен ручной сценарий без `start-dev.ps1`:

```powershell
docker compose up -d
cd frontend
npm install
npm run dev
```

Backend при необходимости можно запускать отдельно через Maven. Для локальной разработки доступен `dev`-профиль с H2 fallback.

## Demo reset

Локальный reset:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-reset.ps1 -Base http://127.0.0.1:8080/api
```

Если backend запущен не через `start-dev.ps1`, а вручную или на другом порту, в `-Base` нужно передать фактический адрес `/api`.

Публичный backend:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-reset.ps1 -Base https://farm-sales-backend.onrender.com/api
```

Reset очищает накопленное runtime-состояние, пересоздаёт demo-пользователей и каталог, а затем поднимает канонический транспортный сценарий для защиты: `25` точек доставки и `35` заказов в статусе `APPROVED`.

## Демо-аккаунты

| Роль | Логин | Пароль |
|---|---|---|
| Director | `berezka` | `BrzK8r2pQ1` |
| Director | `kvartal` | `KvtT4n7xR2` |
| Director | `yantar` | `YntP6m9sL3` |
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
- `scripts/demo-reset.ps1` — reset демо-данных;
- `docs/` — обзор, архитектура, защита, deploy.

## Документация

- [Технический обзор](docs/PROJECT_OVERVIEW.md)
- [Runtime architecture](docs/architecture/runtime-architecture.md)
- [Сценарий защиты](docs/defense/demo-scenario.md)
- [Транспортная задача](docs/defense/transport-task.md)
- [Deploy notes](docs/deploy/free-deploy-render-cloudflare.md)
