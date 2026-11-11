import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The API sends no CORS headers, so the page calls these paths and Vite forwards them.
// Override with LOCAL_API or HOSTED_API to point either one somewhere else.
const local = process.env.LOCAL_API || 'http://localhost:8080'
const hosted = process.env.HOSTED_API || 'https://dockmind-bsxe.onrender.com'

const forward = (prefix: string, target: string) => ({
  target,
  changeOrigin: true,
  rewrite: (path: string) => path.replace(prefix, ''),
})

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api/local': forward('/api/local', local),
      '/api/hosted': forward('/api/hosted', hosted),
    },
  },
})
