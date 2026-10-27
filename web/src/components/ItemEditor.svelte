<script>
  // Edits one item: type, amount, name and lore (MiniMessage), enchantments, durability, potion, dye colour, armour
  // trim, rocket flight and glint - whatever fits the item. The tooltip on the right shows the result live. On
  // "Apply" MRadmin builds the real item (POST /items/preview), so what comes back is exactly what will be stored;
  // everything the editor has no field for stays as it was (the item's full data travels along).
  import { api } from '../lib/api.js';
  import { t, errorText } from '../lib/i18n.js';
  import { items, loadItems, typeName, enchantName, effectName, potionName, gameText, pretty, roman } from '../lib/items.svelte.js';
  import { miniSpans } from '../lib/minimessage.js';
  import ItemIcon from './ItemIcon.svelte';
  import ItemTooltip from './ItemTooltip.svelte';

  /** item: the item's data or null (empty slot); onsave(item or null); onclose() */
  let { item = null, heading = '', onsave, onclose } = $props();

  loadItems();

  const blank = (type) => ({ type, amount: 1, name: null, lore: [], enchants: [], unbreakable: false, damage: 0, glint: null });
  // svelte-ignore state_referenced_locally
  let edit = $state(item ? structuredClone($state.snapshot(item)) : null);
  // svelte-ignore state_referenced_locally
  let picking = $state(!item);
  let search = $state('');
  let showAll = $state(false);
  let saving = $state(false);
  let error = $state('');
  let dialog = $state();

  $effect(() => {
    dialog?.showModal();
  });

  const entry = $derived(edit ? items.catalog?.items?.find((i) => i.id === edit.type) : null);
  const kinds = $derived(entry?.kinds ?? []);
  const stack = $derived(entry?.stack ?? edit?.maxStack ?? 64);
  const durability = $derived(entry?.durability ?? edit?.max ?? 0);
  const isBook = $derived(kinds.includes('book'));
  // An enchanted book keeps its enchantments "stored".
  const enchantKey = $derived(isBook ? 'stored' : 'enchants');

  const loreText = $derived((edit?.lore ?? []).join('\n'));

  const preview = $derived(
    edit
      ? {
          ...edit,
          title: edit.name ? miniSpans(edit.name, { i: true, color: '#ffffff' }) : null,
          loreView: (edit.lore ?? []).map((line) => miniSpans(line, { i: true, color: '#aa00aa' })),
          max: durability,
        }
      : null,
  );

  const results = $derived.by(() => {
    const list = items.catalog?.items ?? [];
    const q = search.trim().toLowerCase();
    const found = q ? list.filter((i) => i.id.includes(q.replace(/ /g, '_')) || typeName(i.id).toLowerCase().includes(q)) : list;
    return found.slice(0, 96);
  });

  const enchantments = $derived.by(() => {
    const all = items.catalog?.enchantments ?? [];
    const have = new Set((edit?.[enchantKey] ?? []).map(([id]) => id));
    return all
      .filter((e) => !have.has(e.id) && (showAll || isBook || e.items.includes(edit?.type)))
      .sort((a, b) => enchantName(a.id).localeCompare(enchantName(b.id)));
  });

  function conflict(id) {
    const e = items.catalog?.enchantments?.find((x) => x.id === id);
    return (edit?.[enchantKey] ?? []).some(([other]) => other !== id && e?.conflicts?.includes(other));
  }

  function pick(type) {
    if (!edit) {
      edit = blank(type);
    } else {
      edit.type = type;
      const next = items.catalog?.items?.find((i) => i.id === type);
      if (next && edit.amount > next.stack) edit.amount = next.stack;
      if (next && !next.durability) edit.damage = 0;
    }
    picking = false;
    search = '';
  }

  function setLore(text) {
    edit.lore = text === '' ? [] : text.split('\n');
  }

  function addEnchant(id) {
    if (!id) return;
    const e = items.catalog?.enchantments?.find((x) => x.id === id);
    edit[enchantKey] = [...(edit[enchantKey] ?? []), [id, e?.max ?? 1]];
  }

  function removeEnchant(i) {
    edit[enchantKey] = edit[enchantKey].filter((_, j) => j !== i);
  }

  function ensurePotion() {
    if (!edit.potion) edit.potion = { base: null, color: null, effects: [] };
  }

  function addEffect(id) {
    if (!id) return;
    ensurePotion();
    edit.potion.effects = [...edit.potion.effects, { type: id, level: 1, seconds: 60, ambient: false, particles: true, icon: true }];
  }

  async function apply() {
    error = '';
    saving = true;
    try {
      const body = $state.snapshot(edit);
      // Only what the editor sends - the server fills in the rest from "data".
      delete body.title;
      delete body.loreView;
      const answer = await api('POST', '/items/preview', { item: body });
      onsave?.(answer.item);
      close();
    } catch (e) {
      error = errorText(e);
    } finally {
      saving = false;
    }
  }

  function clear() {
    onsave?.(null);
    close();
  }

  function close() {
    dialog?.close();
    onclose?.();
  }

  const hex = (c) => c ?? '#a06540';

  /** Name of a base potion, with extended / strong. */
  function potionLabel(id) {
    if (id.startsWith('long_')) return potionName(id) + ' (' + t('items.potionLong') + ')';
    if (id.startsWith('strong_')) return potionName(id) + ' (' + t('items.potionStrong') + ')';
    return potionName(id);
  }
