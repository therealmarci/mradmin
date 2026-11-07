# Commands

| Command | What it does | Permission |
|---|---|---|
| `/mradmin` (`/mra`) | Admin dashboard · `/mradmin help` shows all commands | `mradmin.dashboard` |
| `/report [player]` (`/melden`, `/ticket`, `/support`) | Ticket menu: report players, report bugs, other concerns, own tickets | `mradmin.tickets.create` |
| `/web [devices\|logout]` (`/me`) | Link and 6-digit code for your own player page · own devices · sign out | `mradmin.web.self` |
| `/sc [message]` | Staff chat (without a message: on/off) | `mradmin.staffchat` |
| `/mradmin mode [on\|off\|toggle] [player]` · `mode list` | Admin mode for you · for others · who is in it | `mradmin.mode` · `.mode.others` |
| `/mradmin announce [-d] [-q] <text\|template> [values]` | Announcement as title, chat and action bar (`-d` also on Discord, `-q` without sound) | `mradmin.announce` |
| `/mradmin maintenance [status\|on\|schedule\|extend\|off\|refresh]` | View, start, schedule, extend, end maintenance, query Uptime Kuma again | `mradmin.maintenance.view` · `.manage` |
| `/mradmin tickets` | All tickets | `mradmin.tickets.view` |
| `/mradmin player <player>` · `tp <player>` | Player menu · teleport to a player | `mradmin.player.view` · `.teleport` |
| `/mradmin status` | Server status | `mradmin.status` |
| `/mradmin web [setup\|invite\|sessions\|logout\|remove]` | Set up web access, invite the team, view and end sessions, delete accounts | `mradmin.web` · `mradmin.web.manage` |
| `/mradmin mail test <address>` | Test mail via the configured SMTP server | `mradmin.web.manage` |
| `/mradmin discord [test]` | Discord status · test message | `mradmin.admin.discord` |
| `/mradmin storage` | Storage status (yaml, sqlite, mysql) | `mradmin.admin.storage` |
| `/mradmin map [setup]` | Link to the map of the player page · for admins the state of squaremap, set up squaremap again | `mradmin.map` · `mradmin.map.admin` |
| `/mradmin restart [time\|now\|cancel] [reason]` · `stop [time\|now\|cancel] [reason]` | Restart or stop the server with a countdown (optionally with a reason), cancel the countdown | `mradmin.server.restart` |
| `/mradmin starter [setup [mode]]` | State of the [starter](starter.md), next planned restart, Paper update · set up the starter in the server folder | `mradmin.server.restart` · `mradmin.server.setup` |
| `/mradmin reload` · `version` | Reload config and language files · version | `mradmin.admin.reload` · `mradmin.version` |

Details: [Maintenance](wartung.md), [Announcements](ankuendigungen.md), [Dashboard & admin mode](dashboard.md), web commands under [Web interface](/docs/WEBOBERFLAECHE.md#setup).
