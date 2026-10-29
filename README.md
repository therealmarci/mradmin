# MRadmin

**Der Werkzeugkasten für dein Server-Team:** Wartungen mit Countdown, Tickets und Reports, Admin-Modus, Spieler-Werkzeuge, Ankündigungen, Staff-Chat - im Spiel über Menüs und vom Handy über eine eigene Weboberfläche. Außerdem verbindet MRadmin alle anderen MRtechnology-Plugins miteinander.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mradmin/)** · **[📖 Anleitung online](https://mrtechnology.marcelr.at/docs/mradmin/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mradmin/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 |
| Benötigt | nichts - LuckPerms empfohlen, Uptime Kuma und LibertyBans optional |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | Vorabversion (0.x) - Config, Befehle und Permissions können sich bis 1.0.0 noch ändern |

## Was kann MRadmin?
- **Wartungsarbeiten:** Während einer Wartung kommt nur das Team auf den Server. Geplante Wartungen kommen auf Wunsch live aus **Uptime Kuma**, vorher läuft eine **Bossbar mit Countdown**.
- **Tickets & Reports:** Spieler melden Regelverstöße, Bugs und Fragen mit `/report` über ein Menü. Das Team antwortet im Spiel oder im Web - wie in einem Chat.
- **Admin-Modus:** Creative und ein Teleport-Kompass. Beim Beenden ist alles wieder exakt wie vorher - Inventar, XP, Leben, Position.
- **Spieler-Werkzeuge:** hinteleportieren, herholen, Inventar und Enderchest ansehen, einfrieren, kicken.
- **Ankündigungen** auf dem Bildschirm aller Spieler, mit Vorlagen (z.B. fürs Restart-Script) und auf Wunsch im Community-Discord.
- **Admin-Dashboard** mit Live-Kacheln, **Serverzustand** und **Staff-Chat**.
- **Weboberfläche** fürs Handy: Tickets, Spieler, Serverzustand, Wartungen, Push-Benachrichtigungen - mit 2FA und Passkeys.
- **Spielerseite im Web (`/web`):** Jeder Spieler bekommt seine eigene Seite - eigene Tickets, Ranglisten und alles, was die anderen Plugins anbieten (z.B. Chunkloader verwalten, Duell-Statistik). Als Lesezeichen speicherbar, Deutsch und Englisch.
- **Versionen & Updates:** alle MRtechnology-Plugins mit ihren Updates im Web, Updates per Klick herunterladen (Installation beim nächsten Neustart) und den Server mit Countdown neu starten.
- **Discord-Logs**, **Update-Prüfung**, eine Permission für jede Funktion, jede Funktion einzeln abschaltbar.

## Schnellstart
1. **[MRadmin herunterladen](https://mrtechnology.marcelr.at/download/mradmin/)** und die JAR in den Ordner `plugins/` legen.
2. Den Server starten. Dabei entstehen `plugins/MRadmin/config.yml` und die Sprachdateien in `plugins/MRadmin/lang/`.
3. **Rechte geben** - es gibt fertige Gruppen (in allen MRtechnology-Plugins gleich aufgebaut):
   ```text
   /lp group default permission set mradmin.player true
   /lp group supporter permission set mradmin.groups.support true
   /lp group staff permission set mradmin.groups.staff true
   /lp group admin permission set mradmin.groups.admin true
   ```
4. Fertig: **`/mradmin`** öffnet das Dashboard, Spieler erstellen Tickets mit **`/report`**.

Alles andere ist optional: [Uptime Kuma](#uptime-kuma) für geplante Wartungen, ein [Discord-Webhook](#discord) für Logs, die [Weboberfläche](#weboberfläche) fürs Handy.

**Update:** Die alte JAR durch die neue ersetzen und den Server neu starten. Die Config wird automatisch angepasst: Deine Werte bleiben, neue Optionen kommen dazu, die alte Datei wird als `config.v<N>.backup.yml` gesichert.

### Funktionen ein- und ausschalten
Alles, was du nicht brauchst, schaltest du unter `features` in der Config ab (`/mradmin reload` genügt):

```yaml
features:
  maintenance: true    # Wartungsmodus inkl. Uptime Kuma
  announce: true       # Ankündigungen
  admin-mode: true     # Admin-Modus und Kompass
  teleport: true       # zu Spielern teleportieren
  summon: true         # Spieler herholen
  inventory: true      # Inventar/Enderchest ansehen
  freeze: true         # Einfrieren
  server-status: true  # Serverzustand
  tickets: true        # Tickets & Reports (/report)
  staff-chat: true     # Staff-Chat (/sc)
```

Eine ausgeschaltete Funktion verschwindet komplett aus Befehlen, Menüs und Web. Ihre Daten (Tickets, geplante Wartungen) bleiben erhalten.

---

## Admin-Dashboard
**`/mradmin`** (oder Rechtsklick mit dem Admin-Kompass) öffnet das Dashboard:
- **Live-Kacheln** für Spieler, Server, Wartung und Tickets. Darunter zeigt eine farbige Scheibe den Zustand, z.B. TPS (grün ab 19,5, gelb ab 15), Wartung (gelb = geplant, rot = aktiv), Tickets (rot = warten auf Antwort).
- **Ankündigen:** alle Vorlagen als Knöpfe, „Eigener Text“ öffnet einen Dialog.
- **Plugin-Knöpfe:** Admin-Menüs der anderen MRtechnology-Plugins mit Live-Infos. Eigene Knöpfe (z.B. für Chunky) gehen über `admin-mode.menu.buttons`.
- **Schalter** für Admin-Modus und Staff-Chat.

Fehlt eine Permission, fehlt einfach der Knopf. Alle Befehle zeigt `/mradmin help`.

## Admin-Modus
- **`/mradmin mode on`** (oder der Schalter im Dashboard): Inventar, Rüstung, XP, Leben, Hunger, Effekte, Flugstatus und Position werden gespeichert. Danach bist du im Creative-Modus mit dem **Admin-Kompass**.
- **`/mradmin mode off`:** Alles wird exakt wiederhergestellt, und du landest wieder am Ausgangspunkt.
- Gespeichert wird **vor** jeder Änderung - auch ein Absturz verliert nichts. Du bist danach einfach noch im Admin-Modus.
- Im Admin-Modus kann man nichts fallen lassen oder aufheben, damit keine Creative-Items ins Survival gelangen.
- `/mradmin mode list` zeigt, wer im Admin-Modus ist. `/mradmin mode off <spieler>` beendet ihn für andere.

## Spieler-Menü & Serverzustand
**Spieler-Menü** (Kompass → Spieler teleportieren → Kopf anklicken, oder `/mradmin player <spieler>`):
- Infos: Position, Spielmodus, Leben, Ping, Spielzeit, erster Join
- **Hinteleportieren** und **zu mir holen** (Shift-Klick auf einen Kopf teleportiert direkt)
- **Inventar** und **Enderchest** ansehen - live und nur lesend
- **Einfrieren:** Der Spieler kann sich nicht bewegen, bauen oder kämpfen und nimmt keinen Schaden. Ausloggen hilft nicht.
- Seine Reports und die Infos aus den anderen MRtechnology-Plugins

**Serverzustand** (Dashboard → Server oder `/mradmin status`): TPS, MSPT, RAM, Chunks, Entities und Spieler pro Welt. Darunter mit MRchunks die auffälligen und gesperrten Chunkloader, ohne MRchunks die Chunks mit den meisten Entities - ein Klick teleportiert hin.

## Tickets & Reports
- **`/report`** (auch `/ticket`, `/support`) öffnet für alle Spieler das **Ticket-Menü**: neues Ticket, offene Tickets (neue Antworten leuchten) und das Archiv.
- **Neues Ticket:** Kategorie wählen, dann ein Formular ausfüllen. Bei Spieler-Reports wird erst der Spieler gewählt. `/report <spieler>` springt direkt zur Auswahl des Grundes. Der Standort wird mitgespeichert.
- **Kategorien** legst du unter `tickets.categories` an. Standard: Cheating, Chat, Griefing, Betrug, Bug, Frage/Sonstiges.
- Im Ticket steht der Verlauf wie in einem Chat. Wer offline ist, erfährt beim nächsten Join von neuen Antworten.
- Das **Team** bekommt neue Tickets mit **[Öffnen] [Teleport] [Alle Tickets]**. Unter Dashboard → Tickets oder `/mradmin tickets`: filtern, antworten, **übernehmen**, schließen, wieder öffnen. Wer zuerst antwortet, übernimmt das Ticket automatisch.
- **Automatische Tickets von Plugins:** Meldet ein Plugin ein Problem eines Spielers, bekommt er automatisch ein Ticket - z.B. **mit MRchunks**, wenn sein Chunkloader wegen Lag gesperrt wird, mit allen Messwerten. Schließen kann es nur das Team. Abschalten mit `tickets.plugin-reports: false`.

## Staff-Chat
`/sc <nachricht>` schreibt an alle mit `mradmin.staffchat`, auch aus der Konsole. `/sc` ohne Text schaltet um: Dann geht dein normaler Chat ans Team. Spiegeln in den Admin-Discord ist optional (Event `staff-chat`).

---

## Wartungsarbeiten
| Zeitpunkt | Was passiert |
|---|---|
| 15 Minuten vorher (`warn-before`) | Chat-Ankündigung und **Bossbar mit Countdown** für alle |
| 15m, 10m, 5m, 2m, 1m, 30s vorher | Chat-Erinnerung mit Ton |
| letzte 10 Sekunden | großer Countdown auf dem Bildschirm |
| Beginn | Titel „Wartungsarbeiten“, kurz danach werden alle ohne Bypass gekickt |
| während der Wartung | Join nur mit `mradmin.maintenance.bypass`, die Serverliste zeigt „Wartung“ |
| Ende | Chat-Nachricht, alle dürfen wieder joinen |

### Ohne Uptime Kuma
| Befehl | Was er tut |
|---|---|
| `/mradmin maintenance` | Status |
| `/mradmin maintenance on [dauer] [titel]` | Sofort starten (ohne Dauer bis `off`) |
| `/mradmin maintenance schedule <in> [dauer] [titel]` | Mit Countdown planen, z.B. `schedule 15m 1h Update` |
| `/mradmin maintenance extend <dauer>` | Ende verschieben, z.B. `30m` oder `-15m` |
| `/mradmin maintenance off` | Beenden oder absagen |

### Uptime Kuma
Wartungen, die du in [Uptime Kuma](https://github.com/louislam/uptime-kuma) planst, übernimmt MRadmin live - inklusive Countdown. Wird eine Wartung in Kuma **verlängert, verschoben oder abgesagt**, passt sich alles sofort an, und die Spieler werden informiert.

```yaml
uptime-kuma:
  enabled: true
  url: "https://status.deinserver.de"
  username: "admin"
  password: "geheim"
  monitor-ids: [12]      # nur Wartungen, die den Minecraft-Monitor (ID 12) betreffen
```

- **2FA in Kuma aktiv?** Dann statt Passwort den Login-Token eintragen (`token`): im Browser bei Kuma einloggen, F12 → Anwendung → Local Storage → Wert von `token` kopieren.
- `monitor-ids` / `maintenance-ids` legen fest, welche Wartungen für diesen Server gelten (beide leer = alle).
- Ist Kuma nicht erreichbar, bleibt die zuletzt bekannte Planung gültig, und MRadmin verbindet sich automatisch neu.
- Unterstützt werden alle Wartungsarten von Kuma: einmalig, wiederkehrend, Cron und manuell. `/mradmin maintenance refresh` fragt sofort neu ab.

## Ankündigungen
```text
/mradmin announce Heute Abend Event am Spawn!
/mradmin announce Achtung | Der Nether wird gleich zurückgesetzt
/mradmin announce restart 5 Minuten
```
- **Eigener Text** erscheint als Titel auf dem Bildschirm. Mit `|` trennst du **Titel | Untertitel**. Farben im [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html) sind erlaubt.
- **Vorlagen** (z.B. `restart`, `event`) stehen in der Sprachdatei unter `announce-presets`. Werte nach dem Namen füllen `<arg1>`, `<arg2>` … Eigene Vorlagen schreibst du einfach dazu.

<details>
<summary>Beispiel: Restart-Script mit Countdown</summary>

```bash
mcrcon -p "$RCON_PW" "mradmin announce restart 5 Minuten"; sleep 240
mcrcon -p "$RCON_PW" "mradmin announce restart 1 Minute";  sleep 60
mcrcon -p "$RCON_PW" "mradmin announce restart-now" "stop"
```
</details>

### Ankündigungen im Community-Discord
Ein **eigener Webhook** für deinen öffentlichen Channel (`discord-announce`), getrennt vom Admin-Log. Er postet sparsam:
- **Eine Nachricht pro Wartung**, höchstens 24 Stunden vorher (`announce-ahead`). Sie wird mitgeführt statt neu gepostet: **„Wartungsarbeiten geplant“** → **„Wartungsarbeiten laufen“** → **„Server wieder online“** mit der tatsächlichen Zeit (bei vorzeitigem Ende mit Hinweis) oder **„Wartung abgesagt“**. Neue Zeiten werden in dieselbe Nachricht eingetragen. Die Rolle aus `ping-role-id` wird nur beim ersten Post erwähnt.
- **Ankündigungen**, wenn die Vorlage einen `discord`-Text hat. Eigene Texte nur mit `-d`: `/mradmin announce -d Heute Abend Event!`. Mit `-q` geht eine Vorlage nicht nach Discord.

Zeiten erscheinen als Discord-Zeitstempel - jeder sieht sie in seiner Zeitzone. Test: `/mradmin discord test announce`.

> [!NOTE]
> Discord benachrichtigt bei bearbeiteten Nachrichten nicht. Beginn und Ende einer Wartung sieht also, wer in den Channel schaut - der Ping kommt nur, wenn die Wartung zum ersten Mal gepostet wird.

---

## Weboberfläche
MRadmin vom Handy, Tablet oder PC bedienen: Übersicht mit Live-Werten, Tickets, Spieler, Serverzustand, Wartungen und Ankündigungen, Push-Benachrichtigungen, dazu Seiten für Chunkloader, Schlaf und Duelle, wenn die Plugins installiert sind. Im Web gelten **dieselben Rechte** wie im Spiel.

1. In der Config `web.enabled: true` und `web.public-url` auf deine Adresse setzen (z.B. `https://admin.deinserver.de`), dann `/mradmin reload`.
2. **Reverse Proxy** einrichten: deine Domain mit HTTPS → `http://127.0.0.1:8125`.
3. Im Spiel **`/mradmin web setup`** → Link öffnen → Passwort festlegen und Authenticator-App verbinden. Fürs Team: `/mradmin web invite <spieler>`.

Anmeldung immer mit Passwort **und** Code aus der Authenticator-App - oder per Passkey (Fingerabdruck, Face ID, Windows Hello) oder Discord. Alle Funktionen, Sicherheitsdetails, die Web-Konsole, Entbannungsanträge und das Kontaktformular: **[Weboberfläche im Detail](docs/WEBOBERFLAECHE.md)**.

**Items wie im Spiel:** Inventare und Kits zeigt die Weboberfläche mit den echten Item-Bildern und -Namen samt Tooltip. Dafür lädt MRadmin beim ersten Start **einmal** den offiziellen Spiel-Client von Mojang (rund 40 MB) und speichert daraus nur die fertigen Bilder unter `plugins/MRadmin/items/`. Abschalten mit `web.item-icons: false` - dann stehen Kürzel statt Bildern da.

### Spielerseite (`/web`)
Neben dem Team-Bereich hat jeder **Spieler** seine eigene Seite in der Weboberfläche. Standardmäßig ist sie aus.

1. Die Weboberfläche muss laufen (`web.enabled`, `web.public-url`, siehe oben).
2. In der Config `player-web.enabled: true`, dann `/mradmin reload`.
3. Spieler brauchen `mradmin.web.self` - das steckt in `mradmin.player`.

**Für Spieler:** Die Seite hat immer dieselbe Adresse (`<deine Adresse>/me`) - am besten als **Lesezeichen** speichern. Zum Anmelden fragt sie nach einem **6-stelligen Code**: `/web` im Spiel eingeben, der Chat zeigt den Link zur Seite und den Code (ein Klick auf den Code kopiert ihn). Der Code gilt 2 Minuten und nur einmal (`player-web.code-seconds`). Danach bleibt das Gerät **höchstens 14 Tage** angemeldet (`player-web.session-days`) und wird schon nach **7 Tagen ohne Besuch** abgemeldet (`player-web.idle-days`) - dann fragt die Seite einfach wieder nach einem Code. `/web devices` zeigt, auf wie vielen Geräten man angemeldet ist, `/web logout` meldet alle ab - einzelne Geräte gehen auch auf der Seite unter **Geräte** (Klick auf den eigenen Namen oben rechts), dort steht auch, wie lange die Anmeldung noch gilt.

Was auf der Seite steht, hängt davon ab, was installiert ist:

| Bereich | Was der Spieler sieht und tut | Braucht |
|---|---|---|
| **Tickets** | eigene Tickets ansehen, neue öffnen, antworten, schließen - wie mit `/report` | `features.tickets` und `mradmin.tickets.create` |
| **Neuigkeiten** | eine Seite mit zwei Bereichen: **Neuigkeiten** (die neueste groß, die anderen als Karten, Lesen im Fenster) und **Changelog** (Zeitleiste der Versionen mit Neu / Geändert / Behoben / Entfernt); aktive **Warnungen** stehen rot ganz oben. Auf der Startseite ein Karussell, das von selbst weiterblättert: Warnungen, Wartung, Neustart, Warnungen der Plugins, Ticket-Antworten und die neuesten Beiträge. | `player-web.news.enabled` |
| **Meine Statistik** | Kachel auf der Startseite neben denen der Plugins: eigene Spielzeit, abgebaute Blöcke, besiegte Mobs, Tode (Auswahl und Reihenfolge unter `player-web.personal-stats`, die erste groß). Führt zur Statistik-Seite. | `player-web.personal-stats` nicht leer |
| **Statistik** | zwei Reiter: **Persönlich** (jeder eigene Wert mit Platz, Medaille für die Top 3 und Vergleich zum Besten) und **Server** (Summen aller Spieler, Ranglisten mit Siegertreppchen - Auswahl unter `player-web.rankings` - und von den Plugins). Wer nicht in Ranglisten stehen will, trägt sich dort aus. | `player-web.rankings.enabled` |
| **Chunkloader** | Kopf mit Budget und Warnungen; jeder Loader als Karte (Status, Größe, Auslastung, An/Aus-Knopf), ein Klick zeigt seine Grenzwerte wie in der Team-Ansicht, eine Live-Karte rund um ihn und alle Aktionen (umbenennen, Dummy, löschen); Mitglieder mit Kopf und Entfernen-Knopf, Einladen per Formular (Spieler, für welchen Loader, Dauer); mit squaremap die Live-Karte. Größe nur im Spiel. | MRchunks |
| **Duelle** | eigene Duell-Statistik und letzte Runden, Duell-Ranglisten auf der Statistik-Seite | MRpvp |

Die Seite gibt es auf Deutsch und Englisch (Umschalter oben). Gesperrte Spieler (LibertyBans) kommen nicht hinein. Jedes Plugin prüft bei jeder Aktion dieselben Permissions und Regeln wie im Spiel.

**Neuigkeiten, Changelogs und Warnungen (Team):** In der Weboberfläche unter **Neuigkeiten** (Permission `mradmin.news`, in `mradmin.groups.admin`). **Neuigkeiten** mit Titel und Text, **Changelogs** mit Version und Einträgen (Neu, Geändert, Behoben, Entfernt), **kritische Warnungen**, die sofort an alle Online-Spieler gehen (Chat und Titel, `player-web.news.alerts`). Texte auf Deutsch und Englisch - eine leere Sprache zeigt die andere. Pro Beitrag optional **auch im Discord posten** (Kanal von `discord-announce`, mit Link zur ganzen Nachricht). Neue Neuigkeiten und Changelogs bekommt jeder Spieler einmal als **Vorschau im Chat** mit Link (`player-web.news.chat-preview`). Vorfälle, die auf einer **Uptime-Kuma-Statusseite** angeheftet sind (`uptime-kuma.status-pages`), erscheinen wie eigene Warnungen und im Discord (`discord-announce.kuma`). Karussell: `player-web.news` (`seconds`, `slides`, `show-maintenance`, `show-restart`, `show-plugin-notices`, `show-tickets`). Details in `docs/WEBOBERFLAECHE.md`.

### Versionen & Updates
In der Weboberfläche unter **Versionen** siehst du alle MRtechnology-Plugins auf deinem Server: installierte und neueste Version, was neu ist, und ob ein Update wartet.
- **Herunterladen** holt die neue Version aus dem offiziellen Release auf GitHub - nur für deine Minecraft-Version und nur, wenn die Prüfsumme (SHA-256) stimmt. Sie landet in `plugins/update/`, **installiert wird beim nächsten Neustart** (das macht Paper selbst). Im laufenden Betrieb wird nichts ausgetauscht. Der Dateiname bleibt dabei der alte (z.B. `MRsleep-mc26.3-v1.1.5.jar` mit Version 1.2.0 darin) - nur so weiß Paper, welches Plugin ersetzt wird. Welche Version läuft, zeigt die Seite Versionen bzw. `/<plugin> version`.
- **Automatisch:** `versions.auto-download: patch` lädt z.B. Hotfixes (1.2.x) von selbst, `minor` auch neue Funktionen, `all` alles. Standard ist `off`.
- **Server neu starten:** Nach dem Herunterladen zeigt die Seite „Neustart nötig“. Einen **Neustart-Knopf** mit Countdown (`versions.restart-delays`, Standard 5 Minuten, 1 Minute, sofort, angekündigt wie `/mradmin announce restart`) gibt es erst, wenn du `versions.restart-method` einstellst - denn ein Server kann sich nicht selbst wieder starten, das muss etwas außerhalb tun:
  - `none` (Standard): kein Knopf.
  - `stop`: Der Server stoppt, und **dein Start-Skript mit Schleife, ein Wächter oder dein Hoster-Panel** startet ihn wieder.
  - `script`: Der Server startet über das `restart-script` aus der `spigot.yml` neu (`settings.restart-script`).
- Downloads und Neustarts stehen im Web-Protokoll und im Discord-Log (`plugin-update-downloaded`, `server-restart`).

> [!WARNING]
> Stell `stop` oder `script` nur ein, wenn du sicher weißt, dass der Server danach wieder hochkommt - sonst stoppt der Knopf ihn nur. Viele Hoster-Panels starten nach einem normalen Stopp **nicht** von selbst neu, nur nach einem Absturz. Probier es einmal aus, bevor du den Knopf im Ernstfall benutzt.

<details>
<summary>Beispiel: Start-Skript mit Schleife (für <code>restart-method: stop</code>)</summary>

Das Skript startet den Server nach jedem Stopp nach 5 Sekunden wieder. Zum endgültigen Beenden in diesen 5 Sekunden `Strg+C` drücken.

**Linux** (`start.sh`, ausführbar machen mit `chmod +x start.sh`, z.B. in `screen` oder `tmux` starten):
```bash
#!/bin/sh
while true; do
  java -Xms4G -Xmx4G -jar paper.jar --nogui
  echo "Server stopped - restarting in 5 seconds (Ctrl+C to quit)"
  sleep 5
done
```

**Windows** (`start.bat`):
```bat
@echo off
:loop
java -Xms4G -Xmx4G -jar paper.jar --nogui
echo Server stopped - restarting in 5 seconds (Ctrl+C to quit)
timeout /t 5
goto loop
```
Den Speicher (`4G`) und den Namen der Paper-JAR an deinen Server anpassen.
</details>

---

## Zusammenarbeit mit anderen Plugins
MRadmin, MRsleep, MRchunks, MRtoolbar und MRpvp laufen jeweils **auch allein**. Sind sie zusammen installiert, erkennt MRadmin sie automatisch und passt Menüs und Web von selbst an:
- **Dashboard:** ein Knopf je Plugin mit Live-Infos.
- **Spieler-Menü:** die Infos des Spielers aus jedem Plugin.
- **Serverzustand:** auffällige und gesperrte Chunkloader (MRchunks), wartende Duell-Prüfungen (MRpvp).
- **Web:** eigene Seiten **Chunkloader**, **Schlaf** und **Duelle** fürs Team, auf der Spielerseite (`/web`) die Bereiche **Chunkloader** und **Duelle** für die Spieler selbst.

**MRadmin ist außerdem die Verbindung zwischen den anderen Plugins** - ohne MRadmin bekommen sie voneinander nichts mit:

| Mit MRadmin | Braucht außerdem |
|---|---|
| Spieler im Duell zählen nicht zur Schlafquote | MRsleep + MRpvp |
| Die Werkzeugleiste pausiert im Duell, die Gürtel werden mitgesichert | MRtoolbar + MRpvp |
| In der Arena-Welt lassen sich keine Chunkloader setzen | MRchunks + MRpvp |
| Spieler im Admin-Modus zählen nicht zur Schlafquote, haben die Werkzeugleiste pausiert und können kein Duell starten | MRsleep / MRtoolbar / MRpvp |

Einrichten musst du dafür nichts. Eine Verbindung abschalten geht unter `integrations` (z.B. `mrsleep: false`). Die Konsole zeigt beim Start, welche Plugins verbunden sind. Für Entwickler: [docs/MODULE-API.md](docs/MODULE-API.md).

**LibertyBans:** Ist es installiert, zeigt MRadmin Strafen im Spieler-Menü und im Web und ermöglicht Entbannungsanträge über die Weboberfläche.

---

## Permissions
Jede Funktion hat eine eigene Permission - **im Spiel und im Web gleich**. Am einfachsten vergibst du die **Gruppen**. Sie sind in allen MRtechnology-Plugins gleich aufgebaut (`<plugin>.player` und `<plugin>.groups.support|staff|admin`), jede Gruppe enthält die darunter. Standardmäßig haben sie nur OPs.

| Gruppe | Enthält |
|---|---|
| `mradmin.player` | `/report` - Tickets erstellen und die eigenen ansehen, beantworten, schließen · `/web` - eigene Spielerseite · `/mradmin version` |
| `mradmin.groups.support` | Spieler + Übersicht, Serverzustand und Health, Tickets komplett bearbeiten (auch Teleport zum Ticket), Spieler ansehen, zu Spielern teleportieren, Inventar ansehen, Staff-Chat, Weboberfläche, Wartung ansehen und währenddessen joinen, Versionen ansehen, Web-Konsole ansehen |
| `mradmin.groups.staff` | Support + Spieler herholen, einfrieren und kicken, Admin-Modus · mit LibertyBans: kicken, muten, verwarnen (und Mutes/Verwarnungen aufheben) |
| `mradmin.groups.admin` | Alles: Staff + Wartung steuern, Ankündigungen, Admin-Modus anderer, Web-Konten und -Protokoll, Reload, Speicher, Discord, Update-Hinweis, Updates herunterladen, Server neu starten, **Befehle in der Web-Konsole** · mit LibertyBans: bannen und entbannen (auch Entbannungsanträge entscheiden) |

Die Gruppen bringen auch die nötigen **LibertyBans**-Rechte mit (falls installiert): Support sieht Strafen, Verlauf und die Bann- und Mute-Listen (`libertybans.commands`, `libertybans.list.*`), Staff darf kicken, muten und verwarnen, nur Admins bannen. Diese Rechte gelten dann auch für die LibertyBans-Befehle im Spiel.

> [!WARNING]
> `mradmin.groups.admin` enthält das Ausführen von Befehlen in der Web-Konsole - so mächtig wie die Server-Konsole. Gib die Gruppe nur Leuten, denen du den Server anvertraust. Vor dem Öffnen fragt das Web zusätzlich den 2FA-Code ab.

**Alle MRtechnology-Plugins auf einmal einrichten:** [Permission-Übersicht mit fertigen LuckPerms-Gruppen](docs/PERMISSIONS.md) - was Support, Staff und Admin in jedem Plugin dürfen, zum Kopieren.

<details>
<summary>Alle einzelnen Permissions</summary>

Standard ist `op`, außer anders angegeben.

| Bereich | Permissions |
|---|---|
| Dashboard | `mradmin.dashboard` · `mradmin.status` · `mradmin.status.health` |
| Tickets | `mradmin.tickets.create` (`/report`) · `.notify` · `.view` · `.reply` · `.claim` · `.close` · `.reopen` · `.teleport` |
| Wartung | `mradmin.maintenance.view` · `.manage` · `.bypass` · `.notify` |
| Ankündigungen, Modus, Chat | `mradmin.announce` · `mradmin.news` (Neuigkeiten im Web) · `mradmin.mode` · `mradmin.mode.others` · `mradmin.staffchat` |
| Spieler | `mradmin.player.view` · `.teleport` · `.summon` · `.inventory` · `.freeze` · `.kick` |
| Weboberfläche | `mradmin.web` · `mradmin.web.audit` · `mradmin.web.manage` · `mradmin.web.console.view` · `mradmin.web.console.execute` (Konsole: ansehen in `groups.support`, ausführen in `groups.admin`) |
| Verwaltung | `mradmin.admin.reload` · `.storage` · `.discord` · `.update` |
| Versionen & Neustart (Web) | `mradmin.versions.view` (ansehen) · `mradmin.versions.install` (herunterladen) · `mradmin.server.restart` (neu starten) |

Die Permissions von MRsleep, MRchunks und MRpvp gelten im Web genauso für deren Bereiche.
</details>

> [!TIP]
> Einzelnen Leuten gibst du zusätzlich einzelne Rechte, z.B. `/lp user <name> permission set mradmin.announce true`. Aus einer Gruppe nimmst du eins heraus mit z.B. `/lp group staff permission set mradmin.player.kick false`.

## Konfiguration
Alles steht kommentiert in `plugins/MRadmin/config.yml`. Änderungen übernimmst du mit `/mradmin reload`.

| Abschnitt | Wofür |
|---|---|
| `features` · `integrations` | Funktionen und Plugin-Verbindungen an/aus |
| `maintenance` · `uptime-kuma` | Ablauf der Wartung, Anbindung an Uptime Kuma |
| `announce` · `discord-announce` | Ankündigungen im Spiel und im Community-Discord |
| `admin-mode` · `gui` | Admin-Modus, Kompass, eigene Dashboard-Knöpfe |
| `tickets` | Kategorien, Cooldowns, Limits, wie lange geschlossene Tickets bleiben |
| `discord` | Admin-Log per Webhook, welche Ereignisse gesendet werden |
| `storage` | Speicher: `yaml` (Standard), `sqlite` oder `mysql` |
| `web` · `mail` | Weboberfläche, Mails für Kontakt- und Entbannungsanfragen |
| `player-web` | Spielerseite (`/web`): an/aus, Anmeldung per Code (`session-days`, `idle-days`, `code-seconds`), Ranglisten, Karussell mit Neuigkeiten (`news`), eigene Statistik-Kachel (`personal-stats`) |
| `health` · `libertybans` · `update-checker` | Verlauf und TPS-Alarm, Strafen-Schnellauswahl, Update-Hinweise |
| `versions` | Seite Versionen: wie oft geprüft wird, automatische Downloads, Vorabversionen, wie der Neustart-Knopf neu startet |

**Sprache:** Alle Texte, Vorlagen und Discord-Nachrichten stehen in `plugins/MRadmin/lang/<sprache>.yml`. Neue Texte aus Updates werden automatisch ergänzt.
- **Jeder Spieler bekommt seine Sprache:** Mit `language: auto` (Standard) richtet sich MRadmin nach der Sprache, die der Spieler in Minecraft eingestellt hat - Deutsch (auch Österreich und Schweiz) bekommt Deutsch, Englisch bekommt Englisch. Für Sprachen ohne eigene Datei gilt `fallback-language` (Standard Englisch). Mit `language: de` oder `en` bekommen alle dieselbe Sprache.
- **Server-Sprache:** Discord-Logs und -Ankündigungen, die Serverliste während einer Wartung, Push-Benachrichtigungen, Mails und Notizen in Tickets haben keinen einzelnen Empfänger - sie kommen in `server-language` (Standard `en`). Auf einem deutschen Server also `server-language: de` setzen.
- Die Konsole ist immer Englisch.
- Ankündigungen, Wartungs-Countdown, Bossbars und Titel sieht jeder Spieler in seiner Sprache. Die Weboberfläche richtet sich nach der Sprache des Browsers - oder nach der Sprache, die man dort umgeschaltet hat.

### Speicher: YAML, SQLite oder MySQL
| `storage.type` | Wo | Wofür |
|---|---|---|
| `yaml` (Standard) | Dateien im Plugin-Ordner | kleine Server |
| `sqlite` | `plugins/MRadmin/mradmin.db` | viele Tickets, die Weboberfläche |
| `mysql` | MySQL oder MariaDB | mehrere Server mit gemeinsamen Tickets |

Umstellen: `type` ändern und den Server neu starten - die bisherigen Daten werden übernommen. Die Datenbank-Treiber lädt Paper beim ersten Start selbst herunter.

### Discord
Für das **Admin-Log** einen Webhook anlegen (*Kanal bearbeiten → Integrationen → Webhooks*), unter `discord` aktivieren und die URL eintragen. Welche Ereignisse gesendet werden (Tickets, Wartung, Admin-Modus, Web-Anmeldungen …), stellst du unter `discord.events` ein. Test: `/mradmin discord test`.

---

## Häufige Fragen
**Ein Teammitglied wird bei der Wartung trotzdem gekickt.**
Es braucht `mradmin.maintenance.bypass` (in `mradmin.groups.support` enthalten). Ohne LuckPerms prüft MRadmin die Rechte erst beim Join.

**Ich war im Admin-Modus, und der Server ist abgestürzt.**
Nichts verloren - du bist nach dem Neustart einfach noch im Admin-Modus. `/mradmin mode off` stellt alles wieder her. Die Dateien in `plugins/MRadmin/admin-mode/` bitte nicht löschen.

**In der Weboberfläche stehen bei Items nur Kürzel statt Bildern.**
MRadmin konnte die Bilder nicht von Mojang laden (die Konsole sagt beim Start, warum - meist kein Internet am Server) oder `web.item-icons` steht auf `false`. Nach dem Beheben `/mradmin reload` - geladen wird nur einmal pro Minecraft-Version.

**Wo sehe ich, welche Plugins verbunden sind?**
In der Konsole beim Start. Ist ein Plugin zu alt für die Verbindung, steht dort eine Warnung - dann alle MRtechnology-Plugins zusammen aktualisieren.

**Ich habe einen Fehler gefunden oder einen Wunsch.**
Gern über das **[Formular auf der Website](https://mrtechnology.marcelr.at/report/mradmin/)** - am besten mit der Ausgabe von `/mradmin version`.

---

## Lizenz
MRadmin steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/` (inkl. Unit-Tests und der Weboberfläche aus `web/`, Node lädt Gradle selbst). Zum Entwickeln der Oberfläche in `web/` `npm run dev` (leitet `/api` an ein laufendes MRadmin auf Port 8125 weiter). Modul-API für andere Plugins: [docs/MODULE-API.md](docs/MODULE-API.md), Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md).
