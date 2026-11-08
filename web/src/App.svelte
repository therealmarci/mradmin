<script>
  // The one web interface (since 1.5): the player area without a prefix and the administration under /admin, one bar
  // with a switch between them for the team. Areas have tabs (AreaTabs); the pages inside are the same as before.
  // Old addresses are translated by the router (lib/router.svelte.js, canonical).
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from './lib/api.js';
  import { route, go } from './lib/router.svelte.js';
  import { t, lang, errorText } from './lib/i18n.js';
  import { connect, disconnect, live } from './lib/live.svelte.js';
  import { MODULE_ICONS } from './lib/icons.js';
  import TopBar from './components/TopBar.svelte';
  import AreaTabs from './components/AreaTabs.svelte';
  import SignIn from './components/SignIn.svelte';
  import LoginsCard from './components/LoginsCard.svelte';
  import Footer from './components/Footer.svelte';
  import MailLink from './pages/MailLink.svelte';
  import Home from './pages/Home.svelte';
  import Account from './pages/Account.svelte';
  import Tickets from './pages/Tickets.svelte';
  import TicketView from './pages/TicketView.svelte';
  import Legal from './pages/Legal.svelte';
  import Health from './pages/Health.svelte';
  import Chunks from './pages/Chunks.svelte';
  import ChunkView from './pages/ChunkView.svelte';
  import Sleep from './pages/Sleep.svelte';
  import Waypoints from './pages/Waypoints.svelte';
  import Pvp from './pages/Pvp.svelte';
  import PvpGame from './pages/PvpGame.svelte';
  import PvpKits from './pages/PvpKits.svelte';
  import PvpKit from './pages/PvpKit.svelte';
  import Players from './pages/Players.svelte';
  import Punishments from './pages/Punishments.svelte';
  import Maintenance from './pages/Maintenance.svelte';
  import News from './pages/News.svelte';
  import PluginView from './pages/PluginView.svelte';
  import Server from './pages/Server.svelte';
  import Appearance from './pages/Appearance.svelte';
  import Discord from './pages/Discord.svelte';
  import PlayerView from './pages/PlayerView.svelte';
  import Contact from './pages/Contact.svelte';
  import Unban from './pages/Unban.svelte';
  import RequestStatus from './pages/RequestStatus.svelte';
  import StartPage from './pages/StartPage.svelte';
  import SelfArea from './self/SelfArea.svelte';
  import SelfTickets from './self/SelfTickets.svelte';
  import SelfTicket from './self/SelfTicket.svelte';
  import SelfStats from './self/SelfStats.svelte';
  import SelfNews from './self/SelfNews.svelte';
  import SelfMap from './self/SelfMap.svelte';

  // null = signed out, undefined = still asking the server
  let me = $state(undefined);
  // the player area: null = not available (switched off, no permission, banned - playerError says why)
  let player = $state(undefined);
  let playerError = $state('');

  async function loadMe() {
    try {
      me = await api('GET', '/me');
      connect();
      await loadPlayer();
    } catch {
      me = null;
    }
  }

  async function loadPlayer() {
    if (!me?.may.selfPage) {
      player = null;
      return;
    }
    try {
      player = await api('GET', '/self?lang=' + lang);
      playerError = '';
    } catch (e) {
      player = null;
      playerError = e.status === 401 ? '' : errorText(e);
    }
  }

  whenSignedOut(() => {
    me = null;
    player = undefined;
    disconnect();
  });

  async function signedIn() {
    me = undefined;
    await loadMe();
    loginOpen = false;
    if (route.path === '/login') go('/', true);
  }

  // The sign-in window over the page (button at the top right). Pages that need a sign-in open it by themselves;
  // closing it there leads to the start page.
  let loginOpen = $state(false);
  const needLogin = $derived(me === null && route.path !== '/');
  function closeLogin() {
    loginOpen = false;
    if (needLogin) go('/', true);
  }

  async function logout() {
    try {
      await api('POST', '/logout');
    } finally {
      me = null;
      player = undefined;
      disconnect();
      go('/', true);
    }
  }

  // Public pages work without signing in.
  const requestToken = $derived(route.path.match(/^\/anfrage\/([A-Za-z0-9_-]{16,64})$/)?.[1] ?? null);
  const publicPage = $derived(
    ['/impressum', '/datenschutz', '/discord', '/kontakt', '/entbannung', '/email', '/reset'].includes(route.path) || requestToken !== null,
  );

  $effect(() => {
    if (me === undefined && !publicPage) untrack(loadMe);
  });
  // Signed in already: the sign-in address leads to the start.
  $effect(() => {
    if (me && route.path === '/login') untrack(() => go('/', true));
  });

  // ------------------------------------------------------------------ where we are

  const p = $derived(route.path);
  const admin = $derived(p === '/admin' || p.startsWith('/admin/'));
  const parts = $derived(p.split('/').filter(Boolean));
  // admin: [admin, area, tab, ...rest]; player: [area, tab, ...rest]
  const area = $derived(admin ? (parts[1] ?? 'overview') : (parts[0] ?? 'start'));
  const sub = $derived(admin ? parts.slice(2) : parts.slice(1));

  // Remember the side (the team comes back where it was) and switch.
  function remember(mode) {
    try {
      localStorage.setItem('mradmin.mode', mode);
    } catch {
      // private mode
    }
  }
  $effect(() => {
    if (me) remember(admin ? 'admin' : 'player');
  });
  let restored = false;
  $effect(() => {
    if (!me || player === undefined || restored) return;
    restored = true;
    let mode = null;
    try {
      mode = localStorage.getItem('mradmin.mode');
    } catch {
      // private mode
    }
    // Back where the team was.
    if (me.team && p === '/' && mode === 'admin') untrack(() => go('/admin', true));
  });

  // ------------------------------------------------------------------ player area

  const playerNav = $derived(!player ? [{ path: '/', area: 'start', label: t('self.nav.home'), icon: 'home' }] : [
    { path: '/', area: 'start', label: t('self.nav.home'), icon: 'home' },
    ...(player.areas?.length ? [{ path: `/game/${player.areas[0].module}/${player.areas[0].id}`, area: 'game', label: t('nav.game'), icon: 'grid' }] : []),
    ...(player.map ? [{ path: '/map', area: 'map', label: t('self.nav.map'), icon: 'pin' }] : []),
    ...(player.news || player.stats ? [{ path: player.news ? '/server/news' : '/server/stats', area: 'server', label: t('nav.serverArea'), icon: 'news' }] : []),
    ...(player.tickets ? [{ path: '/help/tickets', area: 'help', label: t('nav.help'), icon: 'ticket' }] : []),
  ]);

  const gameTabs = $derived((player?.areas ?? []).map((a) => ({
    id: `${a.module}/${a.id}`, label: a.title, path: `/game/${a.module}/${a.id}`, icon: MODULE_ICONS[a.module] ?? a.icon,
  })));
  const serverTabs = $derived([
    ...(player?.news ? [{ id: 'news', label: t('self.nav.news'), path: '/server/news' }] : []),
    ...(player?.stats ? [{ id: 'stats', label: t('self.nav.stats'), path: '/server/stats' }] : []),
  ]);

  // ------------------------------------------------------------------ administration

  const may = $derived(me?.may ?? {});
  const communityTabs = $derived([
    ...(may.players ? [{ id: 'players', label: t('nav.players'), path: '/admin/community/players' }] : []),
    ...(may.tickets ? [{ id: 'tickets', label: t('nav.tickets'), path: '/admin/community/tickets' }] : []),
    ...(may.punishments ? [{ id: 'punishments', label: t('punish.title'), path: '/admin/community/punishments' }] : []),
    ...(may.news ? [{ id: 'news', label: t('news.area.posts'), path: '/admin/community/news' }] : []),
    ...(may.announce ? [{ id: 'announce', label: t('news.area.announce'), path: '/admin/community/announce' }] : []),
  ]);
  const featureTabs = $derived([
    ...(may.mrchunks ? [{ id: 'mrchunks', label: t('nav.chunks'), path: '/admin/features/mrchunks' }] : []),
    ...(may.mrpvp ? [{ id: 'mrpvp', label: t('nav.pvp'), path: '/admin/features/mrpvp' }] : []),
    ...(may.mrsleep ? [{ id: 'mrsleep', label: t('nav.sleep'), path: '/admin/features/mrsleep' }] : []),
    ...(may.mrwaypoints || may.mapAdmin ? [{ id: 'map', label: t('nav.waypoints'), path: '/admin/features/map' }] : []),
  ]);
  const serverAdminTabs = $derived([
    ...(may.versions ? [{ id: 'control', label: t('server.tab.control'), path: '/admin/server' }] : []),
    ...(may.health ? [{ id: 'performance', label: t('server.tab.performance'), path: '/admin/server/performance' }] : []),
    ...(may.maintenance ? [{ id: 'maintenance', label: t('nav.maintenance'), path: '/admin/server/maintenance' }] : []),
    ...(may.versions ? [{ id: 'plugins', label: t('server.tab.plugins'), path: '/admin/server/plugins' }] : []),
    ...(may.audit || may.console ? [{ id: 'logs', label: t('server.tab.logs'), path: '/admin/server/logs' }] : []),
    ...(may.consoleExecute ? [{ id: 'console', label: t('server.tab.console'), path: '/admin/server/console' }] : []),
    ...(may.appearance ? [{ id: 'appearance', label: t('server.tab.appearance'), path: '/admin/server/appearance' }] : []),
  ]);
  const adminNav = $derived(me ? [
    { path: '/admin', area: 'overview', label: t('nav.home'), icon: 'home' },
    ...(communityTabs.length ? [{ path: communityTabs[0].path, area: 'community', label: t('nav.community'), icon: 'users' }] : []),
    ...(featureTabs.length ? [{ path: featureTabs[0].path, area: 'features', label: t('nav.features'), icon: 'grid' }] : []),
    ...(serverAdminTabs.length ? [{ path: serverAdminTabs[0].path, area: 'server', label: t('nav.server'), icon: 'server' }] : []),
  ] : []);

  // An area without a tab in the address: to its first tab.
  $effect(() => {
    const first = (tabs) => tabs[0]?.path;
    let to = null;
    if (admin && area === 'community' && !sub.length) to = first(communityTabs);
    else if (admin && area === 'features' && !sub.length) to = first(featureTabs);
    else if (!admin && area === 'game' && sub.length < 2) to = first(gameTabs);
    else if (!admin && area === 'server' && !sub.length) to = first(serverTabs);
    else if (!admin && area === 'help' && !sub.length) to = '/help/tickets';
    if (to && to !== p) untrack(() => go(to, true));
  });

  const nav = $derived(admin ? adminNav : playerNav);
  const current = $derived(nav.find((i) => i.area === area)?.path ?? null);
  const modes = $derived(me?.team ? {
    current: admin ? 'admin' : 'player', player: t('nav.player'), admin: t('nav.admin'),
    onswitch: (m) => go(m === 'admin' ? '/admin' : '/'),
  } : null);

  // admin/server: [] = control, [performance], [maintenance], [plugins], [plugins, name, config?], [logs], [console]
  const serverTab = $derived(sub[0] ?? 'control');
