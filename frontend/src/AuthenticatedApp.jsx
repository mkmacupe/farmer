import { Component, Suspense, lazy } from "react";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import Box from "@mui/material/Box";
import useMediaQuery from "@mui/material/useMediaQuery";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
import Header from "./components/Header.jsx";
import Sidebar from "./components/Sidebar.jsx";
import BottomNav from "./components/BottomNav.jsx";
import themeMinimal from "./themeMinimal.js";
import { VIEW_LOADERS } from "./viewLoaders.js";

const DirectorView = lazy(VIEW_LOADERS.DIRECTOR);
const LogisticianView = lazy(VIEW_LOADERS.LOGISTICIAN);
const DriverView = lazy(VIEW_LOADERS.DRIVER);
const ManagerView = lazy(VIEW_LOADERS.MANAGER);

const VIEW_BY_ROLE = {
  DIRECTOR: DirectorView,
  MANAGER: ManagerView,
  LOGISTICIAN: LogisticianView,
  DRIVER: DriverView,
};

// Stable sx objects extracted to module scope to avoid re-creation on every render
const rootSx = {
  display: "flex",
  minHeight: "100vh",
  overflow: "hidden",
  bgcolor: "background.default",
};

const mainSx = {
  flexGrow: 1,
  display: "flex",
  flexDirection: "column",
  minWidth: 0,
  minHeight: "100vh",
};

const contentBaseSx = {
  flexGrow: 1,
  overflow: "auto",
  WebkitOverflowScrolling: "touch",
  px: { xs: 1.5, sm: 2.5, lg: 3.5 },
  py: { xs: 2, sm: 3 },
  bgcolor: "transparent",
};

const contentWithBottomNavSx = {
  ...contentBaseSx,
  pb: { xs: 10 },
};

const innerSx = { maxWidth: 1520, mx: "auto" };
const NOOP = () => {};

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
        <Box sx={{ p: 4, textAlign: "center", mt: 8 }}>
          <Typography variant="h5" gutterBottom>
            Что-то пошло не так
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Произошла непредвиденная ошибка. Попробуйте перезагрузить страницу.
          </Typography>
          <Button
            variant="contained"
            onClick={() => {
              this.setState({ hasError: false });
              window.location.reload();
            }}
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
  onLogout,
}) {
  const isMobile = useMediaQuery(themeMinimal.breakpoints.down("md"));
  const RoleView = VIEW_BY_ROLE[auth.role];
  const useBottomNav = isMobile;

  return (
    <ThemeProvider theme={themeMinimal}>
      <CssBaseline />
      <a href="#main-content" className="skip-link">
        Перейти к основному содержимому
      </a>
      <Box sx={rootSx}>
        {!isMobile && (
          <Sidebar
            user={auth}
            activeSection={activeSection}
            onNavigate={onNavigate}
          />
        )}
        <Box component="main" id="main-content" tabIndex={-1} sx={mainSx}>
          <Header
            user={auth}
            activeSection={activeSection}
            onLogout={onLogout}
            onToggleSidebar={NOOP}
            showMenuButton={false}
          />
          <Box sx={useBottomNav ? contentWithBottomNavSx : contentBaseSx}>
            <Box sx={innerSx} className="page-enter contain-layout">
              <ErrorBoundary>
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
                  {RoleView ? (
                    <RoleView
                      token={auth.token}
                      activeSection={activeSection}
                    />
                  ) : null}
                </Suspense>
              </ErrorBoundary>
            </Box>
          </Box>
        </Box>
        {useBottomNav && (
          <BottomNav
            role={auth.role}
            activeSection={activeSection}
            onNavigate={onNavigate}
          />
        )}
      </Box>
    </ThemeProvider>
  );
}
