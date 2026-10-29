<script>
  import { untrack } from 'svelte';
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';

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
</script>

<div class="page">
  <button class="back" onclick={() => go('/chunks')}>← {t('chunks.back')}</button>
  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if loader}
    {@const st = STATUS[loader.status]}
    <!-- Head: owner, name, state (its colour on the left as in the list), the facts and what can be done. -->
    <section class="head {st.tone}">
      <div class="who">
        <img src={head(loader.owner)} alt="" width="56" height="56" />
        <div class="names">
          <span class="status">{#if st.icon}{st.icon} {/if}{t('chunks.status.' + loader.status)}</span>
          <h1>{loader.name}</h1>
          <div class="muted">
            {#if me.may.players}<button class="plink" onclick={() => go('/players/' + loader.owner)}>{loader.ownerName}</button>{:else}{loader.ownerName}{/if}
            · Nr. {loader.number}
          </div>
        </div>
      </div>

      {#if loader.lock}
        <div class="lock">
          <b>{t('chunks.status.locked')}</b>
          {t('chunks.lockedBy', { metric: loader.lock.metric, value: Math.round(loader.lock.value), limit: Math.round(loader.lock.limit) })} · {dateTime(loader.lock.since)}
        </div>
      {/if}

      <div class="facts">
        <div class="fact"><span>{t('ticket.location')}</span><b class="mono coords"><i>{loader.world}</i><i>X {loader.x}</i><i>Y {loader.y}</i><i>Z {loader.z}</i></b></div>
        <div class="fact"><span>{t('chunks.created')}</span><b>{dateTime(loader.created)}</b></div>
        {#if sized}
          <div class="fact"><span>{t('chunks.sizeLabel')}</span><b>{t('chunks.sizeValue', { width: loader.width, length: loader.length, chunks: loader.chunks })}</b></div>
          <div class="fact"><span>{t('chunks.ownerBudget')}</span><b>{t('chunks.budgetValue', { used: loader.budgetUsed, budget: loader.budget < 0 ? '∞' : loader.budget })}</b>
            {#if loader.budget > 0}<div class="bar"><span style="width: {Math.min(100, (loader.budgetUsed / loader.budget) * 100)}%"></span></div>{/if}</div>
        {/if}
        {#if loader.dummiesEnabled}
          <div class="fact"><span>{t('chunks.dummy')}</span>
            <b class:good-text={loader.dummyPresent}>{loader.dummyPresent ? '🧍 ' + t('chunks.dummySits') : loader.dummy ? t('chunks.dummyWaiting') : t('chunks.dummyNone')}</b></div>
        {/if}
      </div>

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
            <button class="danger push" onclick={() => act('delete')} disabled={busy}>{t('chunks.delete')}</button>
          {/if}
        </div>
      {/if}
    </section>

    <div class="grid">
      <section class="card activity">
        <div class="c-head">
          <span class="c-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.pulse} /></svg>
          </span>
          <h2>{t('chunks.activity')}</h2>
        </div>
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
              <div class="metric {tone(m)}">
                <div class="mrow"><span>{m.label}</span><strong class="{tone(m)}-text">{value(m)} <small class="muted">/ {m.lock}</small></strong></div>
                <div class="mbar" title="{t('chunks.warnLimit')} {m.warn} · {t('chunks.lockLimit')} {m.lock}">
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

      <div class="side">
        <section class="card">
          <div class="c-head">
            <span class="c-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round"><path d={ICONS.users} /></svg>
            </span>
            <h2>{t('chunks.members')}</h2>
            <span class="count">{loader.members.length}</span>
          </div>
          {#if loader.members.length === 0}
            <p class="muted small">{t('chunks.noMembers')}</p>
          {:else}
            <ul class="members">
              {#each loader.members as m}
                <li>
                  <img src={head(m.uuid)} alt="" width="28" height="28" />
                  <span class="m-body">
                    <strong>{m.name}</strong>
                    <span class="muted small">{m.scope === 'all' ? t('chunks.allLoaders') : t('chunks.thisLoader')} · {m.until ? t('chunks.until', { time: dateTime(m.until) }) : t('chunks.permanent')}</span>
                  </span>
                </li>
              {/each}
            </ul>
          {/if}
        </section>
      </div>
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --cl: var(--accent);
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--cl);
    padding: 4px 0;
  }

  .head {
    --st: var(--muted);
    display: grid;
    gap: 16px;
    padding: 20px 22px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--cl) 28%, var(--border));
    border-left: 4px solid var(--st);
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--cl) 18%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .head.good {
    --st: var(--good);
  }

  .head.warn {
    --st: var(--warn);
  }

  .head.bad {
    --st: var(--bad);
  }

  .head.info {
    --st: var(--accent);
  }

  .who {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .who img {
    border-radius: 12px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .names {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .status {
    font-size: 0.74rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--st);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
    overflow-wrap: anywhere;
  }

  h2 {
    font-size: 1rem;
    margin: 0;
  }

  .plink {
    background: transparent;
    color: var(--cl);
    padding: 0;
    font-weight: 600;
  }

  .lock {
    padding: 10px 14px;
    border-radius: 10px;
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 10%, var(--surface));
    border: 1px solid color-mix(in srgb, var(--bad) 35%, var(--border));
  }

  .facts {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 180px), 1fr));
  }

  .fact {
    display: grid;
    gap: 3px;
    align-content: start;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    min-width: 0;
  }

  .fact > span {
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 600;
  }

  .fact b {
    font-size: 0.92rem;
    overflow-wrap: anywhere;
  }

  .coords {
    display: flex;
    flex-wrap: wrap;
    gap: 0 8px;
  }

  .coords i {
    font-style: normal;
    white-space: nowrap;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .bar {
    height: 5px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    margin-top: 4px;
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    background: var(--cl);
  }

  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .push {
    margin-left: auto;
  }

  /* Below: activity on the left, size and members on the right */
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
    align-items: start;
  }

  .side {
    display: grid;
    gap: 16px;
  }

  .c-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
  }

  .c-icon {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 10px;
    color: var(--cl);
    background: color-mix(in srgb, var(--cl) 14%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--cl) 30%, transparent);
  }

  .count {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 1px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: var(--surface-2);
  }

  .activity p {
    margin: 0 0 8px;
  }

  .metrics {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
    margin-top: 6px;
  }

  .metric {
    --tn: var(--good);
    display: grid;
    gap: 5px;
    padding: 10px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-left: 3px solid var(--tn);
  }

  .metric.warn {
    --tn: var(--warn);
  }

  .metric.bad {
    --tn: var(--bad);
  }

  .mrow {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 0.88rem;
  }

  .mrow small {
    font-weight: 600;
  }

  .mbar {
    position: relative;
    height: 7px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: visible;
  }

  .fill {
    height: 100%;
    border-radius: 4px;
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
    top: -2px;
    bottom: -2px;
    width: 2px;
    border-radius: 1px;
    background: var(--warn);
  }

  .tiny {
    font-size: 0.72rem;
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
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 10px;
    background: var(--surface-2);
  }

  .members img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .m-body {
    display: grid;
    min-width: 0;
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

  @media (max-width: 860px) {
    .grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 640px) {
    .head {
      padding: 16px;
    }

    .push {
      margin-left: 0;
    }
  }
</style>
