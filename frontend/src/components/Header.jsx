import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import { sectionLabel } from './navigationData.js';

export default function Header({
  user,
  activeSection,
  onLogout,
  onToggleSidebar,
  showMenuButton = false
}) {
  const activeSectionLabel = sectionLabel(user.role, activeSection);
  const pageTitle = activeSectionLabel || 'Рабочий кабинет';

  return (
    <AppBar position="sticky" color="inherit" elevation={0}>
      <Toolbar sx={{ minHeight: { xs: 56, sm: 64 }, px: { xs: 1.5, sm: 2.5 } }}>
        {showMenuButton && (
          <IconButton
            edge="start"
            color="inherit"
            onClick={onToggleSidebar}
            aria-label="Открыть меню"
            sx={{ mr: 1 }}
            size="small"
          >
            <Box component="span" aria-hidden sx={{ fontSize: 18, lineHeight: 1 }}>
              ☰
            </Box>
          </IconButton>
        )}

        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography
            variant="h6"
            component="h1"
            fontWeight={600}
            sx={{
              lineHeight: 1.2,
              letterSpacing: '-0.01em',
              fontSize: { xs: '1rem', sm: '1.1rem' }
            }}
          >
            {pageTitle}
          </Typography>
        </Box>

        <Button
          color="inherit"
          onClick={onLogout}
          aria-label="Выйти"
          sx={{
            color: 'text.secondary',
            minWidth: { xs: 'auto', sm: 88 },
            px: { xs: 1, sm: 1.5 },
            fontSize: '0.8125rem',
            '&:hover': {
              color: 'text.primary'
            }
          }}
          size="small"
        >
          <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
            Выйти
          </Box>
        </Button>
      </Toolbar>
    </AppBar>
  );
}
