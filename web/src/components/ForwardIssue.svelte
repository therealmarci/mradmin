<script>
  // "Forward to MRtechnology" (since 1.7): opens the bug form of the chosen plugin on GitHub, filled with the installed
  // version, the server version and the whole conversation of the ticket (the repositories are public). Sent with the
  // own GitHub account - MRadmin needs no token. Afterwards the address of the issue is linked to the ticket.
  //
  // ticket: the ticket (detail of /api/tickets/{id}); onclose(); onlinked(url) after linking.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { brand } from '../lib/branding.svelte.js';

  let { ticket, onclose, onlinked } = $props();

  // The plugins of MRtechnology with their repository (like versions/Catalog.java).
  const PLUGINS = [
    ['MRadmin', 'therealmarci/mradmin'], ['MRchunks', 'therealmarci/mrchunks'], ['MRpvp', 'therealmarci/mrpvp'],
    ['MRsleep', 'therealmarci/mrsleep'], ['MRtoolbar', 'therealmarci/mrtoolbar'], ['MRwaypoints', 'therealmarci/mrwaypoints'],
    ['MRwaitroom', 'therealmarci/mrwaitroom'],
  ];
  // GitHub opens long addresses only up to a limit - the conversation is shortened to fit.
  const MAX_URL = 7800;

  let plugin = $state('MRadmin');
  let versions = $state({});
  let server = $state('');
  let title = $state(ticket.summary || ticket.categoryName || '');
  let steps = $state('');
  let what = $state(conversation());
  let link = $state(ticket.issue ?? '');
  let opened = $state(false);
  let busy = $state(false);
  let error = $state('');

  // Installed versions and the Paper version (without the permission for the plugin page the fields stay empty).
  api('GET', '/versions').then((v) => {
    versions = Object.fromEntries((v.plugins ?? []).filter((p) => p.installed).map((p) => [p.name, p.installed]));
    const paper = v.restart?.paper;
    if (paper?.build) server = `Paper ${paper.minecraft} #${paper.build}`;
    // a plugin from the ticket's source as a start (e.g. MRchunks for an automatic lag ticket)
    if (ticket.source && PLUGINS.some(([n]) => n === ticket.source)) plugin = ticket.source;
  }).catch(() => {});

  // The log of the ticket, sealed for MRtechnology: a link in the issue (only with web.public-url) and the file to
  // drag into the issue. Only MRtechnology's private key opens it.
  let withLog = $state(!!ticket.hasLog);
  let logLink = $state('');
  if (ticket.hasLog) {
    api('POST', `/tickets/${ticket.id}/log/share`).then((r) => (logLink = r.url)).catch(() => {});
  }
  const logText = $derived(!withLog ? '' : logLink ? t('forward.logLinkText', { url: logLink }) : t('forward.logFileText'));

  let version = $state('');
  $effect(() => {
    version = versions[plugin] ?? '';
  });

  function conversation() {
    const when = (ms) => new Date(ms).toLocaleString();
    const head = t('forward.from', { id: ticket.id, category: ticket.categoryName ?? '', server: brand.name });
    const lines = (ticket.messages ?? []).filter((m) => !m.system).map((m) => `**${m.name}** (${when(m.time)}):\n${m.text}`);
    return [head, '', ...lines].join('\n\n');
  }

  const repo = $derived(PLUGINS.find(([n]) => n === plugin)?.[1] ?? PLUGINS[0][1]);

  function address() {
    const base = `https://github.com/${repo}/issues/new?template=bug_report.yml`;
    const params = (text) => [['title', title], ['version', version], ['server', server], ['steps', steps], ['what', text]]
      .filter(([, v]) => v).map(([k, v]) => `&${k}=${encodeURIComponent(v)}`).join('');
    const tail = logText ? '\n\n' + logText : '';
    let text = what;
    let url = base + params(text + tail);
    // too long: shorten the conversation from the end, with a note (the log line stays)
    while (url.length > MAX_URL && text.length > 200) {
      text = text.slice(0, Math.floor(text.length * 0.85)) + '\n\n' + t('forward.cut');
      url = base + params(text + tail);
    }
    return url;
  }

  function open() {
    window.open(address(), '_blank', 'noopener');
    opened = true;
  }

  async function save() {
    busy = true;
    error = '';
    try {
      await api('POST', `/tickets/${ticket.id}/issue`, { url: link.trim() });
      onlinked(link.trim());
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onclose()} />

<div class="overlay" role="presentation" onclick={(e) => e.target === e.currentTarget && onclose()}>
  <div class="dialog card" role="dialog" aria-modal="true" aria-label={t('forward.title')}>
    <header>
      <h2>{t('forward.title')}</h2>
      <button class="x" aria-label={t('common.close')} onclick={onclose}>✕</button>
    </header>
    <p class="muted small">{t('forward.hint')}</p>
    {#if error}<div class="error">{error}</div>{/if}

    <span class="lbl">{t('forward.plugin')}</span>
    <div class="plugins" role="radiogroup">
      {#each PLUGINS as [name] (name)}
        <button type="button" role="radio" aria-checked={plugin === name} class:on={plugin === name} onclick={() => (plugin = name)}>
          {name}{#if versions[name]}<small>{versions[name]}</small>{/if}
        </button>
      {/each}
    </div>

    <div class="two">
      <label>{t('forward.version')}<input bind:value={version} placeholder="STA-1.6.2-bu001" /></label>
      <label>{t('forward.server')}<input bind:value={server} placeholder="Paper 26.3 #251" /></label>
    </div>
    <label>{t('forward.issueTitle')}<input bind:value={title} maxlength="200" /></label>
    <label>{t('forward.what')}<textarea bind:value={what} rows="9"></textarea></label>
    <label>{t('forward.steps')}<textarea bind:value={steps} rows="3" placeholder={t('forward.stepsHint')}></textarea></label>
    {#if ticket.hasLog}
      <div class="logbox">
        <label class="check"><input type="checkbox" bind:checked={withLog} /> {t('forward.withLog')}</label>
        {#if withLog}
          <p class="muted small">{logLink ? t('forward.logLinkHint') : t('forward.logFileHint')}</p>
          <a class="button secondary small" href={`/api/tickets/${ticket.id}/log/sealed`} download>{t('forward.logDownload')} ↓</a>
        {/if}
      </div>
    {/if}
    <p class="warn small">⚠ {t('forward.public')}</p>

    <button class="block" onclick={open} disabled={!title.trim()}>{t('forward.open', { repo })} ↗</button>

    <div class="link" class:hl={opened}>
      <label>{t('forward.link')}<input bind:value={link} placeholder={`https://github.com/${repo}/issues/123`} /></label>
      <div class="link-actions">
        {#if ticket.issue}<button class="secondary" disabled={busy} onclick={() => { link = ''; save(); }}>{t('forward.unlink')}</button>{/if}
        <button class="secondary" disabled={busy || !link.trim()} onclick={save}>{t('forward.save')}</button>
      </div>
    </div>
  </div>
</div>

<style>
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 55%);
  }

  .dialog {
    width: min(620px, 100%);
    max-height: calc(100dvh - 32px);
    overflow-y: auto;
    display: grid;
    gap: 12px;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  h2 {
    margin: 0;
  }

  .x {
    width: 32px;
    height: 32px;
    padding: 0;
    border-radius: 50%;
    color: var(--muted);
    background: var(--surface-2);
  }

  p {
    margin: 0;
  }

  .lbl,
  label {
    display: grid;
    gap: 6px;
    margin: 0;
    font-weight: 600;
    font-size: 0.88rem;
  }

  textarea {
    width: 100%;
    font: inherit;
    font-size: 0.88rem;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    resize: vertical;
  }

  .plugins {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .plugins button {
    display: inline-flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-start;
    gap: 0;
    padding: 6px 12px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text);
    font-size: 0.86rem;
  }

  .plugins button small {
    color: var(--muted);
    font-weight: 500;
    font-size: 0.72rem;
  }

  .plugins button.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .two {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 10px;
  }

  .warn {
    color: var(--warn);
  }

  .logbox {
    display: grid;
    gap: 8px;
    justify-items: start;
    padding: 12px;
    border-radius: 12px;
    background: var(--surface-2);
  }

  .check {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .check input {
    width: auto;
    margin: 0;
  }

  .logbox a.button {
    text-decoration: none;
  }

  .link {
    display: grid;
    gap: 8px;
    padding: 12px;
    border-radius: 12px;
    border: 1px dashed var(--border);
  }

  .link.hl {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 6%, transparent);
  }

  .link-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>
