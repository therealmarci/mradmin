import { mount } from 'svelte';
import './app.css';
import App from './App.svelte';
import { lang } from './lib/i18n.js';

document.documentElement.lang = lang;

export default mount(App, { target: document.getElementById('app') });
