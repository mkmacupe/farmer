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
import Divider from '@mui/material/Divider';
import Stepper from '@mui/material/Stepper';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import StepContent from '@mui/material/StepContent';
import Avatar from '@mui/material/Avatar';

import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CheckIcon from '@mui/icons-material/Check';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import Skeleton from '@mui/material/Skeleton';
import NavigationIcon from '@mui/icons-material/Navigation';
import PlaceIcon from '@mui/icons-material/Place';

const DEFAULT_DEPOT = {
  latitude: 53.8971270,
  longitude: 30.3320410,
  address: 'Могилёв, ул. Первомайская 31 (База)'
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

function legRouteUrl(fromLat, fromLon, toLat, toLon) {
  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${fromLat}%2C${fromLon}%3B${toLat}%2C${toLon}`;
}

function buildOrderedRoute(orders) {
  const depotLat = normalizeCoord(DEFAULT_DEPOT.latitude, -90, 90);
  const depotLon = normalizeCoord(DEFAULT_DEPOT.longitude, -180, 180);
  if (depotLat == null || depotLon == null) {
    return [];
  }

  // Нам нужны все назначенные и доставленные заказы, чтобы построить полную картину
  const allRelevant = orders.map((order) => {
    const lat = normalizeCoord(order.deliveryLatitude, -90, 90);
    const lon = normalizeCoord(order.deliveryLongitude, -180, 180);
    return {
      orderId: order.id,
      status: order.status,
      latitude: lat ?? depotLat,
      longitude: lon ?? depotLon,
      deliveryAddress: order.deliveryAddressText || 'Адрес не указан',
      customerName: order.customerName
    };
  });

  const remaining = [...allRelevant];
  const ordered = [];
  let previousLat = depotLat;
  let previousLon = depotLon;

  while (remaining.length) {
    let nearestIndex = 0;
    let nearestDistance = haversineKm(previousLat, previousLon, remaining[0].latitude, remaining[0].longitude);

    for (let index = 1; index < remaining.length; index += 1) {
      const candidate = remaining[index];
      const candidateDistance = haversineKm(previousLat, previousLon, candidate.latitude, candidate.longitude);
      if (candidateDistance < nearestDistance) {
        nearestDistance = candidateDistance;
        nearestIndex = index;
      }
    }

    const nextPoint = remaining.splice(nearestIndex, 1)[0];
    ordered.push({
      ...nextPoint,
      fromLat: previousLat,
      fromLon: previousLon,
      distanceKm: nearestDistance,
      url: legRouteUrl(previousLat, previousLon, nextPoint.latitude, nextPoint.longitude)
    });
    previousLat = nextPoint.latitude;
    previousLon = nextPoint.longitude;
  }

  return ordered;
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
  const deliveredOrdersCount = useMemo(
    () => orders.filter((order) => order.status === 'DELIVERED').length,
    [orders]
  );

  // Строим маршрут
  const fullRoute = useMemo(() => buildOrderedRoute(orders), [orders]);
  
  // Разделяем маршрут на активную часть и завершенную
  const activeRouteSteps = useMemo(() => fullRoute.filter(step => step.status === 'ASSIGNED'), [fullRoute]);
  const deliveredRouteSteps = useMemo(() => fullRoute.filter(step => step.status === 'DELIVERED'), [fullRoute]);

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
        <Stack spacing={3}>
          <Paper sx={{ p: 3, borderRadius: 4, border: '1px solid', borderColor: 'divider' }} elevation={0}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
              <Box>
                <Typography variant="h5" fontWeight={800} gutterBottom>Маршрутный лист</Typography>
                <Typography variant="body2" color="text.secondary">
                  Всего точек: {fullRoute.length} · Активных: {activeDeliveries}
                </Typography>
              </Box>
              <Chip icon={<LocalShippingIcon />} label={statusChipLabel} color={statusChipColor} sx={{ fontWeight: 700 }} />
            </Stack>

            {!!activeRouteSteps.length && (
              <Box sx={{ mt: 4 }}>
                <Typography variant="subtitle2" color="primary" fontWeight={700} sx={{ mb: 2, textTransform: 'uppercase', letterSpacing: 1 }}>
                  Текущие этапы
                </Typography>
                <Stepper orientation="vertical" nonLinear sx={{ ml: 1 }}>
                  {activeRouteSteps.map((step, index) => (
                    <Step key={step.orderId} active expanded>
                      <StepLabel
                        StepIconComponent={() => (
                          <Avatar sx={{ bgcolor: 'primary.main', width: 28, height: 28, fontSize: 14, fontWeight: 700 }}>
                            {index + 1}
                          </Avatar>
                        )}
                      >
                        <Typography variant="subtitle1" fontWeight={700}>
                          Заказ #{step.orderId}
                        </Typography>
                      </StepLabel>
                      <StepContent>
                        <Box sx={{ mb: 2, mt: 1 }}>
                          <Typography variant="body2" color="text.secondary" gutterBottom>
                            {step.deliveryAddress}
                          </Typography>
                          <Typography variant="caption" display="block" sx={{ mb: 2 }}>
                            Расстояние: ~{step.distanceKm.toFixed(2)} км
                          </Typography>
                          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                            <Button
                              variant="contained"
                              size="medium"
                              startIcon={<CheckIcon />}
                              onClick={() => handleDelivered(step.orderId)}
                              disabled={actionLoading}
                              sx={{ borderRadius: 2, px: 3 }}
                            >
                              Доставлено
                            </Button>
                            <Button
                              variant="outlined"
                              size="medium"
                              startIcon={<NavigationIcon />}
                              component="a"
                              href={step.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              sx={{ borderRadius: 2 }}
                            >
                              Навигатор
                            </Button>
                          </Stack>
                        </Box>
                      </StepContent>
                    </Step>
                  ))}
                </Stepper>
              </Box>
            )}

            {!!deliveredRouteSteps.length && (
              <Box sx={{ mt: 4 }}>
                <Divider sx={{ mb: 3 }}>
                  <Chip label="Завершено" size="small" variant="outlined" />
                </Divider>
                <Stack spacing={1.5}>
                  {deliveredRouteSteps.map((step) => (
                    <Paper 
                      key={step.orderId} 
                      variant="outlined" 
                      sx={{ 
                        p: 1.5, 
                        borderRadius: 2, 
                        bgcolor: 'action.hover',
                        borderStyle: 'dashed',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between'
                      }}
                    >
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <CheckCircleIcon color="success" fontSize="small" />
                        <Box>
                          <Typography variant="body2" fontWeight={600} color="text.secondary">
                            Заказ #{step.orderId}
                          </Typography>
                          <Typography variant="caption" color="text.disabled">
                            {step.deliveryAddress}
                          </Typography>
                        </Box>
                      </Box>
                      <PlaceIcon sx={{ color: 'text.disabled', opacity: 0.5 }} />
                    </Paper>
                  ))}
                </Stack>
              </Box>
            )}

            {!fullRoute.length && !loading && (
              <Stack alignItems="center" spacing={2} sx={{ py: 6 }}>
                <InboxOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />
                <Typography color="text.secondary" fontWeight={600}>Назначений пока нет</Typography>
                <Typography variant="caption" color="text.disabled">
                  Маршрут появится, когда логист распределит заказы
                </Typography>
              </Stack>
            )}
          </Paper>

          {loading && !orders.length && (
            <Stack spacing={2}>
              {[1, 2].map((i) => (
                <Card key={i} sx={{ borderRadius: 4 }}>
                  <CardContent sx={{ p: 3 }}>
                    <Stack spacing={2}>
                      <Skeleton variant="rounded" width={120} height={28} />
                      <Skeleton variant="text" width="80%" height={32} />
                      <Skeleton variant="text" width="60%" height={24} />
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          )}
          
          {ordersHasNext && (
            <Stack alignItems="center" spacing={1} sx={{ mt: 2 }}>
              <Button
                variant="outlined"
                onClick={handleLoadMoreOrders}
                disabled={ordersLoadingMore}
                sx={{ borderRadius: 2 }}
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
