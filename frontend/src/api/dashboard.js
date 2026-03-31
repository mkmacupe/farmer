import { API_BASE, apiFetch, authHeaders, handleResponse, requestJson } from './core.js';

export async function getAuditLogs(token) {
  const response = await apiFetch(`${API_BASE}/audit/logs`, {
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}

export async function getDashboardSummary(token, params) {
  return requestJson('/dashboard/summary', {
    token,
    params: {
      from: params?.from,
      to: params?.to,
    },
  });
}

export async function getDashboardTrends(token, params) {
  return requestJson('/dashboard/trends', {
    token,
    params: {
      from: params?.from,
      to: params?.to,
    },
  });
}

export async function getDashboardCategories(token, params) {
  return requestJson('/dashboard/categories', {
    token,
    params: {
      from: params?.from,
      to: params?.to,
    },
  });
}

export async function getStockMovements(token, params) {
  return requestJson('/stock-movements', {
    token,
    params: {
      productId: params?.productId,
      from: params?.from,
      to: params?.to,
      limit: params?.limit,
    },
  });
}
