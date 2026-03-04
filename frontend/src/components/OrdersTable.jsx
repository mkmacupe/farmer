import { memo, useMemo, useState } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import InputAdornment from "@mui/material/InputAdornment";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";
import SearchIcon from "@mui/icons-material/Search";
import InboxOutlinedIcon from "@mui/icons-material/InboxOutlined";
import FilterListIcon from "@mui/icons-material/FilterList";
import ClearIcon from "@mui/icons-material/Clear";
import { OrderTableSkeleton } from "./LoadingSkeletons.jsx";
import { formatMoney } from "../utils/formatters.js";

const STATUS_META = {
  CREATED: {
    color: "secondary",
    label: "Создан",
  },
  APPROVED: {
    color: "warning",
    label: "Одобрен",
  },
  ASSIGNED: {
    color: "info",
    label: "Назначен",
  },
  DELIVERED: {
    color: "success",
    label: "Доставлен",
  },
  CANCELLED: {
    color: "error",
    label: "Отменён",
  },
};
const DEFAULT_MAX_RENDERED_ORDERS = 200;

function statusLabel(status) {
  return STATUS_META[status]?.label || status;
}

function formatDateTime(value) {
  if (!value) {
    return "—";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return String(value);
  }
  return parsed.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const OrderMobileCard = memo(function OrderMobileCard({ order, showCustomer, actionRenderer, onShowDetails }) {
  const statusMeta = STATUS_META[order.status] || {};
  const hasLongAddress = String(order.deliveryAddressText || "").trim().length > 72;
  const hasActions = typeof actionRenderer === "function";

  return (
    <Card variant="outlined" sx={{ borderRadius: 2.5 }}>
      <CardContent sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="subtitle2" fontWeight={600}>
            Заказ #{order.id}
          </Typography>
          <Chip
            label={statusMeta.label || order.status}
            color={statusMeta.color || "default"}
            size="small"
            variant="filled"
          />
        </Stack>
        {showCustomer && (
          <Typography variant="body2" fontWeight={600}>
            {order.customerName || "—"}
          </Typography>
        )}
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            display: "-webkit-box",
            WebkitLineClamp: 2,
            WebkitBoxOrient: "vertical",
            overflow: "hidden",
          }}
        >
          {order.deliveryAddressText || "—"}
        </Typography>
        {hasLongAddress && (
          <Button
            size="small"
            variant="text"
            onClick={() => onShowDetails(order)}
            sx={{ alignSelf: "flex-start", px: 0, minHeight: 32 }}
          >
            Показать адрес
          </Button>
        )}
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="caption" color="text.secondary">
            Водитель
          </Typography>
          <Typography variant="body2">
            {order.assignedDriverName || "—"}
          </Typography>
        </Stack>
        <Divider />
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="caption" color="text.secondary">
            Сумма
          </Typography>
          <Typography variant="subtitle2" fontWeight={600}>
            {formatMoney(order.totalAmount)} BYN
          </Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="caption" color="text.secondary">
            Позиций
          </Typography>
          <Chip
            label={order.items?.length || 0}
            size="small"
            sx={{
              minWidth: 32,
              height: 22,
              fontWeight: 600,
              fontSize: "0.75rem",
            }}
          />
        </Stack>
        <Typography variant="caption" color="text.secondary">
          {formatDateTime(order.createdAt)}
        </Typography>
        {hasActions && (
          <Box sx={{ pt: 0.5 }}>{actionRenderer(order)}</Box>
        )}
      </CardContent>
    </Card>
  );
});