</script>

{#if route.path === '/impressum' || route.path === '/datenschutz'}
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
  <Discord onSignedIn={signedIn} />
{:else if route.path === '/email' || route.path === '/reset'}
  <MailLink kind={route.path === '/email' ? 'email' : 'reset'} />
{:else if me === undefined || (me && player === undefined)}
  <div class="center muted">{t('common.loading')}</div>
{:else if me === null}
  <!-- Visitors: the same start page, a sign-in button at the top right -->
  <TopBar items={playerNav} current="/" onnav={(path) => go(path)} user={null} moreLabel={t('nav.more')} menu={[]}
    onlogin={() => (loginOpen = true)} loginLabel={t('nav.login')} />
  <main>
    <StartPage onsignin={() => (loginOpen = true)} />
    <Footer />
  </main>
  {#if loginOpen || needLogin}
    <div class="overlay" role="presentation" onclick={(e) => e.target === e.currentTarget && closeLogin()}
      onkeydown={(e) => e.key === 'Escape' && closeLogin()}>
      <div class="dialog" role="dialog" aria-modal="true" aria-label={t('signin.title')}>
        <SignIn modal onclose={closeLogin} onSignedIn={signedIn} />
      </div>
    </div>
  {/if}
{:else}
  <TopBar items={nav} {current} onnav={(path) => go(path)} user={me} moreLabel={t('nav.more')} {modes}
    live={admin ? live.connected : null} liveTitle={live.connected ? t('common.live') : t('common.offline')} menu={[
      { label: t('nav.account'), icon: '👤', active: route.path === '/account', onclick: () => go('/account') },
      { label: t('nav.logout'), icon: '↩', danger: true, onclick: logout },
    ]} />

  {#if !admin && area === 'map' && player?.map}
    <!-- The map fills the screen below the bar. -->
    <SelfMap />
  {:else}
    <main>
      {#if route.path === '/account'}
        <Account {me} />
      {:else if admin && !me.team}
        <div class="gate">
          <p>{t('signin.noTeam')}</p>
          <button class="secondary" onclick={() => go('/')}>{t('signin.toPlayerArea')}</button>
        </div>
      {:else if admin && !me.teamReady}
        <!-- Team, but no own sign-in yet (or 2FA required): set it up right here. -->
        <div class="gate-setup">
          <h1>{t('signin.teamSetupTitle')}</h1>
          <p class="muted">{t(me.require2fa ? 'signin.teamSetupText2fa' : 'signin.teamSetupText')}</p>
          <LoginsCard team require2fa={me.require2fa} onchange={(d) => { if (d.ownLogin) loadMe(); }} />
        </div>
      {:else if admin}
        {#if area === 'community'}
          <AreaTabs tabs={communityTabs} current={sub[0]} />
          {#if sub[0] === 'tickets' && sub[1] && may.tickets}
            {#key sub[1]}<TicketView id={sub[1]} {me} />{/key}
          {:else if sub[0] === 'tickets' && may.tickets}
            <Tickets {me} />
          {:else if sub[0] === 'players' && sub[1] && may.players}
            {#key sub[1]}<PlayerView uuid={sub[1]} section={sub[2] ?? null} {me} />{/key}
          {:else if sub[0] === 'players' && may.players}
            <Players />
          {:else if sub[0] === 'punishments' && may.punishments}
            <Punishments />
          {:else if (sub[0] === 'news' && may.news) || (sub[0] === 'announce' && may.announce)}
            <News {me} section={sub[0] === 'announce' ? 'announce' : 'posts'} />
          {/if}
        {:else if area === 'features'}
          <AreaTabs tabs={featureTabs} current={sub[0]} />
          {#if sub[0] === 'mrchunks' && may.mrchunks}
            {#if sub[1]}{#key sub[1]}<ChunkView id={sub[1]} {me} />{/key}{:else}<Chunks {me} />{/if}
          {:else if sub[0] === 'mrpvp' && (sub[1] === 'kits' || sub[1] === 'new-kit') && may.mrpvpKits}
            {#if sub[1] === 'kits' && !sub[2]}
              <PvpKits />
            {:else}
              {#key p}<PvpKit id={sub[1] === 'kits' ? sub[2] : null} copyFrom={sub[1] === 'new-kit' ? (sub[2] ?? null) : null} />{/key}
            {/if}
          {:else if sub[0] === 'mrpvp' && sub[1] && may.mrpvp}
            {#key sub[1]}<PvpGame id={sub[1]} {me} />{/key}
          {:else if sub[0] === 'mrpvp' && may.mrpvp}
            <Pvp {me} />
          {:else if sub[0] === 'mrsleep' && may.mrsleep}
            <Sleep {me} />
          {:else if sub[0] === 'map' && (may.mrwaypoints || may.mapAdmin)}
            <Waypoints {me} />
          {/if}
        {:else if area === 'server'}
          <AreaTabs tabs={serverAdminTabs} current={serverTab} />
          {#if serverTab === 'performance' && may.health}
            <Health />
          {:else if serverTab === 'maintenance' && may.maintenance}
            <Maintenance {me} />
          {:else if serverTab === 'appearance' && may.appearance}
            <Appearance />
          {:else if serverTab === 'plugins' && sub[1] && may.versions}
            {#key sub[1]}<PluginView name={sub[1]} tab={sub[2] ?? null} />{/key}
          {:else if serverTab === 'control'}
            <Server {me} tab="overview" bare />
          {:else if ['plugins', 'logs', 'console'].includes(serverTab)}
            <Server {me} tab={serverTab} bare />
          {/if}
        {:else}
          <Home {me} />
        {/if}
      {:else if !player && area !== 'start'}
        <!-- No player area (switched off, no permission, banned) -->
        <div class="gate">
          <p>{playerError || t('signin.noPlayerArea')}</p>
          {#if me.team}<button onclick={() => go('/admin')}>{t('nav.admin')}</button>{/if}
        </div>
      {:else if area === 'game'}
        <AreaTabs tabs={gameTabs} current={sub.slice(0, 2).join('/')} />
        {#if sub.length >= 2}
          {#key p}<SelfArea module={sub[0]} id={sub[1]} onChanged={loadPlayer} />{/key}
        {/if}
      {:else if area === 'server'}
        <AreaTabs tabs={serverTabs} current={sub[0]} />
        {#if sub[0] === 'news' && player.news}
          <SelfNews />
        {:else if sub[0] === 'stats' && player.stats}
          <SelfStats />
        {/if}
      {:else if area === 'help'}
        {#if sub[0] === 'tickets' && sub[1] && player.tickets}
          {#key sub[1]}<SelfTicket id={sub[1]} />{/key}
        {:else if player.tickets}
          <SelfTickets />
        {/if}
      {:else}
        <StartPage {player} canEdit={!!me.may.appearance} />
      {/if}
      <Footer />
    </main>
  {/if}
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

  /* Signed in without the team, team without an own sign-in, no player area */
  .gate {
    display: grid;
    justify-items: center;
    gap: 14px;
    padding: 60px 16px;
    text-align: center;
  }

  .gate p {
    margin: 0;
  }

  .overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 55%);
    backdrop-filter: blur(3px);
  }

  .dialog {
    width: min(440px, 100%);
    max-height: calc(100dvh - 32px);
    overflow: auto;
  }

  .gate-setup {
    max-width: 680px;
    display: grid;
    gap: 14px;
  }

  .gate-setup h1,
  .gate-setup p {
    margin: 0;
  }
</style>
