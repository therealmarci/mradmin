# MRadmin - Weboberfläche

MRadmin vom Handy, Tablet oder PC bedienen: Tickets beantworten, Spieler verwalten, Wartungen planen, den Serverzustand im Blick behalten - mit denselben Rechten wie im Spiel. Diese Seite beschreibt die Weboberfläche im Detail. Was Spieler auf ihrer eigenen Seite sehen, steht unter [Spielerseite](wiki/spielerseite.md), Updates und Neustart unter [Plugins & Updates](wiki/plugins.md).

## Aufbau
Seit 1.5 gibt es **eine** Weboberfläche für Spieler und Team. Wer im Team ist, wechselt oben rechts mit **Spieler | Verwaltung** (auf dem Handy im Menü unter dem Namen). Jeder Bereich hat oben Reiter.

| Spielerbereich | Inhalt |
|---|---|
| **Start** (`/`) | Startseite aus Widgets - eine eigene für Gäste und eine für angemeldete Spieler |
| **Mein Spiel** (`/game/…`) | ein Reiter pro Plugin (Chunkloader, Duelle, Wegpunkte …) |
| **Karte** (`/map`) | die Karte im Vollbild |
| **Statistik** (`/stats`) | eigene Werte und Server-Ranglisten |
| **Posteingang** (`/inbox`, Brief oben rechts) | Neuigkeiten, Changelogs, Warnungen und die eigenen Tickets in einer Liste; die Zahl am Brief zeigt, was seit dem letzten Besuch neu ist (neue Beiträge, Antworten des Teams). Reiter **Alles**, **Neuigkeiten** (`/inbox/news`), **Tickets** (`/inbox/tickets`) |
| **Konto** (`/account`, Menü unter dem Namen) | Anmeldungen, Geräte, fürs Team Benachrichtigungen |

| Verwaltung | Reiter |
|---|---|
| **Übersicht** (`/admin`) | Dashboard |
| **Community** (`/admin/community/…`) | Spieler · Tickets · Strafen · Beiträge · Ankündigungen |
| **Funktionen** (`/admin/features/…`) | ein Reiter pro Plugin: Chunkloader · Duelle · Schlaf · Karte |
| **Server** (`/admin/server/…`) | Steuerung · Leistung · Wartung · Plugins · Protokoll · Konsole · Aussehen (MRadmins Konfiguration: Plugins → MRadmin) |

Jeder sieht nur, wofür er die Rechte hat. Alte Adressen (`/me/…`, `/tickets`, `/chunks`, `/server/plugins` …) leiten auf die neuen weiter - Lesezeichen und alte Links funktionieren also. Wer zuletzt in der Verwaltung war, landet nach dem Anmelden wieder dort.

## Einrichten
1. In der Config `web.enabled: true` und `web.public-url` auf deine Adresse setzen (z.B. `https://admin.deinserver.de`), dann `/mradmin reload`. Für viele Tickets empfiehlt sich `storage.type: sqlite`.
2. **Reverse Proxy** einrichten: Domain mit HTTPS → `http://127.0.0.1:8125`. Der eingebaute Webserver lauscht nur lokal und hat selbst kein HTTPS.
3. **Anmelden:** Jeder kommt mit dem Code aus `/web` im Spiel rein - das Konto entsteht dabei von selbst. Wer im Team ist (Permission `mradmin.web`), richtet danach unter **Konto → Anmeldungen** eine eigene Anmeldung ein: Passwort, Passkey oder Discord. Erst dann öffnet sich die Verwaltung.

| Befehl | Was er tut | Permission |
|---|---|---|
| `/web` | Code für die Anmeldung (für Spieler und Team) | `mradmin.web.self` oder `mradmin.web` |
| `/mradmin web` | Status (läuft, Adresse, Konten mit eigener Anmeldung, Geräte) | `mradmin.web` |
| `/mradmin web sessions [spieler]` | Angemeldete Geräte | `mradmin.web` (andere: `.manage`) |
| `/mradmin web logout [spieler]` | Alle Geräte abmelden | `mradmin.web` (andere: `.manage`) |
| `/mradmin web reset <spieler>` | Eigene Anmeldungen entfernen (Passwort, 2FA, Passkeys, Discord, E-Mail) und alle Geräte abmelden - z.B. verlorenes Handy | `mradmin.web.manage` |
| `/mradmin web remove <spieler>` | Konto löschen | `mradmin.web.manage` |

**Passwort vergessen?** Mit bestätigter E-Mail-Adresse kommt über „Passwort vergessen?“ ein Link per Mail (braucht `mail.*`). Und `/web` im Spiel geht immer.

## Anmeldung und Sicherheit
- **Eine Anmeldung für alle** (seit 1.5): Spieler und Team nutzen dasselbe Konto (eines pro Minecraft-UUID). Rein kommt jeder mit dem 6-stelligen Code aus `/web` (2 Minuten gültig). Ein Gerät bleibt `player-web.session-days` angemeldet (Standard 14 Tage), ohne Nutzung höchstens `player-web.idle-days` (7 Tage).
- **Eigene Anmeldung** unter **Konto → Anmeldungen** (Spielerseite: **Anmeldungen & Geräte**), damit es auch ohne Spiel geht:
  - **Passwort** - Anmeldung mit Spielername oder bestätigter **E-Mail-Adresse**. Die Adresse gilt erst nach dem Klick auf den Link in der Bestätigungsmail; sie ermöglicht „Passwort vergessen“. Passwörter werden mit PBKDF2 (600.000 Runden) gespeichert.
  - **Passkey** (Fingerabdruck, Face ID, Windows Hello, Passwort-Manager) - an die Adresse aus `web.public-url` gebunden (ohne sie nur `http://localhost` zum Testen). Gespeichert werden nur öffentliche Schlüssel. Abschalten: `web.login.passkeys: false`.
  - **Discord** (`web.login.discord`): Discord-Anwendung anlegen, Weiterleitung `<public-url>/api/discord/callback` eintragen, Client ID und Secret in die Config; abgefragt wird nur `identify`.
  - **2FA** (Code aus einer Authenticator-App) ist freiwillig: Ist sie an, fragt die Anmeldung mit Passwort zusätzlich nach dem Code (oder einem Wiederherstellungscode).
