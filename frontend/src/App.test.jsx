import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { loadAuth, saveAuth } from './authStorage.js';
import {
  getAssignedOrders,
  getDirectorAddresses,
  getDirectorProfile,
  getMyOrders,
  getProductCategories,
  getProducts,
  login,
  subscribeNotifications
} from './api.js';

vi.mock('./authStorage.js', () => ({
  loadAuth: vi.fn(),
  saveAuth: vi.fn(),
  clearAuth: vi.fn()
}));

vi.mock('./api.js', () => ({
  login: vi.fn(),
  getProducts: vi.fn(),
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
    getProducts.mockResolvedValue([]);
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

    await userEvent.type(screen.getByLabelText(/логин/i), 'driver');
    await userEvent.type(screen.getByLabelText(/пароль/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('driver', 'secret123'));
    expect(await screen.findByText(/мои доставки/i)).toBeInTheDocument();
    expect(saveAuth).toHaveBeenCalled();
  });

});
