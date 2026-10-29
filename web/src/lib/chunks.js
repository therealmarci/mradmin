// Status of a chunk loader as MRchunks reports it - always with the word (some with a symbol in front), never by
// colour alone.
export const STATUS = {
  locked: { icon: '', tone: 'bad' },
  warn: { icon: '', tone: 'warn' },
  paused: { icon: '⏸', tone: 'info' },
  ok: { icon: '✓', tone: 'good' },
  inactive: { icon: '○', tone: 'muted' },
};
