import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App.jsx';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';
import { login, primeBackendWarmup } from './api/auth.js';

vi.mock('./authStorage.js', () => ({
  loadAuth: vi.fn(),
  saveAuth: vi.fn(),
  clearAuth: vi.fn()
}));

vi.mock('./api/auth.js', () => ({
  LOGIN_LOADING_MESSAGE: 'Подключаем сервер...',
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
  const findWorkspaceHeading = (name) => screen.findByRole('heading', { name, level: 1 }, { timeout: 10_000 });

  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/');
    loadAuth.mockReturnValue(null);
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
    expect(await findWorkspaceHeading(/мои доставки/i)).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('driver-orders');
    expect(window.location.hash).toBe('#section=driver-orders');
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
    fireEvent.change(screen.getByLabelText(/пароль/i), { target: { value: 'MgrD5v8cN4' } });
    fireEvent.click(screen.getByRole('button', { name: /войти/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('manager', 'MgrD5v8cN4'));
    expect(await findWorkspaceHeading(/рабочий кабинет/i)).toBeInTheDocument();
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

    expect(await findWorkspaceHeading(/мои доставки/i)).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('driver-orders');
    expect(login).not.toHaveBeenCalled();
  });

  it('restores a valid section from location hash for persisted auth', async () => {
    window.history.replaceState({}, '', '/#section=manager-reports');
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'manager',
      fullName: 'Manager',
      role: 'MANAGER'
    });

    render(<App />);

    expect(await findWorkspaceHeading(/рабочий кабинет/i)).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('manager-reports');
  });

  it('logs out back to login screen', async () => {
    window.history.replaceState({}, '', '/#section=driver-orders');
    loadAuth.mockReturnValue({
      token: 'persisted-token',
      username: 'driver',
      fullName: 'Driver One',
      role: 'DRIVER'
    });

    render(<App />);
    await findWorkspaceHeading(/мои доставки/i);

    fireEvent.click(screen.getByRole('button', { name: /выйти/i }));

    await waitFor(() => {
      expect(clearAuth).toHaveBeenCalledTimes(1);
    });
    expect(window.location.hash).toBe('');
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
    await findWorkspaceHeading(/мои доставки/i);
    fireEvent.click(screen.getByRole('button', { name: /перейти/i }));
    expect(screen.getByTestId('active-section')).toHaveTextContent('custom-section');
    expect(window.location.hash).toBe('#section=custom-section');
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
    expect(await findWorkspaceHeading(/рабочий кабинет/i)).toBeInTheDocument();
    expect(screen.getByTestId('active-section')).toHaveTextContent('');
    expect(saveAuth).toHaveBeenCalledWith(expect.objectContaining({ role: 'UNKNOWN' }));
  });
});
