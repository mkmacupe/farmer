import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App.jsx';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';
import {
  getAssignedOrders,
  getDirectorAddresses,
  getDirectorProfile,
  getMyOrders,
  getProductCategories,
  getProductsPage,
  login,
  subscribeNotifications
} from './api.js';

vi.mock('./authStorage.js', () => ({
  loadAuth: vi.fn(),
  saveAuth: vi.fn(),
  clearAuth: vi.fn()
}));

vi.mock('./views/DirectorView.jsx', () => ({
  default: () => <h2>Профиль директора</h2>
}));

vi.mock('./views/ManagerView.jsx', () => ({
  default: () => <h2>Панель менеджера</h2>
}));

vi.mock('./views/LogisticianView.jsx', () => ({
  default: () => <h2>Логистика и назначения</h2>
}));

vi.mock('./views/DriverView.jsx', () => ({
  default: () => <h2>Мои доставки</h2>
}));

vi.mock('./api.js', () => ({
  login: vi.fn(),
  getProductsPage: vi.fn(),
  getProductCategories: vi.fn(),
  getDirectorProfile: vi.fn(),
  updateDirectorProfile: vi.fn(),
  getDirectorAddresses: vi.fn(),
  createDirectorAddress: vi.fn(),
  updateDirectorAddress: vi.fn(),
  deleteDirectorAddress: vi.fn(),
  lookupGeo: vi.fn(),
  reverseGeo: vi.fn(),
  createOrder: vi.fn(),
  repeatOrder: vi.fn(),
  getMyOrders: vi.fn(),
  getAssignedOrders: vi.fn(),
  getAllOrders: vi.fn(),
  approveOrder: vi.fn(),
  assignOrderDriver: vi.fn(),
  markOrderDelivered: vi.fn(),
  getOrderTimeline: vi.fn(),
  createDirectorUser: vi.fn(),
  getDirectors: vi.fn(),
  getDrivers: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  deleteProduct: vi.fn(),
  downloadOrdersReport: vi.fn(),
  getDashboardSummary: vi.fn(),
  getAuditLogs: vi.fn(),
  getStockMovements: vi.fn(),
  subscribeNotifications: vi.fn()
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    loadAuth.mockReturnValue(null);
    login.mockResolvedValue({
      token: 'jwt-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });
    getProductsPage.mockResolvedValue({
      items: [],
      page: 0,
      size: 24,
      totalItems: 0,
      totalPages: 0,
      hasNext: false
    });
    getProductCategories.mockResolvedValue([]);
    getDirectorProfile.mockResolvedValue({
      id: 1,
      username: 'mogilevkhim',
      fullName: 'Олег Курилин',
      phone: '+375291112233',
      legalEntityName: 'ОАО "Могилевхимволокно"'
    });
    getDirectorAddresses.mockResolvedValue([]);
    getMyOrders.mockResolvedValue([]);
    getAssignedOrders.mockResolvedValue([]);
    subscribeNotifications.mockReturnValue(() => {});
  });

  it('shows login form for anonymous user', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: /вход/i })).toBeInTheDocument();
  });

  it('switches to role view after successful login', async () => {
    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: 'driver' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'secret123' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('driver', 'secret123'));
    expect(await screen.findByRole('heading', { name: /мои доставки/i, level: 1 })).toBeInTheDocument();
    expect(saveAuth).toHaveBeenCalled();
  });

  it('renders role view from persisted auth', async () => {
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: /мои доставки/i, level: 1 })).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs out back to login screen', async () => {
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });
    render(<App />);
    await screen.findByRole('heading', { name: /мои доставки/i, level: 1 });

    fireEvent.click(screen.getByRole('button', { name: /выйти/i }));

    await waitFor(() => {
      expect(clearAuth).toHaveBeenCalledTimes(1);
    });
    expect(await screen.findByRole('heading', { name: /вход/i })).toBeInTheDocument();
  }, 30000);

});
