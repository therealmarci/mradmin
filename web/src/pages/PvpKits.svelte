<script>
  // MRpvp: all kits as cards (icon, name, rules, hotbar) in the order the game shows them. Open one to edit it, copy
  // it, or move it up and down. Needs mrpvp.admin.kits (like the kit editor in the game).
  import { moduleAction, moduleData } from '../lib/modules.js';
  import { t, errorText } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import ItemIcon from '../components/ItemIcon.svelte';
  import KitTransfer from '../components/KitTransfer.svelte';

  let kits = $state(null);
  let arenas = $state([]);
  let transfer = $state(null); // 'export' | 'import'
  let error = $state('');
  let busy = $state(false);

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

  async function move(index, step) {
    const ids = kits.map((k) => k.id);
    const [id] = ids.splice(index, 1);
    ids.splice(index + step, 0, id);
    busy = true;
    try {
      await moduleAction('mrpvp', { action: 'kitOrder', ids });
      await load();
    } catch (e) {
      error = errorText(e);
    } finally {
      busy = false;
    }
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

<div class="stack">
  <button class="back" onclick={() => go('/pvp')}>← {t('pvp.back')}</button>
  <div class="head">
    <div>
      <h1>{t('pvp.kits.title')}</h1>
      <p class="muted small">{t('pvp.kits.subtitle')}</p>
    </div>
    <div class="top-buttons">
      <button class="secondary" onclick={() => (transfer = 'import')}>{t('pvp.kits.import.button')}</button>
      <button class="secondary" onclick={() => (transfer = 'export')} disabled={!kits?.length}>{t('pvp.kits.export.button')}</button>
      <button onclick={() => go('/pvp/new-kit')}>{t('pvp.kits.new')}</button>
    </div>
  </div>

  {#if error}<div class="error">{error}</div>{/if}

  {#if !kits}
    {#if !error}<p class="muted">{t('common.loading')}</p>{/if}
  {:else if kits.length === 0}
    <p class="muted">{t('pvp.kits.none')}</p>
  {:else}
    <div class="list">
      {#each kits as kit, i (kit.id)}
        <div class="card kit">
          <div class="top">
            <ItemIcon item={{ type: kit.icon, amount: 1 }} size={44} />
            <div class="title">
              <strong>{kit.name}</strong>
              <div class="muted small mono">{kit.id}{kit.permission ? ' · ' + kit.permission : ''}</div>
            </div>
            <div class="order">
              <button class="secondary tiny" disabled={busy || i === 0} onclick={() => move(i, -1)} aria-label={t('pvp.kits.up')}>↑</button>
              <button class="secondary tiny" disabled={busy || i === kits.length - 1} onclick={() => move(i, 1)} aria-label={t('pvp.kits.down')}>↓</button>
            </div>
          </div>
          <div class="hotbar">
            {#each Array.from({ length: 9 }, (_, s) => kit.hotbar.find((h) => h.slot === s)) as item}
              <ItemIcon {item} size={30} />
            {/each}
          </div>
          <div class="badges">
            {#each ruleBadges(kit.rules) as b}<span class="badge">{b}</span>{/each}
            <span class="muted small">{t('pvp.kits.itemCount', { n: kit.items })}</span>
          </div>
          <div class="actions">
            <button onclick={() => go('/pvp/kits/' + kit.id)}>{t('pvp.kits.edit')}</button>
            <button class="secondary" onclick={() => go('/pvp/new-kit/' + kit.id)}>{t('pvp.kits.copy')}</button>
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

{#if transfer && kits}
  <KitTransfer mode={transfer} {kits} {arenas} onclose={() => (transfer = null)} ondone={load} />
{/if}

<style>
  .back {
    justify-self: start;
    background: none;
    border: 0;
    padding: 0;
    color: var(--text);
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    flex-wrap: wrap;
  }

  .top-buttons {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .list {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr));
  }

  .kit {
    display: grid;
    gap: 10px;
    padding: 12px 14px;
  }

  .top {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .title {
    flex: 1;
    min-width: 0;
  }

  .order {
    display: flex;
    gap: 4px;
  }

  .tiny {
    padding: 4px 9px;
  }

  .hotbar {
    display: flex;
    gap: 3px;
    flex-wrap: wrap;
  }

  .badges {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    align-items: center;
  }

  .actions {
    display: flex;
    gap: 8px;
  }
</style>
