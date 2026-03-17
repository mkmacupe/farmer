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
import Stepper from '@mui/material/Stepper';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import StepContent from '@mui/material/StepContent';
import Avatar from '@mui/material/Avatar';

import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import Skeleton from '@mui/material/Skeleton';
import NavigationIcon from '@mui/icons-material/Navigation';

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

function normalizePositiveInt(value) {
  const numeric = Number(value);
  if (!Number.isInteger(numeric) || numeric <= 0) {
    return null;
  }
  return numeric;
}

function legRouteUrl(fromLat, fromLon, toLat, toLon) {
  return `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${fromLat}%2C${fromLon}%3B${toLat}%2C${toLon}`;
}

function formatOrderCount(count) {
  const value = Number(count) || 0;
  const mod10 = value % 10;
  const mod100 = value % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${value} заказ`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${value} заказа`;
  }
  return `${value} заказов`;
}

function isSameStop(left, right) {
  if (!left || !right) {
    return false;
  }
  return left.routeTripNumber === right.routeTripNumber
    && left.deliveryAddress === right.deliveryAddress
    && Math.abs(left.latitude - right.latitude) <= 0.000001
    && Math.abs(left.longitude - right.longitude) <= 0.000001;
}

export function buildRouteStops(routeSteps) {
  if (!Array.isArray(routeSteps) || routeSteps.length === 0) {
    return [];
  }

  const stops = [];
  for (const step of routeSteps) {
    const currentStop = stops.at(-1);
    if (currentStop && isSameStop(currentStop.primaryOrder, step)) {
      currentStop.orders.push(step);
      continue;
    }

    stops.push({
      key: `${step.routeTripNumber ?? 'route'}-${step.routeStopSequence ?? step.orderId}`,
      displayStopNumber: stops.length + 1,
      primaryOrder: step,
      orders: [step]
    });
  }
  return stops;
}

function isDeliveredOrder(order) {
  return order?.status === 'DELIVERED';
}

function compareRouteSteps(left, right) {
  return (left.routeStopSequence ?? Number.MAX_SAFE_INTEGER) - (right.routeStopSequence ?? Number.MAX_SAFE_INTEGER)
    || (left.routeTripNumber ?? Number.MAX_SAFE_INTEGER) - (right.routeTripNumber ?? Number.MAX_SAFE_INTEGER)
    || left.orderId - right.orderId;
}

function compareOrdersForDisplay(left, right) {
  const leftDelivered = isDeliveredOrder(left);
  const rightDelivered = isDeliveredOrder(right);
  if (leftDelivered !== rightDelivered) {
    return leftDelivered ? 1 : -1;
  }
  if (!leftDelivered) {
    return compareRouteSteps(left, right);
  }
  return (left.deliveredAtMs ?? 0) - (right.deliveredAtMs ?? 0)
    || compareRouteSteps(left, right);
}

function stopHasActiveOrders(stop) {
  return stop.orders.some((order) => !isDeliveredOrder(order));
}

function stopLastDeliveredAtMs(stop) {
  return stop.orders.reduce(
    (maxValue, order) => Math.max(maxValue, order.deliveredAtMs ?? 0),
    0
  );
}

export function buildDisplayRouteStops(routeStops) {
  if (!Array.isArray(routeStops) || routeStops.length === 0) {
    return [];
  }

  const normalizedStops = routeStops.map((stop) => ({
    ...stop,
    orders: [...stop.orders].sort(compareOrdersForDisplay)
  }));

  return normalizedStops.sort((left, right) => {
    const leftHasActiveOrders = stopHasActiveOrders(left);
    const rightHasActiveOrders = stopHasActiveOrders(right);
    if (leftHasActiveOrders !== rightHasActiveOrders) {
      return leftHasActiveOrders ? -1 : 1;
    }
    if (leftHasActiveOrders) {
      return compareRouteSteps(left.primaryOrder, right.primaryOrder);
    }
    return stopLastDeliveredAtMs(left) - stopLastDeliveredAtMs(right)
      || compareRouteSteps(left.primaryOrder, right.primaryOrder);
  });
}

