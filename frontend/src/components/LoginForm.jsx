import { memo, useState } from 'react';
import './LoginForm.css';

const DEMO_ACCOUNTS = [
  { label: 'mogilevkhim', username: 'mogilevkhim' },
  { label: 'mogilevlift', username: 'mogilevlift' },
  { label: 'babushkina', username: 'babushkina' },
  { label: 'manager', username: 'manager' },
  { label: 'logistician', username: 'logistician' },
  { label: 'driver1', username: 'driver1' },
  { label: 'driver2', username: 'driver2' },
  { label: 'driver3', username: 'driver3' }
];

function LoginForm({ onLogin, loading, error }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const canSubmit = username.trim().length > 0 && password.length > 0;

  const applyDemo = (usernameValue) => {
    setUsername(usernameValue);
    setPassword('1');
  };

  const submit = (event) => {
    event.preventDefault();
    onLogin(username.trim(), password);
  };

  return (
    <main className="login-shell">
      <section className="login-card" aria-labelledby="login-title">
        <header className="login-head">
          <h1 id="login-title">Вход</h1>
          <p>Введите логин и пароль, чтобы открыть рабочее пространство.</p>
        </header>

        <div className="login-body">
          {error ? (
            <div className="login-error" role="alert">
              {error}
            </div>
          ) : null}

          <form className="login-form" onSubmit={submit}>
            <label htmlFor="login-username">Логин</label>
            <input
              id="login-username"
              name="username"
              type="text"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoFocus
              required
            />

            <label htmlFor="login-password">Пароль</label>
            <input
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />

            <button type="submit" disabled={loading || !canSubmit}>
              {loading ? 'Вход…' : 'Войти'}
            </button>
          </form>

          <div className="login-divider" aria-hidden="true" />

          <div className="login-demo" role="group" aria-label="Демо аккаунты">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.username}
                type="button"
                className={username === account.username ? 'is-active' : ''}
                onClick={() => applyDemo(account.username)}
                disabled={loading}
              >
                {account.label}
              </button>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}

export default memo(LoginForm);
