// Texts of the ticket log view. Kept apart from lib/i18n.js because the same view also runs as its own bundle on
// the MRtechnology website (viewer for logs forwarded to MRtechnology) - both always show exactly the same.

const TEXTS = {
  de: {
    jumpError: 'Fehler', jumpWarn: 'Warnung', jumpLag: 'Lag', jumpDeath: 'Tod', jumpEvent: 'Ereignis',
    jumpHint: 'Zum nächsten nach der aktuellen Stelle springen', newer: '{count} neuere Einträge anzeigen',
    recordingFrom: 'Aufzeichnung beginnt {time}', zoomIn: 'Hineinzoomen', zoomOut: 'Herauszoomen', follow: 'Spieler folgen', player: 'Spieler', around: 'Ringsum',
    emptyHand: 'leere Hand', level: 'Level', chronicle: 'Chronik', noChronicle: 'Keine Geschehnisse für diesen Filter.',
    g_act: 'Aktionen', g_fight: 'Kampf', g_around: 'Umgebung', g_move: 'Bewegung',
    c_broken: '{n}× {what} abgebaut', c_placed: '{n}× {what} platziert', c_picked: '{n}× {what} aufgehoben', c_dropped: '{n}× {what} fallen gelassen',
    c_taken: '{n} Schaden durch {what}', c_dealt: '{n} Schaden an {what}', c_command: '{what}', c_came: '{what} kam in die Nähe', c_went: '{what} ging weg',
    c_despawn: '{n}× {what} despawnt', c_mobdeath: '{n}× {what} gestorben',
    inventoryAtEnd: 'Das Inventar erscheint am Ende der Zeitleiste (Moment des Tickets).',
    area: 'Umgebung', tabMap: 'Spieler & Karte', tabPerf: 'Leistung', tabLog: 'Protokoll', tabServer: 'Server',
    timeline: 'Verlauf',     laneLog: 'Server-Log', lanePerf: 'Leistung', lanePlayer: 'Spieler',
                lowest: 'tiefster Wert', highest: 'höchster Wert', highestOf: 'höchster Wert von {max}', longestTick: 'längster Tick',
    atCreation: 'beim Erstellen', serverData: 'Server', slots: 'Plätze',
            errors: 'Fehler', warnings: 'Warnungen', events: 'Ereignisse',
    zoom: 'Zeitraum', min: '{n} Min',
    tps: 'TPS', mspt: 'Tick-Zeit', cpu: 'CPU', ram: 'RAM', world: 'Welt', entities: 'Entities', tiles: 'Block-Entities', chunks: 'Chunks',
    players: 'Spieler', avg: 'Durchschnitt', max: 'Maximum', server: 'Server', system: 'System', used: 'belegt',
   
    noHealth: 'Keine Leistungswerte aus diesem Zeitraum (Server gerade gestartet?).', created: 'Ticket erstellt',
    software: 'Software', cores: 'Kerne', uptime: 'Läuft seit', distances: 'Sicht/Simulation', storage: 'Speicher', jvm: 'JVM',
    creator: 'Ersteller', target: 'Gemeldet', position: 'Position', chunk: 'Chunk', biome: 'Biom', light: 'Licht',
    gameMode: 'Spielmodus', flying: 'fliegt', state: 'Zustand', client: 'Client', protocol: 'Protokoll', viewDistance: 'Sichtweite',
    hand: 'In der Hand', effects: 'Effekte', worlds: 'Welten', hotChunks: 'Chunks mit den meisten Entities', plugins: 'Plugins ({count})',
    all: 'Alles', console: 'Server-Log', search: 'Im Log suchen ...', older: '{count} ältere Einträge anzeigen',
    empty: 'Keine Einträge für diesen Filter.', cut: 'Das Server-Log war länger - nur die neuesten Zeilen wurden gespeichert.',
        layerEntities: 'Entities', layerDespawned: 'Despawnte Items', layerDeaths: 'Tode', layerPath: 'Weg',     hostile: 'feindlich', passive: 'friedlich', item: 'Item',         inventory: 'Inventar von {name}',
    noMap: 'Kein Kartenbild (Chunks nicht geladen).', none: 'keine', loaderArea: 'Fläche',
    module: 'Von {plugin}', slot: 'Platz',
    nearPlayers: 'Spieler in der Nähe', entitiesNear: 'Entities in der Nähe', tilesNear: 'Block-Entities',
                    mNoServer: 'Keine Serverwerte für diese Minute (außerhalb des Logs).',
   
    ev_join: 'Betreten', ev_quit: 'Verlassen', ev_death: 'Gestorben', ev_teleport: 'Teleport',
    play: 'Abspielen', speed: 'Geschwindigkeit', noTerrain: 'Gelände hier nicht aufgezeichnet (Chunks waren nicht geladen)',
  },
  en: {
    jumpError: 'Error', jumpWarn: 'Warning', jumpLag: 'Lag', jumpDeath: 'Death', jumpEvent: 'Event',
    jumpHint: 'Jump to the next one after the current moment', newer: 'Show {count} newer entries',
    recordingFrom: 'Recording starts {time}', zoomIn: 'Zoom in', zoomOut: 'Zoom out', follow: 'Follow the player', player: 'Player', around: 'Around',
    emptyHand: 'empty hand', level: 'Level', chronicle: 'Chronicle', noChronicle: 'Nothing happened for this filter.',
    g_act: 'Actions', g_fight: 'Combat', g_around: 'Surroundings', g_move: 'Movement',
    c_broken: '{n}× {what} broken', c_placed: '{n}× {what} placed', c_picked: '{n}× {what} picked up', c_dropped: '{n}× {what} dropped',
    c_taken: '{n} damage from {what}', c_dealt: '{n} damage to {what}', c_command: '{what}', c_came: '{what} came near', c_went: '{what} left',
    c_despawn: '{n}× {what} despawned', c_mobdeath: '{n}× {what} died',
    inventoryAtEnd: 'The inventory shows at the end of the timeline (the moment of the ticket).',
    area: 'Surroundings', tabMap: 'Player & map', tabPerf: 'Performance', tabLog: 'Log', tabServer: 'Server',
    timeline: 'Timeline',     laneLog: 'Server log', lanePerf: 'Performance', lanePlayer: 'Player',
                lowest: 'lowest', highest: 'highest', highestOf: 'highest of {max}', longestTick: 'longest tick',
    atCreation: 'when it was created', serverData: 'Server', slots: 'Slots',
            errors: 'errors', warnings: 'warnings', events: 'events',
    zoom: 'Time span', min: '{n} min',
    tps: 'TPS', mspt: 'Tick time', cpu: 'CPU', ram: 'RAM', world: 'World', entities: 'Entities', tiles: 'Block entities', chunks: 'Chunks',
    players: 'Players', avg: 'Average', max: 'Maximum', server: 'Server', system: 'System', used: 'used',
   
    noHealth: 'No performance values from this time (server just started?).', created: 'Ticket created',
    software: 'Software', cores: 'cores', uptime: 'Running for', distances: 'View/simulation', storage: 'Storage', jvm: 'JVM',
    creator: 'Creator', target: 'Reported', position: 'Position', chunk: 'chunk', biome: 'Biome', light: 'light',
    gameMode: 'Game mode', flying: 'flying', state: 'State', client: 'Client', protocol: 'protocol', viewDistance: 'view distance',
    hand: 'In hand', effects: 'Effects', worlds: 'Worlds', hotChunks: 'Chunks with the most entities', plugins: 'Plugins ({count})',
    all: 'All', console: 'Server log', search: 'Search the log ...', older: 'Show {count} older entries',
    empty: 'No entries for this filter.', cut: 'The server log was longer - only the newest lines were saved.',
        layerEntities: 'Entities', layerDespawned: 'Despawned items', layerDeaths: 'Deaths', layerPath: 'Way',     hostile: 'hostile', passive: 'passive', item: 'item',         inventory: 'Inventory of {name}',
    noMap: 'No map picture (chunks not loaded).', none: 'none', loaderArea: 'Area',
    module: 'From {plugin}', slot: 'Slot',
    nearPlayers: 'Players nearby', entitiesNear: 'Entities nearby', tilesNear: 'Block entities',
                    mNoServer: 'No server values for this minute (outside the log).',
   
    ev_join: 'Joined', ev_quit: 'Left', ev_death: 'Died', ev_teleport: 'Teleport',
    play: 'Play', speed: 'Speed', noTerrain: 'Terrain not recorded here (chunks were not loaded)',
  },
};

/** A text of the ticket log view in the given language ("de" or "en"), {name} parts filled from values. */
export function tl(lang, key, values = {}) {
  const text = TEXTS[lang === 'de' ? 'de' : 'en'][key] ?? TEXTS.en[key] ?? key;
  return text.replace(/\{(\w+)\}/g, (_, k) => (values[k] ?? `{${k}}`));
}
