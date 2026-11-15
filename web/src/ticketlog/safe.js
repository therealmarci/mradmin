// Values from a log are data, never code: a log file can come from anyone (the viewer on the MRtechnology website opens
// files people send). Svelte escapes all text; these keep the few values that end up in attributes harmless.

/** A picture from the log only if it is a PNG data address, otherwise nothing. */
export function safeImage(value) {
  return typeof value === 'string' && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(value) ? value : null;
}

/** A colour from the log only as #rrggbb, otherwise the fallback. */
export function safeColor(value, fallback = 'var(--muted)') {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value) ? value : fallback;
}

/** A tone name only from the known ones. */
export function safeTone(value) {
  return ['good', 'warn', 'bad'].includes(value) ? value : 'none';
}

/** A finite number or the fallback. */
export function num(value, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
