const normalizeApiBase = (value) => value.replace(/[\\/]+$/, '');

const buildDefaultApiBase = () => {
  const target = import.meta.env.VITE_PROXY_API_TARGET?.trim();
  if (target) {
    const normalizedTarget = normalizeApiBase(target);
    if (/\/api$/i.test(normalizedTarget)) {
      return normalizedTarget;
    }
    return `${normalizedTarget}/api`;
  }

  const host = import.meta.env.VITE_PROXY_API_HOST?.trim();
  const port = import.meta.env.VITE_PROXY_API_PORT?.trim();
  if (host && port) {
    return `http://${host}:${port}/api`;
  }

  return '/api';
};

const resolveApiBase = () => {
  const configuredApiBase = import.meta.env.VITE_API_BASE?.trim();
  if (configuredApiBase) {
    const normalizedConfiguredApiBase = normalizeApiBase(configuredApiBase);
    return normalizedConfiguredApiBase;
  }
  return buildDefaultApiBase();
};

const API_BASE = resolveApiBase();
const LOGIN_TIMEOUT_MS = 10_000;
const NETWORK_TIMEOUT_MESSAGE = 'Истекло время ожидания ответа от сервера.';
const NETWORK_UNAVAILABLE_MESSAGE = 'Сервер недоступен. Убедитесь, что backend запущен.';
const WARMING_UP_MESSAGE = 'Сервер всё ещё просыпается после простоя. Подождите 10–20 секунд и повторите вход.';
const RETRYABLE_RESPONSE_STATUSES = new Set([408, 425, 429, 502, 503, 504]);
const DEFAULT_RETRY_POLICY = Object.freeze({
  attempts: 2,
  baseDelayMs: 1_200,
  maxDelayMs: 4_000
});
const AUTH_RETRY_POLICY = Object.freeze({
  attempts: 4,
  baseDelayMs: 1_500,
  maxDelayMs: 6_000
});

function createApiError(message, details = {}) {
  const error = new Error(message);
  Object.assign(error, details);
  return error;
}

function normalizeFetchError(error) {
  if (error?.code || typeof error?.status === 'number') {
    return error;
  }
  if (error?.name === 'AbortError') {
    return createApiError(NETWORK_TIMEOUT_MESSAGE, { code: 'NETWORK_TIMEOUT' });
  }
  if (error instanceof TypeError) {
    return createApiError(NETWORK_UNAVAILABLE_MESSAGE, { code: 'NETWORK_UNAVAILABLE' });
  }
  return error;
}

function resolveRetryPolicy(method, retryPolicy) {
  if (retryPolicy === false) {
    return null;
  }
  if (retryPolicy && typeof retryPolicy === 'object') {
    return retryPolicy;
  }

  const normalizedMethod = String(method || 'GET').toUpperCase();
  if (normalizedMethod === 'GET' || normalizedMethod === 'HEAD') {
    return DEFAULT_RETRY_POLICY;
  }
  return null;
}

function shouldRetryResponse(response) {
  return RETRYABLE_RESPONSE_STATUSES.has(response?.status);
}

function shouldRetryError(error) {
  return (
    error?.code === 'NETWORK_TIMEOUT'
    || error?.code === 'NETWORK_UNAVAILABLE'
    || RETRYABLE_RESPONSE_STATUSES.has(error?.status)
  );
}

function parseRetryAfterMs(response) {
  const retryAfterHeader = response?.headers?.get?.('retry-after');
  if (!retryAfterHeader) {
    return null;
  }

  const numericSeconds = Number(retryAfterHeader);
  if (Number.isFinite(numericSeconds) && numericSeconds >= 0) {
    return numericSeconds * 1_000;
  }

  const retryAt = Date.parse(retryAfterHeader);
  if (Number.isNaN(retryAt)) {
    return null;
  }

  return Math.max(0, retryAt - Date.now());
}

function getRetryDelayMs(policy, attemptIndex, response) {
  const retryAfterMs = parseRetryAfterMs(response);
  if (Number.isFinite(retryAfterMs)) {
    return Math.min(policy.maxDelayMs, Math.max(0, retryAfterMs));
  }

  const baseDelay = policy.baseDelayMs * (attemptIndex + 1);
  return Math.min(policy.maxDelayMs, baseDelay);
}

async function apiFetchOnce(url, options = {}) {
  const { timeoutMs = 15000, ...rest } = options;
  if (!timeoutMs || timeoutMs <= 0) {
    return globalThis.fetch(url, rest);
  }

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);
  if (rest.signal) {
    if (rest.signal.aborted) {
      controller.abort();
    } else {
      rest.signal.addEventListener('abort', () => controller.abort(), { once: true });
    }
  }

  try {
    return await globalThis.fetch(url, { ...rest, signal: controller.signal });
  } catch (error) {
    throw normalizeFetchError(error);
  } finally {
    window.clearTimeout(timeoutId);
  }
}

