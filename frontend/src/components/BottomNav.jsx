import { memo } from 'react';
import Box from '@mui/material/Box';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Paper from '@mui/material/Paper';
import { NAV_ITEMS, itemIcon } from './navigation.jsx';

export default memo(function BottomNav({ role, activeSection, onNavigate }) {
  const items = NAV_ITEMS[role] || [];
  if (!items.length) {
    return null;
  }
  const compactMode = items.length > 4;

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
        paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        px: 0.5
      }}
    >
      <Box sx={{ overflowX: 'auto', scrollbarWidth: 'none', '&::-webkit-scrollbar': { display: 'none' } }}>
        <BottomNavigation
          showLabels
          value={activeSection}
          aria-label="Мобильная навигация"
          onChange={(event, value) => onNavigate(value)}
          sx={{
            height: compactMode ? 68 : 64,
            minWidth: '100%',
            width: 'max-content',
            bgcolor: 'transparent'
          }}
        >
          {items.map((item) => (
            <BottomNavigationAction
              key={item.id}
              label={item.label}
              value={item.id}
              icon={itemIcon(item.id)}
              aria-label={item.label}
              sx={{
                minWidth: compactMode ? 76 : 80,
                px: compactMode ? 0.75 : 1.25,
                '&.Mui-selected': {
                  fontWeight: 600
                }
              }}
            />
          ))}
        </BottomNavigation>
      </Box>
    </Paper>
  );
});
