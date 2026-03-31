import { API_BASE, apiFetch, authHeaders, handleResponse } from './core.js';

export async function createDirectorUser(token, payload) {
  const response = await apiFetch(`${API_BASE}/users/directors`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function getDirectors(token) {
  const response = await apiFetch(`${API_BASE}/users/directors`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getDrivers(token) {
  const response = await apiFetch(`${API_BASE}/users/drivers`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}
