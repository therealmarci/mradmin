# MRadmin – Modul-API

Die einheitliche Schnittstelle zwischen MRadmin und anderen Plugins (MRchunks, MRsleep, MRpvp, MRtoolbar …).
**Optional für beide Seiten:** Jedes Plugin läuft ohne MRadmin, MRadmin läuft ohne jedes Plugin.

## Idee

Ein Plugin meldet sich bei MRadmin als **Modul** an und beschreibt selbst, was es kann. MRadmin kennt keine
einzelnen Plugins mehr – Dashboard-Knopf, Spielermenü, Serverzustand, Tickets und Weboberfläche funktionieren für
jedes Modul gleich. Eine neue Aktion im Web braucht deshalb **nur** eine Änderung im Plugin.

| Richtung | Was | API |
|---|---|---|
| Plugin → MRadmin | anmelden | `MRadminService.register(module)` |
| MRadmin → Plugin | Dashboard-Zeilen, eigenes Menü | `AdminModule.summary`, `openMenu` |
| | Spielermenü | `playerSummary`, `openPlayerMenu` |
| | Serverzustand (Auffälliges) | `health`, `openHealth` |
| | Weboberfläche | `AdminModule.web()` → `WebModule` |
| | Hinweise auf der Startseite im Web | `WebModule.alerts()` |
| | Kachel auf der Startseite im Web (ab API 3) | `WebModule.tile()` → `WebTile` |
| | eigene Web-Push-Arten (ab API 3) | `WebModule.pushKinds()` → `WebPushKind` |
| | Namen der Ticket-Kategorien seiner Reports (ab API 3) | `WebModule.reportCategories()` |
| | geladene Chunks im Serverzustand (ab API 3) | `AdminModule.loadedChunks()` |
| | eigene NPCs (z.B. Dummies) aus Listen und Zählungen nehmen (ab API 3) | `AdminModule.isNpc(player)` |
| | Bereiche auf der Spielerseite (/web, ab API 4) | `WebModule.selfAreas(viewer)`, `selfAction`, `selfPermissions` |
| Plugin ↔ Plugin | Spieler ist beschäftigt (Duell) | `AdminModule.busy` / `MRadminService.busy(player)` |
| | Welt gehört einem Plugin (Arena) | `AdminModule.ownsWorld` / `MRadminService.worldOwner(world)` |
| | Gegenstände außerhalb des Inventars (Gürtel) | `AdminModule.storedItems` / `MRadminService.storedItems(player)` |
| Plugin → MRadmin | Admin-Modus abfragen | `MRadminService.adminMode(player)` |
| | Live-Update im Web | `webChanged(id, type, objectId)` |
| | Discord-Admin-Log | `adminLog(id, text)` |
| | Ticket für einen Spieler | `report(id, Report)` / `resolve(id, key, note)` |

Alle Typen liegen im Paket `at.marcelr.mradmin.api` (Teilprojekt `api/`).

## Einbinden (Plugin-Seite)

**`gradle.properties`**
```properties
mradminApi=api-1.8.0
```

**`build.gradle.kts`**
```kotlin
repositories {
    // Lokal gebaute API (in MRadmin: gradlew :api:publishToMavenLocal) hat Vorrang vor JitPack.
    mavenLocal { content { includeGroup("com.github.therealmarci") } }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.therealmarci.*") } }
}
dependencies {
    compileOnly("com.github.therealmarci:mradmin:${providers.gradleProperty("mradminApi").get()}")
}
```

**`plugin.yml`**: `softdepend: [MRadmin]` (dann ist MRadmin schon aktiv, wenn das Plugin startet).

**Zwei Klassen** – nur eine davon kennt MRadmin-Typen:

1. **`MRadminLink`** (ohne MRadmin-Typen, immer geladen): prüft `isPluginEnabled("MRadmin")`, ruft dann das Modul
   auf und fängt `LinkageError` ab. Hört auf `PluginEnableEvent`/`PluginDisableEvent` von MRadmin.
