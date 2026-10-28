<script>
  // The player area ("/me"): every player sees and manages only their own things. Separate from the team's panel -
  // own sign-in (only through /web in game), own navigation. The areas come from the installed plugins.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { route, go } from '../lib/router.svelte.js';
  import { t, lang, setLang, errorText } from '../lib/i18n.js';
  import { head } from '../lib/format.js';
  import SelfLogin from './SelfLogin.svelte';
  import SelfHome from './SelfHome.svelte';
  import SelfDevices from './SelfDevices.svelte';
  import SelfArea from './SelfArea.svelte';
  import SelfTickets from './SelfTickets.svelte';
  import SelfTicket from './SelfTicket.svelte';

  // null = not signed in, undefined = still asking, otherwise {uuid, name, areas}
  let player = $state(undefined);
  let error = $state('');

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
    if (route.path !== '/me/login' && player === undefined) untrack(load);
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
    ...(player?.tickets ? [{ path: '/me/tickets', label: t('self.nav.tickets') }] : []),
    { path: '/me/devices', label: t('self.nav.devices') },
  ]);
</script>

{#if route.path === '/me/login'}
  <SelfLogin onSignedIn={() => { player = undefined; go('/me', true); }} />
{:else if player === undefined}
  <div class="center muted">{t('common.loading')}</div>
{:else if player === null}
  <div class="center">
    <section class="card gate">
      <img src="/icon.svg" alt="" width="48" height="48" />
      <h1>{t('self.title')}</h1>
      {#if error}<div class="error">{error}</div>{/if}
      <p>{t('self.howTo')}</p>
      <p class="muted small">{t('self.howToBookmark')}</p>
      <button class="linkish small" onclick={() => setLang(lang === 'de' ? 'en' : 'de')}>{lang === 'de' ? 'English' : 'Deutsch'}</button>
    </section>
  </div>
{:else}
  <header class="top">
    <div class="who">
      <img class="head" src={head(player.uuid)} alt="" width="28" height="28" />
      <strong>{player.name}</strong>
    </div>
    <nav>
      {#each nav as item}
        <button class="link" class:active={route.path === item.path || (item.path !== '/me' && route.path.startsWith(item.path + '/'))} onclick={() => go(item.path)}>{item.label}</button>
      {/each}
    </nav>
    <div class="right">
      <button class="secondary small" onclick={() => setLang(lang === 'de' ? 'en' : 'de')} title={t('self.language')}>{lang === 'de' ? 'EN' : 'DE'}</button>
      <button class="secondary small" onclick={logout}>{t('nav.logout')}</button>
    </div>
  </header>
  <main>
    {#if route.path === '/me/devices'}
      <SelfDevices />
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

  .gate {
    max-width: 420px;
    text-align: center;
    display: grid;
    gap: 10px;
    justify-items: center;
  }

  .gate h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  .gate p {
    margin: 0;
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
    margin-left: auto;
    display: flex;
    gap: 8px;
  }

  main {
    max-width: 1100px;
    margin: 0 auto;
    padding: 16px;
  }

  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 0;
  }
</style>
