import {
  API_BASE,
  AUTO_ASSIGN_TIMEOUT_MS,
  ROUTE_GEOMETRY_TIMEOUT_MS,
  apiFetch,
  authHeaders,
  handleResponse,
  normalizePage,
  requestJson,
} from './core.js';

export async function createOrder(token, payload) {
  const response = await apiFetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function repeatOrder(token, orderId) {
  const response = await apiFetch(`${API_BASE}/orders/${orderId}/repeat`, {
    method: 'POST',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getMyOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/my`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getMyOrdersPage(token, params = {}) {
  const payload = await requestJson('/orders/my/page', {
    token,
    params: {
      page: params.page,
      size: params.size,
    },
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAssignedOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/assigned`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getAssignedOrdersPage(token, params = {}) {
  const payload = await requestJson('/orders/assigned/page', {
    token,
    params: {
      page: params.page,
      size: params.size,
    },
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
}

export async function getAllOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getAllOrdersPage(token, params = {}) {
  const payload = await requestJson('/orders/page', {
    token,
    params: {
      page: params.page,
      size: params.size,
    },
  });
  return normalizePage(payload, params.page ?? 0, params.size ?? 50);
}

export async function approveOrder(token, id) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/approve`, {
    method: 'POST',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function approveAllOrders(token) {
  const response = await apiFetch(`${API_BASE}/orders/approve-all`, {
    method: 'POST',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function assignOrderDriver(token, id, driverId) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/assign-driver`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ driverId }),
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
    body: JSON.stringify({ driverIds }),
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
    signal,
  });
  return handleResponse(response);
}

export async function approveAutoAssignOrders(token, assignments) {
  const response = await apiFetch(`${API_BASE}/orders/auto-assign/approve`, {
    method: 'POST',
    timeoutMs: AUTO_ASSIGN_TIMEOUT_MS,
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ assignments }),
  });
  return handleResponse(response);
}

export async function markOrderDelivered(token, id) {
  const response = await apiFetch(`${API_BASE}/orders/${id}/deliver`, {
    method: 'POST',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getOrderTimeline(token, orderId) {
  const response = await apiFetch(`${API_BASE}/orders/${orderId}/timeline`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}