- Die **erste** eigene Anmeldung richtet man einfach so ein. Jede weitere - und Ändern oder Entfernen - bestätigt man mit einer vorhandenen (Passwort, 2FA-Code oder Passkey).
- **Team:** Die Verwaltung öffnet sich mit `mradmin.web` **und** mindestens einer eigenen Anmeldung. `web.login.require-2fa: true` verlangt zusätzlich 2FA (Standard aus). Eine `/web`-Anmeldung reicht, sobald die eigene Anmeldung eingerichtet ist. Ohne sie zeigt die Seite direkt die Karte **Anmeldungen** zum Einrichten.
- **Heikle Schritte** (Web-Konsole entsperren, Notfall-Neustart) bestätigt man mit einer eigenen Anmeldung: 2FA-Code, Passkey, Discord oder Passwort.
- Nach 5 Fehlversuchen ist das Konto 15 Minuten gesperrt, dazu höchstens 10 Anmeldeversuche pro Minute und IP. Damit das mit der echten IP der Besucher funktioniert, trägst du deinen Proxy unter `web.trusted-proxies` ein.
- Cookies: `HttpOnly`, `SameSite=Strict`, über HTTPS `Secure`; Sitzungs-Cookies, Wiederherstellungscodes und Mail-Links nur als Hash. Änderungen nur mit eigenem Header und passender Herkunft (kein Auslösen über fremde Seiten), strenge Sicherheits-Header.
- Im Web gelten dieselben Rechte wie ingame. Mit LuckPerms auch für Offline-Spieler, ohne LuckPerms über `web.roles` und `web.players`. Wer `mradmin.web` verliert, sieht die Verwaltung sofort nicht mehr (bleibt aber als Spieler angemeldet).
- Jede Anmeldung und Aktion landet im Protokoll (Web: **Protokoll**, mit `mradmin.web.audit`), Anmeldungen auch im Discord-Log (`web-login`).
- **Nach dem Update auf 1.5:** Bestehende Team-Konten bleiben mit Passwort, 2FA, Passkeys und Discord erhalten, angemeldete Geräte bleiben angemeldet. Einrichtungslinks (`/mradmin web setup`, `invite`) gibt es nicht mehr.

## Aussehen (Server, `mradmin.appearance`)
Die Weboberfläche trägt den Namen und die Farben deines Servers - für Spieler taucht „MRadmin“ nirgends auf.
- **Name** und **Spruch** (Anmeldeseite), **Akzentfarbe** (Knöpfe, Hervorhebungen; die Schriftfarbe darauf wählt die Seite selbst), **Text und bis zu 4 Links** in der Fußzeile (z.B. Discord, Website, Regeln).
- **Logo** (Anmeldeseite, Mails) und **Browser-Symbol** (Tab, Leiste, „Zum Startbildschirm hinzufügen“): PNG, WebP oder SVG, höchstens 512 KB. Ohne eigenes Symbol gilt das Logo. SVGs mit Skripten oder eingebetteten Inhalten werden abgelehnt.
- Rechts eine **Vorschau** der Anmelde- und Startseite, hell und dunkel. Nach dem Speichern gilt alles sofort für alle.
- Der Name erscheint im Browser-Tab, auf der Anmeldeseite, in der Leiste, in den Mails, in der 2FA-App und bei Passkeys.
- Gespeichert wird in der `config.yml` (Abschnitt `branding`, auch per Datei änderbar), die Bilder unter `plugins/MRadmin/branding/`.
- **Mails** (Bestätigung, Passwort vergessen, Entbannung, Kontakt, Testmail) kommen im selben Aussehen: Kopf mit Logo und Name in der Akzentfarbe, Links als Knopf, Fußzeile mit Text, Links, Impressum und Datenschutz - dazu immer eine reine Textfassung. Damit das Logo in Mails erscheint, braucht es `web.public-url`.

### Startseite (Widgets)
Es gibt **zwei Startseiten** (`/`): eine für **Gäste** (nicht angemeldet) und eine für **angemeldete Spieler**. Beide bestehen aus **Widgets** in einem Raster mit 12 Spalten; jedes Widget hat eine Breite (1-12 Spalten) und eine Höhe (1-8 Reihen). Persönliche Widgets gibt es nur auf der Seite der Angemeldeten, die Log-In-Aufforderung nur auf der Seite der Gäste. Auf dem Tablet werden kleine Widgets halb breit und größere voll breit, auf dem Handy liegen alle untereinander.

**Bearbeiten** direkt auf der Startseite (`mradmin.appearance`, Knopf **Bearbeiten** oben; oben umschalten zwischen **Gäste** und **Angemeldete**, gespeichert werden beide zusammen):
- **Verschieben:** am Griff ⠿ ziehen und auf ein anderes Widget fallen lassen (linke Hälfte = davor, rechte = danach).
- **Größe:** am Griff unten rechts ziehen oder Breite/Höhe in den Einstellungen eingeben.
- **Einfügen:** **+ Widget einfügen** (kommt nach dem gewählten Widget), **Einstellungen** ⚙, **Entfernen** ✕.
- **Speichern** oder **Verwerfen**; **Vorlage laden** ersetzt die gerade gewählte Seite durch die Vorlage (erst beim Speichern).

| Gruppe | Widgets |
|---|---|
| Server & Status | **Banner** (Logo, Name, Spruch, Hintergrundbild, Adresse, Spieler online - angemeldet mit „Hallo <Name>“, für Gäste mit Log In), **Hinweise** (Wartung, Neustart, Warnungen, Beiträge - angemeldet persönlich), **Server-Adresse**, **Spieler online** |
| Persönlich (nur angemeldet) | **Begrüßung**, **Meine Bereiche** (eine Kachel pro Bereich der Plugins), **Meine Statistik** |
| Inhalte | **Neuigkeiten**, **Text** (mit Formatierung), **Bild** (PNG, WebP, SVG bis 512 KB), **Links**, **Discord** (Einladung), **Log-In-Aufforderung** |

