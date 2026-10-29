<script>
  // MRpvp: one kit in the editor - the inventory like in the game (click a slot to edit its item, or switch on
  // "move" to swap two slots), name and description per language, icon, permission and the rules of the kit.
  // Nothing is stored until "Save"; then MRpvp writes kits.yml and reloads the kits (rounds already running keep
  // their kit). id = kit to edit; for a new kit id is null and copyFrom optionally names a kit to start from.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { items, typeName, effectName } from '../lib/items.svelte.js';
  import { miniSpans } from '../lib/minimessage.js';
  import ItemGrid from '../components/ItemGrid.svelte';
  import ItemIcon from '../components/ItemIcon.svelte';
  import ItemEditor from '../components/ItemEditor.svelte';
  import Toast from '../components/Toast.svelte';

  let { id = null, copyFrom = null } = $props();

  const ARMOR = { 0: 'boots', 1: 'leggings', 2: 'chestplate', 3: 'helmet' };
  const ARMOR_INDEX = { boots: 0, leggings: 1, chestplate: 2, helmet: 3 };
  const LANGUAGES = ['de', 'en'];

  let kit = $state(null);
  let arenas = $state([]);
  // slot ("0"-"35", "helmet" ... "offhand") -> item data
  let slots = $state({});
  let error = $state('');
  let message = $state('');
  let saving = $state(false);
  let dirty = $state(false);
  let moving = $state(false);
  let picked = $state(null);
  let editing = $state(null);
  let iconPicking = $state(false);

  async function load() {
    try {
      const source = id ?? copyFrom;
      if (source) {
        const data = await moduleData('mrpvp', 'kit', { id: source });
        arenas = data.arenas;
        const names = {};
        const descriptions = {};
        for (const l of LANGUAGES) {
          names[l] = data.names[l] ?? data.names[''] ?? '';
          descriptions[l] = (data.descriptions[l] ?? data.descriptions[''] ?? []).join('\n');
        }
        kit = {
          id: id ?? data.id + '_copy',
          original: id,
          names,
          descriptions,
          icon: data.icon,
          permission: data.permission,
          // Older MRpvp versions send no effects / hearts / cooldowns / minutes yet.
          rules: { effects: [], maxHearts: 0, cooldowns: [], minutes: 0, ...data.rules, arenas: [...data.rules.arenas] },
        };
        const s = {};
        for (const i of data.inventory) s[String(i.slot)] = i;
        for (const i of data.armor) s[ARMOR[i.slot]] = i;
        for (const i of data.offhand) s.offhand = i;
        slots = s;
      } else {
        arenas = (await moduleData('mrpvp', 'kits')).arenas;
        kit = {
          id: '',
          original: null,
          names: { de: '', en: '' },
          descriptions: { de: '', en: '' },
          icon: 'iron_sword',
          permission: '',
          rules: { damage: true, hitsToWin: 0, regen: null, hunger: null, arenas: [], build: false, effects: [], maxHearts: 0, cooldowns: [], minutes: 0 },
        };
        slots = {};
      }
      dirty = !id && !!copyFrom;
    } catch (e) {
      error = errorText(e);
    }
  }

  // ItemGrid wants three lists with their slot numbers.
  const grid = $derived.by(() => {
    const inventory = [];
    const armor = [];
    const offhand = [];
    for (const [slot, item] of Object.entries(slots)) {
      if (!item) continue;
      if (slot === 'offhand') offhand.push({ ...item, slot: 0 });
      else if (slot in ARMOR_INDEX) armor.push({ ...item, slot: ARMOR_INDEX[slot] });
      else inventory.push({ ...item, slot: Number(slot) });
    }
    return { inventory, armor, offhand };
  });

  function onslot(slot) {
    const key = String(slot);
    if (moving) {
      if (picked == null) {
        picked = key;
      } else {
        const a = slots[picked];
        const b = slots[key];
        slots[picked] = b;
        slots[key] = a;
        picked = null;
        dirty = true;
      }
      return;
    }
    editing = key;
  }

  function slotLabel(key) {
    if (key in ARMOR_INDEX || key === 'offhand') return t('pvp.slot.' + key);
    const n = Number(key);
    return n < 9 ? t('pvp.kits.slotHotbar', { n: n + 1 }) : t('pvp.kits.slotInventory', { n: n - 8 });
  }

  /** Only what MRpvp needs to build the item again (the display parts stay here). */
  function strip(item) {
    const { slot, title, loreView, nameKey, maxStack, max, ...rest } = $state.snapshot(item);
    if (rest.potion) delete rest.potion.baseEffects;
    return rest;
  }

  async function save() {
    error = '';
    message = '';
    saving = true;
    try {
      const names = {};
      const descriptions = {};
      const same = LANGUAGES.every((l) => kit.names[l] === kit.names[LANGUAGES[0]])
        && LANGUAGES.every((l) => kit.descriptions[l] === kit.descriptions[LANGUAGES[0]]);
      for (const l of same ? [''] : LANGUAGES) {
        const from = l === '' ? LANGUAGES[0] : l;
        names[l] = kit.names[from];
        descriptions[l] = kit.descriptions[from] === '' ? [] : kit.descriptions[from].split('\n');
      }
      const items = Object.entries(slots).filter(([, item]) => item).map(([slot, item]) => ({ slot, item: strip(item) }));
      const body = {
        id: kit.id.trim().toLowerCase(),
        original: kit.original,
        names,
        descriptions,
        icon: kit.icon,
        permission: kit.permission.trim(),
        rules: $state.snapshot(kit.rules),
        items,
      };
      await moduleAction('mrpvp', { action: 'kitSave', kit: body });
      dirty = false;
      message = t('pvp.kits.saved');
      if (body.id !== id) {
        go('/pvp/kits/' + body.id, true);
      } else {
        await load();
      }
    } catch (e) {
      error = errorText(e);
    } finally {
      saving = false;
    }
  }

  async function remove() {
    if (!confirm(t('pvp.kits.deleteConfirm', { name: title }))) return;
    try {
      await moduleAction('mrpvp', { action: 'kitDelete', id });
      dirty = false;
      go('/pvp/kits');
    } catch (e) {
      error = errorText(e);
    }
  }

  function back() {
    if (dirty && !confirm(t('pvp.kits.leaveConfirm'))) return;
    go('/pvp/kits');
  }

  function toggleArena(arena) {
    const list = kit.rules.arenas;
    kit.rules.arenas = list.includes(arena) ? list.filter((a) => a !== arena) : [...list, arena];
    dirty = true;
  }

  /** The kit's name in the page's language without MiniMessage tags. */
  const title = $derived(kit ? miniSpans(kit.names[lang] || kit.names.en || kit.id).map((s) => s.text).join('') : '');

  // Arenas the kit is limited to that were deleted since - shown so they can be ticked off (saving refuses them).
  const missingArenas = $derived(kit ? kit.rules.arenas.filter((a) => !arenas.some((x) => x.id === a)) : []);
  const nowhere = $derived(kit ? kit.rules.arenas.length > 0 && missingArenas.length === kit.rules.arenas.length : false);

  // Item types in the kit - those can get a cooldown.
  const kitTypes = $derived([...new Set(Object.values(slots).filter(Boolean).map((i) => i.type))].sort((a, b) => typeName(a).localeCompare(typeName(b))));

  const choice = (v) => (v == null ? '' : String(v));
  const fromChoice = (v) => (v === '' ? null : v === 'true');

  load();
