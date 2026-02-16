import React, { useEffect, useMemo, useState } from 'react';
import {
  assignOrderDriver,
  getAllOrders,
  getDrivers,
  getOrderTimeline,
  subscribeNotifications
} from '../api.js';
import OrdersTable from '../components/OrdersTable.jsx';

// MUI Imports
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Alert,
  Snackbar,
  Avatar,
  Slide
} from '@mui/material';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

import {
  Assignment as AssignmentIcon,
  History as HistoryIcon,
  Notifications as NotificationsIcon,
  Person as PersonIcon,
  InboxOutlined as InboxOutlinedIcon
} from '@mui/icons-material';
import { OrderTableSkeleton } from '../components/LoadingSkeletons.jsx';

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

export default function LogisticianView({ token, activeSection }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [orders, setOrders] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [driverSelection, setDriverSelection] = useState({});
  const [notifications, setNotifications] = useState([]);
  
  // UI State
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });

  // Timeline Dialog
  const [timelineOpen, setTimelineOpen] = useState(false);
  const [selectedTimelineOrderId, setSelectedTimelineOrderId] = useState(null);
  const [timeline, setTimeline] = useState([]);

  const pendingAssignmentCount = useMemo(
    () => orders.filter((order) => order.status === 'APPROVED').length,
    [orders]
  );
  const latestNotifications = useMemo(() => notifications.slice(0, 4), [notifications]);
  const showSection = (sectionId) => !activeSection || activeSection === sectionId;

  const showMessage = (message, severity = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const load = async () => {
    setLoading(true);
    try {
      const [ordersData, driversData] = await Promise.all([
        getAllOrders(token),
        getDrivers(token)
      ]);
      setOrders(ordersData);
      setDrivers(driversData);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить данные', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    const unsubscribe = subscribeNotifications(token, {
      onNotification: (payload) => {
        setNotifications((prev) => [payload, ...prev].slice(0, 10));
        showMessage(`Новое событие: ${payload.title || 'Уведомление'}`, 'info');
      }
    });
    return () => unsubscribe();
  }, [token]);

  const handleAssign = async (orderId) => {
    const driverId = Number(driverSelection[orderId]);
    if (!driverId) {
      return showMessage('Выберите водителя перед назначением', 'warning');
    }

    setActionLoading(true);
    try {
      await assignOrderDriver(token, orderId, driverId);
      showMessage(`Водитель назначен на заказ #${orderId}`);
      await load();
    } catch (err) {
      showMessage(err.message || 'Не удалось назначить водителя', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLoadTimeline = async (orderId) => {
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
  };

  const MetricCard = ({ title, value, icon, color }) => (
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
            {loading && <CircularProgress size={24} />}
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
            emptyText="Заказы отсутствуют."
            actionRenderer={(order) => (
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                alignItems={{ xs: 'stretch', sm: 'center' }}
              >
                {order.status === 'APPROVED' && (
                  <>
                    <TextField
                      select
                      size="small"
                      placeholder="Выберите водителя"
                      value={driverSelection[order.id] || ''}
                      onChange={(e) => setDriverSelection((prev) => ({ ...prev, [order.id]: e.target.value }))}
                      fullWidth={isMobile}
                      sx={{ minWidth: { sm: 180 } }}
                      disabled={actionLoading}
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
                      fullWidth={isMobile}
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
                  fullWidth={isMobile}
                >
                  История
                </Button>
              </Stack>
            )}
          />

          {!!latestNotifications.length && (
            <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mt: 4 }}>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center" gap={1}>
                <NotificationsIcon color="action" /> Последние уведомления
              </Typography>
              <Stack spacing={1}>
                {latestNotifications.map((notif, idx) => (
                  <Alert key={idx} severity="info" icon={false} sx={{ py: 0 }}>
                    <Typography variant="subtitle2" component="span" fontWeight="bold">
                      {notif.title || 'Событие'}: 
                    </Typography>
                    {' '}{notif.message} — <Typography variant="caption" color="text.secondary">{formatDateTime(notif.createdAt)}</Typography>
                  </Alert>
                ))}
              </Stack>
            </Paper>
          )}
        </Box>
      )}

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
