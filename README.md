# MRadmin

Admin-Werkzeuge für **Paper 26.3** (Java 25): Wartungsmodus mit Uptime-Kuma-Anbindung, Ankündigungen und Admin-Modus.

> 🧪 **Pre-Release (0.x):** MRadmin befindet sich in der Testphase. Bis zum Release 1.0.0 können sich Config,
> Befehle und Permissions noch ändern.

- **Wartungsarbeiten:** Während einer Wartung kann nur das Team joinen. Geplante Wartungen kommen live aus
  **Uptime Kuma**, 15 Minuten vorher läuft eine **Bossbar mit Countdown**. Änderungen in Kuma (verlängern,
  verschieben, absagen) werden sofort übernommen.
- **Ankündigungen** auf dem Bildschirm aller Spieler, mit eigenem Text oder **Presets** (z.B. fürs Restart-Script).
- **Admin-Modus:** Creative und ein Teleport-Kompass. Beim Beenden ist alles wieder exakt wie vorher: Inventar,
  Rüstung, XP, Leben, Effekte und Position.
- **LuckPerms-Permissions** für jede Funktion, **Sprachdateien** Deutsch/Englisch, alles in einer `config.yml`,
  **Discord-Logs** und **Update-Check**.

---

## Installation
1. `MRadmin-mc26.3-v<version>.jar` nach `plugins/` legen und den Server starten.
2. `plugins/MRadmin/config.yml` anpassen und `/mradmin reload` ausführen.
3. Dem Team die Rechte geben, z.B. `/lp group staff permission set mradmin.staff true`.

Sprache: `language: de` oder `language: en` in der Config. Alle Texte (auch Presets und Discord) stehen in
`plugins/MRadmin/lang/<sprache>.yml`. Neue Texte aus Updates werden automatisch ergänzt.

### Funktionen ein- und ausschalten
Jede Funktion lässt sich unter `features` in der Config abschalten (`/mradmin reload` genügt):

```yaml
features:
  maintenance: true    # Wartungsmodus inkl. Uptime Kuma
  announce: true       # Ankündigungen
  admin-mode: true     # Admin-Modus und Kompass
  teleport: true       # zu Spielern teleportieren (Spieler-Menü, /mradmin tp, Tickets, Serverzustand)
  summon: true         # Spieler herholen
  inventory: true      # Inventar/Enderchest ansehen
  freeze: true         # Einfrieren
  server-status: true  # Serverzustand
  tickets: true        # Tickets & Reports (/report)
  staff-chat: true     # Staff-Chat (/sc)
```

Eine ausgeschaltete Funktion verschwindet komplett: Ihre Befehle melden „ausgeschaltet“, Knöpfe und Kacheln fehlen in
allen Menüs, und sie greift nirgends mehr ein. Ihre Daten (Tickets, geplante Wartungen) bleiben erhalten.
Sonderfälle: Wer beim Abschalten des Admin-Modus darin ist, bekommt sein Inventar sofort zurück (offline beim nächsten
Join). Beim Abschalten von `freeze` sind alle eingefrorenen Spieler frei. Discord-Logs, Discord-Ankündigungen,
Uptime Kuma und Update-Check haben ihren Schalter (`enabled`) im jeweiligen Abschnitt.

### Zusammenarbeit mit MRsleep und MRchunks
MRadmin, MRsleep und MRchunks laufen jeweils **auch allein**. Sind sie zusammen installiert, erkennt MRadmin die
anderen automatisch, auch wenn sie später geladen oder entfernt werden, und passt die Menüs von selbst an:
- **Dashboard:** ein Knopf je Plugin mit Live-Infos, der dessen Admin-Menü öffnet.
- **Spieler-Menü:** die Infos des Spielers aus dem Plugin, ein Klick öffnet dessen Spieler-Menü.
- **Serverzustand** (MRchunks): auffällige, gesperrte und pausierte Chunkloader statt der Chunks mit den meisten
  Entities.

Konfigurieren muss man dafür nichts. Wer eine Verbindung nicht möchte, schaltet sie unter `integrations` ab
(`mrsleep: false` / `mrchunks: false`). Beim Start steht in der Konsole, welche Plugins verbunden sind.

