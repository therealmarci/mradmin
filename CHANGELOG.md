# Changelog

Alle wichtigen Änderungen an MRadmin. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Chunkloader mit Chunk-Budget (MRchunks):** Die Weboberfläche zeigt bei jedem Chunkloader seine Größe (z.B. 5×9 Chunks) und das Budget des Owners. Mit der MRchunks-Permission `mrchunks.admin.resize` lässt sich die Größe dort pro Richtung oder in alle Richtungen ändern. Auf der Spielerseite steht statt Setz- und Aktiv-Limit das Chunk-Budget (genutzt / verfügbar, mit `mrchunks.admin.limit` änderbar), dazu die feste Höchstzahl an Loadern. Die Aktivität eines Loaders zeigt zusätzlich, wie ausgelastet alle aktiven Loader des Owners zusammen sind. Braucht MRchunks ab der Version mit Chunk-Budget; ältere MRchunks-Versionen werden weiter wie bisher angezeigt.

- **Spielerseite im Web (`/web`, im Aufbau):** Jeder Spieler bekommt mit `/web` einen Link zu seiner eigenen Seite in der Weboberfläche. Der Link gilt nur einmal und ein paar Minuten, meldet das Gerät aber dauerhaft an - danach lässt sich die Seite (`<web.public-url>/me`) als Lesezeichen speichern. Jeder Besuch verlängert die Anmeldung. Unter **Geräte** meldet man einzelne Geräte ab, `/web devices` und `/web logout` im Spiel melden alle ab. Deutsch und Englisch, umschaltbar. Neu in der Config: `player-web` (`enabled`, Standard `false`, `session-days` 90, `link-minutes` 5, `max-devices` 5; config-version 24). Permission `mradmin.web.self` (in `mradmin.player`). Gesperrte Spieler (LibertyBans) kommen nicht hinein. Auf der Seite: die Bereiche der installierten Plugins (z.B. MRchunks: eigene Chunkloader verwalten) und **eigene Tickets** wie mit `/report` - neue öffnen, antworten, schließen (gleiche Regeln: Wartezeit, offene Tickets, Mindestlängen; Namen des Teams nur mit `tickets.show-staff-names`). Dazu eine **Statistik-Seite** mit Ranglisten: Minecraft-Statistiken aller Spieler (auch offline - Spielzeit, abgebaute Blöcke, besiegte Mobs, Tode, Strecke ...; Auswahl und Anzahl Plätze in `player-web.rankings`, config-version 25) und Ranglisten der Plugins (z.B. MRpvp). Der eigene Platz wird hervorgehoben, auch wenn er weiter unten liegt. Jeder Spieler kann sich auf der Seite aus allen Ranglisten austragen.
- **Neuigkeiten und Changelogs für die Spielerseite:** Admins schreiben in der Weboberfläche unter **Neuigkeiten** Beiträge für die Spieler - als Neuigkeit oder Changelog, auf Deutsch und Englisch, anheftbar und mit Ablaufdatum. Auf der Startseite der Spielerseite (`/web`) steht unter der Begrüßung ein Karussell, das von selbst weiterblättert und sich am Handy wischen lässt: geplante oder laufende Wartung, ein geplanter Neustart und die neuesten Beiträge. Alle Beiträge stehen auf der neuen Seite **Neuigkeiten**. Permission `mradmin.news` (in `mradmin.groups.admin`). Neu in der Config: `player-web.news` (`enabled` true, `seconds` 8, `slides` 5, `show-maintenance`, `show-restart`; config-version 26). Der Lesezeichen-Tipp auf der Spielerseite ist jetzt nur noch eine dezente Zeile am Ende.
- **Sprache im Web umschaltbar:** Die Weboberfläche merkt sich eine gewählte Sprache (Deutsch/Englisch) im Browser, sonst gilt wie bisher die Browsersprache.
- **Modul-API 1.2.0 (API-Version 3):** Plugins liefern jetzt selbst ihre Kachel auf der Startseite der Weboberfläche (`WebModule.tile()`), ihre Push-Benachrichtigungen (`pushKinds()`), die Namen ihrer Ticket-Kategorien (`reportCategories()`), die von ihnen geladenen Chunks für den Serverzustand (`AdminModule.loadedChunks()`) und ihre Kunstspieler (`isNpc()`). Nur Ergänzungen - Plugins für ältere API-Versionen laufen weiter. Details in `docs/MODULE-API.md`.

### Behoben & geändert
- **Schönere Kacheln auf der Spielerseite:** Die Kacheln der Plugins auf der Startseite der Spielerseite (`/web`) haben ein neues Design: eigene Farbe pro Plugin, Symbol groß im Hintergrund, die wichtigste Zahl groß, weitere Werte darunter, Werte wie „120 / 245 Chunks“ mit Balken, Sperren als Warnzeile.
- **Permissions-Übersicht:** `docs/PERMISSIONS.md` nennt für MRchunks `mrchunks.budget.<n>` statt `mrchunks.limit.<n>` / `mrchunks.active.<n>`.
- **MRadmin kennt MRchunks nicht mehr fest:** Kachel auf der Startseite, Push-Art „Gesperrte Chunkloader“, Ticket-Kategorie „Chunkloader-Sperre“, die Kurve der Chunkloader im Serverzustand und das Erkennen der Dummies kommen jetzt über die Modul-API von MRchunks. Dafür braucht es MRchunks ab der Version mit Chunk-Budget - mit älteren MRchunks fehlen Kachel, Push-Art und Kurve. Deine Wahl bei der Push-Art bleibt erhalten.
- **Config:** `tickets.chunkloader-locks` heißt jetzt `tickets.plugin-reports` und schaltet automatische Tickets aller Plugins ab (Wert wird übernommen, config-version 23). Die Kurve im Serverzustand heißt „davon von Plugins geladen“.

## [0.8.0] - 2026-10-28

