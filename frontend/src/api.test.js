import { beforeEach, describe, expect, it, vi } from 'vitest';

const jsonResponse = (payload, init = {}) => new Response(JSON.stringify(payload), {
  status: 200,
  headers: { 'content-type': 'application/json' },
  ...init
});

const textResponse = (text, init = {}) => new Response(text, {
  status: 200,
  headers: { 'content-type': 'text/plain; charset=utf-8' },
  ...init
});

const sseResponse = (chunks = []) => {
  const encoder = new TextEncoder();
  let index = 0;
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'text/event-stream' }),
    body: {
      getReader() {
        return {
          async read() {
            if (index >= chunks.length) {
              return { done: true, value: undefined };
            }
            const value = encoder.encode(chunks[index]);
            index += 1;
            return { done: false, value };
          }
        };
      }
    }
  };
};

let api;

beforeEach(async () => {
  vi.restoreAllMocks();
  vi.unmock('./api.js');
  vi.unstubAllEnvs();
  vi.stubEnv('VITE_API_BASE', '/api');
  vi.stubEnv('VITE_PROXY_API_TARGET', '');
  vi.stubEnv('VITE_PROXY_API_HOST', '');
  vi.stubEnv('VITE_PROXY_API_PORT', '');
  api = await import('./api.js');
});

