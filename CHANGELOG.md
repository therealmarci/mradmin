# Changelog

Alle wichtigen Änderungen an MRadmin. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Chunkloader: Auslastung pro Loader:** Jede Loader-Kachel der Chunkloader-Seite zeigt ständig einen Balken **Ø Auslastung** (Anteil an der Sperrgrenze beim schlechtesten Messwert; grün, gelb ab der Warnung, rot ab der Sperrgrenze). Die Liste ist danach **absteigend sortiert** - ganz oben steht der Loader, den die gestaffelte Abschaltung als nächsten abschalten würde. Braucht MRchunks ab der nächsten Version.
- **Chunkloader: Rückkehrzeit in den Kacheln:** Loader, die durch Lags deaktiviert sind (auch nach der harten Grenze, wenn alle aus sind), zeigen in ihrer Kachel, seit wann sie aus sind und wann sie voraussichtlich zurückkommen. Die Loader-Seite sagt „voraussichtlich“ statt „frühestens“.

### Behoben & geändert
- **Kurze Chat- und Menütexte:** Chatnachrichten sind jetzt eine knappe Zeile; Einzelheiten (Hinweise, Grenzwerte, Befehle) erscheinen, wenn man mit der Maus darüberfährt, in der Konsole in Klammern dahinter. Auch einige Menü-Beschreibungen sind kürzer (`lang-version` 16 - eigene Änderungen an diesen Texten in `lang/de.yml` und `lang/en.yml` werden dabei ersetzt).

## [1.0.2] - 2026-10-30

### Behoben & geändert
- **Chunkloader: Steuerung als Kennzahl-Kacheln:** Die beiden Schalter „Gestaffelte Abschaltung“ und „Alle pausieren“ sind jetzt zwei Kacheln in der Reihe der Kennzahlen (Zustand groß, darunter seit wann und von wem, farbige Oberkante) mit einem Schalter oben rechts statt einer eigenen Kachel mit Knöpfen. Ausschalten der Abschaltung und Pausieren fragen weiter nach.

## [1.0.1] - 2026-10-30

### Neu
- **Chunkloader: Steuerung für das Team:** Auf der Chunkloader-Seite gibt es die Kachel **Steuerung**: alle Chunkloader vorübergehend pausieren bzw. die Pause aufheben (`mrchunks.admin.pause`) und die gestaffelte Abschaltung mit harter Grenze aus- und einschalten (`mrchunks.admin.monitoring`), jeweils mit Hinweis, wer es seit wann geändert hat. Braucht MRchunks ab 1.0.1.

