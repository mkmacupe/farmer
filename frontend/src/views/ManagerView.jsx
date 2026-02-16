import React, { useEffect, useMemo, useState } from 'react';
import {
  approveOrder,
  createDirectorUser,
  createProduct,
  deleteProduct,
  downloadOrdersReport,
  getAllOrders,
  getDashboardSummary,
  getDirectors,
  getDrivers,
  getOrderTimeline,
  getProductCategories,
  getProductsPage,
  subscribeNotifications,
  updateProduct
} from '../api.js';

import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  TableContainer,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  Alert,
  Snackbar,
  Avatar,
  Slide
} from '@mui/material';
import {
  Add as AddIcon,
  Refresh as RefreshIcon,
  Download as DownloadIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Check as CheckIcon,
  History as HistoryIcon,
  Notifications as NotificationsIcon,
  LocalShipping,
  AttachMoney,
  Receipt,
  Group as GroupIcon,
  DragIndicator as DragIndicatorIcon
} from '@mui/icons-material';
import ProductImage from '../components/ProductImage.jsx';
import { filterLocalizedCategories, filterLocalizedProducts } from '../utils/productFilters.js';
import { DashboardSkeleton } from '../components/LoadingSkeletons.jsx';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

const KANBAN_COLUMNS = [
  { id: 'CREATED', title: 'Создан', color: 'info' },
  { id: 'APPROVED', title: 'Одобрен', color: 'warning' },
  { id: 'ASSIGNED', title: 'Назначен', color: 'secondary' },
  { id: 'DELIVERED', title: 'Доставлен', color: 'success' }
];

function todayDateValue() {
  return new Date().toISOString().slice(0, 10);
}

function isMethodNotAllowedError(error) {
  const message = String(error?.message || '').toLowerCase();
  return message.includes('method not allowed') || message.includes('405');
}

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

