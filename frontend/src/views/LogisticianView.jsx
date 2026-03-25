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
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
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
import NotificationsIcon from '@mui/icons-material/Notifications';
import { useStableEvent } from '../utils/useStableEvent.js';
import { routeColor } from '../utils/routeColors.js';
import {
  buildRoutePlanPreview,
  buildTripLegendEntries,
  collectTripNumbers,
  extractRoutePlanAssignments,
  normalizeTripNumber,
  selectVisiblePlan
} from '../utils/routePlanPreview.js';

const RoutePlanMap = lazy(() => import('../components/RoutePlanMap.jsx'));
const transportNumberFormatter = new Intl.NumberFormat('ru-RU', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1
});

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

function formatTransportNumber(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return transportNumberFormatter.format(0);
  }
  return transportNumberFormatter.format(numeric);
}

function TripLegendSwatch({ visual, width = 22 }) {
  return (
    <Box
      component="svg"
      viewBox={`0 0 ${width} 10`}
      aria-hidden="true"
      sx={{ width, height: 10, display: 'block', overflow: 'visible' }}
    >
      <line
        x1="1"
        y1="5"
        x2={width - 1}
        y2="5"
        stroke={visual.color}
        strokeWidth="4"
        strokeLinecap="round"
      />
    </Box>
  );
}

function TripLegendChip({ label, visual, selected = false, onClick }) {
  return (
    <Chip
      label={label}
      clickable={Boolean(onClick)}
      onClick={onClick}
      variant="outlined"
      icon={<TripLegendSwatch visual={visual} />}
      sx={{
        borderColor: visual.color,
        borderWidth: selected ? 2 : 1,
        bgcolor: selected ? 'action.selected' : 'transparent',
        color: 'text.primary',
        fontWeight: selected ? 700 : 500,
        '& .MuiChip-label': {
          px: 0.5
        },
        '& .MuiChip-icon': {
          ml: 1
        }
      }}
    />
  );
}

