import React from 'react';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Paper from '@mui/material/Paper';
import { NAV_ITEMS, itemIcon } from './navigation.jsx';

export default function BottomNav({ role, activeSection, onNavigate }) {
  const items = NAV_ITEMS[role] || [];
  if (!items.length) {
    return null;
  }

  return (
    <Paper
      elevation={0}
      sx={{
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: (theme) => theme.zIndex.appBar + 1,
        borderTop: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        paddingBottom: 'env(safe-area-inset-bottom, 0px)'
      }}
    >
      <BottomNavigation
        showLabels
        value={activeSection}
        onChange={(event, value) => onNavigate(value)}
        sx={{
          height: 64,
          bgcolor: 'transparent'
        }}
      >
        {items.map((item) => (
          <BottomNavigationAction
            key={item.id}
            label={item.label}
            value={item.id}
            icon={itemIcon(item.id)}
            sx={{
              minWidth: 64,
              '&.Mui-selected': {
                fontWeight: 600
              }
            }}
          />
        ))}
      </BottomNavigation>
    </Paper>
  );
}
