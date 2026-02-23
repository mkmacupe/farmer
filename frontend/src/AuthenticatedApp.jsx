import { Component, Suspense, lazy } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import useMediaQuery from '@mui/material/useMediaQuery';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Header from './components/Header.jsx';
import themeMinimal from './themeMinimal.js';

const Sidebar = lazy(() => import('./components/Sidebar.jsx'));
const BottomNav = lazy(() => import('./components/BottomNav.jsx'));
const DirectorView = lazy(() => import('./views/DirectorView.jsx'));
const LogisticianView = lazy(() => import('./views/LogisticianView.jsx'));
const DriverView = lazy(() => import('./views/DriverView.jsx'));
const ManagerView = lazy(() => import('./views/ManagerView.jsx'));

const VIEW_BY_ROLE = {
  DIRECTOR: DirectorView,
  MANAGER: ManagerView,
  LOGISTICIAN: LogisticianView,
  DRIVER: DriverView
};

class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <Box sx={{ p: 4, textAlign: 'center', mt: 8 }}>
          <Typography variant="h5" gutterBottom>
            Что-то пошло не так
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Произошла непредвиденная ошибка. Попробуйте перезагрузить страницу.
          </Typography>
          <Button
            variant="contained"
            onClick={() => { this.setState({ hasError: false }); window.location.reload(); }}
          >
            Перезагрузить
          </Button>
        </Box>
      );
    }
    return this.props.children;
  }
}

export default function AuthenticatedApp({
  auth,
  activeSection,
  onNavigate,
  onLogout
}) {
  const isMobile = useMediaQuery(themeMinimal.breakpoints.down('md'));
  const RoleView = VIEW_BY_ROLE[auth.role];
  const useBottomNav = isMobile;

  return (
    <ThemeProvider theme={themeMinimal}>
      <CssBaseline />
      <Box
        sx={{
          display: 'flex',
          minHeight: '100vh',
          overflow: 'hidden',
          bgcolor: 'background.default'
        }}
      >
        {!isMobile && (
          <Suspense fallback={null}>
            <Sidebar
              user={auth}
              activeSection={activeSection}
              onNavigate={onNavigate}
            />
          </Suspense>
        )}
        <Box component="main" sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: '100vh' }}>
          <Header
            user={auth}
            activeSection={activeSection}
            onLogout={onLogout}
            onToggleSidebar={() => {}}
            showMenuButton={false}
          />
          <Box
            sx={{
              flexGrow: 1,
              overflow: 'auto',
              WebkitOverflowScrolling: 'touch',
              px: { xs: 1.5, sm: 2.5, lg: 3.5 },
              py: { xs: 2, sm: 3 },
              pb: useBottomNav ? { xs: 10 } : undefined,
              bgcolor: 'transparent'
            }}
          >
            <Box sx={{ maxWidth: 1520, mx: 'auto' }} className="page-enter">
              <ErrorBoundary>
                <Suspense fallback={<div className="loading-indicator">Загружаем рабочее пространство...</div>}>
                  {RoleView ? <RoleView token={auth.token} activeSection={activeSection} /> : null}
                </Suspense>
              </ErrorBoundary>
            </Box>
          </Box>
        </Box>
        {useBottomNav && (
          <Suspense fallback={null}>
            <BottomNav role={auth.role} activeSection={activeSection} onNavigate={onNavigate} />
          </Suspense>
        )}
      </Box>
    </ThemeProvider>
  );
}
