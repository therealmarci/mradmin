// A small MiniMessage reader for the live preview in the item editor: colours (<red>, <#ff8800>, <color:...>),
// <b> <i> <u> <st> <obf>, closing tags and <reset>. Gradients and the like show in their first colour - the server
// builds the real item when it is saved.
const NAMED = {
  black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000',
  dark_purple: '#aa00aa', gold: '#ffaa00', gray: '#aaaaaa', grey: '#aaaaaa', dark_gray: '#555555',
  dark_grey: '#555555', blue: '#5555ff', green: '#55ff55', aqua: '#55ffff', red: '#ff5555',
  light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff',
};
const DECORATIONS = {
  b: 'b', bold: 'b', i: 'i', em: 'i', italic: 'i', u: 'u', underlined: 'u', st: 's', strikethrough: 's',
  obf: 'o', obfuscated: 'o',
};

function colorOf(name) {
  if (NAMED[name]) return NAMED[name];
  if (/^#[0-9a-f]{6}$/i.test(name)) return name.toLowerCase();
  return null;
}

/**
 * MiniMessage text -> [{text, color, b, i, u, s, o}]. {@code base} is the look of the line before any tag
 * (e.g. {color: '#aa00aa', i: true} for lore).
 */
export function miniSpans(text, base = {}) {
  const spans = [];
  const stack = [{ ...base }];
  const re = /<(\/?)([!a-z0-9_#-]+)(?::[^>]*)?>/gi;
  let last = 0;
  let m;
  const push = (t) => {
    if (t) spans.push({ ...stack[stack.length - 1], text: t });
  };
  while ((m = re.exec(text)) !== null) {
    push(text.slice(last, m.index));
    last = re.lastIndex;
    const closing = m[1] === '/';
    let tag = m[2].toLowerCase();
    const full = m[0].slice(1, -1).replace(/^\//, '');
    if (tag === 'reset') {
      stack.length = 1;
      stack[0] = { ...base, i: false, color: undefined };
      continue;
    }
    if (closing) {
      if (stack.length > 1) stack.pop();
      continue;
    }
    const top = { ...stack[stack.length - 1] };
    const negate = tag.startsWith('!');
    if (negate) tag = tag.slice(1);
    if (DECORATIONS[tag]) {
      top[DECORATIONS[tag]] = !negate;
    } else if (tag === 'color' || tag === 'colour' || tag === 'c') {
      top.color = colorOf(full.split(':')[1]?.toLowerCase() ?? '') ?? top.color;
    } else if (tag === 'gradient' || tag === 'transition') {
      top.color = colorOf(full.split(':')[1]?.toLowerCase() ?? '') ?? top.color;
    } else if (colorOf(tag)) {
      top.color = colorOf(tag);
    } else {
      // unknown tag: keep it as text, like MiniMessage does
      push(m[0]);
      continue;
    }
    stack.push(top);
  }
  push(text.slice(last));
  return spans;
}
