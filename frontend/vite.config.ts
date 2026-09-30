import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Mirror Caddy's same-origin routing: API calls go to Spring Boot.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
