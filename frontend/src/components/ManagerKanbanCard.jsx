import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { alpha, useTheme } from "@mui/material/styles";
import CheckIcon from "@mui/icons-material/Check";
import HistoryIcon from "@mui/icons-material/History";
import { formatMoney } from "../utils/formatters.js";

function statusLabel(status) {
  const labels = {
    CREATED: "Создан",
    APPROVED: "Одобрен",
    ASSIGNED: "Назначен",
    DELIVERED: "Доставлен",
  };
  return labels[status] || status || "-";
}

function formatDateTime(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleString("ru-RU");
}

function orderStoreName(order) {
  return order?.storeName || order?.customerName || "Магазин";
}

function orderDirectorName(order) {
  return order?.customerName || "Директор магазина";
}

function orderInitials(order) {
  const source = orderStoreName(order).replace(/["«»]/g, " ");
  const parts = source
    .split(/\s+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .slice(0, 2);
  if (!parts.length) {
    return "М";
  }
  return parts.map((part) => part[0]?.toUpperCase() || "").join("");
}

function stabilizeProductLabel(value) {
  return String(value || "").replace(
    /(\d+(?:[.,]\d+)?)\s+(кг|г|л|мл|шт|%)/giu,
    "$1\u00a0$2",
  ).replace(/\s+×\s+(\d+)/gu, "\u00a0×\u00a0$1");
}

function orderAmountBreakdown(items) {
  if (!Array.isArray(items) || !items.length) {
    return [];
  }
  return items.map((item, index) => ({
    key: `${item.productId ?? "item"}-${index}`,
    label: `${stabilizeProductLabel(item.productName || "Товар")} × ${item.quantity || 0}`,
    total: `${formatMoney(item.lineTotal)} BYN`,
  }));
}

export default function ManagerKanbanCard({
  order,
  isMobile,
  draggingOrderId,
  actionLoading,
  onDragStart,
  onDragEnd,
  onApprove,
  onLoadTimeline,
  columnMeta,
}) {
  const theme = useTheme();
  const hasQuickApprove = order.status === "CREATED";
  const dragEnabled = !isMobile && order.status === "CREATED";
  const createdAtLabel = formatDateTime(order.createdAt);
  const amountBreakdown = orderAmountBreakdown(order.items);

  return (
    <Card
      draggable={dragEnabled}
      onDragStart={
        dragEnabled ? (event) => onDragStart(event, order.id) : undefined
      }
      onDragEnd={dragEnabled ? onDragEnd : undefined}
      sx={{
        display: "flex",
        flexDirection: "column",
        border: "1px solid",
        borderColor:
          draggingOrderId === order.id
            ? "primary.main"
            : alpha(theme.palette.divider, 0.9),
        borderRadius: 2.5,
        boxShadow: draggingOrderId === order.id
          ? `0 0 0 1px ${alpha(theme.palette.primary.main, 0.16)}`
          : "none",
        backgroundColor: theme.palette.background.paper,
        cursor: dragEnabled ? "grab" : "default",
        "&:active": { cursor: dragEnabled ? "grabbing" : "default" },
        "& .kanban-actions": {
          opacity: 1,
          transform: "none",
        },
      }}
    >
      <CardContent sx={{ pb: 1.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
        <Stack
          direction="row"
          alignItems="flex-start"
          justifyContent="space-between"
          spacing={1}
        >
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1.2 }}>
              Заказ #{order.id}
            </Typography>
            {!!createdAtLabel && (
              <Typography variant="caption" color="text.secondary" display="block">
                {createdAtLabel}
              </Typography>
            )}
          </Box>
          <Chip
            label={statusLabel(order.status)}
            size="small"
            color={columnMeta?.color || "default"}
          />
        </Stack>
        <Stack direction="row" spacing={1.25} alignItems="flex-start">
          <Avatar
            sx={{
              width: 44,
              height: 44,
              bgcolor: alpha(theme.palette.primary.main, 0.14),
              color: "primary.dark",
              fontWeight: 700,
              fontSize: "0.95rem",
            }}
          >
            {orderInitials(order)}
          </Avatar>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="subtitle2" fontWeight={700}>
              {orderStoreName(order)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Директор: {orderDirectorName(order)}
            </Typography>
          </Box>
        </Stack>
        <Paper
          variant="outlined"
          sx={{
            p: 1.25,
            borderRadius: 2.5,
            bgcolor: theme.palette.background.default,
            borderColor: alpha(theme.palette.divider, 0.8),
          }}
        >
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.4 }}>
            Адрес доставки
          </Typography>
          <Typography
            variant="body2"
            sx={{
              minHeight: 24,
            }}
          >
            {order.deliveryAddressText || "Адрес не указан"}
          </Typography>
        </Paper>
        <Paper
          variant="outlined"
          sx={{
            p: 1.25,
            borderRadius: 2.5,
            bgcolor: theme.palette.background.paper,
            borderColor: alpha(theme.palette.divider, 0.78),
          }}
        >
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
            Расчёт суммы заказа
          </Typography>
          <Stack spacing={0.5}>
            {amountBreakdown.length ? (
              amountBreakdown.map((row) => (
                <Box
                  key={row.key}
                  sx={{
                    display: "grid",
                    gridTemplateColumns: "minmax(0, 1fr) auto",
                    columnGap: 1.25,
                    alignItems: "start",
                    minHeight: 44,
                  }}
                >
                  <Typography
                    variant="body2"
                    sx={{
                      minWidth: 0,
                      lineHeight: 1.4,
                      overflowWrap: "normal",
                      wordBreak: "keep-all",
                      hyphens: "none",
                      whiteSpace: "normal",
                    }}
                  >
                    {row.label}
                  </Typography>
                  <Typography
                    variant="body2"
                    fontWeight={600}
                    sx={{
                      whiteSpace: "nowrap",
                      textAlign: "right",
                      minWidth: 88,
                    }}
                  >
                    {row.total}
                  </Typography>
                </Box>
              ))
            ) : (
              <Typography variant="body2" color="text.secondary">
                Нет позиций для расчёта
              </Typography>
            )}
            <Box
              sx={{
                mt: 0.75,
                pt: 0.75,
                borderTop: "1px dashed",
                borderColor: "divider",
                display: "flex",
                justifyContent: "space-between",
                gap: 1,
              }}
            >
              <Typography variant="subtitle2" fontWeight={700}>
                Итого
              </Typography>
              <Typography variant="subtitle2" fontWeight={700}>
                {formatMoney(order.totalAmount)} BYN
              </Typography>
            </Box>
          </Stack>
        </Paper>
      </CardContent>
      <Box
        className="kanban-actions"
        sx={{
          px: 2,
          pb: 2,
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(152px, 1fr))",
          gap: 1,
        }}
      >
        {hasQuickApprove && (
          <Button
            size="small"
            variant="contained"
            startIcon={<CheckIcon />}
            onClick={() => onApprove(order.id)}
            disabled={actionLoading}
            fullWidth
          >
            Одобрить
          </Button>
        )}
        <Button
          size="small"
          variant="outlined"
          startIcon={<HistoryIcon />}
          onClick={() => onLoadTimeline(order.id)}
          disabled={actionLoading}
          fullWidth
        >
          История
        </Button>
      </Box>
    </Card>
  );
}
