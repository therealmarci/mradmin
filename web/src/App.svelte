<script>
  // The one web interface (since 1.5): the player area without a prefix and the administration under /admin, one bar
  // with a switch between them for the team. The player area has areas with tabs (AreaTabs); the administration a
  // sidebar with all its pages in groups (AdminSidebar, since 1.6).
  // Old addresses are translated by the router (lib/router.svelte.js, canonical).
  import { untrack } from 'svelte';
  import { api, whenSignedOut } from './lib/api.js';
  import { route, go } from './lib/router.svelte.js';
  import { t, lang, errorText } from './lib/i18n.js';
  import { connect, disconnect, live } from './lib/live.svelte.js';
  import { MODULE_ICONS } from './lib/icons.js';
  import TopBar from './components/TopBar.svelte';
  import AreaTabs from './components/AreaTabs.svelte';
  import AdminSidebar from './components/AdminSidebar.svelte';
  import Settings from './pages/Settings.svelte';
  import Plugins from './pages/Plugins.svelte';
  import Logs from './pages/Logs.svelte';
  import Console from './pages/Console.svelte';
  import PageHead from './components/PageHead.svelte';
  import PageTabs from './components/PageTabs.svelte';
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
  import Apply from './pages/Apply.svelte';
  import Unban from './pages/Unban.svelte';
  import RequestStatus from './pages/RequestStatus.svelte';
  import StartPage from './pages/StartPage.svelte';
  import SelfArea from './self/SelfArea.svelte';
  import SelfStats from './self/SelfStats.svelte';
  import SelfMap from './self/SelfMap.svelte';
  import Inbox from './self/Inbox.svelte';
  import SelfNews from './self/SelfNews.svelte';
  import SelfTickets from './self/SelfTickets.svelte';
  import SelfTicket from './self/SelfTicket.svelte';
  import { poll } from './lib/poll.js';
  import { device, page, tabChoice, chooseTabs, DEFAULT_ADMIN_TABS } from './lib/mobile.svelte.js';
  import { brand } from './lib/branding.svelte.js';
  import MobileBar from './components/mobile/MobileBar.svelte';
  import TabBar from './components/mobile/TabBar.svelte';
  import MorePage from './components/mobile/MorePage.svelte';
  import ProfileSheet from './components/mobile/ProfileSheet.svelte';
  import PullRefresh from './components/mobile/PullRefresh.svelte';
  import InstallHint from './components/mobile/InstallHint.svelte';

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
    ['/impressum', '/datenschutz', '/discord', '/kontakt', '/bewerben', '/entbannung', '/email', '/reset'].includes(route.path) || requestToken !== null,
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
    ...(player.stats ? [{ path: '/stats', area: 'stats', label: t('self.nav.stats'), icon: 'chart' }] : []),
    ...(newsTabs.length ? [{ path: newsTabs[0].path, area: 'news', label: t('self.nav.news'), icon: 'news' }] : []),
  ]);

  // Entry News: the tabs news, changelogs and own tickets
  const newsTabs = $derived(!player ? [] : [
    ...(player.news ? [{ id: 'news', label: t('self.news.areaNews'), path: '/news' },
      { id: 'changelog', label: t('self.news.areaChangelog'), path: '/news/changelog' }] : []),
    ...(player.tickets ? [{ id: 'tickets', label: t('nav.tickets'), path: '/news/tickets' }] : []),
  ]);

  // The inbox (letter at the top right): notifications about new posts, answers to own tickets (and team messages);
  // the counter = unread notifications.
  const hasInbox = $derived(!!(player && (player.inbox || player.news || player.tickets)));
  let inboxCount = $state(0);
  let inboxAlarm = $state(false);
  async function loadInbox() {
    try {
      const inbox = await api('GET', '/self/inbox');
      inboxCount = inbox.total;
      inboxAlarm = !!inbox.alarm;
    } catch {
      // keeps the last number (e.g. briefly offline)
    }
  }
  $effect(() => {
    if (hasInbox) return poll(loadInbox, 60000);
  });


  const gameTabs = $derived((player?.areas ?? []).map((a) => ({
    id: `${a.module}/${a.id}`, label: a.title, path: `/game/${a.module}/${a.id}`, icon: MODULE_ICONS[a.module] ?? a.icon,
  })));

  // ------------------------------------------------------------------ administration

  const may = $derived(me?.may ?? {});
  // The sidebar (since 1.6): every page of the administration in groups; plugin pages only with their plugin.
  // Counters from /api/overview (what waits), the state of the network at the bottom.
  let overview = $state(null);
  async function loadOverview() {
    try {
      overview = await api('GET', '/overview');
    } catch {
      // keeps the last state (e.g. briefly offline)
    }
  }
  $effect(() => {
    if (!(me?.team && me.teamReady && admin)) return;
    untrack(loadOverview);
    return poll(loadOverview, 10000);
  });
  // The counters right away after a page change and whenever the server reports something (ticket, plugin, log).
  $effect(() => {
    p;
    live.ticket.tick;
    live.plugin.tick;
    live.log.tick;
    if (me?.team && me.teamReady && admin) untrack(loadOverview);
  });
  let sidebarOpen = $state(false);

  // Hints of a plugin (e.g. locked chunk loaders, rounds to review): their number at the plugin, red if one is bad.
  const moduleCount = (id) => {
    const list = (overview?.alerts ?? []).filter((a) => a.module === id);
    return { n: list.length || undefined, tone: list.some((a) => a.tone === 'bad') ? 'bad' : 'warn' };
  };
  // The waiting room's hints (gone, lagging, countdown): their number, red if it is gone.
  const roomUpdates = () => overview?.network?.room?.updates ?? 0;
  const roomCount = () => {
    const list = (overview?.alerts ?? []).filter((a) => a.key?.startsWith('room'));
    const n = list.length + roomUpdates();
    return { n: n || undefined, tone: list.some((a) => a.tone === 'bad') ? 'bad' : list.some((a) => a.tone === 'warn') ? 'warn' : 'info' };
  };
  const adminGroups = $derived(!me ? [] : [
    { id: 'community', label: t('nav.community'), items: [
      ...(may.players ? [{ id: 'players', label: t('nav.players'), path: '/admin/community/players', icon: 'users' }] : []),
      ...(may.tickets ? [{ id: 'tickets', label: t('nav.tickets'), path: '/admin/community/tickets', icon: 'ticket',
        n: overview?.tickets?.unread || undefined }] : []),
      ...(may.punishments ? [{ id: 'punishments', label: t('punish.title'), path: '/admin/community/punishments', icon: 'ban' }] : []),
    ] },
    { id: 'content', label: t('nav.group.content'), items: [
      ...(may.news ? [{ id: 'news', label: t('news.area.posts'), path: '/admin/content/news', icon: 'news' }] : []),
      ...(may.announce ? [{ id: 'announce', label: t('news.area.announce'), path: '/admin/content/announce', icon: 'megaphone' }] : []),
    ] },
    { id: 'plugins', label: t('nav.group.plugins'), items: [
      ...(may.mrchunks ? [{ id: 'mrchunks', label: t('nav.chunks'), path: '/admin/plugins/mrchunks', icon: 'grid', ...moduleCount('mrchunks') }] : []),
      ...(may.mrpvp ? [{ id: 'mrpvp', label: t('nav.pvp'), path: '/admin/plugins/mrpvp', icon: 'swords', ...moduleCount('mrpvp') }] : []),
      ...(may.mrsleep ? [{ id: 'mrsleep', label: t('nav.sleep'), path: '/admin/plugins/mrsleep', icon: 'moon', ...moduleCount('mrsleep') }] : []),
      ...(may.mrwaypoints || may.mapAdmin ? [{ id: 'map', label: t('nav.waypoints'), path: '/admin/plugins/map', icon: 'pin', ...moduleCount('mrwaypoints') }] : []),
    ] },
    // The servers themselves; each one has its tabs on its page (mainTabs, roomTabs).
    { id: 'server', label: t('nav.server'), items: [
      ...(mainTabs.length ? [{ id: 'main', label: t('nav.serverMain'), path: serverTabNow ? tabPath(mainTabs, serverTabNow) : mainTabs[0].path, icon: 'server',
        n: overview?.network?.endsAt > 0 || overview?.maintenance?.phase === 'active' ? '!' : overview?.versions?.updates || undefined,
        tone: overview?.network?.endsAt > 0 || overview?.maintenance?.phase === 'active' ? 'warn' : 'info' }] : []),
      ...(may.versions && overview?.network?.room ? [{ id: 'room', label: t('nav.serverRoom'), path: serverTabNow ? tabPath(roomTabs, serverTabNow) : '/admin/server/room', icon: 'restore',
        ...roomCount() }] : []),
    ] },
    { id: 'settings', label: t('nav.group.settings'), items: [
      ...(may.appearance ? [{ id: 'appearance', label: t('server.tab.appearance'), path: '/admin/settings/appearance', icon: 'image' }] : []),
    ] },
  ]);
  // The tabs of the main server (address /admin/server[/tab]) and of the waiting room (/admin/server/room[/tab]).
  const mainTabs = $derived([
    ...(may.versions ? [{ id: 'control', label: t('server.tab.control'), path: '/admin/server' }] : []),
    ...(may.health ? [{ id: 'performance', label: t('server.tab.performance'), path: '/admin/server/performance' }] : []),
    ...(may.maintenance ? [{ id: 'maintenance', label: t('nav.maintenance'), path: '/admin/server/maintenance' }] : []),
    ...(may.versions ? [{ id: 'plugins', label: t('server.tab.plugins'), path: '/admin/server/plugins', n: overview?.versions?.updates || undefined }] : []),
    ...(may.audit || may.console ? [{ id: 'logs', label: t('server.tab.logs'), path: '/admin/server/logs' }] : []),
    ...(may.consoleExecute ? [{ id: 'console', label: t('server.tab.console'), path: '/admin/server/console' }] : []),
    ...(may.config && may.versions ? [{ id: 'config', label: t('nav.config'), path: '/admin/server/config' }] : []),
  ]);
  const roomTabs = $derived([
    { id: 'control', label: t('server.tab.control'), path: '/admin/server/room' },
    ...(may.health ? [{ id: 'performance', label: t('server.tab.performance'), path: '/admin/server/room/performance' }] : []),
    { id: 'plugins', label: t('server.tab.plugins'), path: '/admin/server/room/plugins', n: roomUpdates() || undefined },
    ...(may.audit || may.console ? [{ id: 'logs', label: t('server.tab.logs'), path: '/admin/server/room/logs' }] : []),
    ...(may.consoleExecute ? [{ id: 'console', label: t('server.tab.console'), path: '/admin/server/room/console' }] : []),
    ...(may.config ? [{ id: 'config', label: t('nav.config'), path: '/admin/server/room/config' }] : []),
  ]);
  // Switching between the servers keeps the open tab (if the other one has it): the entries point there.
  const serverTabNow = $derived(area !== 'server' ? null : sub[0] === 'room' ? (sub[1] ?? 'control') : (sub[0] ?? 'control'));
  const tabPath = (tabs, id) => tabs.find((x) => x.id === id)?.path ?? tabs[0]?.path;
  // The open page = the entry of the sidebar that is highlighted (the server, not its tab).
  const adminPage = $derived(area === 'overview' ? 'overview' : area === 'server' ? (sub[0] === 'room' ? 'room' : 'main') : (sub[0] ?? ''));
  // The head tile of the sidebar: the overview with what is open (the hints of the dashboard).
  const homeEntry = $derived({ path: '/admin', label: t('nav.home'), n: overview?.alerts?.length || 0,
    tone: overview?.alerts?.some((a) => a.tone === 'bad') ? 'bad' : 'warn' });

  // Main server and waiting room at the bottom of the sidebar.
  let clockNow = $state(Date.now());
  $effect(() => {
    const timer = setInterval(() => (clockNow = Date.now()), 1000);
    return () => clearInterval(timer);
  });
  const servers = $derived.by(() => {
    const net = overview?.network;
    if (!net) return [];
    const left = net.endsAt > 0 ? Math.max(0, Math.round((net.endsAt - clockNow) / 1000)) : 0;
    const mmss = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
    const list = [{ label: t('nav.serverMain'), tone: net.endsAt > 0 ? 'warn' : 'good',
      text: net.endsAt > 0 ? t(net.kind === 'stop' ? 'nav.state.stopIn' : 'nav.state.restartIn', { time: mmss }) : t('nav.state.online') }];
    if (net.room) list.push({ label: t('nav.serverRoom'), tone: net.room.restarting ? 'info' : net.room.online ? 'good' : 'bad',
      text: t(net.room.restarting ? 'nav.state.restarting' : net.room.online ? 'nav.state.online' : 'nav.state.offline') });
    return list;
  });

  // An area without a page in the address: to its first page.
  $effect(() => {
    let to = null;
    const group = adminGroups.find((g) => g.id === area);
    if (admin && group && area !== 'server' && !sub.length) to = group.items[0]?.path ?? '/admin';
    else if (!admin && area === 'game' && sub.length < 2) to = gameTabs[0]?.path;
    else if (!admin && area === 'news' && !sub.length && player && !player.news) to = newsTabs[0]?.path;
    if (to && to !== p) untrack(() => go(to, true));
  });

  const nav = $derived(admin ? [] : playerNav);
  const current = $derived(admin ? null : (nav.find((i) => i.area === area)?.path ?? null));
  const modes = $derived(me?.team ? {
    current: admin ? 'admin' : 'player', player: t('nav.player'), admin: t('nav.admin'),
    onswitch: (m) => go(m === 'admin' ? '/admin' : '/'),
  } : null);

  // ------------------------------------------------------------------ phone layout (since 1.7)
  // Phones and upright tablets: a bar at the top with the big title, a tab bar at the bottom with four places and
  // "More" (/admin/more, /more). The administration chooses its four places itself (lib/mobile.svelte.js).

  const adminShell = $derived(admin && !!me?.team && !!me?.teamReady);
  // Every page of the administration that can be a place in the tab bar.
  const adminPages = $derived([
    { id: 'overview', label: t('nav.home'), icon: 'home', path: '/admin', n: homeEntry.n || undefined, tone: homeEntry.tone },
    ...adminGroups.flatMap((g) => g.items),
  ]);
  const adminTabs = $derived.by(() => {
    const byId = new Map(adminPages.map((x) => [x.id, x]));
    const ids = tabChoice.ids.filter((id) => byId.has(id));
    for (const id of [...DEFAULT_ADMIN_TABS, ...byId.keys()]) if (ids.length < 4 && !ids.includes(id)) ids.push(id);
    return ids.map((id) => byId.get(id));
  });
  const playerTabs = $derived([
    { id: 'start', label: t('self.nav.home'), icon: 'home', path: '/' },
    ...(player?.areas?.length ? [{ id: 'game', label: t('nav.game'), icon: 'grid', path: gameTabs[0].path }] : []),
    ...(player?.map ? [{ id: 'map', label: t('self.nav.map'), icon: 'pin', path: '/map' }] : []),
    ...(newsTabs.length ? [{ id: 'news', label: t('self.nav.news'), icon: 'news', path: newsTabs[0].path }] : []),
  ]);
  const moreTab = $derived({ id: 'more', label: t('nav.more'), icon: 'more', path: admin ? '/admin/more' : '/more',
    // what waits on pages that are not in the bar
    ...(admin ? (() => {
      const hidden = adminPages.filter((x) => !adminTabs.includes(x) && x.n);
      return hidden.length ? { n: hidden.length, tone: hidden.some((x) => x.tone === 'bad') ? 'bad' : 'warn' } : {};
    })() : {}) });
  const mobileTabs = $derived(me ? [...(adminShell ? adminTabs : playerTabs), moreTab] : []);
  const mobileCurrent = $derived(area === 'more' ? 'more'
    : adminShell ? (adminTabs.some((x) => x.id === adminPage) ? adminPage : 'more')
    : playerTabs.some((x) => x.id === area) ? area : 'more');
  // The pages of "More": everything that is not in the tab bar.
  const moreGroups = $derived(adminShell ? [
    { id: 'home', label: t('nav.admin'), items: adminPages.filter((x) => x.id === 'overview' && !adminTabs.includes(x)) },
    ...adminGroups.map((g) => ({ ...g, items: g.items.filter((x) => !adminTabs.includes(x)) })),
  ] : [
    { id: 'self', label: t('nav.player'), items: [
      ...(player?.stats ? [{ id: 'stats', label: t('self.nav.stats'), icon: 'chart', path: '/stats' }] : []),
      ...(player?.tickets ? [{ id: 'tickets', label: t('nav.tickets'), icon: 'ticket', path: '/news/tickets' }] : []),
      ...(hasInbox ? [{ id: 'inbox', label: t('inbox.title'), icon: 'mail', path: '/inbox', n: inboxCount || undefined, tone: inboxAlarm ? 'bad' : 'info' }] : []),
    ] },
    { id: 'account', label: t('mobile.group.account'), items: [
      { id: 'account', label: t('nav.account'), icon: 'users', path: '/account' },
      ...(me?.team ? [{ id: 'admin', label: t('nav.admin'), icon: 'shield', path: '/admin' }] : []),
    ] },
  ]);
  // The title in the bar: the page head of the open page, otherwise the name of the place.
  const fallbackTitle = $derived(area === 'more' ? t('nav.more')
    : adminShell ? (adminPages.find((x) => x.id === adminPage)?.label ?? '')
    : route.path === '/account' ? t('nav.account')
    : area === 'inbox' ? t('inbox.title')
    : area === 'stats' ? t('self.nav.stats')
    : area === 'start' ? brand.name
    : (playerTabs.find((x) => x.id === area)?.label ?? ''));
  const mobileTitle = $derived(page.title || fallbackTitle);
  let profileOpen = $state(false);
  let installOpen = $state(false);
  // Pull to refresh: the page is built again (it loads its data), the counters with it.
  let refreshKey = $state(0);
  async function refresh() {
    refreshKey++;
    await Promise.allSettled([adminShell ? loadOverview() : Promise.resolve(), hasInbox ? loadInbox() : Promise.resolve()]);
  }
  function back(b) {
    if (b.onnav) b.onnav(b.path);
    else go(b.path);
  }


