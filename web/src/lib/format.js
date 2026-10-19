import { lang } from './i18n.js';

const locale = lang === 'de' ? 'de-AT' : 'en-GB';

export function dateTime(millis) {
  if (!millis) return '-';
  return new Date(millis).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
}

export function ago(millis) {
  const seconds = Math.round((Date.now() - millis) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  if (seconds < 60) return rtf.format(-seconds, 'second');
  if (seconds < 3600) return rtf.format(-Math.round(seconds / 60), 'minute');
  if (seconds < 86400) return rtf.format(-Math.round(seconds / 3600), 'hour');
  return rtf.format(-Math.round(seconds / 86400), 'day');
}

/** Colour class for a TPS value (as in the in-game dashboard). */
export function tpsTone(tps) {
  return tps >= 19.5 ? 'good' : tps >= 15 ? 'warn' : 'bad';
}

/** Player head (also offline players) - made by MRadmin itself, no outside service sees the visitor. */
export function head(uuid) {
  return `/api/head/${uuid}`;
}

/** A duration in ms as "2 d 3 h", "45 min" or "30 s" (the largest two units). */
export function duration(ms) {
  const s = Math.max(0, Math.round(ms / 1000));
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d) return h ? `${d} d ${h} h` : `${d} d`;
  if (h) return m ? `${h} h ${m} min` : `${h} h`;
  if (m) return `${m} min`;
  return `${s} s`;
}
