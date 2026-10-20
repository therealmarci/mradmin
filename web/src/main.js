import { mount } from 'svelte';
import './app.css';
import App from './App.svelte';
import { lang } from './lib/i18n.js';

document.documentElement.lang = lang;

// Service worker: push notifications (and installable as an app). It caches nothing.
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}

export default mount(App, { target: document.getElementById('app') });
