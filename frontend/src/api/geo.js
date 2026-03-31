import { requestJson } from './core.js';

export async function lookupGeo(token, query, limit = 5) {
  return requestJson('/geo/lookup', {
    token,
    params: { q: query, limit },
  });
}

export async function reverseGeo(token, latitude, longitude) {
  return requestJson('/geo/reverse', {
    token,
    params: { lat: latitude, lon: longitude },
  });
}
