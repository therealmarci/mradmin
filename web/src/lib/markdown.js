// A small, safe Markdown renderer for the imprint and privacy texts: everything is HTML-escaped first, then only
// headings, paragraphs, lists, quotes, **bold**, *italic*, `code` and links (http, https, mailto) become HTML.

const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function inline(text) {
  return escape(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\(((?:https?:\/\/|mailto:)[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
}

export function markdown(source) {
  const out = [];
  let paragraph = [];
  let list = null;
  let quote = [];
  const flush = () => {
    if (paragraph.length) out.push('<p>' + paragraph.map(inline).join('<br>') + '</p>');
    if (list) out.push('<ul>' + list.map((i) => '<li>' + inline(i) + '</li>').join('') + '</ul>');
    if (quote.length) out.push('<blockquote>' + quote.map(inline).join('<br>') + '</blockquote>');
    paragraph = [];
    list = null;
    quote = [];
  };
  for (const raw of source.replace(/\r/g, '').split('\n')) {
    const line = raw.trimEnd();
    const heading = line.match(/^(#{1,3})\s+(.*)$/);
    if (!line.trim()) {
      flush();
    } else if (heading) {
      flush();
      out.push(`<h${heading[1].length}>${inline(heading[2])}</h${heading[1].length}>`);
    } else if (/^\s*[-*]\s+/.test(line)) {
      if (paragraph.length || quote.length) flush();
      (list ??= []).push(line.replace(/^\s*[-*]\s+/, ''));
    } else if (line.startsWith('>')) {
      if (paragraph.length || list) flush();
      quote.push(line.replace(/^>\s?/, ''));
    } else {
      if (list || quote.length) flush();
      paragraph.push(line);
    }
  }
  flush();
  return out.join('\n');
}