</script>

<svelte:window onbeforeunload={(e) => dirty && e.preventDefault()} />

<div class="stack">
  <button class="back" onclick={back}>← {t('pvp.kits.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if !kit}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else}
    <div class="head">
      <ItemIcon item={{ type: kit.icon, amount: 1 }} size={48} />
      <div class="grow">
        <h1>{id ? title : t('pvp.kits.newTitle')}</h1>
        {#if id}<div class="muted small mono">{id}</div>{/if}
      </div>
      <div class="buttons">
        {#if id}
          <button class="secondary" onclick={() => go('/pvp/new-kit/' + id)}>{t('pvp.kits.copy')}</button>
          <button class="danger" onclick={remove}>{t('pvp.kits.delete')}</button>
        {/if}
        <button onclick={save} disabled={saving || !kit.id.trim()}>{saving ? t('pvp.kits.saving') : t('pvp.kits.save')}</button>
      </div>
    </div>

    <div class="layout">
      <section class="card">
        <div class="section-head">
          <h3>{t('pvp.kits.inventory')}</h3>
          <label class="check"><input type="checkbox" bind:checked={moving} onchange={() => (picked = null)} /> {t('pvp.kits.move')}</label>
        </div>
        <p class="muted small">{moving ? (picked == null ? t('pvp.kits.moveHint1') : t('pvp.kits.moveHint2')) : t('pvp.kits.inventoryHint')}</p>
        <ItemGrid inventory={grid.inventory} armor={grid.armor} offhand={grid.offhand} {onslot} selected={picked == null ? null : picked in ARMOR_INDEX || picked === 'offhand' ? picked : Number(picked)} size={44} />
      </section>

      <section class="card stack">
        <h3>{t('pvp.kits.general')}</h3>
        <div class="field">
          <label for="kit-id">{t('pvp.kits.id')}</label>
          <input id="kit-id" class="mono" maxlength="32" bind:value={kit.id} oninput={() => (dirty = true)} />
          <div class="hint">{id ? t('pvp.kits.idRenameHint') : t('pvp.kits.idHint')}</div>
        </div>
        {#each LANGUAGES as l}
          <div class="field">
            <label for="kit-name-{l}">{t('pvp.kits.name')} ({l.toUpperCase()})</label>
            <input id="kit-name-{l}" maxlength="128" bind:value={kit.names[l]} oninput={() => (dirty = true)} />
          </div>
          <div class="field">
            <label for="kit-desc-{l}">{t('pvp.kits.description')} ({l.toUpperCase()})</label>
            <textarea id="kit-desc-{l}" rows="2" bind:value={kit.descriptions[l]} oninput={() => (dirty = true)}></textarea>
          </div>
        {/each}
        <div class="hint">{t('pvp.kits.textHint')}</div>
        <div class="field">
          <span class="label">{t('pvp.kits.icon')}</span>
          <div class="icon-row">
            <ItemIcon item={{ type: kit.icon, amount: 1 }} size={40} />
            <span class="grow">{typeName(kit.icon)}</span>
            <button class="secondary" onclick={() => (iconPicking = true)}>{t('pvp.kits.iconChange')}</button>
          </div>
        </div>
        <div class="field">
          <label for="kit-perm">{t('pvp.kits.permission')}</label>
          <input id="kit-perm" class="mono" maxlength="64" placeholder="mrpvp.kit.{kit.id || 'name'}" bind:value={kit.permission} oninput={() => (dirty = true)} />
          <div class="hint">{t('pvp.kits.permissionHint')}</div>
        </div>
      </section>

      <section class="card stack">
        <h3>{t('pvp.kits.rules')}</h3>
        <label class="check"><input type="checkbox" checked={!kit.rules.damage} onchange={(e) => { kit.rules.damage = !e.currentTarget.checked; dirty = true; }} /> {t('pvp.kits.rule.noDamage')}</label>
        <div class="field">
          <label for="kit-hits">{t('pvp.kits.rule.hits')}</label>
          <input id="kit-hits" type="number" min="0" max="1000" bind:value={kit.rules.hitsToWin} oninput={() => (dirty = true)} />
          <div class="hint">{t('pvp.kits.rule.hitsHint')}</div>
        </div>
        <div class="field">
          <label for="kit-regen">{t('pvp.kits.rule.regen')}</label>
          <select id="kit-regen" value={choice(kit.rules.regen)} onchange={(e) => { kit.rules.regen = fromChoice(e.currentTarget.value); dirty = true; }}>
            <option value="">{t('pvp.kits.rule.playersChoose')}</option>
            <option value="true">{t('pvp.kits.rule.on')}</option>
            <option value="false">{t('pvp.kits.rule.off')}</option>
          </select>
        </div>
        <div class="field">
          <label for="kit-hunger">{t('pvp.kits.rule.hunger')}</label>
          <select id="kit-hunger" value={choice(kit.rules.hunger)} onchange={(e) => { kit.rules.hunger = fromChoice(e.currentTarget.value); dirty = true; }}>
            <option value="">{t('pvp.kits.rule.playersChoose')}</option>
            <option value="true">{t('pvp.kits.rule.on')}</option>
            <option value="false">{t('pvp.kits.rule.off')}</option>
          </select>
        </div>
        <label class="check"><input type="checkbox" bind:checked={kit.rules.build} onchange={() => (dirty = true)} /> {t('pvp.kits.rule.build')}</label>
        <div class="row2">
          <div class="field">
            <label for="kit-minutes">{t('pvp.kits.rule.minutes')}</label>
            <input id="kit-minutes" type="number" min="0" max="120" bind:value={kit.rules.minutes} oninput={() => (dirty = true)} />
            <div class="hint">{t('pvp.kits.rule.minutesHint')}</div>
          </div>
          <div class="field">
            <label for="kit-hearts">{t('pvp.kits.rule.hearts')}</label>
            <input id="kit-hearts" type="number" min="0" max="100" bind:value={kit.rules.maxHearts} oninput={() => (dirty = true)} />
            <div class="hint">{t('pvp.kits.rule.heartsHint')}</div>
          </div>
        </div>
        <div class="field">
          <span class="label">{t('pvp.kits.rule.effects')}</span>
          <div class="hint">{t('pvp.kits.rule.effectsHint')}</div>
          {#each kit.rules.effects as e, i}
            <div class="line">
              <span class="grow">{effectName(e.type)}</span>
              <input class="small-num" type="number" min="1" max="10" bind:value={e.level} oninput={() => (dirty = true)} aria-label={t('items.level')} />
              <button class="danger tiny" onclick={() => { kit.rules.effects = kit.rules.effects.filter((_, j) => j !== i); dirty = true; }} aria-label={t('items.remove')}>✕</button>
            </div>
          {/each}
          <select onchange={(e) => { const v = e.currentTarget.value; if (v) { kit.rules.effects = [...kit.rules.effects, { type: v, level: 1 }]; dirty = true; } e.currentTarget.value = ''; }}>
            <option value="">{t('items.addEffect')}</option>
            {#each (items.catalog?.effects ?? []).filter((x) => !kit.rules.effects.some((e) => e.type === x.id)).sort((a, b) => effectName(a.id).localeCompare(effectName(b.id))) as x}
              <option value={x.id}>{effectName(x.id)}</option>
            {/each}
          </select>
        </div>
        <div class="field">
          <span class="label">{t('pvp.kits.rule.cooldowns')}</span>
          <div class="hint">{t('pvp.kits.rule.cooldownsHint')}</div>
          {#each kit.rules.cooldowns as c, i}
            <div class="line">
              <ItemIcon item={{ type: c.item, amount: 1 }} size={30} />
              <span class="grow">{typeName(c.item)}</span>
              <input class="small-num" type="number" min="1" max="3600" bind:value={c.seconds} oninput={() => (dirty = true)} aria-label={t('pvp.kits.rule.seconds')} />
              <span class="muted small">s</span>
              <button class="danger tiny" onclick={() => { kit.rules.cooldowns = kit.rules.cooldowns.filter((_, j) => j !== i); dirty = true; }} aria-label={t('items.remove')}>✕</button>
            </div>
          {/each}
          <select onchange={(e) => { const v = e.currentTarget.value; if (v) { kit.rules.cooldowns = [...kit.rules.cooldowns, { item: v, seconds: 5 }]; dirty = true; } e.currentTarget.value = ''; }}>
            <option value="">{t('pvp.kits.rule.addCooldown')}</option>
            {#each kitTypes.filter((type) => !kit.rules.cooldowns.some((c) => c.item === type)) as type}
              <option value={type}>{typeName(type)}</option>
            {/each}
          </select>
        </div>
        <div class="field">
          <span class="label">{t('pvp.kits.rule.arenas')}</span>
          <div class="hint">{t('pvp.kits.rule.arenasHint')}</div>
          <div class="arenas">
            {#each arenas as a (a.id)}
              <label class="check" class:off={!a.enabled}>
                <input type="checkbox" checked={kit.rules.arenas.includes(a.id)} onchange={() => toggleArena(a.id)} />
                {a.name}{a.build ? ' · ' + t('pvp.build') : ''}{a.enabled ? '' : ' (' + t('pvp.kits.arenaOff') + ')'}
              </label>
            {/each}
            {#each missingArenas as a (a)}
              <label class="check missing">
                <input type="checkbox" checked onchange={() => toggleArena(a)} />
                <span class="mono">{a}</span> ({t('pvp.kits.arenaMissing')})
              </label>
            {/each}
          </div>
          {#if missingArenas.length}<div class="error">{nowhere ? t('pvp.kits.arenaNowhere') : t('pvp.kits.arenaMissingHint')}</div>{/if}
        </div>
      </section>
    </div>
  {/if}
</div>

{#if editing != null}
  <ItemEditor item={slots[editing] ?? null} heading={slotLabel(editing)}
    onsave={(item) => { slots[editing] = item; dirty = true; }}
    onclose={() => (editing = null)} />
{/if}

{#if iconPicking}
  <ItemEditor item={null} heading={t('pvp.kits.icon')}
    onsave={(item) => { if (item) { kit.icon = item.type; dirty = true; } }}
    onclose={() => (iconPicking = false)} />
{/if}

<style>
  .back {
    justify-self: start;
    background: none;
    border: 0;
    padding: 0;
    color: var(--text);
  }

  .head,
  .icon-row,
  .section-head,
  .buttons {
    display: flex;
    gap: 10px;
    align-items: center;
    flex-wrap: wrap;
  }

  .head h1 {
    margin: 0;
  }

  .section-head {
    justify-content: space-between;
  }

  .section-head h3 {
    margin: 0;
  }

  .grow {
    flex: 1;
    min-width: 0;
  }

  .layout {
    display: grid;
    gap: 14px;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }

  .layout > section:first-child {
    grid-column: 1 / -1;
  }

  @media (max-width: 860px) {
    .layout {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  section {
    padding: 14px 16px;
    display: grid;
    gap: 10px;
  }

  .label {
    display: block;
    font-weight: 600;
    font-size: 0.9rem;
    margin-bottom: 6px;
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

  input[type='checkbox'] {
    width: auto;
  }

  .check {
    display: flex;
    gap: 8px;
    align-items: center;
    font-weight: 500;
    margin: 0;
  }

  .check.off {
    color: var(--muted);
  }

  .check.missing {
    color: var(--bad);
  }

  .row2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .line {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-top: 6px;
  }

  input.small-num {
    width: 80px;
    padding: 7px 8px;
  }

  .tiny {
    padding: 4px 9px;
  }

  .field select {
    margin-top: 6px;
  }

  .arenas {
    display: grid;
    gap: 6px;
    margin-top: 6px;
  }
</style>
