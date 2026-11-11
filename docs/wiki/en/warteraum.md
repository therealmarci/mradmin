# Waiting room (proxy)

Restarts and maintenance matter to the team, but to players they are an interruption. With a **proxy** (Velocity) and a small second server running **[MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** nobody gets kicked anymore: players wait in the waiting room, see the reason and the time left and come back by themselves.

The waiting room is **optional** and off by default. Without a proxy MRadmin works as before.

## What MRadmin does
- **Before a restart or stop** (countdown from the browser, `/mradmin restart|stop`, planned restarts) MRadmin sends all players to the waiting room right before the server goes down (`waiting-room.on-restart`).
- **When a maintenance starts** players without the maintenance bypass move to the waiting room instead of being kicked - also whoever joins during a maintenance (`waiting-room.on-maintenance`).
- **Info for the waiting room:** through a small endpoint of its own (not the web interface) the waiting room learns the restart countdown and reason, running and planned maintenance and active warnings from **News**. It keeps the last answer, so the info is still there while your server is down.

> [!NOTE]
> What the waiting room shows and when it sends players back is explained in the [guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/).

## Setting up
1. **Proxy:** in `velocity.toml` add the main server and the waiting room under `[servers]`, set `try = ["main", "waitroom"]` (main server first) and `bungee-plugin-message-channel = true`. Switch on Velocity forwarding on both Paper servers - details in the [guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/).
2. **MRadmin:** in `config.yml` (or in the browser under **Server → Plugins → MRadmin → Configuration → Waiting room**):
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
Once the waiting room is connected, **MRwaitroom** shows up under **Server → Plugins** - marked "on the waiting room server":

- **Configuration:** like every plugin via **Configuration** (permission `mradmin.config`). On saving, the waiting room picks up the new `config.yml` on its next question (a few seconds), keeps a backup (`config.backup-remote.yml`) and reloads.
- **Updates:** "Update" (permission `mradmin.versions.install`, or automatically according to `versions.auto-download`) orders the new version. The waiting room downloads it **itself** - only from the official releases and only with the right checksum - and restarts as soon as nobody waits and your server is ready.
- If the waiting room has not reported for a while, it says "Does not report".

> [!NOTE]
> The connection always starts at the waiting room - MRadmin never connects to it. You do the first installation by hand ([guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/)).

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

## FAQ
**What happens on a crash?** Velocity sends the players to the waiting room by itself when the connection to the main server breaks unexpectedly (`failover-on-unexpected-server-disconnect = true`, the default). The waiting room then shows "Server is restarting · since …".

**And with `/stop` in the console?** That is no countdown of MRadmin - Paper kicks the players. Use `/mradmin stop` or `/mradmin restart`, then they move to the waiting room first.
