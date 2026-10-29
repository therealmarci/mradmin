// Reloading a page now and then - but only while the browser tab is visible (tabs in the background cost the server
// nothing), and right away when it becomes visible again. Returns the function that stops it (for $effect).
export function poll(load, millis) {
  const timer = setInterval(() => {
    if (!document.hidden) load();
  }, millis);
  const visible = () => {
    if (!document.hidden) load();
  };
  document.addEventListener('visibilitychange', visible);
  return () => {
    clearInterval(timer);
    document.removeEventListener('visibilitychange', visible);
  };
}
