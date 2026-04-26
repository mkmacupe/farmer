import { memo, useEffect, useState } from 'react';
import './LoginForm.css';

function LoginForm({ onLogin, loading, error, loadingMessage }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showLoadingHint, setShowLoadingHint] = useState(false);

  const canSubmit = username.trim().length > 0 && password.length > 0;

  useEffect(() => {
    if (!loading) {
      setShowLoadingHint(false);
      return undefined;
    }

    const timerId = window.setTimeout(() => {
      setShowLoadingHint(true);
    }, 5000);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [loading]);

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

          <form className="login-form" autoComplete="off" onSubmit={submit}>
            <label htmlFor="login-username">Логин</label>
            <input
              id="login-username"
              name="username"
              type="text"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
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
              autoComplete="off"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />

            <button type="submit" disabled={loading || !canSubmit}>
              {loading ? 'Вход…' : 'Войти'}
            </button>
            {loading && showLoadingHint ? (
              <p className="login-hint" role="status" aria-live="polite">
                {loadingMessage || 'Подключаем сервер. Если backend был в спящем режиме, это может занять до 30 секунд.'}
              </p>
            ) : null}
          </form>
        </div>
      </section>
    </main>
  );
}

export default memo(LoginForm);
