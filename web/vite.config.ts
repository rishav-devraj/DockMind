import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The API sends no CORS headers, so the page calls /api and Vite forwards it.
// Set API_TARGET to test another server, such as http://localhost:8080.
const target = process.env.API_TARGET || 'https://dockmind-bsxe.onrender.com'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
