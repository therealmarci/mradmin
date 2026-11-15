import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { fileURLToPath } from 'node:url';

// The ticket log viewer for the MRtechnology website: the same component as in MRadmin (src/ticketlog/) as one
// ES module + stylesheet in build/viewer-dist. lib/i18n.js is swapped for a small stand-in (only the chart's texts).
const shim = fileURLToPath(new URL('./src/ticketlog/viewer/i18n.js', import.meta.url));
export default defineConfig({
  plugins: [
    svelte(),
    {
      name: 'viewer-i18n',
      enforce: 'pre',
      resolveId(source) {
        return /(^|\/)lib\/i18n\.js$/.test(source) ? shim : null;
      },
    },
  ],
  build: {
    outDir: '../build/viewer-dist',
    emptyOutDir: true,
    sourcemap: false,
    lib: {
      entry: fileURLToPath(new URL('./src/ticketlog/viewer/main.js', import.meta.url)),
      formats: ['es'],
      fileName: () => 'ticket-log-viewer.js',
      cssFileName: 'ticket-log-viewer',
    },
  },
});
