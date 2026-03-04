UPDATE orders
SET status = UPPER(TRIM(status))
WHERE status IS NOT NULL
  AND status <> UPPER(TRIM(status));

UPDATE orders
SET status = 'CREATED'
WHERE status IN ('NEW', 'PENDING', 'PENDING_APPROVAL', 'DRAFT', 'OPEN');

UPDATE orders
SET status = 'APPROVED'
WHERE status IN ('CONFIRMED', 'ACCEPTED');

UPDATE orders
SET status = 'ASSIGNED'
WHERE status IN ('IN_DELIVERY', 'IN_TRANSIT', 'ON_ROUTE', 'SHIPPED');

UPDATE orders
SET status = 'DELIVERED'
WHERE status IN ('DONE', 'COMPLETED', 'CLOSED');

UPDATE orders
SET status = 'CREATED'
WHERE status IS NULL
   OR status NOT IN ('CREATED', 'APPROVED', 'ASSIGNED', 'DELIVERED');