### Speicher: YAML, SQLite oder MySQL
Unter `storage.type` in der Config wählst du, wo MRadmin Tickets, Wartungen, eingefrorene Spieler und den Status der
Discord-Ankündigungen speichert:

| `type` | Wo | Wofür |
|---|---|---|
| `yaml` (Standard) | Dateien im Plugin-Ordner | kleine Server |
| `sqlite` | eine Datei `plugins/MRadmin/mradmin.db` | viele Tickets, die kommende Weboberfläche |
| `mysql` | MySQL oder MariaDB (`storage.mysql`) | später mehrere Server mit gemeinsamen Tickets |

- Gespeichert wird immer im Hintergrund, nie auf dem Server-Thread. Mit SQLite/MySQL wird bei einer neuen
  Nachricht nur diese geschrieben, nicht alle Tickets.
- **Umstellen:** `type` ändern und den Server neu starten. Beim ersten Start werden die bisherigen YAML-Daten
  übernommen, die Dateien bleiben als `*.migrated.yml`. `/mradmin storage` zeigt, welcher Speicher aktiv ist.
- Ist die Datenbank beim Start nicht erreichbar, nutzt MRadmin vorerst die YAML-Dateien und meldet das in der
  Konsole.
- Die Treiber (HikariCP, SQLite, MariaDB) lädt Paper beim ersten Start selbst herunter (Ordner `libraries/` des
  Servers), die JAR bleibt klein.

### Weboberfläche (Vorschau)
MRadmin vom Handy, Tablet oder PC bedienen: **Übersicht** (TPS, MSPT, RAM, Spieler online, Wartung, offene
Tickets), **Tickets** (Liste mit Suche und Filtern, Chat-Ansicht: antworten, übernehmen, schließen, wieder öffnen -
Änderungen erscheinen live), **Konto** (Geräte, Wiederherstellungscodes) und **Protokoll**. Weiteres folgt
(Plan: [docs/WEB-PLAN.md](docs/WEB-PLAN.md)).

1. In der Config `web.enabled: true`, `web.public-url` auf deine Adresse setzen (z.B. `https://admin.deinserver.de`)
   und `/mradmin reload`. Für viele Tickets empfiehlt sich `storage.type: sqlite`.
2. Reverse Proxy einrichten: Domain mit HTTPS → `http://127.0.0.1:8125`. Der eingebaute Webserver lauscht nur
   lokal und hat selbst kein HTTPS.
3. Konto anlegen: ingame `/mradmin web setup` (Permission `mradmin.web`) → Link öffnen → Passwort festlegen,
   Authenticator-App per QR-Code verbinden → Wiederherstellungscodes sichern. Für andere: `/mradmin web invite
   <spieler>` (auch aus der Konsole).

**Sicherheit**
- Anmeldung immer mit Passwort **und** Code aus der Authenticator-App (oder einem Wiederherstellungscode).
  Passwörter werden mit PBKDF2 (600.000 Runden) gespeichert, Sitzungs-Cookies nur als Hash.
- Nach 5 Fehlversuchen ist das Konto 15 Minuten gesperrt, dazu höchstens 10 Anmeldeversuche pro Minute und IP.
  Damit das mit der echten IP der Besucher funktioniert, trägst du deinen Proxy unter `web.trusted-proxies` ein.
- Cookies: `HttpOnly`, `SameSite=Strict`, über HTTPS `Secure`. Änderungen nur mit eigenem Header und passender
  Herkunft (kein Auslösen über fremde Seiten), strenge Sicherheits-Header.
- Im Web gelten dieselben Rechte wie ingame. Mit LuckPerms auch für Offline-Spieler, ohne LuckPerms über
  `web.roles` und `web.players`. Wer `mradmin.web` verliert, ist sofort überall abgemeldet.
- Jede Anmeldung und Aktion landet im Protokoll (Web: **Protokoll**, mit `mradmin.web.audit`), Anmeldungen und
  Einrichtungen auch im Discord-Log (`web-login`, `web-setup`).
- Passwort vergessen oder Handy weg: einfach einen neuen Einrichtungslink holen. Der setzt Passwort und 2FA neu
  und meldet alle Geräte ab.

