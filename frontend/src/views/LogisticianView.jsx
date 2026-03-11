import {
  Suspense,
  lazy,
  memo,
  startTransition,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState
} from 'react';
import {
  approveAutoAssignOrders,
  assignOrderDriver,
  getAllOrdersPage,
  getDrivers,
  getOrderTimeline,
  previewAutoAssignOrders,
  subscribeNotifications
} from '../api.js';
import OrdersTable from '../components/OrdersTable.jsx';

import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Avatar from '@mui/material/Avatar';
import Slide from '@mui/material/Slide';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

import AssignmentIcon from '@mui/icons-material/Assignment';
import HistoryIcon from '@mui/icons-material/History';
import RouteOutlinedIcon from '@mui/icons-material/RouteOutlined';
import NotificationsIcon from '@mui/icons-material/Notifications';
import { useStableEvent } from '../utils/useStableEvent.js';

const RoutePlanMap = lazy(() => import('../components/RoutePlanMap.jsx'));

function statusLabel(status) {
  const labels = {
    CREATED: 'Создан',
    APPROVED: 'Одобрен',
    ASSIGNED: 'Назначен',
    DELIVERED: 'Доставлен'
  };
  return labels[status] || status || '-';
}

function formatDateTime(value) {
  if (!value) return '';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return '';
  return parsed.toLocaleString('ru-RU');
}

const MetricCard = memo(function MetricCard({ title, value, icon, color }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%' }}>
      <CardContent sx={{ display: 'flex', alignItems: 'center', p: 3, '&:last-child': { pb: 3 } }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            {title}
          </Typography>
          <Typography variant="h4" fontWeight="bold">
            {value}
          </Typography>
        </Box>
        <Avatar sx={{ bgcolor: `${color}.light`, color: `${color}.main`, width: 56, height: 56 }}>
          {icon}
        </Avatar>
      </CardContent>
    </Card>
  );
});

const ROUTE_COLORS = ['#5a7fa8', '#b18a52', '#4f8a6d', '#8a78a5', '#b07a7a'];

function routeColor(routeIndex) {
  return ROUTE_COLORS[routeIndex % ROUTE_COLORS.length];
}

function routeTripLoad(route) {
  const trips = Array.isArray(route?.trips) ? route.trips : [];
  if (!trips.length) {
    return {
      tripCount: route?.points?.length ? 1 : 0,
      maxTripWeightKg: Number(route?.totalWeightKg) || 0,
      maxTripVolumeM3: Number(route?.totalVolumeM3) || 0
    };
  }
  return {
    tripCount: trips.length,
    maxTripWeightKg: Math.max(...trips.map((trip) => Number(trip?.totalWeightKg) || 0)),
    maxTripVolumeM3: Math.max(...trips.map((trip) => Number(trip?.totalVolumeM3) || 0))
  };
}

function tripCountLabel(count) {
  const normalized = Number.isFinite(Number(count)) ? Math.max(0, Math.trunc(Number(count))) : 0;
  const mod10 = normalized % 10;
  const mod100 = normalized % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${normalized} рейс`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${normalized} рейса`;
  }
  return `${normalized} рейсов`;
}