> [!WARNING]
> **Neue Permission-Gruppen:** Die Pakete `mradmin.support`, `mradmin.staff` und `mradmin.admin` gibt es nicht mehr. Stattdessen gibt es in allen MRtechnology-Plugins dieselben Gruppen `<plugin>.player` und `<plugin>.groups.support|staff|admin`. Bitte die LuckPerms-Gruppen umstellen, z.B. `/lp group staff permission unset mradmin.staff` und `/lp group staff permission set mradmin.groups.staff true`. Außerdem ist `/report` nicht mehr automatisch für alle Spieler da: `/lp group default permission set mradmin.player true`.

### Neu
- **Permission-Gruppen:** `mradmin.player` (`/report`), `mradmin.groups.support` (Übersicht, Serverzustand und Health, Tickets komplett, Spieler ansehen, Teleport, Inventar ansehen, Staff-Chat, Weboberfläche, Wartung ansehen und joinen, Versionen und Web-Konsole ansehen), `mradmin.groups.staff` (+ herholen, einfrieren, kicken, Admin-Modus) und `mradmin.groups.admin` (alles, auch Befehle in der Web-Konsole). Jede Gruppe enthält die darunter, alle Einzel-Permissions bleiben.
- **Items wie im Spiel in der Weboberfläche:** Inventare (z.B. bei Duellen) zeigen die echten Item-Bilder aus dem Inventar, Anzahl, Haltbarkeit, Verzauberungs-Glanz und beim Darüberfahren bzw. Antippen den Tooltip wie im Spiel (Name, Verzauberungen, Trank-Effekte, Beschreibung) - in der Sprache des Browsers. Dafür lädt MRadmin beim ersten Start mit eingeschalteter Weboberfläche **einmal** den offiziellen Spiel-Client deiner Minecraft-Version von Mojang (rund 40 MB, mit Prüfsumme geprüft) und speichert nur die fertigen Bilder und Item-Namen unter `plugins/MRadmin/items/<version>/`. Neuer Config-Schlüssel `web.item-icons` (Standard `true`, `false` = kein Download, dann Kürzel statt Bildern; config-version 22).
- **Item-Editor:** Ein Dialog zum Bearbeiten eines Items - Item mit Bild suchen, Anzahl, eigener Name und Beschreibung (MiniMessage, nur Farben und Stil), Verzauberungen (auch über die normale Stufe hinaus), Glanz, unzerstörbar, Haltbarkeit, Trank mit eigenen Effekten, Lederfarbe, Rüstungsbesatz, Raketen-Flugdauer, mit Live-Vorschau. Benutzt wird er z.B. vom kommenden Kit-Editor in MRpvp.
- **Kit-Editor für Duelle (mit MRpvp):** Neue Seite Duelle → Kits mit allen Kits, Reihenfolge, Kopieren, Neu, Umbenennen, Löschen und dem Kit-Editor (Inventar mit Item-Editor, Texte DE/EN, Symbol, Permission, alle Regeln). Ist ein Kit noch auf eine gelöschte Arena festgelegt, zeigt der Editor das an. Mit dem neuen MRpvp auch festes Zeitlimit, Herzen, Effekte beim Start und Item-Cooldowns sowie **Export** (Kits auswählen, Datei herunterladen) und **Import** (Datei wählen, Vorschau pro Kit: neu anlegen oder ersetzen, fehlende Arenen zuordnen oder weglassen). Permission `mrpvp.admin.kits`.
- **Modul-API 2 (`api-1.1.0`):** `MRadminService.items()` wandelt Items in Daten für die Weboberfläche und zurück (`WebItems`), damit andere Plugins Inventare und Items mit Bildern zeigen und bearbeiten lassen können. Modul-Aktionen bekommen jetzt auch verschachtelte Werte (Listen, Objekte). Plugins für API 1 laufen unverändert weiter.

### Entfernt
- **Pakete `mradmin.support`, `mradmin.staff`, `mradmin.admin`:** ersetzt durch `mradmin.groups.support`, `mradmin.groups.staff` und `mradmin.groups.admin` (siehe oben - Support darf jetzt mehr als vorher: Serverzustand, Teleport, Inventar ansehen, Wartung joinen).

### Behoben & geändert
- **`/report` nur noch mit Permission:** `mradmin.tickets.create` ist nicht mehr automatisch für alle Spieler - vergib `mradmin.player` an deine Spieler-Gruppe.
- **`/mradmin version` nur noch mit Permission:** `mradmin.version` (in `mradmin.player`) - bisher durfte das jeder.
- **LibertyBans-Rechte in den Gruppen:** `mradmin.groups.support` sieht Strafen und die Bann- und Mute-Listen, `mradmin.groups.staff` darf kicken, muten und verwarnen, `mradmin.groups.admin` bannen und entbannen (auch Entbannungsanträge). Gilt im Web und für die LibertyBans-Befehle im Spiel.
- **Web-Konsole in den Gruppen:** Ansehen (`mradmin.web.console.view`) ist in `mradmin.groups.support`, Befehle ausführen (`mradmin.web.console.execute`) in `mradmin.groups.admin`. Bisher war die Konsole in keinem Paket.
- **Chunkloader und Schlaf im Web nach den neuen Rechten:** An/aus braucht bei MRchunks jetzt `mrchunks.admin.toggle`, Verknüpfen `mrchunks.admin.link` (ältere MRchunks-Versionen weiter mit `mrchunks.admin.manage`). Bei MRsleep sind Bypass- und Sperr-Zeiten über der eigenen Grenze ausgegraut, „Cooldown zurücksetzen“ braucht `mrsleep.admin.resetcooldown`.
- **Permission-Übersicht:** [docs/PERMISSIONS.md](docs/PERMISSIONS.md) zeigt für alle MRtechnology-Plugins, was Support, Staff und Admin dürfen, mit fertigen LuckPerms-Befehlen.
- **Dashboard-Knöpfe der anderen Plugins:** Ob ein Teammitglied den Knopf von MRchunks, MRpvp, MRsleep oder MRtoolbar sieht, bestimmt jetzt das jeweilige Plugin selbst (seine Ansichts-Permission). MRadmin hat keine Permission-Namen anderer Plugins mehr fest eingebaut - auch nicht für die Push-Benachrichtigung „Chunkloader gesperrt“.

