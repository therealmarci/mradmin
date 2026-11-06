# Quick start

1. **[Download MRadmin](https://mrtechnology.marcelr.at/download/mradmin/)** and put the JAR into the `plugins/` folder.
2. Start the server. This creates `plugins/MRadmin/config.yml` and the language files in `plugins/MRadmin/lang/`.
3. **Give permissions** - there are ready-made groups (built the same in all MRtechnology plugins):
   ```text
   /lp group default permission set mradmin.player true
   /lp group supporter permission set mradmin.groups.support true
   /lp group staff permission set mradmin.groups.staff true
   /lp group admin permission set mradmin.groups.admin true
   ```
4. Done: **`/mradmin`** opens the dashboard, players create tickets with **`/report`**.

Everything else is optional: [Uptime Kuma](wartung.md#uptime-kuma) for scheduled maintenance, a [Discord webhook](konfiguration.md#discord) for logs, the [web interface](/docs/WEBOBERFLAECHE.md) for your phone, the [player page](spielerseite.md) for all players.

## Update
Replace the old JAR with the new one and restart the server. The config is adapted automatically: your values stay, new options are added, the old file is backed up as `config.v<N>.backup.yml`. With the web interface this also works with a click - see [Plugins & updates](plugins.md).

## Switching features on and off
Everything you do not need you switch off under `features` in the config (`/mradmin reload` is enough):

```yaml
features:
  maintenance: true    # maintenance mode incl. Uptime Kuma
  announce: true       # announcements
  admin-mode: true     # admin mode and menu item
  teleport: true       # teleport to players
  summon: true         # summon players
  inventory: true      # view inventory/ender chest
  freeze: true         # freeze
  server-status: true  # server status
  tickets: true        # tickets & reports (/report)
  staff-chat: true     # staff chat (/sc)
```

A feature that is switched off disappears completely from commands, menus and web. Its data (tickets, scheduled maintenance) is kept.
