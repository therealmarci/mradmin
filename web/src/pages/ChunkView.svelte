<script>
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
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
      loader = await api('GET', '/mrchunks/loaders/' + id);
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

  async function act(action) {
    if (action === 'delete' && !confirm(t('chunks.deleteConfirm', { name: loader.name }))) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', `/mrchunks/loaders/${id}/${action}`);
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
          <div class="muted">{loader.ownerName} · Nr. {loader.number}</div>
        </div>
        <span class="badge-status {st.tone}">{st.icon} {t('chunks.status.' + loader.status)}</span>
      </div>
      <dl class="facts">
        <dt>{t('ticket.location')}</dt><dd class="mono coords"><span>{loader.world}</span><span>X {loader.x}</span><span>Y {loader.y}</span><span>Z {loader.z}</span></dd>
        <dt>{t('chunks.created')}</dt><dd>{dateTime(loader.created)}</dd>
        {#if loader.lock}
          <dt>{t('chunks.status.locked')}</dt>
          <dd class="bad-text">{t('chunks.lockedBy', { metric: loader.lock.metric, value: Math.round(loader.lock.value), limit: Math.round(loader.lock.limit) })} · {dateTime(loader.lock.since)}</dd>
        {/if}
      </dl>
      {#if me.may.mrchunksManage}
        <div class="actions">
          {#if loader.lock}
            <button onclick={() => act('unlock')} disabled={busy}>{t('chunks.unlock')}</button>
          {:else}
            <button class:secondary={loader.active} onclick={() => act('toggle')} disabled={busy}>
              {loader.active ? t('chunks.switchOff') : t('chunks.switchOn')}
            </button>
          {/if}
          <button class="danger" onclick={() => act('delete')} disabled={busy}>{t('chunks.delete')}</button>
        </div>
      {/if}
    </section>

    <section class="card">
      <h2>{t('chunks.activity')}</h2>
      {#if loader.metrics}
        <p class="muted small">
          {t('chunks.measured', { ago: ago(loader.measured), loaded: loader.loadedChunks, total: loader.totalChunks })}
        </p>
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
