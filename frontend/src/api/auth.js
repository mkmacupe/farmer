import {
  API_BASE,
  apiFetch,
  apiFetchOnce,
  handleResponse,
  resolveBackendOrigin,
  shouldRetryError,
  waitMs,
} from './core.js';

const LOGIN_TIMEOUT_MS = 20_000;
const READINESS_TIMEOUT_MS = 12_000;
const READINESS_RETRY_DELAY_MS = 5_000;
const BACKGROUND_WARMUP_MAX_WAIT_MS = 4 * 60_000;
const AUTH_WARMUP_MAX_WAIT_MS = 6 * 60_000;
const WARMING_UP_TIMEOUT_MESSAGE = 'Сервер не проснулся за 6 минут. Повторите вход чуть позже.';
const AUTH_RETRY_POLICY = Object.freeze({
  attempts: 4,
  baseDelayMs: 1_500,
  maxDelayMs: 6_000,
});

let backendWarmupPromise = null;

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
      timeoutMs: READINESS_TIMEOUT_MS,
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

function isAuthPayload(payload) {
  return Boolean(payload) && typeof payload === 'object' && typeof payload.token === 'string' && payload.token.trim().length > 0;
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
        body: JSON.stringify({ username, password }),
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
  return authenticateWithWarmup('seed-login', username, password, false);
}
