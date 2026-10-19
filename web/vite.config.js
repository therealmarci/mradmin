import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Gebaut wird nach build/web-dist - Gradle packt das als web/ in die Plugin-JAR.
// "npm run dev" startet einen Entwicklungsserver, der /api an ein laufendes MRadmin weiterreicht.
export default defineConfig({
  plugins: [svelte()],
  build: {
    outDir: '../build/web-dist',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: {
    proxy: { '/api': 'http://127.0.0.1:8125' },
  },
});
