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

export default function App() {
  const [auth, setAuth] = useState(loadAuth());
  const [activeSection, setActiveSection] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const isMobile = useMediaQuery(themeMinimal.breakpoints.down('md'));

  useEffect(() => {
    if (auth) {
      saveAuth(auth);
      if (!activeSection) {
        if (auth.role === 'DIRECTOR') setActiveSection('director-profile');
        else if (auth.role === 'MANAGER') setActiveSection('manager-dashboard');
        else if (auth.role === 'LOGISTICIAN') setActiveSection('logistic-orders');
        else if (auth.role === 'DRIVER') setActiveSection('driver-orders');
      }
    }
  }, [auth, activeSection]);

  useEffect(() => {
    if (!isMobile) {
      setMobileNavOpen(false);
    }
  }, [isMobile]);

  const handleNavigate = (section) => {
    setActiveSection(section);
    if (isMobile) {
      setMobileNavOpen(false);
    }
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
      if (newAuth.role === 'DIRECTOR') setActiveSection('director-profile');
      else if (newAuth.role === 'MANAGER') setActiveSection('manager-dashboard');
      else if (newAuth.role === 'LOGISTICIAN') setActiveSection('logistic-orders');
      else if (newAuth.role === 'DRIVER') setActiveSection('driver-orders');
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
    if (auth.role === 'DIRECTOR') {
      return <DirectorView token={auth.token} activeSection={activeSection} />;
    }
    if (auth.role === 'MANAGER') {
      return <ManagerView token={auth.token} activeSection={activeSection} />;
    }
    if (auth.role === 'LOGISTICIAN') {
      return <LogisticianView token={auth.token} activeSection={activeSection} />;
    }
    if (auth.role === 'DRIVER') {
      return <DriverView token={auth.token} activeSection={activeSection} />;
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

  const useBottomNav = isMobile && (auth.role === 'DIRECTOR' || auth.role === 'DRIVER');

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
        {!useBottomNav && (
          <Sidebar
            user={auth}
            activeSection={activeSection}
            onNavigate={handleNavigate}
            isMobile={isMobile}
            mobileOpen={mobileNavOpen}
            onClose={() => setMobileNavOpen(false)}
          />
        )}
        <Box component="main" sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: '100vh' }}>
          <Header
            user={auth}
            activeSection={activeSection}
            onLogout={handleLogout}
            onToggleSidebar={() => setMobileNavOpen(true)}
            showMenuButton={isMobile && !useBottomNav}
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
