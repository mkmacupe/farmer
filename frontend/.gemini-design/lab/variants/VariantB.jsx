import React from 'react';
import { Box, Grid, Paper, Typography, Stack, Divider, Chip, Button } from '@mui/material';
import { kpiData, ordersData, inventoryData } from '../data/fixtures';

const tasks = [
  { title: 'Подтвердить заявки', meta: '2 ждут проверки', tone: 'warning' },
  { title: 'Согласовать окна доставки', meta: '3 маршрута', tone: 'info' },
  { title: 'Пополнить остатки', meta: `${inventoryData.length} позиции`, tone: 'error' }
];

const kpiLabel = (title) => ({
  'Total Revenue': 'Выручка',
  'Active Orders': 'Активные заказы',
  'Pending Deliveries': 'Ожидают доставки',
  'Low Stock Items': 'Низкий остаток'
}[title] || title);

const statusLabel = (status) => {
  if (status === 'DELIVERED') return 'Доставлено';
  if (status === 'SHIPPED') return 'Отгружено';
  return 'В ожидании';
};

export default function VariantB() {
  return (
    <Box>
      <Paper variant="outlined" sx={{ p: 2, mb: 3, display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Стол решений</Typography>
          <Typography variant="body2" color="text.secondary">Быстрые согласования без лишних переключений.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="contained" size="small">Новая заявка</Button>
          <Button variant="outlined" size="small">Добавить позицию</Button>
        </Stack>
      </Paper>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Ключевые показатели</Typography>
            <Stack spacing={2}>
              {kpiData.map((kpi, index) => (
                <Box key={kpi.title}>
                  <Typography variant="caption" color="text.secondary">{kpiLabel(kpi.title)}</Typography>
                  <Typography variant="h6" fontWeight={600}>{kpi.value}</Typography>
                  <Typography variant="caption" color="text.secondary">{kpi.trend}</Typography>
                  {index < kpiData.length - 1 && <Divider sx={{ mt: 1.5 }} />}
                </Box>
              ))}
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Задачи на сегодня</Typography>
            <Stack spacing={1.5}>
              {tasks.map((task) => (
                <Box key={task.title} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography fontWeight={600}>{task.title}</Typography>
                    <Typography variant="caption" color="text.secondary">{task.meta}</Typography>
                  </Box>
                  <Chip size="small" label="Сегодня" color={task.tone} variant="outlined" />
                </Box>
              ))}
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={8}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1" fontWeight={600}>Заявки на проверку</Typography>
              <Button size="small">Все заявки</Button>
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Stack spacing={2}>
              {ordersData.map((order) => (
                <Box key={order.id} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography fontWeight={600}>{order.customer}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      №{order.id} - {new Date(order.date).toLocaleDateString('ru-RU')}
                    </Typography>
                  </Box>
                  <Box sx={{ textAlign: 'right' }}>
                    <Typography fontWeight={600}>{order.total}</Typography>
                    <Chip size="small" label={statusLabel(order.status)} variant="outlined" sx={{ mt: 0.5 }} />
                  </Box>
                </Box>
              ))}
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
