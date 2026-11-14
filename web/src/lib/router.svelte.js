// Tiny router: the path decides the page, links change it without reloading. A "#anchor" or "?query" in the path stays
// in the address (the page can use it), route.path is without them.
//
// Since 1.6 the administration has a sidebar: /admin, /admin/community/…, /admin/content/…, /admin/plugins/<plugin>/…,
// /admin/server/…, /admin/settings/…
// Since 1.5 there is one interface: the player area without a prefix (/, /game/…, /map, /stats, /news/…, /inbox, /account)
// and the administration under /admin/…. Old addresses (bookmarks, links in Discord and mails, links inside older
// pages) are translated here by canonical() - also go('/tickets/12') from a page lands on /admin/community/tickets/12.

const PLAYER = [
  [/^\/me\/?$/, () => '/'],
  [/^\/me\/login$/, () => '/'],
  [/^\/me\/devices$/, () => '/account'],
  [/^\/me\/map$/, () => '/map'],
  [/^\/me\/news$/, () => '/news'],
  [/^\/me\/stats$/, () => '/stats'],
  [/^\/me\/tickets(?:\/(\d+))?$/, (m) => '/news/tickets' + (m[1] ? '/' + m[1] : '')],
  // earlier layouts of 1.5: news and statistics under Server, tickets under Help, inbox with tabs
  [/^\/(?:server|inbox)\/news$/, () => '/news'],
  [/^\/server\/stats$/, () => '/stats'],
  [/^\/(?:help|inbox)(?:\/tickets(?:\/(\d+))?)$/, (m) => '/news/tickets' + (m[1] ? '/' + m[1] : '')],
  [/^\/help$/, () => '/news/tickets'],
  [/^\/me\/([a-z0-9_-]+)\/([a-z0-9_-]+)$/, (m) => `/game/${m[1]}/${m[2]}`],
];

const ADMIN = [
  [/^\/tickets(\/\d+)?$/, (m) => '/admin/community/tickets' + (m[1] ?? '')],
  [/^\/players(\/[0-9a-f-]{36}(?:\/[a-z]+)?)?$/, (m) => '/admin/community/players' + (m[1] ?? '')],
  [/^\/health$/, () => '/admin/server/performance'],
  [/^\/chunks(\/\d+)?$/, (m) => '/admin/plugins/mrchunks' + (m[1] ?? '')],
  [/^\/sleep$/, () => '/admin/plugins/mrsleep'],
  [/^\/pvp((?:\/\d+)|(?:\/(?:kits|new-kit)(?:\/[a-z0-9_-]+)?))?$/, (m) => '/admin/plugins/mrpvp' + (m[1] ?? '')],
  [/^\/waypoints$/, () => '/admin/plugins/map'],
  [/^\/maintenance$/, () => '/admin/server/maintenance'],
  // first layout of 1.5 (area Communication, tab Settings)
  [/^\/admin\/communication\/maintenance$/, () => '/admin/server/maintenance'],
  [/^\/admin\/communication\/(news|announce)$/, (m) => '/admin/content/' + m[1]],
  [/^\/admin\/communication$/, () => '/admin'],
  [/^\/admin\/server\/settings$/, () => '/admin/settings/config/MRadmin'],
  // layout of 1.5 (areas with tabs): since the sidebar (1.6) plugins, contents and settings have their own groups
  [/^\/admin\/features(\/.*)?$/, (m) => '/admin/plugins' + (m[1] ?? '')],
  [/^\/admin\/community\/(news|announce)$/, (m) => '/admin/content/' + m[1]],
  [/^\/admin\/server\/appearance$/, () => '/admin/settings/appearance'],
  [/^\/admin\/server\/plugins\/([A-Za-z0-9_-]+)\/config$/, (m) => '/admin/settings/config/' + m[1]],
  [/^\/admin\/(?:community|content)$/, () => '/admin'],
  [/^\/versions$/, () => '/admin/server/plugins'],
  [/^\/plugins\/([A-Za-z0-9_-]+)\/config$/, (m) => '/admin/settings/config/' + m[1]],
  [/^\/plugins(\/[A-Za-z0-9_-]+)?$/, (m) => '/admin/server/plugins' + (m[1] ?? '')],
  [/^\/logs$/, () => '/admin/server/logs'],
  [/^\/console$/, () => '/admin/server/console'],
  [/^\/server$/, () => '/admin/server'],
  [/^\/server\/(plugins|logs|console)$/, (m) => '/admin/server/' + m[1]],
];

/** The address of 1.5 for an old one (unchanged if it is new already). Keeps "?query" and "#anchor". */
export function canonical(path) {
  const cut = path.search(/[?#]/);
  const plain = cut < 0 ? path : path.slice(0, cut);
  const rest = cut < 0 ? '' : path.slice(cut);
  for (const [pattern, to] of [...PLAYER, ...ADMIN]) {
    const m = plain.match(pattern);
    if (m) return to(m) + rest;
  }
  return path;
}

const start = canonical(location.pathname + location.search + location.hash);
if (start !== location.pathname + location.search + location.hash) history.replaceState(null, '', start);

export const route = $state({ path: location.pathname });

export function go(path, replace = false) {
  const target = canonical(path);
  const plain = target.split(/[?#]/)[0];
  if (target === route.path) return;
  if (replace) history.replaceState(null, '', target);
  else history.pushState(null, '', target);
  route.path = plain;
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', () => {
  const now = location.pathname + location.search + location.hash;
  const target = canonical(now);
  if (target !== now) history.replaceState(null, '', target);
  route.path = location.pathname;
});
