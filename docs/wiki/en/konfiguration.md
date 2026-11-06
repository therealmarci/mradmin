# Configuration

Easiest in the browser: web interface → **Plugins → MRadmin → Configuration** - every setting with an explanation, saving reloads MRadmin right away (permission `mradmin.config`). Everything is also commented in `plugins/MRadmin/config.yml`, in the same layout as in all MRtechnology plugins; you apply changes by hand with `/mradmin reload`. Only in the file: your own dashboard buttons (`admin-mode.menu.buttons`), `web.roles` / `web.players` and new ticket categories.

| Section | What for | More |
|---|---|---|
| `general` | `language`, `fallback-language`, `server-language` (languages, automatically per player) and `timezone` (time zone of all displayed times, empty = the server's) | [Language](#language) |
| `features` · `integrations` · `libertybans` | Features and plugin connections on/off, quick-select for punishments | [Quick start](schnellstart.md#switching-features-on-and-off), [Working with other plugins](zusammenarbeit.md) |
| `maintenance` · `uptime-kuma` | How maintenance works, connection to Uptime Kuma | [Maintenance](wartung.md) |
| `announce` | Announcements in game | [Announcements](ankuendigungen.md) |
| `admin-mode` | Admin mode, menu item, own dashboard buttons | [Dashboard & admin mode](dashboard.md) |
| `tickets` | Categories, cooldowns, limits, how long closed tickets are kept | [Handling tickets](tickets.md) |
| `health` | History and TPS alarm | |
| `versions` | Plugins page: how often it is checked, automatic downloads, pre-releases, how the restart button restarts | [Plugins & updates](plugins.md) |
| `storage` | Storage: `yaml` (default), `sqlite` or `mysql` | [Storage](#storage-yaml-sqlite-or-mysql) |
| `web` · `mail` | Web interface, mails for contact and unban requests | [Web interface](/docs/WEBOBERFLAECHE.md) |
| `player-web` | Player page (`/web`): on/off, sign-in with a code, rankings, news, own statistics tile | [Player page](spielerseite.md) |
| `map` | Map: install, update and set up squaremap automatically, hide worlds of plugins, `nether-roof`, `player-page` | [Map](karte.md) |
| `logging` | How many days the log is kept (`keep-days`, 1-14) | |
| `discord` · `discord-announce` | Admin log via webhook, announcements on the community Discord | [Discord](#discord) |
| `updates` · `gui` | Update notices, menus | |

## Language
All messages, templates and Discord texts are in `plugins/MRadmin/lang/<language>.yml`. New messages from updates are added automatically. General information under [Languages & messages](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/).
- **Server language:** Discord logs and announcements, the server list during maintenance, push notifications, mails and notes in tickets have no single recipient - they use `general.server-language` (default `en`). On a German server, set `server-language: de`.
- Every player sees announcements, the maintenance countdown, boss bars and titles in their language. The web interface follows the browser's language - or the language you switched to there.

## Storage: YAML, SQLite or MySQL
| `storage.type` | Where | What for |
|---|---|---|
| `yaml` (default) | Files in the plugin folder | small servers |
| `sqlite` | `plugins/MRadmin/mradmin.db` | many tickets, the web interface |
| `mysql` | MySQL or MariaDB | several servers with shared tickets |

To switch: change `type` and restart the server - the existing data is taken over. Paper downloads the database drivers by itself on the first start. `/mradmin storage` shows the state.

## Discord
For the **admin log** create a webhook (*Edit channel → Integrations → Webhooks*), enable it under `discord` and enter the URL. Which events are sent (tickets, maintenance, admin mode, web sign-ins …) you set under `discord.events`. Test: `/mradmin discord test`.

The other MRtechnology plugins can send their logs through this webhook (`discord.use-mradmin-webhook` in their config). All events are also in the **log** of the web interface. For announcements in the public channel there is the separate webhook `discord-announce` - see [Announcements](ankuendigungen.md#on-the-community-discord).

## All settings
Every setting of `config.yml` with its default - the same fields as in MRadmin's browser form. The list is generated automatically from the plugin and therefore always matches this version.

<!-- auto:config -->
