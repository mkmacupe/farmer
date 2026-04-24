export const loadAuthenticatedApp = () => import("./AuthenticatedApp.jsx");

export const VIEW_LOADERS = {
  DIRECTOR: () => import("./views/DirectorView.jsx"),
  MANAGER: () => import("./views/ManagerView.jsx"),
  LOGISTICIAN: () => import("./views/LogisticianView.jsx"),
  DRIVER: () => import("./views/DriverView.jsx"),
};

export function preloadRoleView(role) {
  const loadView = VIEW_LOADERS[role];
  return loadView ? loadView() : Promise.resolve();
}
