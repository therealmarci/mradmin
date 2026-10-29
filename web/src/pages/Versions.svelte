<script>
  // Versions of the MRtechnology plugins: installed and newest version, download into plugins/update/ (installed by
  // Paper on the next start) and the restart button with countdown. See VersionRoutes.java.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { ago, dateTime, duration } from '../lib/format.js';
  import { markdown } from '../lib/markdown.js';
  import { ICONS } from '../lib/icons.js';
  import Toast from '../components/Toast.svelte';

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
  const updates = $derived(data ? data.plugins.filter((p) => p.update && !p.staged) : []);
  const left = $derived(data && data.restart.endsAt > 0 ? Math.max(0, data.restart.endsAt - now) : 0);

  function status(p) {
    if (p.downloading) return { tone: 'info', text: t('versions.state.downloading') };
    if (p.staged) return { tone: 'info', text: t('versions.state.staged', { version: p.staged }) };
    if (p.update) return { tone: 'warn', text: t('versions.state.update') };
    if (p.error) return { tone: 'bad', text: t('versions.state.error') };
    if (!p.latest) return { tone: '', text: t('versions.state.unknown') };
    return { tone: 'good', text: t('versions.state.current') };
  }

  /** "4:09" or "1:05:09" */
  function clock(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
  }

  // Problems first: update, waiting for the restart, error, then the current ones by name.
  const order = { warn: 0, info: 1, bad: 2, '': 3, good: 4 };
  const plugins = $derived(data ? [...data.plugins].sort((a, b) => order[status(a).tone] - order[status(b).tone] || a.name.localeCompare(b.name)) : []);
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.box} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.box} /></svg>
      </span>
      <div>
        <h1>{t('versions.title')}</h1>
        <p class="muted">{t('versions.subtitle')}</p>
      </div>
    </div>
    {#if data}
      <div class="numbers">
        <span><b>{data.plugins.length}</b> {t('versions.plugins')}</span>
        {#if updates.length}<span class="warn-text"><b>{updates.length}</b> {t('versions.updates')}</span>{/if}
        {#if staged.length}<span class="info-text"><b>{staged.length}</b> {t('versions.waiting')}</span>{/if}
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if data}
    <!-- When it was checked, and checking now -->
    <div class="check-bar">
      <span class="check-state" class:busy={data.checking}>
        <span class="dot"></span>
        {data.checking ? t('versions.checking') : data.checkedAt ? t('versions.checkedAt', { time: ago(data.checkedAt) }) : t('versions.neverChecked')}
      </span>
      <span class="chip">{t('versions.auto.' + data.auto)}</span>
      <button class="secondary mini" disabled={busy || data.checking} onclick={() => act('/versions/check', {}, t('versions.checkStarted'))}>
        ⟳ {t('versions.check')}
      </button>
    </div>

    <!-- Restart: a running countdown, or - only after an update was downloaded - the restart buttons -->
    {#if data.restart.endsAt > 0}
      <section class="restart bad">
        <div class="r-main">
          <div class="r-text">
            <span class="status">{t('versions.restartTitle')}</span>
            <strong>{t('versions.restartBy', { name: data.restart.by })}</strong>
          </div>
          <div class="r-clock">
            <span class="label">{t('versions.restartCountdown')}</span>
            <span class="clock mono">{left > 0 ? clock(left) : t('versions.restartNow')}</span>
          </div>
        </div>
        {#if data.may.restart}
          <div class="r-actions">
            <button class="secondary" disabled={busy} onclick={() => act('/restart/cancel', {}, t('versions.restartCancelled'))}>{t('versions.restartCancel')}</button>
          </div>
        {/if}
      </section>
    {:else if data.may.restart && staged.length}
      <section class="restart warn">
        <div class="r-text">
          <span class="status">{t('versions.restartNeeded', { n: staged.length })}</span>
          <span class="muted small">{t('versions.restartHint.' + data.restart.method)}</span>
        </div>
        <div class="r-actions">
          {#each data.restart.delays as d}
            <button class="danger" disabled={busy}
              onclick={() => act('/restart', { delay: String(d) }, t('versions.restartStarted'), t('versions.restartConfirm', { time: delayText(d) }))}>{delayText(d)}</button>
          {/each}
        </div>
      </section>
    {:else if staged.length}
      <section class="restart warn">
        <div class="r-text">
          <span class="status">{t('versions.restartNeeded', { n: staged.length })}</span>
          <span class="muted small">{t('versions.restartManual')}</span>
        </div>
      </section>
    {/if}

    {#if data.plugins.length === 0}
      <p class="muted">{t('versions.none')}</p>
    {:else}
      <ul class="grid">
        {#each plugins as p (p.name)}
          {@const st = status(p)}
          <li class="plugin {st.tone}">
            <div class="p-top">
              <span class="status">{st.text}</span>
              <span class="chip">MC {p.minecraft}</span>
            </div>
            <div class="p-name">
              <strong>{p.name}</strong>
              <span class="versions mono">
                <span>{p.installed}</span>
                {#if p.latest && p.latest.version !== p.installed}
                  <span class="arrow">→</span><span class="new">{p.staged || p.latest.version}</span>
                {/if}
              </span>
            </div>
            <div class="facts">
              <div><span class="muted">{t('versions.latest')}</span>{p.latest ? p.latest.version : '–'}</div>
              <div><span class="muted">{t('versions.published')}</span>{p.latest?.published ? dateTime(p.latest.published) : '–'}</div>
              {#if p.mod}<div><span class="muted">{t('versions.mod')}</span>{p.mod.version}</div>{/if}
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
          </li>
        {/each}
      </ul>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --vs: var(--accent);
    display: grid;
    gap: 16px;
  }

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--vs) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--vs) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--vs);
    opacity: 0.08;
    transform: rotate(-12deg);
    pointer-events: none;
  }

  .intro {
    display: flex;
    gap: 16px;
    align-items: center;
    flex: 1;
    min-width: 240px;
  }

  .icon {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    flex-shrink: 0;
    border-radius: 14px;
    color: var(--vs);
    background: color-mix(in srgb, var(--vs) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--vs) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .numbers {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .numbers span {
    padding: 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    font-size: 0.88rem;
  }

  .numbers b {
    font-size: 1.1rem;
    margin-right: 2px;
  }

  .warn-text {
    color: var(--warn);
  }

  .info-text {
    color: var(--accent);
  }

  /* Colours of a state: current green, update yellow, waiting for the restart blue, error red */
  .good {
    --tn: var(--good);
  }

  .warn {
    --tn: var(--warn);
  }

  .info {
    --tn: var(--accent);
  }

  .bad {
    --tn: var(--bad);
  }

  .status {
    font-size: 0.72rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--tn, var(--muted));
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .label {
    color: var(--muted);
    font-size: 0.78rem;
    font-weight: 700;
  }

  /* Check */
  .check-bar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
  }

  .check-state {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 0.88rem;
    color: var(--muted);
    margin-right: auto;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--good);
  }

  .check-state.busy .dot {
    background: var(--accent);
    animation: pulse 1s ease-in-out infinite;
  }

  @keyframes pulse {
    50% {
      opacity: 0.3;
    }
  }

  /* Restart */
  .restart {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 14px 24px;
    padding: 18px 22px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 5px solid var(--tn, var(--muted));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tn, var(--muted)) 12%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .r-main {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 14px 24px;
    flex: 1;
  }

  .r-text {
    display: grid;
    gap: 4px;
  }

  .r-clock {
    display: grid;
    justify-items: end;
  }

  .clock {
    font-size: 2.2rem;
    font-weight: 800;
    line-height: 1.1;
    color: var(--tn);
  }

  .r-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  /* Plugins: four rows per card as subgrid, so the cards of a row line up. */
  .grid {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  .plugin {
    display: grid;
    grid-row: span 4;
    grid-template-rows: subgrid;
    row-gap: 10px;
    align-items: start;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 4px solid var(--tn, var(--muted));
    background: var(--surface);
    box-shadow: var(--shadow);
    min-width: 0;
  }

  .p-top {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .p-top .status {
    margin-right: auto;
  }

  .p-name {
    display: grid;
    gap: 2px;
  }

  .p-name strong {
    font-size: 1.15rem;
  }

  .versions {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 6px;
    font-size: 1.3rem;
    font-weight: 800;
  }

  .arrow {
    color: var(--muted);
    font-size: 1rem;
  }

  .new {
    color: var(--tn);
  }

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
    font-size: 0.9rem;
    font-weight: 700;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .facts .muted {
    display: block;
    font-size: 0.72rem;
    font-weight: 600;
    margin-bottom: 1px;
  }

  .actions {
    display: grid;
    gap: 8px;
    justify-items: start;
    padding-top: 10px;
    border-top: 1px solid var(--border);
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

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }

    .r-clock {
      justify-items: start;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .check-state.busy .dot {
      animation: none;
    }
  }
</style>