Widgets ohne Inhalt (leerer Text, kein Bild, keine Links) sind außerhalb des Bearbeitens unsichtbar. **Log In** steht oben rechts in der Leiste und öffnet ein Fenster über der Seite (direkt: `/login`). Die bisherige Startseite wird beim Update automatisch auf beide Seiten aufgeteilt (für alle → beide, nur Gäste → Gäste, nur Angemeldete → Angemeldete). Gespeichert wird im Speicher von MRadmin (`landing`), die Bilder unter `plugins/MRadmin/branding/landing/`. Name, Logo, Farbe und Fußzeile stellst du unter Server → Aussehen ein.

## Strafen (Community, mit LibertyBans)
- Alle Strafen aller Spieler auf einen Blick, neueste zuerst (höchstens 300): **Aktiv** zeigt, was gerade gilt - wie `/banlist` und `/mutelist` von LibertyBans -, **Alle** auch abgelaufene, aufgehobene Strafen und Kicks.
- Filter nach Art (Bann, Mute, Verwarnung, Kick) und Suche nach Spieler oder Grund; ein Klick auf den Spieler öffnet seine Seite.
- **Aufheben** direkt in der Liste, mit denselben Rechten wie im Spiel (`libertybans.<art>.undo.target.uuid`).
- Rechte wie bei LibertyBans: `libertybans.commands` plus `libertybans.list.banlist` (Banns), `libertybans.list.mutelist` (Mutes) bzw. `libertybans.list.history` (Verwarnungen und „Alle“); dazu `mradmin.player.view`. Ohne LibertyBans gibt es den Reiter nicht.

## Übersicht (Dashboard)
- Oben die **Lage**: Läuft etwas aus dem Ruder, steht hier jede Meldung mit Symbol und Wort, die dringendsten
  zuerst: niedrige TPS, hohe Tick-Zeit, voller Arbeitsspeicher, wenig Speicherplatz, hohe CPU-Last, hoher Ping,
  unbeantwortete und dir zugewiesene Tickets, gesperrte, auffällige oder pausierte Chunkloader, /nosleep-Blockaden,
  Wartung. Läuft
  alles rund, stehen dort die wichtigsten aktuellen Werte. Aktualisiert sich alle 2 Sekunden.
- Darunter Kacheln für Server, Spieler, Tickets, Chunkloader, Schlaf und Wartung mit Klick zur jeweiligen Seite.
- Jede Meldung und jede Kachel erscheint nur mit der Permission für ihren Bereich: Server und Spieler mit
  `mradmin.status`, CPU mit `mradmin.status.health`, Tickets mit `mradmin.tickets.view` (zugewiesene mit
  `mradmin.tickets.claim`), Chunkloader mit `mrchunks.admin.gui`, Schlaf mit `mrsleep.admin.gui`, Wartung mit `mradmin.maintenance.view`. Ist ein
  Plugin oder Feature nicht da, fehlt der Teil einfach.
- Das **Konto** (Geräte, Wiederherstellungscodes) öffnet ein Klick auf deinen Kopf bzw. Namen oben rechts.

## Health-Dashboard (`mradmin.status.health`)
- Wie der Health-Report von spark: TPS, MSPT (Durchschnitt, 95 %, Spitze), CPU (Server und System),
  Arbeitsspeicher, Garbage Collection, Spieler, Ø Ping, Chunks (mit MRchunks auch „davon Chunkloader“) und Entities
  als Verlauf über 1 Minute bis 30 Tage, dazu Welten, Systeminfos, Servergröße (alle 10 Minuten im Hintergrund
  gemessen), Größe der MRadmin-Datenbank und die 5 Chunks mit den meisten Entities.
- Gemessen wird jede Sekunde aus Werten, die Java und Paper ohnehin führen (kein Lag), die Seite aktualisiert sich
  live. Die letzte Stunde ist sekündlich da (ab 1 Minute wählbar), 24 Stunden im 10-Sekunden-Takt, ältere als
  5-Minuten-Mittel (Spitzen bleiben als Höchstwert erhalten, bei der Garbage Collection als eigene Linie
  „Spitze“). Aufbewahrung `health.history`: `24h`, `7d` oder `30d`, mit SQLite/MySQL bzw. `health.log` über
  Neustarts hinweg. Nach einem Neustart füllen die gespeicherten 5-Minuten-Werte die Zeit davor auf.
- Die Zeitachse zeigt immer genau den gewählten Zeitraum; wo keine Werte sind (Server aus), bleibt sie leer.
- Jedes Diagramm lässt sich mit **⤢** im Vollbild öffnen (schließen mit Esc, Klick daneben oder „Schließen“) und
  als Tabelle anzeigen.
- `health.alert`: Warnung an das Team (ingame) und ins Discord-Log, wenn die TPS länger unter einer Grenze liegt,
  und Entwarnung, sobald sie sich erholt hat.

## Spieler (`mradmin.player.view`)
- Alle, die schon auf dem Server waren: online zuerst, dann nach letztem Besuch, mit Suche, Filter (online,
  auffällig) und Markierungen (eingefroren, gemeldet, blockiert /nosleep, Chunkloader gesperrt ...). Keine IP-Adressen.
- Pro Spieler eine Seite: oben eine kurze Vorschau mit Ort, Spielmodus, Ping, Leben, erstem Besuch und Spielzeit;
  Einfrieren (`mradmin.player.freeze`) und Kicken mit Grund (`mradmin.player.kick`, Discord-Log `player-kick`).
