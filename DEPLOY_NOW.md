# 🚀 Инструкция по бесплатному деплою: farmer.indevs.in

Я подготовил твой проект так, чтобы он работал **полностью бесплатно и навсегда**. Следуй этим 3 шагам.

---

## Шаг 1: Создание Базы Данных (Neon.tech)
*Render удаляет бесплатную БД через 90 дней, поэтому используем Neon (PostgreSQL).*

1. Зарегистрируйся на [Neon.tech](https://neon.tech/).
2. Создай новый проект (Project Name: `farm-sales`).
3. В панели управления Neon найди **Connection String**.
4. Скопируй её (она выглядит так: `postgresql://alex:password@ep-cool-water-123.eu-central-1.aws.neon.tech/neondb?sslmode=require`).
5. **Важно:** Сохрани отдельно:
   - `SPRING_DATASOURCE_URL`: сама строка.
   - `SPRING_DATASOURCE_USERNAME`: имя пользователя (из строки).
   - `SPRING_DATASOURCE_PASSWORD`: пароль (из строки).

---

## Шаг 2: Деплой на Render.com
*Твой проект уже содержит файл `render.yaml`, поэтому Render настроит всё почти сам.*

1. Запушь все мои изменения в свой GitHub-репозиторий.
2. Зайди на [Render Dashboard](https://dashboard.render.com/).
3. Нажми **New** -> **Blueprint**.
4. Выбери свой репозиторий.
5. Render покажет список сервисов. В поле **Environment Variables** для `farm-sales-backend` впиши:
   - `SPRING_DATASOURCE_URL`: (из Шага 1)
   - `SPRING_DATASOURCE_USERNAME`: (из Шага 1)
   - `SPRING_DATASOURCE_PASSWORD`: (из Шага 1)
6. Нажми **Apply**.
7. **После завершения деплоя:**
   - Скопируй URL бекенда (например, `https://farm-backend.onrender.com`).
   - Зайди в настройки `farm-sales-frontend` на Render и в переменную `VITE_API_BASE` вставь: `https://твой-бекенд.onrender.com/api`.

---

## Шаг 3: Настройка твоего домена (farmer.indevs.in)

### 1. В панели Render:
1. Перейди в сервис `farm-sales-frontend`.
2. Зайди в **Settings** -> **Custom Domains**.
3. Нажми **Add Custom Domain** и введи `farmer.indevs.in`.
4. Render даст тебе адрес для CNAME (например, `farm-sales-frontend.onrender.com`).

### 2. В панели DNS твоего домена (indevs.in):
Добавь новую запись:
- **Type:** `CNAME`
- **Name (Host):** `farmer`
- **Value (Target):** Адрес, который дал Render в пункте выше.

---

### Почему это круто:
- **Backend:** На Docker + Java 21 (Render Free).
- **Database:** PostgreSQL на Neon (Бесплатно навсегда).
- **Frontend:** Статический сайт с SSL (HTTPS) на твоем домене.
- **CORS:** Я уже прописал разрешение для `farmer.indevs.in`, так что запросы будут работать без ошибок.

Удачи с курсовым проектом! 🐾
