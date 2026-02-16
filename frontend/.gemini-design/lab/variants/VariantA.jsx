import React from 'react';
import { Box, Grid, Paper, Typography, Divider, Stack, Chip, Button, LinearProgress } from '@mui/material';
import { kpiData, ordersData, inventoryData } from '../data/fixtures';

const statusTone = (status) => {
  if (status === 'DELIVERED') return 'success';
  if (status === 'SHIPPED') return 'info';
  return 'warning';
};

const statusLabel = (status) => {
  if (status === 'DELIVERED') return 'Доставлено';
  if (status === 'SHIPPED') return 'Отгружено';
  return 'В ожидании';
};

const kpiLabel = (title) => ({
  'Total Revenue': 'Выручка',
  'Active Orders': 'Активные заказы',
  'Pending Deliveries': 'Ожидают доставки',
  'Low Stock Items': 'Низкий остаток'
}[title] || title);

export default function VariantA() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Оперативный обзор</Typography>
          <Typography variant="body2" color="text.secondary">
            Сегодня - {new Date().toLocaleDateString('ru-RU')}
          </Typography>
        </Box>
        <Button size="small">Экспорт</Button>
      </Box>

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2}>
          {kpiData.map((kpi) => (
            <Grid item xs={6} md={3} key={kpi.title}>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                <Typography variant="caption" color="text.secondary">{kpiLabel(kpi.title)}</Typography>
                <Typography variant="h5" fontWeight={600}>{kpi.value}</Typography>
                <Typography
                  variant="caption"
                  color={kpi.status === 'warning' ? 'warning.main' : 'text.secondary'}
                >
                  {kpi.trend}
                </Typography>
              </Box>
            </Grid>
          ))}
        </Grid>
      </Paper>

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1" fontWeight={600}>Заказы</Typography>
              <Button size="small">Все заказы</Button>
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Stack spacing={1.5}>
              {ordersData.map((order) => (
                <Box key={order.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Box>
                    <Typography fontWeight={600}>{order.customer}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      №{order.id} - {new Date(order.date).toLocaleDateString('ru-RU')}
                    </Typography>
                  </Box>
                  <Box sx={{ textAlign: 'right' }}>
                    <Typography fontWeight={600}>{order.total}</Typography>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={statusTone(order.status)}
                      label={statusLabel(order.status)}
                      sx={{ mt: 0.5 }}
                    />
                  </Box>
                </Box>
              ))}
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600}>Склад</Typography>
            <Stack spacing={2} sx={{ mt: 2 }}>
              {inventoryData.map((item) => (
                <Box key={item.id}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" fontWeight={600}>{item.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {item.stock} {item.unit}
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={Math.min((item.stock / item.threshold) * 100, 100)}
                    color={item.stock < item.threshold * 0.35 ? 'warning' : 'primary'}
                    sx={{ height: 6, borderRadius: 999 }}
                  />
                </Box>
              ))}
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