- Darunter eine **Kachel pro Bereich** mit seinem Status auf einen Blick (z.B. „2 offen“, „3 / 5 Loader“, „im Duell
  #12“, „gesperrt bis …“). Ein Klick öffnet den Bereich mit allen Einzelheiten und Aktionen (Adresse
  `/players/<uuid>/<bereich>`, lässt sich also auch als Lesezeichen speichern). Bereiche: seine Tickets
  (`mradmin.tickets.view`) und ein Bereich pro Plugin, jeweils nur mit dessen Permissions - fehlt das Plugin, fehlt
  die Kachel:
  - **MRsleep:** /nosleep-Blockaden, Cooldown, Bypass, Sperre (`mrsleep.admin.gui`, `.clear`, `.bypass`, `.ban`).
  - **MRchunks:** seine Loader (mit Größe), Chunk-Budget und Dummy-Limit mit Herkunft (Standard, Permission, Admin), Budget setzen; auf der Seite eines Loaders seine Größe ändern (`mrchunks.admin.resize`) und seine Limit-Sperre für bis zu 24 Stunden aussetzen (`mrchunks.admin.suspendlock`); dort stehen auch die Diagramme der Messwerte (1m bis 12h, `mrchunks.admin.gui`) mit Live-Ansicht (`mrchunks.admin.live`)
    (`mrchunks.admin.limit`), für Chunkloader sperren (`mrchunks.admin.ban`), alle Loader löschen
    (`mrchunks.admin.manage`), Einladungen und mit ihm geteilte Loader (`mrchunks.admin.gui`). Verknüpfte Accounts
    ansehen, lösen und neu verknüpfen - per Spielername als Zweit- oder Haupt-Account, ohne Zustimmung der Spieler
    (`mrchunks.admin.manage`, wie `/mrchunks link`).
  - **MRpvp:** Duell-Statistik, die laufende Runde (Klick → Duellsteuerung) und die letzten Runden als Kacheln
    (`mrpvp.admin.games`), von Duellen sperren mit Grund und Schnellauswahl der Dauer (`mrpvp.admin.ban`, Dauern aus
    `gui.ban-durations` in der MRpvp-Config), gesicherte Stände zurückgeben (`mrpvp.admin.restore`), Statistik
    zurücksetzen (`mrpvp.admin.reset`).
  - **LibertyBans:** aktive Strafen und Verlauf (`libertybans.commands` + `libertybans.list.history`), bannen, muten,
    verwarnen und kicken (`libertybans.<typ>.do.target.uuid`), aufheben (`libertybans.<typ>.undo.target.uuid`). Die
    Dauer kommt aus der Schnellauswahl `libertybans.durations` in der MRadmin-Config; hat LibertyBans seine
    Dauer-Permissions eingeschaltet (`duration-permissions`), gelten sie auch hier. Ausgeführt wird über die API von
    LibertyBans - also mit dessen Kick-Bildschirm, Meldungen und Benachrichtigungen. In der Spielerliste erscheinen
    gebannte (`libertybans.list.banlist`) und gemutete (`libertybans.list.mutelist`) Spieler markiert.
- Spieler in Tickets, Chunkloadern und auf der Schlafseite verlinken direkt auf ihre Seite.

## Chunkloader (mit MRchunks)
Permissions von MRchunks: `mrchunks.admin.gui` ansehen, `mrchunks.admin.manage` handeln.

- Alle Chunkloader mit Status (OK, aus, auffällig, gesperrt, pausiert - immer mit Symbol und Wort), Probleme
  zuerst, Suche nach Name, Spieler, Welt oder Koordinaten, Server-Pause als Hinweis.
- Pro Loader: Ort, Sperrgrund, alle Messwerte des Lag-Schutzes mit Warn- und Sperrgrenze, Mitglieder. Aktionen:
  ein-/ausschalten, Sperre aufheben, löschen - der Owner bekommt wie ingame eine Nachricht, alles landet im
  Discord- und im Web-Protokoll.

## Schlaf (mit MRsleep)
Permissions: `mrsleep.admin.gui` ansehen, Blockaden aufheben mit `mrsleep.admin.clear`.

- Jede Welt mit Tag/Nacht, wie viele schlafen und wie viele nötig sind, dazu wer gerade mit /nosleep blockiert -
  einzeln oder alle aufheben. Alles zu einem einzelnen Spieler steht auf seiner Seite unter **Spieler**.

## Duelle (mit MRpvp)
Permissions von MRpvp: `mrpvp.admin.gui` ansehen, `.games` beendete Runden, `.stop`, `.pause`, `.ban`, `.reset`, `.restore`, `.review` handeln, `.kits` Kits bearbeiten.

- **Kits** (`.kits`, Chip „Kits →“): alle Kits als Karten (Symbol, Schnellleiste, Regeln), Reihenfolge mit ↑/↓, kopieren,
  neu anlegen. Im Kit-Editor das Inventar wie im Spiel - Klick auf einen Platz öffnet den Item-Editor, mit der Maus
  ziehen tauscht zwei Plätze (Strg = kopieren), Rechtsklick öffnet Kopieren/Ausschneiden/Einfügen/Leeren; auf Handy und
  Tablet Platz gedrückt halten = Menü, gedrückt halten und ziehen = verschieben. Dazu ID (eine andere ID benennt um), Name und Beschreibung auf Deutsch und Englisch (gleich =
  für alle Sprachen), Symbol, Permission und die Regeln: kein Schaden, Treffer bis zum Sieg, Regeneration und Hunger
  (fest an/aus oder Spielerwahl), braucht Bau-Arena, festes Zeitlimit, Herzen, Effekte beim Start (Effekt + Stufe),
  Cooldowns für Items des Kits (Sekunden), nur bestimmte Arenen. Eine neue Arena erscheint beim nächsten
  Öffnen; Kits ohne Arena-Auswahl nutzen sie sofort. Gelöschte Arenen, auf die ein Kit noch festgelegt ist, stehen rot
  da und müssen abgehakt werden. Gespeichert wird erst mit „Speichern“, neue Runden bekommen das Kit sofort.
- **Exportieren / Importieren** (Kit-Liste): Kits auswählen und als `.json` herunterladen - z.B. für einen anderen
  Server. Beim Import zeigt der Dialog pro Kit, was passiert: neu, oder schon vorhanden (unter neuer ID anlegen oder
  ersetzen), Arenen, die es hier nicht gibt (einer eigenen zuordnen oder weglassen), und mit welcher Minecraft-Version
  die Datei erstellt wurde. Gespeichert wird jedes Kit wie im Kit-Editor (gleiche Prüfungen). Dieselbe Datei geht
  auch im Spiel (MRpvp-Kit-Liste, Ordner `plugins/MRpvp/imports/`).

- Laufende Duelle live: Herzen beider Spieler, Zeit, Kit, Arena. Steuern: pausieren und fortsetzen (`.pause`),
  einen Sieger bestimmen oder abbrechen (`.stop`). Pro Runde eine Seite mit Mini-Karte (der
  Punkt leuchtet beim Treffen und Getroffenwerden), Inventaren und Kampf-Log; beendete Runden mit den gesicherten
  Inventaren (beim Betreten und am Ende, Enderchest, MRtoolbar-Gürtel) und einer Wiedergabe der Positionen.