</script>

<dialog bind:this={dialog} onclose={() => onclose?.()} onclick={(e) => e.target === dialog && close()}>
  <div class="head">
    <h3>{heading || t('items.edit')}</h3>
    <button class="secondary small-button" onclick={close} aria-label={t('items.cancel')}>✕</button>
  </div>

  {#if !items.ready}
    <p class="muted">{t('items.loading')}</p>
  {:else if picking}
    <input type="search" placeholder={t('items.search')} bind:value={search} />
    {#if items.icons.state === 'loading'}<p class="hint">{t('items.iconsLoading')}</p>{/if}
    <div class="results">
      {#each results as r (r.id)}
        <div class="result" role="button" tabindex="0" title={typeName(r.id)} onclick={() => pick(r.id)}
          onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), pick(r.id))}>
          <ItemIcon item={{ type: r.id, amount: 1, nameKey: r.key }} size={36} />
          <span>{typeName(r.id)}</span>
        </div>
      {/each}
    </div>
    {#if results.length === 0}<p class="muted">{t('items.noResults')}</p>{/if}
    <div class="actions">
      {#if edit}<button class="secondary" onclick={() => (picking = false)}>{t('items.back')}</button>{/if}
    </div>
  {:else if edit}
    <div class="layout">
      <div class="fields">
        <div class="type">
          <ItemIcon item={preview} size={52} />
          <div>
            <strong>{typeName(edit.type)}</strong>
            <div class="muted small mono">{edit.type}</div>
          </div>
          <button class="secondary" onclick={() => (picking = true)}>{t('items.change')}</button>
        </div>

        <div class="row">
          <div class="field narrow">
            <label for="ie-amount">{t('items.amount')}</label>
            <input id="ie-amount" type="number" min="1" max={stack} bind:value={edit.amount} />
            <div class="hint">{t('items.amountMax', { n: stack })}</div>
          </div>
          <div class="field grow">
            <label for="ie-name">{t('items.name')}</label>
            <input id="ie-name" maxlength="256" placeholder={typeName(edit.type)} value={edit.name ?? ''}
              oninput={(e) => (edit.name = e.currentTarget.value || null)} />
            <div class="hint">{t('items.nameHint')}</div>
          </div>
        </div>

        <div class="field">
          <label for="ie-lore">{t('items.lore')}</label>
          <textarea id="ie-lore" rows="3" value={loreText} oninput={(e) => setLore(e.currentTarget.value)}></textarea>
          <div class="hint">{t('items.loreHint')}</div>
        </div>

        <fieldset>
          <legend>{isBook ? t('items.stored') : t('items.enchants')}</legend>
          {#each edit[enchantKey] ?? [] as pair, i}
            <div class="line">
              <span class="grow">{enchantName(pair[0])}</span>
              {#if conflict(pair[0])}<span class="badge warn" title={t('items.conflictHint')}>{t('items.conflict')}</span>{/if}
              <input class="level" type="number" min="1" max="255" bind:value={pair[1]} aria-label={t('items.level')} />
              <span class="muted small roman">{roman(pair[1])}</span>
              <button class="danger small-button" onclick={() => removeEnchant(i)} aria-label={t('items.remove')}>✕</button>
            </div>
          {/each}
          <div class="line">
            <select class="grow" onchange={(e) => { addEnchant(e.currentTarget.value); e.currentTarget.value = ''; }}>
              <option value="">{t('items.addEnchant')}</option>
              {#each enchantments as e}
                <option value={e.id}>{enchantName(e.id)} (max {roman(e.max)})</option>
              {/each}
            </select>
          </div>
          {#if !isBook}
            <label class="check"><input type="checkbox" bind:checked={showAll} /> {t('items.showAllEnchants')}</label>
          {/if}
          <label class="check">
            {t('items.glint')}
            <select value={edit.glint == null ? '' : String(edit.glint)}
              onchange={(e) => (edit.glint = e.currentTarget.value === '' ? null : e.currentTarget.value === 'true')}>
              <option value="">{t('items.glintNormal')}</option>
              <option value="true">{t('items.glintOn')}</option>
              <option value="false">{t('items.glintOff')}</option>
            </select>
          </label>
        </fieldset>

        {#if durability > 0}
          <fieldset>
            <legend>{t('items.durability')}</legend>
            <label class="check"><input type="checkbox" bind:checked={edit.unbreakable} /> {t('items.unbreakable')}</label>
            {#if !edit.unbreakable}
              <div class="line">
                <input class="grow" type="range" min="1" max={durability} value={durability - (edit.damage ?? 0)}
                  oninput={(e) => (edit.damage = durability - Number(e.currentTarget.value))} />
                <span class="small">{durability - (edit.damage ?? 0)} / {durability}</span>
              </div>
            {/if}
          </fieldset>
        {/if}

        {#if kinds.includes('potion')}
          <fieldset>
            <legend>{t('items.potion')}</legend>
            <div class="line">
              <select class="grow" value={edit.potion?.base ?? ''} onchange={(e) => { ensurePotion(); edit.potion.base = e.currentTarget.value || null; }}>
                <option value="">{t('items.potionNone')}</option>
                {#each items.catalog.potions as p}
                  <option value={p.id}>{potionLabel(p.id)}</option>
                {/each}
              </select>
            </div>
            <label class="check">
              <input type="checkbox" checked={!!edit.potion?.color}
                onchange={(e) => { ensurePotion(); edit.potion.color = e.currentTarget.checked ? '#3f76e4' : null; }} />
              {t('items.potionColor')}
              {#if edit.potion?.color}<input type="color" bind:value={edit.potion.color} />{/if}
            </label>
            {#each edit.potion?.effects ?? [] as effect, i}
              <div class="effect">
                <div class="line">
                  <span class="grow">{effectName(effect.type)}</span>
                  <button class="danger small-button" onclick={() => (edit.potion.effects = edit.potion.effects.filter((_, j) => j !== i))} aria-label={t('items.remove')}>✕</button>
                </div>
                <div class="line">
                  <label class="inline">{t('items.level')} <input class="level" type="number" min="1" max="255" bind:value={effect.level} /></label>
                  <label class="inline">{t('items.seconds')}
                    <input class="level" type="number" min="1" max="86400" disabled={effect.seconds === -1}
                      value={effect.seconds === -1 ? '' : effect.seconds} oninput={(e) => (effect.seconds = Number(e.currentTarget.value) || 1)} />
                  </label>
                  <label class="inline"><input type="checkbox" checked={effect.seconds === -1}
                    onchange={(e) => (effect.seconds = e.currentTarget.checked ? -1 : 60)} /> {t('items.endless')}</label>
                </div>
                <div class="line small">
                  <label class="inline"><input type="checkbox" bind:checked={effect.particles} /> {t('items.particles')}</label>
                  <label class="inline"><input type="checkbox" bind:checked={effect.icon} /> {t('items.icon')}</label>
                  <label class="inline"><input type="checkbox" bind:checked={effect.ambient} /> {t('items.ambient')}</label>
                </div>
              </div>
            {/each}
            <select onchange={(e) => { addEffect(e.currentTarget.value); e.currentTarget.value = ''; }}>
              <option value="">{t('items.addEffect')}</option>
              {#each [...items.catalog.effects].sort((a, b) => effectName(a.id).localeCompare(effectName(b.id))) as e}
                <option value={e.id}>{effectName(e.id)}</option>
              {/each}
            </select>
          </fieldset>
        {/if}

        {#if kinds.includes('dye')}
          <fieldset>
            <legend>{t('items.color')}</legend>
            <label class="check">
              <input type="checkbox" checked={!!edit.color} onchange={(e) => (edit.color = e.currentTarget.checked ? hex(edit.color) : null)} />
              {t('items.colorOwn')}
              {#if edit.color}<input type="color" bind:value={edit.color} />{/if}
            </label>
          </fieldset>
        {/if}

        {#if kinds.includes('trim')}
          <fieldset>
            <legend>{t('items.trim')}</legend>
            <div class="line">
              <select class="grow" value={edit.trim?.pattern ?? ''}
                onchange={(e) => (edit.trim = e.currentTarget.value ? { pattern: e.currentTarget.value, material: edit.trim?.material ?? items.catalog.trimMaterials[0]?.id } : null)}>
                <option value="">{t('items.trimNone')}</option>
                {#each items.catalog.trimPatterns as p}<option value={p.id}>{gameText(p.key) ?? pretty(p.id)}</option>{/each}
              </select>
              {#if edit.trim}
                <select class="grow" bind:value={edit.trim.material}>
                  {#each items.catalog.trimMaterials as m}<option value={m.id}>{gameText(m.key) ?? pretty(m.id)}</option>{/each}
                </select>
              {/if}
            </div>
          </fieldset>
        {/if}

        {#if kinds.includes('rocket')}
          <fieldset>
            <legend>{t('items.flight')}</legend>
            <select value={String(edit.flight ?? 1)} onchange={(e) => (edit.flight = Number(e.currentTarget.value))}>
              {#each [1, 2, 3] as n}<option value={String(n)}>{n}</option>{/each}
            </select>
          </fieldset>
        {/if}
      </div>

      <div class="preview">
        <div class="muted small">{t('items.preview')}</div>
        <ItemTooltip item={preview} />
      </div>
    </div>

    {#if error}<div class="error">{error}</div>{/if}
    <div class="actions">
      {#if item}<button class="danger" onclick={clear}>{t('items.clear')}</button>{/if}
      <span class="grow"></span>
      <button class="secondary" onclick={close}>{t('items.cancel')}</button>
      <button onclick={apply} disabled={saving || !edit.amount || edit.amount < 1}>{t('items.apply')}</button>
    </div>
  {/if}
</dialog>

<style>
  dialog {
    width: min(860px, calc(100vw - 32px));
    max-height: calc(100dvh - 32px);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
    color: var(--text);
    padding: 18px;
    box-shadow: var(--shadow);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 45%);
  }

  .head,
  .type,
  .line,
  .actions,
  .row {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .head {
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .head h3 {
    margin: 0;
  }

  .row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .grow {
    flex: 1;
    min-width: 0;
  }

  .field.narrow {
    width: 110px;
  }

  .field.grow {
    min-width: 200px;
  }

  .layout {
    display: grid;
    grid-template-columns: 1fr 260px;
    gap: 18px;
    align-items: start;
  }

  .fields {
    display: grid;
    gap: 14px;
  }

  .preview {
    position: sticky;
    top: 0;
    display: grid;
    gap: 6px;
  }

  @media (max-width: 720px) {
    .layout {
      grid-template-columns: 1fr;
    }

    .preview {
      position: static;
      order: -1;
    }
  }

  .type {
    gap: 12px;
  }

  .type > div {
    flex: 1;
  }

  fieldset {
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
    display: grid;
    gap: 8px;
    margin: 0;
  }

  legend {
    font-weight: 600;
    font-size: 0.9rem;
    padding: 0 4px;
  }

  textarea,
  select {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 9px 10px;
  }

  .line select {
    width: auto;
  }

  .line select.grow {
    width: 100%;
  }

  input.level {
    width: 76px;
    padding: 7px 8px;
  }

  input[type='color'] {
    width: 44px;
    height: 30px;
    padding: 2px;
  }

  input[type='checkbox'] {
    width: auto;
  }

  .check,
  .inline {
    display: flex;
    gap: 8px;
    align-items: center;
    font-weight: 500;
    margin: 0;
  }

  .roman {
    width: 32px;
  }

  .small-button {
    padding: 4px 10px;
  }

  .effect {
    border-top: 1px dashed var(--border);
    padding-top: 8px;
    display: grid;
    gap: 6px;
  }

  .effect .line {
    flex-wrap: wrap;
  }

  .badge.warn {
    background: color-mix(in srgb, var(--warn) 20%, transparent);
    color: var(--warn);
  }

  .results {
    margin-top: 10px;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 6px;
    max-height: 55dvh;
    overflow-y: auto;
  }

  .result {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 8px 4px 4px;
    font-size: 0.85rem;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--surface-2);
    cursor: pointer;
  }

  .result:hover,
  .result:focus-visible {
    border-color: var(--accent);
  }

  .result :global(.slot) {
    flex: none;
    pointer-events: none;
  }

  .actions {
    margin-top: 14px;
  }
</style>
