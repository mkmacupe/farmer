import React from 'react';
import { Box, Paper, Grid, Typography, List, ListItem, ListItemText, Divider, Avatar, Button, IconButton } from '@mui/material';
import { kpiData, ordersData } from '../data/fixtures';
import DashboardIcon from '@mui/icons-material/Dashboard';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import PeopleIcon from '@mui/icons-material/People';
import SettingsIcon from '@mui/icons-material/Settings';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';

// Variant F: Professional SaaS (Dark Sidebar)
export default function VariantF() {
  return (
    <Box sx={{ display: 'flex', height: '600px', borderRadius: 2, overflow: 'hidden', border: '1px solid #ddd', bgcolor: 'background.paper' }}>
      {/* Dark Sidebar */}
      <Box sx={{ width: 240, bgcolor: 'primary.dark', color: 'white', display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ p: 3 }}>
          <Typography variant="h6" fontWeight={700} sx={{ letterSpacing: 1 }}>АГРО<span style={{ color: '#81c784' }}>КОРП</span></Typography>
        </Box>
        <List sx={{ px: 1 }}>
          {[
            { icon: <DashboardIcon />, label: 'Обзор', active: true },
            { icon: <ShoppingCartIcon />, label: 'Заказы' },
            { icon: <PeopleIcon />, label: 'Клиенты' },
            { icon: <SettingsIcon />, label: 'Настройки' },
          ].map((item, index) => (
            <ListItem 
              key={index} 
              button 
              sx={{ 
                borderRadius: 1, 
                mb: 0.5, 
                bgcolor: item.active ? 'rgba(255,255,255,0.1)' : 'transparent',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.05)' }
              }}
            >
              <Box sx={{ mr: 2, display: 'flex', color: item.active ? 'white' : 'rgba(255,255,255,0.7)' }}>{item.icon}</Box>
              <ListItemText 
                primary={item.label} 
                primaryTypographyProps={{ 
                  fontSize: '0.9rem', 
                  fontWeight: 500,
                  color: item.active ? 'white' : 'rgba(255,255,255,0.7)' 
                }} 
              />
            </ListItem>
          ))}
        </List>
        <Box sx={{ mt: 'auto', p: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, p: 1, bgcolor: 'rgba(0,0,0,0.2)', borderRadius: 2 }}>
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: '0.8rem' }}>ИИ</Avatar>
            <Box>
              <Typography variant="caption" sx={{ display: 'block', fontWeight: 600 }}>Иван Иванов</Typography>
              <Typography variant="caption" sx={{ display: 'block', opacity: 0.7 }}>Директор</Typography>
            </Box>
          </Box>
        </Box>
      </Box>

      {/* Main Content */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', bgcolor: '#f4f6f8' }}>
        {/* Header */}
        <Box sx={{ p: 2, bgcolor: 'white', borderBottom: '1px solid #eee', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6" fontWeight={700}>Дашборд</Typography>
          <IconButton><NotificationsNoneIcon /></IconButton>
        </Box>

        {/* Scrollable Content */}
        <Box sx={{ p: 3, overflow: 'auto' }}>
          <Grid container spacing={3} sx={{ mb: 3 }}>
            {kpiData.map((kpi, idx) => (
              <Grid item xs={3} key={idx}>
                <Paper elevation={0} sx={{ p: 2, border: '1px solid #e0e0e0' }}>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>{kpi.title.toUpperCase()}</Typography>
                  <Typography variant="h5" fontWeight={700} sx={{ mt: 1, mb: 0.5 }}>{kpi.value}</Typography>
                  <Typography variant="caption" sx={{ color: 'success.main', fontWeight: 500 }}>{kpi.trend}</Typography>
                </Paper>
              </Grid>
            ))}
          </Grid>

          <Paper elevation={0} sx={{ border: '1px solid #e0e0e0' }}>
            <Box sx={{ p: 2, borderBottom: '1px solid #eee', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle1" fontWeight={700}>Последние заказы</Typography>
              <Button size="small" variant="outlined">Экспорт</Button>
            </Box>
            {ordersData.map((order, i) => (
              <Box key={order.id} sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: i !== ordersData.length - 1 ? '1px solid #f0f0f0' : 'none' }}>
                <Box>
                  <Typography variant="subtitle2" fontWeight={600}>{order.customer}</Typography>
                  <Typography variant="caption" color="text.secondary">Заказ #{order.id} • {order.date}</Typography>
                </Box>
                <Typography variant="body2" fontWeight={600}>{order.total}</Typography>
              </Box>
            ))}
          </Paper>
        </Box>
      </Box>
    </Box>
  );
}