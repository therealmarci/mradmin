// Changelogs: entries of a type (new, changed, fixed, removed) and headings in three levels (h1 main heading, h2 sub
// heading, h3 a further level). The entries after a heading belong to it - shown indented below it, by type.

export const ENTRY_TYPES = ['new', 'changed', 'fixed', 'removed'];
export const HEADINGS = ['h1', 'h2', 'h3'];

export const isHeading = (type) => HEADINGS.includes(type);

/** Level of a heading (1-3), 0 for an entry. */
export const levelOf = (type) => HEADINGS.indexOf(type) + 1;

/**
 * The entries cut into sections at every heading: [{ level, title, groups: [{ type, items }] }]. The part before the
 * first heading has level 0 and no title. textOf(entry) gives an entry's text.
 */
export function sections(entries, textOf = (e) => e.text) {
  const result = [];
  let current = { level: 0, title: null, items: [] };
  for (const e of entries ?? []) {
    if (isHeading(e.type)) {
      result.push(current);
      current = { level: levelOf(e.type), title: textOf(e), items: [] };
    } else {
      current.items.push(e);
    }
  }
  result.push(current);
  return result
    .filter((s) => s.title != null || s.items.length)
    .map((s) => ({
      level: s.level,
      title: s.title,
      groups: ENTRY_TYPES.map((type) => ({ type, items: s.items.filter((e) => e.type === type) })).filter((g) => g.items.length),
    }));
}

/** How many real entries (no headings) a changelog has. */
export const entryCount = (entries) => (entries ?? []).filter((e) => !isHeading(e.type)).length;
