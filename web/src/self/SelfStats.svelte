<script>
  // Statistics page: rankings of the game's statistics and of the plugins (e.g. duels won), the own place
  // highlighted. Players can hide themselves from all rankings.
  import { api } from '../lib/api.js';
  import { t, lang, errorText } from '../lib/i18n.js';
  import { head } from '../lib/format.js';

  let data = $state(null);
  let error = $state('');
  let busy = $state(false);

  async function load() {
    try {
      data = await api('GET', '/self/stats?lang=' + lang);
    } catch (e) {
      error = errorText(e);
    }
  }
  load();

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

  // The game's statistics are named by the page ("game:play-time"), the plugins' bring their own title.
  const title = (r) => r.title ?? t('self.stats.' + r.id.slice(r.id.indexOf(':') + 1));
</script>

<div class="page">
  <h1>📊 {t('self.stats.title')}</h1>
  {#if error}<div class="error">{error}</div>{/if}
  {#if data}
    <section class="card privacy">
      <div>
        <strong>{data.hidden ? t('self.stats.hiddenTitle') : t('self.stats.visibleTitle')}</strong>
        <p class="muted small">{data.hidden ? t('self.stats.hiddenText') : t('self.stats.visibleText')}</p>
      </div>
      <button class="secondary small" disabled={busy} onclick={() => setHidden(!data.hidden)}>
        {data.hidden ? t('self.stats.show') : t('self.stats.hide')}
      </button>
    </section>

    {#if data.rankings.length === 0}
      <p class="muted">{t('self.stats.none')}</p>
    {/if}
    <div class="grid">
      {#each data.rankings as r (r.id)}
        <section class="card">
          <h2>{title(r)}</h2>
          {#if r.places.length === 0}
            <p class="muted small">{t('self.stats.empty')}</p>
          {:else}
            <ol>
              {#each r.places as p (p.uuid)}
                <li class:self={p.self}>
                  <span class="rank">{p.rank}</span>
                  <img src={head(p.uuid)} alt="" width="22" height="22" />
                  <span class="name">{p.name}</span>
                  <strong>{p.value}</strong>
                </li>
              {/each}
              {#if r.own}
                <li class="gap" aria-hidden="true">…</li>
                <li class="self">
                  <span class="rank">{r.own.rank}</span>
                  <img src={head(r.own.uuid)} alt="" width="22" height="22" />
                  <span class="name">{r.own.name}</span>
                  <strong>{r.own.value}</strong>
                </li>
              {/if}
            </ol>
          {/if}
        </section>
      {/each}
    </div>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    display: grid;
    gap: 16px;
  }

  h1 {
    margin: 0;
    font-size: 1.4rem;
  }

  h2 {
    margin: 0 0 10px;
    font-size: 1rem;
  }

  .privacy {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .privacy p {
    margin: 4px 0 0;
  }

  .grid {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  ol {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 2px;
  }

  li {
    display: grid;
    grid-template-columns: 2em 22px 1fr auto;
    gap: 8px;
    align-items: center;
    padding: 5px 8px;
    border-radius: 8px;
  }

  li.self {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    font-weight: 700;
  }

  li.gap {
    display: block;
    text-align: center;
    color: var(--muted);
    padding: 0;
  }

  li img {
    border-radius: 4px;
    image-rendering: pixelated;
  }

  .rank {
    color: var(--muted);
    text-align: right;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>
