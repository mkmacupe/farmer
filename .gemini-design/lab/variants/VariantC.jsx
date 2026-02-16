import React from 'react';
import { Box, Paper, Grid, Typography, Table, TableBody, TableCell, TableHead, TableRow, LinearProgress, IconButton } from '@mui/material';
import { kpiData, ordersData, inventoryData } from '../data/fixtures';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';

// Variant C: Высокая плотность (Compact)
export default function VariantC() {
  return (
    <Box>
      {/* Dense KPI Grid */}
      <Grid container spacing={1} sx={{ mb: 2 }}>
        {kpiData.map((kpi, idx) => (
          <Grid item xs={6} md={3} key={idx}>
            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                {kpi.title.toUpperCase()}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', mt: 1 }}>
                <Typography variant="h6" fontWeight={700} sx={{ lineHeight: 1 }}>{kpi.value}</Typography>
                <Typography variant="caption" sx={{ fontSize: '0.7rem', color: kpi.status === 'warning' ? 'warning.main' : 'success.main' }}>
                  {kpi.trend}
                </Typography>
              </Box>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Box sx={{ px: 2, py: 1, bgcolor: '#f9f9f9', borderBottom: '1px solid #eee' }}>
               <Typography variant="subtitle2" fontWeight={700}>Лента заказов</Typography>
            </Box>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ '& th': { fontWeight: 600, fontSize: '0.75rem', color: 'text.secondary' } }}>
                  <TableCell>ID</TableCell>
                  <TableCell>Клиент</TableCell>
                  <TableCell>Дата</TableCell>
                  <TableCell>Статус</TableCell>
                  <TableCell align="right">Сумма</TableCell>
                  <TableCell padding="none"></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {ordersData.map((row) => (
                  <TableRow key={row.id} hover sx={{ '& td': { fontSize: '0.85rem', py: 0.75 } }}>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{row.id}</TableCell>
                    <TableCell>{row.customer}</TableCell>
                    <TableCell>{row.date}</TableCell>
                    <TableCell>
                      <Box sx={{ 
                        display: 'inline-block', 
                        width: 8, 
                        height: 8, 
                        borderRadius: '50%', 
                        bgcolor: row.status === 'DELIVERED' ? 'success.main' : 'warning.main',
                        mr: 1
                      }} />
                      {row.status}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>{row.total}</TableCell>
                    <TableCell padding="none">
                      <IconButton size="small"><KeyboardArrowRightIcon fontSize="small" /></IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Paper variant="outlined" sx={{ p: 0 }}>
             <Box sx={{ px: 2, py: 1, bgcolor: '#f9f9f9', borderBottom: '1px solid #eee' }}>
               <Typography variant="subtitle2" fontWeight={700}>Уровень запасов</Typography>
            </Box>
            {inventoryData.map((item) => (
              <Box key={item.id} sx={{ p: 1.5, borderBottom: '1px solid #f0f0f0' }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="body2" fontWeight={600} sx={{ fontSize: '0.85rem' }}>{item.name}</Typography>
                  <Typography variant="caption">{item.stock} / {item.threshold} {item.unit}</Typography>
                </Box>
                <LinearProgress 
                  variant="determinate" 
                  value={(item.stock / item.threshold) * 100} 
                  color={item.stock < 50 ? "error" : "primary"}
                  sx={{ height: 6, borderRadius: 3 }}
                />
              </Box>
            ))}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
