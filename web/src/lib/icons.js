// Line symbols (24×24, drawn with the text colour) - shared by the bars and the tiles of the team panel and the
// player area.

export const ICONS = {
  more: 'M4 6h16M4 12h16M4 18h16',
  terminal: 'M4 5h16v14H4zM7 9l3 3-3 3M12 15h5',
  box: 'M12 3 4 7.5v9L12 21l8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9',
  server: 'M4 4h16v6H4zM4 14h16v6H4zM8 7h.01M8 17h.01M12 7h4M12 17h4',
  news: 'M4 5h13v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM17 9h3v10a2 2 0 0 1-2 2M8 9h5M8 13h5M8 17h3',
  tool: 'M14.7 6.3a4 4 0 0 0-5.4 5.2L4 16.8V20h3.2l5.3-5.3a4 4 0 0 0 5.2-5.4l-2.5 2.5-2.4-.6-.6-2.4z',
  home: 'M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  ticket: 'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4z',
  pulse: 'M3 12h4l2-6 4 12 2-6h6',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z',
  swords: 'M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5 21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4M5 21l-2-2',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c0-3.3 3.1-6 7-6s7 2.7 7 6M16 3.5a4 4 0 0 1 0 7M18 14c2.4.6 4 2.6 4 5',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  shield: 'M12 3 4 6v6c0 4.5 3.4 8.3 8 9 4.6-.7 8-4.5 8-9V6z',
  hotbar: 'M2 9h20v6H2zM7 9v6M12 9v6M17 9v6',
  mail: 'M3 6h18v12H3zM3 7l9 6 9-6',
  archive: 'M3 4h18v4H3zM5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8M10 12h4',
  restore: 'M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5',
  image: 'M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M15 9h.01',
  text: 'M5 6h14M5 11h14M5 16h9',
  link: 'M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1',
  login: 'M14 4h5v16h-5M10 8l4 4-4 4M14 12H4',
  userPlus: 'M15 19a6 6 0 0 0-12 0M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M19 8v6M16 11h6',
  chat: 'M4 5h16v11H9l-5 4z',
  heart: 'M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z',
  pin: 'M12 21s-6.5-6.2-6.5-11.2a6.5 6.5 0 0 1 13 0C18.5 14.8 12 21 12 21ZM12 12.3a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.5-2-3.5-2.4 1a7.5 7.5 0 0 0-2.6-1.5L14 2.5h-4l-.4 2.5A7.5 7.5 0 0 0 7 6.5l-2.4-1-2 3.5 2 1.5a7.6 7.6 0 0 0 0 3l-2 1.5 2 3.5 2.4-1a7.5 7.5 0 0 0 2.6 1.5l.4 2.5h4l.4-2.5a7.5 7.5 0 0 0 2.6-1.5l2.4 1 2-3.5z',
  megaphone: 'M3 10v4h3l7 4V6L6 10zM16 9a3 3 0 0 1 0 6M18.5 6.5a7 7 0 0 1 0 11',
  ban: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM5.6 5.6l12.8 12.8',
  logs: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
  sidebar: 'M4 4h16v16H4zM9 4v16',
  power: 'M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0',
};

/** Symbols of known plugins; others get the box. */
export const MODULE_ICONS = { mrchunks: 'grid', mrpvp: 'swords', mrsleep: 'moon', mrwaypoints: 'pin' };

/** A stable hue per plugin id - the same colour on the team panel and the player area. */
export function moduleHue(id) {
  return [...id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7);
}
