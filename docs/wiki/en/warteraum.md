# Waiting room (proxy)

Restarts and maintenance matter to the team, but to players they are an interruption. With a **proxy** (Velocity) and a small second server running **[MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** nobody gets kicked anymore: players wait in the waiting room, see the reason and the time left and come back by themselves.

The waiting room is **optional** and off by default. Without a proxy MRadmin works as before.

## What MRadmin does
- **Before a restart or stop** (countdown from the browser, `/mradmin restart|stop`, planned restarts) MRadmin sends all players to the waiting room right before the server goes down (`waiting-room.on-restart`).
- **When a maintenance starts** everyone who may not stay by the [rights](#rights-around-the-waiting-room) moves to the waiting room instead of being kicked - also whoever joins during a maintenance (`waiting-room.on-maintenance`).
- **The announcements say so:** the countdown to a restart or stop and the maintenance announcements in the chat end with "No need to leave." (placeholder `<stay>`, only with the waiting room) - so nobody leaves the server beforehand.
- **No second welcome:** whoever comes back from the waiting room is no new arrival - notices like "still in admin mode", team notices, tickets and update notices do not come again.
- **Info for the waiting room:** through a small endpoint of its own (not the web interface) the waiting room learns the restart countdown and reason, running and planned maintenance and active warnings from **News**. It keeps the last answer, so the info is still there while your server is down.

> [!NOTE]
> What the waiting room shows and when it sends players back is explained in the [guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/).

## Setting up
1. **Proxy:** in `velocity.toml` add the main server and the waiting room under `[servers]`, set `try = ["main", "waitroom"]` (main server first) and `bungee-plugin-message-channel = true`. Switch on Velocity forwarding on both Paper servers - details in the [guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/).
2. **MRadmin:** in `config.yml` (or in the browser under **Settings → Configuration → MRadmin → Waiting room**):
   ```yaml
   waiting-room:
     enabled: true
     server: "waitroom"        # name of the waiting room in velocity.toml
     link:
       bind: "10.0.0.5"        # internal address of this machine
       port: 8126
       key: ""                 # created on the first start
   ```
3. `/mradmin reload` - MRadmin creates the key (`waiting-room.link.key`) and logs `Waiting room link listening on …`.
4. Copy the address (`http://10.0.0.5:8126`) and the key into the `config.yml` of MRwaitroom (`main.link-url`, `main.link-key`).

> [!WARNING]
> `link.bind` belongs on the **internal** address (LAN), never on a public one. The endpoint only tells what players in the waiting room see anyway, but it does not belong on the internet. Wrong keys are refused after 10 tries per minute.

## Managing the waiting room from MRadmin
Once the waiting room is connected, **MRwaitroom** shows up under **Server → Waiting room → Plugins** - marked "waiting room server":

- **Configuration:** like every plugin via **Configuration** (permission `mradmin.config`). On saving, the waiting room picks up the new `config.yml` on its next question (a few seconds), keeps a backup (`config.backup-remote.yml`) and reloads.
- **Updates:** "Update" (permission `mradmin.versions.install`, or automatically according to `versions.auto-download`) orders the new version. The waiting room downloads it **itself** - only from the official releases and only with the right checksum - and restarts as soon as nobody waits and your server is ready.
- If the waiting room has not reported for a while, it says "Does not report".

> [!NOTE]
> The waiting room fetches configuration and updates from MRadmin itself. For performance, log and console (below) MRadmin asks the waiting room on a port of its own. You do the first installation by hand ([guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/)).

## Performance of the waiting room
Under **Server → Waiting room → Performance** you see the waiting room server exactly like your own under **Server → Main server → Performance**: TPS, milliseconds per tick, CPU, memory, players, worlds and the chunks with the most entities, with the same ranges (1 minute to 30 days). Permission as for the main server (`mradmin.status.health`).

- MRadmin asks the waiting room **every second** and records like for the main server: the last hour per second, 24 hours as 10-second points, longer as 5-minute points that are stored (`health.history`).
- If the waiting room lags for a while, the same lag warning as for the main server comes, only with "waiting room" in the text (in the game and as push, kind "Lag").
- MRadmin finds the address by itself: the waiting room reports its port (`agent.port`, default 8127) and MRadmin takes the address its reports come from. To set it fixed, use `waiting-room.agent-url` (e.g. `http://10.0.0.6:8127`).

> [!TIP]
> The port needs to be allowed in the network between the two servers (firewall) from the main server to the waiting room. Best let it listen only on the internal address (`agent.bind` in MRwaitroom).

## Log and console of the waiting room
- **Server → Waiting room → Log** shows the events of the waiting room (started, restart, main server gone and back, configuration and updates from MRadmin) and its server log of the last 14 days - with search, range and chart like for the main server. MRadmin keeps the events in its own log (`logging.keep-days`); they do not show in the log of the main server.
- **Server → Waiting room → Console** shows the output live and takes commands - with the same rules as for the main server: permission `mradmin.web.console.execute`, unlocking with a 2FA code or passkey (counts for both consoles), block list `web.console.blocked`, every command in the web log (with "[waitroom]"). Links and IP addresses are hidden like for the main server.
- In the waiting room, commands from MRadmin can be switched off completely with `agent.console-commands: false` - the console then only shows the output.

## Controlling the waiting room server
**Server → Waiting room → Control** controls the waiting room server like the main server (permission `mradmin.server.restart`):

- **Values:** state (with waiting players), up for, Paper build (yellow if a newer build for the same Minecraft version exists - the starter puts it in on the next start), starter, next planned restart.
- **Restart** and **Stop** in 15, 30 or 60 minutes (`versions.restart-delays`), optionally with a **reason**. Shorter times through **Emergency** - first confirm again (2FA code or passkey), then a required reason (`versions.emergency-delays`).
- A running countdown shows with clock, who started it and the reason; **Cancel** ends it.
- With planned restarts (`server.restart-times` in MRwaitroom): **Pause planned restarts for 24 h** / **Switch on again**.
- Below the plugins of the waiting room and its latest events.

A **restart** still waits at the time until it may:

- your server runs and is ready,
- **no maintenance** runs,
- **no restart of your server** comes within the next 15 minutes,
- **nobody waits** in the waiting room (at most 10 minutes, then it is given up).

If one of these is not met already when clicking, the button is locked and the reason shows below. A **stop** and an **emergency restart** do not wait - waiting players then go to your server through the proxy. Without the starter the waiting room stays down after a stop.

**While the waiting room restarts**, MRadmin refuses restarts and stops of your server (browser, `/mradmin restart|stop`, `mrserver`) - players would have nowhere to wait. That usually takes only a few seconds.

## Hints and warnings
- The **overview** (dashboard) and the counters in the sidebar include the waiting room: not reporting (red), lagging, a countdown running.
- The lag warning comes in the game, as push (kind "Lag") and in the Discord log (events `waitroom-health-alert` and `waitroom-health-recovered` in `discord.events`).
- Under **Server → Waiting room → Plugins** MRwaitroom can be managed, and below - for viewing only - **all plugins** of the waiting room server with their version.

## Rights around the waiting room
What the team may do around the waiting room is set in a **table**: MRadmin's configuration → **Waiting room** → **Rights per role** (`waiting-room.rights`). The rows are the team roles **Admin**, **Staff** and **Supporter** (the highest of `mradmin.groups.admin`, `.staff`, `.support` counts), every cell is **No**, **Yes** or **Only in admin mode**. Normal players have none of the rights.

| Right | Meaning | Admin | Staff | Supporter |
|---|---|---|---|---|
| **On the server during maintenance** | Stays on your server during a maintenance or gets in - otherwise you wait in the waiting room | Only in admin mode | No | No |
| **Visit the waiting room** | Button **To the waiting room** in the admin menu (`/mradmin`), also when nothing is going on | Only in admin mode | No | No |
| **Stay in the waiting room** | Not sent back by itself; back with the item **Back to the server** | Only in admin mode | No | No |
| **Admin mode in the waiting room** | Item **Admin mode** in the hotbar of the waiting room | Yes | No | No |

With the defaults an **admin without admin mode is a normal player**: during a maintenance they end up in the waiting room and are sent back like everyone afterwards. **On restarts everyone always moves** to the waiting room - that is no right. The table only applies while the waiting room is on; without it the maintenance keeps its bypass (`mradmin.maintenance.bypass`). The waiting room gets the table from MRadmin, you set none of it there.

### Admin mode across the network
The admin mode counts on **both** servers: whoever switches it on in the waiting room arrives on your server in admin mode (inventory saved as usual); whoever switches it off there gets their inventory back when coming back. The other way round, whoever is in admin mode on your server is in admin mode in the waiting room too. `admin-mode.end-on-quit` has no effect with the waiting room - moving to the waiting room is not leaving.

> [!TIP]
> A maintenance is running and you want onto the server? In the waiting room switch the admin mode on with the item on the left of the hotbar, then go over with **Back to the server** (right).

> [!TIP]
> The other way round MRadmin takes care too: if the waiting room is restarting itself when your server's countdown ends, MRadmin waits up to 2 minutes for it before going down.

## Settings
| Key | Default | Meaning |
|---|---|---|
| `waiting-room.enabled` | `false` | Use the waiting room |
| `waiting-room.server` | `waitroom` | Name of the waiting room in the proxy |
| `waiting-room.on-restart` | `true` | Send everyone to the waiting room before a restart or stop |
| `waiting-room.on-maintenance` | `true` | Waiting room instead of a kick during maintenance |
| `waiting-room.link.bind` | `127.0.0.1` | Address of the endpoint (empty = none) |
| `waiting-room.link.port` | `8126` | Port of the endpoint |
| `waiting-room.link.key` | (created) | Shared key with the waiting room |
| `waiting-room.rights` | see above | Rights per team role ([Rights around the waiting room](#rights-around-the-waiting-room)) |

## FAQ
**What happens on a crash?** Velocity sends the players to the waiting room by itself when the connection to the main server breaks unexpectedly (`failover-on-unexpected-server-disconnect = true`, the default). The waiting room then shows "Server is restarting · since …".

**And with `/stop` in the console?** That is no countdown of MRadmin - Paper kicks the players. Use `/mradmin stop` or `/mradmin restart`, then they move to the waiting room first.
