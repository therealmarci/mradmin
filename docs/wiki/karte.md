# Karte (squaremap)

Die [Spielerseite](spielerseite.md) hat einen Tab **Karte** mit echtem Minecraft-Gelände. Darauf zeigen die anderen Plugins ihre **Ebenen** - z.B. die eigenen Chunkloader (MRchunks) oder die Server-Wegpunkte (MRwaypoints), jeweils passend für den angemeldeten Spieler, mit Rechtsklick-Aktionen. Die Bilder dafür rendert **[squaremap](https://modrinth.com/plugin/squaremap)**, um das du dich nicht kümmern musst.

## Was MRadmin selbst erledigt
- **Automatisch installiert:** Fehlt squaremap, lädt MRadmin das offizielle Release für deine Minecraft-Version von Modrinth (nur wenn die SHA-512-Prüfsumme stimmt). Es ist nach dem nächsten Neustart aktiv.
- **Automatisch aktualisiert:** Neue squaremap-Versionen landen in `plugins/update/`, Paper tauscht sie beim nächsten Neustart (geprüft alle `map.squaremap.check-hours`, Standard 12 Stunden). Abschaltbar mit `map.squaremap.auto-install` und `auto-update`.
- **Automatisch gerendert:** Nach der Installation und nach jedem squaremap-Update rendert MRadmin alle Welten der Karte einmal komplett (alle generierten Chunks, im Hintergrund; `map.squaremap.full-render`, gemerkt in `plugins/MRadmin/map-render.yml`). Welten ohne Kartenbilder werden immer gerendert.
- **Automatisch eingerichtet:** Bei jedem Start bringt MRadmin die squaremap-Config auf den passenden Stand: squaremaps eigene Webseite aus, schnelle Aktualisierung, keine Spieler auf der Karte, Nether von innen statt der Bedrock-Decke, Namen der Dimensionen. Welten, die einem Plugin gehören (z.B. die Arena-Welt von MRpvp), bleiben von der Karte (`map.hide-module-worlds`). **Was du selbst in `plugins/squaremap/config.yml` änderst, bleibt.** `/mradmin map setup` überschreibt auch deine Änderungen, `map.auto-configure: false` lässt die squaremap-Config ganz in Ruhe.
- **Version im Blick:** Auf der Seite **Plugins** steht bei MRadmin auch die squaremap-Version (und ein wartendes Update).

## Netherdecke als eigene Karte
squaremap zeigt den Nether von innen. Die Bedrockdecke und was darauf gebaut ist, rendert MRadmin selbst als eigene Karte **„Nether · Decke“** (Bilder in `plugins/MRadmin/map-roof/`): einmal komplett im Hintergrund, danach jeder Chunk, in dem sich oben etwas ändert. Steht ein Spieler auf der Decke (ab Y 128), springt die Karte bei „Zu mir“ und „Folgen“ von selbst dorthin, sonst über die Welt-Auswahl. Wegpunkte und Chunkloader erscheinen auf der Karte, auf der sie liegen. Einstellungen unter `map.nether-roof` (`enabled`, `from-y`, `full-render`, `chunks-per-second`).

## Nur in MRadmin
Die Karte gibt es **nur in MRadmin** - squaremaps eigene Webseite (Port 8080) ist aus, du brauchst keinen Port freizugeben und keinen eigenen Proxy. Die Kartenbilder liefert MRadmin selbst aus, nur an angemeldete Spieler. Dafür muss die Spielerseite eingeschaltet sein (`player-web.enabled`) und `web.public-url` stimmen, damit `/mradmin map` einen Link geben kann.

`/mradmin map` gibt Spielern den Link zur Karte (`mradmin.map`). Admins (`mradmin.map.admin`) sehen dort zusätzlich die squaremap-Version und ob ein Update auf den Neustart wartet oder die Installation fehlgeschlagen ist.

## Spieler auf der Karte
> [!TIP]
> **Spieler auf der Karte** (man selbst und die anderen Online-Spieler), Öffnen am eigenen Standort, „Mir folgen“ und das Chunkraster stellst du unter `map.player-page` ein. Für ein Manhunt z.B. `map.player-page.players.others: false` - dann sieht jeder nur sich selbst. Spieler im Admin-Modus, im Zuschauermodus und unsichtbare Spieler sehen andere nie (abschaltbar), in `players.hidden-worlds` genannte Welten zeigen niemanden. Andere Spieler sieht nur, wer `mradmin.map.players` hat. Mit `players.allow-hide` dürfen Spieler sich selbst ausblenden.

## Karte für alle ausschalten
Auf der Seite **Karte** im Adminportal (mit MRwaypoints auch Kategorien und Wegpunkte) stehen zwei Schalter (`mradmin.map.admin`): **Karte der Spielerseite** (aus = der Tab „Karte“ verschwindet für alle Spieler) und **Spieler auf der Karte** (aus = niemand sieht andere Spieler oder kann nach ihnen suchen). Beide wirken sofort und bleiben über Neustarts.

> [!NOTE]
> Bis MRadmin 1.1 hat MRchunks squaremap angebunden, und die Karte war eine öffentliche squaremap-Webseite. Aktualisiere MRadmin und MRchunks gemeinsam - MRadmin übernimmt die Einrichtung aus der alten MRchunks-Config. Ein Reverse-Proxy oder eine Portfreigabe für squaremap kann danach weg.