## [0.7.0] - 2026-10-27

### Neu
- **Duelle steuern und nachträglich aus der Wertung nehmen (mit MRpvp):** Die Seite einer laufenden Runde hat jetzt Pausieren/Fortsetzen (`mrpvp.admin.pause`), „<Spieler> gewinnt“ und Abbrechen (`mrpvp.admin.stop`). Beendete Runden lassen sich aus der Wertung nehmen (mit optionalem Grund) und wieder werten (`mrpvp.admin.review`).
- **Chunkloader: Accounts verknüpfen im Web:** Auf der Spielerseite unter „Verknüpfte Accounts“ lassen sich Accounts per Spielername als Zweit- oder Haupt-Account verknüpfen (mit MRchunks, `mrchunks.admin.manage`). Lösen ging schon vorher.
- **Versionen & Updates in der Weboberfläche:** Neue Seite **Versionen** mit allen MRtechnology-Plugins (installierte und neueste Version, was neu ist). Updates lassen sich per Klick herunterladen - nur aus dem offiziellen GitHub-Release, für die eigene Minecraft-Version und mit geprüfter SHA-256-Prüfsumme - und werden beim nächsten Neustart von Paper installiert. Auf Wunsch automatisch: `versions.auto-download` (`off`, `patch`, `minor`, `all`). Permissions `mradmin.versions.view` und `mradmin.versions.install`, Config-Abschnitt `versions` (config-version 21).
- **Server neu starten aus dem Web:** Knopf mit Countdown (`versions.restart-delays`, Standard 5 Minuten, 1 Minute, sofort), angekündigt wie `/mradmin announce restart`, abbrechbar. Standardmäßig **aus** (`versions.restart-method: none`), weil ein Server sich nicht selbst wieder starten kann. Mit `stop` stoppt der Server und ein Start-Skript mit Schleife, ein Wächter oder das Panel startet ihn wieder, mit `script` startet er über das `restart-script` der `spigot.yml` neu. Beispiel-Skripte stehen in der README. Permission `mradmin.server.restart`.
- **Discord-Log:** neue Ereignisse `plugin-update-downloaded`, `server-restart` und `server-restart-cancelled`.
- **Health-Dashboard: Diagramme im Vollbild:** Mit **⤢** öffnet sich ein Diagramm groß über der Seite (schließen mit Esc, Klick daneben oder „Schließen“).
- **Jeder Spieler in seiner Sprache:** Chat, Menüs, Dialoge, Ankündigungen, Wartungs-Countdown, Bossbars, Titel, der Kick-Bildschirm und der Staff-Chat richten sich jetzt nach der Sprache, die der Spieler in Minecraft eingestellt hat (`language: auto`, neuer Standard): Deutsch (auch Österreich/Schweiz) bekommt Deutsch, Englisch bekommt Englisch, alle anderen `fallback-language` (Standard `en`). `language: de` oder `en` stellt weiterhin eine Sprache für alle ein.
- **Server-Sprache (`server-language`):** Discord-Logs und -Ankündigungen, die Serverliste während einer Wartung (dort ist die Sprache des Spielers noch unbekannt), Push-Benachrichtigungen, Mails und Notizen in Tickets kommen in dieser Sprache (Standard `en`).
- **Automatische Umstellung:** Bestehende Configs bekommen `language: auto`; eine bisher fest eingestellte Sprache wird zur `server-language`, damit z.B. die Discord-Logs eines deutschen Servers deutsch bleiben.
- **Ankündigungen pro Spieler:** Vorlagen (z.B. `restart`) und die Standard-Überschrift eigener Texte sieht jeder in seiner Sprache; die Wartungs-Bossbars gibt es pro Spieler.

### Entfernt
- **`discord-announce.maintenance.post-start` und `post-end`:** Es gibt keine eigenen Posts für Beginn und Ende mehr - jede Wartung hat jetzt genau eine Nachricht, die mitgeführt wird (siehe unten). Die Schlüssel werden beim Update automatisch entfernt (config-version 20).

