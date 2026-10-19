// Tiny router: the path decides the page, links change it without reloading.
export const route = $state({ path: location.pathname });

export function go(path, replace = false) {
  if (path === route.path) return;
  if (replace) history.replaceState(null, '', path);
  else history.pushState(null, '', path);
  route.path = path;
  window.scrollTo(0, 0);
}

window.addEventListener('popstate', () => {
  route.path = location.pathname;
});
