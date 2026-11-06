# Ankündigungen

```text
/mradmin announce Heute Abend Event am Spawn!
/mradmin announce Achtung | Der Nether wird gleich zurückgesetzt
/mradmin announce restart 5 Minuten
```

- **Eigener Text** erscheint als Titel auf dem Bildschirm. Mit `|` trennst du **Titel | Untertitel**. Farben im [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html) sind erlaubt.
- **Eigene Vorlagen** legst du in der Weboberfläche unter **Kommunikation → Ankündigungen** an (`mradmin.announce.templates`): Titel, Untertitel, Chat und Actionbar je Deutsch und Englisch mit Vorschau, optional ein Discord-Text, Anzeigedauer des Titels, Ton und **Zielgruppe** (Spieler, Bots, Support, Staff, Admin - jede Gruppe bekommt auch, was für die darunter gedacht ist; „Spieler“ = alle). Sie gehen im Web, im Ankündigungs-Menü und mit `/mradmin announce <name>`. Werte nach dem Namen füllen `<arg1>`, `<arg2>` … bzw. `<args>`.
- **Eingebaute Vorlagen** (`restart`, `restart-now`, `update`, `event`) stehen in der Sprachdatei unter `announce-presets`. MRadmin nutzt `restart` selbst (Neustart-Countdown); in den Menüs stehen sie nicht, mit dem Befehl und in Skripten gehen sie weiter.
- Jeder Spieler sieht die Ankündigung in seiner Sprache. `-q` spielt keinen Ton.

Ankündigen braucht `mradmin.announce`, Einstellungen stehen unter `announce` in der [Konfiguration](konfiguration.md).

## Beispiel: Restart-Script mit Countdown
```bash
mcrcon -p "$RCON_PW" "mradmin announce restart 5 Minuten"; sleep 240
mcrcon -p "$RCON_PW" "mradmin announce restart 1 Minute";  sleep 60
mcrcon -p "$RCON_PW" "mradmin announce restart-now" "stop"
```

## Im Community-Discord
Ein **eigener Webhook** für deinen öffentlichen Channel (`discord-announce`), getrennt vom Admin-Log. Er postet sparsam:
- **Eine Nachricht pro Wartung**, höchstens 24 Stunden vorher (`announce-ahead`). Sie wird mitgeführt statt neu gepostet: **„Wartungsarbeiten geplant“** → **„Wartungsarbeiten laufen“** → **„Server wieder online“** mit der tatsächlichen Zeit (bei vorzeitigem Ende mit Hinweis) oder **„Wartung abgesagt“**. Neue Zeiten werden in dieselbe Nachricht eingetragen. Die Rolle aus `ping-role-id` wird nur beim ersten Post erwähnt.
- **Ankündigungen**, wenn die Vorlage einen `discord`-Text hat. Eigene Texte nur mit `-d`: `/mradmin announce -d Heute Abend Event!`. Mit `-q` geht eine Vorlage nicht nach Discord.
- Optional **Neuigkeiten und Changelogs** der Spielerseite und **Vorfälle von Uptime Kuma** (`discord-announce.kuma`).

Zeiten erscheinen als Discord-Zeitstempel - jeder sieht sie in seiner Zeitzone. Test: `/mradmin discord test announce`.

> [!NOTE]
> Discord benachrichtigt bei bearbeiteten Nachrichten nicht. Beginn und Ende einer Wartung sieht also, wer in den Channel schaut - der Ping kommt nur, wenn die Wartung zum ersten Mal gepostet wird.
