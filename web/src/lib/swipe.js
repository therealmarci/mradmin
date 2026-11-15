// Gestures of the phone layout (since 1.7).

/** True if a touch on this element must not switch tabs: fields, charts, maps, sheets, rows that swipe themselves and
 *  everything that scrolls sideways itself. */
function own(el) {
  for (let n = el; n && n !== document.body; n = n.parentElement) {
    if (n.matches?.('input, textarea, select, canvas, [data-noswipe], .overlay, [role="dialog"], .mr-tabbar, .mr-topbar, .sheet')) return true;
    const style = getComputedStyle(n);
    if ((style.overflowX === 'auto' || style.overflowX === 'scroll') && n.scrollWidth > n.clientWidth + 2) return true;
  }
  return false;
}

/**
 * Calls handler(+1) after a swipe to the left (next) and handler(-1) after a swipe to the right (previous) anywhere on
 * the page. Not at the edges of the screen (the system's back gesture) and not on elements that use the touch
 * themselves. Returns the function that removes it.
 */
export function onSideSwipe(handler) {
  let x0 = 0, y0 = 0, t0 = 0, ok = false;
  const start = (e) => {
    const touch = e.touches[0];
    ok = e.touches.length === 1 && touch.clientX > 24 && touch.clientX < innerWidth - 24 && !own(e.target);
    x0 = touch.clientX;
    y0 = touch.clientY;
    t0 = Date.now();
  };
  const end = (e) => {
    if (!ok) return;
    ok = false;
    const touch = e.changedTouches[0];
    const dx = touch.clientX - x0, dy = touch.clientY - y0;
    if (Math.abs(dx) > 70 && Math.abs(dx) > 2.2 * Math.abs(dy) && Date.now() - t0 < 700) handler(dx < 0 ? 1 : -1);
  };
  window.addEventListener('touchstart', start, { passive: true });
  window.addEventListener('touchend', end, { passive: true });
  return () => {
    window.removeEventListener('touchstart', start);
    window.removeEventListener('touchend', end);
  };
}

/**
 * Pulls an element down with the finger: onmove(px) while it moves (0 when let go), onend(px) once at the end.
 * Starts only on the given handle (or anywhere on the element if the content is scrolled to the top).
 */
export function dragDown(node, { onmove, onend, handle = null }) {
  let y0 = null;
  const start = (e) => {
    const onHandle = handle ? e.target.closest(handle) : null;
    if (!onHandle && node.scrollTop > 0) return;
    if (!onHandle && e.target.closest('input, textarea, select, [data-nodrag]')) return;
    y0 = e.touches[0].clientY;
  };
  const move = (e) => {
    if (y0 === null) return;
    const dy = Math.max(0, e.touches[0].clientY - y0);
    if (dy > 0 && e.cancelable) e.preventDefault();
    onmove(dy);
  };
  const end = (e) => {
    if (y0 === null) return;
    const dy = Math.max(0, e.changedTouches[0].clientY - y0);
    y0 = null;
    onmove(0);
    onend(dy);
  };
  node.addEventListener('touchstart', start, { passive: true });
  node.addEventListener('touchmove', move, { passive: false });
  node.addEventListener('touchend', end, { passive: true });
  return () => {
    node.removeEventListener('touchstart', start);
    node.removeEventListener('touchmove', move);
    node.removeEventListener('touchend', end);
  };
}