function formatMoney(value) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric)) return '0.00';
  return numeric.toLocaleString('ru-RU', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

export default function ManagerView({ token, activeSection }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [directors, setDirectors] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const productPageSize = 20;
  const [productPage, setProductPage] = useState(0);
  const [productHasNext, setProductHasNext] = useState(false);
  const [productTotalItems, setProductTotalItems] = useState(0);
  const [productsLoadingMore, setProductsLoadingMore] = useState(false);

  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'info' });

  const [dashboardFrom, setDashboardFrom] = useState('');
  const [dashboardTo, setDashboardTo] = useState(todayDateValue());

  const [reportFrom, setReportFrom] = useState('');
  const [reportTo, setReportTo] = useState('');
  const [reportStatus, setReportStatus] = useState('');

  const [timelineOpen, setTimelineOpen] = useState(false);
  const [timelineOrderId, setTimelineOrderId] = useState(null);
  const [timeline, setTimeline] = useState([]);

  const [productDrawerOpen, setProductDrawerOpen] = useState(false);
  const [editingProductId, setEditingProductId] = useState(null);
  const [productDraft, setProductDraft] = useState({
    name: '',
    category: '',
    description: '',
    photoUrl: '',
    price: '',
    stockQuantity: ''
  });

  const [newDirector, setNewDirector] = useState({
    username: '',
    password: '',
    fullName: '',
    phone: '',
    legalEntityName: ''
  });

  const [draggingOrderId, setDraggingOrderId] = useState(null);

  const showSection = (sectionId) => !activeSection || activeSection === sectionId;

  const orderStats = useMemo(() => ({
    CREATED: orders.filter((order) => order.status === 'CREATED').length,
    APPROVED: orders.filter((order) => order.status === 'APPROVED').length,
    ASSIGNED: orders.filter((order) => order.status === 'ASSIGNED').length,
    DELIVERED: orders.filter((order) => order.status === 'DELIVERED').length
  }), [orders]);

  const latestNotifications = useMemo(() => notifications.slice(0, 4), [notifications]);

  const ordersByStatus = useMemo(() => {
    const grouped = {};
    KANBAN_COLUMNS.forEach((col) => { grouped[col.id] = []; });
    orders.forEach((order) => {
      if (!grouped[order.status]) {
        grouped[order.status] = [];
      }
      grouped[order.status].push(order);
    });
    return grouped;
  }, [orders]);

  const showMessage = (message, severity = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const loadMain = async () => {
    const [ordersData, productsPage, categoriesData] = await Promise.all([
      getAllOrders(token),
      getProductsPage(token, { page: 0, size: productPageSize }),
      getProductCategories(token)
    ]);
    setOrders(ordersData);
    setProducts(filterLocalizedProducts(productsPage.items));
    setProductPage(productsPage.page);
    setProductHasNext(productsPage.hasNext);
    setProductTotalItems(productsPage.totalItems);
    setCategories(filterLocalizedCategories(categoriesData));

    try {
      const directorsData = await getDirectors(token);
      setDirectors(directorsData);
    } catch (err) {
      if (!isMethodNotAllowedError(err)) {
        console.error('Failed to load directors', err);
      } else {
        setDirectors([]);
      }
    }

    try {
      const driversData = await getDrivers(token);
      setDrivers(driversData);
    } catch (err) {
      if (!isMethodNotAllowedError(err)) {
        console.error('Failed to load drivers', err);
      } else {
        setDrivers([]);
      }
    }
  };

  const loadDashboard = async () => {
    try {
      const data = await getDashboardSummary(token, {
        from: dashboardFrom || null,
        to: dashboardTo || null
      });
      setDashboard(data);
    } catch (err) {
      console.error('Failed to load dashboard', err);
    }
  };

  const load = async () => {
    setLoading(true);
    try {
      await Promise.all([loadMain(), loadDashboard()]);
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
        setNotifications((prev) => [payload, ...prev].slice(0, 20));
        showMessage(`Новое событие: ${payload.title || 'Уведомление'}`, 'info');
      }
    });
    return () => unsubscribe();
  }, [token]);

  const handleApprove = async (orderId) => {
    setActionLoading(true);
    try {
      await approveOrder(token, orderId);
      showMessage(`Заказ #${orderId} одобрен`);
      await Promise.all([loadMain(), loadDashboard()]);
    } catch (err) {
      showMessage(err.message || 'Не удалось одобрить заказ', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLoadTimeline = async (orderId) => {
    setActionLoading(true);
    try {
      const data = await getOrderTimeline(token, orderId);
      setTimelineOrderId(orderId);
      setTimeline(data);
      setTimelineOpen(true);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить таймлайн', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDragStart = (event, orderId) => {
    event.dataTransfer.setData('text/plain', String(orderId));
    event.dataTransfer.effectAllowed = 'move';
    setDraggingOrderId(orderId);
  };

  const handleDragEnd = () => {
    setDraggingOrderId(null);
  };

  const handleDrop = (event, targetStatus) => {
    event.preventDefault();
    const orderId = Number(event.dataTransfer.getData('text/plain'));
    if (!orderId) {
      return;
    }
    const order = orders.find((item) => item.id === orderId);
    if (!order) {
      return;
    }
    if (order.status === targetStatus) {
      return;
    }
    if (order.status === 'CREATED' && targetStatus === 'APPROVED') {
      handleApprove(orderId);
      return;
    }
    showMessage('Перемещение доступно только для этапа согласования', 'info');
  };

  const handleOpenProductDrawer = (product = null) => {
    if (product) {
      setEditingProductId(product.id);
      setProductDraft({
        name: product.name || '',
        category: product.category || '',
        description: product.description || '',
        photoUrl: product.photoUrl || '',
        price: String(product.price ?? ''),
        stockQuantity: String(product.stockQuantity ?? '')
      });
    } else {
      setEditingProductId(null);
      setProductDraft({
        name: '',
        category: '',
        description: '',
        photoUrl: '',
        price: '',
        stockQuantity: ''
      });
    }
    setProductDrawerOpen(true);
  };

  const handleProductSave = async () => {
    const price = Number(productDraft.price);
    const stockQuantity = Number(productDraft.stockQuantity);

    if (!productDraft.name.trim()) return showMessage('Укажите наименование', 'error');
    if (!productDraft.category.trim()) return showMessage('Укажите категорию', 'error');
    if (!Number.isFinite(price) || price < 0) return showMessage('Некорректная цена', 'error');
    if (!Number.isInteger(stockQuantity) || stockQuantity < 0) return showMessage('Некорректное количество', 'error');

    const payload = {
      name: productDraft.name.trim(),
      category: productDraft.category.trim(),
      description: productDraft.description.trim(),
      photoUrl: productDraft.photoUrl.trim(),
      price,
      stockQuantity
    };

    setActionLoading(true);
    try {
      if (editingProductId) {
        await updateProduct(token, editingProductId, payload);
        showMessage('Товар обновлён');
      } else {
        await createProduct(token, payload);
        showMessage('Товар добавлен');
      }
      setProductDrawerOpen(false);
      await loadMain();
    } catch (err) {
      showMessage(err.message || 'Ошибка сохранения', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleProductDelete = async (id) => {
    if (!window.confirm(`Удалить товар #${id}?`)) return;

    setActionLoading(true);
    try {
      await deleteProduct(token, id);
      showMessage(`Товар #${id} удалён`);
      await loadMain();
    } catch (err) {
      showMessage(err.message || 'Не удалось удалить', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDirectorCreate = async () => {
    if (!newDirector.username || !newDirector.password) {
      return showMessage('Заполните обязательные поля', 'error');
    }
    setActionLoading(true);
    try {
      await createDirectorUser(token, newDirector);
      showMessage(`Пользователь ${newDirector.username} создан`);
      setNewDirector({ username: '', password: '', fullName: '', phone: '', legalEntityName: '' });
      await loadMain();
    } catch (err) {
      showMessage(err.message || 'Ошибка создания', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDownloadReport = async () => {
    setActionLoading(true);
    let url = null;
    try {
      const blob = await downloadOrdersReport(token, {
        from: reportFrom || null,
        to: reportTo || null,
        status: reportStatus || null
      });
      url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `otchet-zakazy-${Date.now()}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      showMessage('Отчёт скачан');
    } catch (err) {
      showMessage(err.message || 'Ошибка скачивания', 'error');
    } finally {
      if (url) {
        URL.revokeObjectURL(url);
      }
      setActionLoading(false);
    }
  };

  const kpiCards = [
    {
      title: 'Выручка',
      value: dashboard ? formatMoney(dashboard.totalRevenue || 0) : '0.00',
      icon: <AttachMoney />,
      color: 'secondary'
    },
    {
      title: 'Заказы',
      value: dashboard ? dashboard.totalOrders : orders.length,
      icon: <Receipt />,
      color: 'primary'
    },
    {
      title: 'Доставки',
      value: dashboard ? dashboard.deliveredOrders : orderStats.DELIVERED,
      icon: <LocalShipping />,
      color: 'success'
    },
    {
      title: 'Активные пользователи',
      value: directors.length,
      icon: <GroupIcon />,
      color: 'info'
    }
  ];

  const KanbanCard = ({ order }) => {
    const hasQuickApprove = order.status === 'CREATED';
    const columnMeta = KANBAN_COLUMNS.find((col) => col.id === order.status);
    const dragEnabled = !isMobile;
    return (
      <Card
        draggable={dragEnabled}
        onDragStart={dragEnabled ? (event) => handleDragStart(event, order.id) : undefined}
        onDragEnd={dragEnabled ? handleDragEnd : undefined}
        sx={{
          border: '1px solid',
          borderColor: draggingOrderId === order.id ? 'primary.main' : 'divider',
          boxShadow: 'none',
          cursor: dragEnabled ? 'grab' : 'default',
          '&:active': { cursor: dragEnabled ? 'grabbing' : 'default' },
          '& .kanban-actions': {
            opacity: { xs: 1, md: 0 },
            transform: { xs: 'none', md: 'translateY(6px)' },
            transition: 'all 0.2s ease'
          },
          '&:hover .kanban-actions': {
            opacity: 1,
            transform: 'translateY(0)'
          }
        }}
      >
        <CardContent sx={{ pb: 1.5 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
            <Stack direction="row" alignItems="center" spacing={1}>
              {dragEnabled && <DragIndicatorIcon fontSize="small" color="action" />}
              <Typography variant="subtitle2" fontWeight={700}>
                Заказ #{order.id}
              </Typography>
            </Stack>
            <Chip label={statusLabel(order.status)} size="small" color={columnMeta?.color || 'default'} />
          </Stack>
          <Typography variant="body2" fontWeight={600} sx={{ mt: 1 }}>
            {order.customerName || 'Директор магазина'}
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
            {order.deliveryAddressText || 'Адрес не указан'}
          </Typography>
          <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
            <Typography variant="caption" color="text.secondary">
              Сумма: {formatMoney(order.totalAmount)} BYN
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Позиций: {order.items?.length || 0}
            </Typography>
          </Stack>
        </CardContent>
        <Box className="kanban-actions" sx={{ px: 2, pb: 2, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          {hasQuickApprove && (
            <Button
              size="small"
              variant="contained"
              startIcon={<CheckIcon />}
              onClick={() => handleApprove(order.id)}
              disabled={actionLoading}
            >
              Одобрить
            </Button>
          )}
          <Button
            size="small"
            variant="outlined"
            startIcon={<HistoryIcon />}
            onClick={() => handleLoadTimeline(order.id)}
            disabled={actionLoading}
          >
            История
          </Button>
        </Box>
      </Card>
    );
  };

  const handleLoadMoreProducts = async () => {
    if (!productHasNext || productsLoadingMore) {
      return;
    }
    setProductsLoadingMore(true);
    try {
      const nextPage = productPage + 1;
      const nextPageData = await getProductsPage(token, {
        page: nextPage,
        size: productPageSize
      });
      setProducts((prev) => filterLocalizedProducts([...prev, ...nextPageData.items]));
      setProductPage(nextPageData.page);
      setProductHasNext(nextPageData.hasNext);
      setProductTotalItems(nextPageData.totalItems);
    } catch (err) {
      showMessage(err.message || 'Не удалось загрузить ещё товары', 'error');
    } finally {
      setProductsLoadingMore(false);
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
      {showSection('manager-dashboard') && (
        loading && !dashboard && !orders.length ? (
          <DashboardSkeleton />
        ) : (
        <Box>
          <Paper sx={{ p: 3, mb: 3, borderRadius: 2.5, border: '1px solid', borderColor: 'divider' }}>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ md: 'center' }} spacing={2}>
              <Box>
                <Typography variant="h5" fontWeight={700} gutterBottom sx={{ letterSpacing: '-0.02em' }}>
                  Панель менеджера
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Неодобренных заявок: {orderStats.CREATED}
                </Typography>
              </Box>
              <Button
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => { load(); }}
                disabled={loading}
              >
                Обновить
              </Button>
            </Stack>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 3, borderRadius: 2.5, border: '1px solid', borderColor: 'divider' }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'flex-end' }}>
              <TextField
                label="Период с"
                type="date"
                size="small"
                InputLabelProps={{ shrink: true }}
                value={dashboardFrom}
                onChange={(e) => setDashboardFrom(e.target.value)}
              />
              <TextField
                label="Период по"
                type="date"
                size="small"
                InputLabelProps={{ shrink: true }}
                value={dashboardTo}
                onChange={(e) => setDashboardTo(e.target.value)}
              />
              <Button variant="contained" onClick={loadDashboard} disabled={loading}>
                Применить фильтр
              </Button>
            </Stack>
          </Paper>

          <Grid container spacing={2} sx={{ mb: 3 }}>
            {kpiCards.map((card) => (
              <Grid size={{ xs: 12, sm: 6, md: 3 }} key={card.title}>
                <Paper sx={{ p: 2.5, borderRadius: 2.5, height: '100%', border: '1px solid', borderColor: 'divider' }}>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Avatar sx={{ bgcolor: `${card.color}.light`, color: `${card.color}.main` }}>
                      {card.icon}
                    </Avatar>
                    <Box>
                      <Typography variant="subtitle2" color="text.secondary">
                        {card.title}
                      </Typography>
                      <Typography variant="h4" fontWeight={700}>
                        {card.value}
                      </Typography>
                    </Box>
                  </Stack>
                </Paper>
              </Grid>
            ))}
          </Grid>

          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 12, md: 8 }}>
              <Paper sx={{ p: 2.5, borderRadius: 2.5, height: '100%', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                  Выручка во времени
                </Typography>
                <Box
                  sx={{
                    mt: 2,
                    height: 240,
                    borderRadius: 2,
                    border: '1px dashed',
                    borderColor: 'divider',
                    bgcolor: 'background.default',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}
                >
                  <Typography variant="caption" color="text.secondary">
                    Линейный график
                  </Typography>
                </Box>
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Paper sx={{ p: 2.5, borderRadius: 2.5, height: '100%', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="subtitle1" fontWeight={600} gutterBottom>
                  Заказы по категориям
                </Typography>
                <Box
                  sx={{
                    mt: 2,
                    height: 240,
                    borderRadius: 2,
                    border: '1px dashed',
                    borderColor: 'divider',
                    bgcolor: 'background.default',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}
                >
                  <Typography variant="caption" color="text.secondary">
                    Кольцевая диаграмма
                  </Typography>
                </Box>
              </Paper>
            </Grid>
          </Grid>

          {!!latestNotifications.length && (
            <Paper sx={{ p: 2.5, borderRadius: 2.5, border: '1px solid', borderColor: 'divider' }}>
              <Typography variant="h6" gutterBottom display="flex" alignItems="center" gap={1}>
                <NotificationsIcon color="action" /> Последние события
              </Typography>
              <Stack spacing={1}>
                {latestNotifications.map((notif, idx) => (
                  <Alert key={idx} severity="info" icon={false} sx={{ py: 0 }}>
                    <Typography variant="subtitle2" component="span" fontWeight="bold">
                      {notif.title || 'Событие'}:
                    </Typography>{' '}
                    {notif.message} — <Typography variant="caption" color="text.secondary">{formatDateTime(notif.createdAt)}</Typography>
                  </Alert>
                ))}
              </Stack>
            </Paper>
          )}
        </Box>
        )
      )}
      {showSection('manager-orders') && (
        <Box>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h5" fontWeight={700} gutterBottom sx={{ letterSpacing: '-0.02em' }}>Заявки на доставку</Typography>
            <Typography variant="body2" color="text.secondary">
              {isMobile ? 'Используйте действия на карточках для управления.' : 'Перетаскивайте карточки между статусами'}
            </Typography>
          </Box>

          <Grid container spacing={2}>
            {KANBAN_COLUMNS.map((column) => (
              <Grid size={{ xs: 12, md: 3 }} key={column.id}>
                <Paper
                  variant="outlined"
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => handleDrop(event, column.id)}
                  sx={{
                    p: 2,
                    borderRadius: 2.5,
                    minHeight: { xs: 260, md: 420 },
                    bgcolor: '#f4f4f5',
                    border: '1px solid #e4e4e7'
                  }}
                >
                  <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
                    <Typography variant="subtitle1" fontWeight={700}>{column.title}</Typography>
                    <Chip label={ordersByStatus[column.id]?.length || 0} size="small" color={column.color} />
                  </Stack>
                  <Stack spacing={1.5}>
                    {(ordersByStatus[column.id] || []).map((order) => (
                      <KanbanCard key={order.id} order={order} />
                    ))}
                    {!ordersByStatus[column.id]?.length && (
                      <Typography variant="caption" color="text.secondary">
                        Нет заказов
                      </Typography>
                    )}
                  </Stack>
                </Paper>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}
      {showSection('manager-products') && (
        <Box>
          <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
            <Box>
              <Typography variant="h5" fontWeight={700} gutterBottom sx={{ letterSpacing: '-0.02em' }}>Товары</Typography>
              <Typography variant="body2" color="text.secondary">
                Каталог продукции ({products.length}/{productTotalItems} поз.)
              </Typography>
            </Box>
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => handleOpenProductDrawer()}>
              Добавить товар
            </Button>
          </Box>

              <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
            {products.map((product, index) => (
              <Box
                key={product.id}
                sx={{
                  display: 'flex',
                  flexDirection: { xs: 'column', sm: 'row' },
                  alignItems: { xs: 'flex-start', sm: 'center' },
                  gap: 2,
                  p: 2,
                  borderBottom: index === products.length - 1 ? 'none' : '1px solid',
                  borderColor: 'divider'
                }}
              >
                <ProductImage
                  src={product.photoUrl}
                  alt={product.name}
                  width={isMobile ? 72 : 56}
                  height={isMobile ? 72 : 56}
                  borderRadius={1}
                  priority={index < 6}
                />
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="subtitle1" fontWeight={700} noWrap={!isMobile}>
                    {product.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap={!isMobile} display="block">
                    {product.description || 'Описание отсутствует'}
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1, flexWrap: 'wrap' }}>
                    <Chip label={product.category} size="small" />
                    <Typography variant="caption" color="text.secondary">Цена: {formatMoney(product.price)} BYN</Typography>
                    <Typography variant="caption" color="text.secondary">Остаток: {product.stockQuantity}</Typography>
                  </Stack>
                </Box>
                <Stack direction="row" spacing={1} sx={{ alignSelf: { xs: 'flex-end', sm: 'center' } }}>
                  <IconButton size="small" onClick={() => handleOpenProductDrawer(product)} disabled={actionLoading}>
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton size="small" color="error" onClick={() => handleProductDelete(product.id)} disabled={actionLoading}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Box>
            ))}
            {productHasNext && (
              <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
                <Button variant="outlined" onClick={handleLoadMoreProducts} disabled={productsLoadingMore}>
                  {productsLoadingMore ? 'Загрузка...' : 'Показать ещё'}
                </Button>
              </Box>
            )}
                        {!products.length && (
              <Stack alignItems="center" spacing={2} sx={{ py: 6 }}>
                <InboxOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />
                <Typography color="text.secondary">Каталог пуст</Typography>
                <Button variant="contained" startIcon={<AddIcon />} onClick={() => handleOpenProductDrawer()}>
                  Добавить первый товар
                </Button>
              </Stack>
            )}
          </Paper>
        </Box>
      )}

      {showSection('manager-users') && (
        <Box>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h5" fontWeight={700} gutterBottom sx={{ letterSpacing: '-0.02em' }}>Пользователи</Typography>
            <Typography variant="body2" color="text.secondary">Регистрация директоров магазинов</Typography>
          </Box>

          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 4 }}>
              <Paper variant="outlined" sx={{ p: 3, borderRadius: 2.5 }}>
                <Typography variant="h6" gutterBottom>Новый директор</Typography>
                <Stack spacing={2}>
                  <TextField
                    label="Логин"
                    fullWidth
                    size="small"
                    value={newDirector.username}
                    onChange={(e) => setNewDirector({ ...newDirector, username: e.target.value })}
                  />
                  <TextField
                    label="Пароль"
                    type="password"
                    fullWidth
                    size="small"
                    value={newDirector.password}
                    onChange={(e) => setNewDirector({ ...newDirector, password: e.target.value })}
                  />
                  <TextField
                    label="ФИО"
                    fullWidth
                    size="small"
                    value={newDirector.fullName}
                    onChange={(e) => setNewDirector({ ...newDirector, fullName: e.target.value })}
                  />
                  <TextField
                    label="Телефон"
                    fullWidth
                    size="small"
                    value={newDirector.phone}
                    onChange={(e) => setNewDirector({ ...newDirector, phone: e.target.value })}
                  />
                  <TextField
                    label="Юр. лицо"
                    fullWidth
                    size="small"
                    value={newDirector.legalEntityName}
                    onChange={(e) => setNewDirector({ ...newDirector, legalEntityName: e.target.value })}
                  />
                  <Button variant="contained" fullWidth onClick={handleDirectorCreate} disabled={actionLoading}>
                    Создать пользователя
                  </Button>
                </Stack>
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}>
              <Paper variant="outlined" sx={{ borderRadius: 2.5 }}>
                <TableContainer sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                      <TableRow>
                        <TableCell>ID</TableCell>
                        <TableCell>Логин</TableCell>
                        <TableCell>ФИО</TableCell>
                        <TableCell>Телефон</TableCell>
                        <TableCell>Юр. лицо</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {directors.map((d) => (
                        <TableRow key={d.id}>
                          <TableCell>{d.id}</TableCell>
                          <TableCell>{d.username}</TableCell>
                          <TableCell>{d.fullName}</TableCell>
                          <TableCell>{d.phone || '-'}</TableCell>
                          <TableCell>{d.legalEntityName || '-'}</TableCell>
                        </TableRow>
                      ))}
                      {!directors.length && (
                        <TableRow>
                          <TableCell colSpan={5} align="center" sx={{ py: 5 }}>
                            <Stack alignItems="center" spacing={1}>
                              <GroupIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
                              <Typography color="text.secondary">Нет зарегистрированных директоров</Typography>
                              <Typography variant="caption" color="text.disabled">
                                Создайте директора с помощью формы слева
                              </Typography>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Paper>
            </Grid>
          </Grid>
        </Box>
      )}

      {showSection('manager-reports') && (
        <Box>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h5" fontWeight={700} gutterBottom sx={{ letterSpacing: '-0.02em' }}>Отчёты</Typography>
            <Typography variant="body2" color="text.secondary">Экспорт данных в Excel</Typography>
          </Box>
          <Paper variant="outlined" sx={{ p: 3, maxWidth: 600, borderRadius: 2.5 }}>
            <Stack spacing={3}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <TextField
                    label="Период с"
                    type="date"
                    fullWidth
                    InputLabelProps={{ shrink: true }}
                    value={reportFrom}
                    onChange={(e) => setReportFrom(e.target.value)}
                  />
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <TextField
                    label="Период по"
                    type="date"
                    fullWidth
                    InputLabelProps={{ shrink: true }}
                    value={reportTo}
                    onChange={(e) => setReportTo(e.target.value)}
                  />
                </Grid>
              </Grid>
              <TextField
                select
                label="Статус заказа"
                fullWidth
                value={reportStatus}
                onChange={(e) => setReportStatus(e.target.value)}
              >
                <MenuItem value="">Все статусы</MenuItem>
                <MenuItem value="CREATED">Создан</MenuItem>
                <MenuItem value="APPROVED">Одобрен</MenuItem>
                <MenuItem value="ASSIGNED">Назначен</MenuItem>
                <MenuItem value="DELIVERED">Доставлен</MenuItem>
              </TextField>
              <Button
                variant="contained"
                size="large"
                startIcon={<DownloadIcon />}
                onClick={handleDownloadReport}
                disabled={actionLoading}
              >
                Скачать отчёт (.xlsx)
              </Button>
            </Stack>
          </Paper>
        </Box>
      )}
      <Drawer
        anchor="right"
        open={productDrawerOpen}
        onClose={() => setProductDrawerOpen(false)}
        PaperProps={{ sx: { width: { xs: '100%', sm: 420 }, p: 3 } }}
      >
        <Stack spacing={2}>
          <Typography variant="h6" fontWeight={700}>
            {editingProductId ? 'Редактирование товара' : 'Новый товар'}
          </Typography>
          <TextField
            label="Наименование"
            fullWidth
            value={productDraft.name}
            onChange={(e) => setProductDraft({ ...productDraft, name: e.target.value })}
          />
          <TextField
            label="Категория"
            fullWidth
            value={productDraft.category}
            onChange={(e) => setProductDraft({ ...productDraft, category: e.target.value })}
            helperText="Например: Овощи, Молочка"
          />
          <TextField
            label="Описание"
            fullWidth
            multiline
            rows={3}
            value={productDraft.description}
            onChange={(e) => setProductDraft({ ...productDraft, description: e.target.value })}
          />
          <Grid container spacing={2}>
            <Grid size={{ xs: 6 }}>
              <TextField
                label="Цена"
                type="number"
                fullWidth
                value={productDraft.price}
                onChange={(e) => setProductDraft({ ...productDraft, price: e.target.value })}
              />
            </Grid>
            <Grid size={{ xs: 6 }}>
              <TextField
                label="Количество"
                type="number"
                fullWidth
                value={productDraft.stockQuantity}
                onChange={(e) => setProductDraft({ ...productDraft, stockQuantity: e.target.value })}
              />
            </Grid>
          </Grid>
          <TextField
            label="Ссылка на фото"
            fullWidth
            value={productDraft.photoUrl}
            onChange={(e) => setProductDraft({ ...productDraft, photoUrl: e.target.value })}
          />
          {productDraft.photoUrl && (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 1, border: '1px dashed grey', borderRadius: 1 }}>
              <ProductImage
                src={productDraft.photoUrl}
                alt="Предпросмотр"
                width="100%"
                height={150}
                borderRadius={1}
                priority
              />
            </Box>
          )}
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button onClick={() => setProductDrawerOpen(false)}>Отмена</Button>
            <Button variant="contained" onClick={handleProductSave} disabled={actionLoading}>
              Сохранить
            </Button>
          </Stack>
        </Stack>
      </Drawer>

      <Dialog open={timelineOpen} onClose={() => setTimelineOpen(false)} maxWidth="md" fullWidth fullScreen={isMobile}>
        <DialogTitle>История заказа #{timelineOrderId}</DialogTitle>
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
