import {
  Suspense,
  lazy,
  useCallback,
  useEffect,
  useState,
} from "react";
import { LOGIN_LOADING_MESSAGE, login, primeBackendWarmup } from "./api.js";
import { clearAuth, loadAuth, saveAuth } from "./authStorage.js";
import LoginForm from "./components/LoginForm.jsx";

const AuthenticatedApp = lazy(() => import("./AuthenticatedApp.jsx"));

const DEFAULT_SECTION_BY_ROLE = {
  DIRECTOR: "director-profile",
  MANAGER: "manager-dashboard",
  LOGISTICIAN: "logistic-orders",
  DRIVER: "driver-orders",
};

function defaultSectionForRole(role) {
  return DEFAULT_SECTION_BY_ROLE[role] || "";
}

export default function App() {
  const [auth, setAuth] = useState(() => loadAuth());
  const [activeSection, setActiveSection] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (auth) {
      saveAuth(auth);
    }
  }, [auth]);

  useEffect(() => {
    if (!auth) {
      void primeBackendWarmup();
    }
  }, [auth]);

  useEffect(() => {
    if (auth && !activeSection) {
      setActiveSection(defaultSectionForRole(auth.role));
    }
  }, [auth, activeSection]);

  const applyAuthResponse = useCallback((response) => {
    const newAuth = {
      token: response.token,
      username: response.username,
      fullName: response.fullName,
      role: response.role,
    };
    setAuth(newAuth);
    setActiveSection(defaultSectionForRole(newAuth.role));
  }, []);

  const handleNavigate = useCallback((section) => {
    setActiveSection(section);
  }, []);

  const handleLogin = useCallback(async (username, password) => {
    setLoading(true);
    setError("");
    try {
      const response = await login(username, password);
      applyAuthResponse(response);
    } catch (err) {
      setError(err.message || "Не удалось войти");
    } finally {
      setLoading(false);
    }
  }, [applyAuthResponse]);

  const handleLogout = useCallback(() => {
    setAuth(null);
    setActiveSection("");
    clearAuth();
  }, []);

  if (!auth) {
    return (
      <LoginForm
        onLogin={handleLogin}
        loading={loading}
        loadingMessage={LOGIN_LOADING_MESSAGE}
        error={error}
      />
    );
  }

  return (
    <Suspense
      fallback={
        <div
          className="loading-indicator"
          role="status"
          aria-live="polite"
          aria-atomic="true"
        >
          Загружаем рабочее пространство...
        </div>
      }
    >
      <AuthenticatedApp
        auth={auth}
        activeSection={activeSection}
        onNavigate={handleNavigate}
        onLogout={handleLogout}
      />
    </Suspense>
  );
}
