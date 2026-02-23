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
    build: {
      target: 'es2022',
      modulePreload: {
        polyfill: false
      },
      rollupOptions: {
        output: {
          manualChunks(id) {
            const n = id.replace(/\\/g, '/');
            if (!n.includes('/node_modules/')) {
              return undefined;
            }
            // React core: react, react-dom, scheduler, react-is (shared dep)
            if (
              n.includes('/react/') ||
              n.includes('/react-dom/') ||
              n.includes('/scheduler/') ||
              n.includes('/react-is/')
            ) {
              return 'react-vendor';
            }
            // MUI icons — large, rarely changes, cache-friendly
            if (n.includes('/@mui/icons-material/')) {
              return 'mui-icons';
            }
            // MUI + Emotion + supporting libs
            if (
              n.includes('/@mui/') ||
              n.includes('/@emotion/') ||
              n.includes('/clsx/') ||
              n.includes('/react-transition-group/') ||
              n.includes('/@floating-ui/') ||
              n.includes('/@popperjs/')
            ) {
              return 'mui-core';
            }
            // Leaflet — only loaded with map component
            if (n.includes('/leaflet/')) {
              return 'leaflet';
            }
            // All other vendor modules stay in default chunk (avoids circular deps)
            return undefined;
          }
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