2. **`MRadminModule implements AdminModule (, WebModule)`**: wird nur geladen, wenn MRadmin läuft.

Vorlage: `mrchunks/src/main/java/at/marcelr/mrchunks/api/MRadminLink.java` und `MRadminModule.java`.

## Das Modul

```java
final class MRadminModule implements AdminModule, WebModule {
    public String id()             { return "mrchunks"; }            // klein, stabil
    public Plugin plugin()         { return plugin; }
    public String viewPermission() { return "mrchunks.admin.gui"; }   // ohne sie sieht man nichts vom Modul
    public Material icon()         { return Material.EMERALD_BLOCK; }
    // alles weitere optional (default-Methoden)
}
```

- Texte baut das Modul selbst aus seinen Sprachdateien, für den jeweiligen Betrachter.
- MRadmin ruft alles auf dem **Server-Thread** auf und fängt jede Exception ab (ein kaputtes Modul wird nur geloggt).
- Wird das Plugin deaktiviert, entfernt MRadmin das Modul selbst.
- Der Dashboard-Knopf: bekannte Plugins haben einen Eintrag unter `integrations` in MRadmins Config (Befehl, Name in
  der Sprachdatei). Ein neues Modul bekommt automatisch einen Knopf (Pluginname, `icon()`, Klick → `openMenu`).
  Abschalten: `integrations.<id>: false`.

## Weboberfläche (`WebModule`)

| Route | Methode im Modul | Rechte |
|---|---|---|
| `GET /api/modules/<id>/data/<request>?arg=…` | `data(request, args)` | view + `requests().get(request)` |
| `POST /api/modules/<id>/actions/<action>` | `action(action, args, user)` | view + `actions().get(action)` |
| `GET /api/players/<uuid>/modules/<id>` | `player(uuid)` | `mradmin.player.view` + view |
| `POST /api/players/<uuid>/modules/<id>/<action>` | `playerAction(...)` | dazu `playerActions().get(action)` |

- **Rechte:** `permissions()` ordnet Schlüssel echten Permissions zu (`"manage" → "mrchunks.admin.manage"`).
  `requests()`, `actions()` und `playerActions()` sagen, welcher Schlüssel nötig ist. Eine Aktion, die dort fehlt,
  wird abgelehnt. MRadmin prüft das, **bevor** es das Modul fragt.
- Im Browser stehen die Rechte unter `me.may`: `may.<id>` (sehen) und `may.<id><Schlüssel>`, z.B. `may.mrchunksManage`.
- Die Tabellen müssen **fest** sein: MRadmin liest sie auch außerhalb des Server-Threads (LuckPerms darf nicht auf dem
  Server-Thread gefragt werden).
- `data` liefert nur einfache Werte (Map, List, String, Zahl, Boolean) oder `null` (→ 404). `action` liefert `null`
  (erledigt) oder einen Fehler-Schlüssel, den die Weboberfläche übersetzt (`error.<schlüssel>` in `web/src/lib/i18n.js`).
- Aktionen bekommen den JSON-Body als einfache Java-Werte, auch verschachtelt (Map, List; ganze Zahlen als Long). Texte
  auf oberster Ebene kürzt MRadmin auf 200 Zeichen, verschachtelte prüft das Modul selbst. Body höchstens 64 KB.
- Jede Aktion landet im Web-Protokoll als `<id>-<action>`.
- Im Browser: `web/src/lib/modules.js` (`moduleData`, `moduleAction`, `playerPart`, `playerAction`).
- Eigene Seiten im Web (z.B. Chunkloader, Duelle) bleiben handgemacht; sie nutzen nur noch diese vier Aufrufe.

### Items (`WebItems`, ab API-Version 2)
Inventare, Kits und andere Gegenstände zeigt die Weboberfläche mit den Bildern und Namen aus dem Spiel. Dafür gibt das
Modul seine Items nicht selbst als Daten aus, sondern über MRadmin:

