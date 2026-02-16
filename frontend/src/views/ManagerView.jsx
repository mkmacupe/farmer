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
  DragIndicator as DragIndicatorIcon,
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  WarningAmber as WarningAmberIcon
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
const KANBAN_COLUMN_BY_ID = Object.fromEntries(KANBAN_COLUMNS.map((column) => [column.id, column]));
const STATUS_LABELS = {
  CREATED: 'Создан',
  APPROVED: 'Одобрен',
  ASSIGNED: 'Назначен',
  DELIVERED: 'Доставлен'
};

function todayDateValue() {
  return new Date().toISOString().slice(0, 10);
}

function isMethodNotAllowedError(error) {
  const message = String(error?.message || '').toLowerCase();
  return message.includes('method not allowed') || message.includes('405');
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status || '-';
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

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_DASHBOARD_DAYS = 31;
const CATEGORY_COLORS = ['#1d4ed8', '#15803d', '#92400e', '#7c3aed', '#be123c', '#0f766e'];

function parseDateInput(value) {
  if (!value) return null;
  const parsed = new Date(`${value}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function startOfDay(value) {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date;
}

function dayKey(value) {
  return startOfDay(value).toISOString().slice(0, 10);
}

function formatDayLabel(value) {
  return value.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
}

function orderTimestamp(order) {
  const candidates = [order.createdAt, order.updatedAt, order.approvedAt, order.assignedAt, order.deliveredAt];
  for (const candidate of candidates) {
    if (!candidate) continue;
    const parsed = new Date(candidate);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.getTime();
    }
  }
  return null;
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

  const orderStats = useMemo(() => {
    const stats = {
      CREATED: 0,
      APPROVED: 0,
      ASSIGNED: 0,
      DELIVERED: 0
    };
    for (const order of orders) {
      if (Object.prototype.hasOwnProperty.call(stats, order.status)) {
        stats[order.status] += 1;
      }
    }
    return stats;
  }, [orders]);

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

  const dashboardRange = useMemo(() => {
    const fallbackTo = startOfDay(new Date());
    const parsedTo = parseDateInput(dashboardTo) || fallbackTo;
    const parsedFrom = parseDateInput(dashboardFrom);
    let from = parsedFrom || new Date(parsedTo.getTime() - (7 - 1) * DAY_MS);
    let to = parsedTo;
    if (from > to) {
      [from, to] = [to, from];
    }
    const rawDays = Math.floor((startOfDay(to) - startOfDay(from)) / DAY_MS) + 1;
    if (rawDays > MAX_DASHBOARD_DAYS) {
      from = new Date(to.getTime() - (MAX_DASHBOARD_DAYS - 1) * DAY_MS);
    }
    const normalizedFrom = startOfDay(from);
    const normalizedTo = startOfDay(to);
    return {
      from: normalizedFrom,
      to: normalizedTo,
      days: Math.floor((normalizedTo - normalizedFrom) / DAY_MS) + 1
    };
  }, [dashboardFrom, dashboardTo]);

  const trendSeries = useMemo(() => {
    const buckets = new Map();
    orders.forEach((order) => {
      const timestamp = orderTimestamp(order);
      if (timestamp == null) return;
      const day = startOfDay(timestamp);
      if (day < dashboardRange.from || day > dashboardRange.to) return;
      const key = dayKey(day);
      const current = buckets.get(key) || { orders: 0, revenue: 0, delivered: 0 };
      const amount = Number(order.totalAmount || 0);
      current.orders += 1;
      current.revenue += Number.isFinite(amount) ? amount : 0;
      if (order.status === 'DELIVERED') {
        current.delivered += 1;
      }
      buckets.set(key, current);
    });

    const series = [];
    for (let day = new Date(dashboardRange.from); day <= dashboardRange.to; day = new Date(day.getTime() + DAY_MS)) {
      const key = dayKey(day);
      const bucket = buckets.get(key);
      series.push({
        key,
        label: formatDayLabel(day),
        orders: bucket?.orders || 0,
        revenue: bucket?.revenue || 0,
        delivered: bucket?.delivered || 0
      });
    }
    return series;
  }, [orders, dashboardRange]);

  const trendMeta = useMemo(() => {
    const totalOrders = trendSeries.reduce((sum, point) => sum + point.orders, 0);
    const averageOrders = trendSeries.length ? totalOrders / trendSeries.length : 0;
    const latestOrders = trendSeries.length ? trendSeries[trendSeries.length - 1].orders : 0;
    const previousOrders = trendSeries.length > 1 ? trendSeries[trendSeries.length - 2].orders : latestOrders;
    const deviation = latestOrders - averageOrders;
    const deviationPercent = averageOrders ? (deviation / averageOrders) * 100 : 0;
    const momentum = latestOrders - previousOrders;
    return {
      latestOrders,
      averageOrders,
      deviationPercent,
      momentum,
      totalRevenue: trendSeries.reduce((sum, point) => sum + point.revenue, 0)
    };
  }, [trendSeries]);

  const trendChartModel = useMemo(() => {
    const width = 640;
    const height = 220;
    const left = 36;
    const right = 20;
    const top = 18;
    const bottom = 32;
    const plotWidth = width - left - right;
    const plotHeight = height - top - bottom;
    const maxValue = Math.max(1, ...trendSeries.map((point) => point.orders));
    const points = trendSeries.map((point, index) => {
      const x = left + (trendSeries.length <= 1 ? plotWidth / 2 : (index / (trendSeries.length - 1)) * plotWidth);
      const y = top + plotHeight - ((point.orders / maxValue) * plotHeight);
      return { ...point, x, y };
    });
    const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
    const baseY = top + plotHeight;
    const areaPath = points.length > 1
      ? `${linePath} L ${points[points.length - 1].x} ${baseY} L ${points[0].x} ${baseY} Z`
      : '';
    const tickIndexes = new Set([0, points.length - 1]);
    if (points.length > 2) tickIndexes.add(Math.floor((points.length - 1) / 2));
    if (points.length > 6) {
      tickIndexes.add(Math.floor((points.length - 1) / 3));
      tickIndexes.add(Math.floor((points.length - 1) * 2 / 3));
    }
    const ticks = [...tickIndexes]
      .filter((index) => index >= 0 && index < points.length)
      .sort((a, b) => a - b)
      .map((index) => points[index]);

    return {
      width,
      height,
      top,
      baseY,
      maxValue,
      points,
      linePath,
      areaPath,
      ticks
    };
  }, [trendSeries]);

  const productCategoryById = useMemo(() => {
    const mapping = new Map();
    products.forEach((product) => {
      mapping.set(Number(product.id), product.category || 'Без категории');
    });
    return mapping;
  }, [products]);

  const categoryInsights = useMemo(() => {
    const totals = new Map();
    orders.forEach((order) => {
      (order.items || []).forEach((item) => {
        const quantity = Number(item.quantity || 0);
        if (!Number.isFinite(quantity) || quantity <= 0) return;
        const category = (
          item.category
          || item.productCategory
          || productCategoryById.get(Number(item.productId))
          || 'Без категории'
        );
        totals.set(category, (totals.get(category) || 0) + quantity);
      });
    });

    const sorted = [...totals.entries()].sort((a, b) => b[1] - a[1]);
    const visible = sorted.slice(0, 4);
    if (sorted.length > 4) {
      const rest = sorted.slice(4).reduce((sum, [, value]) => sum + value, 0);
      visible.push(['Остальные', rest]);
    }

    const totalUnits = visible.reduce((sum, [, value]) => sum + value, 0);
    let currentDeg = 0;
    const segments = visible.map(([name, value], index) => {
      const share = totalUnits ? value / totalUnits : 0;
      const start = currentDeg;
      const end = currentDeg + share * 360;
      currentDeg = end;
      return {
        name,
        value,
        share,
        start,
        end,
        color: CATEGORY_COLORS[index % CATEGORY_COLORS.length]
      };
    });

    const gradient = totalUnits
      ? `conic-gradient(${segments.map((segment) => `${segment.color} ${segment.start}deg ${segment.end}deg`).join(', ')})`
      : null;

    return {
      totalUnits,
      segments,
      gradient
    };
  }, [orders, productCategoryById]);

  const attentionItems = useMemo(() => {
    const items = [];
    if (orderStats.CREATED > 0) {
      items.push({
        severity: 'warning',
        title: 'Ожидают одобрения',
        description: `${orderStats.CREATED} заявок остаются в статусе "Создан".`
      });
    }
    if (orderStats.APPROVED > 0) {
      items.push({
        severity: 'warning',
        title: 'Нужны назначения водителей',
        description: `${orderStats.APPROVED} одобренных заказов ждут логиста.`
      });
    }
    const now = Date.now();
    const staleAssigned = orders.filter((order) => {
      if (order.status !== 'ASSIGNED') return false;
      const timestamp = orderTimestamp(order);
      return timestamp != null && (now - timestamp) >= DAY_MS;
    }).length;
    if (staleAssigned > 0) {
      items.push({
        severity: 'info',
        title: 'Задержка в доставке',
        description: `${staleAssigned} заказов в статусе "Назначен" дольше 24 часов.`
      });
    }
    if (Math.abs(trendMeta.deviationPercent) >= 25) {
      const direction = trendMeta.deviationPercent > 0 ? 'выше' : 'ниже';
      items.push({
        severity: 'info',
        title: 'Отклонение от среднего потока',
        description: `Сегодняшний поток на ${Math.abs(trendMeta.deviationPercent).toFixed(0)}% ${direction} среднего по периоду.`
      });
    }
    if (!items.length) {
      items.push({
        severity: 'success',
        title: 'Критичных отклонений нет',
        description: 'Очередь заявок и доставки находятся в рабочем диапазоне.'
      });
    }
    return items.slice(0, 3);
  }, [orderStats, orders, trendMeta.deviationPercent]);

  const showMessage = (message, severity = 'success') => {
    setSnackbar({ open: true, message, severity });
  };

  const loadOrders = async () => {
    const ordersData = await getAllOrders(token);
    setOrders(ordersData);
  };

  const loadProductsAndCategories = async () => {
    const [productsPage, categoriesData] = await Promise.all([
      getProductsPage(token, { page: 0, size: productPageSize }),
      getProductCategories(token)
    ]);
    setProducts(filterLocalizedProducts(productsPage.items));
    setProductPage(productsPage.page);
    setProductHasNext(productsPage.hasNext);
    setProductTotalItems(productsPage.totalItems);
    setCategories(filterLocalizedCategories(categoriesData));
  };

  const loadUsers = async () => {
    const [directorsResult, driversResult] = await Promise.allSettled([
      getDirectors(token),
      getDrivers(token)
    ]);

    if (directorsResult.status === 'fulfilled') {
      setDirectors(directorsResult.value);
    } else if (isMethodNotAllowedError(directorsResult.reason)) {
      setDirectors([]);
    } else {
      console.error('Failed to load directors', directorsResult.reason);
    }

    if (driversResult.status === 'fulfilled') {
      setDrivers(driversResult.value);
    } else if (isMethodNotAllowedError(driversResult.reason)) {
      setDrivers([]);
    } else {
      console.error('Failed to load drivers', driversResult.reason);
    }
  };

  const loadMain = async () => {
    await Promise.all([loadOrders(), loadProductsAndCategories(), loadUsers()]);
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
      await Promise.all([loadOrders(), loadDashboard()]);
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
      await loadProductsAndCategories();
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
      await loadProductsAndCategories();
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
      await loadUsers();
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
    const columnMeta = KANBAN_COLUMN_BY_ID[order.status];
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
            opacity: 1,
            transform: 'none'
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
                inputProps={{ 'aria-label': 'Период аналитики с' }}
              />
              <TextField
                label="Период по"
                type="date"
                size="small"
                InputLabelProps={{ shrink: true }}
                value={dashboardTo}
                onChange={(e) => setDashboardTo(e.target.value)}
                inputProps={{ 'aria-label': 'Период аналитики по' }}
              />
              <Button variant="contained" onClick={loadDashboard} disabled={loading} aria-label="Применить фильтр аналитики">
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

          <Paper sx={{ p: 2.5, mb: 3, borderRadius: 2.5, border: '1px solid', borderColor: 'divider' }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ sm: 'center' }}>
              <Typography variant="subtitle1" fontWeight={700}>
                Что требует внимания сейчас
              </Typography>
              <Chip
                size="small"
                color={attentionItems[0]?.severity === 'success' ? 'success' : 'warning'}
                label={attentionItems[0]?.severity === 'success' ? 'Стабильно' : `${attentionItems.length} зоны внимания`}
              />
            </Stack>
            <Stack spacing={1.25} sx={{ mt: 2 }}>
              {attentionItems.map((item) => (
                <Alert
                  key={item.title}
                  severity={item.severity}
                  variant="outlined"
                  icon={item.severity === 'success' ? false : <WarningAmberIcon fontSize="inherit" />}
                  sx={{ alignItems: 'flex-start' }}
                >
                  <Typography variant="subtitle2" fontWeight={700}>
                    {item.title}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {item.description}
                  </Typography>
                </Alert>
              ))}
            </Stack>
          </Paper>

          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid size={{ xs: 12, md: 8 }}>
              <Paper sx={{ p: 2.5, borderRadius: 2.5, height: '100%', border: '1px solid', borderColor: 'divider' }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ sm: 'center' }}>
                  <Box>
                    <Typography variant="subtitle1" fontWeight={700}>
                      Тренд заказов
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Период: {formatDayLabel(dashboardRange.from)} - {formatDayLabel(dashboardRange.to)}
                    </Typography>
                  </Box>
                  <Chip
                    size="small"
                    icon={trendMeta.momentum >= 0 ? <TrendingUpIcon /> : <TrendingDownIcon />}
                    color={trendMeta.momentum >= 0 ? 'success' : 'warning'}
                    label={trendMeta.momentum >= 0 ? 'Рост к вчера' : 'Снижение к вчера'}
                  />
                </Stack>
                <Box
                  sx={{
                    mt: 2,
                    borderRadius: 2,
                    border: '1px solid',
                    borderColor: 'divider',
                    bgcolor: 'background.default',
                    p: 1
                  }}
                >
                  {trendChartModel.points.length ? (
                    <Box
                      component="svg"
                      viewBox={`0 0 ${trendChartModel.width} ${trendChartModel.height}`}
                      role="img"
                      aria-label="График тренда заказов по дням"
                      sx={{ width: '100%', height: 220, display: 'block' }}
                    >
                      {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
                        const y = trendChartModel.top + ((trendChartModel.baseY - trendChartModel.top) * ratio);
                        return (
                          <line
                            key={`grid-${ratio}`}
                            x1={36}
                            y1={y}
                            x2={trendChartModel.width - 20}
                            y2={y}
                            stroke="#e4e4e7"
                            strokeWidth="1"
                          />
                        );
                      })}
                      {trendChartModel.areaPath && (
                        <path
                          d={trendChartModel.areaPath}
                          fill="rgba(46, 91, 78, 0.12)"
                          stroke="none"
                        />
                      )}
                      {trendChartModel.linePath && (
                        <path
                          d={trendChartModel.linePath}
                          fill="none"
                          stroke="#2E5B4E"
                          strokeWidth="3"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      )}
                      {trendChartModel.points.map((point) => (
                        <circle
                          key={point.key}
                          cx={point.x}
                          cy={point.y}
                          r="4"
                          fill="#2E5B4E"
                          stroke="#ffffff"
                          strokeWidth="2"
                        />
                      ))}
                      {trendChartModel.ticks.map((tick) => (
                        <text
                          key={`tick-${tick.key}`}
                          x={tick.x}
                          y={trendChartModel.height - 8}
                          textAnchor="middle"
                          fill="#71717a"
                          fontSize="11"
                        >
                          {tick.label}
                        </text>
                      ))}
                    </Box>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      Нет данных для построения тренда в выбранном периоде.
                    </Typography>
                  )}
                </Box>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    Среднее: {trendMeta.averageOrders.toFixed(1)} заявок/день
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Отклонение: {trendMeta.deviationPercent >= 0 ? '+' : ''}{trendMeta.deviationPercent.toFixed(0)}%
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Выручка периода: {formatMoney(trendMeta.totalRevenue)} BYN
                  </Typography>
                </Stack>
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <Paper sx={{ p: 2.5, borderRadius: 2.5, height: '100%', border: '1px solid', borderColor: 'divider' }}>
                <Typography variant="subtitle1" fontWeight={700} gutterBottom>
                  Структура спроса
                </Typography>
                {categoryInsights.totalUnits > 0 ? (
                  <Stack spacing={2} sx={{ mt: 1.5 }}>
                    <Box
                      sx={{
                        width: 168,
                        height: 168,
                        borderRadius: '50%',
                        mx: 'auto',
                        position: 'relative',
                        background: categoryInsights.gradient
                      }}
                      role="img"
                      aria-label="Распределение заказов по категориям"
                    >
                      <Box
                        sx={{
                          position: 'absolute',
                          inset: 28,
                          borderRadius: '50%',
                          bgcolor: 'background.paper',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          textAlign: 'center',
                          px: 1
                        }}
                      >
                        <Typography variant="subtitle2" fontWeight={700}>
                          {categoryInsights.totalUnits}
                          <br />
                          ед.
                        </Typography>
                      </Box>
                    </Box>
                    <Stack spacing={0.9}>
                      {categoryInsights.segments.map((segment) => (
                        <Stack key={segment.name} direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                          <Stack direction="row" alignItems="center" spacing={1}>
                            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: segment.color }} />
                            <Typography variant="caption" color="text.secondary">
                              {segment.name}
                            </Typography>
                          </Stack>
                          <Typography variant="caption" fontWeight={700}>
                            {segment.value} ({(segment.share * 100).toFixed(0)}%)
                          </Typography>
                        </Stack>
                      ))}
                    </Stack>
                  </Stack>
                ) : (
                  <Box
                    sx={{
                      mt: 2,
                      p: 2,
                      borderRadius: 2,
                      border: '1px dashed',
                      borderColor: 'divider',
                      bgcolor: 'background.default'
                    }}
                  >
                    <Typography variant="caption" color="text.secondary">
                      Категории появятся после заказов с позициями.
                    </Typography>
                  </Box>
                )}
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
