import React from 'react';
import { Box, Grid, Card, CardContent, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, Button } from '@mui/material';
import { kpiData, ordersData, inventoryData } from '../data/fixtures';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';

// Variant A: Фокус на иерархии
export default function VariantA() {
  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 4, fontWeight: 700 }}>Дашборд</Typography>
      
      {/* KPIs */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {kpiData.map((kpi, idx) => (
          <Grid item xs={12} sm={6} md={3} key={idx}>
            <Card variant="outlined" sx={{ height: '100%' }}>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  {kpi.title}
                </Typography>
                <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                  {kpi.value}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  {kpi.status === 'up' && <ArrowUpwardIcon fontSize="small" color="success" />}
                  <Typography variant="caption" color={kpi.status === 'warning' ? 'warning.main' : 'text.secondary'}>
                    {kpi.trend}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        {/* Recent Orders */}
        <Grid item xs={12} md={8}>
          <Card variant="outlined">
            <CardContent sx={{ pb: 0 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="h6" fontWeight={600}>Последние заказы</Typography>
                <Button size="small">Показать все</Button>
              </Box>
            </CardContent>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>№ Заказа</TableCell>
                    <TableCell>Клиент</TableCell>
                    <TableCell>Статус</TableCell>
                    <TableCell align="right">Сумма</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {ordersData.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>#{row.id}</TableCell>
                      <TableCell>{row.customer}</TableCell>
                      <TableCell>
                        <Chip 
                          label={row.status} 
                          size="small" 
                          color={row.status === 'DELIVERED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'default'} 
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 500 }}>{row.total}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </Grid>

        {/* Inventory Alerts */}
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>Складские оповещения</Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {inventoryData.map((item) => (
                  <Box key={item.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', p: 1.5, border: '1px solid #eee', borderRadius: 1 }}>
                    <Box>
                      <Typography variant="subtitle2" fontWeight={600}>{item.name}</Typography>
                      <Typography variant="caption" color="error.main">
                        Осталось {item.stock} {item.unit}
                      </Typography>
                    </Box>
                    <Button size="small" variant="contained" color="primary">Пополнить</Button>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
