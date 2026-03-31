import {
  API_BASE,
  apiFetch,
  authHeaders,
  handleResponse,
  normalizePage,
  requestJson,
} from './core.js';

export async function getProductsPage(token, params = {}) {
  const payload = await requestJson('/products', {
    token,
    params: {
      category: params.category,
      q: params.q,
      page: params.page,
      size: params.size,
    },
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
    headers: extraHeaders,
  });
}

export async function createProduct(token, payload) {
  const response = await apiFetch(`${API_BASE}/products`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function updateProduct(token, id, payload) {
  const response = await apiFetch(`${API_BASE}/products/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(payload),
  });
  return handleResponse(response);
}

export async function deleteProduct(token, id) {
  const response = await apiFetch(`${API_BASE}/products/${id}`, {
    method: 'DELETE',
    headers: { ...authHeaders(token) },
  });
  return handleResponse(response);
}
