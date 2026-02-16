import { beforeEach, describe, expect, it } from 'vitest';
import { clearAuth, loadAuth, saveAuth } from './authStorage.js';

const AUTH_KEY = 'farm_sales_auth';

const createMemoryStorage = () => {
  let store = new Map();
  return {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => {
      store.set(key, String(value));
    },
    removeItem: (key) => {
      store.delete(key);
    },
    clear: () => {
      store = new Map();
    }
  };
};

const ensureStorage = (name) => {
  const storage = globalThis[name];
  if (storage && typeof storage.getItem === 'function' && typeof storage.setItem === 'function') {
    return storage;
  }
  const memoryStorage = createMemoryStorage();
  Object.defineProperty(globalThis, name, {
    value: memoryStorage,
    configurable: true,
    writable: true
  });
  return memoryStorage;
};

describe('authStorage', () => {
  beforeEach(() => {
    const session = ensureStorage('sessionStorage');
    const local = ensureStorage('localStorage');
    session.removeItem(AUTH_KEY);
    local.removeItem(AUTH_KEY);
  });

  it('loads auth from sessionStorage', () => {
    const payload = { token: 'token-1', username: 'driver' };
    sessionStorage.setItem(AUTH_KEY, JSON.stringify(payload));

    expect(loadAuth()).toEqual(payload);
  });

  it('migrates legacy auth from localStorage', () => {
    const payload = { token: 'token-2', username: 'manager' };
    localStorage.setItem(AUTH_KEY, JSON.stringify(payload));

    expect(loadAuth()).toEqual(payload);
    expect(sessionStorage.getItem(AUTH_KEY)).toBe(JSON.stringify(payload));
    expect(localStorage.getItem(AUTH_KEY)).toBeNull();
  });

  it('returns null for invalid payloads', () => {
    sessionStorage.setItem(AUTH_KEY, '{not-json');
    expect(loadAuth()).toBeNull();
  });

  it('saves and clears auth', () => {
    const payload = { token: 'token-3', username: 'director' };
    saveAuth(payload);

    expect(sessionStorage.getItem(AUTH_KEY)).toBe(JSON.stringify(payload));

    clearAuth();
    expect(sessionStorage.getItem(AUTH_KEY)).toBeNull();
    expect(localStorage.getItem(AUTH_KEY)).toBeNull();
  });
});