const MetricCard = memo(function MetricCard({ title, value, icon, color }) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%' }}>
      <CardContent sx={{ display: 'flex', alignItems: 'center', p: 3, '&:last-child': { pb: 3 } }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>
            {title}
          </Typography>
          <Typography variant="h4" component="p" fontWeight="bold">
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

function countLabel(count, one, few, many) {
  const normalized = Number.isFinite(Number(count)) ? Math.max(0, Math.trunc(Number(count))) : 0;
  const mod10 = normalized % 10;
  const mod100 = normalized % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${normalized} ${one}`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${normalized} ${few}`;
  }
  return `${normalized} ${many}`;
}

function orderCountLabel(count) {
  return countLabel(count, 'заказ', 'заказа', 'заказов');
}

function stopCountLabel(count) {
  return countLabel(count, 'остановка', 'остановки', 'остановок');
}

const TRANSPORT_DRIVER_LIMIT = 3;

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
  const [routePreviewDriverId, setRoutePreviewDriverId] = useState('all');
  const [routePreviewTripNumber, setRoutePreviewTripNumber] = useState('all');
  const [transportMenuOpen, setTransportMenuOpen] = useState(false);
  const [transportDriverIds, setTransportDriverIds] = useState([]);

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
  const routePlanPreview = useMemo(() => buildRoutePlanPreview(routePlan), [routePlan]);
  const routePlanAssignments = useMemo(() => {
    return extractRoutePlanAssignments(routePlanPreview);
  }, [routePlanPreview]);
  const selectedTransportDrivers = useMemo(() => {
    const selectedIds = new Set(transportDriverIds);
    return drivers.filter((driver) => selectedIds.has(driver.id));
  }, [drivers, transportDriverIds]);
  const indexedRoutePlanRoutes = Array.isArray(routePlanPreview?.routes) ? routePlanPreview.routes : [];
  const visibleRoutePlan = useMemo(
    () => selectVisiblePlan(routePlanPreview, routePreviewDriverId, routePreviewTripNumber),
    [routePlanPreview, routePreviewDriverId, routePreviewTripNumber]
  );
  const routePreviewRoutes = Array.isArray(visibleRoutePlan?.routes) ? visibleRoutePlan.routes : [];
  const activePreviewRoute = useMemo(() => {
    if (routePreviewDriverId === 'all') {
      return null;
    }
    return routePreviewRoutes[0] || null;
  }, [routePreviewDriverId, routePreviewRoutes]);
  const activePreviewTripNumbers = useMemo(
    () => (activePreviewRoute ? collectTripNumbers(activePreviewRoute) : []),
    [activePreviewRoute]
  );
  const routeDriverLegendEntries = useMemo(
    () => buildTripLegendEntries(routePlanPreview, routePreviewDriverId, 'all'),
    [routePlanPreview, routePreviewDriverId]
  );
  const visibleRouteLegendEntries = useMemo(
    () => buildTripLegendEntries(routePlanPreview, routePreviewDriverId, routePreviewTripNumber),
    [routePlanPreview, routePreviewDriverId, routePreviewTripNumber]
  );
  const tripFilterAvailable = routePreviewDriverId !== 'all' && activePreviewTripNumbers.length > 1;
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

  useEffect(() => {
    if (!drivers.length) {
      setTransportDriverIds([]);
      return;
    }

    setTransportDriverIds((prev) => {
      const availableDriverIds = new Set(drivers.map((driver) => driver.id));
      const normalizedSelection = prev
        .filter((driverId) => availableDriverIds.has(driverId))
        .slice(0, TRANSPORT_DRIVER_LIMIT);
      if (normalizedSelection.length) {
        return normalizedSelection;
      }
      return drivers.slice(0, Math.min(TRANSPORT_DRIVER_LIMIT, drivers.length)).map((driver) => driver.id);
    });
  }, [drivers]);

  useEffect(() => {
    setRoutePreviewDriverId('all');
    setRoutePreviewTripNumber('all');
  }, [routePlan]);

  useEffect(() => {
    if (routePreviewDriverId === 'all') {
      if (routePreviewTripNumber !== 'all') {
        setRoutePreviewTripNumber('all');
      }
      return;
    }

    if (routePreviewTripNumber === 'all') {
      return;
    }

    const selectedTrip = normalizeTripNumber(routePreviewTripNumber);
    if (!activePreviewTripNumbers.includes(selectedTrip)) {
      setRoutePreviewTripNumber('all');
    }
  }, [activePreviewTripNumbers, routePreviewDriverId, routePreviewTripNumber]);

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

  const handleToggleTransportDriver = useCallback((driverId) => {
    setTransportDriverIds((prev) => {
      if (prev.includes(driverId)) {
        return prev.filter((currentId) => currentId !== driverId);
      }
      if (prev.length >= TRANSPORT_DRIVER_LIMIT) {
        showMessage(`Можно выбрать не более ${TRANSPORT_DRIVER_LIMIT} водителей`, 'warning');
        return prev;
      }
      return [...prev, driverId];
    });
  }, []);

  const handleAutoAssign = () => {
    if (!drivers.length) {
      showMessage('Нет доступных водителей для транспортной задачи', 'warning');
      return;
    }
    setTransportMenuOpen(true);
  };

  const handleBuildRoutePlan = async () => {
    if (!transportDriverIds.length) {
      showMessage('Выберите хотя бы одного водителя', 'warning');
      return;
    }

    setActionLoading(true);
    setTransportMenuOpen(false);
    setRoutePlan(null);
    setRoutePlanOpen(true);
    setRoutePlanLoading(true);
    try {
      const result = await previewAutoAssignOrders(token, { driverIds: transportDriverIds });
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
        `оценка пути ${formatTransportNumber(result.estimatedTotalDistanceKm)} км`
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
               <Typography variant="h5" component="h2" fontWeight="bold" gutterBottom>Логистика и назначения</Typography>
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
              <Typography variant="h6" component="h3" gutterBottom display="flex" alignItems="center" gap={1}>
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
        open={transportMenuOpen}
        onClose={actionLoading ? undefined : () => setTransportMenuOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Выбор водителей</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5}>
            <Alert severity="info">
              Выберите до {TRANSPORT_DRIVER_LIMIT} водителей. Система распределит между ними все заказы так, чтобы
              суммарный пробег был минимальным, а при нехватке вместимости откроет следующий рейс от склада.
            </Alert>
            <Stack spacing={1}>
              {drivers.map((driver) => {
                const checked = transportDriverIds.includes(driver.id);
                const disabled = actionLoading || (!checked && transportDriverIds.length >= TRANSPORT_DRIVER_LIMIT);
                return (
                  <Paper
                    key={driver.id}
                    variant="outlined"
                    sx={{
                      p: 1.25,
                      borderRadius: 1.5,
                      borderColor: checked ? 'primary.main' : 'divider'
                    }}
                  >
                    <Stack direction="row" spacing={1.25} alignItems="center">
                      <Checkbox
                        checked={checked}
                        disabled={disabled}
                        onChange={() => handleToggleTransportDriver(driver.id)}
                        inputProps={{ 'aria-label': `Выбрать водителя ${driver.fullName}` }}
                      />
                      <Box>
                        <Typography variant="subtitle2" fontWeight={700}>
                          {driver.fullName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {checked ? 'Участвует в расчёте' : 'Не выбран'}
                        </Typography>
                      </Box>
                    </Stack>
                  </Paper>
                );
              })}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              В расчёте выбрано {selectedTransportDrivers.length} из {Math.min(TRANSPORT_DRIVER_LIMIT, drivers.length)} водителей.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTransportMenuOpen(false)} disabled={actionLoading}>
            Отмена
          </Button>
          <Button
            variant="contained"
            onClick={handleBuildRoutePlan}
            disabled={actionLoading || !transportDriverIds.length}
          >
            Построить план
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={routePlanOpen}
        onClose={actionLoading || routePlanLoading ? undefined : handleRejectRoutePlan}
        maxWidth="md"
        fullWidth
        fullScreen={isMobile}
        PaperProps={{
          sx: {
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            maxHeight: { xs: '92vh', sm: '88vh' }
          }
        }}
      >
        <DialogTitle data-testid="auto-assign-dialog-title">Предпросмотр транспортной задачи</DialogTitle>
        <DialogContent dividers sx={{ overflowY: 'auto', overflowX: 'hidden', py: 1.5 }}>
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
              <Grid container spacing={1.25}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Заказов в плане</Typography>
                    <Typography variant="h6" fontWeight={700}>
                      {routePlan.plannedOrders}/{routePlan.totalApprovedOrders}
                    </Typography>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Paper variant="outlined" sx={{ p: 1.25, borderRadius: 1.5 }}>
                    <Typography variant="caption" color="text.secondary">Километраж</Typography>
                    <Typography variant="h6" fontWeight={700}>{formatTransportNumber(routePlan.estimatedTotalDistanceKm)} км</Typography>
                  </Paper>
                </Grid>
              </Grid>

              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
                  <Chip
                    label="Все водители"
                    clickable
                    color={routePreviewDriverId === 'all' ? 'primary' : 'default'}
                    variant={routePreviewDriverId === 'all' ? 'filled' : 'outlined'}
                    onClick={() => {
                      setRoutePreviewDriverId('all');
                      setRoutePreviewTripNumber('all');
                    }}
                  />
                  {indexedRoutePlanRoutes.map((route) => (
                    <Chip
                      key={`${route.driverId}-${route.colorIndex}-filter-top`}
                      label={route.driverName}
                      clickable
                      onClick={() => {
                        setRoutePreviewDriverId(String(route.driverId));
                        setRoutePreviewTripNumber('all');
                      }}
                      variant={String(routePreviewDriverId) === String(route.driverId) ? 'filled' : 'outlined'}
                      sx={{
                        borderColor: routeColor(route.colorIndex),
                        color: String(routePreviewDriverId) === String(route.driverId) ? '#ffffff' : 'text.primary',
                        bgcolor: String(routePreviewDriverId) === String(route.driverId)
                          ? routeColor(route.colorIndex)
                          : 'transparent'
                      }}
                    />
                  ))}
                </Stack>
                {tripFilterAvailable ? (
                  <Stack spacing={1} sx={{ mt: 1.5 }}>
                    <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
                      <Chip
                        label="Все рейсы"
                        clickable
                        variant="outlined"
                        onClick={() => setRoutePreviewTripNumber('all')}
                        sx={{
                          borderWidth: routePreviewTripNumber === 'all' ? 2 : 1,
                          bgcolor: routePreviewTripNumber === 'all' ? 'action.selected' : 'transparent',
                          fontWeight: routePreviewTripNumber === 'all' ? 700 : 500
                        }}
                      />
                      {routeDriverLegendEntries.map((entry) => (
                        <TripLegendChip
                          key={entry.key}
                          label={`Рейс ${entry.tripNumber}`}
                          visual={entry}
                          selected={
                            routePreviewTripNumber !== 'all'
                            && normalizeTripNumber(routePreviewTripNumber) === entry.tripNumber
                          }
                          onClick={() => setRoutePreviewTripNumber(String(entry.tripNumber))}
                        />
                      ))}
                    </Stack>
                  </Stack>
                ) : visibleRouteLegendEntries.length ? (
                  <Stack spacing={1} sx={{ mt: 1.5 }}>
                    <Stack direction="row" useFlexGap flexWrap="wrap" gap={1}>
                      {visibleRouteLegendEntries.map((entry) => (
                        <TripLegendChip
                          key={entry.key}
                          label={entry.label}
                          visual={entry}
                        />
                      ))}
                    </Stack>
                  </Stack>
                ) : null}
              </Paper>

              <Suspense
                fallback={
                  <Paper
                    variant="outlined"
                    sx={{
                      height: { xs: 280, md: 360 },
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
                <RoutePlanMap
                  plan={routePlanPreview}
                  token={token}
                  visibleDriverId={routePreviewDriverId}
                  visibleTripNumber={routePreviewTripNumber}
                />
              </Suspense>

              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
                  Сводка по водителям
                </Typography>
                <Grid container spacing={1}>
                  {routePreviewRoutes.map((route) => {
                    const tripLoad = routeTripLoad(route);
                    return (
                  <Grid key={`${route.driverId}-${route.colorIndex}`} size={{ xs: 12, md: 4 }}>
                      <Box
                        sx={{
                          p: 1.25,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderLeft: `6px solid ${routeColor(route.colorIndex)}`,
                          borderRadius: 1.5,
                          minHeight: 88
                        }}
                      >
                        <Typography variant="subtitle2" fontWeight={700}>
                          {route.driverName}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {orderCountLabel(route.assignedOrders)} • {stopCountLabel(route.displayStops?.length ?? route.points?.length ?? 0)} • {formatTransportNumber(route.estimatedRouteDistanceKm)} км • {tripCountLabel(tripLoad.tripCount)}
                        </Typography>
                        <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                          <Typography 
                            variant="caption" 
                            color={tripLoad.maxTripWeightKg > 1400 ? 'error.main' : 'text.secondary'}
                            sx={{ fontWeight: tripLoad.maxTripWeightKg > 1400 ? 700 : 400 }}
                          >
                            Пик веса за рейс: {formatTransportNumber(tripLoad.maxTripWeightKg)} / {formatTransportNumber(1500)} кг
                          </Typography>
                          <Typography 
                            variant="caption" 
                            color={tripLoad.maxTripVolumeM3 > 11 ? 'error.main' : 'text.secondary'}
                            sx={{ fontWeight: tripLoad.maxTripVolumeM3 > 11 ? 700 : 400 }}
                          >
                            Пик объёма за рейс: {formatTransportNumber(tripLoad.maxTripVolumeM3)} / {formatTransportNumber(12)} м³
                          </Typography>
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          Итого: {formatTransportNumber(route.totalWeightKg)} кг • {formatTransportNumber(route.totalVolumeM3)} м³
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