**Datenschutz & Impressum**
- Jede Seite (auch die Anmeldung) hat eine Fußzeile mit **Impressum** und **Datenschutz**. Beim ersten Start legt
  MRadmin Vorlagen in `plugins/MRadmin/web/impressum.md` und `datenschutz.md` an. Die Datenschutz-Vorlage beschreibt,
  was die Oberfläche verarbeitet. Bitte ausfüllen und prüfen (keine Rechtsberatung). Alternativ eigene Seiten unter
  `web.legal` verlinken.
- Keine Drittanbieter: Auch die Spielerköpfe erzeugt MRadmin selbst aus den Skins, der Browser lädt nichts von
  fremden Diensten. Nur ein technisch notwendiges Cookie, kein Tracking.
- Das Protokoll wird nach `web.privacy.audit-retention` (Standard 90 Tage) gelöscht. Mit `web.privacy.mask-ip` werden
  IP-Adressen nur gekürzt gespeichert.

| Befehl | Funktion | Permission |
|---|---|---|
| `/mradmin web` | Status (läuft, Adresse, Konten, Geräte) | `mradmin.web` |
| `/mradmin web setup` | Einrichtungslink für dich | `mradmin.web` |
| `/mradmin web sessions [spieler]` | angemeldete Geräte | `mradmin.web` (andere: `.manage`) |
| `/mradmin web logout [spieler]` | alle Geräte abmelden | `mradmin.web` (andere: `.manage`) |
| `/mradmin web invite <spieler>` | Einrichtungslink für jemand anderen | `mradmin.web.manage` |
| `/mradmin web remove <spieler>` | Web-Konto löschen | `mradmin.web.manage` |

---

## Wartungsarbeiten

### Ablauf
| Zeitpunkt | Was passiert |
|---|---|
| `warn-before` vor Beginn (Standard 15 min) | Chat-Ankündigung, **Bossbar mit Countdown** für alle (wird in der letzten Minute rot) |
| `reminders` (15m, 10m, 5m, 2m, 1m, 30s) | Chat-Erinnerung mit Ton |
| letzte 10 Sekunden | großer Countdown auf dem Bildschirm |
| Beginn | Titel „Wartungsarbeiten“, nach `kick-delay-seconds` werden alle ohne Bypass gekickt |
| während der Wartung | Join nur mit `mradmin.maintenance.bypass`. Staff sieht eine Bossbar mit der Restzeit. Die Serverliste zeigt „Wartung“ |
| Ende | Chat-Nachricht, alle dürfen wieder joinen |

Wird die Wartung in Kuma **verlängert oder verkürzt**, bekommt das Team eine Nachricht und die Bossbar passt sich an.
Wird sie **verschoben**, gibt es eine Nachricht an alle mit der neuen Zeit, und der Countdown läuft neu.
Wird sie **abgesagt** (gelöscht oder pausiert), verschwindet die Bossbar und alle erhalten eine Nachricht.

### Uptime Kuma einrichten
```yaml
uptime-kuma:
  enabled: true
  url: "https://status.deinserver.de"
  username: "admin"
  password: "geheim"
  monitor-ids: [12]      # nur Wartungen, die den Minecraft-Monitor (ID 12) betreffen
```
- MRadmin meldet sich wie die Weboberfläche per Socket.IO an. Die öffentliche Statusseiten-API zeigt Wartungen
  erst, wenn sie schon laufen, und reicht deshalb für den Countdown nicht.
- **2FA aktiv?** Dann statt Passwort den Login-Token eintragen (`token`): im Browser bei Kuma einloggen, F12 →
  Anwendung → Local Storage → Wert von `token` kopieren.
- `monitor-ids` / `maintenance-ids` legen fest, welche Wartungen für diesen Server gelten (beide leer = alle).
- Ist Kuma nicht erreichbar, bleibt die zuletzt bekannte Planung gültig (`keep-schedule-when-offline`). Das Team
  bekommt einen Hinweis, und MRadmin verbindet sich automatisch neu.
- Status, Verbindung und letzte Abfrage zeigt `/mradmin maintenance`.

Unterstützt werden alle Wartungsarten von Kuma: einmalig, wiederkehrend, Cron und manuell. Eine manuelle Wartung
läuft, bis sie in Kuma beendet wird; das Ende zeigt MRadmin als „offen“.

