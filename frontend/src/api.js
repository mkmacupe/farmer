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
const LOGIN_TIMEOUT_MS = 20_000;
const READINESS_TIMEOUT_MS = 12_000;
const READINESS_RETRY_DELAY_MS = 5_000;
const BACKGROUND_WARMUP_MAX_WAIT_MS = 4 * 60_000;
const AUTH_WARMUP_MAX_WAIT_MS = 6 * 60_000;
const AUTO_ASSIGN_TIMEOUT_MS = 90_000;
const ROUTE_GEOMETRY_TIMEOUT_MS = 45_000;
const NETWORK_TIMEOUT_MESSAGE = 'Истекло время ожидания ответа от сервера.';
const NETWORK_UNAVAILABLE_MESSAGE = 'Сервер недоступен. Убедитесь, что backend запущен.';
const WARMING_UP_TIMEOUT_MESSAGE = 'Сервер не проснулся за 6 минут. Повторите вход чуть позже.';
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
let backendWarmupPromise = null;

function createApiError(message, details = {}) {
  const error = new Error(message);
  Object.assign(error, details);
  return error;
}

function normalizeFetchError(error) {
  const normalizedMessage = String(error?.message || '').toLowerCase();
  if (
    error?.name === 'AbortError'
    || normalizedMessage.includes('signal is aborted')
    || normalizedMessage.includes('aborted without reason')
    || normalizedMessage.includes('operation was aborted')
  ) {
    return createApiError(NETWORK_TIMEOUT_MESSAGE, { code: 'NETWORK_TIMEOUT' });
  }
  if (error?.code || typeof error?.status === 'number') {
    return error;
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

function resolveBackendOrigin() {
  if (/^https?:\/\//i.test(API_BASE)) {
    return normalizeApiBase(API_BASE).replace(/\/api$/i, '');
  }

  const target = import.meta.env.VITE_PROXY_API_TARGET?.trim();
  if (target) {
    return normalizeApiBase(target).replace(/\/api$/i, '');
  }

  const host = import.meta.env.VITE_PROXY_API_HOST?.trim();
  const port = import.meta.env.VITE_PROXY_API_PORT?.trim();
  if (host && port) {
    return `http://${host}:${port}`;
  }

  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }

  return '';
}

function isLocalOrPrivateHost(hostname) {
  const normalized = String(hostname || '').trim().toLowerCase();
  if (!normalized) {
    return false;
  }

  if (
    normalized === 'localhost'
    || normalized === '127.0.0.1'
    || normalized === '::1'
    || normalized.endsWith('.local')
  ) {
    return true;
  }

  if (normalized.startsWith('10.') || normalized.startsWith('192.168.')) {
    return true;
  }

  const privateRange172 = normalized.match(/^172\.(\d{1,2})\./);
  if (privateRange172) {
    const segment = Number(privateRange172[1]);
    if (segment >= 16 && segment <= 31) {
      return true;
    }
  }

  return false;
}

function shouldUseColdStartWarmup() {
  const backendOrigin = resolveBackendOrigin();
  if (!backendOrigin || backendOrigin.startsWith('/')) {
    return false;
  }

  try {
    const { hostname } = new URL(backendOrigin);
    return !isLocalOrPrivateHost(hostname);
  } catch {
    return false;
  }
}

export const LOGIN_LOADING_MESSAGE = shouldUseColdStartWarmup()
  ? 'Подключаем сервер. Если backend был в спящем режиме на Render Free, вход продолжится автоматически и может занять до 5 минут.'
  : 'Выполняем вход...';

async function checkBackendReadiness() {
  const backendOrigin = resolveBackendOrigin();
  if (!backendOrigin) {
    return false;
  }

  try {
    const response = await apiFetchOnce(`${backendOrigin}/actuator/health/readiness`, {
      timeoutMs: READINESS_TIMEOUT_MS
    });
    if (!response.ok) {
      return false;
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      return false;
    }

    const payload = await response.json();
    return payload?.status === 'UP';
  } catch {
    return false;
  }
}

async function waitForBackendReadinessUntil(deadlineAt) {
  while (Date.now() < deadlineAt) {
    if (await checkBackendReadiness()) {
      return true;
    }

    const remainingMs = deadlineAt - Date.now();
    if (remainingMs <= 0) {
      return false;
    }
    await waitMs(Math.min(READINESS_RETRY_DELAY_MS, remainingMs));
  }
  return false;
}

export function primeBackendWarmup() {
  if (!shouldUseColdStartWarmup()) {
    return Promise.resolve(true);
  }
  if (backendWarmupPromise) {
    return backendWarmupPromise;
  }

  const deadlineAt = Date.now() + BACKGROUND_WARMUP_MAX_WAIT_MS;
  backendWarmupPromise = waitForBackendReadinessUntil(deadlineAt).finally(() => {
    backendWarmupPromise = null;
  });

  return backendWarmupPromise;
}

async function waitForBackendWarmup(deadlineAt) {
  if (!shouldUseColdStartWarmup()) {
    return false;
  }
  if (backendWarmupPromise) {
    try {
      if (await backendWarmupPromise) {
        return true;
      }
    } catch {
      // Fallback to a dedicated warmup loop below.
    }
  }

  return waitForBackendReadinessUntil(deadlineAt);
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
  const timeoutId = globalThis.setTimeout(() => controller.abort(), timeoutMs);
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
    globalThis.clearTimeout(timeoutId);
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
    globalThis.setTimeout(resolve, duration);
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

function buildQuery(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === '') {
      continue;
    }
    search.set(key, String(value));
  }
  return search.toString();
}

