<script>
  // The tab bar at the bottom of the phone layout (since 1.7): four places and "More", within reach of the thumb.
  // items: [{id, label, icon, path, n?, tone?}] - n = counter at the symbol (tone bad | warn | info);
  // current: id of the highlighted place ('more' = More); onnav(path).
  import { ICONS } from '../../lib/icons.js';

  let { items, current, onnav } = $props();
</script>

<nav class="tabbar mr-tabbar">
  {#each items as item (item.id)}
    <button class:on={current === item.id} aria-current={current === item.id ? 'page' : undefined}
      onclick={() => (current === item.id ? scrollTo({ top: 0, behavior: 'smooth' }) : onnav(item.path))}>
      <span class="glyph">
        {#if ICONS[item.icon]}
          <svg viewBox="0 0 24 24" width="25" height="25" fill="none" stroke="currentColor" stroke-width={current === item.id ? 2.3 : 1.8}
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d={ICONS[item.icon]} /></svg>
        {:else}
          <span class="emoji" aria-hidden="true">{item.icon || '•'}</span>
        {/if}
        {#if item.n}<span class="n {item.tone ?? 'bad'}">{item.n}</span>{/if}
      </span>
      <span class="label">{item.label}</span>
    </button>
  {/each}
</nav>

<style>
  .tabbar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    display: flex;
    padding: 0 max(4px, env(safe-area-inset-right)) env(safe-area-inset-bottom) max(4px, env(safe-area-inset-left));
    background: color-mix(in srgb, var(--surface) 82%, transparent);
    backdrop-filter: saturate(1.6) blur(16px);
    -webkit-backdrop-filter: saturate(1.6) blur(16px);
    border-top: 1px solid var(--border);
  }

  button {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 7px 2px 6px;
    border: 0;
    border-radius: 0;
    background: transparent;
    color: var(--muted);
    font-size: 0.68rem;
    font-weight: 600;
    -webkit-tap-highlight-color: transparent;
  }

  button:active {
    transform: none;
  }

  button:active .glyph {
    transform: scale(0.9);
  }

  button.on {
    color: var(--accent);
  }

  .glyph {
    position: relative;
    display: grid;
    place-items: center;
    width: 52px;
    height: 30px;
    border-radius: 999px;
    transition: background 0.2s, transform 0.1s;
  }

  .on .glyph {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .emoji {
    font-size: 21px;
    line-height: 1;
  }

  .n {
    position: absolute;
    top: -3px;
    left: 30px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: 0.66rem;
    font-weight: 800;
    line-height: 18px;
    color: #fff;
    background: var(--bad);
    box-shadow: 0 0 0 2px var(--surface);
  }

  .n.warn {
    background: var(--warn);
  }

  .n.info {
    background: var(--accent);
  }

  .label {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
