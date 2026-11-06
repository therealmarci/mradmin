<script>
  // The config.yml of a plugin as a form (page Plugins -> plugin -> Configuration). The plugin describes its fields
  // (web-config.yml), MRadmin checks every value, writes the file and the plugin reloads it. See ConfigRoutes.java.
  //   Changes collect in `draft` (key -> new value) until "Save"; a table keeps row -> column -> value.
  import { api } from '../lib/api.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import { duration } from '../lib/format.js';
  import Dropdown from './Dropdown.svelte';
  import Toast from './Toast.svelte';

  let { plugin } = $props();

  let data = $state(null);
  let error = $state('');
  let fieldErrors = $state({});
  let message = $state('');
  let notice = $state(null);
  let busy = $state(false);
  let draft = $state({});
  let secretOpen = $state({});
  let query = $state('');
  let active = $state('');

  const tx = (o) => (o ? o[lang] || o.en || o.de || '' : '');

  async function load() {
    try {
      data = await api('GET', `/config/${encodeURIComponent(plugin)}`);
      error = '';
      if (!active && data.sections.length) active = data.sections[0].id;
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    load();
  });

  // ------------------------------------------------------------------ values

  const fields = $derived(data ? data.sections.flatMap((s) => s.fields) : []);
  const byKey = $derived(Object.fromEntries(fields.map((f) => [f.key, f])));

  function value(f) {
    if (f.type === 'table') {
      const rows = structuredClone($state.snapshot(f.value));
      for (const [row, cells] of Object.entries(draft[f.key] ?? {})) Object.assign(rows[row], cells);
      return rows;
    }
    return f.key in draft ? draft[f.key] : f.value;
  }

  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

  function set(f, v) {
    const next = { ...draft };
    if (same(v, f.value)) delete next[f.key];
    else next[f.key] = v;
    draft = next;
    if (fieldErrors[f.key]) fieldErrors = { ...fieldErrors, [f.key]: undefined };
  }

  function setCell(f, row, col, v) {
    const rows = { ...(draft[f.key] ?? {}) };
    const cells = { ...(rows[row] ?? {}) };
    if (same(v, f.value[row]?.[col])) delete cells[col];
    else cells[col] = v;
    if (Object.keys(cells).length) rows[row] = cells;
    else delete rows[row];
    const next = { ...draft };
    if (Object.keys(rows).length) next[f.key] = rows;
    else delete next[f.key];
    draft = next;
  }

  /** A field differs from the bundled default (secrets: never). */
  const custom = (f) => f.type !== 'secret' && !same(value(f), f.default);

  function reset(f) {
    if (f.type === 'table') {
      const rows = {};
      for (const [row, cells] of Object.entries(f.default)) {
        for (const [col, v] of Object.entries(cells)) {
          if (!same(v, f.value[row]?.[col])) (rows[row] ??= {})[col] = v;
        }
      }
      const next = { ...draft };
      if (Object.keys(rows).length) next[f.key] = rows;
      else delete next[f.key];
      draft = next;
    } else {
      set(f, structuredClone(f.default));
    }
  }

  /** "key", "!key" or "key=a|b" - a field shows only while the other one fits. */
  function shown(f) {
    if (!f.when) return true;
    const negate = f.when.startsWith('!');
    const [key, allowed] = (negate ? f.when.slice(1) : f.when).split('=');
    const other = byKey[key];
    if (!other) return true;
    const v = value(other);
    const ok = allowed === undefined ? v === true : allowed.split('|').includes(String(v));
    return negate ? !ok : ok;
  }

  const changes = $derived(Object.keys(draft).length);

  // ------------------------------------------------------------------ search and sections

  const needle = $derived(query.trim().toLowerCase());
  function matches(f) {
    if (!needle) return true;
    return [tx(f.name), tx(f.help), f.key].some((s) => s.toLowerCase().includes(needle));
  }
  const visible = $derived(
    data ? data.sections.map((s) => ({ ...s, list: s.fields.filter((f) => shown(f) && matches(f)) })).filter((s) => s.list.length) : [],
  );

  let jumping = 0;
  function jump(id) {
    active = id;
    // While the page scrolls there, the marker stays on the chosen section.
    jumping = Date.now() + 900;
    document.getElementById('cfg-' + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** The marker follows the scrolling: the last section whose top passed the bar, at the very bottom the last one. */
  function spy() {
    if (Date.now() < jumping || !visible.length) return;
    let current = visible[0].id;
    for (const s of visible) {
      const el = document.getElementById('cfg-' + s.id);
      if (el && el.getBoundingClientRect().top <= 130) current = s.id;
    }
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) current = visible.at(-1).id;
    active = current;
  }

  $effect(() => {
    visible;
    requestAnimationFrame(spy);
  });

  // ------------------------------------------------------------------ durations

  const DURATION = /^(\d+[dhms])+$|^\d+$/;
  const UNIT = { d: 86400000, h: 3600000, m: 60000, s: 1000 };
  function durationText(raw, permanent) {
    const v = String(raw ?? '').trim().toLowerCase();
    if (permanent && v === 'permanent') return t('config.permanent');
    if (!DURATION.test(v)) return null;
    if (/^\d+$/.test(v)) return duration(Number(v) * 60000);
    let ms = 0;
    for (const [, n, u] of v.matchAll(/(\d+)([dhms])/g)) ms += Number(n) * UNIT[u];
    return duration(ms);
  }

  // ------------------------------------------------------------------ lists

  let adding = $state({});
  function addItem(f) {
    const raw = String(adding[f.key] ?? '').trim();
    if (!raw) return;
    let v = f.type === 'ints' ? Number(raw) : raw;
    if (f.type === 'ints' && !Number.isInteger(v)) return;
    if (f.item === 'time') {
      const m = raw.match(/^([01]?\d|2[0-3]):([0-5]\d)/);
      if (!m) return;
      v = m[1].padStart(2, '0') + ':' + m[2];
    }
    const list = [...value(f)];
    if (!list.includes(v)) list.push(v);
    if (f.item === 'time') list.sort();
    set(f, list);
    adding = { ...adding, [f.key]: '' };
  }
  const removeItem = (f, i) => set(f, value(f).filter((_, j) => j !== i));
  const toggleWorld = (f, w) => {
    const list = value(f);
    set(f, list.includes(w) ? list.filter((x) => x !== w) : [...list, w]);
  };
  const worldsOf = (f) => [...new Set([...(data?.worlds ?? []), ...value(f)])];

  // ------------------------------------------------------------------ saving

  async function save() {
    busy = true;
    error = '';
    notice = null;
    fieldErrors = {};
    try {
      const result = await api('POST', `/config/${encodeURIComponent(plugin)}`, { version: data.version, changes: $state.snapshot(draft) });
      data = { ...data, version: result.version, sections: result.sections };
      draft = {};
      secretOpen = {};
      message = result.changed.length ? t('config.saved', { plugin }) : t('config.nothing');
      if (result.reloadError) notice = { tone: 'bad', text: t('config.reloadFailed', { error: result.reloadError }) };
      else if (result.restart) notice = { tone: 'warn', text: t('config.restartNeeded') };
    } catch (e) {
      if (e.status === 400 && e.key?.startsWith('config-') && e.field) {
        // a table cell answers with key.row.column
        const key = byKey[e.field] ? e.field : fields.find((f) => e.field.startsWith(f.key + '.'))?.key ?? e.field;
        fieldErrors = { [key]: errorText(e) };
        error = t('config.fixField', { field: tx(byKey[key]?.name) || key });
        document.getElementById('field-' + key)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      } else {
        error = errorText(e);
      }
    } finally {
      busy = false;
    }
  }

  function discard() {
    draft = {};
    secretOpen = {};
    fieldErrors = {};
  }

  async function reloadFromFile() {
    discard();
    await load();
  }
</script>

<svelte:window onbeforeunload={(e) => { if (changes) e.preventDefault(); }} onscroll={spy} />

{#snippet control(f)}
  {@const v = value(f)}
  {#if f.type === 'bool'}
    <button type="button" class="switch" class:on={v} role="switch" aria-checked={v} aria-label={tx(f.name)}
      onclick={() => set(f, !v)}><span></span></button>
  {:else if f.type === 'int' || f.type === 'decimal'}
    <span class="number">
      <input type="number" value={v ?? ''} step={f.type === 'int' ? 1 : 'any'} min={f.min ?? undefined} max={f.max ?? undefined}
        oninput={(e) => e.currentTarget.value !== '' && set(f, Number(e.currentTarget.value))} />
      {#if tx(f.unit)}<span class="unit">{tx(f.unit)}</span>{/if}
    </span>
  {:else if f.type === 'choice'}
    {#if f.options.length <= 4 && f.options.every((o) => tx(o.name).length <= 16)}
      <span class="segments" role="radiogroup" aria-label={tx(f.name)}>
        {#each f.options as o}
          <button type="button" role="radio" aria-checked={v === o.value} class:on={v === o.value} onclick={() => set(f, o.value)}>{tx(o.name)}</button>
        {/each}
      </span>
    {:else}
      <Dropdown width="240px" value={v} label={tx(f.name)} options={f.options.map((o) => ({ value: o.value, label: tx(o.name) }))}
        onchange={(x) => set(f, x)} />
    {/if}
  {:else if f.type === 'color'}
    <span class="color">
      <input type="color" value={/^#[0-9a-f]{6}$/i.test(v) ? v : '#000000'} oninput={(e) => set(f, e.currentTarget.value.toUpperCase())} />
      <input class="hex mono" value={v} maxlength="7" oninput={(e) => set(f, e.currentTarget.value)} />
    </span>
  {:else if f.type === 'duration'}
    {@const human = durationText(v, f.permanent)}
    <span class="duration">
      <input class="mono" value={v} class:bad={human === null} oninput={(e) => set(f, e.currentTarget.value)} />
      <span class="hint" class:bad-text={human === null}>{human ?? t('config.durationHint')}</span>
    </span>
  {:else if f.type === 'secret'}
    {#if secretOpen[f.key]}
      <span class="secret">
        <input type="password" autocomplete="new-password" value={v} placeholder={t('config.secretNew')} maxlength={f.maxLength}
          oninput={(e) => set(f, e.currentTarget.value)} />
        <button type="button" class="ghost" onclick={() => { secretOpen = { ...secretOpen, [f.key]: false }; const n = { ...draft }; delete n[f.key]; draft = n; }}>{t('common.cancel')}</button>
      </span>
    {:else}
      <span class="secret">
        <span class="chip" class:good-chip={f.set}>{f.set ? t('config.secretSet') : t('config.secretEmpty')}</span>
        <button type="button" class="ghost" onclick={() => (secretOpen = { ...secretOpen, [f.key]: true })}>{f.set ? t('config.secretChange') : t('config.secretEnter')}</button>
        {#if f.set}<button type="button" class="ghost danger-text" onclick={() => { set(f, ''); secretOpen = { ...secretOpen, [f.key]: true }; }}>{t('config.secretClear')}</button>{/if}
      </span>
    {/if}
  {:else if f.type === 'text'}
    <textarea rows="3" maxlength={f.maxLength} value={v} oninput={(e) => set(f, e.currentTarget.value)}></textarea>
  {:else if f.type === 'strings' && f.count > 0}
    <span class="fixed">
      {#each v as item, i}
        {@const human = f.item === 'duration' ? durationText(item, f.permanent) : ''}
        <input class="mono" value={item} class:bad={human === null} title={human ?? ''}
          oninput={(e) => set(f, v.map((x, j) => (j === i ? e.currentTarget.value : x)))} />
      {/each}
    </span>
  {:else if f.type === 'ints'}
    <span class="chips">
      {#each v as item, i}
        <span class="item mono">{item}{tx(f.unit) ? ' ' + tx(f.unit) : ''}<button type="button" aria-label={t('config.remove')} onclick={() => removeItem(f, i)}>×</button></span>
      {/each}
      <input class="add" type="number" step="1" min={f.min ?? undefined} max={f.max ?? undefined} placeholder={t('config.add')}
        value={adding[f.key] ?? ''} oninput={(e) => (adding = { ...adding, [f.key]: e.currentTarget.value })}
        onkeydown={(e) => e.key === 'Enter' && (e.preventDefault(), addItem(f))} onblur={() => addItem(f)} />
    </span>
  {:else if f.type === 'strings' && f.item === 'time'}
    <!-- Times of day: the browser's time field (HH:MM with a fixed colon), added with a button - not on blur, so a
         click on Save cannot race with adding. -->
    <span class="chips">
      {#each v as item, i}
        <span class="item mono">{item}<button type="button" aria-label={t('config.remove')} onclick={() => removeItem(f, i)}>×</button></span>
      {/each}
      <span class="time-add">
        <input class="add time" type="time" step="60" value={adding[f.key] ?? ''}
          oninput={(e) => (adding = { ...adding, [f.key]: e.currentTarget.value })}
          onkeydown={(e) => e.key === 'Enter' && (e.preventDefault(), addItem(f))} />
        <button type="button" class="secondary small-add" disabled={!adding[f.key]} onclick={() => addItem(f)}>+ {t('config.addTime')}</button>
      </span>
    </span>
  {:else if f.type === 'strings'}
    <span class="chips">
      {#each v as item, i}
        <span class="item mono">{item}<button type="button" aria-label={t('config.remove')} onclick={() => removeItem(f, i)}>×</button></span>
      {/each}
      <input class="add" placeholder={t('config.add')} value={adding[f.key] ?? ''}
        oninput={(e) => (adding = { ...adding, [f.key]: e.currentTarget.value })}
        onkeydown={(e) => e.key === 'Enter' && (e.preventDefault(), addItem(f))} onblur={() => addItem(f)} />
    </span>
  {:else if f.type === 'choices'}
    <span class="chips">
      {#each f.options as o}
        <button type="button" class="world" class:on={v.includes(o.value)} aria-pressed={v.includes(o.value)}
          onclick={() => set(f, v.includes(o.value) ? v.filter((x) => x !== o.value) : [...v, o.value])}>
          {v.includes(o.value) ? '✓ ' : ''}{tx(o.name)}
        </button>
      {/each}
    </span>
  {:else if f.type === 'worlds'}
    <span class="chips">
      {#each worldsOf(f) as w}
        <button type="button" class="world" class:on={v.includes(w)} aria-pressed={v.includes(w)} onclick={() => toggleWorld(f, w)}>
          {v.includes(w) ? '✓ ' : ''}{w}{#if !data.worlds.includes(w)}<em> ({t('config.worldMissing')})</em>{/if}
        </button>
      {/each}
    </span>
  {:else if f.type === 'table'}
    <div class="table" style="--cols:{f.columns.length}">
      <span class="th"></span>
      {#each f.columns as c}<span class="th">{tx(c.name)}</span>{/each}
      {#each f.rows as row}
        <span class="rname">{tx(row.name)}</span>
        {#each f.columns as c}
          {@const cell = v[row.key]?.[c.key]}
          <span class="cell">
            {#if c.type === 'bool'}
              <button type="button" class="switch small" class:on={cell} role="switch" aria-checked={cell}
                aria-label={tx(row.name) + ' ' + tx(c.name)} onclick={() => setCell(f, row.key, c.key, !cell)}><span></span></button>
            {:else if c.type === 'color'}
              <input type="color" value={/^#[0-9a-f]{6}$/i.test(cell) ? cell : '#000000'} aria-label={tx(row.name) + ' ' + tx(c.name)}
                oninput={(e) => setCell(f, row.key, c.key, e.currentTarget.value.toUpperCase())} />
            {:else if c.type === 'int'}
              <input type="number" class="small-num" value={cell ?? ''} oninput={(e) => e.currentTarget.value !== '' && setCell(f, row.key, c.key, Number(e.currentTarget.value))} />
            {:else}
              <input value={cell ?? ''} oninput={(e) => setCell(f, row.key, c.key, e.currentTarget.value)} />
            {/if}
          </span>
        {/each}
      {/each}
    </div>
  {:else}
    <input value={v} maxlength={f.maxLength} oninput={(e) => set(f, e.currentTarget.value)} />
  {/if}
{/snippet}

<div class="editor">
  {#if error}
    <div class="error">
      {error}
      {#if error === errorText({ key: 'config-changed' })}<button type="button" class="ghost" onclick={reloadFromFile}>{t('config.reloadFile')}</button>{/if}
    </div>
  {/if}
  {#if notice}<div class="notice {notice.tone}">{notice.text}</div>{/if}
  <Toast text={message} />

  {#if data}
    <div class="layout">
      <nav class="sections" aria-label={t('config.sections')}>
        <input class="search" type="search" placeholder={t('config.search')} bind:value={query} />
        {#each visible as s}
          <button type="button" class:on={active === s.id} onclick={() => jump(s.id)}>
            {tx(s.name)}
            {#if s.list.some((f) => f.key in draft)}<span class="dirty" title={t('config.changed')}></span>{/if}
          </button>
        {/each}
        <div class="file" title={t('config.fileHint')}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
            stroke-linejoin="round" aria-hidden="true"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h4" /></svg>
          <div>
            <span class="file-label">{t('config.fileLabel')}</span>
            <code>{#each data.file.split('/') as part, i}{i ? '/' : ''}<wbr />{part}{/each}</code>
            <span class="file-hint">{t('config.fileShort')}</span>
          </div>
        </div>
      </nav>

      <div class="content">
        {#each visible as s (s.id)}
          <section class="box" id={'cfg-' + s.id}>
            <header>
              <h3>{tx(s.name)}</h3>
              {#if tx(s.help)}<p class="muted">{tx(s.help)}</p>{/if}
              {#if data.status?.[s.id]}
                {@const st = data.status[s.id]}
                <p class="state {st.tone}"><span class="state-dot"></span>{t('config.status.' + st.key, { mode: st.mode ?? '' })}</p>
              {/if}
            </header>
            {#each s.list as f (f.key)}
              <div class="field" class:wide={f.type === 'table' || f.type === 'text'} class:changed={f.key in draft}
                class:invalid={fieldErrors[f.key]} id={'field-' + f.key}>
                <div class="label">
                  <span class="name">
                    {tx(f.name)}
                    {#if f.restart}<span class="tag">{t('config.restart')}</span>{/if}
                  </span>
                  {#if tx(f.help)}<span class="help">{tx(f.help)}</span>{/if}
                  {#if fieldErrors[f.key]}<span class="help bad-text">{fieldErrors[f.key]}</span>{/if}
                </div>
                <div class="input">
                  {@render control(f)}
                  {#if custom(f)}
                    <button type="button" class="reset" title={t('config.resetHint')} onclick={() => reset(f)}>↺ {t('config.reset')}</button>
                  {/if}
                </div>
              </div>
            {/each}
          </section>
        {:else}
          <p class="muted">{t('config.noMatch')}</p>
        {/each}
      </div>
    </div>

    {#if changes}
      <div class="savebar">
        <span><b>{changes}</b> {t('config.changes')}</span>
        <button type="button" class="ghost" disabled={busy} onclick={discard}>{t('config.discard')}</button>
        <button type="button" disabled={busy} onclick={save}>{busy ? t('config.saving') : t('config.save')}</button>
      </div>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .editor {
    display: grid;
    gap: 14px;
    container-type: inline-size;
  }

  .layout {
    display: grid;
    gap: 16px;
    grid-template-columns: 220px minmax(0, 1fr);
    align-items: start;
  }

  .sections {
    position: sticky;
    top: 76px;
    display: grid;
    gap: 2px;
  }

  .sections .search {
    width: 100%;
    margin: 0 0 16px;
    padding: 8px 10px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font: inherit;
    font-size: 0.9rem;
  }

  .sections button {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 9px;
    border: 0;
    background: transparent;
    color: var(--muted);
    font-weight: 600;
    font-size: 0.9rem;
    text-align: left;
  }

  .sections button:hover {
    background: var(--surface-2);
    color: var(--text);
  }

  .sections button.on {
    background: color-mix(in srgb, var(--accent) 14%, transparent);
    color: var(--accent);
  }

  .dirty {
    width: 7px;
    height: 7px;
    margin-left: auto;
    border-radius: 50%;
    background: var(--warn);
  }

  .file {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    margin-top: 14px;
    padding: 10px 12px;
    border-radius: 11px;
    border: 1px solid color-mix(in srgb, var(--accent) 30%, var(--border));
    background: color-mix(in srgb, var(--accent) 8%, var(--surface));
    color: var(--accent);
  }

  .file svg {
    flex-shrink: 0;
    margin-top: 2px;
  }

  .file div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .file-label {
    font-size: 0.7rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .file code {
    font-family: ui-monospace, monospace;
    font-size: 0.8rem;
    color: var(--text);
  }

  .file-hint {
    font-size: 0.74rem;
    color: var(--muted);
  }

  .content {
    display: grid;
    gap: 16px;
    min-width: 0;
    padding-bottom: 70px;
  }

  .box {
    scroll-margin-top: 80px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
    overflow: hidden;
  }

  .box header {
    padding: 14px 20px 10px;
    border-bottom: 1px solid var(--border);
  }

  h3 {
    margin: 0;
    font-size: 1.02rem;
  }

  .box header p {
    margin: 3px 0 0;
    font-size: 0.86rem;
  }

  .field {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, auto);
    gap: 10px 24px;
    align-items: center;
    padding: 12px 20px;
    border-left: 3px solid transparent;
    scroll-margin-top: 120px;
  }

  .field + .field {
    border-top: 1px solid color-mix(in srgb, var(--border) 60%, transparent);
  }

  .field.wide {
    grid-template-columns: 1fr;
  }

  .field.changed {
    border-left-color: var(--warn);
    background: color-mix(in srgb, var(--warn) 5%, transparent);
  }

  .field.invalid {
    border-left-color: var(--bad);
    background: color-mix(in srgb, var(--bad) 6%, transparent);
  }

  .label {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  .name {
    font-weight: 600;
  }

  .tag {
    margin-left: 6px;
    padding: 1px 7px;
    border-radius: 999px;
    font-size: 0.7rem;
    font-weight: 700;
    vertical-align: 1px;
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  .help {
    color: var(--muted);
    font-size: 0.84rem;
    line-height: 1.4;
  }

  .input {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    min-width: 0;
  }

  .wide .input {
    justify-content: flex-start;
  }

  .mono {
    font-family: ui-monospace, monospace;
  }

  .bad-text,
  .danger-text {
    color: var(--bad);
  }

  /* Inputs: own styles, the global input rule (full width, padding) does not fit here. */
  input:not([type='color']),
  textarea {
    width: auto;
    margin: 0;
    padding: 7px 10px;
    border-radius: 9px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font: inherit;
    font-size: 0.92rem;
  }

  input:focus,
  textarea:focus {
    outline: 2px solid color-mix(in srgb, var(--accent) 45%, transparent);
    outline-offset: 0;
  }

  input.bad {
    border-color: var(--bad);
  }

  textarea {
    width: 100%;
    resize: vertical;
  }

  .input > input {
    width: min(100%, 280px);
  }

  .number input {
    width: 110px;
    text-align: right;
  }

  .number,
  .duration,
  .color,
  .secret {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .unit,
  .hint {
    color: var(--muted);
    font-size: 0.84rem;
  }

  .duration input {
    width: 110px;
  }

  .duration .hint {
    min-width: 80px;
  }

  input[type='color'] {
    width: 36px;
    height: 32px;
    padding: 2px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    cursor: pointer;
  }

  .hex {
    width: 96px;
  }

  .secret input {
    width: min(100%, 280px);
  }

  /* Switch */
  .switch {
    position: relative;
    width: 44px;
    height: 24px;
    padding: 0;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    flex-shrink: 0;
  }

  .switch span {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--muted);
    transition: transform 0.15s, background 0.15s;
  }

  .switch.on {
    background: var(--accent);
    border-color: var(--accent);
  }

  .switch.on span {
    transform: translateX(20px);
    background: #fff;
  }

  .switch.small {
    width: 36px;
    height: 20px;
  }

  .switch.small span {
    width: 14px;
    height: 14px;
  }

  .switch.small.on span {
    transform: translateX(16px);
  }

  /* Segments */
  .segments {
    display: inline-flex;
    padding: 3px;
    gap: 2px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .segments button {
    padding: 5px 12px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--muted);
    font-size: 0.86rem;
    font-weight: 600;
  }

  .segments button.on {
    background: var(--surface);
    color: var(--text);
    box-shadow: 0 1px 3px rgb(0 0 0 / 18%);
  }

  /* Lists */
  .chips {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
    max-width: 460px;
  }

  .item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 3px 4px 3px 10px;
    border-radius: 999px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    font-size: 0.86rem;
  }

  .item button {
    padding: 0 6px;
    border: 0;
    background: transparent;
    color: var(--muted);
    font-size: 1rem;
    line-height: 1;
  }

  .chips .add {
    width: 130px;
    padding: 4px 10px;
    border-radius: 999px;
    font-size: 0.86rem;
  }

  .world {
    padding: 4px 12px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--muted);
    font-size: 0.86rem;
    font-weight: 600;
  }

  .world.on {
    color: var(--accent);
    border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
    background: color-mix(in srgb, var(--accent) 12%, var(--surface-2));
  }

  .world em {
    font-style: normal;
    font-weight: 400;
  }

  .fixed {
    display: inline-flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
  }

  .fixed input {
    width: 96px;
  }

  /* Table (e.g. Discord events) */
  .table {
    display: grid;
    grid-template-columns: minmax(160px, 1fr) repeat(var(--cols), auto);
    gap: 4px 18px;
    align-items: center;
    width: 100%;
  }

  .th {
    color: var(--muted);
    font-size: 0.74rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .rname {
    font-size: 0.9rem;
    padding: 4px 0;
  }

  .cell {
    display: flex;
    justify-content: center;
  }

  .small-num {
    width: 80px;
  }

  /* Buttons */
  .ghost,
  .reset {
    padding: 5px 12px;
    font-size: 0.84rem;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .reset {
    padding: 4px 10px;
    color: var(--muted);
    font-size: 0.78rem;
  }

  .reset:hover,
  .ghost:hover:not(:disabled) {
    border-color: var(--accent);
    color: var(--text);
  }

  .chip {
    font-size: 0.78rem;
    font-weight: 700;
    padding: 3px 10px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .good-chip {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  /* Times of day: time field + add button */
  .time-add {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .chips .add.time {
    width: 7.5rem;
    flex: none;
    font-variant-numeric: tabular-nums;
  }

  .small-add {
    justify-content: flex-start;
    padding: 6px 12px;
    font-size: 0.84rem;
    white-space: nowrap;
  }

  /* Live state of a section (e.g. MRadmin: is the starter set up?) */
  .state {
    display: flex;
    align-items: flex-start;
    gap: 9px;
    margin: 10px 0 0;
    padding: 9px 12px;
    border-radius: 10px;
    font-size: 0.88rem;
    --st: var(--accent);
    border: 1px solid color-mix(in srgb, var(--st) 40%, var(--border));
    background: color-mix(in srgb, var(--st) 10%, transparent);
  }

  .state.good {
    --st: var(--good);
  }

  .state.warn {
    --st: var(--warn);
  }

  .state-dot {
    flex: none;
    width: 9px;
    height: 9px;
    margin-top: 0.38em;
    border-radius: 50%;
    background: var(--st);
  }

  .notice {
    padding: 10px 14px;
    border-radius: 10px;
    border: 1px solid var(--tn);
    color: var(--tn);
    background: color-mix(in srgb, var(--tn) 10%, transparent);
    font-weight: 600;
  }

  .notice.warn {
    --tn: var(--warn);
  }

  .notice.bad {
    --tn: var(--bad);
  }

  .error .ghost {
    margin-left: 10px;
  }

  /* Save bar */
  .savebar {
    position: fixed;
    left: 50%;
    bottom: 20px;
    z-index: 30;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px 10px 18px;
    border-radius: 14px;
    border: 1px solid color-mix(in srgb, var(--warn) 45%, var(--border));
    background: var(--surface);
    box-shadow: 0 8px 30px rgb(0 0 0 / 30%);
    white-space: nowrap;
  }

  .savebar b {
    color: var(--warn);
  }

  @container (max-width: 860px) {
    .layout {
      grid-template-columns: 1fr;
    }

    .sections {
      position: static;
      display: flex;
      flex-wrap: nowrap;
      overflow-x: auto;
      gap: 4px;
    }

    .sections .search {
      flex: 0 0 160px;
      margin: 0;
    }

    .sections button {
      flex-shrink: 0;
    }

    .file {
      display: none;
    }
  }

  @container (max-width: 600px) {
    .field {
      grid-template-columns: 1fr;
      padding: 12px 14px;
    }

    .input,
    .chips,
    .fixed {
      justify-content: flex-start;
    }

    .table {
      grid-template-columns: minmax(120px, 1fr) repeat(var(--cols), auto);
      gap: 4px 10px;
    }
  }

  /* On the phone above the bar at the bottom, over the whole width. */
  @media (max-width: 640px) {
    .savebar {
      bottom: calc(76px + env(safe-area-inset-bottom));
      left: 16px;
      right: 16px;
      transform: none;
      justify-content: flex-end;
    }

    .savebar span {
      margin-right: auto;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .switch span {
      transition: none;
    }
  }
</style>
