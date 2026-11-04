<script>
  // MRwaypoints for the team: all server waypoints (search, filter, rename, move to another category, delete - also
  // several at once) and the categories (the admin sets them up in a window with a preview). Waypoints themselves are
  // created in game or on the map of the player page. On top also MRadmin's switches for the map of the player page.
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { api } from '../lib/api.js';
  import { t, errorText, lang } from '../lib/i18n.js';
  import { head, dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Toast from '../components/Toast.svelte';
  import ItemIcon from '../components/ItemIcon.svelte';
  import { ICONS } from '../lib/icons.js';

  let { me } = $props();

  let overview = $state(null);
  let locks = $state(null);
  let error = $state('');
  let message = $state('');
  let busy = $state(false);
  let tab = $state(sessionStorage.getItem('mradmin.wp.tab') ?? 'waypoints');
  $effect(() => sessionStorage.setItem('mradmin.wp.tab', tab));

  // Filters of the list
  let category = $state('');
  let world = $state('');
  let creator = $state('');
  let sort = $state('new');
  let search = $state('');
  let limit = $state(60);

  /** Ids of the chosen waypoints (several at once), the target of "move", the waypoint renamed in place. */
  let chosen = $state(new Set());
  let target = $state('');
  let renaming = $state(null);
  let newName = $state('');
  /** The category being edited (a copy), or null. */
  let editing = $state(null);

  const manage = $derived(!!me.may.mrwaypointsManage);
  const admin = $derived(!!me.may.mrwaypointsCategories);

  async function load() {
    try {
      overview = await moduleData('mrwaypoints', 'overview');
      error = '';
    } catch (e) {
      error = errorText(e);
    }
    if (me.may.mapAdmin) {
      try {
        locks = await api('GET', '/map/locks');
      } catch {
        locks = null;
      }
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
      await load();
      return false;
    } finally {
      busy = false;
    }
  }

  async function setLock(which, off) {
    if (off && !confirm(t(`wp.lock.${which}.confirm`))) return;
    busy = true;
    try {
      locks = await api('POST', '/map/locks', { which, off: String(off) });
      message = t(off ? `wp.lock.${which}.offDone` : `wp.lock.${which}.onDone`);
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
  }

  const categories = $derived(overview?.categories ?? []);
  const waypoints = $derived(overview?.waypoints ?? []);
  const byId = $derived(Object.fromEntries(categories.map((c) => [c.id, c])));
  const catName = (c) => c?.names?.[lang] || c?.names?.en || Object.values(c?.names ?? {})[0] || c?.id || '?';
  const worlds = $derived([...new Set(waypoints.map((w) => w.world))].sort());
  const creators = $derived(
    [...new Map(waypoints.map((w) => [w.creator, w.creatorName])).entries()]
      .map(([uuid, name]) => ({ uuid, name }))
      .sort((a, b) => a.name.localeCompare(b.name)),
  );
  const onMap = $derived(categories.filter((c) => c.map).length);

  const filtered = $derived.by(() => {
    const q = search.trim().toLowerCase();
    const list = waypoints.filter((w) =>
      (!category || w.category === category) && (!world || w.world === world) && (!creator || w.creator === creator)
      && (!q || w.name.toLowerCase().includes(q) || w.creatorName.toLowerCase().includes(q)));
    if (sort === 'name') list.sort((a, b) => a.name.localeCompare(b.name));
    else if (sort === 'old') list.sort((a, b) => a.created - b.created);
    else list.sort((a, b) => b.created - a.created);
    return list;
  });
  const shown = $derived(filtered.slice(0, limit));
  const filtering = $derived(!!(category || world || creator || search.trim()));

  // Chosen ones that are still there (a reload may have removed some).
  $effect(() => {
    const ids = new Set(waypoints.map((w) => w.id));
    if ([...chosen].some((id) => !ids.has(id))) chosen = new Set([...chosen].filter((id) => ids.has(id)));
  });
  const allShownChosen = $derived(shown.length > 0 && shown.every((w) => chosen.has(w.id)));

  function choose(id) {
    const next = new Set(chosen);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    chosen = next;
  }

  function chooseShown() {
    const next = new Set(chosen);
    if (allShownChosen) shown.forEach((w) => next.delete(w.id));
    else shown.forEach((w) => next.add(w.id));
    chosen = next;
  }

  function resetFilters() {
    category = '';
    world = '';
    creator = '';
    search = '';
  }

  async function bulkMove() {
    if (!target) return;
    const ok = await act({ action: 'bulkRecategorize', ids: [...chosen].join(','), category: target },
      t('wp.bulk.moved', { n: chosen.size }));
    if (ok) chosen = new Set();
  }

  async function bulkDelete() {
    const ok = await act({ action: 'bulkDelete', ids: [...chosen].join(',') }, t('wp.bulk.deleted', { n: chosen.size }),
      t('wp.bulk.deleteConfirm', { n: chosen.size }));
    if (ok) chosen = new Set();
  }

  function startRename(w) {
    renaming = w.id;
    newName = w.name;
  }

  async function saveRename(w) {
    const name = newName.trim();
    renaming = null;
    if (name && name !== w.name) await act({ action: 'rename', id: w.id, name }, t('wp.done.renamed'));
  }

  // ------------------------------------------------------------ categories

  function newCategory() {
    editing = { isNew: true, id: '', nameDe: '', nameEn: '', color: '#4FC3F7', icon: 'paper', playersCreate: true,
      maxPerPlayer: 3, visible: true, map: true, worlds: [] };
  }

  function editCategory(c) {
    editing = {
      isNew: false, id: c.id, nameDe: c.names.de ?? '', nameEn: c.names.en ?? '', color: c.color, icon: c.icon,
      playersCreate: c.playersCreate, maxPerPlayer: c.maxPerPlayer, visible: c.visible, map: c.map,
      worlds: [...(c.worlds ?? [])],
    };
  }

  // Worlds to pick for a category: the loaded ones plus any saved one that is not loaded right now.
  const worldChoices = $derived([...new Set([...(overview?.worlds ?? []), ...(editing?.worlds ?? [])])]);

  function toggleWorld(w) {
    editing.worlds = editing.worlds.includes(w) ? editing.worlds.filter((x) => x !== w) : [...editing.worlds, w];
  }

  async function saveCategory() {
    const ok = await act({ action: 'categorySave', ...editing, maxPerPlayer: String(editing.maxPerPlayer),
      worlds: editing.worlds.join(',') }, t('wp.done.saved'));
    if (ok) editing = null;
  }

  function move(index, delta) {
    const ids = categories.map((c) => c.id);
    const to = index + delta;
    if (to < 0 || to >= ids.length) return;
    [ids[index], ids[to]] = [ids[to], ids[index]];
    act({ action: 'categoryOrder', ids: ids.join(',') }, t('wp.done.order'));
  }

  const previewName = $derived(editing ? (lang === 'de' ? editing.nameDe || editing.nameEn : editing.nameEn || editing.nameDe) || editing.id || '?' : '');
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && (editing = null)} />

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
    {#if overview}
      <div class="numbers">
        <span><b>{waypoints.length}</b> {t('wp.kpi.waypoints')}</span>
        <span><b>{categories.length}</b> {t('wp.kpi.categories')}</span>
      </div>
    {/if}
  </header>

  {#if error}<div class="error">{error}</div>{/if}
  <Toast text={message} />

  {#if overview}
    <div class="kpis">
      <div class="kpi"><div class="label">{t('wp.kpi.waypoints')}</div><div class="value">{waypoints.length}</div>
        <div class="sub">{t('wp.kpi.inWorlds', { n: worlds.length })}</div></div>
      <div class="kpi"><div class="label">{t('wp.kpi.categories')}</div><div class="value">{categories.length}</div>
        <div class="sub">{t('wp.kpi.onMapSub', { n: onMap })}</div></div>
      <div class="kpi"><div class="label">{t('wp.kpi.creators')}</div><div class="value">{creators.length}</div></div>
      {#if locks}
        <!-- MRadmin's switches for the map of the player page (mradmin.map.admin), like the controls of the loaders. -->
        {#each ['page', 'players'] as which}
          {@const l = locks[which]}
          {@const blocked = which === 'players' && locks.page.off}
          <div class="kpi {l.off ? 'bad' : 'good'}" title={t(`wp.lock.${which}.hint`)}>
            <div class="label k-head">{t(`wp.lock.${which}.title`)}
              <button class="sw" role="switch" aria-checked={!l.off} disabled={busy || blocked}
                aria-label={t(`wp.lock.${which}.title`)} onclick={() => setLock(which, !l.off)}>
                <span class="knob" class:on={!l.off}></span>
              </button>
            </div>
            <div class="value">{t(l.off ? 'wp.lock.off' : 'wp.lock.on')}</div>
            <div class="sub">{l.off ? t('wp.lock.since', { by: l.by || '?', since: dateTime(l.since) }) : t(`wp.lock.${which}.onSub`)}</div>
          </div>
        {/each}
      {/if}
    </div>

    <div class="tabs">
      <div class="switch" role="tablist">
        <button role="tab" class:active={tab === 'waypoints'} aria-selected={tab === 'waypoints'} onclick={() => (tab = 'waypoints')}>
          {t('wp.tab.waypoints')} <span class="n">{waypoints.length}</span>
        </button>
        <button role="tab" class:active={tab === 'categories'} aria-selected={tab === 'categories'} onclick={() => (tab = 'categories')}>
          {t('wp.tab.categories')} <span class="n">{categories.length}</span>
        </button>
      </div>
      {#if tab === 'categories' && admin}
        <button class="primary" disabled={busy} onclick={newCategory}>{t('wp.newCategory')}</button>
      {/if}
    </div>

    {#if tab === 'waypoints'}
      <section class="block">
        <div class="toolbar">
          <input class="search" type="search" bind:value={search} placeholder={t('wp.search')} />
          <select bind:value={world} aria-label={t('wp.filter.world')}>
            <option value="">{t('wp.filter.allWorlds')}</option>
            {#each worlds as w}<option value={w}>{w}</option>{/each}
          </select>
          <select bind:value={creator} aria-label={t('wp.filter.creator')}>
            <option value="">{t('wp.filter.allCreators')}</option>
            {#each creators as c (c.uuid)}<option value={c.uuid}>{c.name}</option>{/each}
          </select>
          <select bind:value={sort} aria-label={t('wp.filter.sort')}>
            <option value="new">{t('wp.sort.new')}</option>
            <option value="old">{t('wp.sort.old')}</option>
            <option value="name">{t('wp.sort.name')}</option>
          </select>
          {#if filtering}<button class="secondary small" onclick={resetFilters}>{t('wp.filter.reset')}</button>{/if}
        </div>
        <div class="filters">
          <button class="pill" class:active={!category} onclick={() => (category = '')}>{t('wp.all')} <b>{waypoints.length}</b></button>
          {#each categories as c (c.id)}
            <button class="pill" class:active={category === c.id} style="--cc: {c.color}" onclick={() => (category = c.id)}>
              <span class="cdot" aria-hidden="true"></span>{catName(c)} <b>{c.count}</b>
            </button>
          {/each}
        </div>

        <div class="listhead">
          {#if manage && shown.length}
            <label class="all">
              <input type="checkbox" checked={allShownChosen} onchange={chooseShown} />
              <span>{t('wp.bulk.chooseShown')}</span>
            </label>
          {/if}
          <span class="muted small">{t('wp.shown', { n: filtered.length, all: waypoints.length })}</span>
        </div>

        {#if shown.length}
          <ul class="list">
            {#each shown as w (w.id)}
              {@const c = byId[w.category]}
              <li class="wp" class:chosen={chosen.has(w.id)} style="--cc: {c?.color ?? 'var(--muted)'}">
                <div class="wtop">
                  {#if manage}
                    <input type="checkbox" class="pick" checked={chosen.has(w.id)} onchange={() => choose(w.id)}
                      aria-label={t('wp.bulk.choose', { name: w.name })} />
                  {/if}
                  <div class="wname">
                    {#if renaming === w.id}
                      <!-- svelte-ignore a11y_autofocus -->
                      <input class="rename" bind:value={newName} maxlength={overview.maxNameLength} autofocus
                        onkeydown={(e) => { if (e.key === 'Enter') saveRename(w); if (e.key === 'Escape') renaming = null; }}
                        onblur={() => saveRename(w)} />
                    {:else}
                      <strong title={w.name}>{w.name}</strong>
                    {/if}
                    <span class="cat"><span class="cdot" aria-hidden="true"></span>{catName(c)}</span>
                  </div>
                </div>
                <div class="where mono">{w.world} · {w.x} {w.y} {w.z}</div>
                <div class="wbottom">
                  <button class="who" disabled={!me.may.players} onclick={() => go('/players/' + w.creator)}>
                    <img src={head(w.creator)} alt="" width="20" height="20" />
                    <span>{w.creatorName}</span>
                  </button>
                  <span class="muted small">{dateTime(w.created)}</span>
                </div>
                {#if manage}
                  <div class="wactions">
                    <button class="secondary mini" disabled={busy} onclick={() => startRename(w)}>{t('wp.rename')}</button>
                    <select class="mini" disabled={busy} value={w.category} aria-label={t('wp.moveTo')}
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
          {#if filtered.length > shown.length}
            <button class="secondary more" onclick={() => (limit += 60)}>{t('wp.more', { n: filtered.length - shown.length })}</button>
          {/if}
        {:else}
          <p class="muted">{t('wp.empty')}</p>
        {/if}
      </section>
    {:else}
      <section class="block">
        {#if categories.length}
          <div class="cats">
            {#each categories as c, i (c.id)}
              <article class="catcard" style="--cc: {c.color}">
                <div class="chead">
                  <span class="cicon"><ItemIcon item={{ type: c.icon, amount: 1 }} size={36} notip /></span>
                  <div class="ctitle">
                    <h3>{catName(c)}</h3>
                    <code class="small muted">{c.id}</code>
                  </div>
                  <span class="count" title={t('wp.kpi.waypoints')}>{c.count}</span>
                </div>
                <div class="chips">
                  <span class="chip">{c.playersCreate ? t('wp.chip.players') : t('wp.chip.team')}</span>
                  {#if c.maxPerPlayer > 0}<span class="chip">{t('wp.chip.max', { n: c.maxPerPlayer })}</span>{/if}
                  {#if c.map}<span class="chip">{t('wp.chip.map')}</span>{/if}
                  {#if !c.visible}<span class="chip muted">{t('wp.chip.hidden')}</span>{/if}
                  <span class="chip">{c.worlds?.length ? t('wp.chip.worlds', { worlds: c.worlds.join(', ') }) : t('wp.f.worldsAll')}</span>
                </div>
                <div class="cfoot">
                  <button class="linkish" onclick={() => { category = c.id; tab = 'waypoints'; }}>{t('wp.showWaypoints')} →</button>
                  {#if admin}
                    <div class="cactions">
                      <button class="secondary mini" disabled={busy || i === 0} title={t('wp.up')} onclick={() => move(i, -1)}>▲</button>
                      <button class="secondary mini" disabled={busy || i === categories.length - 1} title={t('wp.down')} onclick={() => move(i, 1)}>▼</button>
                      <button class="secondary mini" disabled={busy} onclick={() => editCategory(c)}>{t('wp.edit')}</button>
                      <button class="danger mini" disabled={busy}
                        onclick={() => act({ action: 'categoryDelete', id: c.id }, t('wp.done.deleted'), t('wp.deleteCategoryConfirm', { name: catName(c), n: c.count }))}>{t('wp.delete')}</button>
                    </div>
                  {/if}
                </div>
              </article>
            {/each}
          </div>
        {:else}
          <p class="muted">{t('wp.noCategories')}</p>
        {/if}
      </section>
    {/if}
  {:else if !error}
    <p class="muted">{t('common.loading')}</p>
  {/if}
</div>

<!-- Several chosen: what to do with all of them, at the bottom of the screen. -->
{#if chosen.size && tab === 'waypoints'}
  <div class="bulkbar">
    <strong>{t('wp.bulk.count', { n: chosen.size })}</strong>
    <span class="sep" aria-hidden="true"></span>
    <select bind:value={target} aria-label={t('wp.moveTo')}>
      <option value="">{t('wp.bulk.pick')}</option>
      {#each categories as k (k.id)}<option value={k.id}>{catName(k)}</option>{/each}
    </select>
    <button class="secondary small" disabled={busy || !target} onclick={bulkMove}>{t('wp.bulk.move')}</button>
    <button class="danger small" disabled={busy} onclick={bulkDelete}>{t('wp.bulk.delete')}</button>
    <button class="linkish" onclick={() => (chosen = new Set())}>{t('wp.bulk.clear')}</button>
  </div>
{/if}

{#if editing}
  <div class="overlay" role="presentation" onclick={() => (editing = null)}>
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <div class="dialog" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()}>
      <div class="dhead">
        <h2>{editing.isNew ? t('wp.newCategory') : t('wp.editCategory', { name: previewName })}</h2>
        <button class="secondary mini" aria-label={t('common.close')} onclick={() => (editing = null)}>✕</button>
      </div>
      <div class="dbody">
        <div class="form">
          <div class="fields">
            <label>
              <span>{t('wp.f.id')}</span>
              <input bind:value={editing.id} disabled={!editing.isNew} maxlength="32" placeholder="shops" />
              {#if editing.isNew}<small class="muted">{t('wp.f.idHint')}</small>{/if}
            </label>
            <label>
              <span>{t('wp.f.color')}</span>
              <span class="row"><input type="color" bind:value={editing.color} /><input bind:value={editing.color} maxlength="7" /></span>
            </label>
            <label><span>{t('wp.f.nameDe')}</span><input bind:value={editing.nameDe} maxlength="48" /></label>
            <label><span>{t('wp.f.nameEn')}</span><input bind:value={editing.nameEn} maxlength="48" /></label>
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
        </div>

        <!-- How it looks: the item in /waypoints and the pin on the map. -->
        <aside class="preview" style="--cc: {editing.color}">
          <span class="plabel">{t('wp.preview')}</span>
          <div class="pitem">
            <ItemIcon item={{ type: editing.icon || 'paper', amount: 1 }} size={44} notip />
            <div class="ptext">
              <strong style="color: {editing.color}">{previewName}</strong>
              <span>{t('wp.preview.lore', { n: editing.isNew ? 0 : byId[editing.id]?.count ?? 0 })}</span>
              <span class="muted">{editing.playersCreate ? t('wp.chip.players') : t('wp.chip.team')}{editing.maxPerPlayer > 0 ? ' · ' + t('wp.chip.max', { n: editing.maxPerPlayer }) : ''}</span>
            </div>
          </div>
          <div class="ppin">
            <svg viewBox="0 0 24 24" width="30" height="30" aria-hidden="true">
              <path d="M12 22s7-6.4 7-12.2A7 7 0 0 0 5 9.8C5 15.6 12 22 12 22z" fill={editing.color} stroke="#fff" stroke-width="1.6" />
              <circle cx="12" cy="9.6" r="2.6" fill="#fff" />
            </svg>
            <span class="muted small">{editing.map ? t('wp.preview.map') : t('wp.preview.noMap')}</span>
          </div>
        </aside>
      </div>
      <div class="dfoot">
        <button class="secondary" disabled={busy} onclick={() => (editing = null)}>{t('common.cancel')}</button>
        <button class="primary" disabled={busy} onclick={saveCategory}>{t('wp.save')}</button>
      </div>
    </div>
  </div>
{/if}

<style>
  .page {
    --cl: var(--accent);
    display: grid;
    gap: 16px;
    padding-bottom: 70px;
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
    border: 1px solid color-mix(in srgb, var(--cl) 30%, var(--border));
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--cl) 20%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .watermark {
    position: absolute;
    right: -16px;
    bottom: -30px;
    width: 150px;
    height: 150px;
    color: var(--cl);
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
    color: var(--cl);
    background: color-mix(in srgb, var(--cl) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--cl) 35%, transparent);
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

  /* key figures and the map switches */
  .kpis {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 170px), 1fr));
  }

  .kpi {
    --tn: var(--cl);
    display: grid;
    gap: 4px;
    align-content: start;
    padding: 14px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    border-top: 3px solid var(--tn);
    background:
      radial-gradient(140% 120% at 100% 0%, color-mix(in srgb, var(--cl) 10%, transparent), transparent 55%),
      var(--surface);
    box-shadow: var(--shadow);
  }

  .kpi.good {
    --tn: var(--good);
  }

  .kpi.bad {
    --tn: var(--bad);
  }

  .kpi.bad .value {
    color: var(--tn);
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

  .sub {
    color: var(--muted);
    font-size: 0.8rem;
  }

  .k-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .sw {
    padding: 0;
    min-height: 0;
    background: none;
    border: 0;
    box-shadow: none;
  }

  .sw:hover {
    filter: none;
  }

  .knob {
    position: relative;
    display: block;
    width: 36px;
    height: 20px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 35%, transparent);
    transition: background 0.15s;
  }

  .knob::after {
    content: '';
    position: absolute;
    top: 3px;
    left: 3px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
    transition: transform 0.15s;
  }

  .knob.on {
    background: var(--good);
  }

  .knob.on::after {
    transform: translateX(16px);
  }

  /* tabs */
  .tabs {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .switch {
    display: inline-flex;
    flex-wrap: wrap;
    padding: 4px;
    gap: 4px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  .switch button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: transparent;
    color: var(--muted);
    padding: 6px 14px;
    border-radius: 9px;
    font-size: 0.9rem;
  }

  .switch button.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
  }

  .n {
    font-size: 0.72rem;
    font-weight: 700;
    padding: 0 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--muted) 18%, transparent);
  }

  .block {
    display: grid;
    gap: 12px;
    padding: 18px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: var(--shadow);
  }

  /* filters */
  .toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }

  .search {
    flex: 1 1 220px;
    max-width: 320px;
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 11px;
    border-radius: 999px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 0.82rem;
    font-weight: 600;
  }

  .pill b {
    color: var(--muted);
    font-weight: 700;
  }

  .pill.active {
    border-color: var(--cc, var(--accent));
    background: color-mix(in srgb, var(--cc, var(--accent)) 16%, transparent);
  }

  .cdot {
    width: 9px;
    height: 9px;
    flex-shrink: 0;
    border-radius: 3px;
    background: var(--cc);
  }

  .listhead {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    min-height: 26px;
  }

  .all {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 0.85rem;
    font-weight: 600;
  }

  /* the waypoints as cards in the colour of their category */
  .list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 270px), 1fr));
  }

  .wp {
    display: grid;
    gap: 8px;
    align-content: start;
    padding: 12px 14px;
    border-radius: 12px;
    border: 1px solid var(--border);
    border-left: 4px solid var(--cc);
    background: var(--surface-2);
  }

  .wp.chosen {
    border-color: var(--accent);
    border-left-color: var(--cc);
    background: color-mix(in srgb, var(--accent) 8%, var(--surface-2));
  }

  .wtop {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }

  .pick {
    margin-top: 3px;
  }

  .wname {
    display: grid;
    gap: 3px;
    min-width: 0;
    flex: 1;
  }

  .wname strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rename {
    padding: 3px 6px;
    font-weight: 700;
  }

  .cat {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
    color: var(--muted);
  }

  .where {
    font-size: 0.8rem;
    color: var(--muted);
  }

  .mono {
    font-variant-numeric: tabular-nums;
  }

  .wbottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
  }

  .who {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    justify-content: flex-start;
    padding: 2px 8px 2px 2px;
    border-radius: 999px;
    background: var(--surface);
    color: var(--text);
    font-size: 0.8rem;
    font-weight: 600;
  }

  .who img {
    border-radius: 50%;
    image-rendering: pixelated;
  }

  .wactions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .wactions select {
    flex: 1;
    min-width: 0;
  }

  .more {
    justify-self: center;
  }

  /* categories */
  .cats {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  .catcard {
    display: grid;
    gap: 10px;
    align-content: start;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--border);
    border-top: 4px solid var(--cc);
    background:
      radial-gradient(120% 120% at 100% 0%, color-mix(in srgb, var(--cc) 12%, transparent), transparent 60%),
      var(--surface-2);
  }

  .chead {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .cicon {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    flex-shrink: 0;
    border-radius: 10px;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .ctitle {
    display: grid;
    min-width: 0;
    flex: 1;
  }

  .ctitle h3 {
    margin: 0;
    font-size: 1rem;
    color: var(--cc);
  }

  .count {
    font-size: 1.3rem;
    font-weight: 800;
  }

  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .chip {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 0.74rem;
    font-weight: 600;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .cfoot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    padding-top: 8px;
    border-top: 1px solid var(--border);
  }

  .cactions {
    display: flex;
    gap: 4px;
  }

  .linkish {
    padding: 2px 0;
    background: transparent;
    color: var(--accent);
    font-size: 0.82rem;
    font-weight: 600;
    justify-content: flex-start;
  }

  .linkish:hover {
    filter: none;
    text-decoration: underline;
  }

  /* bar of the chosen waypoints */
  .bulkbar {
    position: fixed;
    left: 50%;
    bottom: 18px;
    z-index: 30;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 10px;
    max-width: calc(100vw - 24px);
    padding: 10px 14px;
    translate: -50% 0;
    border-radius: 14px;
    border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border));
    background: var(--surface);
    box-shadow: 0 10px 30px rgb(0 0 0 / 0.25);
  }

  :global(body:has(.mr-tabbar)) .bulkbar {
    bottom: 84px;
  }

  .sep {
    width: 1px;
    height: 20px;
    background: var(--border);
  }

  /* the category editor as a window */
  .overlay {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 0.45);
  }

  .dialog {
    display: grid;
    gap: 14px;
    width: min(860px, 100%);
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    padding: 20px;
    border-radius: var(--radius);
    background: var(--surface);
    box-shadow: 0 20px 60px rgb(0 0 0 / 0.35);
  }

  .dhead {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
  }

  .dhead h2 {
    margin: 0;
    font-size: 1.15rem;
  }

  .dbody {
    display: grid;
    gap: 18px;
    grid-template-columns: minmax(0, 1fr) 240px;
  }

  @media (max-width: 720px) {
    .dbody {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  .form {
    display: grid;
    gap: 14px;
  }

  .fields {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .fields label {
    display: grid;
    gap: 4px;
  }

  .fields label > span {
    font-size: 0.85rem;
    font-weight: 600;
  }

  .row {
    display: flex;
    gap: 6px;
  }

  .row input[type='color'] {
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

  .preview {
    display: grid;
    gap: 14px;
    align-content: start;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid var(--border);
    border-top: 4px solid var(--cc);
    background: var(--surface-2);
  }

  .plabel {
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  /* like an item with its tooltip in game */
  .pitem {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }

  .ptext {
    display: grid;
    gap: 2px;
    min-width: 0;
    padding: 6px 9px;
    border-radius: 6px;
    font-size: 0.8rem;
    color: #aaa;
    background: #170818;
    border: 2px solid #2a0c5e;
  }

  .ptext strong {
    font-size: 0.88rem;
    overflow-wrap: anywhere;
  }

  .ptext .muted {
    color: #666;
  }

  .ppin {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .dfoot {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>
