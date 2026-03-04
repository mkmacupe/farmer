import { useEffect, useMemo, useState } from 'react';
import { getAssignedOrdersPage, markOrderDelivered, subscribeNotifications } from '../api.js';

import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Slide from '@mui/material/Slide';

import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MapIcon from '@mui/icons-material/Map';
import CheckIcon from '@mui/icons-material/Check';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import Skeleton from '@mui/material/Skeleton';

const DEFAULT_DEPOT = {
  latitude: 53.8971270,
  longitude: 30.3320410
};

function haversineKm(fromLat, fromLon, toLat, toLon) {
  const toRad = (value) => (value * Math.PI) / 180;
  const dLat = toRad(toLat - fromLat);
  const dLon = toRad(toLon - fromLon);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(fromLat)) * Math.cos(toRad(toLat)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371.0088 * Math.asin(Math.sqrt(a));
}

function normalizeCoord(value, min, max) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric < min || numeric > max) {
    return null;
  }
  return numeric;
}

function routeUrlForPoints(points) {
  if (!points.length) {
    return '';
  }

  const firstPoint = points[0];
  const depotLat = normalizeCoord(DEFAULT_DEPOT.latitude, -90, 90);
  const depotLon = normalizeCoord(DEFAULT_DEPOT.longitude, -180, 180);
  if (depotLat == null || depotLon == null || firstPoint == null) {
    return '';
  }

  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${depotLat}%2C${depotLon}%3B${firstPoint.latitude}%2C${firstPoint.longitude}`;
}

function legRouteUrl(fromLat, fromLon, toLat, toLon) {
  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${fromLat}%2C${fromLon}%3B${toLat}%2C${toLon}`;
}

function buildRouteLegs(points) {
  const depotLat = normalizeCoord(DEFAULT_DEPOT.latitude, -90, 90);
  const depotLon = normalizeCoord(DEFAULT_DEPOT.longitude, -180, 180);
  if (depotLat == null || depotLon == null || !points.length) {
    return [];
  }

  const legs = [];
  let fromLat = depotLat;
  let fromLon = depotLon;
  for (const point of points) {
    legs.push({
      toOrderId: point.orderId,
      url: legRouteUrl(fromLat, fromLon, point.latitude, point.longitude)
    });
    fromLat = point.latitude;
    fromLon = point.longitude;
  }
  return legs;
}

function buildNearestRoutePoints(orders) {
  const depotLat = normalizeCoord(DEFAULT_DEPOT.latitude, -90, 90);
  const depotLon = normalizeCoord(DEFAULT_DEPOT.longitude, -180, 180);
  if (depotLat == null || depotLon == null) {
    return [];
  }

  const remaining = orders
    .filter((order) => order.status === 'ASSIGNED')
    .map((order) => {
      const lat = normalizeCoord(order.deliveryLatitude, -90, 90);
      const lon = normalizeCoord(order.deliveryLongitude, -180, 180);
      return {
        orderId: order.id,
        latitude: lat ?? depotLat,
        longitude: lon ?? depotLon,
        deliveryAddress: order.deliveryAddressText || 'Адрес не указан'
      };
    })
    .filter(Boolean);

  const ordered = [];
  let previousLat = depotLat;
  let previousLon = depotLon;

  while (remaining.length) {
    let nearestIndex = 0;
    let nearestOrderId = Number(remaining[0].orderId) || Number.MAX_SAFE_INTEGER;
    let nearestDistance = haversineKm(
      previousLat,
      previousLon,
      remaining[0].latitude,
      remaining[0].longitude
    );

    for (let index = 1; index < remaining.length; index += 1) {
      const candidate = remaining[index];
      const candidateDistance = haversineKm(previousLat, previousLon, candidate.latitude, candidate.longitude);
      const candidateOrderId = Number(candidate.orderId) || Number.MAX_SAFE_INTEGER;
      if (candidateDistance < nearestDistance) {
        nearestDistance = candidateDistance;
        nearestIndex = index;
        nearestOrderId = candidateOrderId;
      } else if (candidateDistance === nearestDistance && candidateOrderId < nearestOrderId) {
        nearestIndex = index;
        nearestOrderId = candidateOrderId;
      }
    }

    const nextPoint = remaining.splice(nearestIndex, 1)[0];
    ordered.push(nextPoint);
    previousLat = nextPoint.latitude;
    previousLon = nextPoint.longitude;
  }

  return ordered;
}