const OrderTableRow = memo(function OrderTableRow({ order, showCustomer, actionRenderer, onShowDetails }) {
  const statusMeta = STATUS_META[order.status] || {};
  const hasActions = typeof actionRenderer === "function";

  return (
    <TableRow
      hover
      sx={{
        "&:last-child td, &:last-child th": { border: 0 },
        transition: "background-color 0.15s ease",
        contentVisibility: "auto",
        containIntrinsicSize: "auto 73px"
      }}
    >
      <TableCell>
        <Typography variant="subtitle2" fontWeight={600} color="primary">
          #{order.id}
        </Typography>
      </TableCell>
      {showCustomer && (
        <TableCell>
          <Typography
            variant="body2"
            fontWeight={500}
            title={order.customerName || "—"}
            sx={{
              maxWidth: 180,
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              overflow: "hidden",
            }}
          >
            {order.customerName}
          </Typography>
        </TableCell>
      )}
      <TableCell>
        <Stack spacing={0.25} sx={{ maxWidth: 260 }}>
          <Typography
            variant="body2"
            title={order.deliveryAddressText || "—"}
            sx={{
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              overflow: "hidden",
            }}
          >
            {order.deliveryAddressText || "—"}
          </Typography>
          {String(order.deliveryAddressText || "").trim().length > 72 && (
            <Button
              size="small"
              variant="text"
              onClick={() => onShowDetails(order)}
              sx={{ alignSelf: "flex-start", p: 0, minHeight: 28 }}
            >
              Подробнее
            </Button>
          )}
        </Stack>
      </TableCell>
      <TableCell>
        <Typography
          variant="body2"
          color={order.assignedDriverName ? "text.primary" : "text.disabled"}
        >
          {order.assignedDriverName || "—"}
        </Typography>
      </TableCell>
      <TableCell>
        <Chip
          label={statusMeta.label || order.status}
          color={statusMeta.color || "default"}
          size="small"
          variant="filled"
          sx={{ fontWeight: 600 }}
        />
      </TableCell>
      <TableCell>
        <Typography variant="caption" color="text.secondary">
          {formatDateTime(order.createdAt)}
        </Typography>
      </TableCell>
      <TableCell align="right">
        <Typography variant="subtitle2" fontWeight={600}>
          {formatMoney(order.totalAmount)}
        </Typography>
      </TableCell>
      <TableCell align="center">
        <Chip
          label={order.items?.length || 0}
          size="small"
          sx={{ minWidth: 32, height: 24, fontWeight: 600, fontSize: "0.75rem" }}
        />
      </TableCell>
      {hasActions && (
        <TableCell sx={{ minWidth: 280, verticalAlign: "top" }}>
          {actionRenderer(order)}
        </TableCell>
      )}
    </TableRow>
  );
});