- **Prüfen:** auffällige Runden, die noch nicht zählen - werten oder verwerfen. Gewertete Runden lassen sich auf
  ihrer Seite nachträglich aus der Wertung nehmen (mit optionalem Grund) und wieder werten (`.review`). Arenen mit ihren Kopien,
  Auffälligkeiten der letzten 24 Stunden. Alles zu einem einzelnen Spieler (Statistik, Runden, Sperre) steht auf
  seiner Seite unter **Spieler**.

## Items: Bilder, Namen und Editor
Überall, wo die Weboberfläche Gegenstände zeigt (Inventare bei Duellen, Kits), sehen sie aus wie im Spiel: das Bild
aus dem Inventar, Anzahl, Haltbarkeitsbalken, Verzauberungs-Glanz und beim Darüberfahren (am Handy: Antippen) der
Tooltip mit Name, Verzauberungen, Trank-Effekten und Beschreibung - in der Sprache des Browsers.

- **Woher die Bilder kommen:** MRadmin lädt beim ersten Start mit eingeschalteter Weboberfläche **einmal** den
  offiziellen Spiel-Client deiner Minecraft-Version von Mojang (rund 40 MB, mit Prüfsumme kontrolliert), zeichnet daraus
  alle Items und speichert nur das fertige Bild (rund 1,3 MB) und die Item-Namen unter `plugins/MRadmin/items/<version>/`.
  Der Client selbst wird nicht gespeichert. In der Plugin-JAR steckt nichts davon - Mojang erlaubt das Weitergeben nicht.
- **Neue Minecraft-Version:** Die Bilder werden für die neue Version einmal neu geladen, neue Items sind dann dabei.
- **Ohne Internet oder mit `web.item-icons: false`:** Items zeigen ein Kürzel statt eines Bildes, alles andere geht.
- **Item-Editor** (z.B. im Kit-Editor von MRpvp): Item suchen (Name oder ID, mit Bild), Anzahl, eigener Name und
  Beschreibung (MiniMessage: Farben und Stil wie `<red>`, `<#ff8800>`, `<b>`, `<gradient:red:gold>`),
  Verzauberungen (passende zuerst, auf Wunsch alle, Stufe 1-255, Hinweis bei Kombinationen, die es im Spiel nicht
  gibt), Glanz an/aus, unzerstörbar, Haltbarkeit, Trank (Grund-Trank, Farbe, eigene Effekte mit Stufe und Dauer),
  Farbe bei Lederrüstung, Rüstungsbesatz und Flugdauer bei Raketen. Rechts steht live der Tooltip. Beim Übernehmen
  baut MRadmin das echte Item und prüft alle Werte; was der Editor nicht anzeigt, bleibt am Item erhalten.

