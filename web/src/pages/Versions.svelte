<script>
  // Versions of the MRtechnology plugins: installed and newest version, download into plugins/update/ (installed by
  // Paper on the next start) and the restart button with countdown. See VersionRoutes.java.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, duration } from '../lib/format.js';
  import { markdown } from '../lib/markdown.js';

  let data = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let now = $state(Date.now());

  async function load() {
    try {
      data = await api('GET', '/versions');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  // Faster while something is going on (check, download, countdown), otherwise every 30 seconds.
  const active = $derived(!!data && (data.checking || data.restart.endsAt > 0 || data.plugins.some((p) => p.downloading)));
  $effect(() => {
    load();
  });
  $effect(() => {
    const timer = setInterval(load, active ? 2000 : 30000);
    return () => clearInterval(timer);
  });
  $effect(() => {
    const timer = setInterval(() => (now = Date.now()), 1000);
    return () => clearInterval(timer);
  });

  async function act(path, body, done, confirmText) {
    if (confirmText && !confirm(confirmText)) return;
    busy = true;
    error = '';
    message = '';
    try {
      await api('POST', path, body);
      if (done) message = done;
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const delayText = (ms) => (ms === 0 ? t('versions.restartNow') : t('versions.restartIn', { time: duration(ms) }));
  const staged = $derived(data ? data.plugins.filter((p) => p.staged) : []);
  const left = $derived(data && data.restart.endsAt > 0 ? Math.max(0, data.restart.endsAt - now) : 0);

  function status(p) {
    if (p.downloading) return { tone: 'info', text: t('versions.state.downloading') };
    if (p.staged) return { tone: 'info', text: t('versions.state.staged', { version: p.staged }) };
    if (p.update) return { tone: 'warn', text: t('versions.state.update') };
    if (p.error) return { tone: 'bad', text: t('versions.state.error') };
    if (!p.latest) return { tone: '', text: t('versions.state.unknown') };
    return { tone: 'good', text: t('versions.state.current') };
  }
</script>

<div class="stack">
  <div class="head">
    <div>
      <h1>{t('versions.title')}</h1>
      <p class="muted small">{t('versions.subtitle')}</p>
    </div>
    {#if data}
      <div class="check">
        <span class="muted small">
          {data.checking ? t('versions.checking') : data.checkedAt ? t('versions.checkedAt', { time: ago(data.checkedAt) }) : t('versions.neverChecked')}
          · {t('versions.auto.' + data.auto)}
        </span>
        <button class="secondary mini" disabled={busy || data.checking} onclick={() => act('/versions/check', {}, t('versions.checkStarted'))}>
          ⟳ {t('versions.check')}
        </button>
      </div>
    {/if}
  </div>
  {#if error}<div class="error">{error}</div>{/if}
  {#if message}<div class="notice">{message}</div>{/if}

  {#if data}
    {#if data.restart.endsAt > 0}
      <section class="card restart running">
        <div>
          <strong>⟳ {t('versions.restartRunning', { time: left > 0 ? duration(left) : t('versions.restartNow') })}</strong>
          <div class="muted small">{t('versions.restartBy', { name: data.restart.by })}</div>
        </div>
        {#if data.may.restart}
          <button class="secondary" disabled={busy} onclick={() => act('/restart/cancel', {}, t('versions.restartCancelled'))}>{t('versions.restartCancel')}</button>
        {/if}
      </section>
    {:else if data.may.restart}
      <section class="card restart" class:needed={staged.length}>
        <div>
          <strong>{staged.length ? t('versions.restartNeeded', { n: staged.length }) : t('versions.restartTitle')}</strong>
          <div class="muted small">{t('versions.restartHint.' + data.restart.method)}</div>
        </div>
        <div class="buttons">
          {#each data.restart.delays as d}
            <button class="danger" disabled={busy}
              onclick={() => act('/restart', { delay: String(d) }, t('versions.restartStarted'), t('versions.restartConfirm', { time: delayText(d) }))}>{delayText(d)}</button>
          {/each}
        </div>
      </section>
    {:else if staged.length}
      <div class="notice">{t('versions.restartNeeded', { n: staged.length })}</div>
    {/if}

    {#if data.plugins.length === 0}
      <p class="muted">{t('versions.none')}</p>
    {:else}
      <div class="grid">
        {#each data.plugins as p (p.name)}
          {@const st = status(p)}
          <section class="card tile">
            <div class="label">{p.name}</div>
            <div class="big">{p.installed}<span class="tag {st.tone}">{st.text}</span></div>
            <div class="facts">
              <div><span class="muted">{t('versions.latest')}</span>{p.latest ? p.latest.version : '–'}</div>
              <div><span class="muted">Minecraft</span>{p.minecraft}</div>
              <div><span class="muted">{t('versions.published')}</span>{p.latest?.published ? dateTime(p.latest.published) : '–'}</div>
              {#if p.mod}
                <div><span class="muted">{t('versions.mod')}</span>{p.mod.version}</div>
              {/if}
            </div>
            <div class="actions">
              {#if p.error}<div class="error small">{p.error === 'no-release' ? t('versions.noRelease') : p.error}</div>{/if}
              {#if p.update && data.may.install}
                {#if p.checksum}
                  <button disabled={busy || p.downloading}
                    onclick={() => act(`/versions/${encodeURIComponent(p.name)}/download`, {}, t('versions.downloadStarted', { name: p.name }),
                      t('versions.downloadConfirm', { name: p.name, version: p.latest.version }))}>
                    ⬇ {t('versions.download', { version: p.latest.version })}
                  </button>
                {:else}
                  <p class="muted small">{t('versions.noChecksum')}</p>
                {/if}
              {/if}
              {#if p.latest?.notes && (p.update || p.staged)}
                <details>
                  <summary>{t('versions.notes', { version: p.latest.version })}</summary>
                  <div class="notes">{@html markdown(p.latest.notes)}</div>
                </details>
              {/if}
              <div class="links small">
                <a href={p.website} target="_blank" rel="noopener noreferrer">{t('versions.website')} ↗</a>
                {#if p.latest}<a href={p.latest.page} target="_blank" rel="noopener noreferrer">GitHub ↗</a>{/if}
              </div>
            </div>
          </section>
        {/each}
      </div>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .head {
    display: flex;
    flex-wrap: wrap;
    gap: 10px 16px;
    justify-content: space-between;
    align-items: flex-end;
  }

  .head h1 {
    margin: 0;
  }

  .head p {
    margin: 4px 0 0;
  }

  .check {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .restart {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 20px;
    align-items: center;
    justify-content: space-between;
  }

  .restart.needed {
    border-color: var(--warn);
  }

  .restart.running {
    border-color: var(--bad);
    background: color-mix(in srgb, var(--bad) 6%, var(--surface));
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  /* Like the home page: four rows per tile as subgrid, so tiles in a row line up. */
  .grid {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  }

  .tile {
    display: grid;
    grid-row: span 4;
    grid-template-rows: subgrid;
    row-gap: 8px;
    align-content: start;
    align-items: start;
  }

  .label {
    color: var(--muted);
    font-size: 0.85rem;
    font-weight: 600;
  }

  .big {
    font-size: 1.8rem;
    font-weight: 800;
    line-height: 1.1;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px 10px;
  }

  .tag {
    font-size: 0.75rem;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--surface-2);
    color: var(--muted);
  }

  .tag.good { color: var(--good); background: color-mix(in srgb, var(--good) 14%, transparent); }
  .tag.warn { color: var(--warn); background: color-mix(in srgb, var(--warn) 16%, transparent); }
  .tag.bad { color: var(--bad); background: color-mix(in srgb, var(--bad) 14%, transparent); }
  .tag.info { color: var(--accent); background: color-mix(in srgb, var(--accent) 14%, transparent); }

  .facts {
    display: grid;
    gap: 6px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 110px), 1fr));
  }

  .facts > div {
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
    border-radius: 8px;
    padding: 6px 10px;
    font-size: 0.92rem;
    font-weight: 700;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .facts .muted {
    display: block;
    font-size: 0.74rem;
    font-weight: 600;
    margin-bottom: 1px;
  }

  .actions {
    display: grid;
    gap: 8px;
    justify-items: start;
  }

  details {
    width: 100%;
  }

  summary {
    cursor: pointer;
    color: var(--accent);
    font-size: 0.88rem;
    font-weight: 600;
  }

  .notes {
    max-height: 320px;
    overflow: auto;
    font-size: 0.88rem;
    margin-top: 6px;
    padding: 8px 12px;
    background: var(--surface-2);
    border-radius: 8px;
  }

  .links {
    display: flex;
    gap: 14px;
  }

  .links a {
    color: var(--accent);
  }
</style>