export function buildOrderedRoute(orders) {
  const depotLat = normalizeCoord(DEFAULT_DEPOT.latitude, -90, 90);
  const depotLon = normalizeCoord(DEFAULT_DEPOT.longitude, -180, 180);
  if (depotLat == null || depotLon == null) {
    return [];
  }

  const normalizedOrders = orders.map((order) => {
    const lat = normalizeCoord(order.deliveryLatitude, -90, 90) ?? depotLat;
    const lon = normalizeCoord(order.deliveryLongitude, -180, 180) ?? depotLon;
    return {
      orderId: order.id,
      status: order.status,
      latitude: lat,
      longitude: lon,
      deliveryAddress: order.deliveryAddressText || 'Адрес не указан',
      customerName: order.customerName,
      routeTripNumber: normalizePositiveInt(order.routeTripNumber),
      routeStopSequence: normalizePositiveInt(order.routeStopSequence),
      deliveredAtMs: Date.parse(order.deliveredAt || 0) || 0,
      assignedAtMs: Date.parse(order.assignedAt || 0) || 0,
      createdAtMs: Date.parse(order.createdAt || 0) || 0
    };
  });

  const ordered = [...normalizedOrders].sort((left, right) => {
    const leftHasRoute = left.routeStopSequence != null;
    const rightHasRoute = right.routeStopSequence != null;
    if (leftHasRoute && rightHasRoute) {
      return left.routeStopSequence - right.routeStopSequence
        || (left.routeTripNumber ?? Number.MAX_SAFE_INTEGER) - (right.routeTripNumber ?? Number.MAX_SAFE_INTEGER)
        || left.orderId - right.orderId;
    }
    if (leftHasRoute !== rightHasRoute) {
      return leftHasRoute ? -1 : 1;
    }
    return left.assignedAtMs - right.assignedAtMs
      || right.createdAtMs - left.createdAtMs
      || left.orderId - right.orderId;
  });

  let previousLat = depotLat;
  let previousLon = depotLon;
  let previousTripNumber = null;
  return ordered.map((step, index) => {
    const startsNewTrip = index === 0
      || (
        step.routeTripNumber != null
        && previousTripNumber != null
        && step.routeTripNumber !== previousTripNumber
      );
    const fromLat = startsNewTrip ? depotLat : previousLat;
    const fromLon = startsNewTrip ? depotLon : previousLon;
    const distanceKm = haversineKm(fromLat, fromLon, step.latitude, step.longitude);
    const routeStep = {
      ...step,
      fromLat,
      fromLon,
      distanceKm,
      fromDepot: startsNewTrip,
      url: legRouteUrl(fromLat, fromLon, step.latitude, step.longitude)
    };
    previousLat = step.latitude;
    previousLon = step.longitude;
    previousTripNumber = step.routeTripNumber;
    return routeStep;
  });
}

