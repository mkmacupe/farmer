# RBAC Matrix

| Endpoint | DIRECTOR | MANAGER | LOGISTICIAN | DRIVER | ANONYMOUS |
|---|---:|---:|---:|---:|---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ | ✅ |
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
| `POST /api/orders` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/orders/{id}/repeat` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/orders/my` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/orders/assigned` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/orders` | ❌ | ✅ | ✅ | ❌ | ❌ |
| `POST /api/orders/{id}/approve` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `POST /api/orders/{id}/assign-driver` | ❌ | ❌ | ✅ | ❌ | ❌ |
| `POST /api/orders/{id}/deliver` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /api/orders/{id}/timeline` | ✅* | ✅ | ✅ | ✅* | ❌ |
| `POST /api/users/directors` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/users/directors` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/users/drivers` | ❌ | ✅ | ✅ | ❌ | ❌ |
| `GET /api/dashboard/summary` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/stock-movements` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/reports/orders` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/audit/logs` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/notifications/stream` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /actuator/health` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /v3/api-docs` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /swagger-ui.html` | ✅ | ✅ | ✅ | ✅ | ❌ |

Notes:
- Саморегистрация отключена, учётные записи директоров создаёт менеджер.
- Помимо RBAC, применяется бизнес-валидация статусов и владения сущностями.
- `DIRECTOR` видит таймлайн только своих заказов.
- `DRIVER` видит таймлайн только заказов, назначенных на него.
