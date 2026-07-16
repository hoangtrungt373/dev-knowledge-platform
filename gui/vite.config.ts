import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
      '@app': fileURLToPath(new URL('./src/app', import.meta.url)),
      '@auth': fileURLToPath(new URL('./src/features/auth', import.meta.url)),
      '@chat': fileURLToPath(new URL('./src/features/chat', import.meta.url)),
      '@friends': fileURLToPath(new URL('./src/features/friends', import.meta.url)),
      '@messaging': fileURLToPath(new URL('./src/features/messaging', import.meta.url)),
      '@content': fileURLToPath(new URL('./src/features/content', import.meta.url)),
      '@ai': fileURLToPath(new URL('./src/features/ai', import.meta.url)),
    },
  },
  server: {
    port: 3000,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false
      }
    }
  }
});