export default function LogisticianView({ token, activeSection }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [orders, setOrders] = useState([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasNext, setOrdersHasNext] = useState(false);
  const [ordersTotalItems, setOrdersTotalItems] = useState(0);
  const [ordersLoadingMore, setOrdersLoadingMore] = useState(false);
  const [drivers, setDrivers] = useState([]);
  const [driversLoaded, setDriversLoaded] = useState(false);
  const [driverSelection, setDriverSelection] = useState({});
  const [notifications, setNotifications] = useState([]);
  
  // UI State
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });
  const [routePlan, setRoutePlan] = useState(null);
  const [routePlanOpen, setRoutePlanOpen] = useState(false);
  const [routePlanLoading, setRoutePlanLoading] = useState(false);

  // Timeline Dialog
  const [timelineOpen, setTimelineOpen] = useState(false);
  const [selectedTimelineOrderId, setSelectedTimelineOrderId] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const ordersRefreshTimerRef = useRef(null);
  const ordersPageSize = 100;

  const pendingAssignmentCount = useMemo(() => {
    const loadedApproved = orders.filter((order) => order.status === 'APPROVED').length;
    return ordersTotalItems > loadedApproved ? ordersTotalItems : loadedApproved;
  }, [orders, ordersTotalItems]);
  const latestNotifications = useMemo(() => notifications.slice(0, 4), [notifications]);
  const routePlanAssignments = useMemo(() => {
    if (!routePlan || !Array.isArray(routePlan.routes)) {
      return [];
    }
    return routePlan.routes.flatMap((route) => {
      if (!route || !Array.isArray(route.points)) return [];
      return route.points.map((point) => ({
        orderId: point.orderId,
        driverId: route.driverId,
        tripNumber: point.tripNumber || 1,
        stopSequence: point.stopSequence,
        estimatedDistanceKm: point.distanceFromPreviousKm
      }));
    });
  }, [routePlan]);
  const showSection = (sectionId) => !activeSection || activeSection === sectionId;

  const showMessage = (message, severity = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const applyOrdersPage = (ordersPageData, fallbackPage = 0) => {
    const items = Array.isArray(ordersPageData?.items) ? ordersPageData.items : [];
    setOrders(items);
    setOrdersPage(Number.isInteger(ordersPageData?.page) ? ordersPageData.page : fallbackPage);
    setOrdersHasNext(Boolean(ordersPageData?.hasNext));
    setOrdersTotalItems(
      Number.isFinite(ordersPageData?.totalItems) ? ordersPageData.totalItems : items.length
    );
  };

  const loadData = async ({ includeDrivers = false, showLoading = true } = {}) => {
    if (showLoading) {
      setLoading(true);
    }
    try {
      const [ordersData, driversData] = await Promise.all([
        getAllOrdersPage(token, { page: 0, size: ordersPageSize }),
        includeDrivers || !driversLoaded ? getDrivers(token) : Promise.resolve(null)
      ]);
      applyOrdersPage(ordersData);
      if (driversData) {
        setDrivers(driversData);
        setDriversLoaded(true);
      }
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить данные', 'error');
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  };

  const refreshOrders = async () => {
    await loadData({ showLoading: false });
  };

  const scheduleOrdersRefresh = useStableEvent(() => {
    if (ordersRefreshTimerRef.current !== null) {
      window.clearTimeout(ordersRefreshTimerRef.current);
    }
    ordersRefreshTimerRef.current = window.setTimeout(() => {
      ordersRefreshTimerRef.current = null;
      void loadData({ showLoading: false });
    }, 600);
  });

  const handleRealtimeNotification = useStableEvent((payload) => {
    startTransition(() => {
      setNotifications((prev) => [payload, ...prev].slice(0, 10));
    });
    showMessage(`Новое событие: ${payload.title || 'Уведомление'}`, 'info');
    scheduleOrdersRefresh();
  });

  useEffect(() => {
    setDriversLoaded(false);
    void loadData({ includeDrivers: true });
    const unsubscribe = subscribeNotifications(token, {
      onNotification: handleRealtimeNotification
    });
    return () => {
      unsubscribe();
      if (ordersRefreshTimerRef.current !== null) {
        window.clearTimeout(ordersRefreshTimerRef.current);
        ordersRefreshTimerRef.current = null;
      }
    };
  }, [token]);

  const handleAssign = useCallback(async (orderId) => {
    const driverId = Number(driverSelection[orderId]);
    if (!driverId) {
      return showMessage('Выберите водителя перед назначением', 'warning');
    }

    setActionLoading(true);
    try {
      await assignOrderDriver(token, orderId, driverId);
      showMessage(`Водитель назначен на заказ #${orderId}`);
      await refreshOrders();
    } catch (err) {
      showMessage(err.message || 'Не удалось назначить водителя', 'error');
    } finally {
      setActionLoading(false);
    }
  }, [driverSelection, token]);

  const handleLoadMoreOrders = async () => {
    if (!ordersHasNext || ordersLoadingMore) {
      return;
    }
    setOrdersLoadingMore(true);
    try {
      const nextPage = ordersPage + 1;
      const ordersPageData = await getAllOrdersPage(token, { page: nextPage, size: ordersPageSize });
      const items = Array.isArray(ordersPageData?.items) ? ordersPageData.items : [];
      setOrders((prev) => [...prev, ...items]);
      setOrdersPage(Number.isInteger(ordersPageData?.page) ? ordersPageData.page : nextPage);
      setOrdersHasNext(Boolean(ordersPageData?.hasNext));
      setOrdersTotalItems((prev) => (
        Number.isFinite(ordersPageData?.totalItems) ? ordersPageData.totalItems : prev
      ));
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить ещё заказы', 'error');
    } finally {
      setOrdersLoadingMore(false);
    }
  };

  const handleAutoAssign = async () => {
    setActionLoading(true);
    setRoutePlan(null);
    setRoutePlanOpen(true);
    setRoutePlanLoading(true);
    try {
      const result = await previewAutoAssignOrders(token);
      if (result.totalApprovedOrders === 0) {
        setRoutePlanOpen(false);
        showMessage('Нет одобренных заказов для распределения', 'info');
      } else if (result.plannedOrders === 0) {
        setRoutePlanOpen(false);
        showMessage('Не удалось построить маршрутный план для текущего набора заказов', 'warning');
      } else {
        setRoutePlan(result);
        setRoutePlanOpen(true);
      }
    } catch (err) {
      setRoutePlanOpen(false);
      setRoutePlan(null);
      showMessage(err.message || 'Не удалось выполнить автоназначение', 'error');
    } finally {
      setRoutePlanLoading(false);
      setActionLoading(false);
    }
  };

  const handleApproveRoutePlan = async () => {
    if (!routePlanAssignments.length) {
      showMessage('План пуст: нет точек для назначения', 'warning');
      return;
    }
    setActionLoading(true);
    try {
      const result = await approveAutoAssignOrders(token, routePlanAssignments);
      showMessage(
        `Автораспределено: ${result.assignedOrders}/${result.totalApprovedOrders}, ` +
        `оценка пути ${result.estimatedTotalDistanceKm} км`
      );
      const assignedOrderIds = new Set(
        Array.isArray(result.assignments)
          ? result.assignments.map((item) => item.orderId)
          : routePlanAssignments.map((item) => item.orderId)
      );
      if (assignedOrderIds.size) {
        setOrders((prev) => prev.filter((order) => !assignedOrderIds.has(order.id)));
        setOrdersTotalItems((prev) => Math.max(0, prev - assignedOrderIds.size));
        setDriverSelection((prev) => {
          const next = { ...prev };
          assignedOrderIds.forEach((orderId) => {
            delete next[orderId];
          });
          return next;
        });
      }
      setRoutePlanOpen(false);
      setRoutePlan(null);
      void refreshOrders();
    } catch (err) {
      showMessage(err.message || 'Не удалось применить маршрутный план', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectRoutePlan = () => {
    if (routePlanLoading) {
      return;
    }
    setRoutePlanOpen(false);
    setRoutePlan(null);
    showMessage('Маршрутный план отклонен, назначения не сохранены', 'info');
  };

  const handleLoadTimeline = useCallback(async (orderId) => {
    setActionLoading(true);
    try {
      const data = await getOrderTimeline(token, orderId);
      setSelectedTimelineOrderId(orderId);
      setTimeline(data);
      setTimelineOpen(true);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить таймлайн', 'error');
    } finally {
      setActionLoading(false);
    }
  }, [token]);

  const renderOrderActions = useCallback((order) => (
    <Stack
      direction="column"
      spacing={1}
      alignItems="stretch"
      sx={{ minWidth: 220 }}
    >
      {order.status === 'APPROVED' && (
        <>
          <TextField
            select
            size="small"
            value={driverSelection[order.id] || ''}
            onChange={(e) => setDriverSelection((prev) => ({ ...prev, [order.id]: e.target.value }))}
            fullWidth
            disabled={actionLoading}
            data-testid={`driver-select-${order.id}`}
            SelectProps={{
              displayEmpty: true,
              inputProps: {
                'aria-label': `Выбор водителя для заказа #${order.id}`
              }
            }}
          >
            <MenuItem value="" disabled>Выберите водителя</MenuItem>
            {drivers.map((driver) => (
              <MenuItem key={driver.id} value={driver.id}>{driver.fullName}</MenuItem>
            ))}
          </TextField>
          <Button
            variant="contained"
            size="small"
            onClick={() => handleAssign(order.id)}
            disabled={actionLoading}
            fullWidth
          >
            Назначить
          </Button>
        </>
      )}
      <Button
        size="small"
        variant="outlined"
        startIcon={<HistoryIcon />}
        onClick={() => handleLoadTimeline(order.id)}
        disabled={actionLoading}
        fullWidth
      >
        История
      </Button>
    </Stack>
  ), [actionLoading, driverSelection, drivers, handleAssign, handleLoadTimeline]);

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

      {showSection('logistic-orders') && (
        <Box>
          <Box
            sx={{
              mb: 3,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: { xs: 'flex-start', sm: 'center' },
              flexDirection: { xs: 'column', sm: 'row' },
              gap: 1.5
            }}
          >
            <div>
               <Typography variant="h5" fontWeight="bold" gutterBottom>Логистика и назначения</Typography>
               <Typography variant="body2" color="text.secondary">
                 Отображаются только одобренные менеджером заявки.
               </Typography>
            </div>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Button
                variant="contained"
                onClick={handleAutoAssign}
                disabled={loading || actionLoading || pendingAssignmentCount === 0}
                data-testid="auto-assign-button"
              >
                Транспортная задача
              </Button>
              {loading && <CircularProgress size={24} />}
            </Stack>
          </Box>

          <Grid container spacing={3} sx={{ mb: 4 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
               <MetricCard 
                 title="К назначению" 
                 value={pendingAssignmentCount} 
                 icon={<AssignmentIcon />} 
                 color="warning" 
               />
            </Grid>
          </Grid>

          <OrdersTable
            orders={orders}
            loading={loading}
            compactView
            emptyText="Заказы отсутствуют."
            actionRenderer={renderOrderActions}
            maxRendered={orders.length}
          />

          {ordersHasNext && (
            <Stack alignItems="center" spacing={1} sx={{ mt: 2 }}>
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

          {!!latestNotifications.length && (
            <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mt: 4 }}>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center" gap={1}>
                <NotificationsIcon color="action" /> Последние уведомления
              </Typography>
              <Stack spacing={1}>
                {latestNotifications.map((notif, idx) => {
                  if (!notif) return null;
                  return (
                    <Alert
                      key={`${notif.createdAt || 'event'}-${notif.title || 'Событие'}-${notif.orderId || idx}`}
                      severity="info"
                      icon={false}
                      sx={{ py: 0 }}
                    >
                      <Typography variant="subtitle2" component="span" fontWeight="bold">
                        {notif.title || 'Событие'}: 
                      </Typography>
                      {' '}{notif.message} — <Typography variant="caption" color="text.secondary">{formatDateTime(notif.createdAt)}</Typography>
                    </Alert>
                  );
                })}
              </Stack>
            </Paper>
          )}
        </Box>
      )}

      <Dialog
        open={routePlanOpen}
        onClose={actionLoading || routePlanLoading ? undefined : handleRejectRoutePlan}
        maxWidth="md"
        fullWidth
        fullScreen={isMobile}
        PaperProps={{
          sx: {
            overflow: 'hidden',
            maxHeight: { xs: '92vh', sm: '88vh' }
          }
        }}
      >
        <DialogTitle data-testid="auto-assign-dialog-title">Предпросмотр транспортной задачи</DialogTitle>
        <DialogContent dividers sx={{ overflow: 'hidden', py: 1.5 }}>
          {routePlanLoading ? (
            <Stack
              alignItems="center"
              justifyContent="center"
              sx={{ minHeight: { xs: 260, sm: 340 }, textAlign: 'center' }}
            >
              <CircularProgress size={34} />
            </Stack>
          ) : routePlan ? (
            <Stack spacing={1.5}>
              <Alert severity="info" icon={<RouteOutlinedIcon fontSize="inherit" />}>
                <strong>Старт для всех 3 водителей:</strong> {routePlan.depotLabel}. План можно
                одобрить или отклонить без сохранения.
              </Alert>
              <Grid container spacing={1.25}>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Заказов в плане</Typography>
                    <Typography variant="h6" fontWeight={700}>
                      {routePlan.plannedOrders}/{routePlan.totalApprovedOrders}
                    </Typography>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Вне плана</Typography>
                    <Typography variant="h6" fontWeight={700}>{routePlan.unplannedOrders}</Typography>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Километраж</Typography>
                    <Typography variant="h6" fontWeight={700}>{routePlan.estimatedTotalDistanceKm} км</Typography>
                  </Paper>
                </Grid>
              </Grid>

              <Suspense
                fallback={
                  <Paper
                    variant="outlined"
                    sx={{
                      height: { xs: 210, md: 250 },
                      borderRadius: 2,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}
                  >
                    <CircularProgress size={26} />
                  </Paper>
                }
              >
                <RoutePlanMap plan={routePlan} />
              </Suspense>

              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
                  Водители и их маршруты на карте
                </Typography>
                <Stack direction="row" useFlexGap flexWrap="wrap" gap={1} sx={{ mb: 1.25 }}>
                  {routePlan.routes.map((route, index) => (
                    <Stack
                      key={`${route.driverId}-${index}-legend`}
                      direction="row"
                      alignItems="center"
                      spacing={1}
                      sx={{
                        px: 1.25,
                        py: 0.5,
                        borderRadius: 999,
                        border: '1px solid',
                        borderColor: 'divider',
                        bgcolor: 'background.paper'
                      }}
                    >
                      <Box
                        sx={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          bgcolor: routeColor(index),
                          flexShrink: 0
                        }}
                      />
                      <Typography variant="body2" fontWeight={600}>
                        {route.driverName}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
                <Grid container spacing={1}>
                  {routePlan.routes.map((route, index) => {
                    const tripLoad = routeTripLoad(route);
                    return (
                    <Grid key={`${route.driverId}-${index}`} size={{ xs: 12, md: 4 }}>
                      <Box
                        sx={{
                          p: 1.25,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderLeft: `6px solid ${routeColor(index)}`,
                          borderRadius: 1.5,
                          minHeight: 88
                        }}
                      >
                        <Typography variant="subtitle2" fontWeight={700}>
                          {route.driverName}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {route.assignedOrders} точек • {route.estimatedRouteDistanceKm} км • {tripCountLabel(tripLoad.tripCount)}
                        </Typography>
                        <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                          <Typography 
                            variant="caption" 
                            color={tripLoad.maxTripWeightKg > 1400 ? 'error.main' : 'text.secondary'}
                            sx={{ fontWeight: tripLoad.maxTripWeightKg > 1400 ? 700 : 400 }}
                          >
                            Пик веса за рейс: {tripLoad.maxTripWeightKg} / 1500 кг
                          </Typography>
                          <Typography 
                            variant="caption" 
                            color={tripLoad.maxTripVolumeM3 > 11 ? 'error.main' : 'text.secondary'}
                            sx={{ fontWeight: tripLoad.maxTripVolumeM3 > 11 ? 700 : 400 }}
                          >
                            Пик объема за рейс: {tripLoad.maxTripVolumeM3} / 12 м³
                          </Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          Итого: {route.totalWeightKg} кг • {route.totalVolumeM3} м³
                        </Typography>
                      </Box>
                    </Grid>
                    );
                  })}
                </Grid>
              </Paper>
            </Stack>
          ) : (
            <Alert severity="info">План маршрутов пока не сформирован.</Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={handleRejectRoutePlan}
            disabled={actionLoading || routePlanLoading}
            data-testid="reject-route-plan-button"
          >
            Отклонить
          </Button>
          <Button
            variant="contained"
            onClick={handleApproveRoutePlan}
            disabled={actionLoading || !routePlanAssignments.length}
          >
            {actionLoading ? 'Применение...' : 'Одобрить и назначить'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Timeline Dialog */}
      <Dialog open={timelineOpen} onClose={() => setTimelineOpen(false)} maxWidth="md" fullWidth fullScreen={isMobile}>
        <DialogTitle>История заказа #{selectedTimelineOrderId}</DialogTitle>
        <DialogContent dividers>
          {!timeline.length ? (
            <Typography color="text.secondary">История пуста.</Typography>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Время</TableCell>
                    <TableCell>Статус</TableCell>
                    <TableCell>Кто изменил</TableCell>
                    <TableCell>Детали</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {timeline.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                      <TableCell>
                        {statusLabel(item.fromStatus)} → <b>{statusLabel(item.toStatus)}</b>
                      </TableCell>
                      <TableCell>{item.actorUsername || 'Система'}</TableCell>
                      <TableCell>{item.details || '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTimelineOpen(false)}>Закрыть</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
