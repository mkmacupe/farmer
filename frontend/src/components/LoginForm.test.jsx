import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from './LoginForm.jsx';

describe('LoginForm', () => {
  it('requires username and password before submit', async () => {
    render(<LoginForm onLogin={() => {}} loading={false} error="" />);

    const button = screen.getByRole('button', { name: /войти/i });
    expect(button).toBeDisabled();

    await userEvent.type(screen.getByLabelText(/логин/i), 'mogilevkhim');
    expect(button).toBeDisabled();

    await userEvent.type(screen.getByLabelText(/пароль/i), 'secret123');
    expect(button).toBeEnabled();
  });

  it('submits credentials to callback', async () => {
    const onLogin = vi.fn();
    render(<LoginForm onLogin={onLogin} loading={false} error="" />);

    await userEvent.type(screen.getByLabelText(/логин/i), 'manager');
    await userEvent.type(screen.getByLabelText(/пароль/i), 'secret123');
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

});
