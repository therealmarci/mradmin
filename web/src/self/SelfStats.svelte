<script>
  // The statistics page of the player area in two tabs, built the same way: "Personal" - every own value with the
  // place among all players and how far it is from the best - and "Server" - every value for all players together
  // with the first three and the own place; a click opens the whole ranking with a podium. Both as tiles or as a
  // table (remembered in the browser). /me/stats#server opens the second tab.
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { head } from '../lib/format.js';

  const VIEW_KEY = 'mradmin.stats.view';

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);
  let tab = $state(location.hash === '#server' ? 'server' : 'personal');
  let view = $state(stored());
  let opened = $state(null);

  function stored() {
    try {
      return localStorage.getItem(VIEW_KEY) === 'table' ? 'table' : 'tiles';
    } catch {
      return 'tiles';
    }
  }

  function setView(which) {
    view = which;
    try {
      localStorage.setItem(VIEW_KEY, which);
    } catch {
      // only for this visit then
    }
  }

  async function load() {
    try {
      data = await api('GET', '/self/stats?lang=' + lang);
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

  function show(which) {
    tab = which;
    history.replaceState(null, '', which === 'server' ? '/me/stats#server' : '/me/stats');
  }

  async function setHidden(hidden) {
    busy = true;
    try {
      await api('POST', '/self/stats/hide', { hidden: String(hidden) });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const ICONS = {
    'play-time': '⏱️', 'blocks-mined': '⛏️', 'mob-kills': '⚔️', 'xp-level': '✨', deaths: '💀',
    distance: '🧭', 'fish-caught': '🎣', jumps: '🦘', 'items-crafted': '🛠️',
  };
  // The game's statistics are named by the page ("game:play-time"), the plugins' bring their own title.
  const key = (id) => id.slice(id.indexOf(':') + 1);
  const game = (id) => id.startsWith('game:');
  const title = (r) => r.title ?? t('self.stats.' + key(r.id));
  const icon = (r) => (game(r.id) ? ICONS[key(r.id)] ?? '📊' : '🏆');
  const medal = (rank) => ['🥇', '🥈', '🥉'][rank - 1] ?? null;

  const gamePersonal = $derived((data?.personal ?? []).filter((p) => game(p.id)));
  const pluginPersonal = $derived((data?.personal ?? []).filter((p) => !game(p.id)));
  const gameRankings = $derived((data?.rankings ?? []).filter((r) => game(r.id)));
  const pluginRankings = $derived((data?.rankings ?? []).filter((r) => !game(r.id)));
  const totalOf = (id) => data?.totals.find((x) => x.id === id)?.value ?? null;
  // The viewer's place in a ranking: among the top places, or further down (own).
  const ownOf = (r) => r.places.find((p) => p.self) ?? r.own ?? null;
  const openRanking = $derived(opened ? data?.rankings.find((r) => r.id === opened) ?? null : null);
</script>

{#snippet statCard(p)}
  <article class="stat" class:top={p.rank > 0 && p.rank <= 3}>
    <div class="s-head">
      <span class="s-icon" aria-hidden="true">{icon(p)}</span>
      <span class="s-title">{title(p)}</span>
      {#if medal(p.rank)}<span class="medal" title={t('self.stats.place', { n: p.rank })}>{medal(p.rank)}</span>{/if}
    </div>
    <div class="s-value">{p.value}</div>
    <div class="s-rank">
      {#if p.rank > 0}
        {t('self.stats.placeOf', { n: p.rank, total: p.total })}
      {:else}
        <span class="muted">{t('self.stats.noPlace')}</span>
      {/if}
    </div>
    {#if p.share !== null && p.share !== undefined}
      <div class="bar" title={t('self.stats.ofBest')}><span style="width: {Math.max(2, p.share * 100)}%"></span></div>
    {/if}
    {#if p.best && p.rank !== 1}
      <div class="s-best muted">{t('self.stats.best', { name: p.bestName, value: p.best })}</div>
    {:else if p.rank === 1}
      <div class="s-best leader">{t('self.stats.leader')}</div>
    {/if}
  </article>
{/snippet}

{#snippet personalTable(list)}
  <div class="table-wrap">
    <table>
      <thead>
        <tr><th>{t('self.stats.colStat')}</th><th class="num">{t('self.stats.colValue')}</th><th>{t('self.stats.colPlace')}</th><th>{t('self.stats.colBest')}</th></tr>
      </thead>
      <tbody>
        {#each list as p (p.id)}
          <tr class:top={p.rank > 0 && p.rank <= 3}>
            <td><span aria-hidden="true">{icon(p)}</span> {title(p)}</td>
            <td class="num strong">{p.value}</td>
            <td>{#if p.rank > 0}{medal(p.rank) ?? ''} {t('self.stats.placeOf', { n: p.rank, total: p.total })}{:else}<span class="muted">–</span>{/if}</td>
            <td class="muted">{#if p.rank === 1}{t('self.stats.leader')}{:else if p.best}{p.bestName} ({p.best}){:else}–{/if}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/snippet}

{#snippet serverCard(r)}
  {@const total = totalOf(r.id)}
  {@const mine = ownOf(r)}
  <button class="stat server-card" onclick={() => (opened = r.id)}>
    <span class="s-head">
      <span class="s-icon" aria-hidden="true">{icon(r)}</span>
      <span class="s-title">{title(r)}</span>
    </span>
    {#if total !== null}
      <span class="s-value">{total}</span>
      <span class="s-rank muted">{t('self.stats.allTogether')}</span>
    {:else if r.places[0]}
      <span class="s-value">{r.places[0].value}</span>
      <span class="s-rank muted">{t('self.stats.topValue')}</span>
    {/if}
    {#if r.places.length}
      <span class="mini">
        {#each r.places.slice(0, 3) as p, i (p.uuid)}
          <span class="mini-row" class:self={p.self}>
            <span>{medal(i + 1)}</span>
            <img src={head(p.uuid)} alt="" width="18" height="18" />
            <span class="name">{p.name}</span>
            <b>{p.value}</b>
          </span>
        {/each}
      </span>
    {:else}
      <span class="muted small">{t('self.stats.empty')}</span>
    {/if}
    <span class="s-best">
      {#if mine}{t('self.stats.yourPlace', { n: mine.rank })}{:else}<span class="muted">{t('self.stats.noPlace')}</span>{/if}
      <span class="open-hint">{t('self.stats.wholeRanking')} →</span>
    </span>
  </button>
{/snippet}

{#snippet serverTable(list)}
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>{t('self.stats.colStat')}</th><th class="num">{t('self.stats.colTotal')}</th>
          <th>🥇</th><th>🥈</th><th>🥉</th><th>{t('self.stats.colYou')}</th>
        </tr>
      </thead>
      <tbody>
        {#each list as r (r.id)}
          {@const mine = ownOf(r)}
          <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions -->
          <tr class="clickable" onclick={() => (opened = r.id)}>
            <td><span aria-hidden="true">{icon(r)}</span> {title(r)}</td>
            <td class="num strong">{totalOf(r.id) ?? '–'}</td>
            {#each [0, 1, 2] as i}
              {@const p = r.places[i]}
              <td class:self-cell={p?.self}>{#if p}{p.name} <span class="muted">({p.value})</span>{:else}<span class="muted">–</span>{/if}</td>
            {/each}
            <td>{#if mine}{t('self.stats.place', { n: mine.rank })} <span class="muted">({mine.value})</span>{:else}<span class="muted">–</span>{/if}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/snippet}

<div class="page">
  <header class="head">
    <span class="watermark" aria-hidden="true">📊</span>
    <div class="intro">
      <span class="icon" aria-hidden="true">📊</span>
      <div>
        <h1>{t('self.stats.title')}</h1>
        <p class="muted">{t('self.stats.subtitle')}</p>
      </div>
    </div>
    <div class="controls">
      <div class="switch" role="tablist">
        <button role="tab" aria-selected={tab === 'personal'} class:active={tab === 'personal'} onclick={() => show('personal')}>
          🙂 {t('self.stats.tabPersonal')}
        </button>
        <button role="tab" aria-selected={tab === 'server'} class:active={tab === 'server'} onclick={() => show('server')}>
          🌍 {t('self.stats.tabServer')}
        </button>
      </div>
      <div class="switch small-switch" role="group" aria-label={t('self.stats.view')}>
        <button class:active={view === 'tiles'} aria-pressed={view === 'tiles'} onclick={() => setView('tiles')}>▦ {t('self.stats.viewTiles')}</button>
        <button class:active={view === 'table'} aria-pressed={view === 'table'} onclick={() => setView('table')}>☰ {t('self.stats.viewTable')}</button>
      </div>
    </div>
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    {#if tab === 'personal'}
      <section class="card tile">
        <h2>{t('self.stats.yourGame')}</h2>
        <p class="muted small sub">{t('self.stats.yourGameHint', { players: data.players })}</p>
        {#if view === 'tiles'}
          <div class="stats">{#each gamePersonal as p (p.id)}{@render statCard(p)}{/each}</div>
        {:else}
          {@render personalTable(gamePersonal)}
        {/if}
      </section>
      {#if pluginPersonal.length}
        <section class="card tile">
          <h2>{t('self.stats.yourPlugins')}</h2>
          {#if view === 'tiles'}
            <div class="stats">{#each pluginPersonal as p (p.id)}{@render statCard(p)}{/each}</div>
          {:else}
            {@render personalTable(pluginPersonal)}
          {/if}
        </section>
      {/if}
      {#if data.hidden}
        <p class="note muted small">🙈 {t('self.stats.hiddenNote')}</p>
      {/if}
    {:else}
      <section class="card tile">
        <h2>{t('self.stats.serverGame')}</h2>
        <p class="muted small sub">{t('self.stats.serverGameHint', { players: data.players })}</p>
        {#if gameRankings.length === 0}
          <p class="muted small">{t('self.stats.none')}</p>
        {:else if view === 'tiles'}
          <div class="stats">{#each gameRankings as r (r.id)}{@render serverCard(r)}{/each}</div>
        {:else}
          {@render serverTable(gameRankings)}
        {/if}
      </section>
      {#if pluginRankings.length}
        <section class="card tile">
          <h2>{t('self.stats.serverPlugins')}</h2>
          {#if view === 'tiles'}
            <div class="stats">{#each pluginRankings as r (r.id)}{@render serverCard(r)}{/each}</div>
          {:else}
            {@render serverTable(pluginRankings)}
          {/if}
        </section>
      {/if}

      <section class="card privacy">
        <span class="p-icon" aria-hidden="true">{data.hidden ? '🙈' : '👀'}</span>
        <div>
          <strong>{data.hidden ? t('self.stats.hiddenTitle') : t('self.stats.visibleTitle')}</strong>
          <p class="muted small">{data.hidden ? t('self.stats.hiddenText') : t('self.stats.visibleText')}</p>
        </div>
        <button class="secondary small" disabled={busy} onclick={() => setHidden(!data.hidden)}>
          {data.hidden ? t('self.stats.show') : t('self.stats.hide')}
        </button>
      </section>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<svelte:window onkeydown={(e) => openRanking && e.key === 'Escape' && (opened = null)} />

{#if openRanking}
  {@const r = openRanking}
  <div class="overlay" role="presentation" onclick={() => (opened = null)}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="card ranking dialog" role="dialog" aria-modal="true" aria-label={title(r)} tabindex="-1" onclick={(e) => e.stopPropagation()}>
      <div class="dialog-head">
        <h2><span aria-hidden="true">{icon(r)}</span> {title(r)}</h2>
        <button class="secondary close" aria-label={t('common.close')} onclick={() => (opened = null)}>✕</button>
      </div>
      {#if r.places.length === 0}
        <p class="muted small">{t('self.stats.empty')}</p>
      {:else}
        <div class="podium">
          {#each [1, 0, 2] as i}
            {@const p = r.places[i]}
            {#if p}
              <div class="step s{i + 1}" class:self={p.self}>
                <img src={head(p.uuid)} alt="" width="36" height="36" />
                <span class="p-name">{p.name}</span>
                <span class="p-value">{p.value}</span>
                <span class="block"><span>{medal(i + 1)}</span></span>
              </div>
            {:else}
              <div class="step empty-step"></div>
            {/if}
          {/each}
        </div>
        {#if r.places.length > 3 || r.own}
          <ol class="rest">
            {#each r.places.slice(3) as p (p.uuid)}
              <li class:self={p.self}>
                <span class="rank">{p.rank}</span>
                <img src={head(p.uuid)} alt="" width="22" height="22" />
                <span class="name">{p.name}</span>
                <strong>{p.value}</strong>
              </li>
            {/each}
            {#if r.own}
              <li class="gap" aria-hidden="true">⋯</li>
              <li class="self">
                <span class="rank">{r.own.rank}</span>
                <img src={head(r.own.uuid)} alt="" width="22" height="22" />
                <span class="name">{r.own.name}</span>
                <strong>{r.own.value}</strong>
              </li>
            {/if}
          </ol>
        {/if}
      {/if}
    </div>
  </div>
{/if}

<style>
  .page {
    --st: #8b5cf6;
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 16px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--st) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--st) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -10px;
    bottom: -34px;
    font-size: 9rem;
    opacity: 0.06;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    position: relative;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    font-size: 1.8rem;
    background: color-mix(in srgb, var(--st) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--st) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .switch {
    position: relative;
    display: inline-flex;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    background: transparent;
    color: var(--muted);
    padding: 8px 14px;
    border-radius: 9px;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .sub {
    margin: 4px 0 0;
  }

  .tile .stats {
    margin-top: 14px;
  }

  /* Personal: one tile per value */
  .stats {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 210px), 1fr));
  }

  .stat {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 14px 16px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .stat.top {
    border-color: color-mix(in srgb, #f59e0b 55%, var(--border));
    background: linear-gradient(160deg, color-mix(in srgb, #f59e0b 12%, var(--surface-2)), var(--surface-2) 60%);
  }

  .s-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .s-icon {
    font-size: 1.2rem;
  }

  .s-title {
    flex: 1;
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--muted);
  }

  .medal {
    font-size: 1.3rem;
  }

  .s-value {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.15;
    font-variant-numeric: tabular-nums;
  }

  .s-rank {
    font-size: 0.85rem;
    font-weight: 600;
  }

  .bar {
    height: 6px;
    border-radius: 3px;
    background: color-mix(in srgb, var(--border) 80%, transparent);
    overflow: hidden;
  }

  .bar span {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--st);
  }

  .s-best {
    font-size: 0.75rem;
    margin-top: auto;
  }

  .leader {
    color: #d97706;
    font-weight: 700;
  }

  .note {
    margin: 0;
    text-align: center;
  }

  .ranking {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  .podium {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    align-items: end;
    gap: 6px;
  }

  .step {
    display: grid;
    justify-items: center;
    gap: 3px;
    text-align: center;
    min-width: 0;
  }

  .step img {
    border-radius: 8px;
    image-rendering: pixelated;
  }

  .p-name {
    font-size: 0.82rem;
    font-weight: 700;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .p-value {
    font-size: 0.78rem;
    color: var(--muted);
    font-variant-numeric: tabular-nums;
  }

  .block {
    width: 100%;
    display: grid;
    place-items: start center;
    padding-top: 6px;
    border-radius: 8px 8px 0 0;
    font-size: 1.2rem;
  }

  .s1 .block {
    height: 64px;
    background: linear-gradient(180deg, color-mix(in srgb, #f59e0b 35%, var(--surface-2)), var(--surface-2));
  }

  .s2 .block {
    height: 46px;
    background: linear-gradient(180deg, color-mix(in srgb, #94a3b8 35%, var(--surface-2)), var(--surface-2));
  }

  .s3 .block {
    height: 34px;
    background: linear-gradient(180deg, color-mix(in srgb, #b45309 30%, var(--surface-2)), var(--surface-2));
  }

  .step.self .p-name {
    color: var(--st);
  }

  .rest {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 2px;
  }

  .rest li {
    display: grid;
    grid-template-columns: 2em 22px 1fr auto;
    gap: 8px;
    align-items: center;
    padding: 5px 8px;
    border-radius: 8px;
  }

  .rest li:nth-child(odd):not(.self):not(.gap) {
    background: color-mix(in srgb, var(--surface-2) 60%, transparent);
  }

  .rest li.self {
    background: color-mix(in srgb, var(--st) 14%, transparent);
    font-weight: 700;
  }

  .rest li.gap {
    display: block;
    text-align: center;
    color: var(--muted);
    padding: 0;
  }

  .rest img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .rank {
    color: var(--muted);
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .privacy {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
  }

  .p-icon {
    font-size: 1.6rem;
  }

  .privacy > div {
    flex: 1;
    min-width: 200px;
  }

  .privacy p {
    margin: 2px 0 0;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .switch {
      width: 100%;
    }

    .switch button {
      flex: 1;
    }
  }


  .controls {
    position: relative;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .small-switch button {
    padding: 6px 10px;
    font-size: 0.85rem;
  }

  /* Server: one card per value, a click opens the whole ranking */
  .server-card {
    width: 100%;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    justify-content: flex-start;
    align-items: stretch;
    cursor: pointer;
    transition: transform 0.15s, border-color 0.15s;
  }

  .server-card:hover {
    filter: none;
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--st) 50%, var(--border));
  }

  .mini {
    display: grid;
    gap: 3px;
    margin-top: 4px;
  }

  .mini-row {
    display: grid;
    grid-template-columns: 1.4em 18px 1fr auto;
    gap: 6px;
    align-items: center;
    font-size: 0.82rem;
  }

  .mini-row img {
    border-radius: 3px;
    image-rendering: pixelated;
  }

  .mini-row.self {
    color: var(--st);
    font-weight: 700;
  }

  .s-best {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    flex-wrap: wrap;
  }

  .open-hint {
    color: var(--st);
    font-weight: 700;
  }

  /* Table view */
  .table-wrap {
    margin-top: 14px;
    overflow-x: auto;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.92rem;
  }

  th {
    text-align: left;
    font-size: 0.78rem;
    font-weight: 700;
    color: var(--muted);
    padding: 8px 10px;
    border-bottom: 1px solid var(--border);
    white-space: nowrap;
  }

  td {
    padding: 9px 10px;
    border-bottom: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    white-space: nowrap;
  }

  .num {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .strong {
    font-weight: 800;
  }

  tr.top td:first-child {
    box-shadow: inset 3px 0 0 #f59e0b;
  }

  tr.clickable {
    cursor: pointer;
  }

  tr.clickable:hover td {
    background: color-mix(in srgb, var(--st) 7%, transparent);
  }

  .self-cell {
    color: var(--st);
    font-weight: 700;
  }

  /* The whole ranking in a window */
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 50%);
    overflow-y: auto;
  }

  .dialog {
    width: min(100%, 520px);
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    border-top: 4px solid var(--st);
  }

  .dialog-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }

  .close {
    padding: 4px 10px;
  }
</style>
