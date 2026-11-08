// The look of the server (since 1.5): name, tagline, accent colour, footer and pictures from GET /api/branding.
// Loaded once at the start; the page Appearance calls load() again after saving. The server already writes name,
// icon and colour into index.html, so nothing flashes - this keeps the page in step without reloading.
import { api } from './api.js';

export const brand = $state({ name: document.title, ownName: '', tagline: '', accent: '', accentText: '#ffffff', footer: '',
  links: [], logo: 0, icon: 0 });

/** Address of a picture (logo / icon) with its save time, so a new upload is fetched at once. */
export function picture(kind) {
  const stamp = kind === 'icon' ? brand.icon || brand.logo : brand.logo;
  return stamp ? `/branding/${kind}?v=${stamp}` : '/icon.svg';
}

/** Applies colour, title and browser icon to the page. */
export function apply(values = brand) {
  const root = document.documentElement.style;
  if (values.accent) {
    root.setProperty('--accent', values.accent);
    root.setProperty('--accent-text', values.accentText);
  }
  if (values.name) document.title = values.name;
  const icon = document.querySelector('link[rel="icon"]');
  if (icon) {
    icon.removeAttribute('type');
    icon.href = picture('icon');
  }
}

export async function load() {
  try {
    Object.assign(brand, await api('GET', '/branding'));
    apply();
  } catch {
    // keeps what index.html brought
  }
}
