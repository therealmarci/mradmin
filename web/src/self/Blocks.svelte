<script>
  // Draws the building blocks of a plugin's area (see the API's SelfBlock): key figures, text, tables with buttons,
  // rankings, a small map, bars, cards (open with a click), players, forms, embedded pages and groups of them (API 5).
  // Everything is text - never HTML from a plugin. A button calls onAction(action, args).
  import { t } from '../lib/i18n.js';
  import { head } from '../lib/format.js';
  import AreaMap from './AreaMap.svelte';
  import MeterList from './MeterList.svelte';
  import CardGrid from './CardGrid.svelte';
  import PeopleList from './PeopleList.svelte';
  import FormBlock from './FormBlock.svelte';
  import EmbedBlock from './EmbedBlock.svelte';
  import Blocks from './Blocks.svelte';

  // nested: inside an open card (no outer card frames).
  let { blocks, onAction = null, busy = false, nested = false } = $props();

  // The button whose text field is open, and what is typed there.
  let editing = $state(null);
  let value = $state('');

  function click(button) {
    if (button.input) {
      editing = button;
      value = button.input.value ?? '';
      return;
    }
    if (button.confirm && !confirm(button.confirm)) return;
    onAction?.(button.action, button.args);
  }

  function submit(event) {
    event.preventDefault();
    const button = editing;
    editing = null;
    onAction?.(button.action, { ...button.args, value });
  }
</script>

