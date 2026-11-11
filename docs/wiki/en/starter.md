# Starter (mrserver)

The starter starts your Paper server and **brings it back by itself**: after a restart from MRadmin, after a crash and when the server hangs. On every start it can install the **newest stable Paper build**. Everything is controlled from MRadmin: restart button in the browser, countdown in game, daily restarts, memory, Paper updates.

The starter is **optional**. Without it MRadmin works as before.

> [!NOTE]
> The starter is part of the MRadmin JAR - there is nothing extra to download. It runs on **Linux** (screen or systemd) and **Windows**.

## What it does
- **Restart and stop with a countdown** from the browser (page **Server**, tab Overview), in game (`/mradmin restart`, `/mradmin stop`) or on the machine (`mrserver restart`, `mrserver stop`). Announced in game like `/mradmin announce restart` (presets `restart` and `stop` in the language file), in the community Discord only with `server.discord-announce: true`.
- **With a reason:** Every restart and stop is shown with its reason in the console, the [log](weboberflaeche.md) and the team Discord. Add your own reason at the end (`/mradmin restart 5m hardware swap`, `mrserver stop 10m moving`) or type it into the **Reason** field in the browser. Without one, MRadmin names it itself: "Planned restart (05:00)", waiting updates (e.g. "Updates: MRchunks 1.2.2, Paper 244") or "no reason given".
- **Daily restarts** at fixed times (`server.restart-times`). The countdown starts exactly `server.countdown` before (default 15 minutes). On the page **Server** you can **pause them for 24 hours** (e.g. during an event) or **skip** a running planned restart.
- **Enough warning for the players:** in the browser, restart and stop offer 15, 30 and 60 minutes (`versions.restart-delays`). Shorter times only through the red button in the row **Emergency** (for restart and stop): it first asks for your 2FA code or passkey again, then for the time (`versions.emergency-delays`) and a reason. Players in a duel get the countdown as a line above the hotbar instead of a title across the screen.
- **Crashes:** if the server goes down, the starter starts it again after 10 seconds. After `server.crashes.max` crashes within `server.crashes.window-minutes` minutes (default 3 in 15) it gives up - then something is fundamentally wrong and the server stays down until you look.
- **Hangs:** if the server has not ticked for `server.hang-seconds` seconds (default 180), the starter ends it and starts it again.
- **Paper updates** (`server.paper-update: auto`): before every start the newest **stable** build of your Minecraft version from the official PaperMC download, only with a matching checksum. **It never installs a new Minecraft version (e.g. 26.3 → 26.4).** If a new build does not start, the starter goes back to the old one by itself and skips the broken one. MRadmin checks every 15 minutes whether Paper is up to date (also without the starter) and shows it on the page **Server**. With the starter MRadmin downloads a new build right away - the control card then shows "ready for the restart", and the restart does not have to download first. 5 minutes before a planned restart MRadmin checks Paper and the plugins once more.
- **Reports:** crashes, hangs, giving up and Paper updates appear after the next start in the console, in the [log](weboberflaeche.md#log-mradminwebaudit) and in the Discord log (events `server-crash`, `server-hang`, `server-gave-up`, `paper-updated`, `paper-rollback`).
- **Autostart** when the machine boots.

## Setting it up

### New server
**Easiest with one command** (also on the [downloads page](https://mrtechnology.marcelr.at/en/downloads/) to copy):

| System | Command |
|---|---|
| Linux (as a normal user, not root) | `curl -fsSL https://mrtechnology.marcelr.at/install.sh \| sh` |
| Windows (PowerShell) | `irm https://mrtechnology.marcelr.at/install.ps1 \| iex` |

The script checks Java 25 and installs it on request (Debian/Ubuntu with `sudo apt`, Windows with `winget`), asks for the **folder** (default `~/MINECRAFT/Server` or `%USERPROFILE%\MINECRAFT\Server`, must be empty), the **memory** (min./max.), the **public address of the web interface** (optional), whether you want **beta versions** (default no; yes = the newest MRadmin beta if it is newer than the stable version, and `versions.channel: beta` for all plugins) and the [Minecraft EULA](https://aka.ms/MinecraftEULA). Then it downloads MRadmin and Paper (both with checksum), switches on the web interface (only locally on port 8125, storage SQLite) and on request sets up the autostart and starts. Install more MRtechnology plugins afterwards in the browser under **Server → Plugins**.

> [!TIP]
> Without questions, e.g. for scripts: Linux `… | sh -s -- --memory-min 4G --memory-max 4G --web-url https://admin.example.com --accept-eula --yes` (also `--dir <folder>`, `--no-start`, `--beta` or `--stable`, `--proxy here|docker|<address>`); Windows with the environment variables `MR_DIR`, `MR_MEMORY_MIN`, `MR_MEMORY_MAX`, `MR_WEB_URL`, `MR_PROXY`, `MR_CHANNEL=beta`, `MR_ACCEPT_EULA=1`, `MR_YES=1`, `MR_START=0` before the command. The scripts are public: [install.sh](https://mrtechnology.marcelr.at/install.sh), [install.ps1](https://mrtechnology.marcelr.at/install.ps1).

> [!NOTE]
> Test servers with **dev builds** (every development state) only with the developer's release token: Linux `--dev` (asks for the token) or `MR_DEV_TOKEN`, Windows `MR_DEV_TOKEN`. MRadmin then comes as a dev build, the other plugins follow beta; the token goes into `config.yml` as `versions.dev-token`.

**By hand:**
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

> [!WARNING]
> **Old script with a watchdog or daily restart via cron** (e.g. your own `start.sh` or `mc.sh`): remove the cron entries **before** switching (`crontab -e`) - otherwise the old watchdog starts the server a second time next to the starter. Set daily restarts in `server.restart-times` instead, memory in `server.memory-min` / `memory-max`.

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
| `setup [--accept-eula] [--mode …] [--memory-min 2G] [--memory-max 4G] [--web] [--web-url …] [--proxy here\|docker\|<address>] [--channel stable\|beta\|dev]` | set up the folder, download Paper if missing; MRadmin takes memory, web interface and channel over into `config.yml` on its first start. `--proxy` says where your reverse proxy runs: `here` (this machine, MRadmin only listens locally), `docker` (Docker on this machine: listens on all addresses, trusts `172.16.0.0/12`) or the address/range of another machine. `--channel` only works on a new server (dev needs the token in `MRSERVER_DEV_TOKEN`) |
| `install` · `uninstall` | autostart on · off (a running server keeps running) |
| `start` · `run` | start in the background · start in the current terminal |
| `stop [time\|now]` · `restart [time\|now]` | stop or restart with a countdown (without a time: `server.countdown`, default 15 minutes) |
| `cancel` | cancel a countdown |
| `status` | is the server running? Paper build, mode |
| `console <command>` | send a command to the server console |
| `attach` | show the console (screen) or the output (systemd) |

Times like `30s`, `5m`, `1h`, a number (seconds) or `now`. If MRadmin does not answer (e.g. not installed), `mrserver` stops or restarts right away without a countdown.

## Settings
**In the browser or in `config.yml`** (section `server`, Server → Plugins → MRadmin → Configuration → "Starter & planned restarts" - it also shows at the top whether the starter is set up and active). Memory and Java options apply from the next start on.

| Key | Default | What for |
|---|---|---|
| `server.memory-min` · `memory-max` | `1G` · `4G` | memory of the server |
| `server.java-flags` | empty | extra Java options for tuning (e.g. Aikar's flags) |
| `server.paper-update` | `auto` | `auto` = on every start, `notify` = only show, `off` |
| `server.restart-times` | `[]` | daily restarts, e.g. `["05:00"]` (time zone `general.timezone`) |
| `server.countdown` | `15m` | countdown of planned restarts and of `mrserver stop/restart` without a time |
| `server.plain-stop` | `stop` | what a plain `/stop` does: `stop` = the server stays down, `restart` = it comes back |
| `server.discord-announce` | `false` | countdown of restarts/stops also in the community Discord (the team log always gets it) |
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

## Setting up a waiting room server
Behind a Velocity proxy the starter can also run a **waiting room server** - Paper with [MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/) instead of MRadmin:

```text
MRSERVER_LINK_KEY=<waiting-room.link.key> ./mrserver.sh setup --role waitroom \
    --main-server main --main-address 10.0.0.5:25565 --link-url http://10.0.0.5:8126
```

Or simply use the installer from the website and choose **waiting room server** at the first question. Afterwards `mrserver install|start|stop|restart|status|console` work as usual. Memory, Paper updates and planned restarts are set in MRwaitroom's configuration (section `server`) - most easily via **Server → Plugins → MRwaitroom** in your web interface. Restarts of the waiting room see [Waiting room (proxy)](warteraum.md#restarting-the-waiting-room-server).

> [!NOTE]
> Nobody updates the starter itself automatically on the waiting room (no MRadmin runs there). A new starter version comes with running `mrserver setup --role waitroom` again - rarely needed.
