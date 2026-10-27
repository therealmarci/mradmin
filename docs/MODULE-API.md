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
mradminApi=api-1.0.0
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
        "chunkloader-lock",   // Ticket-Kategorie (Name aus MRadmins Sprachdatei ticket-categories.<kind>)
        owner, ownerName,
        "Farm gesperrt",      // Betreff (max. 100 Zeichen)
        details,              // Text, mehrzeilig
        location,             // Teleport-Knopf im Ticket, oder null
        "chunks-locked"));    // Web-Push-Art, oder null
service.resolve("mrchunks", "17", "Die Sperre wurde von Marci aufgehoben.");
```
Der Knopf „Objekt öffnen“ im Ticket ruft `openHealth(viewer, key, back)` des Moduls auf.

## Versionen

- API-Version im Code: `MRadminService.API_VERSION` (beim Bauen), `service.apiVersion()` (des laufenden MRadmin).
  Version 1 = `api-1.0.0`, Version 2 = `api-1.1.0` (`items()`).
- Neue Methoden kommen nur als `default`-Methoden mit einer höheren Version dazu – ein Modul für eine ältere API läuft
  weiter. Wer neuere Methoden nutzt, prüft vorher `apiVersion()`.
- Veröffentlicht wird die API über **JitPack** mit einem Git-Tag `api-<version>` im MRadmin-Repo (z.B. `api-1.0.0`);
  `jitpack.yml` baut nur `:api` mit Java 25. Lokal: `gradlew :api:publishToMavenLocal`.

## Stand

MRchunks, MRsleep, MRpvp und MRtoolbar sind Module (je `api/MRadminLink` + `api/MRadminModule`). Die alten
statischen Hook-Klassen (`MRadminHook`, `MRadminApi`, `LoaderLockEvent`) gibt es nicht mehr: Plugin-Versionen von vor
der Modul-API verbinden sich mit diesem MRadmin nicht (Warnung beim Start), laufen aber sonst normal weiter.
