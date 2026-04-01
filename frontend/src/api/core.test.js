import { beforeEach, describe, expect, it, vi } from 'vitest';

const emptyResponse = (status = 200) => new Response(null, { status });

describe('api/core auth invalidation', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    vi.unstubAllEnvs();
    vi.stubEnv('VITE_API_BASE', '/api');
    vi.stubEnv('VITE_PROXY_API_TARGET', '');
    vi.stubEnv('VITE_PROXY_API_HOST', '');
    vi.stubEnv('VITE_PROXY_API_PORT', '');
  });

  it('dispatches a global auth-expired event for 401 responses on authorized requests', async () => {
    const listener = vi.fn();
    window.addEventListener('farm-sales-auth-expired', listener);

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(emptyResponse(401));
    const { apiFetch } = await import('./core.js');

    const response = await apiFetch('/api/products', {
      headers: { Authorization: 'Bearer stale-token' }
    });

    expect(response.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledTimes(1);

    window.removeEventListener('farm-sales-auth-expired', listener);
  });
});
