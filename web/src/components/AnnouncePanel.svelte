<script>
  // Announcements on the page "Communication": send one (an own template, with values if it has placeholders, or a
  // free text), and - with mradmin.announce.templates - make the templates: texts per language with a live preview of
  // the screen, Discord, times, sound and who gets it. The built-in presets of the language file are not shown here
  // (MRadmin uses some itself), they stay usable with /mradmin announce and in scripts.
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { miniSpans } from '../lib/minimessage.js';

  let { me, onmessage, onerror } = $props();

  const PARTS = ['title', 'subtitle', 'chat', 'actionbar'];
  const AUDIENCES = ['player', 'bot', 'support', 'staff', 'admin'];

  let presets = $state([]);
  let templates = $state([]);
  let builtIn = $state([]);
  let busy = $state(false);

  // Sending
  let preset = $state('');
  let args = $state('');
  let text = $state('');
  let toDiscord = $state(true);

  // Editing a template (null = none open)
  let draft = $state(null);
  let lang = $state('de');
  let sounds = $state(null);

  async function load() {
    try {
      const r = await api('GET', '/announce');
      presets = r.presets;
      templates = r.templates ?? [];
      builtIn = r.builtIn ?? [];
      if (preset && !presets.some((p) => p.name === preset)) preset = '';
    } catch (e) {
      onerror(errorText(e));
    }
  }
  load();

  async function send(event) {
    event.preventDefault();
    const body = preset ? { preset, args, discord: String(toDiscord) } : { text, discord: String(toDiscord) };
    if (!confirm(t('mt.announceConfirm'))) return;
    busy = true;
    try {
      await api('POST', '/announce', body);
      onmessage(t('mt.announced'));
      args = '';
      text = '';
    } catch (e) {
      onerror(errorText(e));
    } finally {
      busy = false;
    }
  }

  const blankTexts = () => Object.fromEntries([...PARTS, 'discord'].map((p) => [p, { de: '', en: '' }]));

  function newTemplate() {
    draft = {
      original: null, name: '', texts: blankTexts(), discord: false, fadeIn: 0.5, stay: 3.5, fadeOut: 1,
      soundMode: 'default', sound: '', volume: 1, pitch: 1, audience: 'player',
    };
    lang = 'de';
    loadSounds();
  }

  function edit(tpl) {
    const custom = tpl.sound !== 'default' && tpl.sound !== 'none';
    draft = {
      original: tpl.name, name: tpl.name, texts: structuredClone(tpl.texts), discord: tpl.discord, fadeIn: tpl.fadeIn,
      stay: tpl.stay, fadeOut: tpl.fadeOut, soundMode: custom ? 'custom' : tpl.sound, sound: custom ? tpl.sound : '',
      volume: tpl.volume, pitch: tpl.pitch, audience: tpl.audience,
    };
    lang = 'de';
    loadSounds();
  }

  async function loadSounds() {
    if (sounds) return;
    try {
      sounds = (await api('GET', '/announce/sounds')).sounds;
    } catch {
      sounds = [];
    }
  }

  async function save(event) {
    event.preventDefault();
    busy = true;
    try {
      const { soundMode, sound, ...rest } = draft;
      await api('POST', '/announce/template', {
        ...rest,
        name: draft.name.trim().toLowerCase(),
        sound: soundMode === 'custom' ? sound.trim() : soundMode,
        fadeIn: Number(draft.fadeIn), stay: Number(draft.stay), fadeOut: Number(draft.fadeOut),
        volume: Number(draft.volume), pitch: Number(draft.pitch),
      });
      onmessage(t('ann.saved', { name: draft.name.trim().toLowerCase() }));
      draft = null;
      await load();
    } catch (e) {
      onerror(errorText(e));
    } finally {
      busy = false;
    }
  }

  async function remove(tpl) {
    if (!confirm(t('ann.deleteConfirm', { name: tpl.name }))) return;
    busy = true;
    try {
      await api('POST', `/announce/template/${encodeURIComponent(tpl.name)}/delete`);
      if (draft?.original === tpl.name) draft = null;
      onmessage(t('ann.deleted', { name: tpl.name }));
      await load();
    } catch (e) {
      onerror(errorText(e));
    } finally {
      busy = false;
    }
  }

  const chosen = $derived(presets.find((p) => p.name === preset));
  const filled = (l) => PARTS.some((p) => draft?.texts[p][l]?.trim());
  const nameOk = $derived(!!draft && /^[a-z0-9_-]{1,32}$/.test(draft.name.trim().toLowerCase()));
  const nameTaken = $derived(
    !!draft && (builtIn.includes(draft.name.trim().toLowerCase())
      || (draft.name.trim().toLowerCase() !== draft.original && templates.some((x) => x.name === draft.name.trim().toLowerCase()))),
  );
  const complete = $derived(!!draft && nameOk && !nameTaken && (filled('de') || filled('en')));

  // The preview fills placeholders with examples, as a player would see it.
  const example = (raw) => (raw ?? '').replace(/<args>/g, '5 Minuten').replace(/<arg\d>/g, '…');
  const preview = (part) => {
    const raw = draft?.texts[part][lang] || draft?.texts[part][lang === 'de' ? 'en' : 'de'] || '';
    return raw ? miniSpans(example(raw), part === 'chat' ? { color: '#ffffff' } : { color: '#ffffff' }) : [];
  };
  const spanStyle = (s) => [s.color ? `color:${s.color}` : '', s.b ? 'font-weight:700' : '', s.i ? 'font-style:italic' : '',
    s.u || s.s ? `text-decoration:${[s.u ? 'underline' : '', s.s ? 'line-through' : ''].join(' ').trim()}` : ''].filter(Boolean).join(';');

  // Which groups get it: the chosen one and all above it.
  const reach = (audience) => AUDIENCES.slice(AUDIENCES.indexOf(audience)).map((a) => t('ann.audience.' + a)).join(', ');
