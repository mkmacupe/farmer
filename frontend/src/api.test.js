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

let api;

beforeEach(async () => {
  vi.restoreAllMocks();
  vi.unmock('./api.js');
  api = await import('./api.js');
});

describe('api', () => {
  it('login sends POST request with credentials', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ token: 'jwt' }));

    const result = await api.login('manager', 'secret');

    expect(result).toEqual({ token: 'jwt' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/login');
    expect(options.method).toBe('POST');
    expect(options.headers['Content-Type']).toBe('application/json');
    expect(options.body).toBe(JSON.stringify({ username: 'manager', password: 'secret' }));
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
    expect(url).toBe('/api/products');
    expect(options.method).toBe('POST');
    expect(options.headers.Authorization).toBe('Bearer token-4');
    expect(options.body).toBe(JSON.stringify(payload));
  });

  it('updateProduct sends PUT to product endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 11 }));
    await api.updateProduct('token-5', 11, { name: 'Сыр' });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/products/11');
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
    expect(blob).toBeInstanceOf(Blob);
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

  it('order workflow requests use POST and auth headers', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(
      jsonResponse({ ok: true })
    ));

    await api.approveOrder('token-13', 300);
    await api.assignOrderDriver('token-13', 300, 77);
    await api.markOrderDelivered('token-13', 300);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/orders/300/approve');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/orders/300/assign-driver');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][1].body).toBe(JSON.stringify({ driverId: 77 }));
    expect(fetchMock.mock.calls[2][0]).toBe('/api/orders/300/deliver');
    expect(fetchMock.mock.calls[2][1].method).toBe('POST');
  });
});