### Behoben & geändert
- **Duelle → Arenen übersichtlicher:** Jede Arena zeigt Vorrat, bereit, im Aufbau und belegt als eigene Werte; die Liste der einzelnen Plätze darunter ist entfallen.
- **Admin-Kompass: Menütitel in der falschen Sprache:** Das Admin-Menü über den Kompass im Admin-Modus hatte den Titel in der Server-Sprache statt in der Sprache des Spielers.
- **Health-Dashboard: Zeitachse passte nicht zum gewählten Zeitraum:** Die Achse reichte nur vom ersten bis zum letzten vorhandenen Wert, und 6 h / 24 h zeigten nach einem Neustart nur die Zeit seitdem. Jetzt zeigt jedes Diagramm genau den gewählten Zeitraum, und die gespeicherten 5-Minuten-Werte füllen die Zeit vor dem Neustart auf.
- **Health-Dashboard: Garbage Collection bei langen Zeiträumen:** Im Mittel über Minuten verschwanden kurze GC-Pausen, und die Achse zeigte überall „0 ms“. Jetzt gibt es eine zweite Linie „Spitze“ (höchster Wert im Zeitabschnitt), und kleine Werte haben eine Nachkommastelle.- **Spielerseite in der Weboberfläche übersichtlicher:** Oben steht eine kurze Vorschau des Spielers (Ort, Spielmodus, Ping, Leben, Spielzeit, Einfrieren, Kicken), darunter eine Kachel pro Bereich - Tickets, Schlaf, Chunkloader, Duelle, Strafen - mit dem Status auf einen Blick. Ein Klick öffnet den Bereich mit allen Einzelheiten und Aktionen (eigene Adresse `/players/<uuid>/<bereich>`).
- **Duelle auf der Spielerseite neu aufgebaut:** Statistik, die laufende Runde (Klick führt in die Duellsteuerung) und die letzten Runden sind jetzt Kacheln statt Textzeilen, jede Runde farbig nach Ergebnis. Der Bereich ist aufgebaut wie Schlaf und Chunkloader: Status oben, Aktionen in Abschnitten darunter, die Sperre mit Grund und roten Dauer-Knöpfen statt Auswahlliste (die Dauern stellt MRpvp mit `gui.ban-durations` ein).
- **Discord-Ankündigung von Wartungen neu gebaut:** Bisher blieb z.B. „Wartungsarbeiten laufen bis 18:00“ stehen, obwohl die Wartung vorzeitig beendet war, und nach einem Verbindungsabbruch zu Uptime Kuma wurde eine Wartung als „abgesagt“ bzw. „beendet“ und gleich danach erneut (mit Ping) gepostet. Jetzt gilt:
  - Jede Wartung hat **eine Nachricht**, die bearbeitet wird: geplant → läuft → „Server wieder online“ (mit tatsächlichem Beginn und Ende, bei vorzeitigem Ende mit Hinweis) oder abgesagt. Die Rolle wird nur beim ersten Post erwähnt.
  - Beginn und Ende werden sofort eingetragen, neue Zeiten nach `debounce-seconds`.
  - Nach einer (Neu-)Verbindung zu Uptime Kuma wird erst ausgewertet, wenn die Wartungsliste angekommen ist.
  - Mehrere Wartungen werden unabhängig voneinander verfolgt.
  - Wurde die Nachricht in Discord gelöscht oder ist ihre ID unbekannt, wird sie neu gepostet statt nicht mehr aktualisiert.
  - Lief eine geplante Wartung ab, während der Server aus war, wird sie als beendet eingetragen statt als abgesagt.
  - Die Texte `maintenance-started` und `maintenance-ended` im Abschnitt `discord-announce` der Sprachdateien zeigen den tatsächlichen Beginn und das Ende (neue Platzhalter `{started}` und `{ended}`, neuer Text `maintenance-ended-early`). Die beiden Texte werden in `de.yml` und `en.yml` automatisch auf den neuen Standard gesetzt - wer sie angepasst hatte, muss das danach wiederholen. Eine laufende Ankündigung aus 0.6.0 wird übernommen.
