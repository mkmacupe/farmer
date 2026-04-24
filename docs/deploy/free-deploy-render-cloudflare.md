# Бесплатный деплой (Render или Render + Cloudflare Pages)

Ниже два рабочих сценария для курсового проекта:
- Вариант A (проще): backend + frontend на Render
- Вариант B: backend на Render, frontend на Cloudflare Pages

## 1) Подготовка репозитория

В проект уже добавлен `render.yaml` для backend.

Убедитесь, что код в Git-репозитории и запушен на GitHub:

```bat
C:\Progra~1\Git\cmd\git.exe add render.yaml backend/src/main/resources/application.yml
C:\Progra~1\Git\cmd\git.exe commit -m "Add free deploy config for Render"
C:\Progra~1\Git\cmd\git.exe remote add origin https://github.com/<YOUR_USER>/<YOUR_REPO>.git
C:\Progra~1\Git\cmd\git.exe branch -M main
C:\Progra~1\Git\cmd\git.exe push -u origin main
```

## 2) Деплой backend + frontend на Render (вариант A)

1. Откройте:
   `https://dashboard.render.com/blueprint/new?repo=https://github.com/<YOUR_USER>/<YOUR_REPO>`
2. Нажмите `Apply`.
3. Создайте или подключите PostgreSQL и задайте для сервиса `farm-sales-backend`:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
4. В переменных сервиса `farm-sales-backend` задайте:
   - `APP_CORS_ALLOWED_ORIGINS=https://farmer.indevs.in,https://farm-sales-frontend.onrender.com,https://*.onrender.com`
5. В переменных сервиса `farm-sales-frontend` задайте:
   - `VITE_API_BASE=https://farm-sales-backend.onrender.com/api`
6. Дождитесь статуса `Live` у обоих сервисов.

Что уже безопасно задано в репозитории:

- `SPRING_PROFILES_ACTIVE=prod`
- `APP_DEMO_ENABLED=false`
- `APP_DEMO_SEED_ON_STARTUP=false`

То есть production deploy по умолчанию не полагается на demo/reset.

Публичный defense-reset в текущем backend доступен через manager-only endpoint `POST /api/scenario/reset`. Для него не нужно включать публичный demo-login. Автоматический seed при старте по-прежнему выключен, чтобы deploy не менял состояние базы без явного запуска сценария.

Для обычного production-runtime лучше оставлять demo-login и startup-seed выключенными.

Важно: в текущем Render blueprint backend собирается отдельно от frontend static-сервиса. Поэтому публичное включение demo/reset без дополнительной поставки каталога `frontend/public/images/products` в backend-контейнер гарантирует только core demo-каталог. Полное восстановление расширенного каталога имеет смысл на локальном контуре или в контейнере, куда эта директория передана явно через образ/volume.

Проверка:

```text
https://farm-sales-backend.onrender.com/actuator/health/readiness
```

## 3) Деплой frontend на Cloudflare Pages (вариант B)

1. Cloudflare Dashboard -> `Workers & Pages` -> `Create` -> `Pages` -> `Connect to Git`.
2. Выберите этот же GitHub-репозиторий.
3. Настройки сборки:
   - Framework preset: `Vite`
   - Root directory: `frontend`
   - Build command: `npm ci && npm run build`
   - Build output directory: `dist`
4. Environment Variables (Production и Preview):
   - `VITE_API_BASE=https://<render-service>.onrender.com/api`
5. Нажмите `Save and Deploy`.

## 4) Финальная связка CORS (вариант B)

После того как получите итоговый домен Pages (`https://<PAGES_PROJECT>.pages.dev`), обновите на Render:

`APP_CORS_ALLOWED_ORIGINS=https://<PAGES_PROJECT>.pages.dev`

Если будет кастомный домен, добавьте его через запятую:

`https://<PAGES_PROJECT>.pages.dev,https://your-domain.com`

## 5) Важные ограничения free-варианта

- Render Free backend может засыпать после простоя, но frontend уже умеет автоматически пережидать cold start и повторять transient `502/503/504` запросы на входе и при загрузке данных.
- Для Render health check лучше использовать readiness endpoint `/actuator/health/readiness`, а не общий `/actuator/health`.
- Локально проект использует PostgreSQL через Docker; на Render состояние базы определяется подключённым Postgres-инстансом, а не файловой системой контейнера.
- `docker-compose.yml` тоже использует безопасные defaults для production-профиля: demo/reset там выключены, а для локальной защиты их нужно включать явно через `.env`.
- Если нужен backend без засыпания и реально 24/7, free-вариант не подходит: потребуется always-on тариф или другой always-on хостинг.
