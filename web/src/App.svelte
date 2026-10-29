<script>
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from './lib/api.js';
  import { route, go } from './lib/router.svelte.js';
  import { t } from './lib/i18n.js';
  import { connect, disconnect, live } from './lib/live.svelte.js';
  import TopBar from './components/TopBar.svelte';
  import Login from './pages/Login.svelte';
  import Setup from './pages/Setup.svelte';
  import Home from './pages/Home.svelte';
  import Account from './pages/Account.svelte';
  import Tickets from './pages/Tickets.svelte';
  import TicketView from './pages/TicketView.svelte';
  import Legal from './pages/Legal.svelte';
  import Footer from './components/Footer.svelte';
  import Health from './pages/Health.svelte';
  import Chunks from './pages/Chunks.svelte';
  import ChunkView from './pages/ChunkView.svelte';
  import Sleep from './pages/Sleep.svelte';
  import Pvp from './pages/Pvp.svelte';
  import PvpGame from './pages/PvpGame.svelte';
  import PvpKits from './pages/PvpKits.svelte';
  import PvpKit from './pages/PvpKit.svelte';
  import Players from './pages/Players.svelte';
  import Console from './pages/Console.svelte';
  import Maintenance from './pages/Maintenance.svelte';
  import News from './pages/News.svelte';
  import Versions from './pages/Versions.svelte';
  import Discord from './pages/Discord.svelte';
  import PlayerView from './pages/PlayerView.svelte';
  import Contact from './pages/Contact.svelte';
  import Unban from './pages/Unban.svelte';
  import RequestStatus from './pages/RequestStatus.svelte';
  import SelfApp from './self/SelfApp.svelte';

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

  // Public pages work without signing in: setup, imprint, privacy, contact, unban request, request status - and the
  // player area (/me) with its own sign-in.
  const selfPage = $derived(route.path === '/me' || route.path.startsWith('/me/'));
  const requestToken = $derived(route.path.match(/^\/anfrage\/([A-Za-z0-9_-]{16,64})$/)?.[1] ?? null);
  const publicPage = $derived(
    ['/setup', '/impressum', '/datenschutz', '/discord', '/kontakt', '/entbannung'].includes(route.path) || requestToken !== null || selfPage,
  );

  // Ask who is signed in as soon as a page needs it.
  $effect(() => {
    if (me === undefined && !publicPage) untrack(loadMe);
  });

  // /tickets/12 -> "12"; on a single ticket "Tickets" stays highlighted in the navigation.
  const ticketId = $derived(route.path.match(/^\/tickets\/(\d+)$/)?.[1] ?? null);
  const chunkId = $derived(route.path.match(/^\/chunks\/(\d+)$/)?.[1] ?? null);
  // /players/<uuid> is the overview, /players/<uuid>/<section> one section of it (tickets, mrsleep, ...).
  const playerMatch = $derived(route.path.match(/^\/players\/([0-9a-f-]{36})(?:\/([a-z]+))?$/));
  const playerId = $derived(playerMatch?.[1] ?? null);
  const playerSection = $derived(playerMatch?.[2] ?? null);
  const pvpId = $derived(route.path.match(/^\/pvp\/(\d+)$/)?.[1] ?? null);
  // /pvp/kits = list, /pvp/kits/<id> = one kit, /pvp/new-kit[/<copy of>] = new kit
  const kitMatch = $derived(route.path.match(/^\/pvp\/(kits|new-kit)(?:\/([a-z0-9_-]+))?$/));
  const section = $derived(ticketId ? '/tickets' : chunkId ? '/chunks' : playerId ? '/players' : pvpId || kitMatch ? '/pvp' : route.path);

  const nav = $derived(
    me
      ? [
          { path: '/', label: t('nav.home'), icon: 'home' },
          ...(me.may.tickets ? [{ path: '/tickets', label: t('nav.tickets'), icon: 'ticket' }] : []),
          ...(me.may.players ? [{ path: '/players', label: t('nav.players'), icon: 'users' }] : []),
          ...(me.may.health ? [{ path: '/health', label: t('nav.health'), icon: 'pulse' }] : []),
          ...(me.may.mrchunks ? [{ path: '/chunks', label: t('nav.chunks'), icon: 'grid' }] : []),
          ...(me.may.mrsleep ? [{ path: '/sleep', label: t('nav.sleep'), icon: 'moon' }] : []),
          ...(me.may.mrpvp ? [{ path: '/pvp', label: t('nav.pvp'), icon: 'swords' }] : []),
          ...(me.may.maintenance || me.may.announce ? [{ path: '/maintenance', label: t('nav.maintenance'), icon: 'tool' }] : []),
          ...(me.may.news ? [{ path: '/news', label: t('nav.news'), icon: 'news' }] : []),
          ...(me.may.versions ? [{ path: '/versions', label: t('nav.versions'), icon: 'box' }] : []),
          ...(me.may.console ? [{ path: '/console', label: t('nav.console'), icon: 'terminal' }] : []),
        ]
      : [],
  );
