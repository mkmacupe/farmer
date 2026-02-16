import React from 'react';
import { Box, Paper, Grid, Typography, Card, CardActionArea, Button, Avatar, Stack } from '@mui/material';
import { kpiData, ordersData } from '../data/fixtures';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';

// Variant D: Задачи и Действия
export default function VariantD() {
  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" fontWeight={800} sx={{ color: 'primary.main', mb: 1 }}>Привет, Менеджер</Typography>
        <Typography color="text.secondary">Вот что происходит в хозяйстве сегодня.</Typography>
      </Box>

      {/* Action Stats */}
      <Grid container spacing={2} sx={{ mb: 4 }}>
        {kpiData.slice(0, 3).map((kpi, idx) => (
          <Grid item xs={12} md={4} key={idx}>
            <Card variant="elevation" elevation={0} sx={{ border: '1px solid #eee', transition: '0.3s', '&:hover': { borderColor: 'primary.main', transform: 'translateY(-2px)' } }}>
              <CardActionArea sx={{ p: 2 }}>
                <Typography variant="body2" color="text.secondary">{kpi.title}</Typography>
                <Typography variant="h3" fontWeight={700} sx={{ my: 1, fontSize: '2.5rem' }}>{kpi.value}</Typography>
                <Typography variant="caption" sx={{ bgcolor: '#e0f2f1', color: 'primary.dark', px: 1, py: 0.5, borderRadius: 1 }}>
                  {kpi.trend}
                </Typography>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" fontWeight={700} sx={{ mb: 2 }}>Приоритетные задачи</Typography>
      
      <Stack spacing={2}>
        <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2, borderLeft: '4px solid', borderColor: 'warning.main' }}>
           <Avatar sx={{ bgcolor: 'warning.light', color: 'warning.dark' }}><Inventory2OutlinedIcon /></Avatar>
           <Box sx={{ flex: 1 }}>
             <Typography fontWeight={600}>Пополнить: Картофель (Гала)</Typography>
             <Typography variant="body2" color="text.secondary">Запас на уровне 15% (Критично)</Typography>
           </Box>
           <Button variant="contained" color="warning">Заказать</Button>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
           <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main' }}><LocalShippingOutlinedIcon /></Avatar>
           <Box sx={{ flex: 1 }}>
             <Typography fontWeight={600}>Одобрить 3 новых заказа</Typography>
             <Typography variant="body2" color="text.secondary">Общая сумма: 4,500 BYN</Typography>
           </Box>
           <Button variant="outlined" endIcon={<ArrowForwardIcon />}>Проверить</Button>
        </Paper>
      </Stack>
    </Box>
  );
}
