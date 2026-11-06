# Maintenance

| When | What happens |
|---|---|
| 15 minutes before (`warn-before`) | Chat announcement and **boss bar with a countdown** for everyone |
| 15m, 10m, 5m, 2m, 1m, 30s before | Chat reminder with a sound |
| last 10 seconds | big countdown on the screen |
| Start | Title "Maintenance", shortly after that everyone without bypass is kicked |
| during maintenance | Joining only with `mradmin.maintenance.bypass`, the server list shows "Maintenance" |
| End | Chat message, everyone may join again |

## Without Uptime Kuma
| Command | What it does |
|---|---|
| `/mradmin maintenance` | Status |
| `/mradmin maintenance on [duration] [title]` | Start right away (without a duration until `off`) |
| `/mradmin maintenance schedule <in> [duration] [title]` | Schedule with a countdown, e.g. `schedule 15m 1h Update` |
| `/mradmin maintenance extend <duration>` | Move the end, e.g. `30m` or `-15m` |
| `/mradmin maintenance off` | End or cancel |

Viewing needs `mradmin.maintenance.view`, controlling `mradmin.maintenance.manage`. The web interface has the same under **Maintenance**.

## Uptime Kuma
Maintenance you schedule in [Uptime Kuma](https://github.com/louislam/uptime-kuma) is taken over by MRadmin live - including the countdown. If a maintenance is **extended, moved or cancelled** in Kuma, everything adapts right away, and the players are informed.

```yaml
uptime-kuma:
  enabled: true
  url: "https://status.yourserver.com"
  username: "admin"
  password: "secret"
  monitor-ids: [12]      # only maintenance that affects the Minecraft monitor (ID 12)
```

- **2FA active in Kuma?** Then enter the login token (`token`) instead of the password: sign in to Kuma in the browser, F12 → Application → Local Storage → copy the value of `token`.
- `monitor-ids` / `maintenance-ids` decide which maintenance applies to this server (both empty = all).
- If Kuma cannot be reached, the last known schedule stays valid, and MRadmin reconnects automatically.
- All maintenance types of Kuma are supported: one-time, recurring, cron and manual. `/mradmin maintenance refresh` asks again right away.
- Incidents pinned on a **Kuma status page** (`uptime-kuma.status-pages`) appear as warnings on the player page and in game.

## On the community Discord
With a webhook under `discord-announce` MRadmin posts **one message per maintenance** and keeps it up to date ("scheduled" → "running" → "back online" or "cancelled") - see [Announcements](ankuendigungen.md#on-the-community-discord).
