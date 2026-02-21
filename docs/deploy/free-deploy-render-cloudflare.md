# Бесплатный деплой (Render + Cloudflare Pages)

Ниже рабочий сценарий для курсового проекта:
- backend (Spring Boot) -> Render Free Web Service
- frontend (Vite/React) -> Cloudflare Pages

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

## 2) Деплой backend на Render

1. Откройте:
   `https://dashboard.render.com/blueprint/new?repo=https://github.com/<YOUR_USER>/<YOUR_REPO>`
2. Нажмите `Apply`.
3. В переменных сервиса задайте:
   - `APP_DEMO_PASSWORD` (свой пароль демо-пользователей)
   - `APP_CORS_ALLOWED_ORIGINS` (пока временно): `https://<PAGES_PROJECT>.pages.dev`
4. Дождитесь статуса `Live` и скопируйте URL backend:
   - `https://<render-service>.onrender.com`

Проверка:

```text
https://<render-service>.onrender.com/actuator/health
```

## 3) Деплой frontend на Cloudflare Pages

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

## 4) Финальная связка CORS

После того как получите итоговый домен Pages (`https://<PAGES_PROJECT>.pages.dev`), обновите на Render:

`APP_CORS_ALLOWED_ORIGINS=https://<PAGES_PROJECT>.pages.dev`

Если будет кастомный домен, добавьте его через запятую:

`https://<PAGES_PROJECT>.pages.dev,https://your-domain.com`

## 5) Важные ограничения free-варианта

- Render Free backend может засыпать после простоя (первый запрос после сна медленный).
- В `dev` профиле используется H2-файл; на free Render файловая система непостоянная.
  После рестарта/пересборки demo-данные могут сбрасываться.
