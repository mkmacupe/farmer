import { requestBlob } from './core.js';

const REPORT_DOWNLOAD_TIMEOUT_MS = 180_000;

export async function downloadOrdersReport(token, params) {
  return requestBlob('/reports/orders', {
    token,
    timeoutMs: REPORT_DOWNLOAD_TIMEOUT_MS,
    params: {
      from: params?.from,
      to: params?.to,
      status: params?.status,
    },
  });
}
