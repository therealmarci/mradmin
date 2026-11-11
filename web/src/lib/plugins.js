// Shared by the page Plugins and the page of one plugin: state of a plugin from GET /api/versions.
import { t } from './i18n.js';
import { MODULE_ICONS } from './icons.js';

export const PLUGIN_ICONS = { mradmin: 'shield', mrtoolbar: 'hotbar', mrwaitroom: 'restore', ...MODULE_ICONS };

export const pluginIcon = (name) => PLUGIN_ICONS[name.toLowerCase()] ?? 'box';

/**
 * A version in parts, like util/Versions.java: "STA-1.4.0-bu002" -> {channel: 'stable', number: '1.4.0', build: 2},
 * old "1.3.1" -> {channel: 'stable', number: '1.3.1', build: null}, "0.9.14" / "1.0.0-rc.1" -> beta.
 */
export function versionParts(v) {
  if (!v) return null;
  const s = String(v).replace(/\+.*$/, '');
  const m = /^(STA|BET)-(\d+(?:\.\d+){0,2})-bu(\d+)$/i.exec(s);
  if (m) return { channel: m[1].toUpperCase() === 'STA' ? 'stable' : 'beta', number: m[2], build: Number(m[3]) };
  const plain = s.replace(/^v/i, '');
  return { channel: plain.startsWith('0.') || plain.includes('-') ? 'beta' : 'stable', number: plain, build: null };
}

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
  // On the waiting room server (it reports itself; updates and configuration are orders it picks up).
  if (p.remote) {
    if (!p.remote.online) return { tone: 'bad', text: t('plugins.remote.offline') };
    if (p.remote.ordered) return { tone: 'info', text: t('plugins.remote.ordered', { version: p.remote.ordered }) };
    if (p.staged) return { tone: 'info', text: t('plugins.remote.staged', { version: p.staged }) };
  }
  if (!p.installed) {
    if (p.notLoaded) return { tone: 'bad', text: t('plugins.state.notLoaded') };
    if (p.pending) return { tone: 'info', text: t('plugins.state.pending', { version: p.pending }) };
    if (p.error && p.error !== 'no-release') return { tone: 'bad', text: t('versions.state.error') };
    if (!p.latest) return { tone: '', text: t('plugins.state.unreleased') };
    return { tone: '', text: t('plugins.state.available') };
  }
  if (p.staged) return { tone: 'info', text: t('versions.state.staged', { version: p.staged }) };
  if (p.blocked) return { tone: 'warn', text: t('plugins.state.blocked') };
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
