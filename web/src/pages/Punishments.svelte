<script>
  // Community -> Punishments: the punishments of all players from LibertyBans (GET /api/punishments, PlayerRoutes.java).
  // "Active" like LibertyBans' /banlist and /mutelist, "All" (also expired, lifted, kicks) with libertybans.list.history.
  // Lifting goes through the player route with LibertyBans' own permissions.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, ago, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';

  let filter = $state('active');
  let type = $state('');
  let search = $state('');
  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);

  async function load() {
    try {
      data = await api('GET', '/punishments?filter=' + filter);
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }
  $effect(() => {
    filter;
    load();
  });

  const TYPES = ['ban', 'mute', 'warn', 'kick'];
  const shown = $derived((data?.entries ?? []).filter((e) => (!type || e.type === type)
    && (!search.trim() || e.player.toLowerCase().includes(search.trim().toLowerCase())
      || (e.reason ?? '').toLowerCase().includes(search.trim().toLowerCase()))));
  const counts = $derived(Object.fromEntries(TYPES.map((k) => [k, (data?.entries ?? []).filter((e) => e.type === k && e.active).length])));

  function stateOf(e) {
    if (e.active) return 'active';
    if (e.type === 'kick') return null;
    return e.end > 0 && e.end <= Date.now() ? 'expired' : 'lifted';
  }

  async function undo(e) {
    if (!window.confirm(t('lb.undoConfirm', { type: t('lb.type.' + e.type), name: e.player }))) return;
    busy = true;
    error = '';
    try {
      await api('POST', `/players/${e.uuid}/libertybans`, { action: 'undo', type: e.type, id: String(e.id) });
      message = t('punish.lifted', { name: e.player });
      await load();
    } catch (err) {
      error = errorText(err);
    } finally {
      busy = false;
    }
  }
</script>

<div class="page">
  <header class="head card">
    <span class="icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round"><path d={ICONS.shield} /></svg>
    </span>
    <div class="title">
      <h1>{t('punish.title')}</h1>
      <p class="muted small">{t('punish.subtitle')}</p>
    </div>
    {#if data && filter === 'active'}
      <div class="numbers">
        {#each ['ban', 'mute', 'warn'] as k (k)}
          <span class="num t-{k}"><b>{counts[k]}</b> {t('lb.type.' + k)}</span>
        {/each}
      </div>
    {/if}
  </header>

  <div class="bar card">
    <div class="seg" role="radiogroup">
      <button role="radio" aria-checked={filter === 'active'} class:on={filter === 'active'} onclick={() => (filter = 'active')}>
        {t('punish.active')}
      </button>
      {#if data?.history}
        <button role="radio" aria-checked={filter === 'all'} class:on={filter === 'all'} onclick={() => (filter = 'all')}>
          {t('punish.all')}
        </button>
      {/if}
    </div>
    <div class="chips">
      <button class="chip" class:on={!type} onclick={() => (type = '')}>{t('punish.allTypes')}</button>
      {#each TYPES as k (k)}
        {#if filter === 'all' || k !== 'kick'}
          <button class="chip t-{k}" class:on={type === k} onclick={() => (type = type === k ? '' : k)}>{t('lb.type.' + k)}</button>
        {/if}
      {/each}
    </div>
    <input type="search" bind:value={search} placeholder={t('punish.search')} />
  </div>

  <Toast text={message} />
  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    {#if shown.length}
      <div class="list">
        {#each shown as e (e.id)}
          {@const st = stateOf(e)}
          <div class="row card t-{e.type}" class:off={!e.active}>
            <button class="who" onclick={() => go(`/admin/community/players/${e.uuid}`)}>
              <img src={head(e.uuid)} alt="" width="32" height="32" />
              <span class="name">{e.player}</span>
            </button>
            <span class="type t-{e.type}">{t('lb.type.' + e.type)}</span>
            <div class="what">
              <span class="reason">{e.reason || '—'}</span>
              <span class="muted small">
                {t('lb.by', { name: e.operator ?? t('lb.console') })} · <span title={dateTime(e.start)}>{ago(e.start)}</span>
                {#if e.type !== 'kick'}
                  · {e.end === 0 ? t('lb.permanent') : t('lb.until', { time: dateTime(e.end) })}
                {/if}
              </span>
            </div>
            <div class="side">
              {#if st}<span class="state s-{st}">{t('lb.state.' + st)}</span>{/if}
              {#if e.active && data.undo[e.type]}
                <button class="ghost" disabled={busy} onclick={() => undo(e)}>{t('lb.undo.' + e.type)}</button>
              {/if}
            </div>
          </div>
        {/each}
      </div>
    {:else}
      <p class="muted empty">{t(filter === 'active' ? 'punish.noneActive' : 'punish.none')}</p>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 14px;
  }

  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    padding: 18px 20px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    border-radius: 12px;
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 14%, transparent);
  }

  .title {
    flex: 1;
    min-width: 200px;
  }

  h1 {
    margin: 0;
    font-size: 1.35rem;
  }

  .title p {
    margin: 2px 0 0;
  }

  .numbers {
    display: flex;
    gap: 14px;
    flex-wrap: wrap;
  }

  .num b {
    font-size: 1.2rem;
  }

  .bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px 14px;
    padding: 12px 14px;
  }

  .bar input {
    flex: 1 1 200px;
    width: auto;
    padding: 7px 10px;
  }

  .seg {
    display: inline-flex;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg button {
    justify-content: flex-start;
    padding: 5px 12px;
    font-size: 0.86rem;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    border: 0;
  }

  .seg button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    justify-content: flex-start;
    padding: 4px 10px;
    font-size: 0.82rem;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .chip.on {
    border-color: var(--accent);
    color: var(--accent);
  }

  .list {
    display: grid;
    gap: 8px;
  }

  .row {
    display: grid;
    grid-template-columns: minmax(150px, 200px) 110px minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border-left: 4px solid var(--muted);
  }

  .row.t-ban {
    border-left-color: var(--bad);
  }

  .row.t-mute {
    border-left-color: var(--warn);
  }

  .row.t-warn {
    border-left-color: #eab308;
  }

  .row.off {
    opacity: 0.65;
    border-left-color: var(--border);
  }

  .who {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    min-width: 0;
    padding: 0;
    background: transparent;
    color: var(--text);
    border: 0;
  }

  .who img {
    border-radius: 6px;
  }

  .name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .type {
    justify-self: start;
    padding: 2px 9px;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    background: var(--surface-2);
  }

  .type.t-ban,
  .num.t-ban b {
    color: var(--bad);
  }

  .type.t-mute,
  .num.t-mute b {
    color: var(--warn);
  }

  .type.t-warn,
  .num.t-warn b {
    color: #eab308;
  }

  .what {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .reason {
    overflow-wrap: anywhere;
  }

  .side {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .state {
    font-size: 0.8rem;
    color: var(--muted);
  }

  .state.s-active {
    color: var(--good);
    font-weight: 600;
  }

  .ghost {
    justify-content: flex-start;
    padding: 5px 12px;
    font-size: 0.84rem;
    background: transparent;
    color: var(--text);
    border: 1px solid var(--border);
  }

  .empty {
    padding: 30px 0;
    text-align: center;
  }

  @media (max-width: 760px) {
    .row {
      grid-template-columns: minmax(0, 1fr) auto;
    }

    .what {
      grid-column: 1 / -1;
    }

    .side {
      grid-column: 1 / -1;
      justify-content: space-between;
    }
  }
</style>