{#snippet buttonRow(buttons)}
  {#if buttons.length && onAction}
    <div class="buttons">
      {#each buttons as b}
        <button class={b.tone === 'bad' ? 'danger small' : b.tone === 'good' ? 'small' : 'secondary small'} disabled={busy}
          onclick={() => click(b)}>{b.label}</button>
      {/each}
    </div>
  {/if}
{/snippet}

<div class="blocks">
  {#each blocks as block, i (i)}
    {#if block.type === 'stats'}
      <div class="stats">
        {#each block.stats as s}
          <div class="stat"><span class="muted small">{s.label}</span><strong class="{s.tone}-text">{s.value}</strong></div>
        {/each}
      </div>
    {:else if block.type === 'text'}
      <p class="text {block.tone}-text">{block.text}</p>
    {:else if block.type === 'table'}
      <section class="card">
        {#if block.title}<h2>{block.title}</h2>{/if}
        {#if block.rows.length === 0}
          <p class="muted">{block.empty}</p>
        {:else}
          <div class="table">
            <div class="row head-row">
              {#each block.columns as c}<span>{c}</span>{/each}
              <span></span>
            </div>
            {#each block.rows as r}
              <div class="row {r.tone}">
                {#each r.cells as c, ci}<span data-label={block.columns[ci] ?? ''}>{c}</span>{/each}
                <span class="actions">{@render buttonRow(r.buttons)}</span>
              </div>
            {/each}
          </div>
        {/if}
      </section>
    {:else if block.type === 'buttons'}
      {@render buttonRow(block.buttons)}
    {:else if block.type === 'ranking'}
      <section class="card">
        {#if block.title}<h2>{block.title}</h2>{/if}
        {#if block.places.length === 0}
          <p class="muted">{block.empty}</p>
        {:else}
          <ol class="ranking">
            {#each block.places as p}
              <li class:self={p.self}>
                <span class="rank">{p.rank}</span>
                <img src={head(p.uuid)} alt="" width="24" height="24" />
                <span class="name">{p.name}</span>
                <strong>{p.value}</strong>
              </li>
            {/each}
          </ol>
        {/if}
      </section>
    {:else if block.type === 'map'}
      <AreaMap {block} />
    {:else if block.type === 'meters'}
      <section class="card" class:flat={nested}>
        {#if block.title}<h2>{block.title}</h2>{/if}
        {#if block.text}<p class="muted small meters-text">{block.text}</p>{/if}
        <MeterList meters={block.meters} />
      </section>
    {:else if block.type === 'cards'}
      <CardGrid {block} onButton={click} {onAction} {busy} />
    {:else if block.type === 'people'}
      <PeopleList {block} onButton={click} {busy} flat={nested} />
    {:else if block.type === 'form'}
      <FormBlock {block} onSubmit={(a, args) => onAction?.(a, args)} {busy} flat={nested} />
    {:else if block.type === 'embed'}
      <EmbedBlock {block} />
    {:else if block.type === 'group'}
      <!-- Blocks that belong together on one tile; inside an opened card a framed box. -->
      <section class="card group" class:inset={nested}>
        {#if block.title}<h2>{block.title}</h2>{/if}
        {#if block.text}<p class="muted small group-text">{block.text}</p>{/if}
        <Blocks blocks={block.blocks} {onAction} {busy} nested />
      </section>
    {/if}
  {/each}
</div>

{#if editing}
  <!-- Text field of a button (e.g. a new name) -->
  <div class="overlay" role="presentation">
    <form class="card dialog" onsubmit={submit}>
      <label>
        <span>{editing.input.label}</span>
        <!-- svelte-ignore a11y_autofocus -->
        <input type="text" bind:value maxlength={editing.input.maxLength} autofocus />
      </label>
      <div class="buttons">
        <button type="submit" class="small" disabled={busy}>{editing.label}</button>
        <button type="button" class="secondary small" onclick={() => (editing = null)}>{t('common.cancel')}</button>
      </div>
    </form>
  </div>
{/if}

<style>
  .blocks {
    display: grid;
    gap: 16px;
  }

  h2 {
    margin: 0 0 12px;
    font-size: 1.05rem;
  }

  .text {
    margin: 0;
  }

  .stats {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 140px), 1fr));
  }

  .stat {
    display: grid;
    gap: 2px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .stat strong {
    font-size: 1.15rem;
  }

  .table {
    display: grid;
    gap: 6px;
  }

  .row {
    display: grid;
    grid-auto-flow: column;
    grid-auto-columns: minmax(0, 1fr);
    gap: 8px;
    align-items: center;
    padding: 8px 10px;
    border-radius: 8px;
    background: var(--surface-2);
    overflow-wrap: anywhere;
  }

  .row.head-row {
    background: transparent;
    color: var(--muted);
    font-size: 0.8rem;
    padding-top: 0;
    padding-bottom: 0;
  }

  .row.bad {
    box-shadow: inset 3px 0 0 var(--bad);
  }

  .row.warn {
    box-shadow: inset 3px 0 0 var(--warn);
  }

  .row.good {
    box-shadow: inset 3px 0 0 var(--good);
  }

  .row.info {
    box-shadow: inset 3px 0 0 var(--accent);
  }

  .actions {
    justify-self: end;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  /* Phone: every row as a small card, the column name in front of each value. */
  @media (max-width: 640px) {
    .row.head-row {
      display: none;
    }

    .row {
      grid-auto-flow: row;
      grid-auto-columns: auto;
    }

    .row span[data-label]:not([data-label=''])::before {
      content: attr(data-label) ': ';
      color: var(--muted);
      font-size: 0.8rem;
    }

    .actions {
      justify-self: start;
    }
  }

  .ranking {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 4px;
  }

  .ranking li {
    display: grid;
    grid-template-columns: 2.2em 24px 1fr auto;
    gap: 10px;
    align-items: center;
    padding: 6px 10px;
    border-radius: 8px;
  }

  .ranking li.self {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    font-weight: 700;
  }

  .ranking img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .rank {
    color: var(--muted);
    text-align: right;
  }

  .card.flat {
    padding: 0;
    border: 0;
    box-shadow: none;
    background: transparent;
  }

  .group-text {
    margin: -6px 0 4px;
  }

  .group.inset {
    padding: 16px;
    box-shadow: none;
  }

  .meters-text {
    margin: -6px 0 12px;
  }

  .overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 45%);
  }

  .dialog {
    width: min(100%, 420px);
    display: grid;
    gap: 12px;
  }

  .dialog label {
    display: grid;
    gap: 6px;
  }

  .good-text {
    color: var(--good);
  }

  .warn-text {
    color: var(--warn);
  }

  .bad-text {
    color: var(--bad);
  }

  .info-text {
    color: var(--accent);
  }
</style>
