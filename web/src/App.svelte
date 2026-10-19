<script>
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from './lib/api.js';
  import { route, go } from './lib/router.svelte.js';
  import { t } from './lib/i18n.js';
  import { connect, disconnect, live } from './lib/live.svelte.js';
  import { head } from './lib/format.js';
  import Login from './pages/Login.svelte';
  import Setup from './pages/Setup.svelte';
  import Home from './pages/Home.svelte';
  import Account from './pages/Account.svelte';
  import Audit from './pages/Audit.svelte';
  import Tickets from './pages/Tickets.svelte';
  import TicketView from './pages/TicketView.svelte';
  import Legal from './pages/Legal.svelte';
  import Footer from './components/Footer.svelte';
  import Health from './pages/Health.svelte';
  import Chunks from './pages/Chunks.svelte';
  import ChunkView from './pages/ChunkView.svelte';
  import Sleep from './pages/Sleep.svelte';
  import Players from './pages/Players.svelte';
  import Console from './pages/Console.svelte';
  import PlayerView from './pages/PlayerView.svelte';

  // null = signed out, undefined = still asking the server
  let me = $state(undefined);

  async function loadMe() {
    try {
      me = await api('GET', '/me');
      connect();
      if (route.path === '/login') go('/', true);
    } catch {
      me = null;
    }
  }

  whenSignedOut(() => {
    me = null;
    disconnect();
  });

  async function logout() {
    try {
      await api('POST', '/logout');
    } finally {
      me = null;
      disconnect();
      go('/login');
    }
  }

  // Ask who is signed in as soon as a page needs it (not on setup, imprint and privacy - they work without).
  $effect(() => {
    if (me === undefined && !['/setup', '/impressum', '/datenschutz'].includes(route.path)) untrack(loadMe);
  });

  // /tickets/12 -> "12"; on a single ticket "Tickets" stays highlighted in the navigation.
  const ticketId = $derived(route.path.match(/^\/tickets\/(\d+)$/)?.[1] ?? null);
  const chunkId = $derived(route.path.match(/^\/chunks\/(\d+)$/)?.[1] ?? null);
  const playerId = $derived(route.path.match(/^\/players\/([0-9a-f-]{36})$/)?.[1] ?? null);
  const section = $derived(ticketId ? '/tickets' : chunkId ? '/chunks' : playerId ? '/players' : route.path);

  const nav = $derived(
    me
      ? [
          { path: '/', label: t('nav.home'), icon: 'home' },
          ...(me.may.tickets ? [{ path: '/tickets', label: t('nav.tickets'), icon: 'ticket' }] : []),
          ...(me.may.players ? [{ path: '/players', label: t('nav.players'), icon: 'users' }] : []),
          ...(me.may.health ? [{ path: '/health', label: t('nav.health'), icon: 'pulse' }] : []),
          ...(me.may.mrchunks ? [{ path: '/chunks', label: t('nav.chunks'), icon: 'grid' }] : []),
          ...(me.may.mrsleep ? [{ path: '/sleep', label: t('nav.sleep'), icon: 'moon' }] : []),
          ...(me.may.console ? [{ path: '/console', label: t('nav.console'), icon: 'terminal' }] : []),
          ...(me.may.audit ? [{ path: '/audit', label: t('nav.audit'), icon: 'list' }] : []),
        ]
      : [],
  );

  // Phone: at most five places in the bottom bar - with more, four plus "More" for the rest.
  const MAX_TABS = 5;
  const tabs = $derived(nav.length > MAX_TABS ? nav.slice(0, MAX_TABS - 1) : nav);
  const extra = $derived(nav.length > MAX_TABS ? nav.slice(MAX_TABS - 1) : []);
  let moreOpen = $state(false);

  const icons = {
    more: 'M5 12h.01M12 12h.01M19 12h.01',
    terminal: 'M4 5h16v14H4zM7 9l3 3-3 3M12 15h5',
    home: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
    ticket: 'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z',
    user: 'M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5Z',
    list: 'M4 6h16M4 12h16M4 18h10',
    pulse: 'M3 12h4l2-6 4 12 2-6h6',
    grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z',
    users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c0-3.3 3.1-6 7-6s7 2.7 7 6M16 3.5a4 4 0 0 1 0 7M18 14c2.4.6 4 2.6 4 5',
    logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4',
  };
</script>

