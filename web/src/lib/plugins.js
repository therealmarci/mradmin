// Shared by the page Plugins and the page of one plugin: state of a plugin from GET /api/versions.
import { t } from './i18n.js';
import { MODULE_ICONS } from './icons.js';

export const PLUGIN_ICONS = { mradmin: 'shield', mrtoolbar: 'hotbar', ...MODULE_ICONS };

export const pluginIcon = (name) => PLUGIN_ICONS[name.toLowerCase()] ?? 'box';

/** Channels of a plugin in the order of the switch (VersionManager.Channel). */
export const CHANNELS = ['stable', 'beta', 'dev'];

/** Short description of a known plugin (empty for unknown ones). */
export function pluginDescription(name) {
  const key = 'plugins.desc.' + name.toLowerCase();
  const text = t(key);
  return text === key ? '' : text;
}

/**
 * State of a plugin: tone (good / warn / info / bad / '' = neutral) and text.
 * Order of importance: busy, waiting for the restart, update, error, not installed, current.
 */
export function pluginState(p) {
  if (p.downloading) return { tone: 'info', text: t('versions.state.downloading') };
  if (!p.installed) {
    if (p.notLoaded) return { tone: 'bad', text: t('plugins.state.notLoaded') };
    if (p.pending) return { tone: 'info', text: t('plugins.state.pending', { version: p.pending }) };
    if (p.error && p.error !== 'no-release') return { tone: 'bad', text: t('versions.state.error') };
    if (!p.latest) return { tone: '', text: t('plugins.state.unreleased') };
    return { tone: '', text: t('plugins.state.available') };
  }
  if (p.staged) return { tone: 'info', text: t('versions.state.staged', { version: p.staged }) };
  if (p.update) return { tone: 'warn', text: t('versions.state.update') };
  if (p.channel === 'dev' && !p.dev) return { tone: '', text: t('plugins.state.noDev') };
  if (p.error && p.error !== 'dev-none') return { tone: 'bad', text: t('versions.state.error') };
  if (p.channel !== 'dev' && !p.latest) return { tone: '', text: t('versions.state.unknown') };
  return { tone: 'good', text: t('versions.state.current') };
}

/** "4:09" or "1:05:09" */
export function clock(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

/** The error text of a plugin, or '' (no release / no dev build for this Minecraft version is no error). */
export const pluginError = (p) => (!p.error || p.error === 'no-release' || p.error === 'dev-none' ? '' : p.error);
