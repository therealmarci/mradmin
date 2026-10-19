<script>
  import { api, whenSignedOut } from './lib/api.js';
  import { route, go } from './lib/router.svelte.js';
  import { t } from './lib/i18n.js';
  import Login from './pages/Login.svelte';
  import Setup from './pages/Setup.svelte';
  import Home from './pages/Home.svelte';
  import Account from './pages/Account.svelte';
  import Audit from './pages/Audit.svelte';

  // null = signed out, undefined = still asking the server
  let me = $state(undefined);

  async function loadMe() {
    try {
      me = await api('GET', '/me');
      if (route.path === '/login') go('/', true);
    } catch {
      me = null;
    }
  }

  whenSignedOut(() => {
    me = null;
  });

  async function logout() {
    try {
      await api('POST', '/logout');
    } finally {
      me = null;
      go('/login');
    }
  }

  if (route.path !== '/setup') loadMe();

  const nav = $derived(
    me
      ? [
          { path: '/', label: t('nav.home'), icon: 'home' },
          { path: '/account', label: t('nav.account'), icon: 'user' },
          ...(me.may.audit ? [{ path: '/audit', label: t('nav.audit'), icon: 'list' }] : []),
        ]
      : [],
  );

  const icons = {
    home: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
    user: 'M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5Z',
    list: 'M4 6h16M4 12h16M4 18h10',
    logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4',
  };
</script>

{#if route.path === '/setup'}
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
    </div>
    <nav class="desktop">
      {#each nav as item}
        <button class:active={route.path === item.path} class="link" onclick={() => go(item.path)}>{item.label}</button>
      {/each}
    </nav>
    <div class="user">
      <img class="head" src={`https://mc-heads.net/avatar/${me.uuid}/64`} alt="" width="28" height="28" />
      <span class="name">{me.name}</span>
      <button class="secondary icon" title={t('nav.logout')} aria-label={t('nav.logout')} onclick={logout}>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons.logout} /></svg>
      </button>
    </div>
  </header>

  <main>
    {#if route.path === '/account'}
      <Account {me} />
    {:else if route.path === '/audit' && me.may.audit}
      <Audit />
    {:else}
      <Home {me} />
    {/if}
  </main>

  <nav class="tabs">
    {#each nav as item}
      <button class:active={route.path === item.path} onclick={() => go(item.path)}>
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d={icons[item.icon]} /></svg>
        <span>{item.label}</span>
      </button>
    {/each}
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

  /* Tablet and PC: navigation at the top instead of the bottom bar. */
  @media (min-width: 760px) {
    .desktop {
      display: flex;
    }

    .name {
      display: inline;
    }

    .tabs {
      display: none;
    }

    main {
      padding: 28px 24px 40px;
    }
  }
</style>
