import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { formatMoney } from "../utils/formatters.js";

function formatShortDate(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function orderStatusLabel(status) {
  if (status === "DELIVERED") return "Доставлен";
  if (status === "ASSIGNED") return "На маршруте";
  if (status === "APPROVED") return "Одобрен";
  return "Создан";
}

export default function DirectorProfileOverview({
  selectedTotal,
  activeOrdersCount,
  recentOrders,
}) {
  return (
    <Box
      sx={{
        p: { xs: 2.5, md: 3 },
        borderRadius: 3,
        border: "1px solid",
        borderColor: "divider",
        bgcolor: "background.paper",
      }}
    >
      <Grid container spacing={3} alignItems="center">
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="overline" sx={{ color: "text.secondary" }}>
            Панель директора
          </Typography>
          <Typography
            variant="h5"
            component="h2"
            fontWeight={600}
            sx={{ mt: 0.5, mb: 0.5, letterSpacing: "-0.02em" }}
          >
            Закупки без лишних шагов
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Сводка по заказам, текущая корзина и уведомления.
          </Typography>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Box
              sx={{
                flex: 1,
                p: 2,
                borderRadius: 2.5,
                bgcolor: "#f0fdf4",
                border: "1px solid #dcfce7",
              }}
            >
              <Typography
                variant="caption"
                color="text.secondary"
                fontWeight={500}
              >
                Корзина
              </Typography>
              <Typography
                variant="h5"
                component="p"
                fontWeight={600}
                sx={{ mt: 0.5, color: "primary.main" }}
              >
                {formatMoney(selectedTotal)}{" "}
                <Typography
                  component="span"
                  variant="body2"
                  fontWeight={500}
                >
                  BYN
                </Typography>
              </Typography>
            </Box>
            <Box
              sx={{
                flex: 1,
                p: 2,
                borderRadius: 2.5,
                bgcolor: "#f4f4f5",
                border: "1px solid #e4e4e7",
              }}
            >
              <Typography
                variant="caption"
                color="text.secondary"
                fontWeight={500}
              >
                Активные заказы
              </Typography>
              <Typography variant="h5" component="p" fontWeight={600} sx={{ mt: 0.5 }}>
                {activeOrdersCount}
              </Typography>
            </Box>
          </Stack>
        </Grid>
      </Grid>

      <Box
        sx={{
          mt: 3,
          pt: 2.5,
          borderTop: "1px solid",
          borderColor: "divider",
        }}
      >
        <Typography
          variant="overline"
          sx={{ color: "text.secondary", mb: 1.5, display: "block" }}
        >
          Последние заказы
        </Typography>
        {recentOrders.length > 0 ? (
          <Stack spacing={1.5}>
            {recentOrders.map((order) => (
              <Box
                key={order.id}
                sx={{
                  p: 1.5,
                  borderRadius: 2,
                  border: "1px solid",
                  borderColor: "divider",
                  bgcolor: "background.default",
                }}
              >
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                >
                  <Typography variant="subtitle2" fontWeight={600}>
                    Заказ #{order.id}
                  </Typography>
                  <Chip
                    label={orderStatusLabel(order.status)}
                    size="small"
                    variant="outlined"
                  />
                </Stack>
                <Typography variant="body2" color="text.secondary" noWrap>
                  {order.deliveryAddressText || "Адрес не указан"}
                </Typography>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  sx={{ mt: 0.5 }}
                >
                  <Typography variant="caption" color="text.secondary">
                    {formatShortDate(order.createdAt) || "—"}
                  </Typography>
                  <Typography variant="subtitle2" fontWeight={600}>
                    {formatMoney(order.totalAmount)} BYN
                  </Typography>
                </Stack>
              </Box>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Пока нет заказов. Вы можете сформировать заказ из каталога.
          </Typography>
        )}
      </Box>
    </Box>
  );
}
