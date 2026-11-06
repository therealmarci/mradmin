# Starter (mrserver)

The starter starts your Paper server and **brings it back by itself**: after a restart from MRadmin, after a crash and when the server hangs. On every start it can install the **newest stable Paper build**. Everything is controlled from MRadmin: restart button in the browser, countdown in game, daily restarts, memory, Paper updates.

The starter is **optional**. Without it MRadmin works as before.

> [!NOTE]
> The starter is part of the MRadmin JAR - there is nothing extra to download. It runs on **Linux** (screen or systemd) and **Windows**.

## What it does
- **Restart and stop with a countdown** from the browser (page Plugins), in game (`/mradmin restart`, `/mradmin stop`) or on the machine (`mrserver restart`, `mrserver stop`). Announced in game like `/mradmin announce restart` (presets `restart` and `stop` in the language file).
- **Daily restarts** at fixed times (`server.restart-times`); the countdown starts before.
- **Crashes:** if the server goes down, the starter starts it again after 10 seconds. After `server.crashes.max` crashes within `server.crashes.window-minutes` minutes (default 3 in 15) it gives up - then something is fundamentally wrong and the server stays down until you look.
- **Hangs:** if the server has not ticked for `server.hang-seconds` seconds (default 180), the starter ends it and starts it again.
- **Paper updates** (`server.paper-update: auto`): before every start the newest **stable** build of your Minecraft version from the official PaperMC download, only with a matching checksum. **It never installs a new Minecraft version (e.g. 26.3 → 26.4).** If a new build does not start, the starter goes back to the old one by itself and skips the broken one.
- **Reports:** crashes, hangs, giving up and Paper updates appear after the next start in the console, in the [log](weboberflaeche.md#log-mradminwebaudit) and in the Discord log (events `server-crash`, `server-hang`, `server-gave-up`, `paper-updated`, `paper-rollback`).
- **Autostart** when the machine boots.

## Setting it up

### New server
1. Create an empty folder and put **`mrserver.sh`** (Linux) or **`mrserver.cmd`** (Windows) from the [MRadmin download](https://mrtechnology.marcelr.at/download/mradmin/) into it.
2. Set it up - downloads MRadmin and Paper itself (both with checksum) and asks for the [Minecraft EULA](https://aka.ms/MinecraftEULA):
   - Linux: `sh mrserver.sh setup`
   - Windows: `mrserver setup`
3. Switch on the autostart and start: `./mrserver.sh install`, then `./mrserver.sh start` (Windows: `mrserver install`, `mrserver start`).

Java 25 must be installed (e.g. `sudo apt install openjdk-25-jre-headless`, on Windows [Adoptium](https://adoptium.net/)).

### Existing server
1. In game or in the console: `/mradmin starter setup` (permission `mradmin.server.setup`). This puts `mrserver.sh`, `mrserver.cmd` and `mrserver.properties` into the server folder - **nothing that is there gets changed.** Or on the machine in the server folder: `java -jar plugins/MRadmin-….jar setup`.
2. Stop the server **one last time the old way** and switch off your old start script or its autostart (otherwise two programs start the server).
3. `./mrserver.sh install` and `./mrserver.sh start` (Windows: `mrserver install`, `mrserver start`).

> [!TIP]
> `/mradmin starter` shows whether the starter is active. With the starter you do not need `versions.restart-method` any more.

## How the starter runs (`mode`)
Setting `mode` in `mrserver.properties` in the server folder. `setup` picks it by itself; change it with `mrserver setup --mode <mode>`, then `mrserver uninstall` and `mrserver install`.

| `mode` | System | Console | Autostart |
|---|---|---|---|
| `systemd` | Linux with systemd (default if available) | `mrserver attach` shows the output, commands with `mrserver console` | systemd service of the user (no root needed) |
| `screen` | Linux | `mrserver attach` (leave with `Ctrl+A`, then `D`) | `@reboot` line in the crontab |
| `window` | Windows | own console window | Windows task at logon |
| `foreground` | Docker, hosting panel | in its own terminal (`mrserver run`) | done by your panel or container |

> [!WARNING]
> **systemd:** so the server also runs while you are not logged in, run `loginctl enable-linger <user>` once as root. `mrserver install` tries it itself and tells you if it cannot.

## Commands on the machine
In the server folder `./mrserver.sh <command>` (Linux) or `mrserver <command>` (Windows):

| Command | What it does |
|---|---|
| `setup [--accept-eula] [--mode …]` | set up the folder, download Paper if missing |
| `install` · `uninstall` | autostart on · off (a running server keeps running) |
| `start` · `run` | start in the background · start in the current terminal |
| `stop [time\|now]` · `restart [time\|now]` | stop or restart with a countdown (without a time: `server.countdown`, default 5 minutes) |
| `cancel` | cancel a countdown |
| `status` | is the server running? Paper build, mode |
| `console <command>` | send a command to the server console |
| `attach` | show the console (screen) or the output (systemd) |

Times like `30s`, `5m`, `1h`, a number (seconds) or `now`. If MRadmin does not answer (e.g. not installed), `mrserver` stops or restarts right away without a countdown.

## Settings
**In the browser or in `config.yml`** (section `server`, Plugins → MRadmin → Configuration → "Starter & planned restarts"). Memory and Java options apply from the next start on.

| Key | Default | What for |
|---|---|---|
| `server.memory-min` · `memory-max` | `1G` · `4G` | memory of the server |
| `server.java-flags` | empty | extra Java options for tuning (e.g. Aikar's flags) |
| `server.paper-update` | `auto` | `auto` = on every start, `notify` = only show, `off` |
| `server.restart-times` | `[]` | daily restarts, e.g. `["05:00"]` (time zone `general.timezone`) |
| `server.countdown` | `5m` | countdown of planned restarts and of `mrserver stop/restart` without a time |
| `server.plain-stop` | `stop` | what a plain `/stop` does: `stop` = the server stays down, `restart` = it comes back |
| `server.crashes.max` · `window-minutes` | `3` · `15` | when the starter gives up |
| `server.hang-seconds` | `180` | hang detection, `0` = off |
| `server.stop-timeout-seconds` | `180` | how long shutting down may take |

> [!NOTE]
> **Security:** from the browser only Java options for tuning can be set. Anything that could start programs on the machine or read and write any file (e.g. `-XX:OnOutOfMemoryError`, `-javaagent`, a log4j configuration) is ignored by MRadmin with a warning in the console. Which Java, which Paper JAR and further options are only in **`mrserver.properties`** on the machine (`java`, `jar`, `mode`, `name`, `java-flags`) - they cannot be changed in the browser.

## Where the files are
- `mrserver.sh`, `mrserver.cmd`, `mrserver.properties` in the server folder. MRadmin renews the two scripts itself on updates - please do not edit them.
- `plugins/MRadmin/starter/`: exchange between MRadmin and the starter (`launch.properties`, heartbeat, events), `starter.log` with everything the starter did, `paper-previous.jar` (the Paper build before the last update).
- After an MRadmin update the starter takes its new version by itself on the next restart.

## FAQ
**The server does not come back after `/stop`.** On purpose: `server.plain-stop: stop`. To restart use `/mradmin restart` or `mrserver restart` - or set `plain-stop: restart`.

**The starter gave up.** `mrserver status` shows it. Look into `logs/latest.log` and `crash-reports/`, fix the cause and start with `mrserver start`.

**I want a new Minecraft version.** The starter deliberately does not do that by itself, because then the plugins usually need new versions too. See [Installing & updating](https://mrtechnology.marcelr.at/wiki/allgemein/installieren/#versions).
