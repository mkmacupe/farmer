import { requestBlob } from './core.js';

export async function downloadOrdersReport(token, params) {
  return requestBlob('/reports/orders', {
    token,
    params: {
      from: params?.from,
      to: params?.to,
      status: params?.status,
    },
  });
}
