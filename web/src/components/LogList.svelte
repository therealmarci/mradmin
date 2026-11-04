<script>
  // The list of the page Log: one row per entry (time | badge | text in one line | tag), grouped by day, a click opens
  // the whole text and the details. rows: [{key, time, clock, badge: {label, color}, text, html, tag, details: [{label,
  // value}]}] - html = text as safe HTML (already escaped), clock = time shown in the row (empty = none, e.g. a stack
  // trace line), accent = colour of the left edge (default: the badge). pending = new entries held back while paused.
  import { t } from '../lib/i18n.js';

  let { rows = [], mono = false, more = false, loading = false, empty = '', pending = 0, onmore, onshow } = $props();

  let open = $state(new Set());

  function toggle(key) {
    const next = new Set(open);
    next.has(key) ? next.delete(key) : next.add(key);
    open = next;
  }

  const days = $derived.by(() => {
    const out = [];
    for (const row of rows) {
      const day = row.time ? new Date(row.time).toDateString() : out.at(-1)?.day ?? '';
      if (!out.length || out.at(-1).day !== day) out.push({ day, time: row.time, items: [] });
      out.at(-1).items.push(row);
    }
    return out;
  });

  const today = new Date().toDateString();
  const dayLabel = (d) =>
    d.day === today ? t('logs.today') : new Date(d.time).toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' });
</script>

<div class="list" class:mono>
  {#if pending}
    <button type="button" class="pending" onclick={onshow}>{t('logs.pending', { n: pending })}</button>
  {/if}
  {#each days as d (d.day)}
    <div class="day">{dayLabel(d)}</div>
    {#each d.items as row (row.key)}
      <div class="entry" class:open={open.has(row.key)} style="--c:{row.badge?.color ?? 'var(--border)'}; --a:{row.accent ?? row.badge?.color ?? 'var(--border)'}">
        <button type="button" class="row" aria-expanded={open.has(row.key)} onclick={() => toggle(row.key)}>
          <span class="clock">{row.clock}</span>
          <span class="badge" class:none={!row.badge?.label}>{row.badge?.label ?? ''}</span>
          <span class="text">{#if row.html}{@html row.text}{:else}{row.text}{/if}</span>
          {#if row.tag}<span class="tag">{row.tag}</span>{/if}
        </button>
        {#if open.has(row.key)}
          <div class="detail">
            {#if row.details?.length}
              <dl>
                {#each row.details as item}
                  <div><dt>{item.label}</dt><dd>{item.value}</dd></div>
                {/each}
              </dl>
            {/if}
          </div>
        {/if}
      </div>
    {/each}
  {/each}
  {#if !rows.length && !loading}
    <p class="empty">{empty}</p>
  {/if}
  {#if more}
    <button type="button" class="more" disabled={loading} onclick={onmore}>{t('logs.more')}</button>
  {/if}
</div>

<style>
  .list {
    container-type: inline-size;
    display: grid;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    overflow: hidden;
  }

  .day {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: 6px 14px;
    font-size: 0.74rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--muted);
    background: var(--surface-2);
    border-bottom: 1px solid var(--border);
  }

  .entry {
    border-bottom: 1px solid color-mix(in srgb, var(--border) 55%, transparent);
  }

  .row {
    display: grid;
    grid-template-columns: 70px 112px minmax(0, 1fr) auto;
    gap: 12px;
    align-items: center;
    width: 100%;
    justify-content: flex-start;
    padding: 7px 14px 7px 11px;
    border: 0;
    border-left: 3px solid var(--a);
    border-radius: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 0.88rem;
    font-weight: 400;
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
    font-size: 0.78rem;
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

  .badge.none {
    visibility: hidden;
  }

  .text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tag {
    color: var(--muted);
    font-family: ui-monospace, monospace;
    font-size: 0.72rem;
  }

  .open .text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .mono .text {
    font-family: ui-monospace, monospace;
    font-size: 0.8rem;
  }

  .mono .row {
    grid-template-columns: 70px 64px minmax(0, 1fr);
  }

  .detail {
    display: grid;
    gap: 10px;
    padding: 2px 14px 10px 96px;
    background: var(--surface-2);
    border-left: 3px solid var(--a);
  }

  dl {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 22px;
    margin: 0;
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
  }

  .pending {
    display: block;
    width: 100%;
    padding: 8px;
    border: 0;
    border-radius: 0;
    background: color-mix(in srgb, var(--accent) 16%, var(--surface));
    color: var(--accent);
    font-weight: 700;
    font-size: 0.86rem;
  }

  .empty {
    margin: 0;
    padding: 28px 14px;
    text-align: center;
    color: var(--muted);
  }

  .more {
    justify-self: center;
    margin: 10px;
    padding: 6px 14px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
  }

  @container (max-width: 600px) {
    .row,
    .mono .row {
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
