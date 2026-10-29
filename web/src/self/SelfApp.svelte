<script>
  // The player area ("/me"): every player sees and manages only their own things. Separate from the team's panel -
  // own sign-in (only through /web in game), own navigation. The areas come from the installed plugins.
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from '../lib/api.js';
  import { route, go } from '../lib/router.svelte.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { head } from '../lib/format.js';
  import SelfLogin from './SelfLogin.svelte';
  import SelfHome from './SelfHome.svelte';
  import SelfDevices from './SelfDevices.svelte';
  import SelfArea from './SelfArea.svelte';
  import SelfTickets from './SelfTickets.svelte';
  import SelfTicket from './SelfTicket.svelte';
  import SelfStats from './SelfStats.svelte';
  import LangFlags from './LangFlags.svelte';
  import SelfNews from './SelfNews.svelte';

  // null = not signed in, undefined = still asking, otherwise {uuid, name, areas}
  let player = $state(undefined);
  let error = $state('');
  let menuOpen = $state(false);

  async function load() {
    try {
      player = await api('GET', '/self?lang=' + lang);
      error = '';
    } catch (e) {
      player = null;
      // Signed out is the normal case - other errors (switched off, banned, no permission) are shown.
      error = e.status === 401 ? '' : errorText(e);
    }
  }

  $effect(() => {
    // Old links /me/login#<code> (before the 6-digit codes) lead to the page itself.
    if (route.path === '/me/login') untrack(() => go('/me', true));
    else if (player === undefined) untrack(load);
  });

  // A session that ran out (session-days, idle-days) or was signed out elsewhere: back to the code.
  whenSignedOut(() => {
    player = null;
  });

  async function logout() {
    try {
      await api('POST', '/self/logout');
    } finally {
      player = null;
      go('/me', true);
    }
  }

  const ticketId = $derived(route.path.match(/^\/me\/tickets\/(\d+)$/)?.[1] ?? null);

  // /me/<plugin>/<area>, e.g. /me/mrchunks/loaders
  const areaMatch = $derived(route.path.match(/^\/me\/([a-z0-9_-]+)\/([a-z0-9_-]+)$/));

  const nav = $derived([
    { path: '/me', label: t('self.nav.home') },
    ...(player?.areas ?? []).map((a) => ({ path: `/me/${a.module}/${a.id}`, label: a.title })),
    ...(player?.news ? [{ path: '/me/news', label: t('self.nav.news') }] : []),
    ...(player?.tickets ? [{ path: '/me/tickets', label: t('self.nav.tickets') }] : []),
    ...(player?.stats ? [{ path: '/me/stats', label: t('self.nav.stats') }] : []),
  ]);
</script>

<!-- The account menu closes on a click elsewhere and with Escape. -->
<svelte:window onclick={(e) => menuOpen && !e.target.closest('.account') && (menuOpen = false)}
  onkeydown={(e) => e.key === 'Escape' && (menuOpen = false)} />

{#if player === undefined}
  <div class="center muted">{t('common.loading')}</div>
{:else if player === null}
  <SelfLogin {error} onSignedIn={() => { error = ''; player = undefined; }} />
{:else}
  <header class="top">
    <nav>
      {#each nav as item}
        <button class="link" class:active={route.path === item.path || (item.path !== '/me' && route.path.startsWith(item.path + '/'))} onclick={() => go(item.path)}>{item.label}</button>
      {/each}
    </nav>
    <div class="right">
      <LangFlags />
      <!-- The player: a click opens the devices and signing out. -->
      <div class="account">
        <button class="who" aria-haspopup="menu" aria-expanded={menuOpen} onclick={() => (menuOpen = !menuOpen)}>
          <img class="head" src={head(player.uuid)} alt="" width="26" height="26" />
          <strong>{player.name}</strong>
          <span class="caret" aria-hidden="true">▾</span>
        </button>
        {#if menuOpen}
          <div class="menu" role="menu">
            <button role="menuitem" class:active={route.path === '/me/devices'} onclick={() => { menuOpen = false; go('/me/devices'); }}>
              <span aria-hidden="true">📱</span> {t('self.nav.devices')}
            </button>
            <button role="menuitem" class="out" onclick={() => { menuOpen = false; logout(); }}>
              <span aria-hidden="true">↩</span> {t('nav.logout')}
            </button>
          </div>
        {/if}
      </div>
    </div>
  </header>
  <main>
    {#if route.path === '/me/devices'}
      <SelfDevices />
    {:else if route.path === '/me/news' && player.news}
      <SelfNews />
    {:else if route.path === '/me/stats' && player.stats}
      <SelfStats />
    {:else if route.path === '/me/tickets' && player.tickets}
      <SelfTickets />
    {:else if ticketId && player.tickets}
      {#key ticketId}<SelfTicket id={ticketId} />{/key}
    {:else if areaMatch}
      {#key route.path}<SelfArea module={areaMatch[1]} id={areaMatch[2]} onChanged={load} />{/key}
    {:else}
      <SelfHome {player} />
    {/if}
  </main>
{/if}

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 16px;
  }

  .top {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 16px;
    padding: 10px 16px;
    background: color-mix(in srgb, var(--surface) 85%, transparent);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border);
  }

  .who {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 10px 4px 4px;
    border-radius: 10px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-weight: 400;
  }

  .who:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .caret {
    color: var(--muted);
    font-size: 0.75rem;
  }

  .account {
    position: relative;
  }

  .menu {
    position: absolute;
    right: 0;
    top: calc(100% + 6px);
    z-index: 30;
    min-width: 190px;
    display: grid;
    gap: 2px;
    padding: 6px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow);
  }

  .menu button {
    display: flex;
    align-items: center;
    gap: 10px;
    justify-content: flex-start;
    padding: 9px 12px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    text-align: left;
  }

  .menu button:hover,
  .menu button.active {
    filter: none;
    background: var(--surface-2);
  }

  .menu .out {
    color: var(--bad);
  }

  .head {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  nav {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  .link {
    background: transparent;
    color: var(--muted);
    padding: 6px 10px;
  }

  .link.active {
    color: var(--text);
    background: var(--surface-2);
  }

  .right {
    align-items: center;
    margin-left: auto;
    display: flex;
    gap: 8px;
  }

  main {
    max-width: 1100px;
    margin: 0 auto;
    padding: 16px;
  }

</style>