### Ohne Uptime Kuma
| Befehl | |
|---|---|
| `/mradmin maintenance` | Status |
| `/mradmin maintenance on [dauer] [titel]` | sofort starten (ohne Dauer bis `off`) |
| `/mradmin maintenance schedule <in> [dauer] [titel]` | mit Countdown planen, z.B. `schedule 15m 1h Update` |
| `/mradmin maintenance extend <dauer>` | Ende verschieben, z.B. `30m` oder `-15m` |
| `/mradmin maintenance off` | beenden/absagen. Eine Kuma-Wartung bleibt aus, bis sie in Kuma neue Zeiten bekommt |
| `/mradmin maintenance refresh` | Wartungen sofort von Kuma abfragen |

Ohne LuckPerms prüft MRadmin die Rechte erst beim Join und kickt dann sofort. Die Join-Nachricht wird dabei
unterdrückt.

---

## Ankündigungen
```
/mradmin announce Heute Abend Event am Spawn!
/mradmin announce Achtung | Der Nether wird gleich zurückgesetzt
/mradmin announce restart 5 Minuten
/mradmin announce event Bauwettbewerb um 20 Uhr
```
- Eigener Text: erscheint unter der Überschrift `announce-custom-title`. Mit `|` getrennt werden **Titel | Untertitel**
  gesetzt. MiniMessage ist erlaubt (`<red>`, `<bold>` …).
- **Presets** stehen in der Sprachdatei unter `announce-presets` (`title`, `subtitle`, `chat`). Werte nach dem
  Namen füllen `<arg1>`, `<arg2>` … bzw. `<args>` (alle zusammen). Eigene Presets einfach dazuschreiben.
- Anzeigedauer, Chat-Ausgabe und Ton stellst du in der Config unter `announce` ein.

**Restart-Script** (z.B. per RCON oder Konsole, z.B. mit `mcrcon`):
```bash
mcrcon -p "$RCON_PW" "mradmin announce restart 5 Minuten"; sleep 240
mcrcon -p "$RCON_PW" "mradmin announce restart 1 Minute";  sleep 60
mcrcon -p "$RCON_PW" "mradmin announce restart-now" "stop"
```

---

## Discord-Ankündigungen (Community)
Ein **eigener Webhook** für den öffentlichen Channel, getrennt vom Admin-Log (`discord`). Er ist bewusst sparsam:

| Ereignis | Discord |
|---|---|
| Wartung ist höchstens `announce-ahead` (24h) entfernt | **neuer Post** „Wartungsarbeiten geplant“ (pingt optional `ping-role-id`) |
| verschoben / verlängert / verkürzt | der letzte Post wird **bearbeitet** (mehrere Änderungen in `debounce-seconds` werden zusammengefasst) |
| abgesagt | der Post „geplant“ wird zu „abgesagt“ (durchgestrichen) |
| Beginn | **neuer Post** „Wartungsarbeiten laufen“ (`post-start`) |
| Ende | **neuer Post** „Server wieder online“ (`post-end`) |
| Ankündigung mit Preset | **Post**, wenn das Preset einen `discord`-Text hat; dasselbe Preset höchstens einmal pro `cooldown` |
| eigener Text | nur mit `-d`: `/mradmin announce -d Heute Abend Event!` |

- Zeiten sind Discord-Zeitstempel. Jeder sieht sie in seiner eigenen Zeitzone, dazu „in 2 Stunden“.
- `-q` schickt ein Preset nicht nach Discord: `/mradmin announce -q restart 5 Minuten`.
- Ist Uptime Kuma kurz nicht erreichbar, bleibt der Discord-Post unverändert. Es gibt also kein „abgesagt“
  und danach wieder „geplant“.
- Test: `/mradmin discord test announce`. Status: `/mradmin discord`.

---

