<script>
  // The table of the page Log: one row per entry (time | source | text in one line | event or level), a click opens
  // the whole text and the details; below it the pages. rows: [{key, time, clock, badge: {label, color}, accent, text,
  // html, mono, tag, details: [{label, value}]}] - html = text as safe HTML (already escaped), mono = fixed font
  // (console lines), accent = colour of the left edge.
  import { t } from '../lib/i18n.js';

  let { rows = [], total = 0, page = 0, size = 50, loading = false, empty = '', onpage } = $props();

  let open = $state(new Set());
  $effect(() => {
    page;
    open = new Set();
  });

  function toggle(key) {
    const next = new Set(open);
    next.has(key) ? next.delete(key) : next.add(key);
    open = next;
  }

  const pages = $derived(Math.max(1, Math.ceil(total / size)));
  // 1 … 4 5 [6] 7 8 … 18
  const numbers = $derived.by(() => {
    const out = [];
    for (let p = 0; p < pages; p++) {
      if (p === 0 || p === pages - 1 || Math.abs(p - page) <= 2) out.push(p);
      else if (out.at(-1) !== '…') out.push('…');
    }
    return out;
  });
  const first = $derived(total ? page * size + 1 : 0);
  const last = $derived(Math.min(total, (page + 1) * size));
</script>

<div class="table" class:loading>
  <div class="head" aria-hidden="true">
    <span>{t('logs.col.time')}</span><span>{t('logs.col.source')}</span><span>{t('logs.col.text')}</span><span>{t('logs.col.event')}</span>
  </div>
  {#each rows as row (row.key)}
    <div class="entry" class:open={open.has(row.key)} class:mono={row.mono}
      style="--c:{row.badge?.color ?? 'var(--border)'}; --a:{row.accent ?? row.badge?.color ?? 'var(--border)'}">
      <button type="button" class="row" aria-expanded={open.has(row.key)} onclick={() => toggle(row.key)}>
        <span class="clock">{row.clock}</span>
        <span class="badge">{row.badge?.label ?? ''}</span>
        <span class="text">{#if row.html}{@html row.text}{:else}{row.text}{/if}</span>
        <span class="tag">{row.tag ?? ''}</span>
      </button>
      {#if open.has(row.key) && row.details?.length}
        <dl class="detail">
          {#each row.details as item}
            <div><dt>{item.label}</dt><dd>{item.value}</dd></div>
          {/each}
        </dl>
      {/if}
    </div>
  {/each}
  {#if !rows.length && !loading}
    <p class="empty">{empty}</p>
  {/if}
  <nav class="pages" aria-label={t('logs.pages')}>
    <span class="range">{t('logs.range.of', { from: first, to: last, total })}</span>
    <span class="buttons">
      <button type="button" disabled={page === 0} aria-label={t('logs.page.prev')} onclick={() => onpage(page - 1)}>‹</button>
      {#each numbers as p}
        {#if p === '…'}
          <span class="gap">…</span>
        {:else}
          <button type="button" class:on={p === page} aria-current={p === page ? 'page' : undefined} onclick={() => onpage(p)}>{p + 1}</button>
        {/if}
      {/each}
      <button type="button" disabled={page >= pages - 1} aria-label={t('logs.page.next')} onclick={() => onpage(page + 1)}>›</button>
    </span>
  </nav>
</div>

<style>
  .table {
    container-type: inline-size;
    display: grid;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    overflow: hidden;
    --cols: 108px 112px minmax(0, 1fr) 120px;
  }

  .loading .entry {
    opacity: 0.6;
  }

  .head {
    display: grid;
    grid-template-columns: var(--cols);
    gap: 12px;
    padding: 7px 14px;
    border-bottom: 1px solid var(--border);
    background: var(--surface-2);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: var(--muted);
  }

  .head span:last-child {
    text-align: right;
  }

  .entry {
    border-bottom: 1px solid color-mix(in srgb, var(--border) 55%, transparent);
  }

  .row {
    display: grid;
    grid-template-columns: var(--cols);
    gap: 12px;
    align-items: center;
    width: 100%;
    justify-content: flex-start;
    min-height: 30px;
    padding: 4px 14px 4px 11px;
    border: 0;
    border-left: 3px solid var(--a);
    border-radius: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 0.86rem;
    font-weight: 400;
    line-height: 1.35;
    text-align: left;
  }

  .row:hover {
    background: color-mix(in srgb, var(--surface-2) 70%, transparent);
  }

  .open .row {
    background: var(--surface-2);
  }

  .clock {
    color: var(--muted);
    font-family: ui-monospace, monospace;
    font-size: 0.76rem;
    white-space: nowrap;
  }

  .badge {
    justify-self: start;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 0.74rem;
    font-weight: 700;
    color: var(--c);
    background: color-mix(in srgb, var(--c) 14%, transparent);
  }

  .text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .open .text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .mono .text {
    font-family: ui-monospace, monospace;
    font-size: 0.8rem;
  }

  .tag {
    justify-self: end;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--muted);
    font-family: ui-monospace, monospace;
    font-size: 0.72rem;
  }

  .detail {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 22px;
    margin: 0;
    padding: 2px 14px 10px 135px;
    background: var(--surface-2);
    border-left: 3px solid var(--a);
    font-size: 0.8rem;
  }

  dt {
    display: inline;
    color: var(--muted);
  }

  dt::after {
    content: ': ';
  }

  dd {
    display: inline;
    margin: 0;
    font-family: ui-monospace, monospace;
    overflow-wrap: anywhere;
  }

  .empty {
    margin: 0;
    padding: 28px 14px;
    text-align: center;
    color: var(--muted);
  }

  .pages {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    background: var(--surface-2);
  }

  .range {
    color: var(--muted);
    font-size: 0.82rem;
    font-variant-numeric: tabular-nums;
  }

  .buttons {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
  }

  .buttons button {
    min-width: 32px;
    height: 30px;
    padding: 0 8px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font-size: 0.84rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .buttons button.on {
    border-color: var(--accent);
    background: var(--accent);
    color: #fff;
  }

  .buttons button:disabled {
    opacity: 0.4;
  }

  .gap {
    padding: 0 4px;
    color: var(--muted);
  }

  @container (max-width: 640px) {
    .head {
      display: none;
    }

    .row {
      grid-template-columns: auto minmax(0, 1fr);
      row-gap: 2px;
    }

    .text {
      grid-column: 1 / -1;
      white-space: normal;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .open .text {
      display: block;
    }

    .tag {
      display: none;
    }

    .detail {
      padding-left: 14px;
    }
  }
</style>
