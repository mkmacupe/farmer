import React from 'react';
import { Box, Grid, Paper, Typography, Table, TableBody, TableCell, TableHead, TableRow, LinearProgress, Chip } from '@mui/material';
import { ordersData, inventoryData, kpiData } from '../data/fixtures';

export default function VariantC() {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Плотный реестр</Typography>
        <Typography variant="caption" color="text.secondary">Компактный вид для опытных пользователей</Typography>
      </Box>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        {kpiData.map((kpi) => (
          <Grid item xs={6} md={3} key={kpi.title}>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary">{({
                'Total Revenue': 'Выручка',
                'Active Orders': 'Активные заказы',
                'Pending Deliveries': 'Ожидают доставки',
                'Low Stock Items': 'Низкий остаток'
              }[kpi.title] || kpi.title)}</Typography>
              <Typography variant="h6" fontWeight={700} sx={{ mt: 0.5 }}>{kpi.value}</Typography>
              <Typography variant="caption" color="text.secondary">{kpi.trend}</Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={8}>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>Заказ</TableCell>
                  <TableCell sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>Клиент</TableCell>
                  <TableCell sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>Дата</TableCell>
                  <TableCell sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>Статус</TableCell>
                  <TableCell align="right" sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>Сумма</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {ordersData.map((row) => (
                  <TableRow key={row.id} hover>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>№{row.id}</TableCell>
                    <TableCell sx={{ fontSize: '0.85rem' }}>{row.customer}</TableCell>
                    <TableCell sx={{ fontSize: '0.85rem' }}>{new Date(row.date).toLocaleDateString('ru-RU')}</TableCell>
                    <TableCell sx={{ fontSize: '0.85rem' }}>
                      <Chip size="small" label={({
                        DELIVERED: 'Доставлено',
                        SHIPPED: 'Отгружено',
                        PENDING: 'В ожидании'
                      }[row.status] || row.status)} variant="outlined" />
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, fontSize: '0.85rem' }}>{row.total}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 2 }}>Остатки на складе</Typography>
            {inventoryData.map((item) => (
              <Box key={item.id} sx={{ mb: 2 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="body2" fontWeight={600}>{item.name}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {item.stock}/{item.threshold} {item.unit}
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
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
