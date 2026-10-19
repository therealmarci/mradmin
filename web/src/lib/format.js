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

/** Player head (also for offline players) - mc-heads.net, allowed by the page's security rules. */
export function head(uuid, size = 64) {
  return `https://mc-heads.net/avatar/${uuid}/${size}`;
}
