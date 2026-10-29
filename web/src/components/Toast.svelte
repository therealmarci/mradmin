<script>
  // The result of an action as a small bubble that slides in at the bottom and goes away by itself after a few
  // seconds (on the phone above the bar at the bottom). A new text shows it again; an empty one changes nothing.
  import { untrack } from 'svelte';
  import { fly } from 'svelte/transition';

  /** text: what to show; tone: 'good' (done) or 'bad' (failed). */
  let { text = '', tone = 'good', seconds = 4 } = $props();

  let shown = $state('');
  let timer;

  $effect(() => {
    const value = text;
    if (!value) return;
    untrack(() => {
      shown = value;
      clearTimeout(timer);
      timer = setTimeout(() => (shown = ''), seconds * 1000);
    });
  });

  $effect(() => () => clearTimeout(timer));
</script>

{#if shown}
  <div class="toast" class:bad={tone === 'bad'} role="status" aria-live="polite" transition:fly={{ y: 40, duration: 220 }}>
    <span class="mark" aria-hidden="true">{tone === 'bad' ? '✕' : '✓'}</span>
    <span>{shown}</span>
  </div>
{/if}

<style>
  .toast {
    position: fixed;
    left: 50%;
    bottom: 20px;
    z-index: 60;
    translate: -50% 0;
    display: flex;
    align-items: center;
    gap: 10px;
    max-width: min(92vw, 520px);
    padding: 10px 16px 10px 12px;
    border-radius: 999px;
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow), 0 10px 30px -12px rgb(0 0 0 / 0.35);
  }

  .mark {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 50%;
    color: #fff;
    font-size: 0.75rem;
    font-weight: 800;
    background: var(--good);
  }

  .toast.bad {
    color: var(--bad);
  }

  .toast.bad .mark {
    background: var(--bad);
  }

  /* Phone: above the bar at the bottom. */
  @media (max-width: 759px) {
    .toast {
      bottom: calc(78px + env(safe-area-inset-bottom));
    }
  }
</style>
