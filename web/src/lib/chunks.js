// Status of a chunk loader as MRchunks reports it - always shown with symbol and word, never by colour alone.
export const STATUS = {
  locked: { icon: '🔒', tone: 'bad' },
  warn: { icon: '!', tone: 'warn' },
  paused: { icon: '⏸', tone: 'info' },
  ok: { icon: '✓', tone: 'good' },
  inactive: { icon: '○', tone: 'muted' },
};
