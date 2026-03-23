import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm.jsx';
import { vi } from 'vitest';

describe('LoginForm', () => {
  it('requires username and password before submit', async () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    const button = screen.getByRole('button', { name: /войти/i });
    expect(button).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/логин/i), {
      target: { value: 'diralekseev' }
    });
    expect(button).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/пароль/i), {
      target: { value: 'secret123' }
    });
    expect(button).toBeEnabled();
  });

  it('submits credentials to callback', async () => {
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} loading={false} error="" />);

    fireEvent.change(screen.getByLabelText(/логин/i), {
      target: { value: 'manager' }
    });
    fireEvent.change(screen.getByLabelText(/пароль/i), {
      target: { value: 'secret123' }
    });
    await userEvent.click(screen.getByRole('button', { name: /войти/i }));

    expect(onLogin).toHaveBeenCalledWith('manager', 'secret123');
  });

  it('trims username before submit', () => {
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} loading={false} error="" />);

    fireEvent.change(screen.getByLabelText(/логин/i), {
      target: { value: '  manager  ' }
    });
    fireEvent.change(screen.getByLabelText(/пароль/i), {
      target: { value: 'secret123' }
    });
    fireEvent.submit(screen.getByRole('button', { name: /войти/i }).closest('form'));

    expect(onLogin).toHaveBeenCalledWith('manager', 'secret123');
  });

  it('does not render quick account buttons', () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    expect(screen.queryByRole('button', { name: 'manager' })).toBeNull();
    expect(screen.queryByRole('group', { name: /аккаунты/i })).toBeNull();
  });

  it('shows server error message', () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="Неверный пароль" />);
    expect(screen.getByText('Неверный пароль')).toBeInTheDocument();
  });

  it('keeps password field masked', () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    const passwordInput = screen.getByLabelText(/пароль/i);
    expect(passwordInput).toHaveAttribute('type', 'password');
    expect(screen.queryByRole('button', { name: /показать символы/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /скрыть символы/i })).toBeNull();
  });

  it('disables submit button while loading', () => {
    render(<LoginForm onLogin={() => {}} loading error="" />);
    expect(screen.getByRole('button', { name: /вход/i })).toBeDisabled();
  });

  it('shows warmup hint only after 5 seconds of loading', async () => {
    vi.useFakeTimers();
    render(<LoginForm onLogin={() => {}} loading error="" />);

    expect(screen.queryByText(/если backend был в спящем режиме/i)).toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(screen.getByText(/если backend был в спящем режиме/i)).toBeInTheDocument();
    vi.useRealTimers();
  });

});