```java
if (service.apiVersion() >= 2) {
    WebItems items = service.items();
    Map<String, Object> data = items.describe(stack);   // für data()/player(): Typ, Anzahl, Name, Verzauberungen ...
    ItemStack back = items.build(dataAusDemEditor);     // IllegalArgumentException("item-enchant" ...) bei Unsinn
}
```

- Das Format steht im Javadoc von `WebItems`. `describe` enthält immer `data` (das ganze Item als Base64). `build` startet
  davon und setzt nur die Felder, die der Editor kennt - alles andere am Item bleibt, wie es war.
- Namen und Lore sind MiniMessage, aber **nur Farben und Stil** (keine Klick-, Hover- oder andere Tags).
- Im Browser: `ItemIcon` (ein Platz mit Bild und Tooltip), `ItemGrid` (Spielerinventar wie im Spiel, mit `onslot` auch
  klickbar), `ItemEditor` (Dialog, prüft beim Übernehmen über `POST /api/items/preview`), Hilfen in
  `web/src/lib/items.svelte.js`.
- Mit einem MRadmin vor API 2 fehlt `items()` ganz (`NoSuchMethodError`) - deshalb vorher `apiVersion()` prüfen und
  sonst wie bisher eigene, einfache Daten liefern (`type`, `amount`, `name`, `enchants`, `damage`, `max`).

## Zwischen den Plugins

**Grundsatz:** Jedes MR-Plugin läuft allein. MR-Plugins kennen sich **nicht** gegenseitig (keine Abhängigkeit, keine
Reflection auf Klassen anderer MR-Plugins) – alles, wofür ein Plugin etwas von einem anderen wissen muss, läuft über
MRadmin. Ohne MRadmin fehlen genau diese Funktionen; das muss in der README des Plugins stehen (Abschnitt
„Zusammenarbeit mit anderen Plugins“, Tabelle „Funktion | Braucht“).

Die Fragen sind allgemein gehalten, damit niemand ein anderes Plugin beim Namen kennen muss:

| Frage | Wer antwortet | Wer fragt |
|---|---|---|
| `busy(player)` – Spieler gerade in Beschlag, Inventar nicht anfassen | MRpvp (Duell, Arena-Editor, Zuschauen) | MRsleep (nicht zählen), MRtoolbar (pausieren) |
| `worldOwner(world)` – Welt gehört einem Plugin | MRpvp (Arena-Welt) | MRchunks (keine Loader), MRsleep (nicht verwalten) |
| `storedItems(player)` – Gegenstände außerhalb des Inventars | MRtoolbar (Gürtel) | MRpvp (beim Sichern vor dem Duell) |

