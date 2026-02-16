# Runtime Architecture

## 1. Approach

Project uses a **modular monolith**: one Spring Boot app, one MySQL database, one frontend SPA.

This is intentional for a coursework scope:
- easier local setup;
- easier debugging;
- fewer integration points;
- less operational overhead.

## 2. Backend modules

- `auth`: login + JWT issuing (`AuthController`, `AuthService`)
- `catalog`: product CRUD (`ProductController`, `ProductService`)
- `orders`: order creation, listing by role, status transitions (`OrderController`, `OrderService`)
- `timeline`: order lifecycle events (`OrderTimelineService`)
- `inventory`: stock movement history (`StockMovementService`, `StockMovementController`)
- `reporting`: Excel export (`ReportController`, `ReportService`)
- `audit`: immutable action history (`AuditController`, `AuditEventListener`)
- `notifications`: SSE stream (`NotificationController`, `NotificationStreamService`)

## 3. Request flow

Typical flow is standard layered architecture:

1. Controller validates input and role access.
2. Service executes business rules and transaction logic.
3. Repository persists/reads entities.
4. DTO response is returned to frontend.

No microservices, no API gateway, no external tracing/metrics stack.

## 4. Audit flow

Domain services publish `AuditTrailEvent` through `AuditTrailPublisher`.

`AuditEventListener` resolves actor identity from JWT/security context and writes to `audit_logs`.

Tracked actions include:
- `AUTH_LOGIN_SUCCESS`
- `AUTH_LOGIN_FAILED`
- `PRODUCT_CREATED`
- `PRODUCT_UPDATED`
- `PRODUCT_DELETED`
- `ORDER_CREATED`
- `ORDER_STATUS_CHANGED`