function statusMeta(status) {
  if (status === 'DELIVERED') {
    return { label: 'Доставлен', color: 'success' };
  }
  return { label: 'Назначен', color: 'warning' };
}

export default function DriverView({ token, activeSection }) {
  const [orders, setOrders] = useState([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasNext, setOrdersHasNext] = useState(false);
  const [ordersTotalItems, setOrdersTotalItems] = useState(0);
  const [ordersLoadingMore, setOrdersLoadingMore] = useState(false);

  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });
  const ordersPageSize = 50;

  const activeDeliveries = useMemo(
    () => orders.filter((order) => order.status === 'ASSIGNED').length,
    [orders]
  );
  const deliveredOrders = useMemo(
    () => orders.filter((order) => order.status === 'DELIVERED').length,
    [orders]
  );
  const driverRoutePoints = useMemo(() => buildNearestRoutePoints(orders), [orders]);
  const routeLegs = useMemo(() => buildRouteLegs(driverRoutePoints), [driverRoutePoints]);
  const commonRouteUrl = useMemo(() => routeUrlForPoints(driverRoutePoints), [driverRoutePoints]);
  const showSection = (sectionId) => !activeSection || activeSection === sectionId;
  const statusChipLabel = loading
    ? 'Обновление...'
    : (activeDeliveries ? 'На маршруте' : 'Ожидание');
  const statusChipColor = activeDeliveries ? 'primary' : 'default';

  const showMessage = (message, severity = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const load = async () => {
    setLoading(true);
    try {
      const pageData = await getAssignedOrdersPage(token, { page: 0, size: ordersPageSize });
      const items = Array.isArray(pageData?.items) ? pageData.items : [];
      setOrders(items);
      setOrdersPage(Number.isInteger(pageData?.page) ? pageData.page : 0);
      setOrdersHasNext(Boolean(pageData?.hasNext));
      setOrdersTotalItems(Number.isFinite(pageData?.totalItems) ? pageData.totalItems : items.length);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить назначения', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    const unsubscribe = subscribeNotifications(token, {
      onNotification: (payload) => {
        showMessage(`Новое событие: ${payload.title || 'Уведомление'}`, 'info');
      }
    });
    return () => unsubscribe();
  }, [token]);

  const handleDelivered = async (orderId) => {
    setActionLoading(true);
    try {
      await markOrderDelivered(token, orderId);
      showMessage(`Заказ #${orderId} отмечен как доставленный`);
      await load();
    } catch (err) {
      showMessage(err.message || 'Не удалось обновить статус', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLoadMoreOrders = async () => {
    if (!ordersHasNext || ordersLoadingMore) {
      return;
    }
    setOrdersLoadingMore(true);
    try {
      const nextPage = ordersPage + 1;
      const pageData = await getAssignedOrdersPage(token, { page: nextPage, size: ordersPageSize });
      const items = Array.isArray(pageData?.items) ? pageData.items : [];
      setOrders((prev) => [...prev, ...items]);
      setOrdersPage(Number.isInteger(pageData?.page) ? pageData.page : nextPage);
      setOrdersHasNext(Boolean(pageData?.hasNext));
      setOrdersTotalItems(Number.isFinite(pageData?.totalItems) ? pageData.totalItems : ordersTotalItems);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить ещё заказы', 'error');
    } finally {
      setOrdersLoadingMore(false);
    }
  };

  const handleOpenRoute = () => {
    if (!commonRouteUrl) {
      showMessage('Координаты точки не указаны', 'warning');
      return;
    }
    window.open(commonRouteUrl, '_blank', 'noopener');
  };

  return (
    <Box sx={{ pb: 4 }}>
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        TransitionComponent={Slide}
        TransitionProps={{ direction: 'right' }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>

      {showSection('driver-orders') && (
        <Stack spacing={2.5}>
          <Paper sx={{ p: 2.5, borderRadius: 3 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
              <Box>
                <Typography variant="h5" fontWeight={800} gutterBottom>Мои доставки</Typography>
                <Typography variant="body2" color="text.secondary">
                  Активных: {activeDeliveries} · Завершено: {deliveredOrders}
                </Typography>
              </Box>
              <Chip icon={<LocalShippingIcon />} label={statusChipLabel} color={statusChipColor} />
            </Stack>
            <Stack direction="row" sx={{ mt: 1.5 }}>
                <Button
                  variant="outlined"
                  startIcon={<MapIcon />}
                  onClick={handleOpenRoute}
                  disabled={!commonRouteUrl}
                >
                  Открыть маршрут в OpenStreetMap
                </Button>
            </Stack>
            {!!routeLegs.length && (
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                flexWrap="wrap"
                sx={{ mt: 1 }}
              >
                {routeLegs.map((leg, index) => (
                  <Button
                    key={`${leg.toOrderId}-${index}`}
                    size="small"
                    variant="text"
                    component="a"
                    href={leg.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Этап {index + 1} · #{leg.toOrderId}
                  </Button>
                ))}
              </Stack>
            )}
          </Paper>

                    {!orders.length && !loading && (
            <Stack alignItems="center" spacing={2} sx={{ py: 6 }}>
              <InboxOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />
              <Typography color="text.secondary">У вас пока нет назначенных заказов</Typography>
              <Typography variant="caption" color="text.disabled">
                Заказы появятся после назначения логистом
              </Typography>
            </Stack>
          )}

          {loading && !orders.length && (
            <Stack spacing={2}>
              {[1, 2].map((i) => (
                <Card key={i} sx={{ borderRadius: 3 }}>
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Stack direction="row" alignItems="center" justifyContent="space-between">
                        <Skeleton variant="rounded" width={80} height={24} />
                        <Skeleton variant="text" width={40} />
                      </Stack>
                      <Skeleton variant="text" width="70%" height={32} />
                      <Skeleton variant="text" width="50%" height={20} />
                      <Stack direction="row" spacing={1.5}>
                        <Skeleton variant="rounded" width={140} height={48} />
                        <Skeleton variant="rounded" width={180} height={48} />
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          )}

          <Stack spacing={2}>
            {orders.map((order) => {
              const meta = statusMeta(order.status);
              return (
                <Card key={order.id} sx={{ borderRadius: 3 }}>
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Stack direction="row" alignItems="center" justifyContent="space-between">
                        <Chip label={meta.label} color={meta.color} />
                        <Typography variant="caption" color="text.secondary">#{order.id}</Typography>
                      </Stack>
                      <Typography variant="h6" fontWeight={600}>
                        {order.deliveryAddressText || 'Адрес не указан'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Клиент: {order.customerName || '—'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        ID заказа: {order.id}
                      </Typography>
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                        {order.status === 'ASSIGNED' && (
                          <Button
                            variant="contained"
                            size="large"
                            color="primary"
                            startIcon={<CheckIcon />}
                            onClick={() => handleDelivered(order.id)}
                            disabled={actionLoading}
                            sx={{ minHeight: 48 }}
                          >
                            Отметить доставленным
                          </Button>
                        )}
                        {order.status === 'DELIVERED' && (
                          <Button
                            variant="contained"
                            size="large"
                            color="success"
                            startIcon={<CheckCircleIcon />}
                            disabled
                            sx={{ minHeight: 48 }}
                          >
                            Доставка завершена
                          </Button>
                        )}
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              );
            })}
          </Stack>
          {ordersHasNext && (
            <Stack alignItems="center" spacing={1}>
              <Button
                variant="outlined"
                onClick={handleLoadMoreOrders}
                disabled={ordersLoadingMore}
              >
                {ordersLoadingMore ? 'Загрузка...' : 'Показать ещё'}
              </Button>
              <Typography variant="caption" color="text.secondary">
                Показано {orders.length} из {ordersTotalItems}
              </Typography>
            </Stack>
          )}
        </Stack>
      )}
    </Box>
  );
}
