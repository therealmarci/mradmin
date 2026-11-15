<script>
  // "More" of the phone layout (since 1.7): every page that is not in the tab bar, in groups like the sidebar, as a
  // list with symbols and counters. The administration can choose its four places of the tab bar here.
  //
  // groups: [{id, label, items: [{id, label, icon, path, n?, tone?}]}]; onnav(path);
  // tabs: null (fixed tab bar) or {all: [{id, label, icon}], chosen: [ids], onchoose(ids), reset()}.
  import { ICONS } from '../../lib/icons.js';
  import { t } from '../../lib/i18n.js';
  import Sheet from './Sheet.svelte';

  let { groups, onnav, tabs = null, children = null } = $props();

  let editing = $state(false);
  let pick = $state([]);
  function edit() {
    pick = [...tabs.chosen];
    editing = true;
  }
  function toggle(id) {
    if (pick.includes(id)) pick = pick.filter((x) => x !== id);
    else if (pick.length < 4) pick = [...pick, id];
  }
  function move(id, dir) {
    const i = pick.indexOf(id), j = i + dir;
    if (j < 0 || j >= pick.length) return;
    const next = [...pick];
    [next[i], next[j]] = [next[j], next[i]];
    pick = next;
  }
</script>

{#snippet glyph(icon)}
  <span class="ico">
    {#if ICONS[icon]}
      <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round" aria-hidden="true"><path d={ICONS[icon]} /></svg>
    {:else}
      <span aria-hidden="true">{icon || '•'}</span>
    {/if}
  </span>
{/snippet}

<div class="more">
  {#each groups.filter((g) => g.items.length) as group (group.id)}
    <section>
      <h2>{group.label}</h2>
      <ul class="list">
        {#each group.items as item (item.id)}
          <li>
            <button onclick={() => onnav(item.path)}>
              {@render glyph(item.icon)}
              <span class="label">{item.label}</span>
              {#if item.n}<span class="n {item.tone ?? 'bad'}">{item.n}</span>{/if}
              <span class="chev" aria-hidden="true">›</span>
            </button>
          </li>
        {/each}
      </ul>
    </section>
  {/each}

  {#if tabs}
    <section>
      <ul class="list">
        <li>
          <button onclick={edit}>
            {@render glyph('grid')}
            <span class="label">{t('mobile.tabs.edit')}</span>
            <span class="chev" aria-hidden="true">›</span>
          </button>
        </li>
      </ul>
    </section>
  {/if}

  {#if children}{@render children()}{/if}
</div>

{#if tabs}
  <Sheet open={editing} onclose={() => (editing = false)} title={t('mobile.tabs.edit')}>
    <p class="muted hint">{t('mobile.tabs.hint')}</p>
    <ul class="list pick">
      {#each tabs.all as item (item.id)}
        {@const n = pick.indexOf(item.id)}
        <li>
          <button class="row" class:on={n >= 0} disabled={n < 0 && pick.length >= 4} onclick={() => toggle(item.id)}>
            {@render glyph(item.icon)}
            <span class="label">{item.label}</span>
            <span class="slot" aria-hidden="true">{n >= 0 ? n + 1 : ''}</span>
          </button>
          {#if n >= 0}
            <span class="order">
              <button class="mini" disabled={n === 0} aria-label={t('mobile.tabs.up')} onclick={() => move(item.id, -1)}>▲</button>
              <button class="mini" disabled={n === pick.length - 1} aria-label={t('mobile.tabs.down')} onclick={() => move(item.id, 1)}>▼</button>
            </span>
          {/if}
        </li>
      {/each}
    </ul>
    <div class="sheet-actions">
      <button class="secondary" onclick={() => { tabs.reset(); editing = false; }}>{t('mobile.tabs.reset')}</button>
      <button disabled={!pick.length} onclick={() => { tabs.onchoose(pick); editing = false; }}>{t('common.save')}</button>
    </div>
  </Sheet>
{/if}

<style>
  .more {
    display: grid;
    gap: 22px;
  }

  section {
    display: grid;
    gap: 6px;
  }

  h2 {
    margin: 0 0 0 16px;
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--muted);
  }

  .list {
    margin: 0;
    padding: 0;
    list-style: none;
    border-radius: 16px;
    overflow: hidden;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .list li {
    display: flex;
    align-items: center;
  }

  .list li + li {
    border-top: 1px solid var(--border);
  }

  .list li button:not(.mini) {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 12px;
    min-height: 52px;
    padding: 8px 14px;
    border: 0;
    border-radius: 0;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    text-align: left;
    -webkit-tap-highlight-color: transparent;
  }

  .list li button:not(.mini):active {
    transform: none;
    background: var(--surface-2);
  }

  .ico {
    flex: none;
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: 9px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 13%, transparent);
  }

  .label {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chev {
    color: var(--muted);
    font-size: 1.4rem;
    line-height: 1;
  }

  .n {
    min-width: 22px;
    padding: 1px 7px;
    border-radius: 999px;
    font-size: 0.75rem;
    font-weight: 800;
    text-align: center;
    color: #fff;
    background: var(--bad);
  }

  .n.warn {
    background: var(--warn);
  }

  .n.info {
    background: var(--accent);
  }

  .hint {
    margin: 0 0 12px;
    font-size: 0.9rem;
  }

  .pick .row:disabled {
    opacity: 0.45;
  }

  .slot {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 2px solid var(--border);
    font-size: 0.78rem;
    font-weight: 800;
  }

  .on .slot {
    color: var(--accent-text);
    background: var(--accent);
    border-color: var(--accent);
  }

  .order {
    display: flex;
    gap: 4px;
    padding-right: 10px;
  }

  .mini {
    width: 34px;
    height: 34px;
    padding: 0;
    border-radius: 10px;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    font-size: 0.7rem;
  }

  .sheet-actions {
    display: flex;
    gap: 10px;
    margin-top: 16px;
  }

  .sheet-actions button {
    flex: 1;
    min-height: 48px;
  }
</style>
