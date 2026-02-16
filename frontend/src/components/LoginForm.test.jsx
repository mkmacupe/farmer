import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm.jsx';

describe('LoginForm', () => {
  it('requires username and password before submit', async () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    const button = screen.getByRole('button', { name: /войти/i });
    expect(button).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/логин/i), {
      target: { value: 'mogilevkhim' }
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

  it('fills demo credentials with password 1', async () => {
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} loading={false} error="" />);

    await userEvent.click(screen.getByRole('button', { name: 'manager' }));
    await userEvent.click(screen.getByRole('button', { name: /войти/i }));

    expect(onLogin).toHaveBeenCalledWith('manager', '1');
  });

  it('shows server error message', () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="Неверный пароль" />);
    expect(screen.getByText('Неверный пароль')).toBeInTheDocument();
  });

  it('toggles password visibility', async () => {
    const uiUser = userEvent.setup();
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    const passwordInput = screen.getByLabelText(/пароль/i);
    expect(passwordInput).toHaveAttribute('type', 'password');

    await uiUser.click(screen.getByRole('button', { name: /показать символы/i }));
    expect(passwordInput).toHaveAttribute('type', 'text');
  });

  it('disables submit button while loading', () => {
    render(<LoginForm onLogin={() => {}} loading error="" />);
    expect(screen.getByRole('button', { name: /вход/i })).toBeDisabled();
  });

});
