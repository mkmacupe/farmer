import React from 'react';
import { Box, Paper, Grid, Typography, Divider, List, ListItem, ListItemText, Chip, Button } from '@mui/material';
import { kpiData, ordersData, inventoryData } from '../data/fixtures';

// Variant B: Разделенный макет
export default function VariantB() {
  return (
    <Box sx={{ height: '100%', display: 'flex', gap: 3 }}>
      {/* Main Content Area */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h5" fontWeight={700} color="primary.main">Обзор</Typography>
          <Typography variant="body2" color="text.secondary">{new Date().toLocaleDateString('ru-RU')}</Typography>
        </Box>

        {/* Horizontal Stat Strip */}
        <Paper variant="outlined" sx={{ p: 2, display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
          {kpiData.map((kpi, idx) => (
            <Box key={idx} sx={{ textAlign: 'center' }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 1 }}>
                {kpi.title}
              </Typography>
              <Typography variant="h5" fontWeight={600} sx={{ mt: 0.5 }}>{kpi.value}</Typography>
            </Box>
          ))}
        </Paper>

        <Paper variant="outlined" sx={{ flex: 1, p: 0, overflow: 'hidden' }}>
          <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
             <Typography variant="subtitle1" fontWeight={600}>Активные заказы</Typography>
          </Box>
          <List sx={{ p: 0 }}>
            {ordersData.map((order) => (
              <ListItem key={order.id} divider sx={{ px: 3, py: 2 }}>
                 <ListItemText 
                   primary={<Typography fontWeight={600}>{order.customer}</Typography>}
                   secondary={`#${order.id} • ${order.date}`}
                 />
                 <Box sx={{ textAlign: 'right' }}>
                   <Typography fontWeight={600}>{order.total}</Typography>
                   <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{order.status}</Typography>
                 </Box>
              </ListItem>
            ))}
          </List>
        </Paper>
      </Box>

      {/* Right Sidebar */}
      <Box sx={{ width: 320 }}>
        <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
          <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>Быстрые действия</Typography>
          <Button variant="contained" fullWidth sx={{ mb: 2 }}>Новый заказ</Button>
          <Button variant="outlined" fullWidth sx={{ mb: 4 }}>Добавить товар</Button>

          <Divider sx={{ mb: 2 }} />
          
          <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 2, color: 'error.main' }}>Мало на складе</Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {inventoryData.map((item) => (
              <Box key={item.id} sx={{ bgcolor: '#FFF4F4', p: 1.5, borderRadius: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" fontWeight={600}>{item.name}</Typography>
                  <Typography variant="caption" fontWeight={700} color="error.main">{item.stock} ост.</Typography>
                </Box>
                <Button size="small" sx={{ mt: 1, p: 0, minWidth: 'auto' }}>Заказать</Button>
              </Box>
            ))}
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}
