import {
  Suspense,
  lazy,
  startTransition,
  useCallback,
  useEffect,
  useState,
} from "react";
import { LOGIN_LOADING_MESSAGE, login, primeBackendWarmup } from "./api.js";
import { clearAuth, loadAuth, saveAuth } from "./authStorage.js";
import LoginForm from "./components/LoginForm.jsx";
import { NAV_ITEMS } from "./components/navigationData.js";

const AuthenticatedApp = lazy(() => import("./AuthenticatedApp.jsx"));

const DEFAULT_SECTION_BY_ROLE = {
  DIRECTOR: "director-profile",
  MANAGER: "manager-dashboard",
  LOGISTICIAN: "logistic-orders",
  DRIVER: "driver-orders",
};
const SECTION_HASH_PREFIX = "#section=";

function defaultSectionForRole(role) {
  return DEFAULT_SECTION_BY_ROLE[role] || "";
}

function parseSectionFromHash(hash) {
  if (!hash.startsWith(SECTION_HASH_PREFIX)) {
    return "";
  }

  const rawSection = hash.slice(SECTION_HASH_PREFIX.length).trim();
  if (!rawSection) {
    return "";
  }

  try {
    return decodeURIComponent(rawSection);
  } catch {
    return rawSection;
  }
}

function replaceLocationHash(nextHash) {
  if (typeof window === "undefined") {
    return;
  }

  const { pathname, search } = window.location;
  window.history.replaceState(window.history.state, "", `${pathname}${search}${nextHash}`);
}

function clearSectionHash() {
  replaceLocationHash("");
}

function syncSectionHash(section) {
  if (typeof window === "undefined" || !section) {
    return;
  }

  const nextHash = `${SECTION_HASH_PREFIX}${encodeURIComponent(section)}`;
  if (window.location.hash === nextHash) {
    return;
  }

  replaceLocationHash(nextHash);
}

function isKnownSectionForRole(role, section) {
  return Boolean(section) && (NAV_ITEMS[role] || []).some((item) => item.id === section);
}

function resolveSectionForRole(role, sectionFromHash) {
  if (isKnownSectionForRole(role, sectionFromHash)) {
    return sectionFromHash;
  }
  return defaultSectionForRole(role);
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
      clearSectionHash();
      void primeBackendWarmup();
    }
  }, [auth]);

  useEffect(() => {
    if (!auth || typeof window === "undefined") {
      return undefined;
    }

    const syncSectionFromLocation = () => {
      const nextSection = resolveSectionForRole(auth.role, parseSectionFromHash(window.location.hash));
      setActiveSection((currentSection) => (
        currentSection === nextSection ? currentSection : nextSection
      ));
    };

    syncSectionFromLocation();
    window.addEventListener("hashchange", syncSectionFromLocation);

    return () => {
      window.removeEventListener("hashchange", syncSectionFromLocation);
    };
  }, [auth]);

  useEffect(() => {
    if (!auth) {
      return;
    }

    if (activeSection) {
      syncSectionHash(activeSection);
      return;
    }

    clearSectionHash();
  }, [auth, activeSection]);

  const applyAuthResponse = useCallback((response) => {
    const newAuth = {
      token: response.token,
      username: response.username,
      fullName: response.fullName,
      role: response.role,
    };

    startTransition(() => {
      setAuth(newAuth);
      setActiveSection(resolveSectionForRole(newAuth.role, parseSectionFromHash(window.location.hash)));
    });
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
    clearSectionHash();
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
