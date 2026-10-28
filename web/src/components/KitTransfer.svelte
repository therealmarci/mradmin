<script>
  // MRpvp kits as a file: export (choose kits, download a .json) and import (choose a file, see per kit what would
  // happen, map arenas this server does not have, then each kit is saved through "kitSave" - with the same checks as
  // the kit editor). mode = 'export' | 'import'; kits / arenas from the kit list; ondone() after an import.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import ItemIcon from './ItemIcon.svelte';
  import { miniSpans } from '../lib/minimessage.js';

  let { mode, kits, arenas, onclose, ondone } = $props();

  let dialog = $state();
  let error = $state('');
  let busy = $state(false);
  // export
  // svelte-ignore state_referenced_locally
  let chosen = $state(Object.fromEntries(kits.map((k) => [k.id, true])));
  // import
  let file = $state(null); // {name, minecraft, kits: [...]}
  let plan = $state([]); // per kit: {kit, take, replace, id, arenaMap: {old: new|''}, result}
  let finished = $state(false);

  $effect(() => {
    dialog?.showModal();
  });

  function close() {
    dialog?.close();
    onclose?.();
  }

  const plain = (text) => miniSpans(text ?? '').map((s) => s.text).join('');
  const nameOf = (k) => plain(k.names?.[lang] ?? k.names?.en ?? k.names?.[''] ?? k.id);
  const exists = (id) => kits.some((k) => k.id === id);
  const known = (arena) => arenas.some((a) => a.id === arena);

  async function download() {
    error = '';
    busy = true;
    try {
      const ids = Object.entries(chosen).filter(([, on]) => on).map(([id]) => id);
      const answer = await moduleData('mrpvp', 'kitExport', { ids: ids.length === kits.length ? '' : ids.join(',') });
      const blob = new Blob([answer.content], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = answer.name;
      a.click();
      URL.revokeObjectURL(url);
      close();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  /** Next free id: "pvp" -> "pvp_2", "pvp_3" ... */
  function freeId(id) {
    const base = id.length > 29 ? id.slice(0, 29) : id;
    for (let n = 2; ; n++) if (!exists(base + '_' + n) && !plan.some((p) => p.id === base + '_' + n)) return base + '_' + n;
  }

  async function pick(e) {
    error = '';
    finished = false;
    const f = e.currentTarget.files?.[0];
    if (!f) return;
    try {
      const data = JSON.parse(await f.text());
      if (data?.format !== 'mrpvp-kits' || !Array.isArray(data.kits)) throw new Error();
      file = { name: f.name, minecraft: data.minecraft ?? '', kits: data.kits };
      plan = [];
      for (const k of data.kits) {
        const arenaMap = {};
        for (const a of k.rules?.arenas ?? []) if (!known(a)) arenaMap[a] = '';
        plan.push({ kit: k, take: true, replace: false, id: exists(k.id) ? '' : k.id, arenaMap, result: null });
      }
      for (const p of plan) if (!p.id) p.id = freeId(p.kit.id);
    } catch {
      file = null;
      plan = [];
      error = t('pvp.kits.import.invalid');
    }
  }

  async function run() {
    error = '';
    busy = true;
    for (const p of plan) {
      if (!p.take) continue;
      const k = $state.snapshot(p.kit);
      const id = p.replace ? k.id : p.id;
      const arenasAfter = [];
      for (const a of k.rules?.arenas ?? []) {
        const mapped = known(a) ? a : p.arenaMap[a];
        if (mapped && !arenasAfter.includes(mapped)) arenasAfter.push(mapped);
      }
      const body = {
        id,
        original: p.replace && exists(id) ? id : null,
        names: k.names ?? {},
        descriptions: k.descriptions ?? {},
        icon: k.icon,
        permission: k.permission ?? '',
        rules: { ...(k.rules ?? {}), arenas: arenasAfter },
        items: k.items ?? [],
      };
      try {
        await moduleAction('mrpvp', { action: 'kitSave', kit: body });
        p.result = { ok: true, text: t('pvp.kits.import.done', { id }) };
      } catch (e) {
        p.result = { ok: false, text: errorText(e) };
      }
    }
    busy = false;
    finished = true;
    ondone?.();
  }

</script>

<dialog bind:this={dialog} onclose={() => onclose?.()}>
  <div class="head">
    <h3>{mode === 'export' ? t('pvp.kits.export.title') : t('pvp.kits.import.title')}</h3>
    <button class="secondary tiny" onclick={close} aria-label={t('items.cancel')}>✕</button>
  </div>

  {#if mode === 'export'}
    <p class="muted small">{t('pvp.kits.export.hint')}</p>
    <div class="actions-top">
      <button class="secondary tiny" onclick={() => kits.forEach((k) => (chosen[k.id] = true))}>{t('pvp.kits.export.all')}</button>
      <button class="secondary tiny" onclick={() => kits.forEach((k) => (chosen[k.id] = false))}>{t('pvp.kits.export.none')}</button>
    </div>
    <div class="list">
      {#each kits as k (k.id)}
        <label class="row">
          <input type="checkbox" bind:checked={chosen[k.id]} />
          <ItemIcon item={{ type: k.icon, amount: 1 }} size={30} />
          <span class="grow">{k.name} <span class="muted small mono">{k.id}</span></span>
        </label>
      {/each}
    </div>
    {#if error}<div class="error">{error}</div>{/if}
    <div class="actions">
      <button class="secondary" onclick={close}>{t('items.cancel')}</button>
      <button onclick={download} disabled={busy || !Object.values(chosen).some(Boolean)}>{t('pvp.kits.export.download')}</button>
    </div>
  {:else}
    <p class="muted small">{t('pvp.kits.import.hint')}</p>
    <input type="file" accept=".json,application/json" onchange={pick} />
    {#if file}
      {#if file.minecraft}<p class="small muted">{t('pvp.kits.import.version', { version: file.minecraft })}</p>{/if}
      <div class="list">
        {#each plan as p}
          <div class="card item">
            <label class="row">
              <input type="checkbox" bind:checked={p.take} disabled={finished} />
              <ItemIcon item={{ type: p.kit.icon, amount: 1 }} size={30} />
              <span class="grow"><strong>{nameOf(p.kit)}</strong> <span class="muted small mono">{p.kit.id}</span></span>
              <span class="muted small">{t('pvp.kits.itemCount', { n: p.kit.items?.length ?? 0 })}</span>
            </label>
            {#if p.take && !finished}
              {#if exists(p.kit.id)}
                <div class="sub">
                  <span class="warn-text small">{t('pvp.kits.import.exists')}</span>
                  <label class="inline"><input type="radio" bind:group={p.replace} value={false} /> {t('pvp.kits.import.asNew')}</label>
                  {#if !p.replace}<input class="mono id" maxlength="32" bind:value={p.id} />{/if}
                  <label class="inline"><input type="radio" bind:group={p.replace} value={true} /> {t('pvp.kits.import.replace')}</label>
                </div>
              {/if}
              {#each Object.keys(p.arenaMap) as a}
                <div class="sub">
                  <span class="small">{t('pvp.kits.import.arenaMissing', { arena: a })}</span>
                  <select bind:value={p.arenaMap[a]}>
                    <option value="">{t('pvp.kits.import.arenaDrop')}</option>
                    {#each arenas as x (x.id)}<option value={x.id}>{x.name}</option>{/each}
                  </select>
                </div>
              {/each}
            {/if}
            {#if p.result}<div class="small" class:bad-text={!p.result.ok} class:good-text={p.result.ok}>{p.result.text}</div>{/if}
          </div>
        {/each}
      </div>
    {/if}
    {#if error}<div class="error">{error}</div>{/if}
    <div class="actions">
      <button class="secondary" onclick={close}>{finished ? t('pvp.kits.import.close') : t('items.cancel')}</button>
      {#if file && !finished}
        <button onclick={run} disabled={busy || !plan.some((p) => p.take)}>{t('pvp.kits.import.start', { n: plan.filter((p) => p.take).length })}</button>
      {/if}
    </div>
  {/if}
</dialog>

<style>
  dialog {
    width: min(720px, calc(100vw - 32px));
    max-height: calc(100dvh - 32px);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    color: var(--text);
    padding: 18px;
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 45%);
  }

  .head,
  .actions,
  .actions-top,
  .row,
  .sub {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }

  .head {
    justify-content: space-between;
  }

  .head h3 {
    margin: 0;
  }

  .actions {
    justify-content: flex-end;
    margin-top: 14px;
  }

  .list {
    display: grid;
    gap: 6px;
    margin-top: 10px;
    max-height: 55dvh;
    overflow-y: auto;
  }

  .item {
    padding: 8px 10px;
    display: grid;
    gap: 6px;
  }

  .row {
    font-weight: 500;
    margin: 0;
  }

  .grow {
    flex: 1;
    min-width: 0;
  }

  .sub {
    padding-left: 30px;
  }

  .inline {
    display: flex;
    gap: 6px;
    align-items: center;
    font-weight: 500;
    margin: 0;
  }

  input[type='checkbox'],
  input[type='radio'] {
    width: auto;
  }

  input.id {
    width: 180px;
    padding: 6px 8px;
  }

  select {
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 6px 8px;
  }

  .tiny {
    padding: 4px 9px;
  }

  .warn-text {
    color: var(--warn);
  }

  .bad-text {
    color: var(--bad);
  }

  .good-text {
    color: var(--good);
  }
</style>
