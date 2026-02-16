import React from 'react';
import { Box, Grid, Paper, Typography, Stack, Chip, Divider } from '@mui/material';
import { kpiData, ordersData } from '../data/fixtures';

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

export default function VariantE() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Пульс клиентов</Typography>
          <Typography variant="body2" color="text.secondary">Четкие сигналы по ключевым аккаунтам.</Typography>
        </Box>
        <Typography variant="caption" color="text.secondary">Обновлено только что</Typography>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {kpiData.map((kpi) => (
          <Grid item xs={6} md={3} key={kpi.title}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">{kpiLabel(kpi.title)}</Typography>
              <Typography variant="h6" fontWeight={700} sx={{ mt: 0.5 }}>{kpi.value}</Typography>
              <Typography variant="caption" color="text.secondary">{kpi.trend}</Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Ключевые клиенты</Typography>
        <Stack spacing={1.5} divider={<Divider flexItem />}>
          {ordersData.map((order) => (
            <Box key={order.id} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box>
                <Typography fontWeight={600}>{order.customer}</Typography>
                <Typography variant="caption" color="text.secondary">Заказ №{order.id} - {new Date(order.date).toLocaleDateString('ru-RU')}</Typography>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography fontWeight={600}>{order.total}</Typography>
                <Chip size="small" label={statusLabel(order.status)} color={statusTone(order.status)} variant="outlined" sx={{ mt: 0.5 }} />
              </Box>
            </Box>
          ))}
        </Stack>
      </Paper>
    </Box>
  );
}
