<script>
  // The player area ("/me"): every player sees and manages only their own things. Separate from the team's panel -
  // own sign-in (only through /web in game), own navigation. The areas come from the installed plugins.
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from '../lib/api.js';
  import { route, go } from '../lib/router.svelte.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import SelfLogin from './SelfLogin.svelte';
  import SelfHome from './SelfHome.svelte';
  import SelfDevices from './SelfDevices.svelte';
  import SelfArea from './SelfArea.svelte';
  import SelfTickets from './SelfTickets.svelte';
  import SelfTicket from './SelfTicket.svelte';
  import SelfStats from './SelfStats.svelte';
  import TopBar from '../components/TopBar.svelte';
  import SelfNews from './SelfNews.svelte';

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
  // The place to highlight - also on its sub-pages (a single ticket, ...).
  const current = $derived(nav.find((i) => route.path === i.path || (i.path !== '/me' && route.path.startsWith(i.path + '/')))?.path ?? null);
</script>

{#if player === undefined}
  <div class="center muted">{t('common.loading')}</div>
{:else if player === null}
  <SelfLogin {error} onSignedIn={() => { error = ''; player = undefined; }} />
{:else}
  <TopBar items={nav} current={current} onnav={(path) => go(path)} user={player} menu={[
    { label: t('self.nav.devices'), icon: '📱', active: route.path === '/me/devices', onclick: () => go('/me/devices') },
    { label: t('nav.logout'), icon: '↩', danger: true, onclick: logout },
  ]} />
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

  main {
    max-width: 1100px;
    margin: 0 auto;
    padding: 16px;
  }

</style>
