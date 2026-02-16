import React, { Suspense, lazy, useEffect, useState } from 'react';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import useMediaQuery from '@mui/material/useMediaQuery';
import { login } from './api.js';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';
import Header from './components/Header.jsx';
import LoginForm from './components/LoginForm.jsx';
import Sidebar from './components/Sidebar.jsx';
import BottomNav from './components/BottomNav.jsx';
import themeMinimal from './themeMinimal.js';

const DirectorView = lazy(() => import('./views/DirectorView.jsx'));
const LogisticianView = lazy(() => import('./views/LogisticianView.jsx'));
const DriverView = lazy(() => import('./views/DriverView.jsx'));
const ManagerView = lazy(() => import('./views/ManagerView.jsx'));

const DEFAULT_SECTION_BY_ROLE = {
  DIRECTOR: 'director-profile',
  MANAGER: 'manager-dashboard',
  LOGISTICIAN: 'logistic-orders',
  DRIVER: 'driver-orders'
};

const VIEW_BY_ROLE = {
  DIRECTOR: DirectorView,
  MANAGER: ManagerView,
  LOGISTICIAN: LogisticianView,
  DRIVER: DriverView
};

function defaultSectionForRole(role) {
  return DEFAULT_SECTION_BY_ROLE[role] || '';
}

export default function App() {
  const [auth, setAuth] = useState(loadAuth());
  const [activeSection, setActiveSection] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const isMobile = useMediaQuery(themeMinimal.breakpoints.down('md'));

  useEffect(() => {
    if (auth) {
      saveAuth(auth);
      if (!activeSection) {
        setActiveSection(defaultSectionForRole(auth.role));
      }
    }
  }, [auth, activeSection]);

  const handleNavigate = (section) => {
    setActiveSection(section);
  };

  const handleLogin = async (username, password) => {
    setLoading(true);
    setError('');
    try {
      const response = await login(username, password);
      const newAuth = {
        token: response.token,
        username: response.username,
        fullName: response.fullName,
        role: response.role
      };
      setAuth(newAuth);
      setActiveSection(defaultSectionForRole(newAuth.role));
    } catch (err) {
      setError(err.message || 'Не удалось войти');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    setAuth(null);
    setActiveSection('');
    clearAuth();
  };

  const renderRoleView = () => {
    const RoleView = VIEW_BY_ROLE[auth.role];
    if (RoleView) {
      return <RoleView token={auth.token} activeSection={activeSection} />;
    }
    return null;
  };

  if (!auth) {
    return (
      <ThemeProvider theme={themeMinimal}>
        <CssBaseline />
        <LoginForm onLogin={handleLogin} loading={loading} error={error} />
      </ThemeProvider>
    );
  }

  // Keep one mobile navigation model for all roles.
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
          <Sidebar
            user={auth}
            activeSection={activeSection}
            onNavigate={handleNavigate}
          />
        )}
        <Box component="main" sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: '100vh' }}>
          <Header
            user={auth}
            activeSection={activeSection}
            onLogout={handleLogout}
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
              <Suspense fallback={<div className="loading-indicator">Загружаем рабочее пространство...</div>}>
                {renderRoleView()}
              </Suspense>
            </Box>
          </Box>
        </Box>
        {useBottomNav && (
          <BottomNav role={auth.role} activeSection={activeSection} onNavigate={handleNavigate} />
        )}
      </Box>
    </ThemeProvider>
  );
}
