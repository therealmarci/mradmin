# Plugins & updates

In the web interface under **Server → Plugins** you see **all MRtechnology plugins** as a table: at the top the ones on your server with installed version, channel, available version and whether an update is waiting, below that the rest. A click on an installed plugin opens its page with "What's new".

## Channels: stable, beta, dev
Every plugin follows a **channel** - switch it in the column **Channel** (`mradmin.versions.install`):

| Channel | What comes | For whom |
|---|---|---|
| **Stable** | stable versions only (`STA-…`) | every server - default |
| **Beta** | also betas (`BET-…`) - new features earlier, not quite finished | if you want to try new things early |
| **Dev** | every development build (after every commit), unfinished | test servers only, only with the release token |

- Without a choice of its own a plugin follows the **default channel** `versions.channel` (`stable` or `beta`); "back to default" removes the own choice. 0.x plugins only exist as beta and always get betas.
- **Back to stable never downgrades:** a running beta or dev build stays until a newer stable version comes (older versions could not handle the settings that were already migrated).
- **Protection against steps back** - for every channel, also for `versions.auto-download`:
  - If the offered version has a lower `config-version` (config.yml) or `lang-version` (language files) than your server, it is **not installed**: your settings were already migrated by a newer version.
  - If the running and the offered build both come from GitHub (all releases and dev builds), the offered one must be a **successor** of the running one. Important after dev: a dev build carries the number of the last release - a later hotfix would look "newer" but would remove the features of the dev build again. It is offered only once it contains the dev state.
  - Blocked versions show **"Step back blocked"** with the reason on the page. If you really want to go back, do it by hand: put the old JAR in and restore the `config.yml` from a backup (`config.v<number>.backup.yml`).
- **Stable is greyed out** as long as there is no stable version yet (plugins with only betas so far, 0.x). **Beta can always be chosen** - it also gets the stable versions, so you can stay on beta even while a stable is the newest.
- **Dev** only appears when a **valid release token** is in the configuration (`versions.dev-token`, you get it from the developer). If it is wrong, dev stays hidden and the page shows a note. The dev builds lie sealed in the GitHub release `mc0-dev`; MRadmin opens them with the token and checks the checksum. A plugin that runs a dev build is marked with **Dev** and the commit (e.g. `Dev c44ebd6`). `versions.auto-download` fetches dev builds by itself only with `all`.

## Install
You install plugins that are not installed yet with one click (`mradmin.versions.install`). MRadmin downloads the official release for your Minecraft version (only with a matching checksum) into `plugins/` - **it is loaded on the next restart**. What gets installed is what the channel of the plugin offers (see above); if there are only pre-releases so far (e.g. MRpvp 0.x), the newest of them.

## Configure in the browser
The page of an installed plugin has the tab **Configuration** (`mradmin.config`, in `mradmin.groups.admin`): all settings of `config.yml` as a form with explanations (German/English). Saving checks every value, writes the file (comments stay, the old file is kept next to it as `config.backup-web.yml`) and reloads the plugin right away - without a command, without a restart. Editing by hand still works. Available for MRadmin and all MRtechnology plugins in a current version.

## Updates
- **Download** (`mradmin.versions.install`) gets the new version from the official release on GitHub - only for your Minecraft version and only if the checksum (SHA-256) matches. It goes to `plugins/update/`, **it is installed on the next restart** (Paper does that itself). Nothing is swapped while the server runs.
- The file name stays the old one (e.g. `MRsleep-mc26.3-v1.1.5.jar` with version 1.2.0 inside) - only this way Paper knows which plugin is replaced. Which version runs is shown under **Server → Plugins** or with `/<plugin> version`.
- **Automatically:** `versions.auto-download: patch` downloads e.g. hotfixes (1.2.x) by itself, `minor` also new features, `all` everything. The default is `off`.
- It is checked every `versions.check-interval-hours` and with **Check now** (`mradmin.versions.install`).
- Downloads and restarts are recorded in the log and the Discord log (`plugin-update-downloaded`, `server-restart`).

## Restarting the server
> [!TIP]
> Easiest with the **[starter](starter.md)** built into MRadmin: it starts the server again after every restart, crash or hang, installs Paper updates and can restart daily. Then you do not need `versions.restart-method`; you can also restart at any time with `/mradmin restart`.

After a download or installation the page shows "Restart needed". A **restart button** (`mradmin.server.restart`) with a countdown (`versions.restart-delays`, default 5 minutes, 1 minute, right away, announced like `/mradmin announce restart`) only exists while an update or a newly installed plugin waits for the restart, and only once you set `versions.restart-method` - because a server cannot start itself again, something outside has to do it:

| `versions.restart-method` | What happens |
|---|---|
| `none` (default) | no button |
| `stop` | The server stops, and **your start script with a loop, a watchdog or your host's panel** starts it again |
| `script` | The server restarts via the `restart-script` from `spigot.yml` (`settings.restart-script`) |

> [!WARNING]
> Only set `stop` or `script` if you know for sure that the server comes back up afterwards - otherwise the button only stops it. Many host panels do **not** restart by themselves after a normal stop, only after a crash. Try it once before you use the button when it matters.

### Example: start script with a loop (for `restart-method: stop`)
The script starts the server again 5 seconds after every stop. To quit for good, press `Ctrl+C` during these 5 seconds.

**Linux** (`start.sh`, make it executable with `chmod +x start.sh`, e.g. start it in `screen` or `tmux`):
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
Adjust the memory (`4G`) and the name of the Paper JAR to your server.
