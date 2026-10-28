// Tiny router: the path decides the page, links change it without reloading. A "#anchor" in the path stays in the
// address (the page can scroll to it), route.path is without it.
export const route = $state({ path: location.pathname });

export function go(path, replace = false) {
  const plain = path.split('#')[0];
  if (path === route.path) return;
  if (replace) history.replaceState(null, '', path);
  else history.pushState(null, '', path);
  route.path = plain;
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', () => {
  route.path = location.pathname;
});