{#if route.path === '/impressum' || route.path === '/datenschutz'}
  <Legal page={route.path === '/impressum' ? 'imprint' : 'privacy'} />
  <Footer />
{:else if route.path === '/setup'}
  <Setup onDone={() => { loadMe(); go('/', true); }} />
{:else if me === undefined}
  <div class="center muted">{t('common.loading')}</div>
{:else if me === null}
  <Login onSignedIn={loadMe} />
{:else}
  <header class="top">
    <div class="brand">
      <img src="/icon.svg" alt="" width="28" height="28" />
      <span>MRadmin</span>
      <span class="dot" class:on={live.connected} title={live.connected ? t('common.live') : t('common.offline')}></span>
    </div>
    <nav class="desktop">
      {#each nav as item}
        <button class:active={section === item.path} class="link" onclick={() => go(item.path)}>{item.label}</button>
      {/each}
    </nav>
    <div class="user">
      <!-- The account: a click on your own head or name. -->
      <button class="me" class:active={route.path === '/account'} title={t('nav.account')} onclick={() => go('/account')}>
        <img class="head" src={head(me.uuid)} alt="" width="28" height="28" />
        <span class="name">{me.name}</span>
      </button>
      <button class="secondary icon" title={t('nav.logout')} aria-label={t('nav.logout')} onclick={logout}>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons.logout} /></svg>
      </button>
    </div>
  </header>

  <main>
    {#if route.path === '/account'}
      <Account {me} />
    {:else if ticketId && me.may.tickets}
      {#key ticketId}<TicketView id={ticketId} {me} />{/key}
    {:else if route.path === '/tickets' && me.may.tickets}
      <Tickets {me} />
    {:else if route.path === '/health' && me.may.health}
      <Health />
    {:else if chunkId && me.may.mrchunks}
      {#key chunkId}<ChunkView id={chunkId} {me} />{/key}
    {:else if route.path === '/chunks' && me.may.mrchunks}
      <Chunks />
    {:else if playerId && me.may.players}
      {#key playerId}<PlayerView uuid={playerId} {me} />{/key}
    {:else if route.path === '/players' && me.may.players}
      <Players />
    {:else if route.path === '/console' && me.may.console}
      <Console />
    {:else if route.path === '/sleep' && me.may.mrsleep}
      <Sleep {me} />
    {:else if route.path === '/audit' && me.may.audit}
      <Audit />
    {:else}
      <Home {me} />
    {/if}
    <Footer />
  </main>

  {#if moreOpen}
    <!-- The rest of the navigation on the phone, above the bottom bar. -->
    <button class="scrim" aria-label={t('common.close')} onclick={() => (moreOpen = false)}></button>
    <div class="more-sheet" role="menu">
      {#each extra as item}
        <button role="menuitem" class:active={section === item.path} onclick={() => { moreOpen = false; go(item.path); }}>
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons[item.icon]} /></svg>
          {item.label}
        </button>
      {/each}
    </div>
  {/if}
  <nav class="tabs">
    {#each tabs as item}
      <button class:active={section === item.path} onclick={() => go(item.path)}>
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons[item.icon]} /></svg>
        <span>{item.label}</span>
      </button>
    {/each}
    {#if extra.length}
      <button class:active={moreOpen || extra.some((i) => i.path === section)} aria-expanded={moreOpen} onclick={() => (moreOpen = !moreOpen)}>
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons.more} /></svg>
        <span>{t('nav.more')}</span>
      </button>
    {/if}
  </nav>
{/if}

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
  }

  .top {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 10px 16px;
    padding-top: max(10px, env(safe-area-inset-top));
    background: color-mix(in srgb, var(--surface) 85%, transparent);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border);
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 800;
    font-size: 1.05rem;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--muted);
    opacity: 0.5;
  }

  .dot.on {
    background: var(--good);
    opacity: 1;
  }

  .desktop {
    display: none;
    gap: 4px;
    margin-left: 12px;
  }

  .link {
    background: transparent;
    color: var(--muted);
    padding: 8px 12px;
  }

  .link.active {
    color: var(--text);
    background: var(--surface-2);
  }

  .user {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .me {
    display: flex;
    align-items: center;
    gap: 10px;
    background: transparent;
    color: var(--text);
    padding: 4px 10px 4px 4px;
  }

  .me:hover,
  .me.active {
    background: var(--surface-2);
  }

  .head {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .name {
    display: none;
    font-weight: 600;
  }

  .icon {
    padding: 8px;
  }

  main {
    max-width: 1100px;
    margin: 0 auto;
    padding: 20px 16px calc(90px + env(safe-area-inset-bottom));
  }

  .tabs {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 10;
    display: flex;
    background: color-mix(in srgb, var(--surface) 92%, transparent);
    backdrop-filter: blur(12px);
    border-top: 1px solid var(--border);
    padding-bottom: env(safe-area-inset-bottom);
  }

  .tabs button {
    flex: 1;
    flex-direction: column;
    gap: 2px;
    background: transparent;
    color: var(--muted);
    border-radius: 0;
    padding: 8px 4px 10px;
    font-size: 0.72rem;
    font-weight: 600;
  }

  .tabs button.active {
    color: var(--accent);
  }

  .tabs button {
    min-width: 0;
  }

  .tabs span {
    max-width: 100%;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .scrim {
    position: fixed;
    inset: 0;
    z-index: 9;
    background: rgb(0 0 0 / 0.25);
    border-radius: 0;
    padding: 0;
  }

  .more-sheet {
    position: fixed;
    right: 8px;
    bottom: calc(70px + env(safe-area-inset-bottom));
    z-index: 11;
    display: grid;
    gap: 2px;
    min-width: 200px;
    padding: 6px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
  }

  .more-sheet button {
    justify-content: flex-start;
    gap: 10px;
    background: transparent;
    color: var(--text);
    padding: 10px 12px;
  }

  .more-sheet button.active {
    color: var(--accent);
    background: var(--surface-2);
  }

  /* Tablet and PC: navigation at the top instead of the bottom bar. */
  @media (min-width: 760px) {
    .desktop {
      display: flex;
    }

    .name {
      display: inline;
    }

    .tabs,
    .scrim,
    .more-sheet {
      display: none;
    }

    main {
      padding: 28px 24px 40px;
    }
  }
</style>
