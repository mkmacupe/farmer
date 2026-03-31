import { beforeEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (payload, init = {}) => new Response(JSON.stringify(payload), {
  status: 200,
  headers: { 'content-type': 'application/json' },
  ...init
});

describe('api/auth', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    vi.unstubAllEnvs();
    vi.stubEnv('VITE_API_BASE', '/api');
    vi.stubEnv('VITE_PROXY_API_TARGET', '');
    vi.stubEnv('VITE_PROXY_API_HOST', '');
    vi.stubEnv('VITE_PROXY_API_PORT', '');
  });

  it('exports local login behavior without warmup hint for relative /api base', async () => {
    const authApi = await import('./auth.js');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ token: 'jwt', username: 'manager' })
    );

    expect(authApi.LOGIN_LOADING_MESSAGE).toBe('Выполняем вход...');
    await expect(authApi.login('manager', 'secret')).resolves.toEqual({
      token: 'jwt',
      username: 'manager'
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/login');
  });

  it('shows warmup login hint for remote backend base', async () => {
    vi.stubEnv('VITE_API_BASE', 'https://farm-sales-backend.onrender.com/api');
    vi.resetModules();

    const authApi = await import('./auth.js');

    expect(authApi.LOGIN_LOADING_MESSAGE).toContain('Подключаем сервер');
  });
});
