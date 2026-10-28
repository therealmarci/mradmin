<script>
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';

  let { id, me } = $props();

  let loader = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      loader = await moduleData('mrchunks', 'loader', { id });
      error = '';
    } catch (e) {
      error = errorText(e);
      if (e.status === 404) loader = null;
    }
  }

  $effect(() => {
    id;
    untrack(load);
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  async function act(action, extra = {}) {
    if (action === 'delete' && !confirm(t('chunks.deleteConfirm', { name: loader.name }))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrchunks', { action, id, ...extra });
      if (action === 'delete') {
        go('/chunks');
        return;
      }
      message = t('chunks.done.' + action);
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  // Share of the lock limit, for the bar; the warn limit is marked on it.
  const share = (m) => (m.lock > 0 ? Math.min(100, (m.value / m.lock) * 100) : 0);
  const tone = (m) => (m.lock > 0 && m.value >= m.lock ? 'bad' : m.warn > 0 && m.value >= m.warn ? 'warn' : 'good');
  const value = (m) => (m.rate ? m.value.toFixed(1) + '/s' : String(Math.round(m.value)));
  // MRchunks before the chunk budget sends no size.
  const sized = $derived(loader?.reach != null);
  // With a dummy on, the size only changes evenly (a dummy needs a centred square).
  const evenOnly = $derived(loader?.dummy && loader?.dummiesEnabled);
  const DIRECTIONS = ['north', 'south', 'west', 'east'];
</script>

<div class="page">
  <button class="back" onclick={() => go('/chunks')}>← {t('chunks.back')}</button>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if loader}
    {@const st = STATUS[loader.status]}
    <section class="card header">
      <div class="title">
        <img src={head(loader.owner)} alt="" width="44" height="44" />
        <div>
          <h1>{loader.name}</h1>
          <div class="muted">
            {#if me.may.players}<button class="plink" onclick={() => go('/players/' + loader.owner)}>{loader.ownerName}</button>{:else}{loader.ownerName}{/if}
            · Nr. {loader.number}
          </div>
        </div>
        <span class="badge-status {st.tone}">{st.icon} {t('chunks.status.' + loader.status)}</span>
      </div>
      <dl class="facts">
        <dt>{t('ticket.location')}</dt><dd class="mono coords"><span>{loader.world}</span><span>X {loader.x}</span><span>Y {loader.y}</span><span>Z {loader.z}</span></dd>
        <dt>{t('chunks.created')}</dt><dd>{dateTime(loader.created)}</dd>
        {#if sized}
          <dt>{t('chunks.sizeLabel')}</dt><dd>{t('chunks.sizeValue', { width: loader.width, length: loader.length, chunks: loader.chunks })}</dd>
          <dt>{t('chunks.ownerBudget')}</dt><dd>{t('chunks.budgetValue', { used: loader.budgetUsed, budget: loader.budget < 0 ? '∞' : loader.budget })}</dd>
        {/if}
        {#if loader.dummiesEnabled}
          <dt>{t('chunks.dummy')}</dt>
          <dd class:good-text={loader.dummyPresent}>{loader.dummyPresent ? '🧍 ' + t('chunks.dummySits') : loader.dummy ? t('chunks.dummyWaiting') : t('chunks.dummyNone')}</dd>
        {/if}
        {#if loader.lock}
          <dt>{t('chunks.status.locked')}</dt>
          <dd class="bad-text">{t('chunks.lockedBy', { metric: loader.lock.metric, value: Math.round(loader.lock.value), limit: Math.round(loader.lock.limit) })} · {dateTime(loader.lock.since)}</dd>
        {/if}
      </dl>
      {#if me.may.mrchunksManage || me.may.mrchunksToggle}
        <div class="actions">
          {#if loader.lock}
            {#if me.may.mrchunksManage}
              <button onclick={() => act('unlock')} disabled={busy}>{t('chunks.unlock')}</button>
            {/if}
          {:else}
            <button class:secondary={loader.active} onclick={() => act('toggle')} disabled={busy}>
              {loader.active ? t('chunks.switchOff') : t('chunks.switchOn')}
            </button>
          {/if}
          {#if me.may.mrchunksManage}
            {#if loader.dummiesEnabled}
              <button class="secondary" onclick={() => act('dummy')} disabled={busy || (!loader.dummy && loader.dummyFits === false)}
                title={!loader.dummy && loader.dummyFits === false ? t('chunks.dummyNeedsSquare') : ''}>
                {loader.dummy ? t('chunks.dummyOff') : t('chunks.dummyOn')}
              </button>
            {/if}
            <button class="danger" onclick={() => act('delete')} disabled={busy}>{t('chunks.delete')}</button>
          {/if}
        </div>
      {/if}
    </section>

    {#if sized && me.may.mrchunksResize}
      <section class="card">
        <h2>{t('chunks.resize')}</h2>
        <p class="muted small">{evenOnly ? t('chunks.resizeDummy') : t('chunks.resizeHint', { max: loader.maxReach * 2 + 1 })}</p>
        <div class="resize">
          {#each evenOnly ? ['all'] : [...DIRECTIONS, 'all'] as d (d)}
            <div class="rrow">
              <span>{t('chunks.dir.' + d)}</span>
              <button class="secondary" disabled={busy} onclick={() => act('resize', { direction: d, delta: '-1' })} aria-label="-1">−</button>
              <strong class="mono">{d === 'all' ? loader.width + '×' + loader.length : loader.reach[d]}</strong>
              <button class="secondary" disabled={busy} onclick={() => act('resize', { direction: d, delta: '1' })} aria-label="+1">+</button>
            </div>
          {/each}
        </div>
      </section>
    {/if}

    <section class="card">
      <h2>{t('chunks.activity')}</h2>
      {#if loader.metrics}
        <p class="muted small">
          {t('chunks.measured', { ago: ago(loader.measured), loaded: loader.loadedChunks, total: loader.totalChunks })}
        </p>
        {#if loader.pool && !loader.lock}
          <p class="small" class:warn-text={loader.pool.warnRatio >= 1} class:bad-text={loader.pool.lockRatio >= 1}>
            {t('chunks.pool', { percent: Math.round(loader.pool.warnRatio * 100), chunks: loader.pool.chunks })}
          </p>
        {/if}
        <div class="metrics">
          {#each loader.metrics as m (m.key)}
            <div class="metric">
              <div class="mrow"><span>{m.label}</span><strong class="{tone(m)}-text">{value(m)}</strong></div>
              <div class="bar" title="{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}">
                <div class="fill {tone(m)}" style="width: {share(m)}%"></div>
                {#if m.lock > 0 && m.warn > 0}<div class="tick" style="left: {(m.warn / m.lock) * 100}%"></div>{/if}
              </div>
              <div class="muted tiny">{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}</div>
            </div>
          {/each}
        </div>
      {:else}
        <p class="muted">{t('chunks.noActivity')}</p>
      {/if}
    </section>

    <section class="card">
      <h2>{t('chunks.members')}</h2>
      {#if loader.members.length === 0}
        <p class="muted">{t('chunks.noMembers')}</p>
      {:else}
        <ul class="members">
          {#each loader.members as m}
            <li>
              <img src={head(m.uuid)} alt="" width="24" height="24" />
              <span>{m.name}</span>
              <span class="muted small">{m.scope === 'all' ? t('chunks.allLoaders') : t('chunks.thisLoader')} · {m.until ? t('chunks.until', { time: dateTime(m.until) }) : t('chunks.permanent')}</span>
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--accent);
    padding: 4px 0;
  }

  .title {
    display: flex;
    gap: 14px;
    align-items: center;
    flex-wrap: wrap;
  }

  .title img {
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .plink {
    background: transparent;
    color: var(--accent);
    padding: 0;
    font-weight: 600;
  }

  .title h1 {
    margin: 0;
    font-size: 1.3rem;
  }

  .title .badge-status {
    margin-left: auto;
  }

  .facts {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 4px 16px;
    margin: 14px 0 0;
    font-size: 0.9rem;
  }

  .facts dt {
    color: var(--muted);
  }

  .facts dd {
    margin: 0;
  }

  /* Wrapped only between the values - never inside one. */
  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 12px;
  }

  .coords span {
    white-space: nowrap;
  }

  .actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 16px;
  }

  .resize {
    display: grid;
    gap: 8px;
    max-width: 360px;
  }

  .rrow {
    display: grid;
    grid-template-columns: 1fr 40px 64px 40px;
    gap: 8px;
    align-items: center;
  }

  .rrow strong {
    text-align: center;
  }

  .metrics {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  }

  .mrow {
    display: flex;
    justify-content: space-between;
    font-size: 0.9rem;
  }

  .bar {
    position: relative;
    height: 8px;
    border-radius: 4px;
    background: var(--surface-2);
    margin: 6px 0 4px;
  }

  .fill {
    height: 100%;
    border-radius: 4px;
  }

  .fill.good {
    background: var(--good);
  }

  .fill.warn {
    background: var(--warn);
  }

  .fill.bad {
    background: var(--bad);
  }

  .tick {
    position: absolute;
    top: -3px;
    width: 2px;
    height: 14px;
    background: var(--muted);
  }

  .tiny {
    font-size: 0.75rem;
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

  .members {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .members li {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .members img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .badge-status {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 3px 10px;
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
</style>
