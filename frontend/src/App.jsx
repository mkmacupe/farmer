import { Suspense, lazy, useCallback, useEffect, useMemo, useState } from 'react';
import { login } from './api.js';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';
import LoginForm from './components/LoginForm.jsx';

const AuthenticatedApp = lazy(() => import('./AuthenticatedApp.jsx'));

const DEFAULT_SECTION_BY_ROLE = {
  DIRECTOR: 'director-profile',
  MANAGER: 'manager-dashboard',
  LOGISTICIAN: 'logistic-orders',
  DRIVER: 'driver-orders'
};

function defaultSectionForRole(role) {
  return DEFAULT_SECTION_BY_ROLE[role] || '';
}

export default function App() {
  const initialAuth = useMemo(() => loadAuth(), []);
  const [auth, setAuth] = useState(initialAuth);
  const [activeSection, setActiveSection] = useState(() => defaultSectionForRole(initialAuth?.role));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (auth) {
      saveAuth(auth);
    }
  }, [auth]);

  useEffect(() => {
    if (auth && !activeSection) {
      setActiveSection(defaultSectionForRole(auth.role));
    }
  }, [auth, activeSection]);

  const handleNavigate = useCallback((section) => {
    setActiveSection(section);
  }, []);

  const handleLogin = useCallback(async (username, password) => {
    setLoading(true);
    setError('');
    try {
      const appShellPromise = import('./AuthenticatedApp.jsx');
      const response = await login(username, password);
      await appShellPromise;
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
  }, []);

  const handleLogout = useCallback(() => {
    setAuth(null);
    setActiveSection('');
    clearAuth();
  }, []);

  if (!auth) {
    return <LoginForm onLogin={handleLogin} loading={loading} error={error} />;
  }

  return (
    <Suspense fallback={<div className="loading-indicator">Загружаем рабочее пространство...</div>}>
      <AuthenticatedApp
        auth={auth}
        activeSection={activeSection}
        onNavigate={handleNavigate}
        onLogout={handleLogout}
      />
    </Suspense>
  );
}
