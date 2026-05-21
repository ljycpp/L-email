import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';

const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 8081,
    proxy: {
      '/login': backend,
      '/user': backend,
      '/api': backend,
      '/inbox': backend,
      '/outbox': backend,
      '/draftbox': backend,
      '/mail_detail': backend,
      '/mail_list': backend,
      '/mail_send': backend,
      '/mail_delete': backend,
      '/mail/': backend,
      '/mail_contacts': backend,
      '/mail_group': backend,
      '/mail_label': backend,
      '/spam': backend,
      '/ws': { target: backend, ws: true, changeOrigin: true }
    }
  }
});
