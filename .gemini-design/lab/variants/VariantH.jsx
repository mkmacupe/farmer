import React from 'react';
import { Box, Typography, Divider, Grid } from '@mui/material';
import { kpiData, ordersData } from '../data/fixtures';

// Variant H: Ультра минимализм
export default function VariantH() {
  return (
    <Box sx={{ maxWidth: 900, mx: 'auto', p: 4, bgcolor: 'white', minHeight: '100vh' }}>
      <Box sx={{ mb: 6 }}>
        <Typography variant="overline" sx={{ letterSpacing: 2, color: 'text.secondary' }}>Ежедневный отчет</Typography>
        <Typography variant="h3" fontWeight={300} sx={{ mt: 1, fontFamily: 'serif' }}>
          Обзор <span style={{ fontStyle: 'italic' }}>Продаж</span>
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 2, maxWidth: 600 }}>
          Детальная разбивка выручки, логистики и состояния склада на 25 Октября 2023.
        </Typography>
      </Box>

      <Grid container spacing={6} sx={{ mb: 8 }}>
        {kpiData.map((kpi, idx) => (
          <Grid item xs={6} md={3} key={idx}>
            <Box sx={{ borderLeft: '1px solid #eee', pl: 2 }}>
              <Typography variant="body2" color="text.secondary" gutterBottom>{kpi.title}</Typography>
              <Typography variant="h5" fontWeight={500}>{kpi.value}</Typography>
            </Box>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" fontWeight={400} sx={{ mb: 3 }}>Последние транзакции</Typography>
      <Divider sx={{ mb: 2 }} />
      
      <Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 1fr', pb: 2, color: 'text.secondary', fontSize: '0.85rem' }}>
          <Box>ID</Box>
          <Box>Клиент</Box>
          <Box>Дата</Box>
          <Box sx={{ textAlign: 'right' }}>Итого</Box>
        </Box>
        {ordersData.map((order) => (
          <Box key={order.id} sx={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 1fr', py: 2, borderTop: '1px solid #f5f5f5' }}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>#{order.id}</Typography>
            <Typography variant="body2" fontWeight={500}>{order.customer}</Typography>
            <Typography variant="body2" color="text.secondary">{order.date}</Typography>
            <Typography variant="body2" fontWeight={500} sx={{ textAlign: 'right' }}>{order.total}</Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
}