### Behoben & geändert
- **Kit-Editor auf Handy und Tablet (Issue #13):** Auf Touch-Geräten gab es weder Rechtsklick noch Ziehen - Items ließen sich nur bearbeiten. Jetzt: einen Platz **kurz gedrückt halten** und loslassen öffnet das Menü (Kopieren, Ausschneiden, Einfügen, Platz leeren), gedrückt halten und **mit dem Finger ziehen** verschiebt das Item auf einen anderen Platz. In der Kit-Liste sortieren auf Touch-Geräten die Pfeile (der Ziehgriff ist dort ausgeblendet).
- **Chunkloader: Steuerung kompakter:** Die beiden Schalter der Kachel **Steuerung** (alle pausieren, gestaffelte Abschaltung) stehen jetzt je in einer schmalen Zeile mit Status-Punkt und kleinem Knopf; die ausführliche Erklärung erscheint beim Darüberfahren.
- **Chunkloader: Zustand „Durch Lags deaktiviert“:** Die Chunkloader-Seiten zeigen Loader, die die gestaffelte Abschaltung von MRchunks vorübergehend abgeschaltet hat (eigener Filter, gelb, mit Zeitpunkt und frühester Rückkehr), dazu einen Hinweis bei hoher Serverlast. Braucht MRchunks ab 1.0.1.
- **Kit-Editor (Duelle) zeigt, warum ein Kit nicht in `/duel` erscheint:** Bei jeder Arena steht rot der Grund, wenn sie das Kit so nicht nimmt („braucht Elytra im Kit“, „nur andere Kits“, „kein Bauen“), Arenen mit „nur Elytra“ sind gekennzeichnet. Passt das Kit in keine eingeschaltete Arena, warnt der Editor, und die Kit-Liste zeigt „nicht in /duel“. Braucht MRpvp ab der nächsten Version (ältere Versionen: keine Warnung).

## [1.0.0] - 2026-10-29

### Neu
- **Eigene Ankündigungs-Vorlagen im Web:** Unter **Kommunikation → Ankündigungen** legt das Team Vorlagen an, ändert und löscht sie (neue Permission **`mradmin.announce.templates`**, in `mradmin.groups.admin`). Einstellbar: Titel, Untertitel, Chat und Actionbar je Deutsch und Englisch (MiniMessage, Platzhalter `<args>`, `<arg1>` …) mit Vorschau des Bildschirms, Discord an/aus mit eigenem Text, Ein-/Anzeige-/Ausblendzeit des Titels, Ton (wie in der Config, keiner oder eigener mit Lautstärke und Tonhöhe) und die **Zielgruppe** Spieler / Bots / Support / Staff / Admin - jede Gruppe bekommt auch, was für die darunter gedacht ist („Spieler“ = alle). Die Vorlagen gehen im Web, im Ankündigungs-Menü im Spiel und mit `/mradmin announce <name>`.
- **Changelogs mit Überschriften:** Neben den Einträgen (Neu, Geändert, Behoben, Entfernt) lassen sich Überschriften in drei Ebenen setzen - 1 Hauptüberschrift (z.B. „Plugins“), 2 Zwischenüberschrift (z.B. „MRpvp“), 3 eine weitere Gliederung. Die Einträge gehören zur Überschrift darüber und werden eingerückt, unter jeder Ebene sind alle Arten möglich. So erscheinen sie auf der Spielerseite, in der Vorschau im Team-Bereich und im Discord. Bestehende Changelogs bleiben unverändert.
- **Wartungsverlauf:** Die Wartungsseite der Weboberfläche zeigt die **drei nächsten** und die **drei letzten Wartungen** (beendet oder abgesagt) - egal ob aus Uptime Kuma oder lokal, die Herkunft steht dabei, mit Zeitraum und Dauer. MRadmin merkt sich dafür die letzten zehn beendeten bzw. abgesagten Wartungen; Wartungen, die Kuma als vorbei meldet, kommen dazu.
- **Neue Statistiken und Gruppen:** Gesetzte Blöcke, Totem-Pops, wie oft geschlafen, Strecke geschwommen und Strecke geflogen kommen dazu (`blocks-placed`, `totems`, `times-slept`, `distance-swum`, `distance-flown`). Auf der Statistik-Seite sind die Werte in beiden Reitern gruppiert: Zeit & Erfahrung, Bauen, Kampf, Unterwegs, Freizeit. Alle Werte des Reiters „Persönlich“ gibt es auch als Rangliste im Reiter „Server“: die neuen und `items-crafted`, `jumps`, `fish-caught` werden an eine bestehende `player-web.rankings.statistics` angehängt (config-version 35). Der Umschalter Podest/Tabelle ist jetzt ein kleines Symbolpaar oben rechts in jeder Ranglisten-Kachel.
- **Neuigkeiten, Changelogs und kritische Warnungen:** Die Seite **Neuigkeiten** der Weboberfläche kennt jetzt drei Arten: Neuigkeiten, **Changelogs** (Version + Einträge Neu / Geändert / Behoben / Entfernt) und **kritische Warnungen**, die sofort an alle Online-Spieler gehen (Chat und Titel; wer später joint, sieht sie im Chat, solange sie aktiv ist; „Beenden“ im Web). Pro Beitrag optional **auch im Discord posten** (Ankündigungs-Webhook, mit Link zur ganzen Nachricht; Warnungen erwähnen die Ping-Rolle). Neue Neuigkeiten und Changelogs bekommt jeder Spieler **einmal als Vorschau im Chat** mit Link. **Uptime Kuma:** Vorfälle, die auf einer Statusseite angeheftet sind, erscheinen je nach Stil: danger rot mit Chat + Titel, warning gelb nur im Chat, info/primary/light/dark blau und einmal pro Spieler im Chat wie eine Neuigkeit (nur `uptime-kuma.url` nötig, keine Anmeldung; gelesen alle `uptime-kuma.incident-seconds`, Standard 15, config-version 32; der Link im Chat führt zur Spielerseite). Im Discord-Kanal der Ankündigungen wird jeder Vorfall gepostet (Link „Mehr dazu“ zur Spielerseite), bei Änderungen bearbeitet und am Ende auf „✅ Behoben“ gestellt (`discord-announce.kuma`: `enabled`, `ping` für rote Vorfälle; config-version 31). Die Seite **Neuigkeiten** der Spielerseite ist neu gestaltet: oben aktive Warnungen, darunter umschaltbar **Neuigkeiten** (die neueste groß, die anderen als Karten, Lesen im Fenster) und **Changelog** (Zeitleiste). Neu in der Config (config-version 30): `uptime-kuma.status-pages` und `incidents`, `player-web.news.chat-preview` (`enabled`, `max` 3, `max-age-days` 14) und `player-web.news.alerts` (`chat`, `title`), `discord-announce.news.ping` (false), `discord-announce.alerts.ping` (true), `discord-announce.colors.news/changelog/alert`.
- **Chunkloader mit Chunk-Budget (MRchunks):** Die Weboberfläche zeigt bei jedem Chunkloader seine Größe (z.B. 5×9 Chunks) und das Budget des Owners. Mit der MRchunks-Permission `mrchunks.admin.resize` lässt sich die Größe dort pro Richtung oder in alle Richtungen ändern. Auf der Spielerseite steht statt Setz- und Aktiv-Limit das Chunk-Budget (genutzt / verfügbar, mit `mrchunks.admin.limit` änderbar), dazu die feste Höchstzahl an Loadern. Die Aktivität eines Loaders zeigt zusätzlich, wie ausgelastet alle aktiven Loader des Owners zusammen sind. Braucht MRchunks ab der Version mit Chunk-Budget; ältere MRchunks-Versionen werden weiter wie bisher angezeigt.

- **Spielerseite im Web (`/web`, im Aufbau):** Jeder Spieler bekommt mit `/web` einen Link zu seiner eigenen Seite in der Weboberfläche. Der Link gilt nur einmal und ein paar Minuten, meldet das Gerät aber dauerhaft an - danach lässt sich die Seite (`<web.public-url>/me`) als Lesezeichen speichern. Jeder Besuch verlängert die Anmeldung. Unter **Geräte** meldet man einzelne Geräte ab, `/web devices` und `/web logout` im Spiel melden alle ab. Deutsch und Englisch, umschaltbar. Neu in der Config: `player-web` (`enabled`, Standard `false`, `session-days` 90, `link-minutes` 5, `max-devices` 5; config-version 24). Permission `mradmin.web.self` (in `mradmin.player`). Gesperrte Spieler (LibertyBans) kommen nicht hinein. Auf der Seite: die Bereiche der installierten Plugins (z.B. MRchunks: eigene Chunkloader verwalten) und **eigene Tickets** wie mit `/report` - neue öffnen, antworten, schließen (gleiche Regeln: Wartezeit, offene Tickets, Mindestlängen; Namen des Teams nur mit `tickets.show-staff-names`). Dazu eine **Statistik-Seite** mit Ranglisten: Minecraft-Statistiken aller Spieler (auch offline - Spielzeit, abgebaute Blöcke, besiegte Mobs, Tode, Strecke ...; Auswahl und Anzahl Plätze in `player-web.rankings`, config-version 25) und Ranglisten der Plugins (z.B. MRpvp). Der eigene Platz wird hervorgehoben, auch wenn er weiter unten liegt. Jeder Spieler kann sich auf der Seite aus allen Ranglisten austragen.
- **Neuigkeiten und Changelogs für die Spielerseite:** Admins schreiben in der Weboberfläche unter **Neuigkeiten** Beiträge für die Spieler - als Neuigkeit oder Changelog, auf Deutsch und Englisch, anheftbar und mit Ablaufdatum. Auf der Startseite der Spielerseite (`/web`) steht unter der Begrüßung ein Karussell, das von selbst weiterblättert und sich am Handy wischen lässt: geplante oder laufende Wartung, ein geplanter Neustart und die neuesten Beiträge. Alle Beiträge stehen auf der neuen Seite **Neuigkeiten**. Permission `mradmin.news` (in `mradmin.groups.admin`). Neu in der Config: `player-web.news` (`enabled` true, `seconds` 8, `slides` 5, `show-maintenance`, `show-restart`; config-version 26). Der Lesezeichen-Tipp auf der Spielerseite ist jetzt nur noch eine dezente Zeile am Ende.
- **Sprache im Web umschaltbar:** Die Weboberfläche merkt sich eine gewählte Sprache (Deutsch/Englisch) im Browser, sonst gilt wie bisher die Browsersprache.
- **Modul-API 1.2.0 (API-Version 3):** Plugins liefern jetzt selbst ihre Kachel auf der Startseite der Weboberfläche (`WebModule.tile()`), ihre Push-Benachrichtigungen (`pushKinds()`), die Namen ihrer Ticket-Kategorien (`reportCategories()`), die von ihnen geladenen Chunks für den Serverzustand (`AdminModule.loadedChunks()`) und ihre Kunstspieler (`isNpc()`). Nur Ergänzungen - Plugins für ältere API-Versionen laufen weiter. Details in `docs/MODULE-API.md`.

### Behoben & geändert
- **Sicherheits-Update der mitgelieferten Bibliotheken:** Die Uptime-Kuma-Anbindung (Socket.IO) brachte alte Versionen mit bekannten Lücken mit - org.json 20090211 (zwei DoS-Lücken), OkHttp 3.12 (Zertifikatsprüfung) und Okio 1.15. Jetzt org.json 20250517, OkHttp 4.12.0 und Okio 3.6.0; alle mitgelieferten Bibliotheken sind laut OSV-Datenbank ohne bekannte Lücke (Audit 2026-10-29).
- **`mradmin.web.self` ist jetzt eine eigene, beschriebene Permission** in der `plugin.yml` (bisher nur über `mradmin.player` vergeben). An der Vergabe ändert sich nichts.
- **Anleitung und Befehlshilfe:** neue Befehlsübersicht in der Anleitung; die Kurzhilfe von `/mradmin` (plugin.yml) nennt jetzt alle Unterbefehle (`web`, `mail`, `storage` fehlten, `menu` gab es nicht).
- **Admin-Modus für Support:** `mradmin.mode` steckt jetzt in `mradmin.groups.support` (vorher Staff) - Support kommt so an den Admin-Kompass und die Menüs aller Plugins. Admin-Modus anderer (`mradmin.mode.others`) bleibt bei Admin.
- **Versionen: „Jetzt prüfen“ nur noch mit `mradmin.versions.install`:** Mit `mradmin.versions.view` (Support und Staff) sieht man die Seite nur noch, ohne selbst bei GitHub nach Updates suchen zu lassen. Die automatische Prüfung (`versions.check-interval-hours`) läuft wie bisher.
- **Chunkloader-Seite für Support:** Mit `mrchunks.admin.toggle.conspicuous` (MRchunks, in dessen Support-Gruppe) erscheinen auf der Seite eines auffälligen Loaders die Knöpfe zum Aus- und Einschalten des Loaders und seines Dummys.
- **Weboberfläche: Startseite mit Auswahl:** Wer nicht angemeldet ist, sieht zuerst zwei Kacheln - „Ich bin Spieler“ führt zur Spielerseite (Anmeldung mit dem Code aus `/web`), „Ich bin im Team“ zur Team-Anmeldung. Ist die Spielerseite aus, geht es direkt zur Team-Anmeldung.
- **Weboberfläche: Team-Anmeldung neu in zwei Schritten:** erst Minecraft-Name und Passwort, dann der Code aus der Authenticator-App (mit Anzeige, als wer man sich anmeldet, und „ändern“). Passkey und Discord stehen im ersten Schritt als Alternative. Geprüft wird weiterhin alles zusammen - das Passwort allein wird nie bestätigt.
- **Konto → Sicherheit: Discord immer sichtbar:** Ist die Discord-Anmeldung auf dem Server nicht eingerichtet, steht dort jetzt, was in der Config (`web.login.discord`) fehlt - vorher fehlte die Karte dann ganz.
- **Weboberfläche: Konto neu gestaltet:** Kopfkachel mit Kopf, Name, letzter Anmeldung und dem Schutz des Kontos als farbigem Chip (gut geschützt grün, schwach gelb). Die Bereiche Sicherheit / Benachrichtigungen / Geräte als Umschalter, Überschriften ohne Emojis. Geräte als Karten, das aktuelle zuerst und markiert. Funktionen bleiben gleich.
- **Weboberfläche: Versionen neu gestaltet:** Kopfkachel mit den Zahlen (Plugins, Updates, warten auf Neustart), darunter wann zuletzt geprüft wurde (blinkt beim Prüfen) mit „Jetzt prüfen“. Der Neustart steht als Kachel in der Farbe seines Zustands - läuft ein Countdown, rot mit großer Uhr und „Abbrechen“; den Neustart-Knopf gibt es nur noch, wenn ein Update heruntergeladen, aber noch nicht installiert ist (gelb mit „Neustart nötig“). Die Plugins sind Karten mit Statusfarbe (aktuell grün, Update gelb, wartet auf Neustart blau, Fehler rot), Updates zuerst; bei einem Update steht „installiert → neu“ groß da. Funktionen und Rechte bleiben gleich.
- **Spielerseite: Changelog wie ein Dokument:** Jede Version steht in der geschriebenen Reihenfolge da - Überschriften als Überschriften (Ebene 1 als farbige Kapitelzeile, 2 fett, 3 grau), jeder Eintrag als Zeile mit farbigem Etikett (Neu, Geändert, Behoben, Entfernt) davor; eingerückte Teile haben feine Führungslinien links. Vorher waren die Einträge nach Art in Kästen nebeneinander sortiert.
- **Weboberfläche: Wartungsseite heißt nur noch „Wartung“** (die Ankündigungen sind unter Kommunikation).
- **Eingebaute Ankündigungs-Vorlagen nicht mehr in den Menüs:** `restart`, `restart-now`, `update` und `event` aus der Sprachdatei stehen im Web und im Ankündigungs-Menü im Spiel nicht mehr (MRadmin nutzt `restart` selbst für den Neustart-Countdown). Mit `/mradmin announce <name>` und in Skripten gehen sie weiter.
- **Weboberfläche: „Neuigkeiten“ heißt jetzt „Kommunikation“ und ist neu gestaltet:** Kopfkachel mit der Zahl der Beiträge und aktiven Warnungen, zwei Bereiche **Beiträge** und **Ankündigungen**. Die Ankündigung ist von der Wartungsseite hierher umgezogen; die Seite sehen jetzt alle mit `mradmin.news` **oder** `mradmin.announce` (jeweils nur ihren Bereich), die Wartungsseite nur noch mit `mradmin.maintenance.view`. Beim Schreiben: Art als Kacheln, Sprache als Umschalter, Anheften und Discord als Schalter, Changelog-Zeilen farbig nach Art. Alle Beiträge als Karten in der Farbe ihrer Art mit Filter und Anzahl.
- **Weboberfläche: Wartung neu gestaltet:** Kopfkachel mit dem Zustand (keine Wartung grün, geplant blau, Countdown gelb, läuft rot). Die aktuelle Wartung steht groß in der Farbe ihrer Phase: läuft sie, mit der Restzeit als laufender Uhr und einem Fortschrittsbalken (seit wann, wie lange gesamt), ist sie geplant, mit dem Countdown bis zum Start; darunter Verlängern/Kürzen als Umschalter und Beenden bzw. Absagen. Ohne Wartung ein grünes „Keine Wartung geplant“. Der Uptime-Kuma-Zustand steht als farbiger Chip darunter. Bei Uptime Kuma stehen Verlängern, Beenden und Löschen direkt an der aktuellen Wartung (die Liste aller Kuma-Wartungen ist weg, dafür der Link „In Uptime Kuma öffnen“). Darunter über die ganze Breite die nächsten drei und die letzten drei Wartungen, dann „Neue Wartung“ mit Umschaltern für Start und Dauer und einer Vorschau, wann sie beginnt und endet. Ankündigung: Discord als Schalter. Funktionen und Rechte bleiben gleich.
- **Weboberfläche: Kampf auf der Mini-Karte sichtbar (live und Wiedergabe):** Ein Treffer zeigt sich als Linie vom Angreifer (in seiner Farbe) zum Getroffenen, mit Einschlag-Ring, Kreuz und aufsteigender Schadenszahl („-2.5❤“). Schaden aus der Umgebung roter Ring mit Zahl, Schuss Ring am Schützen, Enderperle violetter Ring, Totem goldener Strahlenkranz, Arena-Grenze oranger Ring, K.O. großer roter Ring. Die Wiedergabe läuft jetzt flüssig, die Spieler gleiten zwischen den Messpunkten, die Geschwindigkeit ist wählbar (0,25× bis 2×). Live erscheinen Treffer sofort (vorher kam das Kampf-Log erst nach bis zu 5 Sekunden an). Braucht MRpvp vom selben Stand.
- **Weboberfläche: Rundenansicht (MRpvp) neu gestaltet:** Oben stehen sich beide Kämpfer gegenüber (großer Kopf, Name, live die Herzen bzw. nach der Runde „Sieger“ / Ergebnis), in der Mitte Status, Uhr und Spielnummer; die Kopfkante zeigt den Zustand (Kampf/Sieg grün, Countdown/pausiert/Unentschieden gelb). Jede Seite behält überall die Farbe ihres Punkts auf der Mini-Karte (rot / blau). Darunter Kit, Arena, Beginn, Ende und Wertung als Kacheln. Laufende Runden: Steuerung in eigener Kachel, Karte und Kampf-Log nebeneinander, die Inventare beider Spieler. Beendete Runden: Prüfen bzw. Wertung, ein **Vergleich** beider Seiten als „Tauziehen“-Balken (pro Wert ein Balken, Spieler 1 rot von links, Spieler 2 blau von rechts, je nach Anteil; Werte an den Enden, Bezeichnung mittig darüber, Namen über den Enden: ausgeteilt, eingesteckt, Treffer, Pfeile, Totems, Leben am Ende; Pfeile und Totems nur, wenn sie jemand benutzt hat), die Wiedergabe jetzt mit **Abspielen-Knopf**, das Kampf-Log als Zeitleiste (der Eintrag zur Wiedergabezeit ist markiert, Klick springt hin) und die gesicherten Inventare mit Umschaltern für Spieler/Zeitpunkt und Inventar/Endertruhe/Gürtel. Rechte bleiben gleich.
- **Kit-Editor (Web): neue Bedienung des Inventars:** Klick bearbeitet ein Item wie bisher. Neu: Items mit der Maus auf einen anderen Platz **ziehen** (tauscht; mit gedrückter Strg-Taste wird kopiert), **Rechtsklick** auf einen Platz öffnet ein Menü mit Bearbeiten, Kopieren, Ausschneiden, Einfügen und Platz leeren, über einem Platz gehen auch **Strg+C / Strg+X / Strg+V / Entf**. Das kopierte Item steht unter dem Inventar („In der Zwischenablage“) und lässt sich beliebig oft einfügen. Auf Handy und Tablet öffnet langes Drücken das Menü. Die Umschalter „Bearbeiten“ / „Verschieben“ sind weg. Das Kit-Symbol wird jetzt unten bei der Vorschau „So erscheint das Kit im Menü“ geändert (Knopf „Symbol ändern“ oder Klick auf das Symbol), nicht mehr oben.
- **Kits (Web): Liste neu aufgebaut:** Die Kits stehen jetzt als Liste in ihrer Reihenfolge, eine Zeile pro Kit: Platz mit ▲ ▼, Symbol und Name, Schnellleiste, Itemzahl, Permission und Regeln, Kopieren/Bearbeiten. Am PC lässt sich die Reihenfolge auch per Ziehen am Griff (⋮⋮) ändern. Alle Zeilen haben dieselben Spalten, Symbole, Namen und Schnellleisten stehen also genau untereinander. Auf schmalen Bildschirmen rutschen Schnellleiste und Regeln in eigene Zeilen.
- **Weboberfläche: Kits und Kit-Editor (MRpvp) neu gestaltet:** Die Kit-Liste hat eine Kopfkachel mit der Anzahl und den Knöpfen Importieren / Exportieren / Neues Kit. Im Editor stehen oben Symbol, Name, ID, Itemzahl und ein Hinweis auf ungespeicherte Änderungen; solange etwas ungespeichert ist, hängt unten eine Leiste mit „Speichern“. Name und Beschreibung werden pro Sprache (DE/EN umschaltbar) bearbeitet, mit Vorschau, wie das Kit im Menü aussieht. Regeln: an/aus als Schalter, Zahlen als Kacheln, Regeneration und Hunger als Umschalter (Spieler wählen / immer an / immer aus) statt Auswahlliste, Arenen als Chips zum Antippen. Funktionen und Rechte bleiben gleich.
- **Weboberfläche: Duelle (MRpvp) neu gestaltet:** Kopfkachel mit Symbol und dem Knopf „Kits →“, die Zahlen als Kacheln (laufende Duelle grün, Auffälligkeiten und „Warten auf Prüfung“ gelb, belegte Arenen mit Balken). Die Reiter Live / Spiele / Arenen / Auffälligkeiten / Prüfen stehen als Umschalter mit Anzahl in einer Kachel. Laufende Duelle sind Karten mit Statusfarbe (Kampf grün, Countdown bzw. pausiert gelb), beiden Kämpfern mit Kopf, Herzen und „Abbrechen“; offene Einladungen mit beiden Köpfen. Runden als Karten nach Ergebnis (Sieg grün, Unentschieden gelb, abgebrochen grau), der Sieger grün hervorgehoben, Filter als kleiner Umschalter. Arenen zeigen ihre Kopien als farbigen Balken (belegt, bereit, im Aufbau). Der gewählte Reiter bleibt beim Zurückkommen erhalten. Inhalte und Rechte bleiben gleich.
- **Weboberfläche: Schlaf (MRsleep) neu gestaltet:** Kopfkachel mit den Regeln und dem Zustand („Schlafen möglich“ bzw. „n blockiert“), die Zahlen als Kacheln (blockiert gelb, Sperren rot), jede Welt als Karte mit Tageszeit (Rand: Tag grau, Nacht blau, Gewitter bzw. blockiert gelb), Schläfern groß mit Balken und den Blockierern mit „Freigeben“. Inhalte und Rechte bleiben gleich.
- **Weboberfläche: Chunkloader-Grenzen pro Loader:** Die Loader-Seite erklärt jetzt, dass die Grenzen nur für diesen Loader gelten (bis 3×3 feste Werte, darüber nach Größe, nie darunter) und eine Sperre nur ihn trifft - statt der bisherigen Zeile über alle Loader des Owners zusammen (braucht MRchunks ab diesem Stand).
- **Weboberfläche: Rückmeldungen als Blase:** „Erledigt“-Meldungen nach einer Aktion (z. B. „Erledigt - der Owner wurde informiert.“) erscheinen im Team-Bereich jetzt als kleine Blase, die unten hereingleitet und nach ein paar Sekunden verschwindet - wie auf der Spielerseite (dort jetzt mit derselben Blase). Fehler bleiben als roter Hinweis auf der Seite stehen.
- **Weboberfläche: Chunkloader-Größe nur noch am Loader:** „Größe ändern“ ist aus der Loader-Seite im Team-Bereich entfernt - die Größe stellt man im Spiel am Loader ein, im Web wird sie nur angezeigt. Die Kachel „Dummies“ zeigt nur noch die Anzahl (ohne verknüpfte Accounts).
- **Weboberfläche: Chunkloader im Team-Bereich neu gestaltet:** Die Liste hat oben eine Kopfkachel mit den Zahlen (Loader, aktiv, Probleme), darunter die Kennzahlen als Kacheln, dann Umschalter nach Status (mit Anzahl und Farbpunkt) und Suche. Die Loader stehen als Karten mit Statusfarbe (aktiv grün, aus grau, gesperrt rot, auffällig gelb, pausiert blau). Ein Loader zeigt oben Besitzer, Name, Status, die Sperre als roten Hinweis, die Fakten als Kacheln (Budget mit Balken) und die Aktionen; darunter links die Messwerte als Kacheln („Wert / Grenze“ mit Balken und Warnmarke), rechts Größe ändern und die Mitglieder.
- **Weboberfläche: Serverzustand neu gestaltet:** Kopfkachel wie auf den anderen Seiten mit dem Zustand des Servers (flüssig/angespannt/laggt), der Zeitraum als Umschalter, die Kennzahlen als Kacheln mit farbiger Kante je nach Wert (TPS und RAM mit Balken), die Karten Welten, Systeminfos (als Kacheln, Speicherplatz mit Balken) und Lag-Suche mit Symbol und Rangnummern. Graphen und Inhalte bleiben gleich.
- **Weboberfläche: Strafen (LibertyBans) beim Spieler neu gestaltet:** eigene Kopfzeile mit dem aktuellen Stand, aktive Strafen als Karten mit Ablauf und „Aufheben“, „Bestrafen“ mit der Art als Kacheln (Ban, Mute, Warn, Kick), Grund und einem Knopf je erlaubter Dauer, darunter der Verlauf. Funktionen und Rechte bleiben gleich.
- **Weboberfläche: Spieler im Team-Bereich neu gestaltet:** im Stil der Spielerseite. Die Liste hat oben eine Kopfkachel mit den Zahlen (Spieler, online), darunter Umschalter Alle / Online / Auffällig mit Suche und die Spieler als Karten (grün = online, gelb = mit Markierung). Die Seite eines Spielers zeigt oben großen Kopf, Name und Zustand (online, eingefroren, Admin-Modus), die Fakten als Kacheln und Einfrieren/Kicken; die Bereiche (Tickets, Chunkloader, Schlaf, Duelle, Strafen) sind Kacheln wie auf der Startseite. Inhalte und Rechte bleiben gleich.
- **Weboberfläche: Knöpfe im Ticket zum Chunkloader und zum Spieler:** Ein Ticket zu einer Chunkloader-Sperre hat oben „Zum Chunkloader“ (öffnet den gesperrten Loader, mit Recht für MRchunks), ein Ticket zu einem Spieler „Zu <Name>“ - bei Meldungen der gemeldete Spieler, sonst der Spieler, der das Ticket geschrieben hat (mit Recht für die Spielerverwaltung; nicht bei Kontaktanfragen von der Website).
- **Weboberfläche: Tickets im Team-Bereich neu gestaltet:** im Stil der Spielerseite. Die Liste hat oben eine Kopfkachel mit den Zahlen (offen, warten auf Antwort, mir zugewiesen), darunter die Reiter Offen / Mir zugewiesen / Archiv, Suche und Kategorien. Die Tickets stehen als Karten mit Statusfarbe: blau = neue Antwort des Spielers, grün = übernommen, gelb = wartet, grau = Archiv. Ein Ticket zeigt oben Nummer, Kategorie, Status und Übernehmen, darunter die Fakten als Kacheln und das Gespräch als Chat mit Tagestrennern (Spieler links, Team violett rechts). Antworten, Schließen bzw. Annehmen/Ablehnen stehen direkt unter dem Chat. Funktionen und Rechte bleiben gleich.
- **Weboberfläche: Chunkloader-Status ohne Zeichen:** „Gesperrt“ und „Auffällig“ stehen ohne Schloss bzw. Ausrufezeichen - in der Chunkloader-Liste, den Filtern, der Detailseite, auf der Spielerseite und bei den Markierungen in der Spielerliste.
- **Weboberfläche: Karussell auf der Startseite des Team-Bereichs:** wie auf der Spielerseite (wechselt alle 8 Sekunden, Balken zwischen den Pfeilen, wischbar, hält an, solange die Maus darauf ist). Die erste Folie zeigt den Health-Monitor (nur mit dem Recht für den Serverzustand): TPS groß mit Zustand (flüssig/angespannt/laggt), Tick-Zeit, RAM und Spieler sowie vier kleine Verläufe der letzten Stunde (TPS, Tick-Zeit, RAM, Spieler) mit einem Punkt pro Minute, minütlich aktualisiert. Warnungen des Serverzustands (TPS, RAM, Speicherplatz …) stehen mit auf dieser Folie. Danach bekommt jede Funktion, bei der etwas zu tun ist, genau eine Folie (z. B. Tickets ohne Antwort und eigene Tickets zusammen) mit Einzelheiten (z. B. TPS-Verlauf, belegter Speicher, Wartungszeitraum, Restzeit bis zum Neustart, offene Tickets, Werte des Plugins) und „Ansehen →“. Die Lage-Kachel („Alles läuft rund“) entfällt.
- **Weboberfläche: Startseite des Team-Bereichs neu gestaltet:** gleicher Inhalt, jetzt im Stil der Spielerseite - Begrüßung mit Kopf und Live-Punkt, die Lage (was Aufmerksamkeit braucht bzw. „Alles läuft rund“) als farbige Kachel im Ton des wichtigsten Hinweises, Kacheln mit Symbol und großer Hauptzahl in einem einheitlichen Farbschema, Messwerte wie MSPT, RAM oder Schläfer mit Balken. Die Kachel „Spieler“ ist weg (die Zahl steht schon in der Lage), die Online-Spieler stehen unten als Kärtchen - mit Spieler-Recht führt ein Klick direkt zum Spieler.
- **Weboberfläche: neue Kopfleiste im Team-Bereich und auf dem Handy:** Die Kopfleiste des Team-Bereichs sieht jetzt aus wie auf der Spielerseite: Seiten links, rechts die Sprachflaggen und der eigene Name mit Kopf. Ein Klick darauf öffnet ein Menü mit „Konto“ und „Abmelden“. Der Live-Status ist jetzt ein kleiner Punkt am Kopf. Auf dem Handy stehen in beiden Bereichen oben Flaggen und Name, die Seiten in einer Leiste unten: bis zu fünf direkt, bei mehr die ersten vier und ein Menü-Knopf (☰ „Mehr“) für den Rest. Die Spielerseite bekommt diese Leiste neu (vorher brachen die Reiter über mehrere Zeilen um).
- **Spielerseite: Jeder Besuch verlängert die Anmeldung sofort:** Bisher wurde ein Besuch höchstens einmal pro Stunde vermerkt, deshalb sprang die Restzeit „ohne Besuch“ nach einem Besuch nicht wieder auf die vollen `player-web.idle-days` (7 Tage). Jetzt zählt jeder Aufruf gleich; auf die Platte geschrieben wird weiterhin höchstens stündlich (und beim Herunterfahren).
- **Spielerseite: Kopfzeile und Geräte:** Die Sprache wählt man jetzt über zwei kleine Flaggen (Deutsch/Englisch), auch auf der Anmeldeseite. Der eigene Name mit Kopf steht rechts neben den Flaggen; ein Klick öffnet ein Menü mit „Geräte“ und „Abmelden“ (der Reiter „Geräte“ in der Leiste ist weg). Die Geräte-Seite ist neu gestaltet: dieses Gerät oben mit der restlichen Gültigkeit der Anmeldung (Balken, wann sie spätestens endet und wann ohne Besuch), darunter die anderen Geräte mit eigener Restzeit, Abmelden einzeln oder alle anderen auf einmal.
- **Spielerseite schont den Server:** Die Seiten laden nur noch nach, solange der Browser-Tab sichtbar ist (beim Zurückkehren sofort). Das Karussell merkt sich die Hinweise der Plugins pro Spieler 45 Sekunden (nach einer eigenen Aktion sofort neu), statt bei jedem Nachladen alle Bereiche zu bauen. Die Statistik liest nur die Werte des eigenen Spielers bei jedem Aufruf live, die der anderen Online-Spieler höchstens einmal pro Minute.
- **Minecraft-Statistiken wurden nicht gefunden:** Seit Minecraft 26 liegen die Dimensionen in `<welt>/dimensions/…` und die Statistiken der Spieler unter `<welt>/players/stats` statt `<welt>/stats`. MRadmin suchte am falschen Ort, deshalb blieben Ranglisten, „Meine Statistik“ und die Statistik-Seite leer. Jetzt sucht MRadmin den Weltordner mit `level.dat` und liest dort zuerst den neuen, sonst den alten Ort; beim ersten Lesen steht in der Konsole, woher und wie viele Spieler.
- **XP-Level statt „Besiegte Spieler“:** Die Statistik `player-kills` gibt es nicht mehr, dafür `xp-level` (aus den gespeicherten Spielerdaten, auch für Spieler, die offline sind). Online-Spieler haben auf der Statistik-Seite, in den Ranglisten und in „Meine Statistik“ immer ihre aktuellen Werte (direkt vom Server statt aus den Dateien, die Minecraft nur beim Speichern schreibt). In `player-web.rankings.statistics` wird `player-kills` automatisch durch `xp-level` ersetzt (config-version 33).
- **Spielerseite: Statistik neu gestaltet, mit zwei Reitern:** **Persönlich** zeigt jeden eigenen Wert aus den Minecraft-Statistiken (Spielzeit, abgebaute Blöcke, Mobs, Tode, Strecke, Fische, Sprünge, hergestellte Gegenstände ...) als Kachel mit Platz („Platz 3 von 12“), Medaille für die Top 3, Balken im Vergleich zum Besten und dessen Namen, dazu die eigenen Plätze in den Ranglisten der Plugins (z.B. Duelle). **Server** zeigt, was alle Spieler zusammen geschafft haben (Spielerzahl und Summen der Ranglisten-Werte), die Ranglisten mit Siegertreppchen für die ersten drei und darunter die weiteren Plätze - jede Rangliste lässt sich zwischen **Podest und Tabelle** umschalten (der Browser merkt sich die Wahl); die Tabelle zeigt alle Spieler (bis 1000), scrollt in sich und öffnet bei der eigenen, hervorgehobenen Zeile; alle Ranglisten-Kacheln sind gleich aufgebaut (Titel höchstens zweizeilig, Umschalter immer darunter, Inhalt einer Reihe auf einer Linie) -, sowie das Austragen aus den Ranglisten. Wer sich ausgetragen hat, sieht seine eigenen Plätze weiterhin.
- **Spielerseite: Tickets neu gestaltet:** Kopf mit offenen, neu beantworteten und geschlossenen Tickets, Tickets als Karten in einer Kachel mit Umschalter Offen / Geschlossen / Alle (farbig: neue Antwort blau, offen grün, geschlossen grau). Ein neues Ticket wählt die Art über anklickbare Kacheln statt über ein Aufklappmenü, mit Zeichenzählern und Mindestlängen. Die Ticket-Ansicht ist ein Chatverlauf (du rechts, Team links, Systemhinweise in der Mitte, Tagestrenner) mit Antwortfeld darunter (Strg + Enter sendet), sie lädt alle 10 Sekunden nach.
- **Spielerseite: Anmeldung mit 6-stelligem Code:** Die Seite hat jetzt immer dieselbe Adresse (`<web.public-url>/me`, als Lesezeichen speicherbar) und fragt zum Anmelden nach einem 6-stelligen Code. Den gibt `/web` im Spiel zusammen mit dem Link aus - ein Klick auf den Code im Chat kopiert ihn. Der Code gilt 2 Minuten und nur einmal. Ein Gerät bleibt höchstens 14 Tage angemeldet und wird nach 7 Tagen ohne Besuch schon früher abgemeldet, dann fragt die Seite wieder nach einem Code. Neu in der Config: `player-web.idle-days` (7) und `player-web.code-seconds` (120) statt `player-web.link-minutes`; `player-web.session-days` ist jetzt 14 (ein unveränderter alter Wert 90 wird umgestellt; config-version 29). Falsche Codes sind pro Adresse und insgesamt begrenzt. Alte Links `/me/login#…` führen auf die Seite.
- **Schönere Kacheln auf der Spielerseite:** Die Kacheln der Plugins auf der Startseite der Spielerseite (`/web`) haben ein neues Design: eigene Farbe pro Plugin, Symbol groß im Hintergrund, die wichtigste Zahl groß, weitere Werte darunter, Werte wie „120 / 245 Chunks“ mit Balken, Sperren als Warnzeile.
- **Eigene Statistik auf der Spielerseite:** Neben den Kacheln der Plugins steht auf der Startseite der Spielerseite eine Kachel **Meine Statistik** mit Spielzeit, abgebauten Blöcken, besiegten Mobs und Toden aus den Minecraft-Statistiken; ein Klick führt zur Statistik-Seite. Neu in der Config: `player-web.personal-stats` (Liste wie bei `rankings.statistics`, leer = keine Kachel; config-version 27). Unter der Begrüßung steht ein neuer Text.
- **Mehr im Karussell der Spielerseite:** Das Karussell zeigt jetzt zuerst, was den Spieler direkt betrifft: Warnungen der Plugins (z.B. MRchunks: Chunkloader wegen Lag gesperrt, nahe an der Lag-Grenze, Budget überschritten) und neue Antworten des Teams in den eigenen Tickets, jeweils mit Link dorthin. Zwischen den Pfeilen läuft ein Balken pro Folie ab, bis die nächste kommt (Klick springt hin, hält bei Maus oder Tastatur an). Neu in der Config: `player-web.news.show-plugin-notices` und `show-tickets` (beide `true`, config-version 28). Der Lesezeichen-Tipp ist kürzer.
- **Neue Bausteine für die Spielerseite (Modul-API 1.4.1, API-Version 5):** Karten (mit Detail-Ansicht per Klick), Balken mit Grenzwert-Markierung, Spielerlisten mit Köpfen, Formulare, eingebettete Seiten und Gruppen (mehrere Bausteine auf einer Kachel) (z.B. die Live-Karte von squaremap). Karten blättern in Seiten zu höchstens 5 Reihen, Kennzahlen in einer geöffneten Karte stehen als ruhige Liste. Neue Töne `own` (violett) und `other` (gelb) für Vergleiche, z.B. die beiden Seiten eines Duells. Bereichsseiten haben jetzt einen Kopf im Stil der Kachel auf der Startseite, Rückmeldungen nach einer Aktion erscheinen unten als kurze Einblendung. Für eingebettete Seiten erlaubt die Weboberfläche jetzt Frames von http(s)-Adressen.
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