</script>

<!-- A line of the preview with MiniMessage colours. -->
{#snippet spans(list)}
  {#each list as s}<span style={spanStyle(s)}>{s.text}</span>{/each}
{/snippet}

<!-- Send -->
<form class="card tile" onsubmit={send}>
  <h2>{t('ann.send')}</h2>
  <p class="muted small">{t('mt.announceHint')}</p>
  <div class="presets">
    <button type="button" class="preset" class:active={preset === ''} onclick={() => (preset = '')}>
      <strong>{t('mt.freeText')}</strong><span class="muted small">{t('mt.freeTextHint')}</span>
    </button>
    {#each presets as p (p.name)}
      <button type="button" class="preset" class:active={preset === p.name} onclick={() => (preset = p.name)}>
        <span class="p-top">
          <strong class="mono">{p.name}</strong>
          {#if p.audience !== 'player'}<span class="chip">{t('ann.audience.' + p.audience)}+</span>{/if}
          {#if p.discord}<span class="chip">Discord</span>{/if}
        </span>
        <span class="muted small">{p.preview}</span>
      </button>
    {/each}
  </div>
  {#if presets.length === 0}<p class="muted tiny">{t('ann.noTemplates')}</p>{/if}
  {#if preset === ''}
    <input bind:value={text} maxlength="300" placeholder={t('mt.textPlaceholder')} aria-label={t('mt.freeText')} />
  {:else if chosen?.values}
    <input bind:value={args} maxlength="100" placeholder={t('mt.argsPlaceholder')} aria-label={t('mt.args')} />
  {/if}
  <div class="row">
    <button type="button" class="toggle" role="switch" aria-checked={toDiscord} onclick={() => (toDiscord = !toDiscord)}>
      <span class="knob" class:on={toDiscord}></span>{t('mt.toDiscord')}
    </button>
    <button type="submit" disabled={busy || (preset === '' && !text.trim())}>{t('mt.send')}</button>
  </div>
</form>

<!-- Templates -->
{#if me.may.announceTemplates}
  <section class="card tile">
    <div class="row">
      <h2>{t('ann.templates')}<span class="n">{templates.length}</span></h2>
      {#if !draft}<button class="mini" onclick={newTemplate}>+ {t('ann.new')}</button>{/if}
    </div>
    <p class="muted small">{t('ann.templatesHint')}</p>

    {#if draft}
      <form class="editor" onsubmit={save}>
        <div class="row">
          <h3>{draft.original ? t('ann.edit', { name: draft.original }) : t('ann.new')}</h3>
          <button type="button" class="secondary mini" onclick={() => (draft = null)}>{t('news.cancel')}</button>
        </div>

        <div class="ed-grid">
          <!-- left: name, texts per language -->
          <div class="col">
            <div class="field">
              <label class="flabel" for="ann-name">{t('ann.name')}</label>
              <input id="ann-name" class="mono" bind:value={draft.name} maxlength="32" placeholder="event" />
              {#if draft.name && !nameOk}<span class="bad-text tiny">{t('ann.nameRule')}</span>
              {:else if nameTaken}<span class="bad-text tiny">{t('ann.nameTaken')}</span>
              {:else}<span class="muted tiny">{t('ann.nameHint')}</span>{/if}
            </div>

            <div class="texts">
              <div class="row">
                <span class="flabel">{t('ann.texts')}</span>
                <div class="seg small" role="tablist">
                  {#each ['de', 'en'] as l}
                    <button type="button" role="tab" aria-selected={lang === l} class:active={lang === l} onclick={() => (lang = l)}>
                      {l.toUpperCase()}{#if filled(l)}<span class="ok-dot"></span>{/if}
                    </button>
                  {/each}
                </div>
              </div>
              {#each PARTS as part}
                <div class="field">
                  <label class="flabel" for="ann-{part}">{t('ann.part.' + part)}</label>
                  <input id="ann-{part}" bind:value={draft.texts[part][lang]} maxlength="300" placeholder={t('ann.placeholder.' + part)} />
                </div>
              {/each}
              <p class="muted tiny">{t('ann.textHint')}</p>
            </div>

            <div class="field">
              <button type="button" class="toggle" role="switch" aria-checked={draft.discord} onclick={() => (draft.discord = !draft.discord)}>
                <span class="knob" class:on={draft.discord}></span>{t('ann.discord')}
              </button>
              {#if draft.discord}
                <textarea bind:value={draft.texts.discord[lang]} maxlength="300" rows="2" placeholder={t('ann.placeholder.discord')}
                  aria-label={t('ann.discord')}></textarea>
                <span class="muted tiny">{t('ann.discordHint')}</span>
              {/if}
            </div>
          </div>

          <!-- right: preview, audience, times, sound -->
          <div class="col">
            <div class="field">
              <span class="flabel">{t('ann.preview')}</span>
              <div class="screen">
                <div class="screen-title">
                  <div class="big">{@render spans(preview('title'))}</div>
                  <div class="sub">{@render spans(preview('subtitle'))}</div>
                </div>
                <div class="screen-bar">{@render spans(preview('actionbar'))}</div>
                {#if preview('chat').length}<div class="screen-chat">{@render spans(preview('chat'))}</div>{/if}
              </div>
            </div>

            <div class="field">
              <span class="flabel">{t('ann.audience')}</span>
              <div class="seg wide" role="radiogroup">
                {#each AUDIENCES as a}
                  <button type="button" role="radio" aria-checked={draft.audience === a} class:active={draft.audience === a}
                    class:included={AUDIENCES.indexOf(a) > AUDIENCES.indexOf(draft.audience)} onclick={() => (draft.audience = a)}>
                    {t('ann.audience.' + a)}
                  </button>
                {/each}
              </div>
              <span class="muted tiny">{t('ann.reach', { groups: reach(draft.audience) })}</span>
            </div>

            <div class="field">
              <span class="flabel">{t('ann.times')}</span>
              <div class="times">
                {#each [['fadeIn', 'ann.fadeIn'], ['stay', 'ann.stay'], ['fadeOut', 'ann.fadeOut']] as [key, label]}
                  <label class="num">
                    <span class="muted tiny">{t(label)}</span>
                    <span class="num-in"><input type="number" min="0" max="60" step="0.1" bind:value={draft[key]} /><span class="muted small">s</span></span>
                  </label>
                {/each}
              </div>
            </div>

            <div class="field">
              <span class="flabel">{t('ann.sound')}</span>
              <div class="seg" role="radiogroup">
                {#each ['default', 'none', 'custom'] as m}
                  <button type="button" role="radio" aria-checked={draft.soundMode === m} class:active={draft.soundMode === m}
                    onclick={() => (draft.soundMode = m)}>{t('ann.sound.' + m)}</button>
                {/each}
              </div>
              {#if draft.soundMode === 'custom'}
                <input class="mono" list="ann-sounds" bind:value={draft.sound} placeholder="minecraft:block.note_block.pling" aria-label={t('ann.sound')} />
                <datalist id="ann-sounds">
                  {#each sounds ?? [] as s}<option value={s}></option>{/each}
                </datalist>
                <div class="times">
                  <label class="num">
                    <span class="muted tiny">{t('ann.volume')} {Number(draft.volume).toFixed(1)}</span>
                    <input type="range" min="0" max="2" step="0.1" bind:value={draft.volume} />
                  </label>
                  <label class="num">
                    <span class="muted tiny">{t('ann.pitch')} {Number(draft.pitch).toFixed(1)}</span>
                    <input type="range" min="0.5" max="2" step="0.1" bind:value={draft.pitch} />
                  </label>
                </div>
              {/if}
            </div>
          </div>
        </div>

        <div class="row">
          {#if draft.original}
            <button type="button" class="danger" disabled={busy} onclick={() => remove({ name: draft.original })}>{t('news.delete')}</button>
          {:else}<span></span>{/if}
          <button type="submit" disabled={busy || !complete}>{t('ann.save')}</button>
        </div>
      </form>
    {/if}

    {#if templates.length}
      <ul class="tpls">
        {#each templates as tpl (tpl.name)}
          {@const p = presets.find((x) => x.name === tpl.name)}
          <li class="tpl" class:open={draft?.original === tpl.name}>
            <div class="t-top">
              <strong class="mono">{tpl.name}</strong>
              <span class="chip">{t('ann.audience.' + tpl.audience)}{tpl.audience !== 'admin' ? '+' : ''}</span>
              {#if tpl.discord}<span class="chip">Discord</span>{/if}
            </div>
            <span class="small">{p?.preview ?? ''}</span>
            <span class="muted tiny">{tpl.author}{tpl.updated ? ' · ' + dateTime(tpl.updated) : ''}</span>
            <div class="t-actions">
              <button class="secondary mini" disabled={busy} onclick={() => edit(tpl)}>{t('news.editButton')}</button>
              <button class="danger mini" disabled={busy} onclick={() => remove(tpl)}>{t('news.delete')}</button>
            </div>
          </li>
        {/each}
      </ul>
    {:else if !draft}
      <p class="muted small">{t('ann.noTemplates')}</p>
    {/if}
    {#if builtIn.length}<p class="muted tiny">{t('ann.builtInHint', { names: builtIn.join(', ') })}</p>{/if}
  </section>
{/if}

<style>
  .tile {
    display: grid;
    gap: 14px;
    align-content: start;
  }

  h2,
  h3 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 1.05rem;
  }

  h3 {
    font-size: 0.98rem;
  }

  .row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .mini {
    padding: 4px 12px;
    font-size: 0.82rem;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .tiny {
    font-size: 0.76rem;
    margin: 0;
  }

  .bad-text {
    color: var(--bad);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .chip {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .ok-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--good);
  }

  /* Sending */
  .presets {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr));
  }

  .preset {
    display: grid;
    gap: 4px;
    justify-content: stretch;
    justify-items: start;
    text-align: left;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-weight: 400;
    padding: 10px 12px;
  }

  .p-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .preset:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .preset.active {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 8%, var(--surface-2));
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 25%, transparent);
  }

  /* Switch */
  .toggle {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 0;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    justify-self: start;
  }

  .toggle:hover {
    filter: none;
  }

  .knob {
    position: relative;
    width: 40px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
    transition: background 0.15s;
  }

  .knob::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
    transition: transform 0.15s;
  }

  .knob.on {
    background: var(--accent);
  }

  .knob.on::after {
    transform: translateX(18px);
  }

  /* Segmented switches */
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    justify-self: start;
  }

  .seg.wide {
    display: flex;
    justify-self: stretch;
  }

  .seg.wide button {
    flex: 1;
  }

  .seg button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 6px 12px;
    border-radius: 9px;
    font-size: 0.86rem;
  }

  .seg.small button {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .seg button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  /* Groups that get it too (above the chosen one) */
  .seg button.included {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 10%, transparent);
  }

  /* The editor */
  .editor {
    display: grid;
    gap: 16px;
    padding: 16px;
    border-radius: 14px;
    border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--border));
    background: color-mix(in srgb, var(--accent) 4%, var(--surface));
  }

  .ed-grid {
    display: grid;
    gap: 18px 24px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
    align-items: start;
  }

  .col {
    display: grid;
    gap: 14px;
  }

  .field {
    display: grid;
    gap: 6px;
  }

  .flabel {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  .texts {
    display: grid;
    gap: 10px;
    padding: 12px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .texts input,
  textarea {
    background: var(--surface);
  }

  textarea {
    width: 100%;
    font: inherit;
    color: var(--text);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 9px 10px;
  }

  /* The screen preview: title in the middle, action bar below, chat at the bottom left */
  .screen {
    position: relative;
    display: grid;
    grid-template-rows: 1fr auto auto;
    min-height: 220px;
    padding: 14px;
    border-radius: 12px;
    overflow: hidden;
    color: #fff;
    background:
      linear-gradient(180deg, rgb(0 0 0 / 0.15), rgb(0 0 0 / 0.45)),
      linear-gradient(180deg, #6fa8dc 0%, #8fc0e8 45%, #5d8a3a 46%, #4a7030 100%);
    font-family: ui-monospace, 'Courier New', monospace;
    text-shadow: 2px 2px 0 rgb(0 0 0 / 0.55);
  }

  .screen-title {
    display: grid;
    align-content: center;
    justify-items: center;
    text-align: center;
    gap: 4px;
  }

  .big {
    font-size: 1.7rem;
    font-weight: 700;
    line-height: 1.15;
  }

  .sub {
    font-size: 0.95rem;
  }

  .screen-bar {
    min-height: 1.2em;
    text-align: center;
    font-size: 0.85rem;
    margin-bottom: 8px;
  }

  .screen-chat {
    justify-self: start;
    max-width: 100%;
    padding: 3px 6px;
    font-size: 0.78rem;
    background: rgb(0 0 0 / 0.45);
    overflow-wrap: anywhere;
  }

  .times {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  }

  .num {
    display: grid;
    gap: 4px;
    margin: 0;
  }

  .num-in {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .num-in input {
    min-width: 0;
  }

  /* The templates */
  .tpls {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  }

  .tpl {
    display: grid;
    gap: 6px;
    align-content: start;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-left: 4px solid var(--accent);
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .tpl.open {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 30%, transparent);
  }

  .t-top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .t-top strong {
    margin-right: auto;
  }

  .t-actions {
    display: flex;
    gap: 6px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  @media (prefers-reduced-motion: reduce) {
    .knob,
    .knob::after {
      transition: none;
    }
  }
</style>
