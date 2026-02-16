import React from 'react';
import { Box, Paper, Typography, Chip, Stack, Card, CardContent } from '@mui/material';
import { ordersData } from '../data/fixtures';

// Variant G: Канбан доска
export default function VariantG() {
  const columns = [
    { id: 'PENDING', label: 'На рассмотрении', color: '#fff3e0', accent: '#e65100' },
    { id: 'SHIPPED', label: 'В пути', color: '#e3f2fd', accent: '#0277bd' },
    { id: 'DELIVERED', label: 'Выполнено', color: '#e8f5e9', accent: '#2e7d32' }
  ];

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h5" fontWeight={800} color="text.primary">Доска заказов</Typography>
        <Chip label="24 активных заказа" color="primary" size="small" />
      </Box>

      <Box sx={{ display: 'flex', gap: 3, overflowX: 'auto', pb: 2, flex: 1 }}>
        {columns.map(col => (
          <Paper 
            key={col.id} 
            elevation={0}
            sx={{ 
              flex: '0 0 300px', 
              bgcolor: '#f5f7f9', 
              display: 'flex', 
              flexDirection: 'column',
              border: '1px solid #e0e0e0'
            }}
          >
            {/* Column Header */}
            <Box sx={{ p: 2, borderTop: `4px solid ${col.accent}`, bgcolor: 'white', borderBottom: '1px solid #eee' }}>
              <Typography variant="subtitle2" fontWeight={700}>{col.label}</Typography>
            </Box>

            {/* Column Content */}
            <Stack spacing={2} sx={{ p: 2, overflowY: 'auto' }}>
              {ordersData
                .filter(o => o.status === col.id || (col.id === 'PENDING' && o.status === 'CREATED')) // Mock logic
                .concat(col.id === 'PENDING' ? [ordersData[0], ordersData[3]] : []) // Duplicates for demo
                .map((order, idx) => (
                <Card key={`${order.id}-${idx}`} sx={{ boxShadow: '0 2px 4px rgba(0,0,0,0.04)', border: '1px solid #eee' }}>
                  <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                    <Stack direction="row" justifyContent="space-between" mb={1}>
                      <Typography variant="caption" color="text.secondary">#{order.id}</Typography>
                      <Typography variant="caption" fontWeight={600}>{order.total}</Typography>
                    </Stack>
                    <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                      {order.customer}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {order.date}
                    </Typography>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          </Paper>
        ))}
      </Box>
    </Box>
  );
}