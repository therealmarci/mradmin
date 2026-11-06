# Wartungsarbeiten

| Zeitpunkt | Was passiert |
|---|---|
| 15 Minuten vorher (`warn-before`) | Chat-Ankündigung und **Bossbar mit Countdown** für alle |
| 15m, 10m, 5m, 2m, 1m, 30s vorher | Chat-Erinnerung mit Ton |
| letzte 10 Sekunden | großer Countdown auf dem Bildschirm |
| Beginn | Titel „Wartungsarbeiten“, kurz danach werden alle ohne Bypass gekickt |
| während der Wartung | Join nur mit `mradmin.maintenance.bypass`, die Serverliste zeigt „Wartung“ |
| Ende | Chat-Nachricht, alle dürfen wieder joinen |

## Ohne Uptime Kuma
| Befehl | Was er tut |
|---|---|
| `/mradmin maintenance` | Status |
| `/mradmin maintenance on [dauer] [titel]` | Sofort starten (ohne Dauer bis `off`) |
| `/mradmin maintenance schedule <in> [dauer] [titel]` | Mit Countdown planen, z.B. `schedule 15m 1h Update` |
| `/mradmin maintenance extend <dauer>` | Ende verschieben, z.B. `30m` oder `-15m` |
| `/mradmin maintenance off` | Beenden oder absagen |

Ansehen braucht `mradmin.maintenance.view`, steuern `mradmin.maintenance.manage`. In der Weboberfläche gibt es dasselbe unter **Wartung**.

## Uptime Kuma
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
- Vorfälle, die auf einer **Kuma-Statusseite** angeheftet sind (`uptime-kuma.status-pages`), erscheinen als Warnungen auf der Spielerseite und im Spiel.

## Im Community-Discord
Mit einem Webhook unter `discord-announce` postet MRadmin **eine Nachricht pro Wartung** und führt sie mit („geplant“ → „laufen“ → „wieder online“ bzw. „abgesagt“) - siehe [Ankündigungen](ankuendigungen.md#im-community-discord).
