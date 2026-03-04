import { memo } from 'react';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import { NAV_ITEMS, itemIcon, roleLabel } from './navigation.jsx';

const DRAWER_WIDTH = 248;

export default memo(function Sidebar({
  user,
  activeSection,
  onNavigate,
  isMobile = false,
  mobileOpen = false,
  onClose
}) {
  const items = NAV_ITEMS[user.role] || [];
  const open = isMobile ? mobileOpen : true;
  const variant = isMobile ? 'temporary' : 'permanent';

  const handleNavigate = (id) => {
    onNavigate(id);
    if (isMobile && typeof onClose === 'function') {
      onClose();
    }
  };

  const drawerContent = (
    <Box
      component="aside"
      aria-label="Боковая панель"
      sx={{ height: '100%', display: 'flex', flexDirection: 'column', px: 1.5, py: 2.5 }}
    >
      {/* User identity */}
      <Box sx={{ px: 1.5, mb: 3.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Avatar
          sx={{
            width: 36,
            height: 36,
            bgcolor: 'primary.main',
            color: 'white',
            fontSize: '0.875rem',
            fontWeight: 600
          }}
        >
          {(user.fullName || user.username || 'U').charAt(0).toUpperCase()}
        </Avatar>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle2" fontWeight={600} noWrap sx={{ fontSize: '0.875rem' }}>
            {user.fullName || user.username}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              display: 'block',
              color: 'text.secondary',
              fontSize: '0.7rem',
              lineHeight: 1.3
            }}
          >
            {roleLabel(user.role)}
          </Typography>
        </Box>
      </Box>

      {/* Navigation label */}
      <Typography
        variant="overline"
        sx={{
          px: 1.5,
          mb: 1,
          display: 'block',
          fontSize: '0.625rem',
          letterSpacing: '0.08em',
          color: 'text.secondary'
        }}
      >
        Навигация
      </Typography>

      {/* Navigation items */}
      <Box component="nav" aria-label="Основная навигация" sx={{ flex: 1 }}>
        <List disablePadding>
          {items.map((item) => {
            const isActive = activeSection === item.id;
            return (
              <ListItem key={item.id} disablePadding sx={{ mb: 0.25 }}>
                <ListItemButton
                  selected={isActive}
                  onClick={() => handleNavigate(item.id)}
                  aria-current={isActive ? 'page' : undefined}
                  sx={(theme) => ({
                    borderRadius: 2,
                    py: 1,
                    px: 1.5,
                    minHeight: 40,
                    bgcolor: isActive
                      ? 'rgba(46, 91, 78, 0.06)'
                      : 'transparent',
                    color: isActive
                      ? theme.palette.primary.main
                      : theme.palette.text.secondary,
                    '&:hover': {
                      bgcolor: isActive
                        ? 'rgba(46, 91, 78, 0.08)'
                        : 'rgba(0, 0, 0, 0.03)',
                      color: isActive
                        ? theme.palette.primary.main
                        : theme.palette.text.primary
                    },
                    transition: 'background-color 0.15s ease, color 0.15s ease',
                    '&.Mui-selected': {
                      bgcolor: 'rgba(46, 91, 78, 0.06)'
                    },
                    '&.Mui-selected:hover': {
                      bgcolor: 'rgba(46, 91, 78, 0.08)'
                    }
                  })}
                >
                  <ListItemIcon
                    sx={{
                      minWidth: 32,
                      color: 'inherit'
                    }}
                  >
                    {itemIcon(item.id, { fontSize: 'small' })}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{
                      fontWeight: isActive ? 600 : 400,
                      fontSize: '0.875rem'
                    }}
                  />
                </ListItemButton>
              </ListItem>
            );
          })}
        </List>
      </Box>

    </Box>
  );

  return (
    <Drawer
      variant={variant}
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
    sx={{
      width: DRAWER_WIDTH,
      flexShrink: 0,
      '& .MuiDrawer-paper': {
        width: DRAWER_WIDTH,
        boxSizing: 'border-box'
      }
    }}
  >
      {drawerContent}
    </Drawer>
  );
});
