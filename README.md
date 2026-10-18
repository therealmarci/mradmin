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
■ [Spieler] ■ [Server] ■ [Wartung] ■ [Reports] ■     Live-Kacheln, Klick öffnet
□ □ ▬ □ ▬ □ ▬ □ ▬ □                                  Statusleiste: grün / gelb / rot
■ ■ [Ankündigen] ■ [MRsleep] ■ [MRchunks] ■ ■        Werkzeuge und Plugins
■ ■ [Admin-Modus] ■ [Schließen] ■ [Staff-Chat] ■ ■   Schalter
```
- **Statusleiste:** Unter jeder Kachel zeigt eine Scheibe den Zustand, z.B. TPS (grün ab 19,5, gelb ab 15), Wartung
  (gelb = geplant/Countdown, rot = aktiv), Reports (rot = offen) und eingefrorene Spieler (gelb).
- **Ankündigen:** alle Presets als Buttons. Braucht ein Preset Werte (z.B. `restart`), fragt ein Dialog danach.
  Rechtsklick kündigt ohne Discord an. „Eigener Text“ öffnet einen Dialog für Titel, Untertitel und optional Discord.
- **Plugin-Buttons:** öffnen die Admin-Menüs anderer Plugins, mit Live-Infos, wenn das Plugin sie anbietet.
  Mitgeliefert sind MRsleep und MRchunks, sie erscheinen nur, wenn das Plugin installiert ist und du dessen
  Permission hast. Eigene Buttons gehen über `admin-mode.menu.buttons` in der Config, z.B. für Chunky.
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

## Spieler-Menü, Serverzustand, Reports, Staff-Chat

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

### Reports
- `/report <spieler> <grund>` für alle Spieler, mit Cooldown und Höchstzahl offener Reports pro Spieler
  (Config `reports`).
- Das Team bekommt eine Meldung mit **[Teleport]** und **[Alle Reports]**, beim Join auch die Zahl offener Reports,
  dazu ein Post im Admin-Discord.
- Kompass → **Reports** oder `/mradmin reports`: Linksklick teleportiert zum Spieler (offline: zum Ort des
  Reports), Rechtsklick markiert ihn als erledigt. Der Melder erfährt das, wenn er online ist.

### Staff-Chat
`/sc <nachricht>` schreibt an alle mit `mradmin.staffchat`, auch von der Konsole. `/sc` ohne Text schaltet um:
Dann geht dein normaler Chat ans Team. Das Spiegeln in den Admin-Discord ist optional (Event `staff-chat`, aus).

---

## Permissions
| Permission | Standard | Wofür |
|---|---|---|
| `mradmin.staff` | op | Sammel-Permission fürs Team: Wartungs-Bypass und -Hinweise, Admin-Modus, Teleport, Spieler-Menü, Serverzustand, Reports, Staff-Chat |
| `mradmin.maintenance.bypass` | op | während der Wartung joinen, wird nicht gekickt |
| `mradmin.maintenance.notify` | op | Hinweise (z.B. Kuma-Verbindung verloren) |
| `mradmin.maintenance.manage` | op | `/mradmin maintenance …` |
| `mradmin.announce` | op | `/mradmin announce …` |
| `mradmin.mode` | op | `/mradmin mode` für sich selbst |
| `mradmin.mode.teleport` | op | Teleport-Kompass benutzen |
| `mradmin.mode.others` | op | Admin-Modus anderer steuern |
| `mradmin.player.summon` / `.inventory` / `.freeze` | op | Spieler holen, Inventar/Enderchest ansehen, einfrieren |
| `mradmin.status` | op | `/mradmin status` (Serverzustand) |
| `mradmin.report` | **alle** | `/report` |
| `mradmin.report.notify` / `.manage` | op | Meldung bei Reports / Reports bearbeiten (ohne Cooldown) |
| `mradmin.staffchat` | op | `/sc` |
| `mradmin.admin.discord` / `.reload` / `.update` | op | Discord-Status und -Test (beide Webhooks), Reload, Update-Hinweis |
| `mradmin.admin` | op | alles |

Beispiel LuckPerms:
```
/lp group staff permission set mradmin.staff true
/lp group admin permission set mradmin.admin true
```

---

## Dateien
| Datei | Inhalt |
|---|---|
| `config.yml` | alle Einstellungen (wird bei Updates automatisch migriert, Backup `config.v<N>.backup.yml`) |
| `lang/de.yml`, `lang/en.yml` | alle Texte, Presets, Discord-Texte |
| `maintenance.yml` | Zustand der Wartung (manuelle Wartung, letzter Stand aus Kuma), wird vom Plugin verwaltet |
| `discord-announce.yml` | welche Wartung zuletzt im Community-Discord gepostet wurde (für Bearbeitungen), wird vom Plugin verwaltet |
| `reports.yml` | Reports (erledigte werden nach `reports.keep-closed` gelöscht) |
| `frozen.yml` | eingefrorene Spieler |
| `admin-mode/<uuid>.yml` | gespeicherter Stand von Spielern im Admin-Modus, **nicht löschen**, solange sie im Admin-Modus sind |

## Bauen
```
./gradlew build      # JAR in build/libs/, inkl. Unit-Tests
```
Versionen & Releases: [docs/RELEASES.md](docs/RELEASES.md). Lizenz: [LICENSE](LICENSE) (Nutzung und Änderung
erlaubt, Weiterveröffentlichung nicht).
