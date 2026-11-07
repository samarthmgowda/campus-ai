import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 600,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'vendor-react', test: /node_modules\/(react|react-dom|react-router|react-router-dom)/ },
            { name: 'vendor-query', test: /node_modules\/@tanstack/ },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Auth is what the sign-in form posts to. Without this entry the dev
      // server answers /auth/login itself and returns 404.
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ask': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/documents': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/projects': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/events': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/recruiter': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/mentorship': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/demo': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/analytics': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/skills': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/profile': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/profiles': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/messages': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/bookmarks': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/follow': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/endorsements': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/reports': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/activity': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/community': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/notifications': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/alumni': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/me': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/search': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/students': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/reviews': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/roadmaps': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/referrals': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/eval': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/dashboard': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Backend namespaces with no proxy entry would 404 the same way /auth did.
      '/teammate-requests': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/project-invites': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})