<script>
  // MRwaypoints: the categories (the admin sets them up here) and all server waypoints (the team renames, moves them
  // to another category or deletes them). Waypoints themselves are created in game, where the player stands.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import { head, dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Toast from '../components/Toast.svelte';
  import { ICONS } from '../lib/icons.js';

  let { me } = $props();

  let overview = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let filter = $state('');
  let search = $state('');
  /** The category being edited (a copy), or null. */
  let editing = $state(null);

  async function load() {
    try {
      overview = await moduleData('mrwaypoints', 'overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
  }

  $effect(() => {
    load();
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
  });

  async function act(body, done, confirmText) {
    if (confirmText && !confirm(confirmText)) return false;
    busy = true;
    error = '';
    message = '';
    try {
      await moduleAction('mrwaypoints', body);
      message = done;
      await load();
      return true;
    } catch (e) {
      error = errorText(e);
      return false;
    } finally {
      busy = false;
    }
  }

  const categories = $derived(overview?.categories ?? []);
  const byId = $derived(Object.fromEntries(categories.map((c) => [c.id, c])));
  const catName = (c) => c?.names?.[lang] || c?.names?.en || Object.values(c?.names ?? {})[0] || c?.id || '?';
  const creators = $derived(new Set((overview?.waypoints ?? []).map((w) => w.creator)).size);
  const onMap = $derived(categories.filter((c) => c.map).length);
  const shown = $derived(
    (overview?.waypoints ?? [])
      .filter((w) => !filter || w.category === filter)
      .filter((w) => {
        const q = search.trim().toLowerCase();
        return !q || w.name.toLowerCase().includes(q) || w.creatorName.toLowerCase().includes(q);
      })
      .sort((a, b) => b.created - a.created),
  );

  function newCategory() {
    editing = { isNew: true, id: '', nameDe: '', nameEn: '', color: '#4FC3F7', icon: 'paper', playersCreate: true, maxPerPlayer: 3, visible: true, map: true, worlds: [] };
  }

  function editCategory(c) {
    editing = {
      isNew: false, id: c.id, nameDe: c.names.de ?? '', nameEn: c.names.en ?? '', color: c.color, icon: c.icon,
      playersCreate: c.playersCreate, maxPerPlayer: c.maxPerPlayer, visible: c.visible, map: c.map, worlds: [...(c.worlds ?? [])],
    };
  }

  // Worlds to pick for a category: the loaded ones plus any saved one that is not loaded right now.
  const worldChoices = $derived([...new Set([...(overview?.worlds ?? []), ...(editing?.worlds ?? [])])]);

  function toggleWorld(w) {
    editing.worlds = editing.worlds.includes(w) ? editing.worlds.filter((x) => x !== w) : [...editing.worlds, w];
  }

  async function saveCategory() {
    const ok = await act({ action: 'categorySave', ...editing, maxPerPlayer: String(editing.maxPerPlayer), worlds: editing.worlds.join(',') }, t('wp.done.saved'));
    if (ok) editing = null;
  }

  function move(index, delta) {
    const ids = categories.map((c) => c.id);
    const target = index + delta;
    if (target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    act({ action: 'categoryOrder', ids: ids.join(',') }, t('wp.done.order'));
  }

  function rename(w) {
    const name = prompt(t('wp.renamePrompt'), w.name);
    if (name !== null && name.trim() && name !== w.name) act({ action: 'rename', id: w.id, name }, t('wp.done.renamed'));
  }
</script>

<div class="page">
  <header class="head">
    <svg class="watermark" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
      stroke-linejoin="round" aria-hidden="true"><path d={ICONS.pin} /></svg>
    <div class="intro">
      <span class="icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
          stroke-linejoin="round"><path d={ICONS.pin} /></svg>
      </span>
      <div>
        <h1>{t('wp.title')}</h1>
        <p class="muted">{t('wp.subtitle')}</p>
      </div>
    </div>
    {#if me.may.mrwaypointsCategories}
      <button class="primary" disabled={busy} onclick={newCategory}>{t('wp.newCategory')}</button>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if overview}
    <div class="kpis">
      <div class="kpi"><div class="label">{t('wp.kpi.waypoints')}</div><div class="value">{overview.waypoints.length}</div></div>
      <div class="kpi"><div class="label">{t('wp.kpi.categories')}</div><div class="value">{categories.length}</div></div>
      <div class="kpi"><div class="label">{t('wp.kpi.creators')}</div><div class="value">{creators}</div></div>
      <div class="kpi"><div class="label">{t('wp.kpi.onMap')}</div><div class="value">{onMap}</div></div>
    </div>

    {#if editing}
      <section class="editor">
        <h2>{editing.isNew ? t('wp.newCategory') : t('wp.editCategory', { name: editing.nameDe || editing.nameEn || editing.id })}</h2>
        <div class="fields">
          <label>
            <span>{t('wp.f.id')}</span>
            <input bind:value={editing.id} disabled={!editing.isNew} maxlength="32" placeholder="shops" />
            <small class="muted">{t('wp.f.idHint')}</small>
          </label>
          <label><span>{t('wp.f.nameDe')}</span><input bind:value={editing.nameDe} maxlength="48" /></label>
          <label><span>{t('wp.f.nameEn')}</span><input bind:value={editing.nameEn} maxlength="48" /></label>
          <label class="color">
            <span>{t('wp.f.color')}</span>
            <span class="row"><input type="color" bind:value={editing.color} /><input bind:value={editing.color} maxlength="7" /></span>
          </label>
          <label>
            <span>{t('wp.f.icon')}</span>
            <input bind:value={editing.icon} maxlength="64" placeholder="emerald" />
            <small class="muted">{t('wp.f.iconHint')}</small>
          </label>
          <label>
            <span>{t('wp.f.max')}</span>
            <input type="number" min="0" max="1000" bind:value={editing.maxPerPlayer} />
            <small class="muted">{t('wp.f.maxHint')}</small>
          </label>
        </div>
        <div class="worlds">
          <span class="wlabel">{t('wp.f.worlds')}</span>
          <div class="wchips">
            <button class="wchip" class:on={editing.worlds.length === 0} aria-pressed={editing.worlds.length === 0}
              onclick={() => (editing.worlds = [])}>{t('wp.f.worldsAll')}</button>
            {#each worldChoices as w (w)}
              <button class="wchip" class:on={editing.worlds.includes(w)} aria-pressed={editing.worlds.includes(w)}
                onclick={() => toggleWorld(w)}>{w}</button>
            {/each}
          </div>
          <small class="muted">{t('wp.f.worldsHint')}</small>
        </div>
        <div class="toggles">
          <label class="toggle"><input type="checkbox" bind:checked={editing.playersCreate} /><span>{t('wp.f.playersCreate')}</span></label>
          <label class="toggle"><input type="checkbox" bind:checked={editing.visible} /><span>{t('wp.f.visible')}</span></label>
          <label class="toggle"><input type="checkbox" bind:checked={editing.map} /><span>{t('wp.f.map')}</span></label>
        </div>
        <p class="muted small">{t('wp.f.permHint', { id: editing.id || '<id>' })}</p>
        <div class="actions">
          <button class="secondary" disabled={busy} onclick={() => (editing = null)}>{t('common.cancel')}</button>
          <button class="primary" disabled={busy} onclick={saveCategory}>{t('wp.save')}</button>
        </div>
      </section>
    {/if}

    <section class="block">
      <h2 class="section">{t('wp.categories')}</h2>
      {#if categories.length}
        <div class="cats">
          {#each categories as c, i (c.id)}
            <article class="cat" style="--cc: {c.color}">
              <div class="chead">
                <span class="dot" aria-hidden="true"></span>
                <h3>{catName(c)}</h3>
                <span class="count">{c.count}</span>
              </div>
              <div class="chips">
                <span class="chip">{c.playersCreate ? t('wp.chip.players') : t('wp.chip.team')}</span>
                {#if c.maxPerPlayer > 0}<span class="chip">{t('wp.chip.max', { n: c.maxPerPlayer })}</span>{/if}
                {#if c.map}<span class="chip">{t('wp.chip.map')}</span>{/if}
                {#if c.worlds?.length}<span class="chip">{t('wp.chip.worlds', { worlds: c.worlds.join(', ') })}</span>{/if}
                {#if !c.visible}<span class="chip muted">{t('wp.chip.hidden')}</span>{/if}
              </div>
              <code class="small muted">mrwaypoints.create.{c.id}</code>
              {#if me.may.mrwaypointsCategories}
                <div class="cactions">
                  <button class="secondary mini" disabled={busy || i === 0} title={t('wp.up')} onclick={() => move(i, -1)}>▲</button>
                  <button class="secondary mini" disabled={busy || i === categories.length - 1} title={t('wp.down')} onclick={() => move(i, 1)}>▼</button>
                  <button class="secondary mini" disabled={busy} onclick={() => editCategory(c)}>{t('wp.edit')}</button>
                  <button class="danger mini" disabled={busy}
                    onclick={() => act({ action: 'categoryDelete', id: c.id }, t('wp.done.deleted'), t('wp.deleteCategoryConfirm', { name: catName(c), n: c.count }))}>{t('wp.delete')}</button>
                </div>
              {/if}
            </article>
          {/each}
        </div>
      {:else}
        <p class="muted">{t('wp.noCategories')}</p>
      {/if}
    </section>

    <section class="block">
      <div class="listhead">
        <h2 class="section">{t('wp.waypoints')}</h2>
        <input class="search" type="search" bind:value={search} placeholder={t('wp.search')} />
      </div>
      <div class="filters">
        <button class="pill" class:active={!filter} onclick={() => (filter = '')}>{t('wp.all')} <b>{overview.waypoints.length}</b></button>
        {#each categories as c (c.id)}
          <button class="pill" class:active={filter === c.id} style="--cc: {c.color}" onclick={() => (filter = c.id)}>
            <span class="dot" aria-hidden="true"></span>{catName(c)} <b>{c.count}</b>
          </button>
        {/each}
      </div>

      {#if shown.length}
        <ul class="list">
          {#each shown as w (w.id)}
            {@const c = byId[w.category]}
            <li style="--cc: {c?.color ?? 'var(--muted)'}">
              <span class="dot" aria-hidden="true"></span>
              <div class="main">
                <strong>{w.name}</strong>
                <span class="muted small">{catName(c)} · {w.world} {w.x} {w.y} {w.z}</span>
              </div>
              <button class="who" disabled={!me.may.players} onclick={() => go('/players/' + w.creator)}>
                <img src={head(w.creator)} alt="" width="22" height="22" />
                <span>{w.creatorName}</span>
              </button>
              <span class="muted small when">{dateTime(w.created)}</span>
              {#if me.may.mrwaypointsManage}
                <div class="wactions">
                  <button class="secondary mini" disabled={busy} onclick={() => rename(w)}>{t('wp.rename')}</button>
                  <select class="mini" disabled={busy} value={w.category}
                    onchange={(e) => act({ action: 'recategorize', id: w.id, category: e.currentTarget.value }, t('wp.done.moved'))}>
                    {#each categories as k (k.id)}<option value={k.id}>{catName(k)}</option>{/each}
                  </select>
                  <button class="danger mini" disabled={busy}
                    onclick={() => act({ action: 'delete', id: w.id }, t('wp.done.deletedWaypoint'), t('wp.deleteConfirm', { name: w.name }))}>{t('wp.delete')}</button>
                </div>
              {/if}
            </li>
          {/each}
        </ul>
      {:else}
        <p class="muted">{t('wp.empty')}</p>
      {/if}
    </section>
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<style>
  .page {
    --sl: var(--accent);
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
    border: 1px solid color-mix(in srgb, var(--sl) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--sl) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--sl);
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
    color: var(--sl);
    background: color-mix(in srgb, var(--sl) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--sl) 35%, transparent);
  }

  h1 {
    margin: 0;
    font-size: 1.45rem;
  }

  .intro p {
    margin: 2px 0 0;
  }

  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .kpi {
    display: grid;
    gap: 4px;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--sl);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--sl) 10%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .label {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 700;
  }

  .value {
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1.15;
  }

  .block,
  .editor {
    display: grid;
    gap: 12px;
    padding: 16px 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  .editor {
    border-color: color-mix(in srgb, var(--sl) 40%, var(--border));
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .fields {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .fields label {
    display: grid;
    gap: 4px;
    align-content: start;
  }

  .fields label > span {
    font-size: 0.85rem;
    font-weight: 700;
  }

  .color .row {
    display: flex;
    gap: 8px;
  }

  .color input[type='color'] {
    width: 44px;
    padding: 2px;
  }

  .worlds {
    display: grid;
    gap: 6px;
  }

  .wlabel {
    font-size: 0.85rem;
    font-weight: 600;
  }

  .wchips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .wchip {
    padding: 4px 10px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 0.8rem;
    font-weight: 600;
  }

  .wchip.on {
    border-color: var(--accent);
    background: color-mix(in srgb, var(--accent) 18%, transparent);
    color: var(--accent);
  }

  .toggles {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
  }

  .toggle {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  /* Categories: their own colour on the left edge, like the pins on the map */
  .cats {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  }

  .cat {
    display: grid;
    gap: 8px;
    padding: 12px 14px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-left: 4px solid var(--cc);
    background: var(--surface-2);
  }

  .chead {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  h3 {
    margin: 0;
    font-size: 1rem;
    flex: 1;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .count {
    font-weight: 800;
    font-size: 1.1rem;
  }

  .dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
    background: var(--cc);
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 9px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--sl) 12%, transparent);
  }

  .cactions,
  .wactions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    justify-content: flex-start;
  }

  .mini {
    padding: 3px 10px;
    font-size: 0.8rem;
  }

  .listhead {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px 16px;
  }

  .search {
    min-width: 200px;
    flex: 0 1 280px;
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .pill {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 4px 12px;
    border-radius: 999px;
    font-size: 0.85rem;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
  }

  .pill.active {
    border-color: var(--sl);
    background: color-mix(in srgb, var(--sl) 16%, var(--surface-2));
  }

  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 6px;
  }

  .list li {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 14px;
    padding: 8px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border-left: 3px solid var(--cc);
  }

  .main {
    display: grid;
    flex: 1;
    min-width: 180px;
  }

  .who {
    display: inline-flex;
    align-items: center;
    justify-content: flex-start;
    gap: 8px;
    padding: 0;
    background: transparent;
    color: var(--text);
    font: inherit;
  }

  .who:disabled {
    opacity: 1;
    cursor: default;
  }

  .who img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .when {
    min-width: 110px;
  }

  @media (max-width: 640px) {
    .head {
      padding: 18px 16px;
    }
  }
</style>
