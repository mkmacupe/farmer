import { API_BASE, apiFetch, authHeaders, handleResponse } from './core.js';

export async function getDirectorProfile(token) {
  const response = await apiFetch(`${API_BASE}/director/profile`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function updateDirectorProfile(token, payload) {
  const response = await apiFetch(`${API_BASE}/director/profile`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function getDirectorAddresses(token) {
  const response = await apiFetch(`${API_BASE}/director/addresses`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function createDirectorAddress(token, payload) {
  const response = await apiFetch(`${API_BASE}/director/addresses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function updateDirectorAddress(token, id, payload) {
  const response = await apiFetch(`${API_BASE}/director/addresses/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function deleteDirectorAddress(token, id) {
  const response = await apiFetch(`${API_BASE}/director/addresses/${id}`, {
    method: 'DELETE',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}
