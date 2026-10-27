<script>
  // LibertyBans' part of a player's page: active punishments, history, and punishing - with LibertyBans'
  // permissions (do / undo per type, duration permissions if switched on there).
  // view "tile": only the status as a tile on the player's overview.
  import { untrack } from 'svelte';
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, duration } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import PartTile from './PartTile.svelte';

  let { uuid, me, name, onDone, view = 'full', onOpen } = $props();

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);
  let type = $state('');
  let reason = $state('');
  let showAll = $state(false);

  async function load() {
    try {
      data = await api('GET', `/players/${uuid}/libertybans`);
      error = '';
      // First type the member may use.
      if (!type || !data.may[type]?.do) type = TYPES.find((ty) => data.may[ty].do) ?? '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    uuid;
    untrack(load);
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
  });

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    try {
      await api('POST', `/players/${uuid}/libertybans`, body);
      onDone(t(body.action === 'undo' ? 'lb.done.undo' : 'lb.done.' + body.type));
      if (body.action === 'punish') reason = '';
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const TYPES = ['ban', 'mute', 'warn', 'kick'];
  const ICON = { ban: '⛔', mute: '🔇', warn: '⚠', kick: '👢' };
  const TONE = { ban: 'bad', mute: 'warn', warn: 'warn', kick: 'muted' };

  const active = $derived((data?.entries ?? []).filter((e) => e.active));
  const chips = $derived(data ? (active.length
    ? active.map((e) => ({ text: ICON[e.type] + ' ' + t('lb.active.' + e.type), tone: TONE[e.type] }))
    : [{ text: '✓ ' + t('lb.clean'), tone: 'good' }]) : []);
  const shown = $derived(showAll ? data?.entries ?? [] : (data?.entries ?? []).slice(0, 10));
  const canPunish = $derived(data && !data.self && TYPES.some((ty) => data.may[ty].do));
  const time = (d) => (d === 'perm' ? t('sleep.permanent') : d);

  // Status of a past entry: active / expired / lifted (LibertyBans tells "active" only; ended before now = expired).
  function status(e) {
    if (e.type === 'kick') return null;
    if (e.active) return { key: 'active', tone: TONE[e.type] };
    return e.end && e.end <= Date.now() ? { key: 'expired', tone: 'muted' } : { key: 'lifted', tone: 'muted' };
  }
</script>

{#if view === 'tile'}
  {@const until = (type) => { const e = active.find((x) => x.type === type); return !e ? '–' : e.end ? dateTime(e.end) : t('sleep.permanent'); }}
  <PartTile label={t('lb.tileLabel')} loading={!data} {error} {onOpen}
    value={!data ? '' : active.length ? String(active.length) : t('lb.tileNone')} small={data && !active.length}
    unit={data && active.length ? t('lb.tileActiveUnit') : ''} tone={data ? (active.length ? 'bad' : 'good') : ''}
    facts={data ? [
      { label: t('lb.type.ban'), value: until('ban'), tone: active.some((e) => e.type === 'ban') ? 'bad' : '' },
      { label: t('lb.type.mute'), value: until('mute'), tone: active.some((e) => e.type === 'mute') ? 'warn' : '' },
      { label: t('lb.history'), value: String(data.entries.length) },
    ] : []} />
{:else}
<section class="card part">
  <div class="phead">
    <h2>⚖ {t('lb.title')}</h2>
    {#if data}
      <div class="chips">
        {#each chips as c}<span class="tag {c.tone}">{c.text}</span>{/each}
      </div>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    {#if active.length}
      <ul class="list">
        {#each active as e (e.id)}
          <li class="entry {TONE[e.type]}">
            <div class="etop">
              <strong>{ICON[e.type]} {t('lb.type.' + e.type)} #{e.id}</strong>
              <span class="muted small">{e.end ? t('lb.until', { time: dateTime(e.end) }) : t('lb.permanent')}</span>
            </div>
            <div class="reason">{e.reason}</div>
            <div class="muted small">{t('lb.by', { name: e.operator ?? t('lb.console') })} · {dateTime(e.start)}</div>
            {#if data.may[e.type].undo}
              <button class="secondary mini" disabled={busy}
                onclick={() => act({ action: 'undo', type: e.type, id: String(e.id) }, t('lb.undoConfirm', { type: t('lb.type.' + e.type), name }))}>{t('lb.undo.' + e.type)}</button>
            {/if}
          </li>
        {/each}
      </ul>
    {/if}

    {#if canPunish}
      <div class="punish">
        <div class="glabel">{t('lb.punish')}</div>
        <div class="types" role="group">
          {#each TYPES.filter((ty) => data.may[ty].do) as ty}
            <button class:active={type === ty} onclick={() => (type = ty)}>{ICON[ty]} {t('lb.type.' + ty)}</button>
          {/each}
        </div>
        <input type="text" bind:value={reason} maxlength="200" placeholder={t('lb.reason')} />
        <div class="buttons">
          {#if type === 'kick'}
            {#if data.online}
              <button class="danger" disabled={busy}
                onclick={() => act({ action: 'punish', type, reason }, t('lb.confirmKick', { name }))}>{ICON.kick} {t('lb.kickNow')}</button>
            {:else}
              <p class="muted small">{t('lb.kickOffline')}</p>
            {/if}
          {:else if type}
            {#each data.may[type].durations as d}
              <button class="danger" disabled={busy}
                onclick={() => act({ action: 'punish', type, duration: d, reason },
                  t('lb.confirm', { type: t('lb.type.' + type), name, time: time(d) }))}>{ICON[type]} {time(d)}</button>
            {:else}
              <p class="muted small">{t('lb.noDurations')}</p>
            {/each}
          {/if}
        </div>
      </div>
    {/if}

    <h3>{t('lb.history')}</h3>
    {#if data.entries.length === 0}
      <p class="muted small">{t('lb.none')}</p>
    {:else}
      <ul class="list">
        {#each shown as e (e.id)}
          {@const st = status(e)}
          <li class="entry past">
            <div class="etop">
              <span>{ICON[e.type]} <strong>{t('lb.type.' + e.type)}</strong> <span class="muted">#{e.id}</span></span>
              {#if st}<span class="tag {st.tone}">{t('lb.state.' + st.key)}</span>{/if}
            </div>
            <div class="reason">{e.reason}</div>
            <div class="muted small">
              {#if e.operatorUuid && me.may.players}
                <button class="plink" onclick={() => go('/players/' + e.operatorUuid)}>{e.operator}</button>
              {:else}{e.operator ?? t('lb.console')}{/if}
              · {dateTime(e.start)}{#if e.type !== 'kick'} · {e.end ? duration(e.end - e.start) : t('lb.permanent')}{/if}
            </div>
          </li>
        {/each}
      </ul>
      {#if data.entries.length > 10}
        <button class="linkish" onclick={() => (showAll = !showAll)}>{showAll ? t('lb.less') : t('lb.all', { n: data.entries.length })}</button>
      {/if}
    {/if}
  {:else if !error}
    <p class="muted small">{t('common.loading')}</p>
  {/if}
</section>
{/if}

<style>
  .phead {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    align-items: center;
  }

  h2 {
    font-size: 1.05rem;
    margin: 0;
  }

  h3 {
    font-size: 0.9rem;
    margin: 18px 0 8px;
    color: var(--muted);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 14%, transparent);
  }

  .tag.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }

  .list {
    list-style: none;
    margin: 12px 0 0;
    padding: 0;
    display: grid;
    gap: 8px;
  }

  .entry {
    background: var(--surface-2);
    border-radius: 10px;
    padding: 10px 12px;
    border-left: 4px solid var(--border);
    min-width: 0;
  }

  .entry.bad {
    border-left-color: var(--bad);
  }

  .entry.warn {
    border-left-color: var(--warn);
  }

  .entry.past {
    border-left-color: transparent;
  }

  .etop {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    justify-content: space-between;
    align-items: baseline;
  }

  .reason {
    margin: 4px 0 2px;
    overflow-wrap: anywhere;
  }

  .mini {
    margin-top: 8px;
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .punish {
    margin-top: 16px;
    border-top: 1px solid var(--border);
    padding-top: 14px;
    display: grid;
    gap: 8px;
  }

  .glabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
  }

  .types {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .types button {
    background: var(--surface-2);
    color: var(--muted);
    border-color: var(--border);
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 0.88rem;
  }

  .types button.active {
    background: var(--accent);
    color: var(--accent-text);
    border-color: transparent;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .buttons p {
    margin: 0;
  }

  .plink,
  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 0;
    font-weight: 600;
  }

  .linkish {
    margin-top: 8px;
    font-size: 0.85rem;
  }
</style>
