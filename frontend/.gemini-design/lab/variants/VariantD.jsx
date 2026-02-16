import React from 'react';
import { Box, Grid, Paper, Typography, Stack, Button, Divider, Chip } from '@mui/material';
import { ordersData, inventoryData } from '../data/fixtures';

const statusBuckets = [
  { label: 'Ожидают', status: 'PENDING', tone: 'warning' },
  { label: 'Отгружены', status: 'SHIPPED', tone: 'info' },
  { label: 'Доставлены', status: 'DELIVERED', tone: 'success' }
];

const tasks = [
  { title: 'Проверить заявки', meta: '2 заказа', tone: 'warning' },
  { title: 'Подтвердить окна доставки', meta: '3 маршрута', tone: 'info' },
  { title: 'Пополнить остатки', meta: `${inventoryData.length} позиции`, tone: 'error' }
];

export default function VariantD() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Поток задач</Typography>
          <Typography variant="body2" color="text.secondary">Двигаем заявки без лишних шагов.</Typography>
        </Box>
        <Button size="small" variant="outlined">Открыть очередь</Button>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {statusBuckets.map((bucket) => {
          const items = ordersData.filter((order) => order.status === bucket.status);
          return (
            <Grid item xs={12} md={4} key={bucket.status}>
              <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                  <Typography variant="subtitle1" fontWeight={600}>{bucket.label}</Typography>
                  <Chip size="small" label={items.length} color={bucket.tone} variant="outlined" />
                </Box>
                <Stack spacing={0.75}>
                  {items.slice(0, 3).map((order) => (
                    <Typography key={order.id} variant="body2">
                      {order.customer}
                      <Typography component="span" variant="caption" color="text.secondary"> - №{order.id}</Typography>
                    </Typography>
                  ))}
                  {items.length > 3 && (
                    <Typography variant="caption" color="text.secondary">+{items.length - 3} еще</Typography>
                  )}
                </Stack>
              </Paper>
            </Grid>
          );
        })}
      </Grid>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Следующие действия</Typography>
        <Stack spacing={1.5} divider={<Divider flexItem />}>
          {tasks.map((task) => (
            <Box key={task.title} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box>
                <Typography fontWeight={600}>{task.title}</Typography>
                <Typography variant="caption" color="text.secondary">{task.meta}</Typography>
              </Box>
              <Chip size="small" label="Сегодня" color={task.tone} variant="outlined" />
            </Box>
          ))}
        </Stack>
      </Paper>
    </Box>
  );
}