async function apiFetch(url, options = {}) {
  const { retryPolicy, ...rest } = options;
  const policy = resolveRetryPolicy(rest.method, retryPolicy);
  const attempts = Math.max(1, policy?.attempts ?? 1);

  for (let attemptIndex = 0; attemptIndex < attempts; attemptIndex += 1) {
    try {
      const response = await apiFetchOnce(url, rest);
      const hasNextAttempt = attemptIndex + 1 < attempts;
      if (!hasNextAttempt || !shouldRetryResponse(response)) {
        return response;
      }

      await waitMs(getRetryDelayMs(policy, attemptIndex, response));
    } catch (error) {
      const normalizedError = normalizeFetchError(error);
      const hasNextAttempt = attemptIndex + 1 < attempts;
      if (!hasNextAttempt || !shouldRetryError(normalizedError)) {
        throw normalizedError;
      }

      await waitMs(getRetryDelayMs(policy, attemptIndex));
    }
  }

  return apiFetchOnce(url, rest);
}

function isAuthPayload(payload) {
  return Boolean(payload) && typeof payload === 'object' && typeof payload.token === 'string' && payload.token.trim().length > 0;
}

function waitMs(duration) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, duration);
  });
}

function pickErrorMessage(payload) {
  if (!payload) {
    return '';
  }
  if (typeof payload === 'string') {
    return payload.trim();
  }

  const candidates = [
    payload.detail,
    payload.message,
    payload.reason,
    payload.error,
    payload.title
  ];

  for (const value of candidates) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }

  if (Array.isArray(payload.details) && payload.details.length > 0) {
    return payload.details[0];
  }

  return '';
}

async function readErrorMessage(response) {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      const payload = await response.clone().json();
      const fromJson = pickErrorMessage(payload);
      if (fromJson) {
        return fromJson;
      }
    } catch {
      // Fall back to plain text if response body is not valid JSON.
    }
  }

  try {
    const text = await response.text();
    if (text.trim()) {
      return text.trim();
    }
  } catch {
    // Ignore body read errors and fall back to generic status text.
  }
  return `Ошибка запроса (${response.status})`;
}

