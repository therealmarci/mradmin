<script>
  // Menu "Sources" of the page Log: a button with the number of chosen sources, a click opens the list
  // (components/LogFilter.svelte) below it; a click outside or Esc closes it. selected: array of keys (bindable).
  import { t } from '../lib/i18n.js';
  import LogFilter from './LogFilter.svelte';

  let { items = [], selected = $bindable([]) } = $props();
  let open = $state(false);
  let root = $state();

  function outside(event) {
    if (open && root && !root.contains(event.target)) open = false;
  }
</script>

<svelte:window onpointerdown={outside} onkeydown={(e) => e.key === 'Escape' && (open = false)} />

<div class="picker" bind:this={root}>
  <button type="button" class="button" aria-expanded={open} onclick={() => (open = !open)}>
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d="M3 5h18M6 12h12M10 19h4" /></svg>
    {selected.length ? t('logs.sources.some', { n: selected.length }) : t('logs.sources.all')}
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
  </button>
  {#if open}
    <div class="menu">
      <LogFilter title={t('logs.sources')} {items} bind:selected />
    </div>
  {/if}
</div>

<style>
  .picker {
    position: relative;
  }

  .button {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    height: 36px;
    padding: 0 12px;
    border-radius: 9px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 0.86rem;
    font-weight: 600;
    white-space: nowrap;
  }

  .menu {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    z-index: 20;
    width: 250px;
    padding: 10px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }
</style>