Eigene Antworten zählen nicht: ein Plugin vergleicht die zurückgegebene Modul-Id mit seiner eigenen. NPCs (z.B.
MRchunks' Dummy-Spieler) erkennt jedes Plugin selbst an der Metadata `NPC` bzw. dem Schlüssel `mrchunks:dummy` –
dafür braucht es MRadmin nicht.

## Tickets (`Report`)

```java
service.report("mrchunks", new Report(
        "17",                 // key: eigene Id des Problems - ein zweiter Report mit demselben key geht ins offene Ticket
        "chunkloader-lock",   // Ticket-Kategorie (Name aus reportCategories() des Moduls)
        owner, ownerName,
        "Farm gesperrt",      // Betreff (max. 100 Zeichen)
        details,              // Text, mehrzeilig
        location,             // Teleport-Knopf im Ticket, oder null
        "chunks-locked"));    // Web-Push-Art, oder null
service.resolve("mrchunks", "17", "Die Sperre wurde von Marci aufgehoben.");
```
Der Knopf „Objekt öffnen“ im Ticket ruft `openHealth(viewer, key, back)` des Moduls auf. Den Namen der Kategorie liefert das
Modul über `reportCategories()` (in seiner Server-Sprache), die Web-Push-Art meldet es über `pushKinds()` an - MRadmin
kennt keine Kategorie und keine Push-Art eines Plugins fest. Automatische Tickets schaltet der Server-Betreiber mit
`tickets.plugin-reports` in MRadmins Config ab.



## Spielerseite (`/web`, ab API 4)
Jeder Spieler hat in der Weboberfläche eine eigene Seite (`/me`, Anmeldung nur über `/web` im Spiel). Was dort steht,
liefern die Plugins - MRadmin zeichnet es. Ein neues Plugin oder eine neue Funktion braucht dafür **keine** Änderung in
MRadmin.

```java
public Set<String> selfPermissions() { return Set.of("mrchunks.use"); }   // fest, MRadmin prüft sie vorher (auch offline)

public List<SelfArea> selfAreas(SelfViewer viewer) {                       // Server-Thread
    if (!viewer.has("mrchunks.use")) return List.of();
    return List.of(new SelfArea("loaders", "Chunkloader", "🧱",
            List.of(new SelfBlock.Stats(...)),                               // Kachel auf der Startseite
            List.of(new SelfBlock.Stats(...), new SelfBlock.Table(...), new SelfBlock.AreaMap(...))));
}

public String selfAction(SelfViewer viewer, String area, String action, Map<String, String> args) {
    // alles neu prüfen - nur eigene Sachen, dieselben Regeln wie im Spiel
    return null;                                                             // erledigt, sonst der Grund (in viewer.lang())
}
```

- **Bausteine (`SelfBlock`):** `Stats` (Kennzahlen), `Text`, `Table` (Zeilen mit eigenen Knöpfen), `Buttons`, `Ranking`
  (Rangliste, eigener Platz hervorgehoben), `AreaMap` (Bereiche in Blockkoordinaten, je Welt, ohne Gelände; Link auf eine
  große Karte möglich). Ein `Button` kann nachfragen (`confirm`) oder vorher einen Text abfragen (`input`, kommt als
  `args.value`).
- **Weitere Bausteine ab API 5 (`api-1.4.0`):** `Cards` (Karten nebeneinander mit Status, Kennzahlen, Balken und einem
  Knopf direkt darauf; ein Klick öffnet die Karte mit allen Knöpfen und `details` - weitere Bausteine), `Meters` (Balken
  mit Markierung, z.B. Wert gegen Warn- und Sperrgrenze), `People` (Spieler mit Kopf, Zeile und Knöpfen), `Form`
  (Textfelder und Auswahllisten, abgeschickt als eine Aktion mit jedem Feld als Argument) und `Embed` (eine andere
  Seite im Rahmen, z.B. die Live-Karte; nur http(s), Link zum Öffnen darunter), ab `api-1.4.1` auch `Group` (mehrere
  Bausteine auf einer Kachel, z.B. Einladen über der Liste; in einer geöffneten Karte ein umrandeter Kasten). Nur senden, wenn
  `MRadminService.apiVersion() >= 5` - ein älteres MRadmin kann sie nicht zeichnen (ab API 5 lässt MRadmin unbekannte
  Bausteine einfach weg, statt die Seite abzubrechen). Der Kopf einer Bereichsseite
  zeigt dieselbe `summary` wie die Kachel auf der Startseite.
- **Kachel auf der Startseite (`summary`):** Die erste Kennzahl aus `Stats` steht groß, die weiteren darunter; ein Wert
  der Form „a / b“ (z.B. `120 / 245 Chunks`) wird zusätzlich als Balken gezeigt (Ton `warn`/`bad` färbt ihn), `Text`
  erscheint als kurze Zeile (z.B. eine Sperre). Farbe und großes Symbol kommen aus der Plugin-ID bzw. dem `icon`.
  `Text` mit Ton `bad` oder `warn` zeigt MRadmin zusätzlich als eigene Folie im Karussell der Startseite (mit Link zum
  Bereich) - für Dinge, die der Spieler sofort sehen soll (z.B. MRchunks: Loader wegen Lag gesperrt).
- **Sprache:** `viewer.lang()` ist `de` oder `en` - alle Texte darin bauen. Texte sind reiner Text, nie HTML.
- **Sicherheit:** MRadmin prüft vor jedem Aufruf `mradmin.web.self` und LibertyBans-Sperren. Alles andere (Besitz,
  Permissions, Limits) prüft das Plugin in `selfAction` selbst. Aktionen sind pro Spieler auf 60 pro Minute begrenzt.
- Routen: `GET /api/self` (Bereiche mit Kachel), `GET /api/self/areas/<id>/<bereich>`, `POST .../action`.
- **Ranglisten (`rankings(lang)`):** Liste von `SelfRanking` (alle Spieler, beste zuerst, Wert als Text). MRadmin zeigt
  sie auf der Statistik-Seite neben den Minecraft-Statistiken, lässt ausgetragene Spieler weg, zeigt die besten
  (`player-web.rankings.top`) und den eigenen Platz.

## Webkarte (`mapLayers`, API 7 - nicht mehr angezeigt)

Nach MRadmin 1.1 gibt es squaremaps eigene Webseite nicht mehr, die Karte steht nur noch auf der Spielerseite (siehe
unten). `mapLayers()`, `mapIcons()` (`MapLayer`, `MapMarker`) und `service.mapChanged(id)` bleiben in der API, damit
ältere Module weiter laden, haben aber **keine Wirkung** mehr. Neue Module nutzen `selfMap`.

- **Links:** `service.mapUrl()` führt zum Tab „Karte“ der Spielerseite, `service.mapUrl(world, x, z)` dorthin mit
  `?world=…&x=…&z=…` (die Karte öffnet an dieser Stelle). `null`, wenn es keine Karte (squaremap fehlt, Spielerseite
  aus) oder keine `web.public-url` gibt.
- **Welten von Modulen** (`ownsWorld`) lässt MRadmin mit `map.hide-module-worlds` von der Karte.

## Karte der Spielerseite (`selfMap`, ab API 8)

Der Tab **Karte** der Spielerseite ist MRadmins eigene Karte über den Bildern, die squaremap rendert (MRadmin liest sie
aus squaremaps Ordner). Jedes Modul bringt Ebenen **für genau diesen Spieler** mit:

```java
@Override
public List<SelfMapLayer> selfMap(SelfViewer viewer) {          // Hauptthread, beim Laden und alle paar Sekunden
    return List.of(new SelfMapLayer("shops", "Shops", "#FFB300",
            List.of(new SelfMapLayer.Marker("12", "minecraft:overworld", 250, 150, 0, 0, "Test-Shop", "",
                    List.of("Shops", "250 -60 150"),
                    List.of(new SelfMapLayer.Action("delete", "Löschen", "bad", "Wirklich löschen?", null)))),
            List.of(new SelfMapLayer.Action("create", "Wegpunkt hier erstellen", "good", null,
                    new SelfBlock.Input("Name", "", 32))),
            new SelfMapLayer.Toggle("Ingame: an", true, "xaero")));
}

@Override
public String selfMapAction(SelfViewer viewer, String layer, String action, Map<String, String> args) {
    // args: "marker" (Marker-Aktion) bzw. "world", "x", "z" (Rechtsklick auf freie Stelle), "value" (Eingabe), "on" (Schalter)
    return null; // oder der Grund in viewer.lang()
}
```

- **Marker:** Punkt (`width`/`length` 0) oder Fläche ab (x, z), Welt als Schlüssel (`minecraft:overworld`), Farbe leer =
  Farbe der Ebene, `details` = Zeilen beim Anklicken, `actions` = Rechtsklick-Menü.
- **`here`:** Aktionen für einen Rechtsklick auf eine freie Stelle der Karte.
- **`toggle`:** ein Schalter in der Ebenenliste (z.B. MRwaypoints: „Ingame“ = in Xaero zeigen).
- Reine Texte in `viewer.lang()`. MRadmin prüft `mradmin.web.self` und Sperren, alles andere prüft das Modul in
  `selfMapAction` selbst (wie in `selfAction`). Aktionen sind auf 60 pro Minute begrenzt.
- **Ab API 9** (`api-1.8.0`, vorher `apiVersion() >= 9` prüfen, sonst die alten Konstruktoren nutzen):
  - `group` (7. Wert von `SelfMapLayer`): Überschrift in der Ebenenliste, Ebenen mit gleicher Gruppe stehen zusammen (leer = Name des Plugins).
  - `Action.choice` = `SelfMapLayer.Choice(label, value, options)` mit `Option(id, label, color)`: eine Auswahl im selben Dialog wie `input`, kommt als Argument `choice` an (MRwaypoints: Kategorie beim Erstellen und Bearbeiten).
  - `Action.icon`: Symbol im Menü - `plus`, `edit`, `trash`, `power`, `compass`, `eye` oder leer. Aktionen mit `tone` `bad` stehen rot am Ende des Menüs.
  - MRadmin selbst zeichnet zusätzlich Spieler (eigene Position, andere Online-Spieler) und das Chunkraster (`map.player-page`) - dafür braucht ein Modul nichts.
- **Ab API 10** (`api-1.9.0`, vorher `apiVersion() >= 10` prüfen, sonst den Konstruktor ohne Höhe nutzen): `Marker.y` (letzter Wert, `null` = keine Höhe). Im Nether zeigt MRadmin einen Marker mit Höhe nur auf der Karte der Decke (ab `map.nether-roof.from-y`) bzw. nur darunter; ohne Höhe auf beiden.
- **Ab API 11** (`api-1.10.0`, vorher `apiVersion() >= 11` prüfen): `Marker.card` (eine `SelfBlock.Card` wie im eigenen Bereich, am besten ohne `details`) und `Marker.area` (Id des Bereichs). Ein Klick auf den Punkt zeigt dann die Kachel statt der `details`; ihre Knöpfe gehen an `selfAction(viewer, area, …)`, „Einstellungen öffnen“ führt zu `/me/<modul>/<area>#card=<id>` (die Seite öffnet dort diese Kachel). MRchunks: Auslastung, Ein/Aus, Dummy.
- Routen: `GET /api/self/map`, `GET /api/self/map/players`, `GET /api/self/map/tiles/<welt>/<zoom>/<x>_<z>.png`, `GET /api/self/map/stamps`, `GET /api/self/map/worlds`, `POST /api/self/map/action`.

## Konfiguration im Browser (`webConfig`, ab API 12)

Seite **Plugins → <Plugin> → Konfiguration** (Recht `mradmin.config`). Ein Modul gibt seine `config.yml` frei:

```java
@Override
public @Nullable WebConfig webConfig() {
    return new WebConfig(new File(plugin.getDataFolder(), "config.yml"),
            resource("web-config.yml"),   // Formular (unten)
            resource("config.yml"),       // mitgelieferte Config: Standardwerte, Zeilen von Tabellen
            by -> {                       // nach dem Speichern, Hauptthread - wie der Reload-Befehl
                plugin.reload();
                plugin.discord().log("admin-reload", "admin", by);
            });
}
```

MRadmin liest die Datei neu, prüft die Prüfsumme (Datei inzwischen geändert → 409 `config-changed`), prüft jede Änderung
gegen das Formular (nur Schlüssel des Formulars, Typ und Grenzen), legt die alte Datei als `config.backup-web.yml` ab,
schreibt die neue in einem Schritt (UTF-8 mit BOM, Kommentare und Reihenfolge bleiben) und ruft dann `reload` auf.
Bearbeiten per Datei bleibt möglich. Ein Plugin, das für ältere MRadmin-Versionen baut, braucht keine Prüfung - ältere
MRadmin rufen `webConfig()` nie auf.

**Formular** (`src/main/resources/web-config.yml`, englische Kommentare, Texte DE/EN):

```yaml
sections:
  - id: sleep
    name: {de: Schlafen, en: Sleeping}
    group: {de: Server & Neustarts, en: Server & restarts}   # optional: Überschrift in der Navigation (gleiche Gruppen nebeneinander)
    help: {de: "Kurzer Satz, was der Abschnitt einstellt.", en: "..."}
    fields:
      - key: sleep.percentage          # Schlüssel in config.yml
        type: int                      # Typ, siehe unten
        min: 0
        max: 100
        unit: {de: "%", en: "%"}
        name: {de: Anteil der Schläfer, en: Share of sleepers}
        help: {de: "...", en: "..."}    # Erklärung - im Web hinter einem kleinen „i“ (Hover/Tippen)
        heading: {de: Bossbar, en: Boss bar}   # optional: Zwischenüberschrift vor diesem Feld (beginnt einen Block)
        advanced: true                 # optional: erst nach „Erweiterte Einstellungen“ sichtbar (die Suche findet es immer)
        restart: false                 # true = wirkt erst nach einem Neustart (Hinweis im Web)
        when: sleep.enabled            # nur zeigen, wenn: "key" (Schalter an), "!key" (aus), "key=a|b" (Wert)
```

| `type` | Bedienung | Weitere Angaben |
|---|---|---|
| `bool` | Schalter | |
| `int`, `decimal` | Zahlenfeld | `min`, `max`, `unit` |
| `string`, `text` | Eingabezeile / mehrzeilig | `max-length` (Standard 2000) |
| `secret` | „gesetzt“ / „leer“, neuer Wert per Knopf - der Wert geht nie an den Browser | `max-length` |
| `choice` | bis 4 kurze Werte als Umschalter, sonst Auswahlliste | `options`: Liste aus `{value, de, en}` oder einfachen Werten |
| `color` | Farbwähler + `#RRGGBB` | |
| `duration` | Dauer wie `15m`, `1h30m`, `2d` (Zahl = Minuten), mit Klartext daneben | `permanent: true` erlaubt `permanent` |
| `strings` | Liste als Chips | `count` (genau so viele Einträge, dann feste Felder), `item: duration`, `permanent`, `item: time` (Uhrzeiten `HH:MM` mit Zeitwähler und Knopf „Hinzufügen“, sortiert, ohne Doppelte; ohne Anführungszeichen geschriebene Zahlen wie 330 werden als 05:30 gelesen) |
| `ints` | Liste ganzer Zahlen als Chips | `min`, `max`, `count`, `unit` |
| `choices` | Mehrfachauswahl aus festen Werten (Chips zum Anklicken) | `options` wie bei `choice` |
| `worlds` | Welten des Servers zum Anklicken | |
| `table` | Abschnitt mit Unterschlüsseln als Tabelle (z.B. Discord-Ereignisse) | `columns`: `{key, type: bool\|color\|int\|string, name, default}` (`default` = Wert, solange eine Zeile den Schlüssel nicht hat), `rows`: Name je Zeile |

- **Texte mit Komma** in der Kurzschreibweise `{de: …, en: …}` immer in Anführungszeichen setzen, sonst schneidet YAML den Text am Komma ab.
- Jeder Wert der `config.yml` (außer `config-version`) braucht ein Feld - ein Test im Plugin prüft das (Muster: `WebConfigFormTest` in MRsleep).
- Routen: `GET /api/config/<Plugin>`, `POST /api/config/<Plugin>` `{version, changes}` → `{changed, restart, reloadError, version, sections}`; ungültiger Wert: 400 `{error, field}`.

## Protokoll (`log`, ab API 13)

`MRadminService#log(moduleId, event, text, color)` schreibt ein Ereignis ins Protokoll von MRadmin (Seite „Protokoll“,
bis zu 14 Tage). Gedacht für genau die Ereignisse, die ein Plugin in sein Discord-Log schickt - und zwar immer, auch
wenn Discord aus ist: Text fertig in der Server-Sprache (Discord-Markdown wie `**fett**` wird dargestellt), `color` =
Discord-Farbe. Vorher `apiVersion() >= 13` prüfen. Muster: `DiscordWebhook#log` in MRsleep/MRchunks ruft vor dem
Discord-Teil `plugin.mradmin().log(...)`.

`adminLog(id, text)` geht nur in MRadmins Discord-Log (Ereignis „plugin-alert“), **nicht** ins Protokoll - dafür
sorgt `log`. So nutzen die Plugins MRadmins Webhook als Rückfall (`discord.use-mradmin-webhook`, eigene
`webhook-url` leer): `log(...)` fürs Protokoll, `adminLog(...)` statt des eigenen Webhooks.

## Versionen

- API-Version im Code: `MRadminService.API_VERSION` (beim Bauen), `service.apiVersion()` (des laufenden MRadmin).
  Version 1 = `api-1.0.0`, Version 2 = `api-1.1.0` (`items()`), Version 3 = `api-1.2.0` (`tile()`, `pushKinds()`,
  `reportCategories()`, `loadedChunks()`, `isNpc()`), Version 4 = `api-1.3.0` (Spielerseite: `selfAreas`, `selfAction`,
  `selfPermissions`, `rankings`, `SelfArea`, `SelfBlock`, `SelfViewer`, `SelfRanking`), Version 5 = `api-1.4.0`
  (Bausteine `Cards`, `Meters`, `People`, `Form`, `Embed`; `Group` ab `api-1.4.1`), Version 6 = `api-1.5.0`
  (`moduleHistoryAdd`, `moduleHistory`: eigene Messwerte eines Moduls im Speicher von MRadmin ablegen - Dateien,
  SQLite oder MySQL je nach `storage.type`; eine Zeile Text im eigenen Format, Schreiben im Hintergrund, Lesen nur beim
  Aktivieren. So nutzt es MRchunks für den Verlauf der Chunkloader), Version 7 = `api-1.6.0` (Webkarte: `mapLayers`,
  `mapIcons`, `MapLayer`, `MapMarker`, `mapChanged` - nach MRadmin 1.1 ohne Wirkung; `mapUrl`), Version 8 = `api-1.7.0` (Karte der Spielerseite: `selfMap`,
  `selfMapAction`, `SelfMapLayer`), Version 9 = `api-1.8.0` (`SelfMapLayer.group`, `Action.choice`, `Action.icon`), Version 10 = `api-1.9.0` (`Marker.y`), Version 11 = `api-1.10.0` (`Marker.card`, `Marker.area`), Version 12 = `api-1.11.0` (`webConfig`, `WebConfig`: Konfiguration im Browser), Version 13 = `api-1.12.0` (`log`: Protokoll).
- Neue Methoden kommen nur als `default`-Methoden mit einer höheren Version dazu – ein Modul für eine ältere API läuft
  weiter. Wer neuere Methoden nutzt, prüft vorher `apiVersion()`.
- Veröffentlicht wird die API über **JitPack** mit einem Git-Tag `api-<version>` im MRadmin-Repo (z.B. `api-1.0.0`);
  `jitpack.yml` baut nur `:api` mit Java 25. Lokal: `gradlew :api:publishToMavenLocal`.

## Stand

MRchunks, MRsleep, MRpvp und MRtoolbar sind Module (je `api/MRadminLink` + `api/MRadminModule`). Die alten
statischen Hook-Klassen (`MRadminHook`, `MRadminApi`, `LoaderLockEvent`) gibt es nicht mehr: Plugin-Versionen von vor
der Modul-API verbinden sich mit diesem MRadmin nicht (Warnung beim Start), laufen aber sonst normal weiter.