describe('api', () => {
  it('uses custom API base from VITE_API_BASE env variable', async () => {
    vi.stubEnv('VITE_API_BASE', '/custom-api');
    vi.resetModules();
    const customApi = await import('./api.js');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    await customApi.login('manager', 'secret');

    expect(fetchMock.mock.calls[0][0]).toBe('/custom-api/auth/login');
    vi.unstubAllEnvs();
    vi.resetModules();
    api = await import('./api.js');
  });

  it('falls back to default API base when VITE_API_BASE is empty', async () => {
    vi.stubEnv('VITE_API_BASE', '');
    vi.resetModules();
    const defaultApi = await import('./api.js');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    await defaultApi.login('manager', 'secret');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/login');
    vi.unstubAllEnvs();
    vi.resetModules();
    api = await import('./api.js');
  });

  it('keeps relative /api base when VITE_API_BASE is set to /api', async () => {
    vi.stubEnv('VITE_API_BASE', '/api');
    vi.stubEnv('VITE_PROXY_API_HOST', '127.0.0.1');
    vi.stubEnv('VITE_PROXY_API_PORT', '8080');
    vi.resetModules();
    const defaultApi = await import('./api.js');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    await defaultApi.login('manager', 'secret');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/login');
    vi.unstubAllEnvs();
    vi.resetModules();
    api = await import('./api.js');
  });

  it('login sends POST request with credentials', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    const result = await api.login('manager', 'secret');

    expect(result).toEqual({ token: 'jwt' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/auth/login');
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(options.body).toBe(JSON.stringify({ username: 'manager', password: 'secret' }));
  });

  it('demoLogin sends POST request with username only', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt-demo' }));

    const result = await api.demoLogin('manager');

    expect(result).toEqual({ token: 'jwt-demo' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/auth/demo-login');
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(options.body).toBe(JSON.stringify({ username: 'manager' }));
  });

  it('getProductsPage builds query parameters and auth header', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      items: [{ id: 1 }],
      page: 1,
      size: 20,
      totalItems: 21,
      totalPages: 2,
      hasNext: true
    }));

    const page = await api.getProductsPage('token-1', {
      category: 'Овощи',
      q: 'картофель',
      page: 1,
      size: 20
    });

    expect(page.items).toHaveLength(1);
    const [url, options] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/products');
    expect(parsed.searchParams.get('category')).toBe('Овощи');
    expect(parsed.searchParams.get('q')).toBe('картофель');
    expect(parsed.searchParams.get('page')).toBe('1');
    expect(parsed.searchParams.get('size')).toBe('20');
    expect(options.headers.Authorization).toBe('Bearer token-1');
  });

  it('getProductsPage normalizes plain array response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([
      { id: 1, name: 'Молоко' },
      { id: 2, name: 'Хлеб' }
    ]));

    const page = await api.getProductsPage('token-2', { page: 2, size: 10 });

    expect(page).toEqual({
      items: [{ id: 1, name: 'Молоко' }, { id: 2, name: 'Хлеб' }],
      page: 2,
      size: 10,
      totalItems: 2,
      totalPages: 1,
      hasNext: false
    });
  });

  it('getProducts returns items list from normalized page', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      items: [{ id: 9 }],
      page: 0,
      size: 24,
      totalItems: 1,
      totalPages: 1,
      hasNext: false
    }));

    const items = await api.getProducts('token-3');

    expect(items).toEqual([{ id: 9 }]);
  });

  it('createProduct sends JSON payload with auth', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 10 }));
    const payload = { name: 'Творог', category: 'Молочка', price: 6.5, stockQuantity: 7 };

    const response = await api.createProduct('token-4', payload);

    expect(response).toEqual({ id: 10 });
    const [url, options] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/products');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe('Bearer token-4');
    expect(options.body).toBe(JSON.stringify(payload));
  });

  it('updateProduct sends PUT to product endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 11 }));
    await api.updateProduct('token-5', 11, { name: 'Сыр' });

    const [url, options] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/products/11');
    expect(options.method).toBe('PUT');
  });

  it('deleteProduct handles 204 without response body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    const result = await api.deleteProduct('token-6', 4);
    expect(result).toBeNull();
  });

  it('uses JSON error detail in thrown message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(
      { detail: 'Некорректные данные' },
      { status: 400 }
    ));

    await expect(api.getMyOrders('token-7')).rejects.toThrow('Некорректные данные');
  });

  it('falls back to text error message when JSON is missing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(textResponse('Ошибка сервера', { status: 500 }));
    await expect(api.getProductCategories('token-8')).rejects.toThrow('Ошибка сервера');
  });

  it('handles error response without content-type header', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 502,
      headers: new Headers(),
      clone() {
        throw new Error('clone should not be called without json content-type');
      },
      async text() {
        return 'Ошибка без content-type';
      }
    });

    await expect(api.getProductCategories('token-no-content-type')).rejects.toThrow('Ошибка без content-type');
  });

  it('handles null and empty-object JSON errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse(null, { status: 400 }));
    await expect(api.getProducts('token-null')).rejects.toThrow('null');

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({}, { status: 400 }));
    await expect(api.getProducts('token-empty')).rejects.toThrow('{}');
  });

  it('maps network TypeError to unavailable backend message', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(api.login('u', 'p')).rejects.toThrow('Сервер недоступен. Убедитесь, что backend запущен.');
  });

  it('maps AbortError to timeout message', async () => {
    const abortError = new Error('aborted');
    abortError.name = 'AbortError';
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(abortError);
    await expect(api.getProductCategories('token-9')).rejects.toThrow('Истекло время ожидания ответа от сервера.');
  });

  it('allows request options for getProductCategories and can call without auth token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));
    await api.getProductCategories(undefined, {
      headers: { 'X-Request-Id': 'abc-1' }
    });

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers.Authorization).toBeUndefined();
    expect(options.headers['X-Request-Id']).toBe('abc-1');
  });

  it('accepts null options in getProductCategories', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));
    await api.getProductCategories('token-null-options', null);

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers.Authorization).toBe('Bearer token-null-options');
  });

  it('handles internal timeout abort via setTimeout', async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      }, { once: true });
    }));

    const promise = expect(
      api.getProductCategories('token-timeout', { timeoutMs: 50 })
    ).rejects.toThrow('Истекло время ожидания ответа от сервера.');
    await vi.advanceTimersByTimeAsync(60);
    await promise;

    vi.useRealTimers();
  });

  it('propagates external abort signal to request controller', async () => {
    const externalController = new AbortController();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      }, { once: true });
    }));

    const promise = api.getProductCategories('token-signal', { signal: externalController.signal });
    externalController.abort();
    await expect(promise).rejects.toThrow('Истекло время ожидания ответа от сервера.');
  });

  it('handles already aborted external signal', async () => {
    const externalController = new AbortController();
    externalController.abort();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_, options) => Promise.reject(Object.assign(new Error('abort'), {
      name: 'AbortError',
      signal: options.signal
    })));

    await expect(api.getProductCategories('token-signal-aborted', { signal: externalController.signal }))
      .rejects.toThrow('Истекло время ожидания ответа от сервера.');
  });

  it('downloadOrdersReport returns blob for successful response', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('excel-bytes', {
        status: 200,
        headers: { 'content-type': 'application/octet-stream' }
      })
    );

    const blob = await api.downloadOrdersReport('token-10', {
      from: '2026-02-01',
      to: '2026-02-10',
      status: 'CREATED'
    });

    const [url] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/reports/orders');
    expect(parsed.searchParams.get('from')).toBe('2026-02-01');
    expect(parsed.searchParams.get('to')).toBe('2026-02-10');
    expect(parsed.searchParams.get('status')).toBe('CREATED');
    expect(blob).toBeTruthy();
    expect(typeof blob.size).toBe('number');
    expect(blob.size).toBeGreaterThan(0);
    expect(typeof blob.type).toBe('string');
    expect(blob.type).toContain('application/octet-stream');
  });

  it('getDashboardSummary builds query string', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ totalOrders: 5 }));

    const summary = await api.getDashboardSummary('token-11', { from: '2026-02-01', to: '2026-02-16' });

    expect(summary).toEqual({ totalOrders: 5 });
    const [url] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/dashboard/summary');
    expect(parsed.searchParams.get('from')).toBe('2026-02-01');
    expect(parsed.searchParams.get('to')).toBe('2026-02-16');
  });

  it('getStockMovements sends all optional filters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));

    await api.getStockMovements('token-12', {
      productId: 42,
      from: '2026-02-01',
      to: '2026-02-16',
      limit: 50
    });

    const [url] = fetchMock.mock.calls[0];
    const parsed = new URL(url, 'http://localhost');
    expect(parsed.pathname).toBe('/api/stock-movements');
    expect(parsed.searchParams.get('productId')).toBe('42');
    expect(parsed.searchParams.get('from')).toBe('2026-02-01');
    expect(parsed.searchParams.get('to')).toBe('2026-02-16');
    expect(parsed.searchParams.get('limit')).toBe('50');
  });

  it('builds dashboard and stock endpoints when optional params are omitted', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse([])));

    await api.getDashboardSummary('token-no-summary-params');
    await api.getStockMovements('token-no-stock-params');

    const dashboardUrl = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(dashboardUrl.pathname).toBe('/api/dashboard/summary');
    expect(dashboardUrl.search).toBe('');

    const stockUrl = new URL(fetchMock.mock.calls[1][0], 'http://localhost');
    expect(stockUrl.pathname).toBe('/api/stock-movements');
    expect(stockUrl.search).toBe('');
  });

  it('order workflow requests use POST and auth headers', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(
      jsonResponse({ ok: true })
    ));

    await api.approveOrder('token-13', 300);
    await api.assignOrderDriver('token-13', 300, 77);
    await api.markOrderDelivered('token-13', 300);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(new URL(fetchMock.mock.calls[0][0], 'http://localhost').pathname).toBe('/api/orders/300/approve');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[1][0], 'http://localhost').pathname).toBe('/api/orders/300/assign-driver');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][1].body).toBe(JSON.stringify({ driverId: 77 }));
    expect(new URL(fetchMock.mock.calls[2][0], 'http://localhost').pathname).toBe('/api/orders/300/deliver');
    expect(fetchMock.mock.calls[2][1].method).toBe('POST');
  });

  it('builds auto-assign preview and approve endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(
      jsonResponse({ ok: true })
    ));

    await api.autoAssignOrders('token-14');
    await api.previewAutoAssignOrders('token-14');
    await api.approveAutoAssignOrders('token-14', [{ orderId: 1, driverId: 7, stopSequence: 1 }]);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(new URL(fetchMock.mock.calls[0][0], 'http://localhost').pathname).toBe('/api/orders/auto-assign/preview');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[1][0], 'http://localhost').pathname).toBe('/api/orders/auto-assign/preview');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[2][0], 'http://localhost').pathname).toBe('/api/orders/auto-assign/approve');
    expect(fetchMock.mock.calls[2][1].method).toBe('POST');
    expect(fetchMock.mock.calls[2][1].body).toBe(JSON.stringify({
      assignments: [{ orderId: 1, driverId: 7, stopSequence: 1 }]
    }));
  });

  it('uses details array message from backend error payload', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(
      { details: ['Поле name обязательно'] },
      { status: 400 }
    ));
    await expect(api.createProduct('token-14', {})).rejects.toThrow('Поле name обязательно');
  });

  it('uses string payload message from backend error response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse('  Ошибка валидации  ', { status: 400 }));
    await expect(api.getDirectors('token-15')).rejects.toThrow('Ошибка валидации');
  });

  it('falls back to generic status message when error body is empty', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', {
      status: 503,
      headers: { 'content-type': 'text/plain; charset=utf-8' }
    }));
    await expect(api.getDrivers('token-16')).rejects.toThrow('Ошибка запроса (503)');
  });

  it('returns text payload for successful non-json response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(textResponse('plain-ok'));
    const result = await api.getAuditLogs('token-17');
    expect(result).toBe('plain-ok');
  });

  it('returns text payload when successful response has no content-type header', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      async text() {
        return 'plain-no-header';
      }
    });

    const result = await api.getAuditLogs('token-17b');
    expect(result).toBe('plain-no-header');
  });

  it('normalizes paged payload defaults when meta fields are invalid', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      items: [{ id: 1 }],
      page: 'bad',
      size: 'bad',
      totalItems: 'bad',
      totalPages: 'bad',
      hasNext: 1
    }));

    const page = await api.getProductsPage('token-18', { page: 3, size: 7 });
    expect(page).toEqual({
      items: [{ id: 1 }],
      page: 3,
      size: 7,
      totalItems: 1,
      totalPages: 1,
      hasNext: true
    });
  });

  it('normalizes empty array payload into zero pages', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));
    const page = await api.getProductsPage('token-empty-list');
    expect(page).toEqual({
      items: [],
      page: 0,
      size: 24,
      totalItems: 0,
      totalPages: 0,
      hasNext: false
    });
  });

  it('normalizes unexpected object payload into empty page', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ unexpected: true }));
    const page = await api.getProductsPage('token-object-list', { page: 4, size: 9 });
    expect(page).toEqual({
      items: [],
      page: 4,
      size: 9,
      totalItems: 0,
      totalPages: 0,
      hasNext: false
    });
  });

  it('handles director profile and address endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));
    const token = 'token-director';
    const profilePayload = { fullName: 'Иван', phone: '+375291112233' };
    const addressPayload = { label: 'Store #1', addressLine: 'Mogilev' };

    await api.getDirectorProfile(token);
    await api.updateDirectorProfile(token, profilePayload);
    await api.getDirectorAddresses(token);
    await api.createDirectorAddress(token, addressPayload);
    await api.updateDirectorAddress(token, 12, addressPayload);
    await api.deleteDirectorAddress(token, 12);

    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(new URL(fetchMock.mock.calls[0][0], 'http://localhost').pathname).toBe('/api/director/profile');
    expect(new URL(fetchMock.mock.calls[1][0], 'http://localhost').pathname).toBe('/api/director/profile');
    expect(fetchMock.mock.calls[1][1].method).toBe('PATCH');
    expect(fetchMock.mock.calls[1][1].body).toBe(JSON.stringify(profilePayload));
    expect(new URL(fetchMock.mock.calls[2][0], 'http://localhost').pathname).toBe('/api/director/addresses');
    expect(new URL(fetchMock.mock.calls[3][0], 'http://localhost').pathname).toBe('/api/director/addresses');
    expect(fetchMock.mock.calls[3][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[4][0], 'http://localhost').pathname).toBe('/api/director/addresses/12');
    expect(fetchMock.mock.calls[4][1].method).toBe('PUT');
    expect(new URL(fetchMock.mock.calls[5][0], 'http://localhost').pathname).toBe('/api/director/addresses/12');
    expect(fetchMock.mock.calls[5][1].method).toBe('DELETE');
  });

  it('handles order and user management read/write endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));
    const token = 'token-admin';

    await api.createOrder(token, { deliveryAddressId: 1, items: [{ productId: 1, quantity: 2 }] });
    await api.repeatOrder(token, 301);
    await api.getAssignedOrders(token);
    await api.getAllOrders(token);
    await api.getOrderTimeline(token, 301);
    await api.createDirectorUser(token, { username: 'd1', fullName: 'Director 1' });
    await api.getDirectors(token);
    await api.getDrivers(token);
    await api.getAuditLogs(token);

    expect(fetchMock).toHaveBeenCalledTimes(9);
    expect(new URL(fetchMock.mock.calls[0][0], 'http://localhost').pathname).toBe('/api/orders');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[1][0], 'http://localhost').pathname).toBe('/api/orders/301/repeat');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[2][0], 'http://localhost').pathname).toBe('/api/orders/assigned');
    expect(new URL(fetchMock.mock.calls[3][0], 'http://localhost').pathname).toBe('/api/orders');
    expect(new URL(fetchMock.mock.calls[4][0], 'http://localhost').pathname).toBe('/api/orders/301/timeline');
    expect(new URL(fetchMock.mock.calls[5][0], 'http://localhost').pathname).toBe('/api/users/directors');
    expect(fetchMock.mock.calls[5][1].method).toBe('POST');
    expect(new URL(fetchMock.mock.calls[6][0], 'http://localhost').pathname).toBe('/api/users/directors');
    expect(new URL(fetchMock.mock.calls[7][0], 'http://localhost').pathname).toBe('/api/users/drivers');
    expect(new URL(fetchMock.mock.calls[8][0], 'http://localhost').pathname).toBe('/api/audit/logs');
  });

  it('builds geo lookup and reverse endpoints with query params', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse([])));

    await api.lookupGeo('token-geo', 'Mogilev', 10);
    await api.reverseGeo('token-geo', 53.9, 30.33);

    const lookupUrl = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(lookupUrl.pathname).toBe('/api/geo/lookup');
    expect(lookupUrl.searchParams.get('q')).toBe('Mogilev');
    expect(lookupUrl.searchParams.get('limit')).toBe('10');

    const reverseUrl = new URL(fetchMock.mock.calls[1][0], 'http://localhost');
    expect(reverseUrl.pathname).toBe('/api/geo/reverse');
    expect(reverseUrl.searchParams.get('lat')).toBe('53.9');
    expect(reverseUrl.searchParams.get('lon')).toBe('30.33');
  });

  it('throws report download error using backend message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(textResponse('Нет доступа', { status: 403 }));
    await expect(api.downloadOrdersReport('token-19')).rejects.toThrow('Нет доступа');
  });

  it('passes through unknown fetch errors without remapping', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('boom-custom'));
    await expect(api.getProductCategories('token-20')).rejects.toThrow('boom-custom');
  });

  it('subscribes to notifications, parses payload and supports unsubscribe', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse([
      ':heartbeat\n',
      'event: notification\n',
      'data: {"id":1,"title":"New"}\n',
      '\n',
      'event: notification\n',
      'data: {"broken":\n',
      '\n'
    ]));
    const onNotification = vi.fn();
    const onError = vi.fn();

    const unsubscribe = api.subscribeNotifications('token-sse', { onNotification, onError });
    await vi.waitFor(() => expect(onNotification).toHaveBeenCalledWith({ id: 1, title: 'New' }));

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers.Accept).toBe('text/event-stream');
    expect(options.headers.Authorization).toBe('Bearer token-sse');
    expect(options.timeoutMs).toBeUndefined();
    expect(options.signal).toBeTruthy();
    expect(onError).not.toHaveBeenCalled();

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('handles non-ok SSE response through onError callback', async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers({ 'content-type': 'application/json' }),
      clone() {
        return {
          async json() {
            return { message: 'SSE unauthorized' };
          }
        };
      },
      async text() {
        return '';
      }
    });
    const onError = vi.fn();
    const unsubscribe = api.subscribeNotifications('token-sse-unauthorized', { onError });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onError.mock.calls[0][0].message).toContain('SSE unauthorized');
    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('skips reconnect scheduling when onError callback closes subscription', async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('stream-hard-fail'));
    const onError = vi.fn();
    let unsubscribe = () => {};

    unsubscribe = api.subscribeNotifications('token-sse-stop', {
      onError: (error) => {
        onError(error);
        unsubscribe();
      }
    });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(15_000);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('ignores non-notification events and empty event names in SSE stream', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse([
      'event: ping\n',
      'data: {"id":123}\n',
      '\n',
      'id: 99\n',
      'event:\n',
      'data: {"id":456}\n',
      '\n'
    ]));
    const onNotification = vi.fn();

    const unsubscribe = api.subscribeNotifications('token-sse-events', { onNotification });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(5);
    expect(onNotification).not.toHaveBeenCalled();

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('does not schedule reconnect when unsubscribed before stream loop completes', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse([]));

    const unsubscribe = api.subscribeNotifications('token-unsub-before-loop');
    unsubscribe();

    await Promise.resolve();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(2_000);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('reconnects after stream end', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(sseResponse([]));

    const unsubscribe = api.subscribeNotifications('token-reconnect');
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    await vi.advanceTimersByTimeAsync(1500);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('retries stream when onError callback is not provided', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('stream-down'))
      .mockResolvedValue(sseResponse([]));

    const unsubscribe = api.subscribeNotifications('token-no-onerror');
    await vi.advanceTimersByTimeAsync(1500);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('does not reconnect when timer callback runs after unsubscribe', async () => {
    vi.useFakeTimers();
    vi.spyOn(window, 'clearTimeout').mockImplementation(() => {});
    const onError = vi.fn();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('stream-down-once'))
      .mockResolvedValue(sseResponse([]));

    const unsubscribe = api.subscribeNotifications('token-reconnect-cancelled', { onError });
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));

    unsubscribe();
    await vi.advanceTimersByTimeAsync(1500);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('calls onError and schedules reconnect when initial fetch fails', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new Error('stream-down'))
      .mockResolvedValue(sseResponse([]));
    const onError = vi.fn();

    const unsubscribe = api.subscribeNotifications('token-fail', { onError });
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onError.mock.calls[0][0]).toBeInstanceOf(Error);

    await vi.advanceTimersByTimeAsync(1500);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('reports browser stream support error when response body is missing', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'text/event-stream' }),
      body: null
    });
    const onError = vi.fn();

    const unsubscribe = api.subscribeNotifications('token-nobody', { onError });
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onError.mock.calls[0][0].message).toContain('Поток уведомлений не поддерживается');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    unsubscribe();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('does not call onError after unsubscribe when request fails later', async () => {
    let rejectFetch;
    const pendingFetch = new Promise((_, reject) => {
      rejectFetch = reject;
    });
    vi.spyOn(globalThis, 'fetch').mockReturnValue(pendingFetch);
    const onError = vi.fn();

    const unsubscribe = api.subscribeNotifications('token-late', { onError });
    unsubscribe();

    rejectFetch(new Error('late-failure'));
    await Promise.resolve();
    await Promise.resolve();
    expect(onError).not.toHaveBeenCalled();
  });
});
