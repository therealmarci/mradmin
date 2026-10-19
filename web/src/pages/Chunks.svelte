<script>
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';

  let overview = $state(null);
  let loaders = $state(null);
  let error = $state('');
  let filter = $state(sessionStorage.getItem('mradmin.chunks.filter') ?? 'all');
  let search = $state('');

  async function load() {
    try {
      [overview, loaders] = await Promise.all([api('GET', '/mrchunks/overview'), api('GET', '/mrchunks/loaders')]);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Every 5 seconds - MRchunks measures its loaders every 30 s, toggles show up quickly.
  $effect(() => {
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  function setFilter(value) {
    filter = value;
    try {
      sessionStorage.setItem('mradmin.chunks.filter', value);
    } catch {
      // fine
    }
  }

  // Problems first: locked, conspicuous, paused, then the rest by owner and number.
  const order = { locked: 0, warn: 1, paused: 2, ok: 3, inactive: 4 };
  const shown = $derived(
    (loaders ?? [])
      .filter((l) => filter === 'all' || l.status === filter)
      .filter((l) => {
        const q = search.trim().toLowerCase();
        return !q || [l.name, l.ownerName, l.world, `${l.x} ${l.y} ${l.z}`].some((v) => v.toLowerCase().includes(q));
      })
      .sort((a, b) => order[a.status] - order[b.status] || a.ownerName.localeCompare(b.ownerName) || a.number - b.number),
  );
  const counts = $derived(
    (loaders ?? []).reduce((c, l) => ((c[l.status] = (c[l.status] ?? 0) + 1), c), {}),
  );
</script>

<div class="stack">
  <div>
    <h1>{t('chunks.title')}</h1>
    <p class="muted small">{t('chunks.subtitle')}</p>
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if overview}
    {#if overview.paused}
      <div class="notice">⏸ {t('chunks.paused', { tps: overview.tps, resume: overview.resumeTps })}</div>
    {/if}
    <div class="kpis">
      <div class="card kpi"><div class="label">{t('chunks.loaders')}</div><div class="value">{overview.loaders}</div>
        <div class="sub">{t('chunks.owners', { n: overview.owners })}</div></div>
      <div class="card kpi"><div class="label">{t('chunks.active')}</div><div class="value">{overview.active}</div></div>
      <div class="card kpi"><div class="label">{t('chunks.chunks')}</div><div class="value">{overview.chunks}</div>
        <div class="sub">{t('chunks.size', { n: overview.size })}</div></div>
      <div class="card kpi"><div class="label">{t('chunks.problems')}</div>
        <div class="value">{overview.locked + overview.warn}</div>
        <div class="sub">{overview.locked} {t('chunks.status.locked')} · {overview.warn} {t('chunks.status.warn')}</div></div>
    </div>
  {/if}

  <div class="filters">
    <div class="chips">
      <button class:active={filter === 'all'} onclick={() => setFilter('all')}>{t('tickets.all')} {loaders ? `(${loaders.length})` : ''}</button>
      {#each Object.keys(STATUS) as s}
        {#if counts[s]}
          <button class:active={filter === s} onclick={() => setFilter(s)}>{STATUS[s].icon} {t('chunks.status.' + s)} ({counts[s]})</button>
        {/if}
      {/each}
    </div>
    <input type="search" bind:value={search} placeholder={t('chunks.search')} />
  </div>

  {#if !loaders}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else if shown.length === 0}
    <p class="muted">{t('chunks.empty')}</p>
  {:else}
    <div class="list">
      {#each shown as l (l.id)}
        <button class="card item {STATUS[l.status].tone}" onclick={() => go('/chunks/' + l.id)}>
          <img src={head(l.owner)} alt="" width="36" height="36" />
          <div class="body">
            <div class="top">
              <strong>{l.name}</strong>
              <span class="badge-status {STATUS[l.status].tone}">{STATUS[l.status].icon} {t('chunks.status.' + l.status)}</span>
            </div>
            <div class="muted small">{l.ownerName} · <span class="mono">{l.world} {l.x} {l.y} {l.z}</span></div>
            {#if l.lock}
              <div class="small bad-text">{t('chunks.lockedBy', { metric: l.lock.metric, value: Math.round(l.lock.value), limit: Math.round(l.lock.limit) })} · {ago(l.lock.since)}</div>
            {:else if l.worst && l.status === 'warn'}
              <div class="small warn-text">{l.worst.metric}: {Math.round(l.worst.value)} ({t('chunks.warnLimit')} {Math.round(l.worst.warn)})</div>
            {/if}
          </div>
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .kpi {
    padding: 14px 16px;
  }

  .label {
    color: var(--muted);
    font-size: 0.82rem;
    font-weight: 600;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 700;
  }

  .sub {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .filters {
    display: grid;
    gap: 10px;
  }

  .chips {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  .chips button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 12px;
    border-radius: 999px;
    font-size: 0.85rem;
  }

  .chips button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .list {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
  }

  .item {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    text-align: left;
    color: var(--text);
    font-weight: 400;
    padding: 12px 14px;
    border-left: 4px solid var(--border);
  }

  .item.good {
    border-left-color: var(--good);
  }

  .item.warn {
    border-left-color: var(--warn);
  }

  .item.bad {
    border-left-color: var(--bad);
  }

  .item.info {
    border-left-color: var(--accent);
  }

  .item img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .body {
    min-width: 0;
    flex: 1;
  }

  .top {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .badge-status {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .badge-status.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .badge-status.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .badge-status.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .badge-status.info {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .bad-text {
    color: var(--bad);
  }

  .warn-text {
    color: var(--warn);
  }
</style>