function buildApiUrl(path, params) {
  const query = buildQuery(params);
  const url = `${API_BASE}${path}`;
  return query ? `${url}?${query}` : url;
}

async function requestJson(path, { token, params, headers, ...options } = {}) {
  const response = await apiFetch(buildApiUrl(path, params), {
    ...options,
    headers: { ...authHeaders(token), ...(headers || {}) }
  });
  return handleResponse(response);
}

async function requestBlob(path, { token, params, headers, ...options } = {}) {
  const response = await apiFetch(buildApiUrl(path, params), {
    ...options,
    headers: { ...authHeaders(token), ...(headers || {}) }
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.blob();
}

function normalizePage(payload, fallbackPage = 0, fallbackSize = 50) {
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

async function authenticateWithWarmup(path, username, password, validatePayload = true) {
  const useColdStartWarmup = shouldUseColdStartWarmup();
  const deadlineAt = Date.now() + AUTH_WARMUP_MAX_WAIT_MS;

  while (true) {
    try {
      const response = await apiFetch(`${API_BASE}/auth/${path}`, {
        method: 'POST',
        timeoutMs: LOGIN_TIMEOUT_MS,
        retryPolicy: AUTH_RETRY_POLICY,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const payload = await handleResponse(response);
      if (validatePayload && !isAuthPayload(payload)) {
        throw new Error('Сервис авторизации недоступен. Проверьте адрес API и повторите вход.');
      }
      return payload;
    } catch (error) {
      if (useColdStartWarmup && shouldRetryError(error)) {
        if (Date.now() >= deadlineAt) {
          throw new Error(WARMING_UP_TIMEOUT_MESSAGE);
        }

        const backendReady = await waitForBackendWarmup(deadlineAt);
        if (!backendReady) {
          throw new Error(WARMING_UP_TIMEOUT_MESSAGE);
        }
        continue;
      }
      throw error;
    }
  }
}

export async function login(username, password) {
  return authenticateWithWarmup('login', username, password, true);
}

export async function demoLogin(username, password) {
  return authenticateWithWarmup('demo-login', username, password, false);
}

export async function getProductsPage(token, params = {}) {
  const payload = await requestJson('/products', {
    token,
    params: {
      category: params.category,
      q: params.q,
      page: params.page,
      size: params.size
    }
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 24);
}

export async function getProducts(token, params = {}) {
  const page = await getProductsPage(token, params);
  return page.items;
}

export async function getProductCategories(token, options = {}) {
  const { headers: extraHeaders, ...restOptions } = options || {};
  return requestJson('/products/categories', {
    token,
    ...restOptions,
    headers: extraHeaders
  });
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
  return requestJson('/geo/lookup', {
    token,
    params: { q: query, limit }
  });
}

export async function reverseGeo(token, latitude, longitude) {
  return requestJson('/geo/reverse', {
    token,
    params: { lat: latitude, lon: longitude }
  });
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
  const payload = await requestJson('/orders/my/page', {
    token,
    params: {
      page: params.page,
      size: params.size
    }
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAssignedOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/assigned`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getAssignedOrdersPage(token, params = {}) {
  const payload = await requestJson('/orders/assigned/page', {
    token,
    params: {
      page: params.page,
      size: params.size
    }
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAllOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getAllOrdersPage(token, params = {}) {
  const payload = await requestJson('/orders/page', {
    token,
    params: {
      page: params.page,
      size: params.size
    }
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
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

export async function previewAutoAssignOrders(token, options = {}) {
  const driverIds = Array.isArray(options?.driverIds)
    ? options.driverIds.filter((driverId) => Number.isFinite(Number(driverId)))
    : [];
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/preview`, {
    method: 'POST',
    timeoutMs: AUTO_ASSIGN_TIMEOUT_MS,
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ driverIds })
  });
  return handleResponse(response);
}

export async function previewAutoAssignRouteGeometry(token, points, options = {}) {
  const { returnsToDepot = false, signal } = options;
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/route-geometry`, {
    method: 'POST',
    timeoutMs: ROUTE_GEOMETRY_TIMEOUT_MS,
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ points, returnsToDepot }),
    signal
  });
  return handleResponse(response);
}

export async function approveAutoAssignOrders(token, assignments) {
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/approve`, {
    method: 'POST',
    timeoutMs: AUTO_ASSIGN_TIMEOUT_MS,
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
  return requestBlob('/reports/orders', {
    token,
    params: {
      from: params?.from,
      to: params?.to,
      status: params?.status
    }
  });
}

export async function getAuditLogs(token) {
  const response = await apiFetch(`${API_BASE}/audit/logs`, {
    headers: { ...authHeaders(token) }
  });
  return handleResponse(response);
}

export async function getDashboardSummary(token, params) {
  return requestJson('/dashboard/summary', {
    token,
    params: {
      from: params?.from,
      to: params?.to
    }
  });
}

export async function getDashboardTrends(token, params) {
  return requestJson('/dashboard/trends', {
    token,
    params: {
      from: params?.from,
      to: params?.to
    }
  });
}

export async function getDashboardCategories(token, params) {
  return requestJson('/dashboard/categories', {
    token,
    params: {
      from: params?.from,
      to: params?.to
    }
  });
}

export async function getStockMovements(token, params) {
  return requestJson('/stock-movements', {
    token,
    params: {
      productId: params?.productId,
      from: params?.from,
      to: params?.to,
      limit: params?.limit
    }
  });
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