## Admin-Dashboard
`/mradmin` (oder Rechtsklick mit dem Admin-Kompass) öffnet das Dashboard. Die Befehlsliste gibt es mit `/mradmin help`.
```
■ ■ ■ ■ [Du] ■ ■ ■ ■                                Kopf: Admin-Modus, Staff-Chat, Welt
■ [Spieler] ■ [Server] ■ [Wartung] ■ [Tickets] ■     Live-Kacheln, Klick öffnet
□ □ ▬ □ ▬ □ ▬ □ ▬ □                                  Statusleiste: grün / gelb / rot
■ ■ [Ankündigen] ■ [MRsleep] ■ [MRchunks] ■ ■        Werkzeuge und Plugins
■ ■ [Admin-Modus] ■ [Schließen] ■ [Staff-Chat] ■ ■   Schalter
```
- **Statusleiste:** Unter jeder Kachel zeigt eine Scheibe den Zustand, z.B. TPS (grün ab 19,5, gelb ab 15), Wartung
  (gelb = geplant/Countdown, rot = aktiv), Tickets (gelb = offen, rot = warten auf Antwort) und eingefrorene Spieler (gelb).
- **Ankündigen:** alle Presets als Buttons. Braucht ein Preset Werte (z.B. `restart`), fragt ein Dialog danach.
  Rechtsklick kündigt ohne Discord an. „Eigener Text“ öffnet einen Dialog für Titel, Untertitel und optional Discord.
