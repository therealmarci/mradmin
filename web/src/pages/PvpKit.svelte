<script>
  // MRpvp: one kit in the editor - the inventory like in the game, name and description per language, icon,
  // permission and the rules of the kit. Slots: click = edit the item, drag with the mouse = move (Ctrl = copy),
  // right click = copy / cut / paste / delete (also Ctrl+C/X/V and Del over a slot). On touch the modes "move"
  // (tap two slots, they swap) and "copy" (tap the source, then every slot that gets a copy) do the same.
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
  let mode = $state('edit'); // 'edit' | 'move' | 'copy'
  let picked = $state(null);
  let clipboard = $state(null); // item data
  let hovered = null;
  let contextMenu = $state(null); // { slot, x, y }
  let editing = $state(null);
  let iconPicking = $state(false);
  // The language whose name and description are being edited.
  let textLang = $state(LANGUAGES.includes(lang) ? lang : LANGUAGES[0]);

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
    if (mode === 'move') {
      if (picked == null) {
        if (slots[key]) picked = key;
      } else {
        swap(picked, key);
        picked = null;
      }
      return;
    }
    if (mode === 'copy') {
      if (picked == null) {
        if (slots[key]) picked = key;
      } else if (picked === key) {
        picked = null;
      } else {
        put(key, slots[picked]);
      }
      return;
    }
    editing = key;
  }

  function setMode(value) {
    mode = value;
    picked = null;
  }

  function swap(a, b) {
    if (a === b) return;
    const first = slots[a];
    slots[a] = slots[b];
    slots[b] = first;
    dirty = true;
  }

  /** A copy of an item into a slot (the slot's old item is replaced). */
  function put(key, item) {
    if (!item) return;
    slots[key] = $state.snapshot(item);
    dirty = true;
  }

  function clear(key) {
    if (!slots[key]) return;
    slots[key] = null;
    dirty = true;
  }

  // Dragged with the mouse: move (both swap) or copy with Ctrl.
  function dropped(from, to, copy) {
    const a = String(from);
    const b = String(to);
    if (copy) put(b, slots[a]);
    else swap(a, b);
  }

  function command(action, slot) {
    const key = String(slot);
    contextMenu = null;
    if (action === 'edit') editing = key;
    else if (action === 'copy' && slots[key]) clipboard = $state.snapshot(slots[key]);
    else if (action === 'cut' && slots[key]) {
      clipboard = $state.snapshot(slots[key]);
      clear(key);
    } else if (action === 'paste') put(key, clipboard);
    else if (action === 'delete') clear(key);
  }

  function openMenu(slot, x, y) {
    picked = null;
    contextMenu = { slot: String(slot), x: Math.min(x, window.innerWidth - 220), y: Math.min(y, window.innerHeight - 230) };
  }

  // Ctrl+C / Ctrl+X / Ctrl+V / Del over a slot - not while typing or in a dialog.
  function keydown(e) {
    if (e.key === 'Escape') {
      contextMenu = null;
      picked = null;
      return;
    }
    if (hovered == null || editing != null || iconPicking) return;
    if (e.target.closest?.('input, textarea, select, [contenteditable]')) return;
    const ctrl = e.ctrlKey || e.metaKey;
    const action = ctrl && e.key.toLowerCase() === 'c' ? 'copy' : ctrl && e.key.toLowerCase() === 'x' ? 'cut'
      : ctrl && e.key.toLowerCase() === 'v' ? 'paste' : e.key === 'Delete' || e.key === 'Backspace' ? 'delete' : null;
    if (!action) return;
    e.preventDefault();
    command(action, hovered);
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

  function setRule(key, value) {
    kit.rules[key] = value;
    dirty = true;
  }

  /** The kit's name in the page's language without MiniMessage tags. */
  const title = $derived(kit ? miniSpans(kit.names[lang] || kit.names.en || kit.id).map((s) => s.text).join('') : '');

  // The kit as the menu in the game shows it, in the language being edited.
  const previewName = $derived(kit ? miniSpans(kit.names[textLang] || kit.id || '…', { color: '#ffffff' }) : []);
  const previewLore = $derived(kit && kit.descriptions[textLang] ? kit.descriptions[textLang].split('\n').map((l) => miniSpans(l, { color: '#aaaaaa' })) : []);
  const spanStyle = (s) => [s.color ? `color:${s.color}` : '', s.b ? 'font-weight:700' : '', s.i ? 'font-style:italic' : '',
    s.u || s.s ? `text-decoration:${[s.u ? 'underline' : '', s.s ? 'line-through' : ''].join(' ').trim()}` : ''].filter(Boolean).join(';');

  // Arenas the kit is limited to that were deleted since - shown so they can be ticked off (saving refuses them).
  const missingArenas = $derived(kit ? kit.rules.arenas.filter((a) => !arenas.some((x) => x.id === a)) : []);
  const nowhere = $derived(kit ? kit.rules.arenas.length > 0 && missingArenas.length === kit.rules.arenas.length : false);

  // Item types in the kit - those can get a cooldown.
  const kitTypes = $derived([...new Set(Object.values(slots).filter(Boolean).map((i) => i.type))].sort((a, b) => typeName(a).localeCompare(typeName(b))));
  const itemCount = $derived(Object.values(slots).filter(Boolean).length);

  const choice = (v) => (v == null ? '' : String(v));
  const fromChoice = (v) => (v === '' ? null : v === 'true');

  load();
</script>

<svelte:window onbeforeunload={(e) => dirty && e.preventDefault()} onkeydown={keydown}
  onpointerdown={(e) => contextMenu && !e.target.closest('.ctx') && (contextMenu = null)} onscroll={() => (contextMenu = null)} />

<!-- Three choices for regeneration / hunger: players choose, always on, always off. -->
{#snippet tri(key)}
  <div class="seg" role="radiogroup" aria-label={t('pvp.kits.rule.' + key)}>
    {#each [['', 'choose'], ['true', 'on'], ['false', 'off']] as [v, label]}
      <button role="radio" aria-checked={choice(kit.rules[key]) === v} class:active={choice(kit.rules[key]) === v}
        title={v === '' ? t('pvp.kits.rule.playersChoose') : ''} onclick={() => setRule(key, fromChoice(v))}>{t('pvp.kits.rule.' + label)}</button>
    {/each}
  </div>
{/snippet}

<!-- An on/off rule as a row with a switch. -->
{#snippet toggle(on, text, onchange)}
  <button class="toggle" role="switch" aria-checked={on} onclick={() => onchange(!on)}>
    <span class="t-text">{text}</span>
    <span class="knob" class:on></span>
  </button>
{/snippet}

<div class="page">
  <button class="back" onclick={back}>← {t('pvp.kits.back')}</button>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if !kit}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else}
    <header class="head">
      <span class="k-icon"><ItemIcon item={{ type: kit.icon, amount: 1 }} size={44} /></span>
      <div class="intro">
        <h1>{id || title ? title || kit.id : t('pvp.kits.newTitle')}</h1>
        <div class="facts">
          {#if kit.id}<span class="chip mono">{kit.id}</span>{/if}
          <span class="chip">{t('pvp.kits.itemCount', { n: itemCount })}</span>
          {#if kit.permission}<span class="chip perm mono">{kit.permission}</span>{/if}
          {#if dirty}<span class="chip warn">● {t('pvp.kits.unsaved')}</span>{/if}
        </div>
      </div>
      <div class="buttons">
        {#if id}
          <button class="secondary" onclick={() => go('/pvp/new-kit/' + id)}>{t('pvp.kits.copy')}</button>
          <button class="danger" onclick={remove}>{t('pvp.kits.delete')}</button>
        {/if}
        <button onclick={save} disabled={saving || !kit.id.trim()}>{saving ? t('pvp.kits.saving') : t('pvp.kits.save')}</button>
      </div>
    </header>

    <!-- Inventory: edit an item or swap two slots -->
    <section class="card tile inv">
      <div class="s-head">
        <h2>{t('pvp.kits.inventory')}</h2>
        <div class="seg" role="radiogroup">
          {#each [['edit', '✎', 'pvp.kits.edit'], ['move', '⇄', 'pvp.kits.move'], ['copy', '⧉', 'pvp.kits.copyMode']] as [m, sign, key]}
            <button role="radio" aria-checked={mode === m} class:active={mode === m} onclick={() => setMode(m)}>{sign} {t(key)}</button>
          {/each}
        </div>
      </div>
      <p class="hint-line" class:active={mode !== 'edit'}>
        {#if mode === 'move'}{picked == null ? t('pvp.kits.moveHint1') : t('pvp.kits.moveHint2')}
        {:else if mode === 'copy'}{picked == null ? t('pvp.kits.copyHint1') : t('pvp.kits.copyHint2')}
        {:else}{t('pvp.kits.inventoryHint')}{/if}
      </p>
      <div class="inv-grid">
        <ItemGrid inventory={grid.inventory} armor={grid.armor} offhand={grid.offhand} {onslot} size={44}
          selected={picked == null ? null : picked in ARMOR_INDEX || picked === 'offhand' ? picked : Number(picked)}
          ondrop={dropped} onmenu={openMenu} onhover={(s) => (hovered = s)} />
      </div>
      {#if clipboard}
        <div class="clip">
          <ItemIcon item={clipboard} size={32} />
          <span class="grow-text"><span class="muted small">{t('pvp.kits.clipboard')}</span><strong>{typeName(clipboard.type)}</strong></span>
          <button class="secondary mini" onclick={() => (clipboard = null)}>{t('pvp.kits.clipboardClear')}</button>
        </div>
      {/if}
    </section>

    <div class="columns">
      <div class="col">
        <!-- General: id, texts per language with a preview, permission -->
        <section class="card tile">
          <h2>{t('pvp.kits.general')}</h2>
          <div class="field">
            <label for="kit-id">{t('pvp.kits.id')}</label>
            <input id="kit-id" class="mono" maxlength="32" bind:value={kit.id} oninput={() => (dirty = true)} />
            <div class="hint">{id ? t('pvp.kits.idRenameHint') : t('pvp.kits.idHint')}</div>
          </div>

          <div class="texts">
            <div class="s-head">
              <span class="label">{t('pvp.kits.texts')}</span>
              <div class="seg small" role="tablist">
                {#each LANGUAGES as l}
                  <button role="tab" aria-selected={textLang === l} class:active={textLang === l} onclick={() => (textLang = l)}>
                    {l.toUpperCase()}{#if !kit.names[l]}<span class="empty-dot" title="-"></span>{/if}
                  </button>
                {/each}
              </div>
            </div>
            <div class="field">
              <label for="kit-name">{t('pvp.kits.name')}</label>
              <input id="kit-name" maxlength="128" bind:value={kit.names[textLang]} oninput={() => (dirty = true)} />
            </div>
            <div class="field">
              <label for="kit-desc">{t('pvp.kits.description')}</label>
              <textarea id="kit-desc" rows="3" bind:value={kit.descriptions[textLang]} oninput={() => (dirty = true)}></textarea>
            </div>
            <div class="preview">
              <div class="s-head">
                <span class="label small">{t('pvp.kits.preview')}</span>
                <button class="secondary mini" onclick={() => (iconPicking = true)}>{t('pvp.kits.iconChangeLong')}</button>
              </div>
              <div class="mc">
                <button class="icon-pick" onclick={() => (iconPicking = true)} title={t('pvp.kits.iconChangeLong')}>
                  <ItemIcon item={{ type: kit.icon, amount: 1 }} size={40} />
                  <span class="pen" aria-hidden="true">✎</span>
                </button>
                <div class="mc-tip">
                  <div>{#each previewName as s}<span style={spanStyle(s)}>{s.text}</span>{/each}</div>
                  {#each previewLore as line}
                    <div>{#each line as s}<span style={spanStyle(s)}>{s.text}</span>{/each}</div>
                  {/each}
                </div>
              </div>
            </div>
            <div class="hint">{t('pvp.kits.textHint')}</div>
          </div>

          <div class="field">
            <label for="kit-perm">{t('pvp.kits.permission')}</label>
            <input id="kit-perm" class="mono" maxlength="64" placeholder="mrpvp.kit.{kit.id || 'name'}" bind:value={kit.permission} oninput={() => (dirty = true)} />
            <div class="hint">{t('pvp.kits.permissionHint')}</div>
          </div>
        </section>

        <!-- Arenas as chips -->
        <section class="card tile">
          <h2>{t('pvp.kits.rule.arenas')}</h2>
          <div class="hint">{t('pvp.kits.rule.arenasHint')}</div>
          <div class="arenas">
            {#each arenas as a (a.id)}
              {@const on = kit.rules.arenas.includes(a.id)}
              <button class="arena" class:on class:off={!a.enabled} aria-pressed={on} onclick={() => toggleArena(a.id)}>
                <span class="tick" aria-hidden="true">{on ? '✓' : ''}</span>
                <span>{a.name}</span>
                {#if a.build}<span class="a-tag">{t('pvp.build')}</span>{/if}
                {#if !a.enabled}<span class="a-tag">{t('pvp.kits.arenaOff')}</span>{/if}
              </button>
            {/each}
            {#each missingArenas as a (a)}
              <button class="arena on missing" aria-pressed="true" onclick={() => toggleArena(a)}>
                <span class="tick" aria-hidden="true">✓</span>
                <span class="mono">{a}</span>
                <span class="a-tag">{t('pvp.kits.arenaMissing')}</span>
              </button>
            {/each}
          </div>
          {#if missingArenas.length}<div class="error">{nowhere ? t('pvp.kits.arenaNowhere') : t('pvp.kits.arenaMissingHint')}</div>{/if}
        </section>
      </div>

      <div class="col">
        <!-- Rules of the fight -->
        <section class="card tile">
          <h2>{t('pvp.kits.rules')}</h2>
          {@render toggle(!kit.rules.damage, t('pvp.kits.rule.noDamage'), (v) => setRule('damage', !v))}
          {@render toggle(kit.rules.build, t('pvp.kits.rule.build'), (v) => setRule('build', v))}

          <div class="nums">
            <div class="num">
              <label for="kit-hits">{t('pvp.kits.rule.hits')}</label>
              <input id="kit-hits" type="number" min="0" max="1000" bind:value={kit.rules.hitsToWin} oninput={() => (dirty = true)} />
              <div class="hint">{t('pvp.kits.rule.hitsHint')}</div>
            </div>
            <div class="num">
              <label for="kit-minutes">{t('pvp.kits.rule.minutes')}</label>
              <input id="kit-minutes" type="number" min="0" max="120" bind:value={kit.rules.minutes} oninput={() => (dirty = true)} />
              <div class="hint">{t('pvp.kits.rule.minutesHint')}</div>
            </div>
            <div class="num">
              <label for="kit-hearts">{t('pvp.kits.rule.hearts')}</label>
              <input id="kit-hearts" type="number" min="0" max="100" bind:value={kit.rules.maxHearts} oninput={() => (dirty = true)} />
              <div class="hint">{t('pvp.kits.rule.heartsHint')}</div>
            </div>
          </div>

          <div class="tri-row">
            <span class="label">{t('pvp.kits.rule.regen')}</span>
            {@render tri('regen')}
          </div>
          <div class="tri-row">
            <span class="label">{t('pvp.kits.rule.hunger')}</span>
            {@render tri('hunger')}
          </div>
        </section>

        <!-- Effects and cooldowns -->
        <section class="card tile">
          <h2>{t('pvp.kits.rule.effects')}</h2>
          <div class="hint">{t('pvp.kits.rule.effectsHint')}</div>
          {#if kit.rules.effects.length}
            <ul class="lines">
              {#each kit.rules.effects as e, i}
                <li>
                  <span class="grow">{effectName(e.type)}</span>
                  <span class="lvl">
                    <span class="muted small">{t('items.level')}</span>
                    <input class="small-num" type="number" min="1" max="10" bind:value={e.level} oninput={() => (dirty = true)} aria-label={t('items.level')} />
                  </span>
                  <button class="remove" onclick={() => { kit.rules.effects = kit.rules.effects.filter((_, j) => j !== i); dirty = true; }} aria-label={t('items.remove')}>✕</button>
                </li>
              {/each}
            </ul>
          {/if}
          <select class="add" onchange={(e) => { const v = e.currentTarget.value; if (v) { kit.rules.effects = [...kit.rules.effects, { type: v, level: 1 }]; dirty = true; } e.currentTarget.value = ''; }}>
            <option value="">+ {t('items.addEffect')}</option>
            {#each (items.catalog?.effects ?? []).filter((x) => !kit.rules.effects.some((e) => e.type === x.id)).sort((a, b) => effectName(a.id).localeCompare(effectName(b.id))) as x}
              <option value={x.id}>{effectName(x.id)}</option>
            {/each}
          </select>

          <h2 class="second">{t('pvp.kits.rule.cooldowns')}</h2>
          <div class="hint">{t('pvp.kits.rule.cooldownsHint')}</div>
          {#if kit.rules.cooldowns.length}
            <ul class="lines">
              {#each kit.rules.cooldowns as c, i}
                <li>
                  <ItemIcon item={{ type: c.item, amount: 1 }} size={30} />
                  <span class="grow">{typeName(c.item)}</span>
                  <span class="lvl">
                    <input class="small-num" type="number" min="1" max="3600" bind:value={c.seconds} oninput={() => (dirty = true)} aria-label={t('pvp.kits.rule.seconds')} />
                    <span class="muted small">s</span>
                  </span>
                  <button class="remove" onclick={() => { kit.rules.cooldowns = kit.rules.cooldowns.filter((_, j) => j !== i); dirty = true; }} aria-label={t('items.remove')}>✕</button>
                </li>
              {/each}
            </ul>
          {/if}
          <select class="add" disabled={!kitTypes.length} onchange={(e) => { const v = e.currentTarget.value; if (v) { kit.rules.cooldowns = [...kit.rules.cooldowns, { item: v, seconds: 5 }]; dirty = true; } e.currentTarget.value = ''; }}>
            <option value="">+ {t('pvp.kits.rule.addCooldown')}</option>
            {#each kitTypes.filter((type) => !kit.rules.cooldowns.some((c) => c.item === type)) as type}
              <option value={type}>{typeName(type)}</option>
            {/each}
          </select>
        </section>
      </div>
    </div>
  {/if}
</div>

{#if kit && dirty}
  <div class="savebar" role="status">
    <span class="sb-dot" aria-hidden="true"></span>
    <span>{t('pvp.kits.unsaved')}</span>
    <button onclick={save} disabled={saving || !kit.id.trim()}>{saving ? t('pvp.kits.saving') : t('pvp.kits.save')}</button>
  </div>
{/if}

{#if contextMenu}
  {@const has = !!slots[contextMenu.slot]}
  <div class="ctx" role="menu" style="left: {contextMenu.x}px; top: {contextMenu.y}px">
    <div class="ctx-title">{slotLabel(contextMenu.slot)}</div>
    <button role="menuitem" onclick={() => command('edit', contextMenu.slot)}>✎ {t('pvp.kits.edit')}<kbd>{t('pvp.kits.key.click')}</kbd></button>
    <button role="menuitem" disabled={!has} onclick={() => command('copy', contextMenu.slot)}>⧉ {t('pvp.kits.copy')}<kbd>{t('pvp.kits.key.ctrl')}+C</kbd></button>
    <button role="menuitem" disabled={!has} onclick={() => command('cut', contextMenu.slot)}>✂ {t('pvp.kits.menu.cut')}<kbd>{t('pvp.kits.key.ctrl')}+X</kbd></button>
    <button role="menuitem" disabled={!clipboard} onclick={() => command('paste', contextMenu.slot)}>
      {#if clipboard}<ItemIcon item={clipboard} size={18} notip />{:else}📋{/if} {t('pvp.kits.menu.paste')}<kbd>{t('pvp.kits.key.ctrl')}+V</kbd></button>
    <button role="menuitem" class="bad" disabled={!has} onclick={() => command('delete', contextMenu.slot)}>✕ {t('pvp.kits.menu.delete')}<kbd>{t('pvp.kits.key.del')}</kbd></button>
  </div>
{/if}

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
  .page {
    --pv: var(--accent);
    display: grid;
    gap: 16px;
  }

  .back {
    justify-self: start;
    background: transparent;
    color: var(--pv);
    padding: 4px 0;
  }

  /* Head: icon (click = change), name in its colours, facts, buttons */
  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 20px;
    padding: 20px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pv) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pv) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .k-icon {
    position: relative;
    display: grid;
    place-items: center;
    width: 64px;
    height: 64px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 16px;
    background: color-mix(in srgb, var(--pv) 14%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pv) 35%, transparent);
  }


  .pen {
    position: absolute;
    right: -6px;
    bottom: -6px;
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    font-size: 0.72rem;
    color: var(--accent-text);
    background: var(--pv);
    box-shadow: var(--shadow);
  }

  .intro {
    flex: 1;
    min-width: 220px;
    display: grid;
    gap: 6px;
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
    overflow-wrap: anywhere;
  }

  .facts {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .chip.perm {
    font-weight: 600;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 12%, transparent);
  }

  .chip.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 14%, transparent);
  }

  .buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  /* Sections */
  .tile {
    display: grid;
    gap: 12px;
    align-content: start;
  }

  h2 {
    margin: 0;
    font-size: 1.02rem;
  }

  h2.second {
    margin-top: 8px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
  }

  .s-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .label {
    display: block;
    font-weight: 600;
    font-size: 0.9rem;
  }

  .hint-line {
    margin: 0;
    font-size: 0.85rem;
    color: var(--muted);
  }

  .hint-line.active {
    color: var(--pv);
    font-weight: 600;
  }

  .inv-grid {
    display: flex;
    justify-content: center;
    overflow-x: auto;
    padding: 12px;
    border-radius: 12px;
    background: color-mix(in srgb, #000 18%, var(--surface-2));
    border: 1px solid var(--border);
  }

  .columns {
    display: grid;
    gap: 16px;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }

  .col {
    display: grid;
    gap: 16px;
  }

  @media (max-width: 900px) {
    .columns {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  /* Segmented switches (edit/move, languages, regeneration/hunger) */
  .seg {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .seg button {
    display: inline-flex;
    align-items: center;
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

  .empty-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--warn);
  }

  /* Texts with the preview of the menu item */
  .texts {
    display: grid;
    gap: 10px;
    padding: 12px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  textarea,
  select {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 9px 10px;
  }

  .preview {
    display: grid;
    gap: 6px;
  }

  .preview .label {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .mc {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .mc-tip {
    min-width: 0;
    max-width: 320px;
    padding: 6px 9px;
    background: rgb(16 0 16 / 94%);
    border: 2px solid #2a0a5e;
    border-radius: 4px;
    color: #aaaaaa;
    font-size: 0.85rem;
    line-height: 1.35;
    overflow-wrap: anywhere;
  }

  /* On/off rules as rows with a switch */
  .toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    width: 100%;
    padding: 10px 12px;
    text-align: left;
    font: inherit;
    font-weight: 500;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 12px;
  }

  .toggle:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--pv) 40%, var(--border));
  }

  .t-text {
    flex: 1;
    min-width: 0;
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
    background: var(--pv);
  }

  .knob.on::after {
    transform: translateX(18px);
  }

  .nums {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  }

  .num {
    display: grid;
    gap: 6px;
    align-content: start;
    padding: 10px 12px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .num label {
    font-size: 0.85rem;
    font-weight: 600;
    margin: 0;
  }

  .num input {
    font-size: 1.15rem;
    font-weight: 700;
    background: var(--surface);
  }

  .tri-row {
    display: grid;
    gap: 6px;
  }

  /* Effects and cooldowns */
  .lines {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .lines li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px 6px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .grow {
    flex: 1;
    min-width: 0;
    font-weight: 600;
  }

  .lvl {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  input.small-num {
    width: 76px;
    padding: 6px 8px;
  }

  .remove {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 8px;
    color: var(--bad);
    background: transparent;
    border: 1px solid transparent;
  }

  .remove:hover {
    filter: none;
    background: color-mix(in srgb, var(--bad) 12%, transparent);
  }

  .add {
    color: var(--muted);
    background: var(--surface-2);
    border-style: dashed;
  }

  /* Arenas as chips */
  .arenas {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .arena {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px 6px 8px;
    border-radius: 999px;
    font: inherit;
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .arena:hover {
    filter: none;
    border-color: color-mix(in srgb, var(--pv) 45%, var(--border));
  }

  .tick {
    display: grid;
    place-items: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    font-size: 0.7rem;
    border: 1px solid var(--border);
    background: var(--surface);
  }

  .arena.on {
    border-color: color-mix(in srgb, var(--pv) 55%, var(--border));
    background: color-mix(in srgb, var(--pv) 12%, var(--surface-2));
  }

  .arena.on .tick {
    color: var(--accent-text);
    background: var(--pv);
    border-color: transparent;
  }

  .arena.off {
    color: var(--muted);
  }

  .arena.missing {
    color: var(--bad);
    border-color: color-mix(in srgb, var(--bad) 50%, var(--border));
    background: color-mix(in srgb, var(--bad) 10%, var(--surface-2));
  }

  .arena.missing .tick {
    background: var(--bad);
  }

  .a-tag {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 1px 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  /* The icon in the preview: click = change */
  .icon-pick {
    position: relative;
    display: grid;
    place-items: center;
    width: 52px;
    height: 52px;
    flex-shrink: 0;
    padding: 0;
    border-radius: 12px;
    background: color-mix(in srgb, var(--pv) 14%, var(--surface));
    border: 1px solid color-mix(in srgb, var(--pv) 35%, transparent);
  }

  .icon-pick:hover {
    filter: none;
    border-color: var(--pv);
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  /* The clipboard under the inventory */
  .clip {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 12px;
    background: color-mix(in srgb, var(--pv) 8%, var(--surface-2));
    border: 1px dashed color-mix(in srgb, var(--pv) 45%, var(--border));
  }

  .grow-text {
    display: grid;
    flex: 1;
    min-width: 0;
  }

  /* Right-click menu of a slot */
  .ctx {
    position: fixed;
    z-index: 1200;
    display: grid;
    min-width: 210px;
    padding: 6px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow), 0 14px 34px -12px rgb(0 0 0 / 0.4);
  }

  .ctx-title {
    padding: 4px 10px 6px;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--muted);
  }

  .ctx button {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border: 0;
    border-radius: 8px;
    text-align: left;
    font: inherit;
    font-size: 0.88rem;
    font-weight: 500;
    color: var(--text);
    background: transparent;
  }

  .ctx button:not(:disabled):hover {
    filter: none;
    background: var(--surface-2);
  }

  .ctx button.bad {
    color: var(--bad);
  }

  .ctx kbd {
    margin-left: auto;
    padding-left: 14px;
    font-family: inherit;
    font-size: 0.72rem;
    color: var(--muted);
  }

  /* Unsaved changes: a bar at the bottom */
  .savebar {
    position: fixed;
    left: 50%;
    bottom: 20px;
    z-index: 55;
    translate: -50% 0;
    display: flex;
    align-items: center;
    gap: 12px;
    max-width: min(92vw, 520px);
    padding: 8px 8px 8px 16px;
    border-radius: 999px;
    font-size: 0.9rem;
    font-weight: 600;
    background: var(--surface);
    border: 1px solid color-mix(in srgb, var(--warn) 45%, var(--border));
    box-shadow: var(--shadow), 0 10px 30px -12px rgb(0 0 0 / 0.35);
  }

  .savebar button {
    border-radius: 999px;
    padding: 6px 16px;
  }

  .sb-dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--warn);
  }

  /* Room for the bar below the last section */
  .page:has(~ .savebar) {
    padding-bottom: 60px;
  }

  @media (max-width: 759px) {
    .savebar {
      bottom: calc(78px + env(safe-area-inset-bottom));
    }
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .knob,
    .knob::after {
      transition: none;
    }
  }
</style>
