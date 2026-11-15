<script>
  // A sheet from below (phone layout, since 1.7): a grip on top, pulling it down or tapping next to it closes it.
  // open: shown; onclose(); title: optional heading; children: the content.
  import { dragDown } from '../../lib/swipe.js';

  let { open, onclose, title = '', children } = $props();

  let panel = $state();
  let pull = $state(0);
  $effect(() => {
    if (!open || !panel) return;
    return dragDown(panel, {
      handle: '.grip-zone',
      onmove: (px) => (pull = px),
      onend: (px) => px > 90 && onclose(),
    });
  });
  // No scrolling of the page behind while it is open.
  $effect(() => {
    if (!open) return;
    document.documentElement.classList.add('sheet-open');
    return () => document.documentElement.classList.remove('sheet-open');
  });
</script>

<svelte:window onkeydown={(e) => open && e.key === 'Escape' && onclose()} />

{#if open}
  <div class="sheet-wrap" role="presentation" onclick={(e) => e.target === e.currentTarget && onclose()}>
    <div class="sheet" role="dialog" aria-modal="true" aria-label={title} bind:this={panel}
      style:transform={pull ? `translateY(${pull}px)` : null} class:dragging={pull > 0}>
      <div class="grip-zone">
        <span class="grip" aria-hidden="true"></span>
        {#if title}<h2>{title}</h2>{/if}
      </div>
      <div class="body">{@render children()}</div>
    </div>
  </div>
{/if}

<style>
  .sheet-wrap {
    position: fixed;
    inset: 0;
    z-index: 60;
    display: flex;
    align-items: flex-end;
    background: rgb(0 0 0 / 45%);
    animation: fade 0.2s ease;
  }

  .sheet {
    width: 100%;
    max-height: 92dvh;
    display: flex;
    flex-direction: column;
    background: var(--surface);
    border-radius: 22px 22px 0 0;
    box-shadow: 0 -8px 30px rgb(0 0 0 / 30%);
    padding-bottom: env(safe-area-inset-bottom);
    animation: up 0.28s cubic-bezier(0.2, 0.9, 0.3, 1);
    transition: transform 0.25s cubic-bezier(0.2, 0.9, 0.3, 1);
  }

  .sheet.dragging {
    transition: none;
  }

  .grip-zone {
    flex: none;
    display: grid;
    justify-items: center;
    gap: 6px;
    padding: 8px 16px 6px;
    touch-action: none;
  }

  .grip {
    width: 38px;
    height: 5px;
    border-radius: 999px;
    background: var(--border);
  }

  h2 {
    justify-self: start;
    margin: 4px 0 0;
    font-size: 1.15rem;
  }

  .body {
    overflow-y: auto;
    overscroll-behavior: contain;
    padding: 6px 16px 18px;
  }

  @keyframes up {
    from {
      transform: translateY(100%);
    }
  }

  @keyframes fade {
    from {
      opacity: 0;
    }
  }

  @media (min-width: 760px) {
    .sheet {
      max-width: 560px;
      margin: 0 auto;
    }
  }
</style>
