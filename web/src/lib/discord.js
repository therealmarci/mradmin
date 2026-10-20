// Discord: sign in, link, confirm. Each leaves the page for discord.com and comes back via /discord.
import { api } from './api.js';
import { route } from './router.svelte.js';

const KEY = 'mradmin.discord.confirm';

/** Goes to Discord. mode: login | link | confirm; afterwards back to returnTo. */
export async function start(mode, returnTo = route.path) {
  const { url } = await api('POST', '/discord/start', { mode, returnTo });
  window.location.href = url;
}

/**
 * Confirms a sensitive step with Discord: remembers which one, goes to Discord and back. The page then calls
 * confirmed(action) - true once, and the step sends {discord: 'true'} instead of a code.
 */
export async function confirm(action) {
  try {
    sessionStorage.setItem(KEY, action);
  } catch {
    // without storage the member clicks again after coming back
  }
  await start('confirm');
}

/** After coming back: was this action just confirmed with Discord? (true only once) */
export function confirmed(action) {
  try {
    if (sessionStorage.getItem(KEY) === action && sessionStorage.getItem(KEY + '.done') === '1') {
      sessionStorage.removeItem(KEY);
      sessionStorage.removeItem(KEY + '.done');
      return true;
    }
  } catch {
    // fine
  }
  return false;
}

/** Called by the /discord page when a confirmation succeeded. */
export function markConfirmed() {
  try {
    sessionStorage.setItem(KEY + '.done', '1');
  } catch {
    // fine
  }
}

export const body = { discord: 'true' };
