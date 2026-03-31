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
    return normalizeApiBase(configuredApiBase);
  }
  return buildDefaultApiBase();
};

export const API_BASE = resolveApiBase();
export const AUTO_ASSIGN_TIMEOUT_MS = 90_000;
export const ROUTE_GEOMETRY_TIMEOUT_MS = 45_000;
export const NETWORK_TIMEOUT_MESSAGE = 'Истекло время ожидания ответа от сервера.';
export const NETWORK_UNAVAILABLE_MESSAGE = 'Сервер недоступен. Убедитесь, что backend запущен.';

const RETRYABLE_RESPONSE_STATUSES = new Set([408, 425, 429, 502, 503, 504]);

export const DEFAULT_RETRY_POLICY = Object.freeze({
  attempts: 2,
  baseDelayMs: 1_200,
  maxDelayMs: 4_000,
});

function createApiError(message, details = {}) {
  const error = new Error(message);
  Object.assign(error, details);
  return error;
}

export function normalizeFetchError(error) {
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

export function resolveBackendOrigin() {
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

function shouldRetryResponse(response) {
  return RETRYABLE_RESPONSE_STATUSES.has(response?.status);
}

export function shouldRetryError(error) {
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

export async function apiFetchOnce(url, options = {}) {
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

export async function apiFetch(url, options = {}) {
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

export function waitMs(duration) {
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
    payload.title,
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

export async function readErrorMessage(response) {
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

export async function handleResponse(response) {
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

export function authHeaders(token) {
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

export function buildApiUrl(path, params) {
  const query = buildQuery(params);
  const url = `${API_BASE}${path}`;
  return query ? `${url}?${query}` : url;
}

export async function requestJson(path, { token, params, headers, ...options } = {}) {
  const response = await apiFetch(buildApiUrl(path, params), {
    ...options,
    headers: { ...authHeaders(token), ...(headers || {}) },
  });
  return handleResponse(response);
}

export async function requestBlob(path, { token, params, headers, ...options } = {}) {
  const response = await apiFetch(buildApiUrl(path, params), {
    ...options,
    headers: { ...authHeaders(token), ...(headers || {}) },
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.blob();
}

export function normalizePage(payload, fallbackPage = 0, fallbackSize = 50) {
  if (payload && Array.isArray(payload.items)) {
    return {
      items: payload.items,
      page: Number.isInteger(payload.page) ? payload.page : fallbackPage,
      size: Number.isInteger(payload.size) ? payload.size : fallbackSize,
      totalItems: Number.isFinite(payload.totalItems) ? payload.totalItems : payload.items.length,
      totalPages: Number.isInteger(payload.totalPages) ? payload.totalPages : 1,
      hasNext: Boolean(payload.hasNext),
    };
  }

  const items = Array.isArray(payload) ? payload : [];
  return {
    items,
    page: fallbackPage,
    size: fallbackSize,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
  };
}
