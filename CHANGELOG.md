# Changelog

Alle wichtigen Änderungen an MRadmin. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung nach [Semantic Versioning](https://semver.org/lang/de/), veröffentlicht als `mc<minecraft>-v<version>`
(siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Pre-Release:** Alle 0.x-Versionen sind Testversionen. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Jeder Spieler in seiner Sprache:** Chat, Menüs, Dialoge, Ankündigungen, Wartungs-Countdown, Bossbars, Titel, der Kick-Bildschirm und der Staff-Chat richten sich jetzt nach der Sprache, die der Spieler in Minecraft eingestellt hat (`language: auto`, neuer Standard): Deutsch (auch Österreich/Schweiz) bekommt Deutsch, Englisch bekommt Englisch, alle anderen `fallback-language` (Standard `en`). `language: de` oder `en` stellt weiterhin eine Sprache für alle ein.
- **Server-Sprache (`server-language`):** Discord-Logs und -Ankündigungen, die Serverliste während einer Wartung (dort ist die Sprache des Spielers noch unbekannt), Push-Benachrichtigungen, Mails und Notizen in Tickets kommen in dieser Sprache (Standard `en`).
- **Automatische Umstellung:** Bestehende Configs bekommen `language: auto`; eine bisher fest eingestellte Sprache wird zur `server-language`, damit z.B. die Discord-Logs eines deutschen Servers deutsch bleiben.
- **Ankündigungen pro Spieler:** Vorlagen (z.B. `restart`) und die Standard-Überschrift eigener Texte sieht jeder in seiner Sprache; die Wartungs-Bossbars gibt es pro Spieler.

### Behoben & geändert
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
