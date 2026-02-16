import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { visualizer } from 'rollup-plugin-visualizer';

const loopbackHosts = ['127.0.0.1', 'localhost', '::1'];

const ensureNoProxyForLocalhost = () => {
  const hasProxy = process.env.HTTP_PROXY || process.env.HTTPS_PROXY || process.env.http_proxy || process.env.https_proxy;
  if (!hasProxy) {
    return;
  }

  const existing = (process.env.NO_PROXY || process.env.no_proxy || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  const value = Array.from(new Set([...existing, ...loopbackHosts])).join(',');
  process.env.NO_PROXY = value;
  process.env.no_proxy = value;
};

ensureNoProxyForLocalhost();

export default defineConfig(({ mode }) => {
  if (mode === 'test') {
    process.env.NODE_ENV = 'test';
  }

  const analyze = mode === 'analyze';
  const env = loadEnv(mode, process.cwd(), '');
  const apiHost = env.VITE_PROXY_API_HOST || process.env.VITE_PROXY_API_HOST || '127.0.0.1';
  const apiPort = env.VITE_PROXY_API_PORT || process.env.VITE_PROXY_API_PORT || process.env.API_PORT || '8080';
  const apiTarget = env.VITE_PROXY_API_TARGET || process.env.VITE_PROXY_API_TARGET || `http://${apiHost}:${apiPort}`;

  return {
    plugins: [
      react(),
      analyze && visualizer({
        filename: 'dist/bundle-stats.html',
        open: false,
        gzipSize: true,
        brotliSize: true
      })
    ].filter(Boolean),
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      globals: true,
      testTimeout: 15000,
      env: {
        NODE_ENV: 'test'
      },
      exclude: ['e2e/**', 'node_modules/**', 'dist/**']
    }
  };
});