## Wartung
`mradmin.maintenance.view` ansehen, `.manage` ändern. Ankündigungen stehen unter [Kommunikation](#kommunikation-mradminnews-mradminannounce).

- Oben steht die **aktuelle Wartung** groß: läuft sie, mit Restzeit und Fortschrittsbalken, ist sie geplant, mit dem
  Countdown bis zum Start; darunter verlängern/kürzen und beenden bzw. absagen.
- **Mit Uptime Kuma** (`uptime-kuma` eingerichtet): Die Wartungen werden direkt in Kuma verwaltet - neue anlegen
  (jetzt bis zum Beenden, jetzt mit Dauer oder geplant), die aktuelle verlängern, pausieren/fortsetzen, löschen. Neue
  Wartungen hängt MRadmin an `uptime-kuma.monitor-ids`, damit sie für diesen Server gelten (und auf deren
  Status-Seiten erscheinen); geändert werden können nur Wartungen dieses Servers. Wiederkehrende Wartungen werden in
  Kuma bearbeitet (Link „In Uptime Kuma öffnen“).
- **Ohne Uptime Kuma**: die eigene Wartung von MRadmin wie `/mradmin maintenance` - jetzt starten oder mit Countdown
  planen (Dauer oder offenes Ende, Titel), verlängern, beenden bzw. absagen. Eine Vorschau zeigt vorher, wann sie
  beginnt und endet.
- **Kommende Wartungen**: die nächsten drei nach der aktuellen, ebenfalls aus Uptime Kuma und lokal gemischt mit
  Herkunft, Start, Ende und Dauer.
- **Letzte Wartungen**: die drei zuletzt beendeten oder abgesagten Wartungen, egal ob aus Uptime Kuma oder lokal
  (die Herkunft steht dabei), mit Zeitraum und Dauer. MRadmin merkt sich die letzten zehn.

## Kommunikation (`mradmin.news`, `mradmin.announce`)
Zwei Bereiche: **Beiträge** für die Spielerseite (`mradmin.news`) und **Ankündigungen** auf dem Bildschirm im Spiel
(`mradmin.announce`, siehe unten). Wer nur eins der beiden Rechte hat, sieht nur diesen Bereich.

### Beiträge
Drei Arten von Beiträgen für die Spieler, Texte getrennt auf Deutsch und Englisch (jeder sieht seine Sprache, fehlt sie,
die andere):

- **Neuigkeit** (Ankündigungen, Events): Titel und Text, **Anheften** stellt sie immer nach vorne.
- **Changelog**: Version, optional Titel und Einleitung, dazu **Einträge** mit Art (Neu, Geändert, Behoben, Entfernt) und
  **Überschriften** in drei Ebenen (1 Hauptüberschrift, z.B. „Plugins“; 2 Zwischenüberschrift, z.B. „MRpvp“; 3 eine
  weitere Gliederung). Einträge gehören zur Überschrift darüber und werden darunter eingerückt, unter jeder Ebene sind
  alle Arten möglich - hinzufügen, umsortieren, entfernen. Auf der Spielerseite eine Zeitleiste, im Discord die
  Abschnitte der Reihe nach.
- **Kritische Warnung**: Titel und Text. Beim Senden bekommen alle Spieler, die online sind, die Warnung im Chat und als
  Titel auf dem Bildschirm (`player-web.news.alerts`); wer später joint, sieht sie im Chat, solange sie aktiv ist. Sie
  läuft bis „Aktiv bis“ oder bis **Beenden**.

**Auch im Discord posten** (pro Beitrag): in den Kanal des Ankündigungs-Webhooks (`discord-announce`, wo auch die
Wartungen stehen), mit Link zur ganzen Nachricht auf der Spielerseite. Warnungen erwähnen `ping-role-id`
(`discord-announce.alerts.ping`), Neuigkeiten und Changelogs nicht (`discord-announce.news.ping`). Bei einem
bestehenden Beitrag lässt sich das einmal nachholen.

Neue Neuigkeiten und Changelogs bekommt jeder Spieler mit `mradmin.web.self` **einmal als Vorschau im Chat** mit einem
Link „[Lesen]“: sofort, wenn er online ist, sonst beim nächsten Join (`player-web.news.chat-preview`).

**Warnungen aus Uptime Kuma:** Vorfälle, die auf einer Kuma-Statusseite angeheftet sind (`uptime-kuma.status-pages`,
dafür reicht `uptime-kuma.url`; gelesen alle `uptime-kuma.incident-seconds`, Standard 15), erscheinen auf der
Spielerseite, im Karussell und oben auf dieser Seite. Der Link im Chat führt zur Spielerseite. Was im Spiel
passiert, hängt vom Stil in Kuma ab:

| Stil in Kuma | Farbe | Im Spiel |
|---|---|---|
| danger | rot | neu: Chat an alle, dazu groß „ACHTUNG / Kritische Warnung“; beim Join im Chat, solange angeheftet |
| warning | gelb | neu: Chat an alle (kein Titel); beim Join im Chat, solange angeheftet |
| info, primary, light, dark | blau | einmal pro Spieler im Chat, wie eine Neuigkeit |

Was beim Serverstart schon angeheftet ist, wird nicht noch einmal an alle angekündigt. Geändert und beendet werden die
Vorfälle in Uptime Kuma. Im Discord-Kanal der Ankündigungen (`discord-announce.kuma`) wird jeder Vorfall einmal gepostet
(rot pingt die Rolle), mit Link „Mehr dazu“ zur Seite Neuigkeiten der Spielerseite (nicht zu Kuma), bei Änderungen in
Kuma bearbeitet und am Ende auf „✅ Behoben“ gestellt.

**Sichtbar bis** blendet einen Beitrag nach diesem Tag aus (er bleibt in der Liste, grau). Anlegen, Bearbeiten, Beenden
und Löschen landen im Web-Protokoll. Gespeichert wird im Speicher von MRadmin unter `news` (wer welche Vorschau schon
bekommen hat: `news-seen`).

### Ankündigungen
**Senden** (`mradmin.announce`): eine eigene Vorlage (mit Werten, wenn sie Platzhalter hat) oder ein eigener Text
(„Titel | Untertitel“) auf dem Bildschirm, auf Wunsch auch in den Community-Discord. Alles landet im Web-Protokoll.

**Vorlagen** (`mradmin.announce.templates`): neu anlegen, bearbeiten, löschen - nutzbar im Web, im Ankündigungs-Menü
im Spiel und mit `/mradmin announce <name>`.
- **Texte** je Deutsch und Englisch: Titel, Untertitel, Chat, Actionbar (MiniMessage, Platzhalter `<args>`, `<arg1>` …),
  mit einer **Vorschau** des Bildschirms. Leere Felder werden nicht gezeigt.
- **Discord**: an/aus und eigener Text (Markdown, `{args}`).
- **Anzeige**: Ein-, Anzeige- und Ausblendzeit des Titels in Sekunden.
- **Ton**: wie in der Config, keiner oder ein eigener (alle Sounds des Spiels zur Auswahl) mit Lautstärke und Tonhöhe.
- **Zielgruppe**: Spieler, Bots, Support, Staff oder Admin. Jede Gruppe bekommt auch, was für die darunter gedacht ist -
  „Spieler“ erreicht alle, „Support“ Support, Staff und Admin (nach `mradmin.groups.*`), „Bots“ zusätzlich die NPCs.

Die eingebauten Vorlagen der Sprachdatei (`restart`, `restart-now`, `update`, `event`) stehen hier und im Spiel-Menü
nicht - MRadmin nutzt `restart` selbst. Mit `/mradmin announce` und in Skripten gehen sie weiter. Gespeichert wird im
Speicher von MRadmin unter `announce-templates`.

## Server
Alles rund um den laufenden Server steht unter einem Eintrag in der Leiste: **Server**. Oben Reiter, die jeder nur mit dem passenden Recht sieht:

| Reiter | Was dort ist | Recht |
|---|---|---|
| **Übersicht** | Vier Werte nebeneinander (Einzelheiten beim Drüberfahren): läuft seit, Paper-Build mit Farbpunkt (grün aktuell, gelb neuer Build verfügbar - geprüft alle 15 Minuten, auch ohne Starter), [Starter](wiki/starter.md) aktiv oder nicht, nächster geplanter Neustart. Darunter **Neu starten** und **Stoppen** mit Countdown (`versions.restart-delays`, Standard 15/30/60 Minuten), kürzere Zeiten über den roten Knopf **Notfall** (Fenster: erst 2FA-Code oder Passkey, dann Neustart/Stopp, Zeit aus `versions.emergency-delays` und Pflicht-Grund), bzw. der laufende Countdown (mit Grund) und **Abbrechen** (bei einem geplanten Neustart: **Diesen Neustart überspringen**); optional ein eigener **Grund**, sonst nennt MRadmin ihn selbst. Mit geplanten Neustarts zusätzlich **Automatische Neustarts 24 h aussetzen** / **Wieder einschalten**. Darunter Kurzblick auf die Plugins und die letzten Ereignisse. | `mradmin.versions.view`, Knöpfe `mradmin.server.restart` |
| **Plugins** | Versionen, Updates, Installieren, Konfiguration im Browser - siehe [Plugins](#plugins-mradminversionsview) | `mradmin.versions.view` |
| **Protokoll** | Ereignisse und Konsolenausgabe - siehe [Protokoll](#protokoll-mradminwebaudit) | `mradmin.web.audit` oder `mradmin.web.console.view` |
| **Konsole** | Befehle ausführen - siehe [Konsole](#konsole-mradminwebconsoleexecute) | `mradmin.web.console.execute` |

Neu starten geht nur, wenn etwas den Server zurückbringt: der Starter oder `versions.restart-method`. Stoppen geht immer - ohne Starter bleibt der Server dann aus, bis ihn jemand startet. Alte Adressen (`/plugins`, `/logs`, `/console`) leiten auf den passenden Reiter weiter.

## Plugins (`mradmin.versions.view`)
Seite „Plugin-Konfiguration“ unter `/plugins` (die alte Adresse `/versions` leitet weiter).
- **Auf diesem Server:** eine Karte pro installiertem MRtechnology-Plugin mit Version, Zustand (aktuell / Update
  verfügbar / wartet auf Neustart / Fehler) und Update-Knopf. Ein Klick öffnet die Seite des Plugins (`/plugins/<name>`)
  mit installierter und neuester Version, Veröffentlichungsdatum, „Was ist neu“, Links zur Website und zu GitHub; bei
  MRtoolbar auch die neueste Version des Client-Mods (nur zur Info, den installieren die Spieler selbst), bei MRadmin
  die squaremap-Version.
- **Weitere MRtechnology-Plugins:** alle Plugins, die MRadmin kennt, aber nicht laufen - mit **Installieren**
  (`mradmin.versions.install`). Die Datei kommt aus dem offiziellen Release (gleiche Prüfungen wie beim Herunterladen,
  gibt es nur Vorabversionen, die neueste davon) unter ihrem Release-Namen nach `plugins/`; geladen wird sie beim
  nächsten Start. Liegt die JAR schon seit dem Start in `plugins/` und läuft das Plugin trotzdem nicht, steht es als
  „nicht geladen“ da (Paper hat es abgelehnt - Konsole ansehen).
- Erkannt werden installierte Plugins an der `build-info.properties` in ihrer JAR - es braucht dafür keine bestimmte
  Version der anderen Plugins; nicht installierte kennt MRadmin aus einer festen Liste (`versions/Catalog.java`).
  Geprüft wird alle `versions.check-interval-hours` und mit **Jetzt prüfen** (nur mit `mradmin.versions.install`,
  höchstens einmal pro Minute, GitHub erlaubt ohne Anmeldung 60 Anfragen pro Stunde).
- **Konfiguration** (`mradmin.config`, Reiter auf der Seite eines Plugins, `/plugins/<name>/config`): die `config.yml`
  des Plugins als Formular. Links die Abschnitte und eine Suche (Name, Erklärung oder Schlüssel), rechts je Einstellung
  Name, kurze Erklärung und das passende Bedienelement; „Neustart nötig“ markiert Werte, die erst nach einem Neustart
  wirken, „Standard“ setzt auf den mitgelieferten Wert zurück, abhängige Felder erscheinen nur, wenn sie etwas
  bewirken. Geänderte Felder sind gelb markiert, die Leiste unten zeigt die Zahl der Änderungen. Speichern: MRadmin
  prüft alle Werte (bei einem Fehler wird nichts gespeichert und das Feld rot markiert), schreibt die Datei und lässt
  das Plugin neu laden; ein Fehler beim Neuladen erscheint als roter Hinweis. Hat jemand die Datei inzwischen von Hand
  geändert, kommt „Neu laden“ statt Speichern. Geheimnisse (Webhook-URLs, Passwörter) zeigt die Seite nie, nur ob sie
  gesetzt sind. Welche Plugins das können, steht in `docs/MODULE-API.md` (`webConfig`, ab API 12) - MRadmin selbst und alle MRtechnology-Plugins.
- **Herunterladen** (`mradmin.versions.install`): nur aus dem Release des eigenen GitHub-Repos, nur mit passender
  SHA-256-Prüfsumme und nur, wenn die Datei wirklich dieses Plugin enthält. Sie wird unter dem Namen der installierten
  JAR in `plugins/update/` abgelegt; Paper tauscht sie beim nächsten Start aus. Der Dateiname bleibt deshalb der alte, nur
  der Inhalt ist neu. `versions.auto-download`
  (`off`, `patch`, `minor`, `all`) lädt Updates von selbst.
- **Server neu starten** (`mradmin.server.restart`): nur, wenn ein Update heruntergeladen oder ein Plugin installiert, aber noch nicht geladen ist,
  und `versions.restart-method` eingestellt ist (Standard
  `none` - ein Server kann sich nicht selbst wieder starten). Countdown aus `versions.restart-delays`, angekündigt mit
  den Vorlagen `restart` und `restart-now`, abbrechbar. Danach stoppt der Server (`stop` - ein Start-Skript mit
  Schleife, ein Wächter oder das Panel startet ihn wieder) oder startet über das `restart-script` der `spigot.yml` neu
  (`script`). Ohne Knopf zeigt die Seite nach dem Herunterladen „Neustart nötig“. Beispiel-Skripte: [Plugins & Updates](wiki/plugins.md#server-neu-starten).
- Auf der Übersicht zeigt eine Kachel, ob Updates warten oder ein Neustart nötig ist.

## Push-Benachrichtigungen
Unter **Konto → Benachrichtigungen**.

- Meldungen direkt aufs Gerät, auch bei geschlossener Seite: neue Tickets, Nachrichten in meinen Tickets, alle
  Ticket-Nachrichten, gesperrte Chunkloader, Lag-Alarm, Wartungen. Jeder legt selbst fest, welche er bekommt - und
  bekommt nur, wofür er die Rechte hat.
- Pro Gerät einschaltbar (mehrere Geräte möglich, „Test senden“). Ein Gerät, das abgemeldet wird, bekommt nichts mehr.
- Ohne Drittanbieter: Standard-Web-Push (VAPID, verschlüsselt - der Push-Dienst des Browsers kann nichts mitlesen).
  Auf iPhone/iPad nur als installierte App (Teilen → „Zum Home-Bildschirm“) und über HTTPS.

## Protokoll (`mradmin.web.audit`)
Alles, was auf dem Server passiert ist, auf einer Seite. Oben Quellen (Menü, mehrere gleichzeitig), Suche, Zeitraum
(1 Std. bis 14 Tage) und **Live/Anhalten**. Darunter das **Aktivitätsdiagramm**: Balken über den Zeitraum, gestapelt
nach Ereignissen, Konsole, Warnungen und Fehlern; Ziehen oder ein Klick auf einen Balken zeigt nur diesen Ausschnitt
(✕ hebt ihn auf). Darunter die **Tabelle** mit 50 Einträgen pro Seite und Seitenzahlen („1-50 von 873“) - ein Klick
klappt den ganzen Text und die Einzelheiten auf. Live läuft auf Seite 1 ohne Ausschnitt; sonst zählt die Seite neue
Einträge nur („3 neue Einträge - anzeigen“).

- **Ereignisse:** dieselben Ereignisse, die MRadmin und die anderen MRtechnology-Plugins in ihre Discord-Logs
  schicken (auch wenn Discord aus ist), in der Farbe aus `discord.events`, dazu jede Aktion in der Weboberfläche
  (wer, was, von welchem Konto). Filter nach Quelle (MRadmin, Weboberfläche, je Plugin - mehrere gleichzeitig), Suche
  in Text und Ereignis, „Ältere laden“. Neue Einträge erscheinen sofort.
- Aufgehoben `logging.keep-days` Tage (höchstens 14) in `plugins/MRadmin/logs/events-<datum>.log` (eine Zeile pro
  Ereignis). Plugins schreiben erst ab der Version mit Modul-API 13 hinein.
- **Konsole** (Quelle in derselben Liste, mit `mradmin.web.console.view` - in `mradmin.groups.staff`), nur lesen: die
  Zeilen der Server-Konsole nach Zeit zwischen den Ereignissen - neue live, ältere aus Papers `logs/latest.log` und
  `logs/<datum>-<n>.log.gz` (bis zu 14 Tage), Stufe rechts (Info, Warnung gelb, Fehler rot; Stacktrace-Zeilen gehören
  zur Zeile davor). Links und IP-Adressen ausgeblendet wie in der Web-Konsole. Kein Entsperren nötig. MRadmin
  speichert dafür nichts - die Dateien hält Paper selbst. Staff ohne `mradmin.web.audit` sieht nur diese Quelle.

## Konsole (`mradmin.web.console.execute`)
Nur für Admins (in `mradmin.groups.admin` - so mächtig wie die Server-Konsole). Wer nur mitlesen soll, bekommt
`mradmin.web.console.view` und sieht die Konsole im **Protokoll**.

- Die Server-Konsole live (aus `logs/latest.log`, auch Minecrafts eigene Ausgaben) mit Eingabezeile, Filter, Warnungen
  und Fehler farbig. Links und IP-Adressen sind ausgeblendet (`web.console.hide-links` / `hide-ips`).
- Vor dem Öffnen noch einmal der Code aus der Authenticator-App (keine Wiederherstellungscodes), dann
  `web.console.unlock-minutes` lang offen (pro Gerät). 5 falsche Codes sperren die Konsole 15 Minuten.
- Befehle laufen als Konsole. `web.console.blocked` sperrt Befehle komplett - auch mit Namespace
  (`minecraft:op`) und versteckt in `execute ... run`. Jeder Befehl landet im Web-Protokoll und im Discord-Log
  (`web-console`), gesperrte Versuche im Protokoll.

## Entbannungsanträge & Kontaktformular
Zwei öffentliche Seiten, ohne Anmeldung, beide landen als Tickets beim Team (`web.requests` in der Config).

- **Entbannung** (`<public-url>/entbannung`, braucht LibertyBans): Wer gebannt ist und joinen will, sieht in der
  Ban-Meldung die Adresse und einen **persönlichen Code** (z. B. `1-K7F2QX`). Nur mit Name und Code sieht man den
  Bangrund und kann einen Antrag stellen, so kann niemand für andere Anträge stellen. Der Code wird nicht gespeichert,
  sondern aus Spieler, Ban und einem Server-Geheimnis berechnet.
  - **Ein Antrag pro Ban.** Im Ticket stehen die Stellungnahme und die Ban-Details. Das Team antwortet wie gewohnt
    und entscheidet mit **Entbannen** (hebt den Ban in LibertyBans auf, braucht `libertybans.ban.undo.target.uuid`)
    oder **Ablehnen**. Ein normales Schließen zählt als Ablehnung.
  - Nach einer Ablehnung ist **einmal Einspruch** möglich, dann öffnet sich das Ticket wieder. Danach ist die
    Entscheidung endgültig.
  - Entbannungsanträge sind auch **ingame** im Ticket-Menü sichtbar.
- **Kontakt** (`<public-url>/kontakt`, verlinkt unter dem Impressum und in der Fußzeile): Name, Mail-Adresse, Betreff,
  Nachricht. Diese Anfragen gibt es **nur im Web**, ingame tauchen sie nie auf.
- Nach dem Absenden bekommt man einen **privaten Statuslink** (`/anfrage/...`): Stand, Verlauf, antworten,
  Einspruch.
- **Mails** (optional, Abschnitt `mail`): Eingangsbestätigung mit Statuslink, Antworten des Teams und die
  Entscheidung. Testen mit `/mradmin mail test <adresse>`. Ohne Mailversand sieht man Antworten über den Statuslink,
  und bei Kontaktanfragen öffnet ein Knopf im Ticket das eigene Mailprogramm.
- **Spamschutz ohne externe Dienste:** unsichtbares Lockfeld, Mindest-Ausfüllzeit, eine kleine Rechenaufgabe im
  Browser (Proof of Work, ca. 1 s), Limits pro IP und Minute/Stunde, Obergrenze offener Kontaktanfragen.

## Datenschutz & Impressum
- Jede Seite (auch die Anmeldung) hat eine Fußzeile mit **Impressum** und **Datenschutz**. Beim ersten Start legt
  MRadmin Vorlagen in `plugins/MRadmin/web/impressum.md` und `datenschutz.md` an. Die Datenschutz-Vorlage beschreibt,
  was die Oberfläche verarbeitet. Bitte ausfüllen und prüfen (keine Rechtsberatung). Alternativ eigene Seiten unter
  `web.legal` verlinken.
- Keine Drittanbieter: Auch die Spielerköpfe erzeugt MRadmin selbst aus den Skins, der Browser lädt nichts von
  fremden Diensten. Nur ein technisch notwendiges Cookie, kein Tracking.
- Das Protokoll wird nach `web.privacy.audit-retention` (Standard 90 Tage) gelöscht. Mit `web.privacy.mask-ip` werden
  IP-Adressen nur gekürzt gespeichert.
