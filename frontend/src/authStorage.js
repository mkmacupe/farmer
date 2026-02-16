const AUTH_KEY = 'farm_sales_auth';

function parse(raw) {
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function loadAuth() {
  const sessionRaw = sessionStorage.getItem(AUTH_KEY);
  const sessionAuth = parse(sessionRaw);
  if (sessionAuth) {
    return sessionAuth;
  }

  // Migrate legacy data once from localStorage to sessionStorage.
  const legacyRaw = localStorage.getItem(AUTH_KEY);
  const legacyAuth = parse(legacyRaw);
  if (legacyAuth) {
    sessionStorage.setItem(AUTH_KEY, JSON.stringify(legacyAuth));
    localStorage.removeItem(AUTH_KEY);
    return legacyAuth;
  }

  return null;
}

export function saveAuth(auth) {
  sessionStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export function clearAuth() {
  sessionStorage.removeItem(AUTH_KEY);
  localStorage.removeItem(AUTH_KEY);
}
