<script>
  // MRpvp: all kits as a list in the order the game shows them - one row per kit (place, icon and name, hotbar,
  // rules, buttons). The order changes with ↑ ↓ or by dragging the handle. Open one to edit it or copy it.
  // Needs mrpvp.admin.kits (like the kit editor in the game).
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import ItemIcon from '../components/ItemIcon.svelte';
  import KitTransfer from '../components/KitTransfer.svelte';

  let kits = $state(null);
  let arenas = $state([]);
  let transfer = $state(null); // 'export' | 'import'
  let error = $state('');
  let busy = $state(false);

  // Dragging a row by its handle: the row being dragged and the gap it would land in (0 = before the first).
  let grabbed = $state(null);
  let dragIndex = $state(null);
  let gap = $state(null);

  async function load() {
    try {
      const data = await moduleData('mrpvp', 'kits');
      kits = data.kits;
      arenas = data.arenas;
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  async function reorder(from, to) {
    if (from === to) return;
    const ids = kits.map((k) => k.id);
    const [id] = ids.splice(from, 1);
    ids.splice(to, 0, id);
    // Shown at once, MRpvp confirms with the next load.
    kits = ids.map((x) => kits.find((k) => k.id === x));
    busy = true;
    try {
      await moduleAction('mrpvp', { action: 'kitOrder', ids });
      await load();
    } catch (e) {
      error = errorText(e);
      await load();
    } finally {
      busy = false;
    }
  }

  function dragStart(e, i) {
    dragIndex = i;
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', kits[i].id);
  }

  function dragOver(e, i) {
    if (dragIndex == null) return;
    e.preventDefault();
    const r = e.currentTarget.getBoundingClientRect();
    gap = e.clientY < r.top + r.height / 2 ? i : i + 1;
  }

  function dragDrop(e) {
    e.preventDefault();
    const from = dragIndex;
    const to = gap;
    dragEnd();
    if (from == null || to == null) return;
    reorder(from, to > from ? to - 1 : to);
  }

  function dragEnd() {
    dragIndex = null;
    gap = null;
    grabbed = null;
  }

  function ruleBadges(r) {
    const badges = [];
    if (!r.damage) badges.push(t('pvp.kits.badge.noDamage'));
    if (r.hitsToWin > 0) badges.push(t('pvp.kits.badge.hits', { n: r.hitsToWin }));
    if (r.regen === false) badges.push(t('pvp.kits.badge.noRegen'));
    if (r.regen === true) badges.push(t('pvp.kits.badge.regen'));
    if (r.hunger === false) badges.push(t('pvp.kits.badge.noHunger'));
    if (r.hunger === true) badges.push(t('pvp.kits.badge.hunger'));
    if (r.build) badges.push(t('pvp.kits.badge.build'));
    if (r.arenas?.length) badges.push(t('pvp.kits.badge.arenas', { n: r.arenas.length }));
    if (r.minutes > 0) badges.push(t('pvp.kits.badge.minutes', { n: r.minutes }));
    if (r.maxHearts > 0) badges.push(t('pvp.kits.badge.hearts', { n: r.maxHearts }));
    if (r.effects?.length) badges.push(t('pvp.kits.badge.effects', { n: r.effects.length }));
    if (r.cooldowns?.length) badges.push(t('pvp.kits.badge.cooldowns', { n: r.cooldowns.length }));
    return badges;
  }

  load();
</script>

<div class="page">
  <button class="back" onclick={() => go('/pvp')}>← {t('pvp.back')}</button>

  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.swords} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.swords} /></svg>
      </span>
      <div>
        <h1>{t('pvp.kits.title')}{#if kits}<span class="count">{kits.length}</span>{/if}</h1>
        <p class="muted">{t('pvp.kits.subtitle')}</p>
      </div>
    </div>
    <div class="top-buttons">
      <button class="secondary" onclick={() => (transfer = 'import')}>{t('pvp.kits.import.button')}</button>
      <button class="secondary" onclick={() => (transfer = 'export')} disabled={!kits?.length}>{t('pvp.kits.export.button')}</button>
      <button onclick={() => go('/pvp/new-kit')}>+ {t('pvp.kits.new')}</button>
    </div>
  </header>

  {#if error}<div class="error">{error}</div>{/if}

  {#if !kits}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else if kits.length === 0}
    <section class="card empty">
      <span aria-hidden="true">🗡️</span>
      <p>{t('pvp.kits.none')}</p>
      <button onclick={() => go('/pvp/new-kit')}>+ {t('pvp.kits.new')}</button>
    </section>
  {:else}
    <ol class="rows" class:sorting={dragIndex != null}>
      {#each kits as kit, i (kit.id)}
        {@const badges = ruleBadges(kit.rules)}
        <li class="row" class:dragged={dragIndex === i} class:gap-before={gap === i && dragIndex !== i && dragIndex !== i - 1}
          class:gap-after={gap === kits.length && i === kits.length - 1 && dragIndex !== i}
          draggable={grabbed === i} ondragstart={(e) => dragStart(e, i)} ondragover={(e) => dragOver(e, i)}
          ondrop={dragDrop} ondragend={dragEnd}>
          <div class="place">
            <span class="grip" title={t('pvp.kits.drag')} aria-hidden="true"
              onpointerdown={() => (grabbed = i)} onpointerup={() => dragIndex == null && (grabbed = null)}>⋮⋮</span>
            <button class="arrow" disabled={busy || i === 0} onclick={() => reorder(i, i - 1)} aria-label={t('pvp.kits.up')}>▲</button>
            <span class="pos" title={t('pvp.kits.position')}>{i + 1}</span>
            <button class="arrow" disabled={busy || i === kits.length - 1} onclick={() => reorder(i, i + 1)} aria-label={t('pvp.kits.down')}>▼</button>
          </div>

          <button class="kit" onclick={() => go('/pvp/kits/' + kit.id)}>
            <span class="k-icon"><ItemIcon item={{ type: kit.icon, amount: 1 }} size={36} /></span>
            <span class="k-title">
              <strong>{kit.name}</strong>
              <span class="muted small mono">{kit.id}</span>
            </span>
          </button>

          <div class="hotbar">
            {#each Array.from({ length: 9 }, (_, s) => kit.hotbar.find((h) => h.slot === s)) as item}
              <ItemIcon {item} size={28} />
            {/each}
          </div>

          <div class="rules">
            <span class="badge items">{t('pvp.kits.itemCount', { n: kit.items })}</span>
            {#if kit.permission}<span class="badge perm mono" title={t('pvp.kits.permission')}>{kit.permission}</span>{/if}
            {#each badges as b}<span class="badge">{b}</span>{/each}
            {#if !badges.length}<span class="badge plain">{t('pvp.kits.noRules')}</span>{/if}
          </div>

          <div class="actions">
            <button class="secondary mini" onclick={() => go('/pvp/new-kit/' + kit.id)}>{t('pvp.kits.copy')}</button>
            <button class="mini" onclick={() => go('/pvp/kits/' + kit.id)}>{t('pvp.kits.edit')}</button>
          </div>
        </li>
      {/each}
    </ol>
  {/if}
</div>

{#if transfer && kits}
  <KitTransfer mode={transfer} {kits} {arenas} onclose={() => (transfer = null)} ondone={load} />
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

  .head {
    position: relative;
    overflow: hidden;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px 24px;
    padding: 22px 24px;
    border-radius: var(--radius);
    border: 1px solid color-mix(in srgb, var(--pv) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--pv) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--pv);
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
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pv) 35%, transparent);
  }

  h1 {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0;
    font-size: 1.45rem;
  }

  .count {
    font-size: 0.8rem;
    font-weight: 800;
    padding: 2px 9px;
    border-radius: 999px;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 14%, transparent);
  }

  .intro p {
    margin: 2px 0 0;
  }

  .top-buttons {
    position: relative;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  /* The list: one row per kit, columns place | kit | hotbar | rules | buttons. Narrower screens move the
     hotbar (and on phones the rules) into their own line. */
  .rows {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 10px;
  }

  .row {
    position: relative;
    display: grid;
    grid-template-columns: auto minmax(180px, 1fr) auto minmax(180px, 1.2fr) auto;
    grid-template-areas: 'place kit hotbar rules actions';
    align-items: center;
    gap: 10px 18px;
    padding: 12px 16px 12px 10px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
    transition: border-color 0.15s, opacity 0.15s;
  }

  .row:hover {
    border-color: color-mix(in srgb, var(--pv) 40%, var(--border));
  }

  .row.dragged {
    opacity: 0.4;
  }

  /* Where the dragged kit would land */
  .row.gap-before::before,
  .row.gap-after::after {
    content: '';
    position: absolute;
    left: 8px;
    right: 8px;
    height: 3px;
    border-radius: 2px;
    background: var(--pv);
  }

  .row.gap-before::before {
    top: -7px;
  }

  .row.gap-after::after {
    bottom: -7px;
  }

  .place {
    grid-area: place;
    display: grid;
    grid-template-columns: auto auto;
    grid-template-rows: auto auto auto;
    align-items: center;
    justify-items: center;
    column-gap: 4px;
  }

  .grip {
    grid-row: 1 / 4;
    padding: 6px 2px;
    font-size: 0.9rem;
    letter-spacing: -2px;
    color: var(--muted);
    cursor: grab;
    user-select: none;
  }

  .sorting .grip {
    cursor: grabbing;
  }

  .arrow,
  .pos {
    grid-column: 2;
  }

  .arrow {
    width: 28px;
    height: 20px;
    padding: 0;
    font-size: 0.6rem;
    border-radius: 6px;
    color: var(--muted);
    background: transparent;
    border: 1px solid transparent;
  }

  .arrow:not(:disabled):hover {
    filter: none;
    color: var(--pv);
    background: var(--surface-2);
    border-color: var(--border);
  }

  .arrow:disabled {
    opacity: 0.25;
  }

  .pos {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    border-radius: 8px;
    font-size: 0.88rem;
    font-weight: 800;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 12%, var(--surface-2));
  }

  .kit {
    grid-area: kit;
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    padding: 0;
    background: transparent;
    border: 0;
    color: var(--text);
    font: inherit;
    font-weight: 400;
    text-align: left;
  }

  .kit:hover {
    filter: none;
  }

  .kit:hover strong {
    color: var(--pv);
  }

  .k-icon {
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    border-radius: 12px;
    background: color-mix(in srgb, var(--pv) 12%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--pv) 30%, transparent);
  }

  .k-title {
    display: grid;
    min-width: 0;
  }

  .k-title strong {
    font-size: 1.04rem;
    overflow-wrap: anywhere;
  }

  /* The hotbar on a dark strip like in the game */
  .hotbar {
    grid-area: hotbar;
    display: flex;
    gap: 3px;
    padding: 5px;
    border-radius: 10px;
    background: color-mix(in srgb, #000 22%, var(--surface-2));
    border: 1px solid var(--border);
  }

  .rules {
    grid-area: rules;
    display: flex;
    gap: 5px;
    flex-wrap: wrap;
    align-items: center;
    min-width: 0;
  }

  .badge {
    font-size: 0.73rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 15%, transparent);
  }

  .badge.items {
    color: var(--muted);
    background: transparent;
    border: 1px solid var(--border);
  }

  .badge.perm {
    font-weight: 600;
    color: var(--pv);
    background: color-mix(in srgb, var(--pv) 12%, transparent);
  }

  .badge.plain {
    font-weight: 500;
    color: var(--muted);
  }

  .actions {
    grid-area: actions;
    display: flex;
    gap: 6px;
  }

  .mini {
    padding: 5px 12px;
    font-size: 0.85rem;
  }

  .empty {
    display: grid;
    justify-items: center;
    gap: 8px;
    padding: 30px 16px;
    color: var(--muted);
  }

  .empty span {
    font-size: 2.2rem;
  }

  .empty p {
    margin: 0;
  }

  @media (max-width: 1250px) {
    .row {
      grid-template-columns: auto minmax(0, 1fr) minmax(0, 1.3fr) auto;
      grid-template-areas:
        'place kit rules actions'
        'place hotbar hotbar hotbar';
    }

    .hotbar {
      justify-self: start;
    }
  }

  @media (max-width: 760px) {
    .row {
      grid-template-columns: auto minmax(0, 1fr);
      grid-template-areas:
        'place kit'
        'place hotbar'
        'place rules'
        'place actions';
      padding: 12px 12px 12px 6px;
    }

    .grip {
      display: none;
    }

    .hotbar {
      flex-wrap: wrap;
    }

    .actions {
      justify-content: flex-end;
    }
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .row {
      transition: none;
    }
  }
</style>
