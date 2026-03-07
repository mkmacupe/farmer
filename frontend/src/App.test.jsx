import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App.jsx';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';
import { demoLogin, login, primeBackendWarmup } from './api.js';

vi.mock('./authStorage.js', () => ({
  loadAuth: vi.fn(),
  saveAuth: vi.fn(),
  clearAuth: vi.fn()
}));

vi.mock('./api.js', () => ({
  demoLogin: vi.fn(),
  login: vi.fn(),
  primeBackendWarmup: vi.fn(() => Promise.resolve(true))
}));

vi.mock('./AuthenticatedApp.jsx', () => ({
  default: ({ auth, activeSection, onLogout, onNavigate }) => (
    <div>
      <h1>{auth.role === 'DRIVER' ? 'Мои доставки' : 'Рабочий кабинет'}</h1>
      <p data-testid="active-section">{activeSection}</p>
      <button type="button" onClick={() => onNavigate('custom-section')}>Перейти</button>
      <button type="button" onClick={onLogout}>Выйти</button>
    </div>
  )
}));

describe('App', () => {
  const waitForWorkspaceReady = async () => {
    await waitFor(() => {
      expect(screen.queryByText(/загружаем рабочее пространство/i)).not.toBeInTheDocument();
    });
  };

  beforeEach(() => {
    vi.clearAllMocks();
    loadAuth.mockReturnValue(null);
    demoLogin.mockResolvedValue({
      token: 'jwt-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });
    login.mockResolvedValue({
      token: 'jwt-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });
    primeBackendWarmup.mockResolvedValue(true);
  });

  it('shows login form for anonymous user', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: /вход/i })).toBeInTheDocument();
  });

  it('switches to authenticated shell after successful login', async () => {
    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: 'driver' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'secret123' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('driver', 'secret123'));
    await waitForWorkspaceReady();
    expect(await screen.findByRole('heading', { name: /мои доставки/i, level: 1 }, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('driver-orders');
    expect(saveAuth).toHaveBeenCalledWith(expect.objectContaining({
      token: 'jwt-token',
      username: 'driver',
      role: 'DRIVER'
    }));
  });

  it('shows backend error on failed login', async () => {
    login.mockRejectedValueOnce(new Error('Неверный логин или пароль'));
    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: 'driver' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    expect(await screen.findByText('Неверный логин или пароль')).toBeInTheDocument();
    expect(saveAuth).not.toHaveBeenCalled();
  });

  it('fills login form when profile button is clicked', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'manager' }));

    expect(screen.getByLabelText(/логин/i)).toHaveValue('manager');
    expect(screen.getByLabelText(/пароль/i)).toHaveValue('MgrD5v8cN4');
    expect(demoLogin).not.toHaveBeenCalled();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs in with autofilled unique profile password after manual submit', async () => {
    login.mockResolvedValueOnce({
      token: 'manager-token',
      username: 'manager',
      fullName: 'Manager',
      role: 'MANAGER'
    });

    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'manager' }));
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('manager', 'MgrD5v8cN4'));
    await waitForWorkspaceReady();
    expect(await screen.findByRole('heading', { name: /рабочий кабинет/i, level: 1 }, { timeout: 10_000 })).toBeInTheDocument();
  });

  it('trims username before login request', async () => {
    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: '  driver  ' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'secret123' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('driver', 'secret123'));
  });

  it('sets role default section after login', async () => {
    login.mockResolvedValueOnce({
      token: 'manager-token',
      username: 'manager',
      fullName: 'Manager',
      role: 'MANAGER'
    });

    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: 'manager' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'secret123' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitForWorkspaceReady();
    expect(await screen.findByRole('heading', { name: /рабочий кабинет/i, level: 1 }, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('manager-dashboard');
  });

  it('renders authenticated shell from persisted auth', async () => {
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });

    render(<App />);

    await waitForWorkspaceReady();
    expect(await screen.findByRole('heading', { name: /мои доставки/i, level: 1 }, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('driver-orders');
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
    await waitForWorkspaceReady();
    await screen.findByRole('heading', { name: /мои доставки/i, level: 1 }, { timeout: 10_000 });

    fireEvent.click(screen.getByRole('button', { name: /выйти/i }));

    await waitFor(() => {
      expect(clearAuth).toHaveBeenCalledTimes(1);
    });
    expect(await screen.findByRole('heading', { name: /вход/i })).toBeInTheDocument();
  });

  it('updates active section when authenticated app triggers navigation', async () => {
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });

    render(<App />);
    await waitForWorkspaceReady();
    await screen.findByRole('heading', { name: /мои доставки/i, level: 1 }, { timeout: 10_000 });
    fireEvent.click(screen.getByRole('button', { name: /перейти/i }));
    expect(screen.getByTestId('active-section')).toHaveTextContent('custom-section');
  });

  it('shows default login error when backend rejection has no message', async () => {
    login.mockRejectedValueOnce({});
    render(<App />);

    fireEvent.change(screen.getByLabelText(/логин/i), { target: { value: 'driver' } });
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    expect(await screen.findByText('Не удалось войти')).toBeInTheDocument();
  });

  it('keeps empty section for persisted auth with unknown role', async () => {
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'unknown',
      fullName: 'Unknown Role',
      role: 'UNKNOWN'
    });

    render(<App />);
    await waitForWorkspaceReady();
    expect(await screen.findByRole('heading', { name: /рабочий кабинет/i, level: 1 }, { timeout: 10_000 })).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('');
    expect(saveAuth).toHaveBeenCalledWith(expect.objectContaining({ role: 'UNKNOWN' }));
  });
});