- **Plugin-Buttons:** öffnen die Admin-Menüs anderer Plugins, mit Live-Infos, wenn das Plugin sie anbietet.
  MRsleep und MRchunks kommen automatisch, sobald sie installiert sind und du deren Permission hast (siehe
  [Zusammenarbeit](#zusammenarbeit-mit-mrsleep-und-mrchunks)). Eigene Buttons gehen über `admin-mode.menu.buttons`
  in der Config, z.B. für Chunky.
- **Schalter:** Admin-Modus an/aus und Staff-Chat an/aus.
- Alle Reihen sind symmetrisch zur Mitte. Fehlt eine Permission, rücken die übrigen Buttons zusammen.

## Admin-Modus
- `/mradmin mode on` oder der Schalter im Dashboard: Inventar (inkl. Rüstung und Zweithand), ausgewählter Slot, XP,
  Leben, Absorption, Hunger, Sättigung, Effekte, Feuer, Luft, Flugstatus und Position werden gespeichert. Danach
  kommst du in den Creative-Modus und bekommst nur den **Admin-Kompass**. Rechtsklick öffnet das Dashboard.
- `/mradmin mode off`: alles wird exakt wiederhergestellt, und du landest wieder am Ausgangspunkt.
- Der Stand wird **vor** jeder Änderung in `plugins/MRadmin/admin-mode/<uuid>.yml` geschrieben. Bei einem Absturz
  oder Neustart bist du danach einfach noch im Admin-Modus, es geht nichts verloren.
- Im Admin-Modus kann man nichts fallen lassen oder aufheben (`prevent-drop`, `prevent-pickup`), damit keine
  Creative-Items ins Survival gelangen. Beim Tod bleibt alles erhalten.
- `end-on-quit: true` beendet den Admin-Modus beim Verlassen des Servers. Standardmäßig bleibt er aktiv.
- `/mradmin mode list` zeigt, wer gerade im Admin-Modus ist. `/mradmin mode off <spieler>` beendet ihn für andere.

---

## Spieler-Menü, Serverzustand, Tickets, Staff-Chat

### Spieler-Menü
Über Kompass → **Spieler teleportieren** → Klick auf einen Kopf (Shift-Klick teleportiert direkt) oder
`/mradmin player <spieler>`:
- **Infos:** Welt und Position, Spielmodus, Leben, Hunger, Level, Ping, Spielzeit, erster Join, eingefroren bzw. im Admin-Modus
- **Hinteleportieren**, **zu mir holen**
- **Inventar** (mit Rüstung und Zweithand) und **Enderchest** ansehen, live und **nur lesend**. Im Admin-Modus ist
  dein Inventar nur geliehen, herausgenommene Items wären beim Beenden weg.
- **Einfrieren / Auftauen:** Der Spieler kann sich nicht bewegen, bauen, kämpfen oder etwas benutzen und kann auch
  keinen Schaden nehmen. Umschauen geht weiter. Ausloggen hilft nicht: Er bleibt eingefroren, und das Team bekommt
  einen Hinweis.
- **Reports** über diesen Spieler
- **MRsleep / MRchunks:** die Infos des Spielers aus dem jeweiligen Plugin, ein Klick öffnet dessen Spieler-Menü.
  „Zurück“ führt wieder zu MRadmin. Dafür braucht es die MRsleep- bzw. MRchunks-Version mit MRadmin-Anbindung;
  ältere Versionen funktionieren weiter, nur ohne diese Infos.

### Serverzustand
Dashboard → **Server** oder `/mradmin status`: TPS (1, 5 und 15 min), MSPT, RAM, pro Welt geladene Chunks,
Entities und Spieler. Darunter stehen:
- mit **MRchunks** (ab der Version mit Lag-Schutz): die **auffälligen und gesperrten Chunkloader** samt Messwerten.
  Ein Klick öffnet das Menü des Loaders mit Teleport und „Lag-Sperre aufheben“, „Zurück“ führt wieder hierher.
- ohne MRchunks: die **21 Chunks mit den meisten Entities** (Lag-Quellen) samt häufigster Typen. Ein Klick
  teleportiert zum Chunk.

### Tickets & Reports
- `/report` (auch `/ticket`, `/support`) öffnet für alle Spieler das **Ticket-Menü**: **Ticket erstellen**, darunter
  die offenen Tickets (neue Antworten leuchten) und das **Archiv** mit den geschlossenen.
- **Ticket erstellen:** erst die Kategorie wählen (ein Knopf pro Kategorie), dann das **Formular** (Minecraft-Dialog).
  Bei Spieler-Reports wird vorher der Spieler gewählt (Köpfe der Online-Spieler oder Name eingeben, auch offline),
  sonst gibt es Titel und Beschreibung. Der Standort wird mitgespeichert. `/report <spieler> [text]` springt direkt
  zur Auswahl des Grundes.
- **Kategorien** legst du in der Config unter `tickets.categories` an: Name, `player: true` für Spieler-Reports,
  optional eine Permission. Standard: Cheating, Chat, Griefing, Betrug (Spieler-Reports), Bug, Frage/Sonstiges.
- Im **Ticket** stehen oben die Details, darunter der Verlauf wie in einem Chat (Kopf, Name, Uhrzeit, Text) und ein
  Antwortfeld. Wer gerade nicht online ist, erfährt beim nächsten Join von neuen Antworten. Klickbare
  **[Öffnen]**-Links im Chat öffnen das Ticket direkt (`/report #<id>`).
- Das Team bekommt neue Tickets mit **[Öffnen] [Teleport] [Alle Tickets]**, beim Join die Zahl offener Tickets.
  Dashboard → **Tickets** oder `/mradmin tickets`: Filter nach Kategorie und offen/geschlossen, Linksklick öffnet das
  Ticket, Rechtsklick teleportiert. Im Ticket: antworten, **übernehmen**/freigeben, teleportieren, schließen,
  wieder öffnen. Wer zuerst antwortet, übernimmt das Ticket automatisch (abschaltbar).
- Config `tickets`: Cooldowns, Höchstzahl offener Tickets, Textlängen, Kategorien, Symbol, Team-Namen anzeigen oder
  nur „Team“, Ton. Discord-Events `ticket`, `ticket-message` (aus), `ticket-closed`, `ticket-reopened`.
- Bestehende Reports aus `reports.yml` werden beim ersten Start automatisch zu Tickets (Backup
  `reports.migrated.yml`).
- **Mit MRchunks:** Wird ein Chunkloader wegen Lag gesperrt, öffnet MRadmin automatisch ein Ticket für den Owner
  (Kategorie „Chunkloader-Sperre“) mit allen Details der Sperre: Messwerte, Grenzen, Ort, Zeit, Server-TPS. Der
  Owner bekommt einen Link im Chat (offline beim nächsten Join) und kann im Ticket erklären, was passiert ist.
  **Schließen kann es nur das Team.** Im Ticket öffnet
  **Chunkloader-Menü** das Menü des Loaders (Teleport, an/aus, Grenze, Lag-Sperre aufheben). Wird der Loader erneut gesperrt oder entsperrt, auch außerhalb
  des Tickets, steht das im selben Ticket. Abschaltbar mit `tickets.chunkloader-locks: false`.

### Staff-Chat
`/sc <nachricht>` schreibt an alle mit `mradmin.staffchat`, auch von der Konsole. `/sc` ohne Text schaltet um:
Dann geht dein normaler Chat ans Team. Das Spiegeln in den Admin-Discord ist optional (Event `staff-chat`, aus).

---

## Permissions
Jede Funktion hat eine eigene Permission, und sie gilt **ingame und in der Weboberfläche gleich**. Wer ingame Tickets
nur ansehen darf, darf das im Web auch nur. Menüs und Web zeigen nur, was man darf.

**Pakete** zum einfachen Vergeben:

| Paket | enthält |
|---|---|
| `mradmin.support` | Dashboard, Tickets (ansehen, antworten, übernehmen, schließen, wieder öffnen, Hinweise), Spieler ansehen, Staff-Chat, Weboberfläche |
| `mradmin.staff` | Support + Ticket-Teleport, Wartung ansehen/joinen/Hinweise, Admin-Modus, Spieler-Werkzeuge (Teleport, Holen, Inventar, Einfrieren, Kicken), Serverzustand und Health |
| `mradmin.admin` | alles außer der Web-Konsole: Staff + Wartung steuern, Ankündigungen, Admin-Modus anderer, Web-Protokoll und -Konten, Reload, Speicher, Discord, Update-Hinweis |

**Einzelne Permissions** (Standard `op`, außer anders angegeben):

| Bereich | Permissions |
|---|---|
| Dashboard | `mradmin.dashboard` · `mradmin.status` · `mradmin.status.health` |
| Tickets | `mradmin.tickets.create` (**alle**, `/report`) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` |
| Wartung | `mradmin.maintenance.view` · `.manage` · `.bypass` · `.notify` |
| Ankündigungen, Modus, Chat | `mradmin.announce` · `mradmin.mode` (enthält `mradmin.dashboard`) · `mradmin.mode.others` · `mradmin.staffchat` |
| Spieler | `mradmin.player.view` · `.teleport` · `.summon` · `.inventory` · `.freeze` · `.kick` |
| Weboberfläche | `mradmin.web` · `mradmin.web.audit` · `mradmin.web.manage` · `mradmin.web.console.view` · `mradmin.web.console.execute` (beide **nicht** in Paketen, Standard: niemand) |
| Verwaltung | `mradmin.admin.reload` · `.storage` · `.discord` · `.update` |

Die Permissions von MRchunks und MRsleep (z.B. `mrchunks.admin.manage`) gelten im Web ebenso für deren Bereiche.

Beispiel LuckPerms:
```
/lp group supporter permission set mradmin.support true
/lp group staff permission set mradmin.staff true
/lp group admin permission set mradmin.admin true
/lp user <name> permission set mradmin.web.console.execute true   # Konsole im Web nur gezielt
```

---

## Dateien
| Datei | Inhalt |
|---|---|
| `config.yml` | alle Einstellungen (wird bei Updates automatisch migriert, Backup `config.v<N>.backup.yml`) |
| `lang/de.yml`, `lang/en.yml` | alle Texte, Presets, Discord-Texte |
| `maintenance.yml` | Zustand der Wartung (manuelle Wartung, letzter Stand aus Kuma), wird vom Plugin verwaltet |
| `discord-announce.yml` | welche Wartung zuletzt im Community-Discord gepostet wurde (für Bearbeitungen), wird vom Plugin verwaltet |
| `tickets.yml` | Tickets mit Verlauf (geschlossene werden nach `tickets.keep-closed` gelöscht) |
| `frozen.yml` | eingefrorene Spieler |
| `admin-mode/<uuid>.yml` | gespeicherter Stand von Spielern im Admin-Modus, **nicht löschen**, solange sie im Admin-Modus sind |
| `mradmin.db` (+ `-wal`, `-shm`) | nur mit `storage.type: sqlite`: statt `tickets.yml`, `maintenance.yml`, `frozen.yml`, `discord-announce.yml` |

## Bauen
```
./gradlew build      # JAR in build/libs/, inkl. Unit-Tests
```
Der Build baut auch die Weboberfläche (`web/`, Svelte). Node lädt Gradle dafür selbst herunter. Zum Entwickeln
der Oberfläche: in `web/` `npm run dev` (leitet `/api` an ein laufendes MRadmin auf Port 8125 weiter).
Versionen & Releases: [docs/RELEASES.md](docs/RELEASES.md). Lizenz: [LICENSE](LICENSE) (Nutzung und Änderung
erlaubt, Weiterveröffentlichung nicht).
