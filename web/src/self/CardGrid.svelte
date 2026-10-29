<script>
  // Cards of a plugin (SelfBlock.Cards), e.g. the chunk loaders: side by side on one background tile, each with status
  // (its colour: good green, warn yellow, bad red, none grey - also the bar of an open card), key figures, bars and
  // one button right on it. A click opens the card: all its buttons and details. An open card stays open while the
  // page reloads (found again by its id) and closes when it is gone (e.g. deleted).
  import Blocks from './Blocks.svelte';
  import MeterList from './MeterList.svelte';
  import { t } from '../lib/i18n.js';

  let { block, onButton, onAction, busy = false } = $props();

  let openId = $state(null);
  const open = $derived(openId === null ? null : block.cards.find((c) => c.id === openId) ?? null);

  // Pages of at most ROWS rows of cards: how many fit in a row follows the width (same as the grid's columns).
  const ROWS = 5;
  const MIN = 270;
  const GAP = 14;
  let width = $state(0);
  let page = $state(0);
  const perPage = $derived(Math.max(1, Math.floor((width + GAP) / (MIN + GAP))) * ROWS);
  const pages = $derived(Math.max(1, Math.ceil(block.cards.length / perPage)));
  const current = $derived(Math.min(page, pages - 1));
  const shown = $derived(block.cards.slice(current * perPage, (current + 1) * perPage));

  function press(event, button) {
    event.stopPropagation();
    onButton(button);
  }

  function key(event) {
    if (event.key === 'Escape') openId = null;
  }
</script>

<svelte:window onkeydown={open ? key : null} />

<section class="card cards-block">
  {#if block.title}<h2>{block.title}</h2>{/if}
  {#if block.cards.length === 0}
    <p class="muted empty">{block.empty}</p>
  {:else}
    <div class="grid" bind:clientWidth={width}>
      {#each shown as c (c.id)}
        <!-- svelte-ignore a11y_click_events_have_key_events -->
        <div class="card-item tone-{c.tone || 'none'}" role="button" tabindex="0" onclick={() => (openId = c.id)}
          onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), (openId = c.id))}>
          <div class="top">
            <div class="names">
              <strong class="title">{c.title}</strong>
              {#if c.subtitle}<span class="subtitle">{c.subtitle}</span>{/if}
            </div>
            {#if c.badge}<span class="badge {c.tone}">{c.badge}</span>{/if}
          </div>
          {#if c.stats.length}
            <div class="chips">
              {#each c.stats as s}<span class="chip"><span class="muted">{s.label}</span> <b class="{s.tone}-text">{s.value}</b></span>{/each}
            </div>
          {/if}
          {#if c.meters.length}<MeterList meters={c.meters} compact />{/if}
          <div class="bottom">
            {#if c.primary}
              <button class={c.primary.tone === 'good' ? 'small' : c.primary.tone === 'bad' ? 'danger small' : 'secondary small'}
                disabled={busy} onclick={(e) => press(e, c.primary)}>{c.primary.label}</button>
            {/if}
            <span class="more">{t('self.cards.details')} →</span>
          </div>
        </div>
      {/each}
    </div>
    {#if pages > 1}
      <div class="pager">
        <button class="secondary small" disabled={current === 0} onclick={() => (page = current - 1)}>← {t('self.cards.prev')}</button>
        <span class="muted small">{t('self.cards.page', { n: current + 1, total: pages })}</span>
        <button class="secondary small" disabled={current >= pages - 1} onclick={() => (page = current + 1)}>{t('self.cards.next')} →</button>
      </div>
    {/if}
  {/if}
</section>

{#if open}
  <div class="overlay" role="presentation" onclick={() => (openId = null)}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="panel card tone-{open.tone || 'none'}" role="dialog" aria-modal="true" aria-label={open.title} tabindex="-1"
      onclick={(e) => e.stopPropagation()}>
      <div class="panel-head">
        <div class="names">
          <h2>{open.title}</h2>
          {#if open.subtitle}<span class="subtitle">{open.subtitle}</span>{/if}
        </div>
        {#if open.badge}<span class="badge {open.tone}">{open.badge}</span>{/if}
        <button class="close secondary" aria-label={t('common.close')} onclick={() => (openId = null)}>✕</button>
      </div>
      {#if open.stats.length}
        <div class="chips">
          {#each open.stats as s}<span class="chip"><span class="muted">{s.label}</span> <b class="{s.tone}-text">{s.value}</b></span>{/each}
        </div>
      {/if}
      {#if open.primary || open.buttons.length}
        <div class="actions">
          {#each open.primary ? [open.primary, ...open.buttons] : open.buttons as b}
            <button class={b.tone === 'good' ? 'small' : b.tone === 'bad' ? 'danger small' : 'secondary small'} disabled={busy}
              onclick={(e) => press(e, b)}>{b.label}</button>
          {/each}
        </div>
      {/if}
      {#if open.meters.length && !open.details.some((d) => d.type === 'meters')}<MeterList meters={open.meters} />{/if}
      {#if open.details.length}<Blocks blocks={open.details} {onAction} {busy} nested />{/if}
    </div>
  </div>
{/if}

<style>
  h2 {
    margin: 0 0 12px;
    font-size: 1.05rem;
  }

  .cards-block h2 {
    margin-bottom: 14px;
  }

  .pager {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 14px;
    margin-top: 16px;
  }

  .empty {
    margin: 0;
  }

  .grid {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 270px), 1fr));
  }

  .card-item {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px 16px 14px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background:
      linear-gradient(160deg, color-mix(in srgb, var(--tone) 9%, transparent), transparent 45%),
      var(--surface-2);
    box-shadow: none;
    cursor: pointer;
    transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
    min-width: 0;
  }

  .card-item::before {
    content: '';
    position: absolute;
    left: 14px;
    right: 14px;
    top: 0;
    height: 3px;
    border-radius: 0 0 3px 3px;
    background: var(--tone);
  }

  .card-item:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--tone) 55%, var(--border));
  }

  .card-item:focus-visible {
    outline: 2px solid var(--tone);
    outline-offset: 2px;
  }

  .tone-good {
    --tone: var(--good);
  }

  .tone-warn {
    --tone: var(--warn);
  }

  .tone-bad {
    --tone: var(--bad);
  }

  .tone-info {
    --tone: var(--accent);
  }

  .tone-none {
    --tone: var(--muted);
  }

  .top,
  .panel-head {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .names {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .title {
    font-size: 1.05rem;
    overflow-wrap: anywhere;
  }

  .subtitle {
    color: var(--muted);
    font-size: 0.82rem;
    overflow-wrap: anywhere;
  }

  .badge {
    flex-shrink: 0;
    font-size: 0.72rem;
    font-weight: 700;
    padding: 3px 9px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 14%, transparent);
  }

  .badge.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .badge.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .badge.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .badge.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 15%, transparent);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    font-size: 0.82rem;
    padding: 4px 9px;
    border-radius: 8px;
    background: var(--surface);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
  }

  .bottom {
    margin-top: auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .more {
    margin-left: auto;
    font-size: 0.82rem;
    font-weight: 700;
    color: var(--tone);
  }

  .overlay {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 45%);
    overflow-y: auto;
  }

  .panel {
    width: min(100%, 760px);
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    display: grid;
    gap: 16px;
    border-top: 4px solid var(--tone);
  }

  .panel h2 {
    margin: 0;
    font-size: 1.25rem;
  }

  .close {
    padding: 4px 10px;
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .muted {
    color: var(--muted);
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

  @media (prefers-reduced-motion: reduce) {
    .card-item {
      transition: none;
    }
  }
</style>
