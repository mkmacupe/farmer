import React from 'react';
import { Box, Grid, Typography, Card, CardContent, Table, TableBody, TableCell, TableHead, TableRow, Chip, Container } from '@mui/material';
import { kpiData, ordersData } from '../data/fixtures';
import SpaIcon from '@mui/icons-material/Spa';

// Variant E: Выразительный Бренд
export default function VariantE() {
  return (
    <Box sx={{ bgcolor: '#f0f7f4', minHeight: '100%', p: 3, borderRadius: 4 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 4 }}>
        <SpaIcon sx={{ color: 'primary.main', mr: 2, fontSize: 40 }} />
        <Typography variant="h4" fontWeight={800} color="primary.dark">ФермаСтат</Typography>
      </Box>

      {/* Hero Stats */}
      <Grid container spacing={2} sx={{ mb: 4 }}>
        {kpiData.map((kpi, idx) => (
          <Grid item xs={6} md={3} key={idx}>
            <Card sx={{ bgcolor: idx === 0 ? 'primary.main' : 'white', color: idx === 0 ? 'white' : 'text.primary', borderRadius: 4, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
              <CardContent>
                <Typography variant="subtitle2" sx={{ opacity: 0.8 }}>{kpi.title}</Typography>
                <Typography variant="h5" fontWeight={700} sx={{ my: 1 }}>{kpi.value}</Typography>
                <Chip 
                  label={kpi.trend} 
                  size="small" 
                  sx={{ 
                    bgcolor: idx === 0 ? 'rgba(255,255,255,0.2)' : '#f5f5f5', 
                    color: idx === 0 ? 'white' : 'text.secondary',
                    fontWeight: 600
                  }} 
                />
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card sx={{ borderRadius: 4, boxShadow: 'none', border: '1px solid rgba(46, 91, 78, 0.1)' }}>
        <Box sx={{ p: 3, pb: 1 }}>
          <Typography variant="h6" fontWeight={700} color="primary.dark">Недавняя активность</Typography>
        </Box>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ color: 'primary.main', fontWeight: 600 }}>Заказ</TableCell>
              <TableCell sx={{ color: 'primary.main', fontWeight: 600 }}>Клиент</TableCell>
              <TableCell sx={{ color: 'primary.main', fontWeight: 600 }}>Статус</TableCell>
              <TableCell sx={{ color: 'primary.main', fontWeight: 600 }} align="right">Сумма</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {ordersData.map((row, idx) => (
              <TableRow key={row.id} sx={{ bgcolor: idx % 2 === 0 ? '#fafdfc' : 'white' }}>
                <TableCell sx={{ fontWeight: 600 }}>#{row.id}</TableCell>
                <TableCell>{row.customer}</TableCell>
                <TableCell>
                  <Chip 
                    label={row.status} 
                    size="small"
                    sx={{ 
                      bgcolor: row.status === 'DELIVERED' ? '#e8f5e9' : '#fff3e0',
                      color: row.status === 'DELIVERED' ? '#2e7d32' : '#e65100',
                      fontWeight: 700,
                      borderRadius: 2
                    }}
                  />
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600 }}>{row.total}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </Box>
  );
}