</script>

{#if route.path === '/impressum' || route.path === '/datenschutz'}
  <Legal page={route.path === '/impressum' ? 'imprint' : 'privacy'} />
  <Footer />
{:else if route.path === '/kontakt'}
  <Contact />
  <Footer />
{:else if route.path === '/bewerben'}
  <Apply />
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
  {#if device.mobile}
    <MobileBar title={brand.name} large={false} onlogin={() => (loginOpen = true)} loginLabel={t('nav.login')} />
  {:else}
  <TopBar items={playerNav} current="/" onnav={(path) => go(path)} user={null} moreLabel={t('nav.more')} menu={[]}
      onlogin={() => (loginOpen = true)} loginLabel={t('nav.login')} />
  {/if}
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
  <!-- The pages (both frames use them): the administration and the player area -->
  {#snippet adminContent()}
    {#if area === 'community' && sub[0] === 'tickets' && sub[1] && may.tickets}
      {#key sub[1]}<TicketView id={sub[1]} {me} />{/key}
    {:else if area === 'community' && sub[0] === 'tickets' && may.tickets}
      <Tickets {me} />
    {:else if area === 'community' && sub[0] === 'players' && sub[1] && may.players}
      {#key sub[1]}<PlayerView uuid={sub[1]} section={sub[2] ?? null} {me} />{/key}
    {:else if area === 'community' && sub[0] === 'players' && may.players}
      <Players />
    {:else if area === 'community' && sub[0] === 'punishments' && may.punishments}
      <Punishments />
    {:else if area === 'content' && ((sub[0] === 'news' && may.news) || (sub[0] === 'announce' && may.announce))}
      {#key sub[0]}<News {me} section={sub[0] === 'announce' ? 'announce' : 'posts'} />{/key}
    {:else if area === 'plugins' && sub[0] === 'mrchunks' && may.mrchunks}
      {#if sub[1]}{#key sub[1]}<ChunkView id={sub[1]} {me} />{/key}{:else}<Chunks {me} />{/if}
    {:else if area === 'plugins' && sub[0] === 'mrpvp' && ((sub[1] === 'kits' && sub[2]) || sub[1] === 'new-kit') && may.mrpvpKits}
      {#key p}<PvpKit id={sub[1] === 'kits' ? sub[2] : null} copyFrom={sub[1] === 'new-kit' ? (sub[2] ?? null) : null} />{/key}
    {:else if area === 'plugins' && sub[0] === 'mrpvp' && /^\d+$/.test(sub[1] ?? '') && may.mrpvp}
      {#key sub[1]}<PvpGame id={sub[1]} {me} />{/key}
    {:else if area === 'plugins' && sub[0] === 'mrpvp' && may.mrpvp}
      <Pvp {me} tab={sub[1] ?? 'live'} />
    {:else if area === 'plugins' && sub[0] === 'mrsleep' && may.mrsleep}
      <Sleep {me} />
    {:else if area === 'plugins' && sub[0] === 'map' && (may.mrwaypoints || may.mapAdmin)}
      <Waypoints {me} tab={sub[1] ?? null} />
    {:else if area === 'server' && sub[0] === 'room' && may.versions}
      <PageHead title={t('nav.serverRoom')} />
      {@const roomTab = roomTabs.some((x) => x.id === sub[1]) ? sub[1] : 'control'}
      <PageTabs tabs={roomTabs} current={roomTab} />
      {#if roomTab === 'plugins' && sub[2]}
        {#key sub[2]}<PluginView name={sub[2]} back="/admin/server/room/plugins" />{/key}
      {:else if roomTab === 'plugins'}
        <Plugins embedded server="room" />
      {:else if roomTab === 'config'}
        {#key 'room'}<Settings embedded server="room" plugin={sub[2] ?? null} />{/key}
      {:else if roomTab === 'performance'}
        {#key 'room'}<Health server="room" />{/key}
      {:else if roomTab === 'logs'}
        {#key 'room'}<Logs {me} embedded server="room" />{/key}
      {:else if roomTab === 'console'}
        {#key 'room'}<Console {me} embedded server="room" />{/key}
      {:else}
        <Server {me} tab="overview" bare part="room" />
      {/if}
    {:else if area === 'server' && mainTabs.length}
      {@const tab = mainTabs.some((x) => x.id === (sub[0] ?? 'control')) ? (sub[0] ?? 'control') : mainTabs[0].id}
      <PageHead title={t('nav.serverMain')} />
      <PageTabs tabs={mainTabs} current={tab} />
      {#if tab === 'performance'}
        <Health />
      {:else if tab === 'maintenance'}
        <Maintenance {me} />
      {:else if tab === 'plugins' && sub[1]}
        {#key sub[1]}<PluginView name={sub[1]} />{/key}
      {:else if tab === 'plugins'}
        <Plugins embedded server="main" />
      {:else if tab === 'config'}
        {#key 'main'}<Settings embedded server="main" plugin={sub[1] ?? null} />{/key}
      {:else if tab === 'control'}
        <Server {me} tab="overview" bare part="main" />
      {:else}
        <Server {me} tab={tab} bare />
      {/if}
    {:else if area === 'settings' && sub[0] === 'appearance' && may.appearance}
      <Appearance />
    {:else}
      <Home {me} />
    {/if}
  {/snippet}
  {#snippet playerContent()}
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
    {:else if area === 'stats' && player.stats}
      <SelfStats />
    {:else if area === 'news' && newsTabs.length}
      <AreaTabs tabs={newsTabs} current={sub[0] ?? 'news'} />
      {#if sub[0] === 'tickets' && sub[1] && player.tickets}
        {#key sub[1]}<SelfTicket id={Number(sub[1])} onread={loadInbox} />{/key}
      {:else if sub[0] === 'tickets' && player.tickets}
        <SelfTickets />
      {:else if player.news}
        {#key sub[0]}<SelfNews section={sub[0] === 'changelog' ? 'changelog' : 'news'} />{/key}
      {/if}
    {:else if area === 'inbox' && hasInbox}
      <Inbox {player} onseen={loadInbox} />
    {:else}
      <StartPage {player} canEdit={!!me.may.appearance} />
    {/if}
  {/snippet}

  {#if device.mobile}
    <!-- Phone and upright tablet: bar with the big title at the top, tab bar at the bottom, sheets from below -->
    <MobileBar title={mobileTitle} back={page.back} user={me} onback={back} onprofile={() => (profileOpen = true)} {modes}
      large={!(area === 'map' && !admin) && !(area === 'start' && !admin)} profileLabel={t('mobile.profile')}
      inbox={hasInbox ? { count: inboxCount, alarm: inboxAlarm, active: !admin && area === 'inbox', label: t('inbox.title') } : null}
      oninbox={() => go('/inbox')} live={adminShell ? live.connected : null} liveTitle={live.connected ? t('common.live') : t('common.offline')} />
    {#if !admin && area === 'map' && player?.map}
      <SelfMap />
    {:else}
      <main class="phone">
        <InstallHint bind:show={installOpen} here={adminShell ? area === 'overview' : area === 'start'} />
        <PullRefresh onrefresh={refresh}>
          {#key refreshKey}
            {#if area === 'more'}
              <MorePage groups={moreGroups} onnav={(path) => go(path)} tabs={adminShell ? {
                all: adminPages, chosen: adminTabs.map((x) => x.id), onchoose: chooseTabs, reset: () => chooseTabs(DEFAULT_ADMIN_TABS),
              } : null} />
            {:else if adminShell}
              {@render adminContent()}
            {:else}
              {@render playerContent()}
            {/if}
          {/key}
        </PullRefresh>
        <Footer />
      </main>
    {/if}
    {#if mobileTabs.length > 1}<TabBar items={mobileTabs} current={mobileCurrent} onnav={(path) => go(path)} />{/if}
    <ProfileSheet open={profileOpen} onclose={() => (profileOpen = false)} user={me} {modes}
      live={adminShell ? live.connected : null} onaccount={() => go('/account')} onlogout={logout}
      oninstall={device.standalone ? null : () => (installOpen = true)} />
  {:else}
    <TopBar items={nav} {current} onnav={(path) => go(path)} user={me} moreLabel={t('nav.more')} {modes}
      onmenu={admin && me.team && me.teamReady ? () => (sidebarOpen = !sidebarOpen) : null} menuLabel={t('nav.menu')}
      inbox={hasInbox ? { count: inboxCount, alarm: inboxAlarm, active: !admin && area === 'inbox', label: t('inbox.title') } : null}
      live={admin ? live.connected : null} liveTitle={live.connected ? t('common.live') : t('common.offline')} menu={[
        { label: t('nav.account'), icon: '👤', active: route.path === '/account', onclick: () => go('/account') },
        { label: t('nav.logout'), icon: '↩', danger: true, onclick: logout },
      ]} />
  
    {#if !admin && area === 'map' && player?.map}
      <!-- The map fills the screen below the bar. -->
      <SelfMap />
    {:else if admin && me.team && me.teamReady}
      <!-- The administration: the sidebar on the left, the page next to it (Seitenkopf -> Reiter -> Filter -> Inhalt). -->
      <div class="admin-shell">
        <AdminSidebar groups={adminGroups} current={adminPage} home={homeEntry} {servers} bind:open={sidebarOpen} />
        <main class="admin-main">
        {@render adminContent()}
          <Footer />
        </main>
      </div>
    {:else}
      <main>
      {@render playerContent()}
        <Footer />
      </main>
    {/if}
  {/if}
{/if}

<style>
  .center {
    min-height: 100vh;
    display: grid;
    place-items: center;
  }

  /* The administration: sidebar + page. The page gets the rest of the width (tables, charts, the map). */
  .admin-shell {
    display: flex;
    align-items: flex-start;
  }

  .admin-main {
    flex: 1;
    min-width: 0;
    max-width: 1240px;
    margin: 0 auto;
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