export default memo(function OrdersTable({
  orders,
  actionRenderer,
  showCustomer = true,
  emptyText = "Заказов пока нет.",
  searchEnabled = true,
  loading = false,
  maxRendered = DEFAULT_MAX_RENDERED_ORDERS,
}) {
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [detailsOrder, setDetailsOrder] = useState(null);
  const hasActions = typeof actionRenderer === "function";
  const hasFilters = searchEnabled && orders.length > 1;
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const closeOrderDetails = () => setDetailsOrder(null);

  const filteredOrders = useMemo(() => {
    const normalizedSearch = searchTerm.trim().toLowerCase();
    return orders.filter((order) => {
      if (statusFilter && order.status !== statusFilter) {
        return false;
      }
      if (!normalizedSearch) {
        return true;
      }

      const searchable = [
        `#${order.id}`,
        order.customerName,
        order.deliveryAddressText,
        order.assignedDriverName,
        statusLabel(order.status),
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      return searchable.includes(normalizedSearch);
    });
  }, [orders, searchTerm, statusFilter]);
  const renderLimit = Number.isFinite(maxRendered)
    ? Math.max(1, Math.floor(maxRendered))
    : null;
  const visibleOrders = useMemo(
    () => (renderLimit ? filteredOrders.slice(0, renderLimit) : filteredOrders),
    [filteredOrders, renderLimit],
  );
  const hasRenderLimit = renderLimit
    ? filteredOrders.length > visibleOrders.length
    : false;

  const clearFilters = () => {
    setSearchTerm("");
    setStatusFilter("");
  };

  const hasActiveFilters = searchTerm || statusFilter;
  const detailsDialog = (
    <Dialog
      open={Boolean(detailsOrder)}
      onClose={closeOrderDetails}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>Детали заказа #{detailsOrder?.id ?? "—"}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={1.25}>
          {showCustomer && (
            <Box>
              <Typography variant="caption" color="text.secondary">
                Директор
              </Typography>
              <Typography variant="body2" fontWeight={600}>
                {detailsOrder?.customerName || "—"}
              </Typography>
            </Box>
          )}
          <Box>
            <Typography variant="caption" color="text.secondary">
              Адрес доставки
            </Typography>
            <Typography variant="body2">
              {detailsOrder?.deliveryAddressText || "—"}
            </Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">
              Водитель
            </Typography>
            <Typography variant="body2">
              {detailsOrder?.assignedDriverName || "—"}
            </Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">
              Дата создания
            </Typography>
            <Typography variant="body2">
              {formatDateTime(detailsOrder?.createdAt)}
            </Typography>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={closeOrderDetails}>Закрыть</Button>
      </DialogActions>
    </Dialog>
  );
  const filterContainerSx = {
    p: 2,
    borderBottom: isMobile ? "none" : "1px solid",
    borderColor: "divider",
    bgcolor: "background.paper",
    position: isMobile ? "static" : "sticky",
    top: isMobile ? "auto" : 0,
    zIndex: 2,
    borderRadius: isMobile ? 2 : 0,
    border: isMobile ? "1px solid" : "none",
  };

  if (loading) {
    return <OrderTableSkeleton rows={5} />;
  }

  if (!orders.length) {
    return (
      <Paper
        elevation={0}
        sx={{
          py: 8,
          px: 4,
          textAlign: "center",
          borderRadius: 3,
          background: "background.paper",
          border: "1px dashed",
          borderColor: "divider",
        }}
      >
        <InboxOutlinedIcon
          sx={{
            fontSize: 72,
            color: "text.disabled",
            mb: 2,
            opacity: 0.5,
          }}
        />
        <Typography
          variant="h6"
          color="text.secondary"
          fontWeight={600}
          gutterBottom
        >
          Заказов пока нет
        </Typography>
        <Typography variant="body2" color="text.disabled">
          {emptyText}
        </Typography>
      </Paper>
    );
  }

  const filtersNode = hasFilters ? (
    <Box sx={filterContainerSx}>
      <Stack
        direction={{ xs: "column", md: "row" }}
        spacing={2}
        alignItems={{ xs: "stretch", md: "center" }}
      >
        <TextField
          placeholder="Поиск по заказам..."
          size="small"
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          inputProps={{ "aria-label": "Поиск по заказам" }}
          sx={{
            minWidth: { xs: "100%", md: 280 },
            mb: 0,
          }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" color="action" />
              </InputAdornment>
            ),
            endAdornment: searchTerm && (
              <InputAdornment position="end">
                <IconButton
                  size="small"
                  onClick={() => setSearchTerm("")}
                  edge="end"
                  aria-label="Очистить поиск"
                >
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ),
          }}
        />

        <Stack
          direction="row"
          spacing={1.5}
          alignItems="center"
          sx={{ ml: { md: "auto" } }}
        >
          <TextField
            select
            size="small"
            label="Статус"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            SelectProps={{
              inputProps: { "aria-label": "Фильтр по статусу заказа" },
            }}
            sx={{ minWidth: { xs: "100%", sm: 190 } }}
          >
            <MenuItem value="">
              <Chip
                label="Все"
                size="small"
                color="secondary"
                sx={{ fontWeight: 600 }}
              />
            </MenuItem>
            {Object.entries(STATUS_META).map(([status, meta]) => (
              <MenuItem key={status} value={status}>
                <Chip
                  label={meta.label}
                  size="small"
                  color={meta.color}
                  variant="outlined"
                  sx={{ fontWeight: 600 }}
                />
              </MenuItem>
            ))}
          </TextField>
          <Typography
            variant="caption"
            color="text.secondary"
            whiteSpace="nowrap"
          >
            {filteredOrders.length} из {orders.length}
          </Typography>
          {hasRenderLimit && (
            <Typography variant="caption" color="warning.main" whiteSpace="nowrap">
              Показаны первые {renderLimit}
            </Typography>
          )}
          {hasActiveFilters && (
            <Button
              size="small"
              variant="outlined"
              onClick={clearFilters}
              startIcon={<ClearIcon fontSize="small" />}
              sx={{ whiteSpace: "nowrap" }}
            >
              Сбросить
            </Button>
          )}
        </Stack>
      </Stack>
    </Box>
  ) : null;

  if (isMobile) {
    return (
      <>
        <Stack spacing={2}>
        {filtersNode}
        {!filteredOrders.length ? (
          <Box
            sx={{
              p: 5,
              textAlign: "center",
              borderRadius: 2.5,
              border: "1px dashed",
              borderColor: "divider",
            }}
          >
            <FilterListIcon
              sx={{
                fontSize: 48,
                color: "text.disabled",
                mb: 1.5,
                opacity: 0.5,
              }}
            />
            <Typography variant="body1" color="text.secondary" fontWeight={500}>
              По фильтрам ничего не найдено
            </Typography>
            <Button size="small" onClick={clearFilters} sx={{ mt: 1.5 }}>
              Сбросить фильтры
            </Button>
          </Box>
        ) : (
          <Stack spacing={1.5}>
            {visibleOrders.map((order) => (
              <OrderMobileCard
                key={order.id}
                order={order}
                showCustomer={showCustomer}
                actionRenderer={actionRenderer}
                onShowDetails={setDetailsOrder}
              />
            ))}
          </Stack>
        )}
        </Stack>
        {detailsDialog}
      </>
    );
  }

  return (
    <>
      <Paper
        elevation={0}
        sx={{
          borderRadius: 2.5,
          overflow: "hidden",
          border: "1px solid",
          borderColor: "divider",
          overflowX: "auto",
          overflowY: "auto",
          maxHeight: 560,
        }}
      >
      {filtersNode}

      {!filteredOrders.length ? (
        <Box sx={{ p: 5, textAlign: "center" }}>
          <FilterListIcon
            sx={{ fontSize: 48, color: "text.disabled", mb: 1.5, opacity: 0.5 }}
          />
          <Typography variant="body1" color="text.secondary" fontWeight={500}>
            По фильтрам ничего не найдено
          </Typography>
          <Button size="small" onClick={clearFilters} sx={{ mt: 1.5 }}>
            Сбросить фильтры
          </Button>
        </Box>
      ) : (
        <Table
          stickyHeader
          sx={{
            minWidth: 700,
            tableLayout: "auto",
          }}
          size="small"
          aria-label="таблица заказов"
        >
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 600, width: 80 }}>№</TableCell>
              {showCustomer && (
                <TableCell sx={{ fontWeight: 600 }}>Директор</TableCell>
              )}
              <TableCell sx={{ fontWeight: 600 }}>Адрес доставки</TableCell>
              <TableCell sx={{ fontWeight: 600, width: 140 }}>
                Водитель
              </TableCell>
              <TableCell sx={{ fontWeight: 600, width: 120 }}>Статус</TableCell>
              <TableCell sx={{ fontWeight: 600, width: 140 }}>Дата</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, width: 100 }}>
                Сумма
              </TableCell>
              <TableCell align="center" sx={{ fontWeight: 600, width: 70 }}>
                Поз.
              </TableCell>
              {hasActions && (
                <TableCell sx={{ fontWeight: 600, width: 280, minWidth: 280 }}>
                  Действия
                </TableCell>
              )}
            </TableRow>
          </TableHead>
          <TableBody>
            {visibleOrders.map((order) => (
              <OrderTableRow
                key={order.id}
                order={order}
                showCustomer={showCustomer}
                actionRenderer={actionRenderer}
                onShowDetails={setDetailsOrder}
              />
            ))}
          </TableBody>
        </Table>
      )}
      </Paper>
      {detailsDialog}
    </>
  );
});
