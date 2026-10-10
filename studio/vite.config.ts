import { defineConfig, type Plugin } from 'vite';
import { createApi } from './src/server/api.ts';

function studioApi(): Plugin {
  return {
    name: 'studio-api',
    configureServer(server) {
      server.middlewares.use(createApi());
    },
    configurePreviewServer(server) {
      server.middlewares.use(createApi());
    },
  };
}

export default defineConfig({
  plugins: [studioApi()],
  server: { host: '127.0.0.1', port: 5174, open: false, watch: { ignored: ['**/structures/**', '**/renders/**', '**/minecraft/**', '**/.cache/**'] } },
  build: {
    rollupOptions: { input: { main: 'index.html', render: 'render.html' } },
  },
});
