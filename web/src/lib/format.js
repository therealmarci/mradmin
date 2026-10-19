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
