# Starter (mrserver)

Der Starter startet deinen Paper-Server und **bringt ihn von selbst zurück**: nach einem Neustart aus MRadmin, nach einem Absturz und wenn der Server hängt. Bei jedem Start spielt er auf Wunsch den **neuesten stabilen Paper-Build** ein. Gesteuert wird alles aus MRadmin: Neustart-Knopf im Browser, Countdown im Spiel, tägliche Neustarts, Arbeitsspeicher, Paper-Updates.

Der Starter ist **freiwillig**. Ohne ihn läuft MRadmin wie bisher.

> [!NOTE]
> Der Starter steckt in der MRadmin-JAR - es gibt nichts extra herunterzuladen. Er läuft unter **Linux** (screen oder systemd) und **Windows**.

## Was er kann
- **Neustart und Stopp mit Countdown** aus dem Browser (Seite **Server**, Reiter Übersicht), im Spiel (`/mradmin restart`, `/mradmin stop`) oder auf dem Rechner (`mrserver restart`, `mrserver stop`). Angekündigt wird im Spiel wie bei `/mradmin announce restart` (Vorlagen `restart` und `stop` in der Sprachdatei), im Community-Discord nur mit `server.discord-announce: true`.
- **Mit Grund:** Jeder Neustart und Stopp steht mit Grund in der Konsole, im [Protokoll](weboberflaeche.md) und im Team-Discord. Eigenen Grund einfach hinten anhängen (`/mradmin restart 5m Hardware-Tausch`, `mrserver stop 10m Umzug`) bzw. im Browser ins Feld **Grund** schreiben. Ohne eigenen Grund nennt MRadmin ihn selbst: „Geplanter Neustart (05:00)“, wartende Updates (z.B. „Updates: MRchunks 1.2.2, Paper 244“) oder „kein Grund angegeben“.
- **Tägliche Neustarts** zu festen Uhrzeiten (`server.restart-times`). Der Countdown beginnt genau `server.countdown` vorher (Standard 15 Minuten). Auf der Seite **Server** kannst du sie **24 Stunden aussetzen** (z.B. während eines Events) oder einen laufenden geplanten Neustart **überspringen**.
- **Genug Vorlauf für die Spieler:** Im Browser stehen für Neustart und Stopp 15, 30 und 60 Minuten zur Wahl (`versions.restart-delays`). Kürzere Zeiten gibt es nur im aufklappbaren roten Bereich **Notfall** (`versions.emergency-delays`) und nur mit Grund. Spieler, die gerade ein Duell spielen, bekommen den Countdown als Zeile über der Hotbar statt als Titel über den ganzen Bildschirm.
- **Abstürze:** Fällt der Server aus, startet der Starter ihn nach 10 Sekunden wieder. Nach `server.crashes.max` Abstürzen in `server.crashes.window-minutes` Minuten (Standard 3 in 15) gibt er auf - dann stimmt etwas Grundsätzliches nicht und der Server bleibt aus, bis du nachsiehst.
- **Hänger:** Tickt der Server `server.hang-seconds` Sekunden lang nicht (Standard 180), beendet der Starter ihn und startet ihn neu.
- **Paper-Updates** (`server.paper-update: auto`): vor jedem Start der neueste **stabile** Build deiner Minecraft-Version aus dem offiziellen PaperMC-Download, nur mit passender Prüfsumme. **Eine neue Minecraft-Version (z.B. 26.3 → 26.4) spielt er nie ein.** Startet ein neuer Build nicht, nimmt der Starter von selbst wieder den alten und überspringt den fehlerhaften. Ob Paper aktuell ist, prüft MRadmin alle 15 Minuten (auch ohne Starter) und zeigt es auf der Seite **Server**.
- **Meldungen:** Abstürze, Hänger, Aufgeben und Paper-Updates stehen nach dem nächsten Start in der Konsole, im [Protokoll](/docs/WEBOBERFLAECHE.md#protokoll-mradminwebaudit) und im Discord-Log (Ereignisse `server-crash`, `server-hang`, `server-gave-up`, `paper-updated`, `paper-rollback`).
- **Autostart** beim Hochfahren des Rechners.

## Einrichten

### Neuer Server
**Am einfachsten mit einem Befehl** (auch auf der [Download-Seite](https://mrtechnology.marcelr.at/de/downloads/) zum Kopieren):

| System | Befehl |
|---|---|
| Linux (als normaler Benutzer, nicht root) | `curl -fsSL https://mrtechnology.marcelr.at/install.sh \| sh` |
| Windows (PowerShell) | `irm https://mrtechnology.marcelr.at/install.ps1 \| iex` |

Das Skript prüft Java 25 und installiert es auf Wunsch (Debian/Ubuntu mit `sudo apt`, Windows mit `winget`), fragt nach dem **Ordner** (Standard `~/MINECRAFT/Server` bzw. `%USERPROFILE%\MINECRAFT\Server`, muss leer sein), dem **Arbeitsspeicher** (min./max.), der **öffentlichen Adresse der Weboberfläche** (optional), ob du **Beta-Versionen** willst (Standard nein; ja = die neueste MRadmin-Beta, falls neuer als die stabile Version, und `versions.channel: beta` für alle Plugins) und der [Minecraft-EULA](https://aka.ms/MinecraftEULA). Dann lädt es MRadmin und Paper (beides mit Prüfsumme), schaltet die Weboberfläche ein (nur lokal auf Port 8125, Speicher SQLite) und richtet auf Wunsch Autostart und Start ein. Weitere MRtechnology-Plugins installierst du danach im Browser unter **Server → Plugins**.

> [!TIP]
> Ohne Fragen, z.B. für Skripte: Linux `… | sh -s -- --memory-min 4G --memory-max 4G --web-url https://admin.example.com --accept-eula --yes` (dazu `--dir <ordner>`, `--no-start`, `--beta` bzw. `--stable`); Windows über Umgebungsvariablen `MR_DIR`, `MR_MEMORY_MIN`, `MR_MEMORY_MAX`, `MR_WEB_URL`, `MR_CHANNEL=beta`, `MR_ACCEPT_EULA=1`, `MR_YES=1`, `MR_START=0` vor dem Befehl. Die Skripte sind öffentlich einsehbar: [install.sh](https://mrtechnology.marcelr.at/install.sh), [install.ps1](https://mrtechnology.marcelr.at/install.ps1).

> [!NOTE]
> Testserver mit **Dev-Builds** (jeder Entwicklungsstand) nur mit dem Freigabetoken des Entwicklers: Linux `--dev` (fragt nach dem Token) bzw. `MR_DEV_TOKEN`, Windows `MR_DEV_TOKEN`. MRadmin kommt dann als Dev-Build, die anderen Plugins folgen Beta; der Token landet als `versions.dev-token` in der `config.yml`.

**Von Hand:**
1. Leeren Ordner anlegen und **`mrserver.sh`** (Linux) bzw. **`mrserver.cmd`** (Windows) aus dem [MRadmin-Download](https://mrtechnology.marcelr.at/download/mradmin/) hineinlegen.
2. Einrichten - lädt MRadmin und Paper selbst herunter (beides mit Prüfsumme) und fragt nach der [Minecraft-EULA](https://aka.ms/MinecraftEULA):
   - Linux: `sh mrserver.sh setup`
   - Windows: `mrserver setup`
3. Autostart einschalten und starten: `./mrserver.sh install`, dann `./mrserver.sh start` (Windows: `mrserver install`, `mrserver start`).

Java 25 muss installiert sein (z.B. `sudo apt install openjdk-25-jre-headless`, unter Windows [Adoptium](https://adoptium.net/)).

### Bestehender Server
1. Im Spiel oder in der Konsole: `/mradmin starter setup` (Permission `mradmin.server.setup`). Das legt `mrserver.sh`, `mrserver.cmd` und `mrserver.properties` in den Serverordner - **nichts Bestehendes wird verändert.** Alternativ auf dem Rechner im Serverordner: `java -jar plugins/MRadmin-….jar setup`.
2. Den Server **ein letztes Mal auf dem alten Weg stoppen** und dein altes Start-Skript bzw. dessen Autostart abschalten (sonst starten zwei Programme den Server).
3. `./mrserver.sh install` und `./mrserver.sh start` (Windows: `mrserver install`, `mrserver start`).

> [!WARNING]
> **Altes Skript mit Wächter oder täglichem Neustart per cron** (z.B. ein eigenes `start.sh` oder `mc.sh`): Die cron-Einträge **vor** dem Umstieg entfernen (`crontab -e`) - sonst startet der alte Wächter den Server neben dem Starter ein zweites Mal. Tägliche Neustarts stellst du stattdessen in `server.restart-times` ein, Arbeitsspeicher in `server.memory-min` / `memory-max`.

> [!TIP]
> Ob der Starter aktiv ist, zeigt `/mradmin starter`. `versions.restart-method` brauchst du mit dem Starter nicht mehr.

## Wie der Starter läuft (`mode`)
Einstellung `mode` in `mrserver.properties` im Serverordner. `setup` wählt sie automatisch, ändern mit `mrserver setup --mode <modus>`, danach `mrserver uninstall` und `mrserver install`.

| `mode` | System | Konsole | Autostart |
|---|---|---|---|
| `systemd` | Linux mit systemd (Standard, wenn vorhanden) | `mrserver attach` zeigt die Ausgabe, Befehle mit `mrserver console` | systemd-Dienst des Benutzers (kein root nötig) |
| `screen` | Linux | `mrserver attach` (verlassen mit `Strg+A`, dann `D`) | Eintrag `@reboot` in der crontab |
| `window` | Windows | eigenes Konsolenfenster | Windows-Aufgabe bei der Anmeldung |
| `foreground` | Docker, Hoster-Panel | im eigenen Terminal (`mrserver run`) | macht dein Panel bzw. Container |

> [!WARNING]
> **systemd:** Damit der Server auch läuft, wenn du nicht angemeldet bist, einmal als root `loginctl enable-linger <benutzer>` ausführen. `mrserver install` versucht das selbst und sagt Bescheid, wenn es nicht geht.

## Befehle auf dem Rechner
Im Serverordner `./mrserver.sh <befehl>` (Linux) bzw. `mrserver <befehl>` (Windows):

| Befehl | Was er tut |
|---|---|
| `setup [--accept-eula] [--mode …] [--memory-min 2G] [--memory-max 4G] [--web] [--web-url …] [--channel stable\|beta\|dev]` | Ordner einrichten, fehlendes Paper herunterladen; Arbeitsspeicher, Weboberfläche und Kanal übernimmt MRadmin beim ersten Start in die `config.yml`. `--channel` gilt nur für einen neuen Server (Dev braucht den Token in `MRSERVER_DEV_TOKEN`) |
| `install` · `uninstall` | Autostart an · aus (ein laufender Server läuft weiter) |
| `start` · `run` | im Hintergrund starten · im aktuellen Terminal starten |
| `stop [zeit\|now]` · `restart [zeit\|now]` | mit Countdown stoppen bzw. neu starten (ohne Zeit: `server.countdown`, Standard 15 Minuten) |
| `cancel` | Countdown abbrechen |
| `status` | läuft der Server? Paper-Build, Modus |
| `console <befehl>` | Befehl an die Server-Konsole schicken |
| `attach` | Konsole (screen) bzw. Ausgabe (systemd) anzeigen |

Zeiten wie `30s`, `5m`, `1h`, eine Zahl (Sekunden) oder `now`. Antwortet MRadmin nicht (z.B. nicht installiert), stoppt bzw. startet `mrserver` sofort ohne Countdown.

## Einstellungen
**Im Browser bzw. in der `config.yml`** (Abschnitt `server`, Server → Plugins → MRadmin → Konfiguration → „Starter & geplante Neustarts“ - dort steht oben auch, ob der Starter eingerichtet und aktiv ist). Arbeitsspeicher und Java-Optionen gelten ab dem nächsten Start.

| Schlüssel | Standard | Wofür |
|---|---|---|
| `server.memory-min` · `memory-max` | `1G` · `4G` | Arbeitsspeicher des Servers |
| `server.java-flags` | leer | zusätzliche Java-Optionen zum Abstimmen (z.B. Aikar-Flags) |
| `server.paper-update` | `auto` | `auto` = bei jedem Start, `notify` = nur anzeigen, `off` |
| `server.restart-times` | `[]` | tägliche Neustarts, z.B. `["05:00"]` (Zeitzone `general.timezone`) |
| `server.countdown` | `15m` | Countdown der geplanten Neustarts und von `mrserver stop/restart` ohne Zeit |
| `server.plain-stop` | `stop` | was ein einfaches `/stop` macht: `stop` = Server bleibt aus, `restart` = er kommt wieder |
| `server.discord-announce` | `false` | Countdown von Neustart/Stopp auch im Community-Discord (das Team-Log bekommt ihn immer) |
| `server.crashes.max` · `window-minutes` | `3` · `15` | wann der Starter aufgibt |
| `server.hang-seconds` | `180` | Hänger-Erkennung, `0` = aus |
| `server.stop-timeout-seconds` | `180` | so lange darf das Herunterfahren dauern |

> [!NOTE]
> **Sicherheit:** Über den Browser lassen sich nur Java-Optionen zum Abstimmen setzen. Alles, womit man Programme auf dem Rechner starten oder beliebige Dateien lesen und schreiben könnte (z.B. `-XX:OnOutOfMemoryError`, `-javaagent`, eine log4j-Konfiguration), ignoriert MRadmin mit einer Warnung in der Konsole. Welches Java, welche Paper-JAR und weitere Optionen stehen nur in **`mrserver.properties`** auf dem Rechner (`java`, `jar`, `mode`, `name`, `java-flags`) - die lassen sich im Browser nicht ändern.

## Wo die Dateien liegen
- `mrserver.sh`, `mrserver.cmd`, `mrserver.properties` im Serverordner. Die beiden Skripte erneuert MRadmin bei Updates selbst - bitte nicht bearbeiten.
- `plugins/MRadmin/starter/`: Austausch zwischen MRadmin und Starter (`launch.properties`, Lebenszeichen, Ereignisse), `starter.log` mit allem, was der Starter getan hat, `paper-previous.jar` (der Paper-Build vor dem letzten Update).
- Nach einem MRadmin-Update nimmt der Starter seine neue Version beim nächsten Neustart von selbst.

## Häufige Fragen
**Der Server startet nach `/stop` nicht wieder.** Gewollt: `server.plain-stop: stop`. Zum Neustarten `/mradmin restart` oder `mrserver restart` nutzen - oder `plain-stop: restart` einstellen.

**Der Starter hat aufgegeben.** `mrserver status` zeigt es. Sieh in `logs/latest.log` und `crash-reports/` nach, behebe die Ursache und starte mit `mrserver start`.

**Ich will eine neue Minecraft-Version.** Das macht der Starter bewusst nicht automatisch, weil dann meist auch die Plugins neue Versionen brauchen. Siehe [Installieren & aktualisieren](https://mrtechnology.marcelr.at/wiki/allgemein/installieren/#versionen).
