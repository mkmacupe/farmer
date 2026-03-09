import {
  Suspense,
  lazy,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import { demoLogin, LOGIN_LOADING_MESSAGE, login, primeBackendWarmup } from "./api.js";
import { clearAuth, loadAuth, saveAuth } from "./authStorage.js";
import LoginForm from "./components/LoginForm.jsx";

const AuthenticatedApp = lazy(() => import("./AuthenticatedApp.jsx"));

const DEFAULT_SECTION_BY_ROLE = {
  DIRECTOR: "director-profile",
  MANAGER: "manager-dashboard",
  LOGISTICIAN: "logistic-orders",
  DRIVER: "driver-orders",
};

const ROLE_VIEW_PRELOADERS = {
  DIRECTOR: () => import("./views/DirectorView.jsx"),
  MANAGER: () => import("./views/ManagerView.jsx"),
  LOGISTICIAN: () => import("./views/LogisticianView.jsx"),
  DRIVER: () => import("./views/DriverView.jsx"),
};

function defaultSectionForRole(role) {
  return DEFAULT_SECTION_BY_ROLE[role] || "";
}

function isUnauthorizedError(error) {
  const message = String(error?.message || "").toLowerCase();
  return (
    message.includes("401")
    || message.includes("неверный логин")
    || message.includes("unauthorized")
    || message.includes("invalid credentials")
  );
}

function resolveQuickLoginPasswords(fallbackPassword) {
  const normalizedFallbackPassword = String(fallbackPassword ?? "").trim();
  if (!normalizedFallbackPassword) {
    return [];
  }

  const candidates = [normalizedFallbackPassword];
  const separatorIndex = normalizedFallbackPassword.lastIndexOf(":");
  if (separatorIndex > 0) {
    const legacyPassword = normalizedFallbackPassword.slice(0, separatorIndex).trim();
    if (legacyPassword && !candidates.includes(legacyPassword)) {
      candidates.push(legacyPassword);
    }
  }

  return candidates;
}

function resolveQuickLoginUsernames(primaryUsername, legacyUsername) {
  const candidates = [];
  const normalizedPrimaryUsername = String(primaryUsername ?? "").trim();
  const normalizedLegacyUsername = String(legacyUsername ?? "").trim();

  if (normalizedPrimaryUsername) {
    candidates.push(normalizedPrimaryUsername);
  }
  if (normalizedLegacyUsername && !candidates.includes(normalizedLegacyUsername)) {
    candidates.push(normalizedLegacyUsername);
  }

  return candidates;
}

function normalizeQuickLoginSpec(loginSpecOrUsername, fallbackPassword) {
  if (loginSpecOrUsername && typeof loginSpecOrUsername === "object") {
    return {
      usernameCandidates: resolveQuickLoginUsernames(
        loginSpecOrUsername.username,
        loginSpecOrUsername.legacyUsername,
      ),
      passwordCandidates: [
        ...new Set(
          [
            String(loginSpecOrUsername.password ?? "").trim(),
            String(loginSpecOrUsername.legacyPassword ?? "").trim(),
            ...resolveQuickLoginPasswords(fallbackPassword),
          ].filter(Boolean),
        ),
      ],
    };
  }

  return {
    usernameCandidates: resolveQuickLoginUsernames(loginSpecOrUsername, ""),
    passwordCandidates: resolveQuickLoginPasswords(fallbackPassword),
  };
}

export default function App() {
  const initialAuth = useMemo(() => loadAuth(), []);
  const [auth, setAuth] = useState(initialAuth);
  const [activeSection, setActiveSection] = useState(() =>
    defaultSectionForRole(initialAuth?.role),
  );
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

  const applyAuthResponse = useCallback(async (response) => {
    const appShellPromise = import("./AuthenticatedApp.jsx");
    ROLE_VIEW_PRELOADERS[response.role]?.().catch(() => null);
    await appShellPromise;
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
      await applyAuthResponse(response);
    } catch (err) {
      setError(err.message || "Не удалось войти");
    } finally {
      setLoading(false);
    }
  }, [applyAuthResponse]);

  const handleQuickLogin = useCallback(async (loginSpecOrUsername, fallbackPassword) => {
    setLoading(true);
    setError("");
    try {
      const { usernameCandidates, passwordCandidates } = normalizeQuickLoginSpec(
        loginSpecOrUsername,
        fallbackPassword,
      );
      let demoEndpointUnavailable = false;

      if (passwordCandidates.length > 0) {
        for (const usernameCandidate of usernameCandidates) {
          try {
            const response = await demoLogin(usernameCandidate, passwordCandidates[0]);
            await applyAuthResponse(response);
            return;
          } catch (error) {
            const message = String(error?.message || "").toLowerCase();
            if (message.includes("демо-вход отключ")) {
              throw error;
            }
            if (
              message.includes("404")
              || message.includes("not found")
              || message.includes("method not allowed")
            ) {
              demoEndpointUnavailable = true;
              break;
            }
            // Continue with the next username candidate or regular login fallback.
          }
        }
      }
      let lastError = null;

      for (const usernameCandidate of usernameCandidates) {
        for (let index = 0; index < passwordCandidates.length; index += 1) {
          try {
            const response = await login(usernameCandidate, passwordCandidates[index]);
            await applyAuthResponse(response);
            return;
          } catch (error) {
            lastError = error;
            if (!isUnauthorizedError(error)) {
              break;
            }
          }
        }
        if (lastError && !isUnauthorizedError(lastError)) {
          break;
        }
      }

      if (lastError) {
        throw lastError;
      }
      if (demoEndpointUnavailable) {
        throw new Error("Не удалось войти");
      }
      throw new Error("Не удалось войти");
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
        onQuickLogin={handleQuickLogin}
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
