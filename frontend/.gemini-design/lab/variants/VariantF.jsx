import React from 'react';
import { Box, Grid, Paper, Typography, Stack, Chip, LinearProgress } from '@mui/material';
import { inventoryData, ordersData } from '../data/fixtures';

const schedule = [
  { time: '08:30', title: 'Local Bistro', meta: 'Забор - №1004' },
  { time: '10:00', title: 'EcoMarket LLC', meta: 'Доставка - №1001' },
  { time: '12:30', title: 'Green Grocer', meta: 'Доставка - №1002' },
  { time: '15:00', title: 'Fresh Foods', meta: 'Доставка - №1003' },
  { time: '17:15', title: 'Market Chain A', meta: 'Доставка - №1005' }
];

export default function VariantF() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>График доставок</Typography>
          <Typography variant="body2" color="text.secondary">План без лишнего шума.</Typography>
        </Box>
        <Chip size="small" label={`${ordersData.length} активных заявок`} variant="outlined" />
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Сегодня</Typography>
            <Stack spacing={1.5}>
              {schedule.map((item) => (
                <Box key={item.time} sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
                  <Box sx={{ minWidth: 56 }}>
                    <Typography variant="caption" color="text.secondary">{item.time}</Typography>
                  </Box>
                  <Paper variant="outlined" sx={{ flex: 1, p: 1.5 }}>
                    <Typography fontWeight={600}>{item.title}</Typography>
                    <Typography variant="caption" color="text.secondary">{item.meta}</Typography>
                  </Paper>
                </Box>
              ))}
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Состояние маршрутов</Typography>
            <Stack spacing={1.5}>
              <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" fontWeight={600}>Своевременность</Typography>
                  <Typography variant="caption" color="text.secondary">96%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={96} sx={{ height: 6, borderRadius: 999 }} />
              </Box>
              <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" fontWeight={600}>Загрузка</Typography>
                  <Typography variant="caption" color="text.secondary">72%</Typography>
                </Box>
                <LinearProgress variant="determinate" value={72} color="info" sx={{ height: 6, borderRadius: 999 }} />
              </Box>
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Контроль остатков</Typography>
            <Stack spacing={1.5}>
              {inventoryData.map((item) => (
                <Box key={item.id}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" fontWeight={600}>{item.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{item.stock} {item.unit}</Typography>
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
