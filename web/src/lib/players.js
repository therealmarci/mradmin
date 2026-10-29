// Marks in the player list - always symbol and word, never colour alone. Each comes from the part the member may
// see (MRadmin itself, MRsleep, MRchunks); unknown marks from newer plugins still show with a dot.
export const FLAGS = {
  frozen: { icon: '❄', tone: 'info' },
  reported: { icon: '!', tone: 'warn' },
  'sleep-blocking': { icon: '⛔', tone: 'warn' },
  'sleep-banned': { icon: '✕', tone: 'bad' },
  'chunks-locked': { icon: '', tone: 'bad' },
  'chunks-warn': { icon: '', tone: 'warn' },
  'chunks-banned': { icon: '✕', tone: 'bad' },
  'lb-banned': { icon: '⛔', tone: 'bad' },
  'lb-muted': { icon: '🔇', tone: 'warn' },
};
