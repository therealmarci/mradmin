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
mradminApi=api-1.2.0
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
  Seite im Rahmen, z.B. die Live-Karte; nur http(s), Link zum Öffnen darunter). Nur senden, wenn
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

## Versionen

- API-Version im Code: `MRadminService.API_VERSION` (beim Bauen), `service.apiVersion()` (des laufenden MRadmin).
  Version 1 = `api-1.0.0`, Version 2 = `api-1.1.0` (`items()`), Version 3 = `api-1.2.0` (`tile()`, `pushKinds()`,
  `reportCategories()`, `loadedChunks()`, `isNpc()`), Version 4 = `api-1.3.0` (Spielerseite: `selfAreas`, `selfAction`,
  `selfPermissions`, `rankings`, `SelfArea`, `SelfBlock`, `SelfViewer`, `SelfRanking`), Version 5 = `api-1.4.0`
  (Bausteine `Cards`, `Meters`, `People`, `Form`, `Embed`).
- Neue Methoden kommen nur als `default`-Methoden mit einer höheren Version dazu – ein Modul für eine ältere API läuft
  weiter. Wer neuere Methoden nutzt, prüft vorher `apiVersion()`.
- Veröffentlicht wird die API über **JitPack** mit einem Git-Tag `api-<version>` im MRadmin-Repo (z.B. `api-1.0.0`);
  `jitpack.yml` baut nur `:api` mit Java 25. Lokal: `gradlew :api:publishToMavenLocal`.

## Stand

MRchunks, MRsleep, MRpvp und MRtoolbar sind Module (je `api/MRadminLink` + `api/MRadminModule`). Die alten
statischen Hook-Klassen (`MRadminHook`, `MRadminApi`, `LoaderLockEvent`) gibt es nicht mehr: Plugin-Versionen von vor
der Modul-API verbinden sich mit diesem MRadmin nicht (Warnung beim Start), laufen aber sonst normal weiter.
