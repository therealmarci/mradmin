<script>
  // MRchunks' part of a player's page: their loaders, limits, chunk loader ban, invites, loaders shared with them,
  // linked accounts and dummies - with MRchunks' permissions (limit, ban, manage).
  // view "tile": only the status as a tile on the player's overview.
  import { untrack } from 'svelte';
  import { playerAction, playerPart } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime, duration, head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { STATUS } from '../lib/chunks.js';
  import PartTile from './PartTile.svelte';

  let { uuid, me, name, onDone, view = 'full', onOpen } = $props();

  let c = $state(null);
  let error = $state('');
  let busy = $state(false);
  let reason = $state('');
  // The limit being edited ("place" / "active" / "dummy") and the value typed.
  let editing = $state(null);
  let value = $state('');
  // Name typed for linking another account.
  let other = $state('');

  async function load() {
    try {
      c = await playerPart(uuid, 'mrchunks');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    uuid;
    untrack(load);
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  });

  async function act(body, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    try {
      await playerAction(uuid, 'mrchunks', body);
      onDone(t('chunks.done.' + body.action));
      if (body.action === 'ban') reason = '';
      if (body.action === 'link') other = '';
      editing = null;
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  function edit(kind, limit) {
    editing = kind;
    value = limit.custom ?? (limit.value >= 0 ? String(limit.value) : '');
  }

  const limitText = (l) => (l.value < 0 ? '∞' : String(l.value));
  const time = (ms) => (ms === -1 ? t('sleep.permanent') : duration(ms));
  // MRchunks with chunk budget: loaders placed (fixed maximum for everyone) and the budget in chunks.
  // Older MRchunks: place and active limit, both editable.
  const budget = $derived(c?.budget != null);
  const limits = $derived(c ? [
    ...(budget ? [
      { kind: 'place', label: t('chunks.limitPlace'), used: c.loaders.length, limit: { value: c.maxLoaders, source: 'fixed', custom: null }, fixed: true },
      { kind: 'budget', label: t('chunks.limitBudget'), used: c.budgetUsed, limit: c.budget, unit: t('chunks.chunksUnit') },
    ] : [
      { kind: 'place', label: t('chunks.limitPlace'), used: c.loaders.length, limit: c.limit },
      { kind: 'active', label: t('chunks.limitActive'), used: c.active, limit: c.activeLimit },
    ]),
    ...(c.dummiesEnabled ? [{ kind: 'dummy', label: t('chunks.limitDummy'), used: c.dummies, limit: c.dummyLimit }] : []),
  ] : []);
  const chips = $derived(c ? [
    ...(c.exempt ? [{ text: t('chunks.exempt'), tone: '' }] : []),
    ...(c.ban ? [{ text: '✕ ' + (c.ban.permanent ? t('sleep.bannedPermanent') : t('sleep.bannedUntil', { time: dateTime(c.ban.until) })), tone: 'bad' }] : []),  ] : []);
</script>

{#if view === 'tile'}
  <PartTile label={t('nav.chunks')} icon="grid" loading={!c} {error} {onOpen} tone={c?.ban ? 'bad' : ''}
    value={c ? String(c.loaders.length) : ''} unit={c ? t('chunks.tileUnit', { max: limitText(c.limit) }) : ''}
    facts={c ? [
      budget
        ? { label: t('chunks.tileBudget'), value: c.budgetUsed + ' / ' + limitText(c.budget) }
        : { label: t('chunks.tileActive'), value: c.active + ' / ' + limitText(c.activeLimit) },
      { label: t('chunks.tileInvited'), value: String(c.shared.length) },
      { label: t('player.banFact'), value: !c.ban ? (c.exempt ? t('chunks.exempt') : '–') : c.ban.permanent ? t('sleep.permanent') : dateTime(c.ban.until), tone: c.ban ? 'bad' : '' },
    ] : []} />
{:else}
<section class="card part">
  <div class="phead">
    <h2>🧱 {t('nav.chunks')}</h2>
    {#if c}
      <div class="chips">
        {#each chips as ch}<span class="tag {ch.tone}">{ch.text}</span>{/each}
      </div>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}

  {#if c}
    <div class="limits">
      {#each limits as l (l.kind)}
        <div class="fact">
          <div class="flabel">{l.label}</div>
          <div class="fvalue">{l.used} / {limitText(l.limit)}{#if l.unit} <span class="muted tiny">{l.unit}</span>{/if}</div>
          <div class="muted tiny">{t('chunks.source.' + l.limit.source)}</div>
          {#if me.may.mrchunksLimit && !l.fixed}
            {#if editing === l.kind}
              <div class="edit">
                <input type="number" min="0" max={l.kind === 'budget' ? 100000 : 1000} bind:value aria-label={l.label} />
                <button disabled={busy || value === '' || value == null} onclick={() => act({ action: 'limit', kind: l.kind, value: String(value) })}>{t('chunks.limitSave')}</button>
              </div>
              <div class="edit-links">
                {#if l.limit.custom !== null}
                  <button class="linkish" disabled={busy} onclick={() => act({ action: 'limit', kind: l.kind, value: '' })}>{t('chunks.limitReset')}</button>
                {/if}
                <button class="linkish" onclick={() => (editing = null)}>{t('common.cancel')}</button>
              </div>
            {:else}
              <button class="linkish" onclick={() => edit(l.kind, l.limit)}>{t('chunks.limitChange')}</button>
            {/if}
          {/if}
        </div>
      {/each}
    </div>
    {#if !c.online}
      <p class="muted tiny">{budget ? t('chunks.budgetOffline', { budget: c.defaultBudget }) : t('chunks.limitOffline', { place: c.defaultLimit, active: c.defaultActiveLimit })}</p>
    {/if}

    {@const canLink = (me.may.mrchunksLink || me.may.mrchunksManage) && c.linksEnabled && !c.main && c.secondAccounts.length < c.maxLinks}
    {#if c.main || c.secondAccounts?.length || canLink}
      <h3>{t('chunks.linkedSection')}</h3>
      {#if c.main}
        <p class="small">
          {t('chunks.secondOf', { name: c.main.name })}
          {#if me.may.players}<button class="linkish" onclick={() => go('/players/' + c.main.uuid)}>{c.main.name} →</button>{/if}
        </p>
      {:else}
        <p class="small muted">{t('chunks.secondAccounts')}</p>
        <ul class="people">
          {#each c.secondAccounts as s (s.uuid)}
            <li>
              <button class="link-row" onclick={() => go('/players/' + s.uuid)} disabled={!me.may.players}>
                <img src={head(s.uuid)} alt="" width="20" height="20" /> {s.name}
              </button>
            </li>
          {/each}
        </ul>
      {/if}
      {#if (me.may.mrchunksLink || me.may.mrchunksManage) && (c.main || c.secondAccounts.length)}
        <button class="secondary" disabled={busy}
          onclick={() => act({ action: 'unlink' }, t('chunks.unlinkConfirm', { name }))}>{t('chunks.unlink')}</button>
      {/if}
      {#if canLink}
        <div class="link-form">
          <p class="muted tiny">{t('chunks.linkHint')}</p>
          <input type="text" bind:value={other} maxlength="16" placeholder={t('chunks.linkPlaceholder')} aria-label={t('chunks.linkPlaceholder')} />
          <div class="buttons">
            <button class="secondary" disabled={busy || !other.trim()}
              onclick={() => act({ action: 'link', other: other.trim(), role: 'second' }, t('chunks.linkSecondConfirm', { name, other: other.trim() }))}>{t('chunks.linkSecond')}</button>
            {#if !c.secondAccounts.length}
              <button class="secondary" disabled={busy || !other.trim()}
                onclick={() => act({ action: 'link', other: other.trim(), role: 'main' }, t('chunks.linkMainConfirm', { name, other: other.trim() }))}>{t('chunks.linkMain')}</button>
            {/if}
          </div>
        </div>
      {/if}
    {/if}

    <h3>{t('chunks.ownLoaders')}</h3>
    {#if c.loaders.length === 0}
      <p class="muted small">{t('chunks.noLoaders')}</p>
    {:else}
      <ul class="loaders">
        {#each c.loaders as l (l.id)}
          {@const st = STATUS[l.status]}
          <li>
            <button class="link-row" onclick={() => go('/chunks/' + l.id)} disabled={!me.may.mrchunks}>
              <span class="badge-status {st.tone}">{#if st.icon}{st.icon} {/if}{t('chunks.status.' + l.status)}</span>
              <strong>{l.name}</strong>
              <span class="muted small mono">{l.world} {l.x} {l.y} {l.z}</span>
              {#if l.width}<span class="tag">{l.width}×{l.length}</span>{/if}
              {#if l.dummyPresent}<span class="tag">🧍 {t('chunks.dummyBadge')}</span>{/if}
            </button>
          </li>
        {/each}
      </ul>
    {/if}

    {#if c.members.length}
      <h3>{t('chunks.invitedAll')}</h3>
      <ul class="people">
        {#each c.members as m (m.uuid)}
          <li>
            <button class="link-row" onclick={() => go('/players/' + m.uuid)} disabled={!me.may.players}>
              <img src={head(m.uuid)} alt="" width="20" height="20" /> {m.name}
              <span class="muted small">{m.until ? t('chunks.until', { time: dateTime(m.until) }) : t('chunks.permanent')}</span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}

    {#if c.shared.length}
      <h3>{t('chunks.sharedWith')}</h3>
      <ul class="loaders">
        {#each c.shared as l (l.id)}
          <li>
            <button class="link-row" onclick={() => go('/chunks/' + l.id)} disabled={!me.may.mrchunks}>
              <strong>{l.name}</strong> <span class="muted small">{t('chunks.of', { name: l.ownerName })}</span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}

    {#if me.may.mrchunksBan || (me.may.mrchunksManage && c.loaders.length)}
      <div class="groups">
        {#if me.may.mrchunksBan}
          <div class="group">
            <div class="glabel">{t('chunks.banSection')}</div>
            {#if c.ban}
              <p class="small"><span class="muted">{t('sleep.banReason')}:</span> {c.ban.reason} · <span class="muted">{t('sleep.banBy')}:</span> {c.ban.by}</p>
              <button class="secondary" disabled={busy} onclick={() => act({ action: 'unban' })}>{t('sleep.unban')}</button>
            {:else}
              <p class="muted tiny">{t('chunks.banHint')}</p>
              <input type="text" bind:value={reason} maxlength="200" placeholder={t('sleep.reasonPlaceholder')} />
              <div class="buttons">
                {#each c.banDurations as d}
                  <button class="danger" disabled={busy}
                    onclick={() => act({ action: 'ban', value: String(d), reason }, t('chunks.banConfirm', { name, time: time(d), n: c.loaders.length }))}>{t('sleep.banFor', { time: time(d) })}</button>
                {/each}
              </div>
            {/if}
          </div>
        {/if}
        {#if me.may.mrchunksManage && c.loaders.length}
          <div class="group">
            <div class="glabel">{t('chunks.deleteAllSection')}</div>
            <button class="danger" disabled={busy}
              onclick={() => act({ action: 'deleteAll' }, t('chunks.deleteAllConfirm', { name, n: c.loaders.length }))}>{t('chunks.deleteAll', { n: c.loaders.length })}</button>
          </div>
        {/if}
      </div>
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
    margin: 16px 0 8px;
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

  .tag.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 14%, transparent);
  }
  .limits {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
    margin-top: 14px;
  }

  .fact {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 600;
    margin-bottom: 4px;
  }

  .fvalue {
    font-size: 1.3rem;
    font-weight: 700;
  }

  .tiny {
    font-size: 0.75rem;
  }

  p.tiny {
    margin: 6px 0 0;
  }

  .edit {
    display: flex;
    gap: 6px;
    margin-top: 8px;
  }

  .edit input {
    width: 6em;
  }

  .edit-links {
    display: flex;
    gap: 12px;
  }

  .link-form {
    display: grid;
    gap: 6px;
    margin-top: 10px;
    max-width: 420px;
  }

  .linkish {
    background: transparent;
    color: var(--accent);
    padding: 4px 0;
    font-size: 0.85rem;
  }

  .loaders,
  .people {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .link-row {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    align-items: center;
    justify-content: flex-start;
    width: 100%;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    font-weight: 400;
    padding: 8px 12px;
  }

  .link-row:disabled {
    opacity: 1;
  }

  .link-row img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .badge-status {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--border);
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

  .groups {
    display: grid;
    gap: 14px;
    margin-top: 16px;
    border-top: 1px solid var(--border);
    padding-top: 14px;
  }

  .group p {
    margin: 0 0 6px;
  }

  .glabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
    margin-bottom: 6px;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
  }
</style>
