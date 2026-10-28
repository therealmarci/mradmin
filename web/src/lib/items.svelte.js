// Items as the game shows them: pictures from MRadmin's atlas (drawn from the game's own files) and names in the
// language of the page, from the game's own translations. Loaded once per page visit, shared by every component.
// Without pictures (MRadmin could not fetch them) items show short labels instead - see ItemIcon.
import { api } from './api.js';
import { lang } from './i18n.js';

export const items = $state({
  ready: false,
  catalog: null,
  names: {},
  icons: { state: 'off' },
});

let loading = null;

/** Loads catalog and names once; components call it when they first show an item. */
export function loadItems() {
  if (!loading) {
    loading = (async () => {
      try {
        const catalog = await api('GET', '/items/catalog');
        items.catalog = catalog;
        items.icons = catalog.icons ?? { state: 'off' };
        try {
          const res = await fetch('/api/items/names/' + lang, { credentials: 'same-origin' });
          if (res.ok) items.names = await res.json();
        } catch {
          // names stay technical
        }
      } catch {
        items.catalog = null;
      }
      items.ready = true;
      // Still downloading on the server: ask again in a while.
      if (items.icons.state === 'loading') {
        setTimeout(() => {
          loading = null;
          loadItems();
        }, 15000);
      }
    })();
  }
  return loading;
}

/** Position of an item in the atlas as CSS for an element {@code px} wide, or null without a picture. */
export function iconStyle(type, px) {
  const icons = items.icons;
  const index = icons?.items?.[type];
  if (index == null) return null;
  const scale = px / icons.size;
  const x = (index % icons.columns) * px;
  const y = Math.floor(index / icons.columns) * px;
  return `background-image: url(/api/items/atlas.png?v=${icons.version}); background-size: ${icons.columns * px}px auto; background-position: -${x}px -${y}px;` + (scale >= 1 ? ' image-rendering: pixelated;' : '');
}

/** A text of the game ("item.minecraft.stone" ...), or null. */
export function gameText(key) {
  return key ? (items.names[key] ?? null) : null;
}

/** "diamond_sword" -> "Diamond Sword" - when the game's names are missing. */
export function pretty(id) {
  return (id ?? '').replace(/^minecraft:/, '').split(/[_:]/).map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w)).join(' ');
}

/** Name of an item type ("diamond_sword"). */
export function typeName(type) {
  const entry = items.catalog?.items?.find((i) => i.id === type);
  return gameText(entry?.key) ?? gameText('item.minecraft.' + type) ?? gameText('block.minecraft.' + type) ?? pretty(type);
}

/** The normal name of an item (without its own name): by its translation key, else by type. */
export function baseName(item) {
  return gameText(item.nameKey) ?? typeName(item.type);
}

/** Level as the game writes it: I, II ... X, above that the number. */
export function roman(level) {
  return gameText('enchantment.level.' + level) ?? String(level);
}

export function enchantName(id) {
  return gameText('enchantment.minecraft.' + id) ?? pretty(id);
}

export function effectName(id) {
  return gameText('effect.minecraft.' + id) ?? pretty(id);
}

export function potionName(id) {
  const entry = items.catalog?.potions?.find((p) => p.id === id);
  return gameText(entry?.key) ?? pretty(id);
}

/** "1:30", "∞" */
export function duration(seconds) {
  if (seconds < 0) return gameText('effect.duration.infinite') ?? '∞';
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * The tooltip like in the game: lines of spans [{text, color, b, i, u, s, o}]. Name (own name in its look, else the
 * game's name - aqua when enchanted), enchantments (grey, curses red), potion effects, lore, durability, unbreakable.
 */
export function tooltip(item) {
  const lines = [];
  const enchanted = (item.enchants?.length ?? 0) > 0 || item.glint === true;
  if (item.title?.length) {
    lines.push(item.title);
  } else if (item.name && !item.data) {
    // Older modules send the own name as plain text.
    lines.push([{ text: item.name, color: enchanted ? '#55ffff' : '#ffffff', i: true }]);
  } else {
    lines.push([{ text: baseName(item), color: enchanted ? '#55ffff' : '#ffffff' }]);
  }
  const curse = (id) => items.catalog?.enchantments?.find((e) => e.id === id)?.curse;
  for (const [id, level] of [...(item.enchants ?? []), ...(item.stored ?? [])]) {
    const max = items.catalog?.enchantments?.find((e) => e.id === id)?.max ?? 0;
    const text = enchantName(id) + (max === 1 && level === 1 ? '' : ' ' + roman(level));
    lines.push([{ text, color: curse(id) ? '#ff5555' : '#aaaaaa' }]);
  }
  if (item.potion) {
    for (const e of [...(item.potion.baseEffects ?? []), ...(item.potion.effects ?? [])]) {
      const harmful = items.catalog?.effects?.find((x) => x.id === e.type)?.category === 'harmful';
      let text = effectName(e.type) + (e.level > 1 ? ' ' + roman(e.level) : '');
      const instant = items.catalog?.effects?.find((x) => x.id === e.type)?.instant;
      if (!instant) text += ` (${duration(e.seconds)})`;
      lines.push([{ text, color: harmful ? '#ff5555' : '#5555ff' }]);
    }
  }
  if (item.trim) {
    const pattern = items.catalog?.trimPatterns?.find((p) => p.id === item.trim.pattern);
    const material = items.catalog?.trimMaterials?.find((m) => m.id === item.trim.material);
    lines.push([{ text: (gameText(pattern?.key) ?? pretty(item.trim.pattern)) + ' · ' + (gameText(material?.key) ?? pretty(item.trim.material)), color: '#aaaaaa' }]);
  }
  if (item.color) {
    lines.push([{ text: gameText('item.dyed') ?? 'Dyed', color: '#aaaaaa', i: true }]);
  }
  for (const line of item.loreView ?? []) lines.push(line.length ? line : [{ text: ' ' }]);
  if (item.unbreakable) {
    lines.push([{ text: gameText('item.unbreakable') ?? 'Unbreakable', color: '#5555ff' }]);
  } else if (item.max > 0 && item.damage > 0) {
    const text = (gameText('item.durability') ?? 'Durability: %s / %s').replace('%s', item.max - item.damage).replace('%s', item.max);
    lines.push([{ text, color: '#ffffff' }]);
  }
  return lines;
}

/** Plain text of the tooltip (title attribute, screen readers). */
export function tooltipText(item) {
  return tooltip(item).map((line) => line.map((s) => s.text).join('')).join('\n');
}