- **Update-Hinweis bei `/mradmin version` doppelt:** Fand die Abfrage eine neue Version, kam zuerst der Admin-Hinweis „Für MRadmin gibt es ein Update“ und direkt danach „Neue Version verfügbar“. Jetzt kommt nur noch die Antwort auf den Befehl. Die Download-Links führen auf die Website (https://mrtechnology.marcelr.at/download/mradmin/).
- **Config und plugin.yml auf Englisch:** Alle Kommentare in der `config.yml` und die Beschreibungen der Befehle und Permissions in der `plugin.yml` sind jetzt englisch. Bestehende Configs bekommen die neuen Kommentare beim nächsten Start automatisch (config-version 19, Sicherung als `config.v<alt>.backup.yml`), alle Einstellungen bleiben erhalten.

## [0.6.0] - 2026-10-27

### Geändert – Lizenz, Doku, Website
- **MRtechnology Lizenz 1.0:** Eine einheitliche Lizenz für alle MRtechnology-Projekte. Ausdrücklich erlaubt ist jetzt auch die gewerbliche Nutzung, Weiterveröffentlichen und Verkaufen bleiben verboten, die Nutzung erfolgt auf eigenes Risiko.
- **README überarbeitet:** Schnellstart, Auf einen Blick, Häufige Fragen, Kurzlinks zur Website (Download, Anleitung, Fehler melden).
- **Links zur Website** [mrtechnology.marcelr.at](https://mrtechnology.marcelr.at): Update-Hinweise in Konsole und Spiel führen zur Download-Seite, `/mradmin version` zeigt „Website | GitHub“ (statt des Modrinth-Links), `website` in der plugin.yml zeigt auf die Anleitung. Der Text `version-links` wird in den mitgelieferten Sprachdateien automatisch erneuert.

### Neu – Modul-API
- **MRadmin verbindet die MR-Plugins untereinander:** Sie kennen sich nicht mehr gegenseitig, sondern fragen über
  MRadmin: Spieler beschäftigt (`busy`, z.B. im Duell), Welt gehört einem Plugin (`worldOwner`, Arena-Welt) und
  Gegenstände außerhalb des Inventars (`storedItems`, MRtoolbars Gürtel). Ohne MRadmin laufen alle weiter, nur diese
  Verbindungen fehlen. Übersicht in der README.
- **Hinweise auf der Startseite kommen von den Plugins** (`WebModule.alerts`): MRadmin kennt keine
  Chunkloader- oder Schlaf-Hinweise mehr, die Plugins liefern Text und Seite selbst.
- **Einheitliche Schnittstelle für alle Plugins** (`api/`, Doku in `docs/MODULE-API.md`): Plugins melden sich als
  Modul an (`MRadminService.register`) und beschreiben selbst Dashboard-Zeilen, Spielermenü, Serverzustand, Tickets
  (`report`/`resolve`) und ihre Web-Daten und -Aktionen samt Permissions. MRadmin enthält dafür keinen
  plugin-spezifischen Code mehr. Veröffentlicht über JitPack (`com.github.therealmarci:mradmin:api-1.0.0`).
- **Web:** Alle Plugin-Seiten laufen über `/api/modules/<id>/…` und `/api/players/<uuid>/modules/<id>`; die Rechte im
  Browser (`may.mrchunksManage` …) kommen aus den Modulen. Die alten Adressen `/api/mrchunks`, `/api/mrsleep`,
  `/api/mrpvp` gibt es nicht mehr. Aktionen heißen im Web-Protokoll jetzt `<modul>-<aktion>` (z.B. `mrchunks-toggle`).
- **Tickets aus Plugins** sind allgemein (nicht mehr nur Chunkloader-Sperren): Betreff und Text kommen vom Plugin,
  Web-Push „Neues Ticket: …“ verlinkt das Ticket.
- **Ältere Plugin-Versionen** (MRsleep, MRchunks, MRpvp, MRtoolbar von vor der Modul-API) verbinden sich nicht mehr –
  beim Start steht dazu eine Warnung in der Konsole. Alle vier also zusammen mit diesem MRadmin aktualisieren.

### Neu
- **MRchunks (Schnittstelle 5): Dummy-Spieler und verknüpfte Accounts in der Weboberfläche.** Chunkloader zeigen, ob
  ein Dummy auf ihnen sitzt (Liste und Detailseite, Knopf „Dummy ein-/ausschalten“), die Übersicht zählt Dummies und
  verknüpfte Accounts. Auf der Spielerseite: Dummy-Limit, verknüpfte Haupt-/Zweit-Accounts und „Verknüpfung lösen“
  (Permission `mrchunks.admin.manage`). Mit älteren MRchunks-Versionen erscheint davon einfach nichts.
- **NPCs sind keine Spieler:** MRchunks-Dummies und andere NPCs (Metadata `NPC`, z.B. Citizens) zählen nicht zur
  Spielerzahl, erscheinen nicht in Spielerlisten, Teleport- und Ticket-Menüs oder der Tab-Vervollständigung, verfälschen
  den Ping-Schnitt im Serverzustand nicht und werden im Wartungsmodus nicht gekickt.

### Behoben
- Menüs mit Spielerköpfen fragten jede Sekunde erneut bei Mojang nach dem Skin (HTTP 429 im Log, besonders im
  Offline-Mode). Köpfe werden jetzt einmal nachgeschlagen und 10 Minuten zwischengespeichert.

## [0.5.0] - 2026-10-23

### Neu
- **MRpvp** als Integration (`integrations.mrpvp`): Knopf im Dashboard, Eintrag im Spieler-Menü, und im Web eine
  eigene Seite **Duelle**: laufende Duelle live (Leben beider, Zeit; Abbrechen), alle Runden mit Filter, Detailseite
  pro Game-ID mit den gesicherten Inventaren (beim Betreten und am Ende, Enderchest, MRtoolbar-Gürtel), Kampf-Log und
  einer **Mini-Karte** mit Wiedergabe der Positionen, Arenen und Plätze, Auffälligkeiten der letzten 24 Stunden und
  **Prüfen**: auffällige Runden, die auf eine Entscheidung warten, werten oder verwerfen (auch auf der Detailseite;
  in der Server-Übersicht im Spiel stehen sie ganz oben).
  Live-Ansicht mit Herzen wie im Spiel; auf der Mini-Karte leuchtet der Punkt auf, wenn jemand trifft oder getroffen wird.
  Auf der Spielerseite: Duell-Statistik, letzte Runden, Duell-Sperre, Statistik zurücksetzen, offene Rückgaben.
  Markierungen „im Duell“ / „von Duellen gesperrt“ in der Spielerliste. Rechte: die von MRpvp
  (`mrpvp.admin.gui`, `.games`, `.stop`, `.ban`, `.reset`, `.restore`, `.review`).
- `MRadminApi` Version 2: `webEvent` (Live-Updates anderer Plugins für offene Seiten, ohne Inhalt) und `adminLog`
  (Zeile im Discord-Admin-Log, Ereignis `plugin-alert`). config-version 18.

### Behoben
- Im Admin-Modus ließ sich das Kreativ-Inventar komplett leeren (Shift-Klick auf den Mülleimer). Das wird jetzt
  erkannt und sofort rückgängig gemacht.
- Web: Aktionen (Speichern, Abbrechen, Werten …) schlugen mit „falsche Adresse“ fehl, wenn die Seite über `localhost`
  geöffnet war, `web.public-url` aber `127.0.0.1` sagt (oder umgekehrt). Lokale Schreibweisen derselben Adresse gelten
  jetzt als gleich; die Fehlermeldung sagt, über welche Adresse man die Seite öffnen muss.

## [0.4.0] - 2026-10-21

### Neu
- **MRtoolbar** als Integration (wie MRsleep und MRchunks): Knopf im Dashboard und im Spieler-Menü ein Eintrag mit
  Mod-Version und Tool-Slots; ein Klick zeigt die Gürtel des Spielers (nur ansehen, wie Inventar und Enderchest).
  Abschaltbar mit `integrations.mrtoolbar`.

### Neu für Scripts
- `/mradmin announce` lässt sich frei zusammensetzen: `title=`, `subtitle=`, `chat=`, `actionbar=`, `discord=`, `sound=`,
  `stay=`/`fade-in=`/`fade-out=`, `to=` (alle oder eine Permission) und `id=` (Discord-Sperrzeit) - jeder Teil optional,
  Werte mit Leerzeichen in Anführungszeichen. Eigene Presets können benannte Werte nutzen (`grund="Backup"` → `<grund>`).
  Gedacht für Server-Scripts, z.B. Countdowns bei Neustart und Stop.

### Behoben
- Ein geschlossenes Ticket ließ sich für Spieler nicht mehr öffnen ([Öffnen] im Chat bzw. `/report #<id>` brach mit einem
  Fehler ab). Es wird jetzt nur zum Lesen angezeigt - mit „Zurück“ als einzigem Knopf.
- Update-Hinweis erschien doppelt im Chat, wenn man kurz nach einem Serverstart joint (Hinweis beim Betreten und
  erste Update-Prüfung). Jetzt höchstens einmal pro Minute.

## [0.3.1] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.3.1`).

### Behoben
- **Impressum/Datenschutz im Web leer**, wenn `impressum.md`/`datenschutz.md` mit Umlauten als ANSI (Windows-1252)
  gespeichert waren. Jetzt werden UTF-8 (mit/ohne BOM) und ANSI gelesen.
- **Discord-Ankündigung einer Wartung wurde nach Neustarts nicht mehr aktualisiert:** Die Nachrichten-ID wurde beim
  Herunterfahren gespeichert, bevor laufende Posts fertig waren, und sonst nur 3 Sekunden nach dem Posten. Jetzt wird
  sie gespeichert, sobald Discord sie liefert, und beim Stoppen erst nach den letzten Posts. Fehlt die ID, steht
  das jetzt in der Konsole.

## [0.3.0] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.3.0`).

### Neu
- **Schnittstelle für den Admin-Modus:** `at.marcelr.mradmin.api.MRadminApi.adminMode(UUID)` (per Reflection, keine
  Abhängigkeit nötig). MRsleep nimmt Spieler im Admin-Modus damit von der Schlafquote aus.
- `web.push.contact`: Kontakt für die Push-Dienste (`mailto:...` oder `https://...`).
- **Entbannungsanträge** unter `/entbannung` (mit LibertyBans): persönlicher Code in der Ban-Meldung als Nachweis,
  Bangrund und Stellungnahme, ein Antrag pro Ban, einmal Einspruch. Im Ticket: **Entbannen** (hebt den Ban auf) oder
  **Ablehnen**. Auch ingame sichtbar.
- **Kontaktformular** unter `/kontakt`, verlinkt unter dem Impressum. Nur im Web, nie ingame.
- Privater **Statuslink** für beide (`/anfrage/...`): Stand, Verlauf, antworten, Einspruch.
- **Mailversand (SMTP)**, optional: Bestätigung, Antworten, Entscheidung. `/mradmin mail test <adresse>`.
- Spamschutz ohne externe Dienste: Lockfeld, Mindestzeit, Proof of Work im Browser, IP-Limits.
- Datenbank-Schema 2 (Spalte `extra` an den Tickets) - wird beim Start automatisch ergänzt.

### Behoben
- **Push-Benachrichtigungen auf iPhone, iPad und Safari:** Ohne https-`public-url` schickte MRadmin
  `mailto:mradmin@localhost` als Kontakt mit - Apple lehnt das ab (403 BadJwtToken), Google und Mozilla nicht. Jetzt
  gilt `web.push.contact` bzw. die `public-url`, und die Konsole warnt, wenn Apple-Geräte nichts bekommen würden.
- Abgelehnte Push-Nachrichten werden mit der Antwort des Push-Dienstes geloggt (höchstens alle 10 Minuten pro Dienst),
  und der Test-Button zeigt pro Gerät, ob und warum der Push-Dienst abgelehnt hat.
- iPads wurden nicht als iOS erkannt und bekamen „nicht unterstützt“ statt des Hinweises zum Home-Bildschirm.

## [0.2.0] - 2026-10-20
Für Minecraft **26.3** (`mc26.3-v0.2.0`).

### Neu
- **Mit Discord anmelden** (`web.login.discord`, OAuth2, nur `identify`): Discord im Konto verknüpfen, Anmeldung per
  Klick, zählt wie ein Passkey - 2FA abschaltbar, Bestätigungen per Discord. Config-Version 15.
- **Push-Benachrichtigungen** (Web-Push, VAPID, ohne Drittanbieter): neue Tickets, Nachrichten in eigenen/allen Tickets,
  gesperrte Chunkloader, Lag-Alarm, Wartungen - jeder wählt selbst, nur mit den passenden Rechten; pro Gerät, an die
  Sitzung gebunden. Verschlüsselung nach RFC 8291 (gegen das Beispiel aus dem RFC getestet).
- **Konto neu gestaltet:** Bereiche Sicherheit, Benachrichtigungen und Geräte, Schutz-Einschätzung im Kopf.
- **Wartungen in Uptime Kuma verwalten:** Ist Uptime Kuma eingerichtet, legt die Wartungsseite Wartungen direkt in Kuma
  an (an die `monitor-ids` gehängt), verlängert, pausiert, setzt fort und löscht sie - ohne Kuma nutzt sie die eigene
  Wartung von MRadmin. Kuma-Version wird angezeigt. Getestet mit Uptime Kuma 2.5.5.
- **Wartung & Ankündigungen im Web**: Wartungen (manuell und Uptime Kuma) ansehen, starten, planen, verlängern,
  beenden; Ankündigungen mit Vorlagen oder eigenem Text, optional in den Community-Discord - mit denselben
  Permissions wie die Befehle. Dashboard-Kachel und Meldungen verlinken dorthin.
- **Passkeys** (WebAuthn): unter Konto hinzufügen (mit frischem 2FA-Code), Anmeldung ohne Name, Passwort und Code;
  Gerätecheck immer Pflicht, gebunden an `web.public-url`. Zurücksetzen per Einrichtungslink entfernt sie. Die
  Bibliothek (Yubico WebAuthn 2.10.0) steckt mit festen Versionen in der JAR, damit Paper beim Start nichts
  nachladen muss.
- **2FA abschaltbar mit Passkey:** Mit mindestens einem Passkey lässt sich 2FA ausschalten - dann nur noch Anmeldung
  per Passkey (Name + Passwort verlangen immer 2FA). Bestätigungen (Konsole, Passkey hinzufügen) gehen dann per
  Passkey; der letzte Passkey bleibt geschützt; 2FA lässt sich wieder einschalten.
- **Web-Konsole** (`mradmin.web.console.view` / `.execute`, in keinem Paket): Live-Log mit Filter, Befehle als Konsole.
  Entsperren nur mit frischem 2FA-Code (gilt `web.console.unlock-minutes`), Sperrliste `web.console.blocked` (auch für
  `execute ... run` und Namespaces), Links und IPs ausgeblendet, jeder Befehl im Web-Protokoll und Discord-Log
  (`web-console`). Config-Version 14.
- **Spielerverwaltung im Web** (`mradmin.player.view`): alle Spieler, die je auf dem Server waren, mit Suche, Filter
  und Markierungen; pro Spieler eine Seite mit Infos, Tickets, Einfrieren und Kicken (neu: `mradmin.player.kick`,
  Discord-Log `player-kick`) und einem Teil pro Plugin: MRsleep (/nosleep, Cooldown, Bypass, Sperre) und MRchunks
  (Loader, Limits setzen, für Chunkloader sperren, alle löschen) - jeweils mit dessen Permissions.
- **LibertyBans auf der Spielerseite** (optional, `integrations.libertybans`): aktive Strafen, Verlauf, bannen, muten,
  verwarnen, kicken und aufheben - über die API von LibertyBans und mit dessen Permissions, auch den
  Dauer-Permissions. Schnellauswahl der Dauer: `libertybans.durations`. Markierungen „gebannt“ und „gemutet“ in der
  Spielerliste. Getestet mit LibertyBans 1.2.0-M1 (API 1.1.4).
- **Schlaf (MRsleep) im Web**: Welten mit Schlafstatus und /nosleep-Blockaden, Blockaden aufheben. Auf dem Dashboard
  als Kachel und als Meldung bei Blockaden.
- Am Handy zeigt die untere Leiste höchstens fünf Plätze, der Rest steckt unter **Mehr**.
- **Dashboard neu:** Die Kachel **Lage** zeigt sofort, was aus dem Ruder läuft (TPS, Tick-Zeit, RAM, Speicherplatz,
  CPU, Ping, Tickets, Chunkloader, Wartung), sonst die wichtigsten aktuellen Werte. Dazu Kacheln für Server,
  Spieler, Tickets, Chunkloader und Wartung, alle 2 Sekunden aktuell. Jeder Teil nur mit der passenden Permission
  und nur, wenn das Plugin bzw. Feature da ist.
- **Tickets:** neuer Filter **Mir zugewiesen** (offene Tickets, die du übernommen hast; mit `mradmin.tickets.claim`).
- **Health:** Kacheln für Ø Ping, Servergröße und Datenbankgröße, Ping-Verlauf, im Chunk-Verlauf (mit MRchunks)
  grün „davon Chunkloader“. Lag-Quellen auf die Top 5 beschränkt.
- **Konto** nicht mehr in der Navigation, sondern per Klick auf den eigenen Kopf bzw. Namen.
- **Chunkloader im Web** (mit MRchunks ab Schnittstelle 4): Liste mit Status, Suche, Details mit allen Messwerten und
  Mitgliedern, ein-/ausschalten, Sperre aufheben, löschen. Es gelten die Permissions von MRchunks.
- **Health-Dashboard im Web** (`mradmin.status.health`): TPS, MSPT (Ø, 95 %, Spitze), CPU, RAM, GC, Spieler,
  Chunks, Entities, jede Sekunde gemessen und live, als Verlauf (1 Min bis 30 Tage), Welten, Systeminfos, Lag-Quellen. Verlauf `health.history`
  (24h/7d/30d), Warnung bei anhaltend niedriger TPS (`health.alert`, Discord-Events `health-alert`,
  `health-recovered`). Config-Version 11.
- **Impressum & Datenschutz in der Weboberfläche:** Fußzeile auf jeder Seite, Vorlagen in
  `plugins/MRadmin/web/` (oder Links auf eigene Seiten, `web.legal`). Spielerköpfe erzeugt MRadmin selbst (kein
  Drittanbieter mehr), Protokoll wird nach `web.privacy.audit-retention` gelöscht, optional gekürzte IPs
  (`web.privacy.mask-ip`). Config-Version 10.
- **Eigene Permission für jede Funktion**, ingame und im Web gleich, z.B. Tickets getrennt nach ansehen,
  antworten, übernehmen, schließen, wieder öffnen und teleportieren; Dashboard, Wartung ansehen/steuern, Spieler
  ansehen/teleportieren/kicken, Speicher, Web-Protokoll, Web-Konsole. Pakete `mradmin.support`, `mradmin.staff`
  und `mradmin.admin`. Menüs und Web zeigen nur, was man darf.
  **Die bisherigen Permissions entfallen** (`mradmin.report`, `mradmin.report.notify`, `mradmin.report.manage`,
  `mradmin.mode.teleport`) - bitte in LuckPerms durch die neuen ersetzen, z.B. `mradmin.report.manage` durch
  `mradmin.support`.
- **Weboberfläche (Vorschau):** eingebauter Webserver hinter deinem Reverse Proxy, modernes Frontend (Svelte) für
  Handy, Tablet und PC. Konto per Einrichtungslink (`/mradmin web setup`, `/mradmin web invite`), Anmeldung mit
  Passwort und Authenticator-App, Wiederherstellungscodes, Geräteverwaltung, Protokoll aller Web-Aktionen.
  Übersicht (TPS, MSPT, RAM, Spieler, Wartung, Tickets) und Tickets (Suche, Filter, Chat-Ansicht mit Antworten,
  Übernehmen, Schließen, Wieder öffnen), Änderungen erscheinen live (Server-Sent Events).
  Abschaltbar und standardmäßig aus (`web.enabled`). Neue Permissions `mradmin.web`, `mradmin.web.manage`,
  Discord-Events `web-login`, `web-setup` (Config-Version 9).
- **Speicher wählbar:** `storage.type` = `yaml` (Standard, wie bisher), `sqlite` (eine Datei `mradmin.db`) oder
  `mysql` (MySQL/MariaDB). Beim Umstellen werden die YAML-Daten automatisch übernommen. Gespeichert wird immer im
  Hintergrund, bei SQLite/MySQL pro Ticket bzw. Nachricht statt der ganzen Datei. Neuer Befehl `/mradmin storage`.
  Die Treiber lädt Paper beim ersten Start herunter (Config-Version 8).
- **Alle Funktionen abschaltbar** unter `features` in der Config: Wartung, Ankündigungen, Admin-Modus, Teleport,
  Herholen, Inventar ansehen, Einfrieren, Serverzustand, Tickets und Staff-Chat. Eine ausgeschaltete Funktion
  verschwindet aus Befehlen, Hilfe, Tab-Vervollständigung und allen Menüs.
- **MRsleep und MRchunks werden automatisch erkannt**, auch wenn sie zur Laufzeit geladen oder entfernt werden.
  Die Menüs passen sich von selbst an. Abschaltbar unter `integrations`.
- **Ticket bei Chunkloader-Sperre (MRchunks):** Sperrt MRchunks einen Loader wegen Lag, bekommt der Owner
  automatisch ein Ticket (Kategorie „Chunkloader-Sperre“) mit allen Details und einen Link im Chat, um sich zu
  erklären. Nur das Team kann es schließen. Im Ticket öffnet **Chunkloader-Menü** das Menü des
  Loaders (Teleport, an/aus, Lag-Sperre aufheben). Erneute Sperren und das Entsperren werden im selben Ticket vermerkt. Abschaltbar mit
  `tickets.chunkloader-locks: false`. Solche Tickets zählen nicht zu `max-open-per-player`.

### Entfernt
- Seite **Protokoll** in der Weboberfläche (protokolliert wird weiterhin, inkl. Löschfrist).

### Geändert
- MRsleep und MRchunks sind keine Einträge mehr unter `admin-mode.menu.buttons`, sondern fest eingebaut. Die alten
  Einträge werden beim Update entfernt, ein abgeschalteter Button wird zu `integrations.<plugin>: false`
  (Config-Version 7).
- **Reports sind jetzt ein Ticket-System.** `/report` öffnet ein Menü mit „Ticket erstellen“, den offenen Tickets
  und einem Archiv der geschlossenen. Erstellt wird über eine Kategorie-Auswahl und ein Formular (Minecraft-Dialoge),
  im Ticket schreiben Spieler und Team hin und her wie in einem Chat. Kategorien sind frei in der Config
  (`tickets.categories`) anlegbar, auch Spieler-Reports und mit eigener Permission. Das Team kann Tickets
  übernehmen, beantworten, teleportieren, schließen und wieder öffnen (`/mradmin tickets`, Dashboard-Kachel
  **Tickets**). Neue Antworten werden auch nach dem nächsten Join gemeldet.
- Config-Abschnitt `reports` heißt jetzt `tickets` (mit neuen Einstellungen, Werte werden übernommen),
  Discord-Events `report`/`report-closed` heißen `ticket`/`ticket-closed`, neu sind `ticket-message` und
  `ticket-reopened`. Gelöschte Standard-Einträge in eigenen Abschnitten (Kategorien, Menü-Buttons)
  kommen beim Laden nicht mehr zurück.
- `reports.yml` wird automatisch nach `tickets.yml` übernommen.

## [0.1.0] - 2026-10-19
Erste Testversion für Minecraft **26.3** (`mc26.3-v0.1.0`).

### Neu
- **Wartungsmodus:** Während einer Wartung können nur Spieler mit `mradmin.maintenance.bypass` joinen
  (mit LuckPerms schon vor dem Join geprüft), alle anderen werden beim Start gekickt. Serverliste mit eigener MOTD.
- **Uptime-Kuma-Anbindung** (Socket.IO, wie die Weboberfläche): geplante Wartungen lösen 15 Minuten vorher eine
  Bossbar mit Countdown aus, dazu Chat-Erinnerungen und ein großer Countdown in den letzten Sekunden. Verlängern,
  Verschieben, Pausieren und Löschen in Kuma werden sofort übernommen. Filter nach Monitor- oder Wartungs-IDs,
  Login per Passwort oder Token (für 2FA).
- `/mradmin maintenance on|schedule|extend|off|refresh` für Wartungen ohne Kuma; Zustand übersteht Neustarts.
- **Ankündigungen:** `/mradmin announce <text|preset> [werte]` als Titel auf dem Bildschirm, mit Chat und Ton.
  Presets (restart, restart-now, update, event) in der Sprachdatei, auch von Konsole/RCON nutzbar.
- **Admin-Modus:** `/mradmin mode on|off` speichert Inventar, Rüstung, XP, Leben, Hunger, Effekte und Position,
  setzt in den Creative-Modus und gibt einen Admin-Kompass. Sein Menü bündelt Spieler-Teleport,
  die Admin-Menüs von MRsleep und MRchunks (eigene Plugin-Buttons per Config möglich), den Wartungsstatus und
  „Admin-Modus beenden“. Beim Beenden wird alles
  exakt wiederhergestellt. Der Stand liegt sicher auf der Platte und übersteht Abstürze.
- **Admin-Dashboard** (`/mradmin` oder Admin-Kompass): Live-Kacheln für Spieler, Server, Wartung und Reports mit
  farbiger Statusleiste, Ankündigungs-Presets per Klick (mit Eingabe-Dialog), Plugin-Buttons, Schalter für
  Admin-Modus und Staff-Chat.
- **Spieler-Menü** (Kompass oder `/mradmin player`): Infos, hinteleportieren, holen, Inventar/Enderchest ansehen (nur
  lesend), einfrieren, Reports und die Daten aus MRsleep/MRchunks mit Knopf zu deren Spieler-Menü.
- **Serverzustand** (`/mradmin status`): TPS, MSPT, RAM, Welten und die Chunks mit den meisten Entities, mit MRchunks
  stattdessen die auffälligen und gesperrten Chunkloader.
- **Reports** (`/report`, `/mradmin reports`) mit Team-Meldung, Teleport-Knopf, Discord-Log und Cooldown.
- **Staff-Chat** (`/sc`), umschaltbar, optional im Admin-Discord.
- **Discord-Ankündigungen** über einen eigenen Webhook für den Community-Channel: pro Wartung höchstens drei Posts
  (geplant, gestartet, beendet). Änderungen und Absagen bearbeiten den letzten Post. Ankündigungen nur für Presets
  mit `discord`-Text bzw. mit `-d`, mit Cooldown pro Preset. Optionaler Rollen-Ping beim ersten Post.
- Discord-Logs, Update-Check, Sprachdateien Deutsch/Englisch, eine Permission für jede Funktion.
- Keine externen Abhängigkeiten: der Socket.IO-Client steckt in der JAR, auf dem Server wird nichts nachgeladen
  (LuckPerms ist optional).