async function handleResponse(response) {
  if (!response.ok) {
    const error = new Error(await readErrorMessage(response));
    error.status = response.status;
    throw error;
  }
  if (response.status === 204) {
    return null;
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function login(username, password) {
  try {
    const response = await apiFetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      timeoutMs: LOGIN_TIMEOUT_MS,
      retryPolicy: AUTH_RETRY_POLICY,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const payload = await handleResponse(response);
    if (!isAuthPayload(payload)) {
      throw new Error('Сервис авторизации недоступен. Проверьте адрес API и повторите вход.');
    }
    return payload;
  } catch (error) {
    if (shouldRetryError(error)) {
      throw new Error(WARMING_UP_MESSAGE);
    }
    throw error;
  }
}

export async function demoLogin(username, password) {
  try {
    const response = await apiFetch(`${API_BASE}/auth/demo-login`, {
      method: 'POST',
      timeoutMs: LOGIN_TIMEOUT_MS,
      retryPolicy: AUTH_RETRY_POLICY,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    return await handleResponse(response);
  } catch (error) {
    if (shouldRetryError(error)) {
      throw new Error(WARMING_UP_MESSAGE);
    }
    throw error;
  }
}

function normalizeProductsPage(payload, fallbackPage = 0, fallbackSize = 24) {
  if (payload && Array.isArray(payload.items)) {
    return {
      items: payload.items,
      page: Number.isInteger(payload.page) ? payload.page : fallbackPage,
      size: Number.isInteger(payload.size) ? payload.size : fallbackSize,
      totalItems: Number.isFinite(payload.totalItems) ? payload.totalItems : payload.items.length,
      totalPages: Number.isInteger(payload.totalPages) ? payload.totalPages : 1,
      hasNext: Boolean(payload.hasNext)
    };
  }

  const items = Array.isArray(payload) ? payload : [];
  return {
    items,
    page: fallbackPage,
    size: fallbackSize,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false
  };
}

export async function getProductsPage(token, params = {}) {
  const search = new URLSearchParams();
  if (params.category) {
    search.set('category', params.category);
  }
  if (params.q) {
    search.set('q', params.q);
  }
  if (params.page != null) {
    search.set('page', String(params.page));
  }
  if (params.size != null) {
    search.set('size', String(params.size));
  }
  const query = search.toString();
  const url = query ? `${API_BASE}/products?${query}` : `${API_BASE}/products`;
  const response = await apiFetch(url, {
    headers: { ...authHeaders(token) }
  });
  const payload = await handleResponse(response);
  return normalizeProductsPage(payload, params.page ?? 0, params.size ?? 24);
}

export async function getProducts(token, params = {}) {
  const page = await getProductsPage(token, params);
  return page.items;
}

export async function getProductCategories(token, options = {}) {
  const { headers: extraHeaders, ...restOptions } = options || {};
  const response = await apiFetch(`${API_BASE}/products/categories`, {
    ...restOptions,
    headers: { ...authHeaders(token), ...(extraHeaders || {}) }
  });
  return handleResponse(response);
}

export async function createProduct(token, payload) {
  const response = await apiFetch(`${API_BASE}/products`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function updateProduct(token, id, payload) {
  const response = await apiFetch(`${API_BASE}/products/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function deleteProduct(token, id) {
  const response = await apiFetch(`${API_BASE}/products/${id}`, {
    method: 'DELETE',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDirectorProfile(token) {
  const response = await apiFetch(`${API_BASE}/director/profile`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function updateDirectorProfile(token, payload) {
  const response = await apiFetch(`${API_BASE}/director/profile`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function getDirectorAddresses(token) {
  const response = await apiFetch(`${API_BASE}/director/addresses`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function createDirectorAddress(token, payload) {
  const response = await apiFetch(`${API_BASE}/director/addresses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function updateDirectorAddress(token, id, payload) {
  const response = await apiFetch(`${API_BASE}/director/addresses/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function deleteDirectorAddress(token, id) {
  const response = await apiFetch(`${API_BASE}/director/addresses/${id}`, {
    method: 'DELETE',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function lookupGeo(token, query, limit = 5) {
  const search = new URLSearchParams({ q: query, limit: String(limit) });
  const response = await apiFetch(`${API_BASE}/geo/lookup?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function reverseGeo(token, latitude, longitude) {
  const search = new URLSearchParams({ lat: String(latitude), lon: String(longitude) });
  const response = await apiFetch(`${API_BASE}/geo/reverse?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function createOrder(token, payload) {
  const response = await apiFetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function repeatOrder(token, orderId) {
  const response = await apiFetch(`${API_BASE}/orders/${orderId}/repeat`, {
    method: 'POST',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getMyOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/my`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getMyOrdersPage(token, params = {}) {
  const search = new URLSearchParams();
  if (params.page != null) {
    search.set('page', String(params.page));
  }
  if (params.size != null) {
    search.set('size', String(params.size));
  }
  const query = search.toString();
  const url = query ? `${API_BASE}/orders/my/page?${query}` : `${API_BASE}/orders/my/page`;
  const response = await apiFetch(url, {
    headers: { ...authHeaders(token) }
  });
  const payload = await handleResponse(response);
  return normalizeOrdersPage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAssignedOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/assigned`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getAssignedOrdersPage(token, params = {}) {
  const search = new URLSearchParams();
  if (params.page != null) {
    search.set('page', String(params.page));
  }
  if (params.size != null) {
    search.set('size', String(params.size));
  }
  const query = search.toString();
  const url = query ? `${API_BASE}/orders/assigned/page?${query}` : `${API_BASE}/orders/assigned/page`;
  const response = await apiFetch(url, {
    headers: { ...authHeaders(token) }
  });
  const payload = await handleResponse(response);
  return normalizeOrdersPage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAllOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getAllOrdersPage(token, params = {}) {
  const search = new URLSearchParams();
  if (params.page != null) {
    search.set('page', String(params.page));
  }
  if (params.size != null) {
    search.set('size', String(params.size));
  }
  const query = search.toString();
  const url = query ? `${API_BASE}/orders/page?${query}` : `${API_BASE}/orders/page`;
  const response = await apiFetch(url, {
    headers: { ...authHeaders(token) }
  });
  const payload = await handleResponse(response);
  return normalizeOrdersPage(payload, params.page ?? 0, params.size ?? 50);
}

export async function approveOrder(token, id) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/approve`, {
    method: 'POST',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function approveAllOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/approve-all`, {
    method: 'POST',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function assignOrderDriver(token, id, driverId) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/assign-driver`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ driverId })
  });
  return handleResponse(response);
}

export async function autoAssignOrders(token) {
  return previewAutoAssignOrders(token);
}

function normalizeOrdersPage(payload, fallbackPage = 0, fallbackSize = 50) {
  if (payload && Array.isArray(payload.items)) {
    return {
      items: payload.items,
      page: Number.isInteger(payload.page) ? payload.page : fallbackPage,
      size: Number.isInteger(payload.size) ? payload.size : fallbackSize,
      totalItems: Number.isFinite(payload.totalItems) ? payload.totalItems : payload.items.length,
      totalPages: Number.isInteger(payload.totalPages) ? payload.totalPages : 1,
      hasNext: Boolean(payload.hasNext)
    };
  }

  const items = Array.isArray(payload) ? payload : [];
  return {
    items,
    page: fallbackPage,
    size: fallbackSize,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false
  };
}

export async function previewAutoAssignOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/preview`, {
    method: 'POST',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function approveAutoAssignOrders(token, assignments) {
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ assignments })
  });
  return handleResponse(response);
}

export async function markOrderDelivered(token, id) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/deliver`, {
    method: 'POST',
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getOrderTimeline(token, orderId) {
  const response = await apiFetch(`${API_BASE}/orders/${orderId}/timeline`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function createDirectorUser(token, payload) {
  const response = await apiFetch(`${API_BASE}/users/directors`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload)
  });
  return handleResponse(response);
}

export async function getDirectors(token) {
  const response = await apiFetch(`${API_BASE}/users/directors`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDrivers(token) {
  const response = await apiFetch(`${API_BASE}/users/drivers`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function downloadOrdersReport(token, params) {
  const search = new URLSearchParams();
  if (params?.from) search.set('from', params.from);
  if (params?.to) search.set('to', params.to);
  if (params?.status) search.set('status', params.status);

  const response = await apiFetch(`${API_BASE}/reports/orders?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.blob();
}

export async function getAuditLogs(token) {
  const response = await apiFetch(`${API_BASE}/audit/logs`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDashboardSummary(token, params) {
  const search = new URLSearchParams();
  if (params?.from) search.set('from', params.from);
  if (params?.to) search.set('to', params.to);

  const response = await apiFetch(`${API_BASE}/dashboard/summary?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDashboardTrends(token, params) {
  const search = new URLSearchParams();
  if (params?.from) search.set('from', params.from);
  if (params?.to) search.set('to', params.to);

  const response = await apiFetch(`${API_BASE}/dashboard/trends?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDashboardCategories(token, params) {
  const search = new URLSearchParams();
  if (params?.from) search.set('from', params.from);
  if (params?.to) search.set('to', params.to);

  const response = await apiFetch(`${API_BASE}/dashboard/categories?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getStockMovements(token, params) {
  const search = new URLSearchParams();
  if (params?.productId) search.set('productId', String(params.productId));
  if (params?.from) search.set('from', params.from);
  if (params?.to) search.set('to', params.to);
  if (params?.limit) search.set('limit', String(params.limit));

  const response = await apiFetch(`${API_BASE}/stock-movements?${search.toString()}`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export function subscribeNotifications(token, { onNotification, onError } = {}) {
  const controller = new AbortController();
  let closed = false;
  let reconnectAttempts = 0;
  let reconnectTimerId = null;

  const clearReconnectTimer = () => {
    if (reconnectTimerId !== null) {
      window.clearTimeout(reconnectTimerId);
      reconnectTimerId = null;
    }
  };

  const scheduleReconnect = () => {
    if (closed || controller.signal.aborted) {
      return;
    }
    clearReconnectTimer();
    const delayMs = Math.min(10_000, 1_500 * (reconnectAttempts + 1));
    reconnectAttempts += 1;
    reconnectTimerId = window.setTimeout(() => {
      reconnectTimerId = null;
      if (!closed && !controller.signal.aborted) {
        connect();
      }
    }, delayMs);
  };

  const connect = async () => {
    try {
      const response = await apiFetch(`${API_BASE}/notifications/stream`, {
        headers: {
          Accept: 'text/event-stream',
          ...authHeaders(token)
        },
        signal: controller.signal,
        timeoutMs: 0
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response));
      }
      if (!response.body) {
        throw new Error('Поток уведомлений не поддерживается в этом браузере');
      }

      reconnectAttempts = 0;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let eventName = 'message';
      let dataLines = [];

      const flushEvent = () => {
        const currentEventName = eventName;
        const currentData = dataLines.join('\n');
        eventName = 'message';
        dataLines = [];

        if (currentEventName !== 'notification' || !currentData) {
          return;
        }

        try {
          const payload = JSON.parse(currentData);
          if (onNotification) {
            onNotification(payload);
          }
        } catch {
          // Ignore malformed payload and keep stream alive.
        }
      };

      while (!closed) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop();

        for (const line of lines) {
          if (line === '') {
            flushEvent();
            continue;
          }
          if (line.startsWith(':')) {
            continue;
          }
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim() || 'message';
            continue;
          }
          if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trimStart());
          }
        }
      }

      if (!closed && !controller.signal.aborted) {
        scheduleReconnect();
      }
    } catch (error) {
      if (controller.signal.aborted || closed) {
        return;
      }
      if (onError) {
        onError(error);
      }
      scheduleReconnect();
    }
  };

  connect();

  return () => {
    closed = true;
    clearReconnectTimer();
    controller.abort();
  };
}