export default function DriverView({ token, activeSection }) {
  const [orders, setOrders] = useState([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasNext, setOrdersHasNext] = useState(false);
  const [ordersTotalItems, setOrdersTotalItems] = useState(0);
  const [ordersLoadingMore, setOrdersLoadingMore] = useState(false);

  const [loading, setLoading] = useState(false);
  const [pendingOrderIds, setPendingOrderIds] = useState([]);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });
  const ordersPageSize = 50;

  const activeDeliveries = useMemo(
    () => orders.filter((order) => order.status === 'ASSIGNED').length,
    [orders]
  );

  const fullRoute = useMemo(() => buildOrderedRoute(orders), [orders]);
  const routeStops = useMemo(() => buildRouteStops(fullRoute), [fullRoute]);
  const displayedRouteStops = useMemo(() => buildDisplayRouteStops(routeStops), [routeStops]);

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
        void load();
      }
    });
    return () => unsubscribe();
  }, [token]);

  const handleDelivered = async (orderId) => {
    setPendingOrderIds((prev) => (prev.includes(orderId) ? prev : [...prev, orderId]));
    try {
      await markOrderDelivered(token, orderId);
      showMessage(`Заказ #${orderId} отмечен как доставленный`);
      await load();
    } catch (err) {
      showMessage(err.message || 'Не удалось обновить статус', 'error');
    } finally {
      setPendingOrderIds((prev) => prev.filter((id) => id !== orderId));
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
                  Остановок: {routeStops.length} · Заказов: {fullRoute.length} · Активных заказов: {activeDeliveries}
                </Typography>
              </Box>
              <Chip
                icon={<LocalShippingIcon />}
                label={statusChipLabel}
                color={statusChipColor}
                sx={{
                  fontWeight: 700,
                  bgcolor: activeDeliveries ? 'primary.dark' : 'grey.200',
                  color: activeDeliveries ? 'primary.contrastText' : 'text.primary',
                  '& .MuiChip-icon': {
                    color: activeDeliveries ? 'inherit' : 'text.secondary'
                  }
                }}
              />
            </Stack>

            {!!displayedRouteStops.length && (
              <Box sx={{ mt: 4 }}>
                <Typography variant="subtitle2" color="primary" fontWeight={700} sx={{ mb: 2, textTransform: 'uppercase', letterSpacing: 1 }}>
                  Последовательность доставок
                </Typography>
                <Stepper orientation="vertical" nonLinear sx={{ ml: 1 }}>
                  {displayedRouteStops.map((stop) => {
                    const step = stop.primaryOrder;
                    const displayStopNumber = stop.displayStopNumber ?? 1;
                    const hasAssignedOrders = stop.orders.some((order) => order.status === 'ASSIGNED');
                    const allDelivered = stop.orders.every((order) => order.status === 'DELIVERED');
                    return (
                    <Step key={stop.key} active={hasAssignedOrders} completed={allDelivered} expanded>
                      <StepLabel
                        StepIconComponent={() => (
                          <Avatar sx={{
                            bgcolor: allDelivered ? 'success.main' : 'primary.main',
                            width: 28,
                            height: 28,
                            fontSize: 14,
                            fontWeight: 700
                          }}>
                            {displayStopNumber}
                          </Avatar>
                        )}
                      >
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="subtitle1" fontWeight={700}>
                            Остановка {displayStopNumber}
                          </Typography>
                          {step.routeTripNumber != null && (
                            <Chip size="small" variant="outlined" label={`Рейс ${step.routeTripNumber}`} />
                          )}
                          <Chip
                            size="small"
                            variant="outlined"
                            label={formatOrderCount(stop.orders.length)}
                          />
                          {allDelivered && (
                            <Chip size="small" color="success" label="Доставлен" />
                          )}
                        </Stack>
                      </StepLabel>
                      <StepContent>
                        <Box sx={{ mb: 2, mt: 1 }}>
                          <Typography variant="body2" color="text.secondary" gutterBottom>
                            {step.deliveryAddress}
                          </Typography>
                          <Typography variant="caption" display="block" sx={{ mb: 2 }}>
                            {step.fromDepot
                              ? `Старт от склада · ~${step.distanceKm.toFixed(2)} км`
                              : `От предыдущей точки · ~${step.distanceKm.toFixed(2)} км`}
                          </Typography>
                          <Stack spacing={1.5} sx={{ mb: 2 }}>
                            {stop.orders.map((order) => {
                              const isPending = pendingOrderIds.includes(order.orderId);
                              return (
                                <Paper
                                  key={order.orderId}
                                  variant="outlined"
                                  sx={{
                                    p: 1.5,
                                    borderRadius: 2,
                                    bgcolor: order.status === 'DELIVERED' ? 'action.hover' : 'background.paper'
                                  }}
                                >
                                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }} justifyContent="space-between">
                                    <Stack spacing={0.5}>
                                      <Typography variant="body2" fontWeight={700}>
                                        Заказ #{order.orderId}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        {order.status === 'DELIVERED' ? 'Статус: доставлен' : 'Статус: ожидает доставки'}
                                      </Typography>
                                    </Stack>
                                    {order.status === 'ASSIGNED' ? (
                                      <Button
                                        variant="contained"
                                        size="medium"
                                        startIcon={<DoneAllIcon />}
                                        onClick={() => handleDelivered(order.orderId)}
                                        disabled={isPending}
                                        sx={{ borderRadius: 2, px: 3 }}
                                      >
                                        {isPending ? 'Отмечаем...' : 'Отметить доставленным'}
                                      </Button>
                                    ) : (
                                      <Button
                                        variant="outlined"
                                        size="medium"
                                        startIcon={<CheckCircleIcon />}
                                        disabled
                                        sx={{ borderRadius: 2 }}
                                      >
                                        Уже доставлен
                                      </Button>
                                    )}
                                  </Stack>
                                </Paper>
                              );
                            })}
                          </Stack>
                          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
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
                    );
                  })}
                </Stepper>
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
