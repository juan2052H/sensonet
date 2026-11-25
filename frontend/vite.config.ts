/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// En desarrollo, /api y /ws se envían al backend local (mismo origen: sin CORS).
export default defineConfig({
  // Rutas relativas: el mismo build sirve en nginx (Docker) y en GitHub Pages (/sensonet/).
  base: './',
  plugins: [react(), tailwindcss()],
  // ECharts pesa ~600 kB sin comprimir (~200 kB gzip); es aceptable para un dashboard.
  build: { chunkSizeWarningLimit: 1200 },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
