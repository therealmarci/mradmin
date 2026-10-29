<script>
  // The statistics page of the player area in two tabs: "Personal" - every own value with the place among all players
  // and how far it is from the best - and "Server" - what all players did together, the rankings with a podium for the
  // first three, and hiding oneself from them. /me/stats#server opens the second tab.
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { head } from '../lib/format.js';

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);
  let tab = $state(location.hash === '#server' ? 'server' : 'personal');

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
  const podiums = $derived(
    (data?.rankings ?? []).map((r) => ({ ...r, podium: r.places.slice(0, 3), rest: r.places.slice(3) })),
  );
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
    <div class="switch" role="tablist">
      <button role="tab" aria-selected={tab === 'personal'} class:active={tab === 'personal'} onclick={() => show('personal')}>
        🙂 {t('self.stats.tabPersonal')}
      </button>
      <button role="tab" aria-selected={tab === 'server'} class:active={tab === 'server'} onclick={() => show('server')}>
        🌍 {t('self.stats.tabServer')}
      </button>
    </div>
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if data}
    {#if tab === 'personal'}
      <section class="card tile">
        <h2>{t('self.stats.yourGame')}</h2>
        <p class="muted small sub">{t('self.stats.yourGameHint', { players: data.players })}</p>
        <div class="stats">
          {#each gamePersonal as p (p.id)}{@render statCard(p)}{/each}
        </div>
      </section>
      {#if pluginPersonal.length}
        <section class="card tile">
          <h2>{t('self.stats.yourPlugins')}</h2>
          <div class="stats">
            {#each pluginPersonal as p (p.id)}{@render statCard(p)}{/each}
          </div>
        </section>
      {/if}
      {#if data.hidden}
        <p class="note muted small">🙈 {t('self.stats.hiddenNote')}</p>
      {/if}
    {:else}
      <section class="card tile">
        <h2>{t('self.stats.together')}</h2>
        <div class="totals">
          <div class="total main">
            <span class="t-icon" aria-hidden="true">👥</span>
            <span class="t-value">{data.players}</span>
            <span class="muted small">{t('self.stats.players')}</span>
          </div>
          {#each data.totals as x (x.id)}
            <div class="total">
              <span class="t-icon" aria-hidden="true">{ICONS[key(x.id)] ?? '📊'}</span>
              <span class="t-value">{x.value}</span>
              <span class="muted small">{t('self.stats.' + key(x.id))}</span>
            </div>
          {/each}
        </div>
      </section>

      {#if podiums.length === 0}
        <p class="muted">{t('self.stats.none')}</p>
      {/if}
      <div class="rankings">
        {#each podiums as r (r.id)}
          <section class="card ranking">
            <h2><span aria-hidden="true">{icon(r)}</span> {title(r)}</h2>
            {#if r.places.length === 0}
              <p class="muted small">{t('self.stats.empty')}</p>
            {:else}
              <div class="podium">
                {#each [1, 0, 2] as i}
                  {@const p = r.podium[i]}
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
              {#if r.rest.length || r.own}
                <ol class="rest">
                  {#each r.rest as p (p.uuid)}
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
          </section>
        {/each}
      </div>

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

  .tile .stats,
  .totals {
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

  /* Server: totals */
  .totals {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .total {
    display: grid;
    justify-items: start;
    gap: 2px;
    padding: 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .total.main {
    background: color-mix(in srgb, var(--st) 10%, var(--surface-2));
    border-color: color-mix(in srgb, var(--st) 35%, var(--border));
  }

  .t-icon {
    font-size: 1.2rem;
  }

  .t-value {
    font-size: 1.35rem;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
  }

  /* Server: rankings with a podium */
  .rankings {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 330px), 1fr));
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
</style>
