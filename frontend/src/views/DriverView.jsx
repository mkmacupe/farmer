import { useEffect, useMemo, useState } from 'react';
import { getAssignedOrders, markOrderDelivered, subscribeNotifications } from '../api.js';

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

function normalizeCoord(value, min, max) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric < min || numeric > max) {
    return null;
  }
  return numeric;
}

function mapUrl(latitude, longitude) {
  const lat = normalizeCoord(latitude, -90, 90);
  const lon = normalizeCoord(longitude, -180, 180);
  if (lat == null || lon == null) return '';
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`;
}

const SHARED_DRIVER_BASE = { lat: 53.8971270, lon: 30.3320410 };

function resolveDriverBase(assignedDriverName, assignedDriverId) {
  const numericId = Number(assignedDriverId);
  if (assignedDriverName == null && !Number.isInteger(numericId)) {
    return null;
  }
  return SHARED_DRIVER_BASE;
}

function routeUrl(fromLat, fromLon, toLat, toLon) {
  const originLat = normalizeCoord(fromLat, -90, 90);
  const originLon = normalizeCoord(fromLon, -180, 180);
  const destLat = normalizeCoord(toLat, -90, 90);
  const destLon = normalizeCoord(toLon, -180, 180);
  if (originLat == null || originLon == null || destLat == null || destLon == null) {
    return '';
  }
  const routeParam = encodeURIComponent(`${originLat},${originLon};${destLat},${destLon}`);
  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${routeParam}#map=13/${originLat}/${originLon}`;
}

function statusMeta(status) {
  if (status === 'DELIVERED') {
    return { label: 'Доставлен', color: 'success' };
  }
  return { label: 'Назначен', color: 'warning' };
}

export default function DriverView({ token, activeSection }) {
  const [orders, setOrders] = useState([]);

  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });

  const activeDeliveries = useMemo(
    () => orders.filter((order) => order.status === 'ASSIGNED').length,
    [orders]
  );
  const deliveredOrders = useMemo(
    () => orders.filter((order) => order.status === 'DELIVERED').length,
    [orders]
  );
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
      setOrders(await getAssignedOrders(token));
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

  const handleOpenRoute = (order) => {
    const fallbackUrl = mapUrl(order.deliveryLatitude, order.deliveryLongitude);
    if (!fallbackUrl) {
      showMessage('Координаты точки не указаны', 'warning');
      return;
    }

    const driverBase = resolveDriverBase(order.assignedDriverName, order.assignedDriverId);
    const route = driverBase
      ? routeUrl(driverBase.lat, driverBase.lon, order.deliveryLatitude, order.deliveryLongitude)
      : '';
    window.open(route || fallbackUrl, '_blank', 'noopener');
    if (!driverBase) {
      showMessage('Для водителя не задана базовая точка, открыт только адрес доставки', 'warning');
    }
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
                        <Button
                          variant="outlined"
                          size="large"
                          startIcon={<MapIcon />}
                          onClick={() => handleOpenRoute(order)}
                          disabled={!mapUrl(order.deliveryLatitude, order.deliveryLongitude)}
                          sx={{ minHeight: 48 }}
                        >
                          Открыть карту
                        </Button>
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
        </Stack>
      )}
    </Box>
  );
}
