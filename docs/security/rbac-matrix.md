# RBAC Matrix

Матрица ниже отражает фактические правила из `SecurityConfig` и conditional demo-controller'ов.

| Endpoint | DIRECTOR | MANAGER | LOGISTICIAN | DRIVER | ANONYMOUS |
|---|---:|---:|---:|---:|---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/seed-login` | ✅† | ✅† | ✅† | ✅† | ✅† |
| `GET /api/products` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /api/products/categories` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `POST /api/products` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `PUT /api/products/{id}` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `DELETE /api/products/{id}` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/director/profile` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PATCH /api/director/profile` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/director/addresses` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/director/addresses` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PUT /api/director/addresses/{id}` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `DELETE /api/director/addresses/{id}` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/geo/lookup` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/geo/reverse` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/orders` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/orders/{id}/repeat` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/orders/my` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/orders/my/page` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/orders/assigned` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/orders/assigned/page` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/orders` | ❌ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/orders/page` | ❌ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/orders/approve-all` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `POST /api/orders/{id}/approve` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `POST /api/orders/{id}/assign-driver` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/auto-assign` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/auto-assign/preview` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/auto-assign/route-geometry` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/auto-assign/approve` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/{id}/deliver` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/orders/{id}/timeline` | ✅* | ✅ | ✅ | ✅* | ❌ |
| `POST /api/users/directors` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/users/directors` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/users/drivers` | ❌ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/dashboard/summary` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/dashboard/trends` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/dashboard/categories` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/stock-movements` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/reports/orders` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/audit/logs` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `POST /api/scenario/reset` | ❌ | ✅† | ❌ | ❌ | ❌ |
| `POST /api/scenario/clear-orders` | ❌ | ✅† | ❌ | ❌ | ❌ |
| `GET /api/notifications/stream` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /actuator/health` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /actuator/health/readiness` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /actuator/info` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /v3/api-docs/**` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /swagger-ui.html` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /swagger-ui/**` | ✅ | ✅ | ✅ | ✅ | ❌ |

## Примечания

- `DIRECTOR` может видеть timeline только своих заказов.
- `DRIVER` может видеть timeline только заказов, назначенных на него.
- Права RBAC дополняются бизнес-валидацией статусов, ownership и ограничениями переходов состояний.
- `POST /api/auth/seed-login`, `POST /api/scenario/reset` и `POST /api/scenario/clear-orders` доступны только при `app.demo.enabled=true`; когда demo-режим выключен, эти controller'ы не регистрируются и endpoint'ы возвращают `404`.
