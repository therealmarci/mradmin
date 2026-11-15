<script>
  // Pull down to refresh (phone layout, since 1.7): at the top of the page, pulling down shows a spinner; let go far
  // enough and onrefresh() runs (the page loads again). children: the page.
  let { onrefresh, children } = $props();

  const LIMIT = 72;
  let pull = $state(0);
  let busy = $state(false);

  $effect(() => {
    let y0 = null, x0 = 0;
    const start = (e) => {
      if (busy || scrollY > 0 || e.touches.length !== 1) return;
      if (e.target.closest('.overlay, .sheet-wrap, [role="dialog"], .mr-tabbar, input, textarea, select, canvas, [data-nopull]')) return;
      y0 = e.touches[0].clientY;
      x0 = e.touches[0].clientX;
    };
    const move = (e) => {
      if (y0 === null) return;
      const dy = e.touches[0].clientY - y0;
      if (dy <= 0 || Math.abs(e.touches[0].clientX - x0) > dy) {
        pull = 0;
        return;
      }
      if (scrollY > 0) return;
      if (e.cancelable) e.preventDefault();
      pull = Math.min(LIMIT * 1.6, dy * 0.5);
    };
    const end = async () => {
      if (y0 === null) return;
      y0 = null;
      if (pull >= LIMIT) {
        busy = true;
        pull = LIMIT * 0.75;
        navigator.vibrate?.(10);
        try {
          await onrefresh();
        } finally {
          await new Promise((r) => setTimeout(r, 450));
          busy = false;
          pull = 0;
        }
      } else pull = 0;
    };
    addEventListener('touchstart', start, { passive: true });
    addEventListener('touchmove', move, { passive: false });
    addEventListener('touchend', end, { passive: true });
    return () => {
      removeEventListener('touchstart', start);
      removeEventListener('touchmove', move);
      removeEventListener('touchend', end);
    };
  });
</script>

<div class="pull" class:busy class:ready={pull >= LIMIT} style:height="{pull}px" aria-hidden="true">
  <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"
    style:transform="rotate({busy ? 0 : pull * 4}deg)" style:opacity={Math.min(1, pull / LIMIT)}>
    <path d="M21 12a9 9 0 1 1-3-6.7" /><path d="M21 3v6h-6" />
  </svg>
</div>
<div class="content" class:moving={pull > 0 && !busy}>{@render children()}</div>

<style>
  .pull {
    display: grid;
    place-items: center;
    overflow: hidden;
    color: var(--muted);
    transition: height 0.25s cubic-bezier(0.2, 0.9, 0.3, 1);
  }

  .pull.ready {
    color: var(--accent);
  }

  .busy svg {
    color: var(--accent);
    animation: spin 0.8s linear infinite;
  }

  .content.moving,
  .pull:has(+ .content.moving) {
    transition: none;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
