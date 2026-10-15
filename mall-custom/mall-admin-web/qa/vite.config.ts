import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
export default defineConfig({ plugins: [vue()], resolve: { alias: { '@': resolve(__dirname, '../src') } }, server: { host: '127.0.0.1', port: 18081, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:18080', rewrite: p => p.replace(/^\/api/, ''), headers: { 'X-Test-Admin': 'yes' } } } } })
