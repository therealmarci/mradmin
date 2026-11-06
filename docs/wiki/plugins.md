# Plugins & Updates

In der Weboberfläche unter **Plugins** (Seite „Plugin-Konfiguration“) siehst du **alle MRtechnology-Plugins**: oben die auf deinem Server mit installierter und neuester Version und ob ein Update wartet, darunter die übrigen. Ein Klick auf ein installiertes Plugin öffnet seine Seite mit „Was ist neu“.

## Installieren
Nicht installierte Plugins installierst du mit einem Klick (`mradmin.versions.install`). MRadmin lädt das offizielle Release für deine Minecraft-Version (nur mit passender Prüfsumme) nach `plugins/` - **geladen wird es beim nächsten Neustart**. Gibt es bisher nur Vorabversionen (z.B. MRpvp 0.x), wird die neueste davon angeboten.

## Einstellen im Browser
Auf der Seite eines installierten Plugins gibt es den Reiter **Konfiguration** (`mradmin.config`, in `mradmin.groups.admin`): alle Einstellungen der `config.yml` als Formular mit Erklärungen (Deutsch/Englisch). Speichern prüft jeden Wert, schreibt die Datei (Kommentare bleiben, die alte Datei liegt als `config.backup-web.yml` daneben) und lädt das Plugin sofort neu - ohne Befehl, ohne Neustart. Von Hand bearbeiten geht weiterhin. Verfügbar für MRadmin und alle MRtechnology-Plugins in aktueller Version.

## Updates
- **Herunterladen** (`mradmin.versions.install`) holt die neue Version aus dem offiziellen Release auf GitHub - nur für deine Minecraft-Version und nur, wenn die Prüfsumme (SHA-256) stimmt. Sie landet in `plugins/update/`, **installiert wird beim nächsten Neustart** (das macht Paper selbst). Im laufenden Betrieb wird nichts ausgetauscht.
- Der Dateiname bleibt dabei der alte (z.B. `MRsleep-mc26.3-v1.1.5.jar` mit Version 1.2.0 darin) - nur so weiß Paper, welches Plugin ersetzt wird. Welche Version läuft, zeigt **Server → Plugins** bzw. `/<plugin> version`.
- **Automatisch:** `versions.auto-download: patch` lädt z.B. Hotfixes (1.2.x) von selbst, `minor` auch neue Funktionen, `all` alles. Standard ist `off`.
- Geprüft wird alle `versions.check-interval-hours` und mit **Jetzt prüfen** (`mradmin.versions.install`).
- Downloads und Neustarts stehen im Protokoll und im Discord-Log (`plugin-update-downloaded`, `server-restart`).

## Server neu starten
> [!TIP]
> Am einfachsten mit dem **[Starter](starter.md)**, der in MRadmin steckt: Er startet den Server nach jedem Neustart, Absturz oder Hänger wieder, spielt Paper-Updates ein und kann täglich neu starten. Dann brauchst du `versions.restart-method` nicht; neu starten geht außerdem jederzeit mit `/mradmin restart`.

Nach dem Herunterladen oder Installieren zeigt die Seite „Neustart nötig“. Einen **Neustart-Knopf** (`mradmin.server.restart`) mit Countdown (`versions.restart-delays`, Standard 5 Minuten, 1 Minute, sofort, angekündigt wie `/mradmin announce restart`) gibt es nur, solange ein Update oder ein neu installiertes Plugin auf den Neustart wartet, und erst, wenn du `versions.restart-method` einstellst - denn ein Server kann sich nicht selbst wieder starten, das muss etwas außerhalb tun:

| `versions.restart-method` | Was passiert |
|---|---|
| `none` (Standard) | kein Knopf |
| `stop` | Der Server stoppt, und **dein Start-Skript mit Schleife, ein Wächter oder dein Hoster-Panel** startet ihn wieder |
| `script` | Der Server startet über das `restart-script` aus der `spigot.yml` neu (`settings.restart-script`) |

> [!WARNING]
> Stell `stop` oder `script` nur ein, wenn du sicher weißt, dass der Server danach wieder hochkommt - sonst stoppt der Knopf ihn nur. Viele Hoster-Panels starten nach einem normalen Stopp **nicht** von selbst neu, nur nach einem Absturz. Probier es einmal aus, bevor du den Knopf im Ernstfall benutzt.

### Beispiel: Start-Skript mit Schleife (für `restart-method: stop`)
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