</script>

{#if selfPage}
  <SelfApp />
  <Footer />
{:else if route.path === '/impressum' || route.path === '/datenschutz'}
  <Legal page={route.path === '/impressum' ? 'imprint' : 'privacy'} />
  <Footer />
{:else if route.path === '/kontakt'}
  <Contact />
  <Footer />
{:else if route.path === '/entbannung'}
  <Unban />
  <Footer />
{:else if requestToken}
  <RequestStatus token={requestToken} />
  <Footer />
{:else if route.path === '/discord'}
  <Discord onSignedIn={loadMe} />
{:else if route.path === '/setup'}
  <Setup onDone={() => { loadMe(); go('/', true); }} />
{:else if me === undefined}
  <div class="center muted">{t('common.loading')}</div>
{:else if me === null}
  <Login onSignedIn={loadMe} />
{:else}
  <TopBar items={nav} current={section} onnav={(path) => go(path)} user={me} moreLabel={t('nav.more')} live={live.connected}
    liveTitle={live.connected ? t('common.live') : t('common.offline')} menu={[
      { label: t('nav.account'), icon: '👤', active: route.path === '/account', onclick: () => go('/account') },
      { label: t('nav.logout'), icon: '↩', danger: true, onclick: logout },
    ]} />

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
      {#key playerId}<PlayerView uuid={playerId} section={playerSection} {me} />{/key}
    {:else if route.path === '/players' && me.may.players}
      <Players />
    {:else if route.path === '/versions' && me.may.versions}
      <Versions />
    {:else if route.path === '/maintenance' && (me.may.maintenance || me.may.announce)}
      <Maintenance {me} />
    {:else if route.path === '/news' && me.may.news}
      <News />
    {:else if route.path === '/console' && me.may.console}
      <Console {me} />
    {:else if pvpId && me.may.mrpvp}
      {#key pvpId}<PvpGame id={pvpId} {me} />{/key}
    {:else if kitMatch && me.may.mrpvpKits}
      {#if kitMatch[1] === 'kits' && !kitMatch[2]}
        <PvpKits />
      {:else}
        {#key route.path}<PvpKit id={kitMatch[1] === 'kits' ? kitMatch[2] : null} copyFrom={kitMatch[1] === 'new-kit' ? (kitMatch[2] ?? null) : null} />{/key}
      {/if}
    {:else if route.path === '/pvp' && me.may.mrpvp}
      <Pvp {me} />
    {:else if route.path === '/sleep' && me.may.mrsleep}
      <Sleep {me} />
    {:else}
      <Home {me} />
    {/if}
    <Footer />
  </main>

{/if}

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
  }

  main {
    max-width: 1100px;
    margin: 0 auto;
    padding: 20px 16px 32px;
  }

  @media (min-width: 760px) {
    main {
      padding: 28px 24px 40px;
    }
  }
</style>
