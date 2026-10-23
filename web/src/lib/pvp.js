// Helpers for the MRpvp pages: item and number formatting.

/** "diamond_sword" -> "Diamond Sword" (Minecraft's names are not available in the browser). */
export function itemName(item) {
  if (item.name) return item.name;
  return item.type
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

/** Short label for a slot tile: first letters of the words, e.g. "DS" for diamond_sword. */
export function itemShort(item) {
  const words = item.type.split('_').filter((w) => w !== 'minecraft');
  return words.length === 1 ? words[0].slice(0, 3).toUpperCase() : words.slice(0, 3).map((w) => w[0].toUpperCase()).join('');
}

/** A colour per kind of item, so a grid reads at a glance. */
export function itemTone(item) {
  const t = item.type;
  if (/_(sword|axe|mace|trident)$|^(mace|trident)$/.test(t)) return 'weapon';
  if (/_(helmet|chestplate|leggings|boots)$|^(shield|elytra)$/.test(t)) return 'armor';
  if (/^(bow|crossbow|arrow|spectral_arrow|tipped_arrow|wind_charge|ender_pearl|snowball)$/.test(t)) return 'ranged';
  if (/(apple|beef|pork|bread|carrot|potato|steak|stew|chicken|mutton|cod|salmon|cookie|melon|berries|golden)/.test(t)) return 'food';
  if (/potion|totem/.test(t)) return 'special';
  return 'block';
}

/** Tooltip: name, amount, enchantments, durability. */
export function itemTitle(item) {
  const lines = [itemName(item) + (item.amount > 1 ? ` ×${item.amount}` : '')];
  for (const [key, level] of item.enchants ?? []) lines.push(`${key.replace(/_/g, ' ')} ${level}`);
  if (item.max > 0) lines.push(`${item.max - item.damage}/${item.max}`);
  return lines.join('\n');
}

/** Minecraft health points (half hearts) as hearts with one decimal. */
export function hearts(points) {
  return points == null ? '-' : (Math.round((points / 2) * 10) / 10).toString();
}

/** "1:05" */
export function clock(ms) {
  const s = Math.floor((ms ?? 0) / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
