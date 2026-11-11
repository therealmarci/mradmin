# Waiting room (proxy)

Restarts and maintenance matter to the team, but to players they are an interruption. With a **proxy** (Velocity) and a small second server running **[MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** nobody gets kicked anymore: players wait in the waiting room, see the reason and the time left and come back by themselves.

The waiting room is **optional** and off by default. Without a proxy MRadmin works as before.

## What MRadmin does
- **Before a restart or stop** (countdown from the browser, `/mradmin restart|stop`, planned restarts) MRadmin sends all players to the waiting room right before the server goes down (`waiting-room.on-restart`).
- **When a maintenance starts** everyone who may not stay by the [rights](#rights-around-the-waiting-room) moves to the waiting room instead of being kicked - also whoever joins during a maintenance (`waiting-room.on-maintenance`).
- **The announcements say so:** restart, stop and maintenance messages in the chat end with "No need to leave." (placeholder `<stay>`, only with the waiting room) - so nobody leaves the server beforehand.
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
Once the waiting room is connected, **MRwaitroom** shows up under **Server → Plugins** - marked "waiting room server":

- **Configuration:** like every plugin via **Configuration** (permission `mradmin.config`). On saving, the waiting room picks up the new `config.yml` on its next question (a few seconds), keeps a backup (`config.backup-remote.yml`) and reloads.
- **Updates:** "Update" (permission `mradmin.versions.install`, or automatically according to `versions.auto-download`) orders the new version. The waiting room downloads it **itself** - only from the official releases and only with the right checksum - and restarts as soon as nobody waits and your server is ready.
- If the waiting room has not reported for a while, it says "Does not report".

> [!NOTE]
> The connection always starts at the waiting room - MRadmin never connects to it. You do the first installation by hand ([guide of MRwaitroom](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/)).

## Restarting the waiting room server
If the waiting room runs with the starter (`mrserver setup --role waitroom`, see [Starter](starter.md#setting-up-a-waiting-room-server)), **Server → Overview** shows the tile **Waiting room server**: running, since when, Paper build, waiting players, next planned restart. **Restart** (permission `mradmin.server.restart`) restarts it **as soon as it may**:

- your server runs and is ready,
- **no maintenance** runs,
- **no restart of your server** comes within the next 15 minutes,
- **nobody waits** in the waiting room.

If one of these is not met, the tile shows the reason. Planned restarts are set in MRwaitroom's configuration (`server.restart-times`) - they wait in the same way until it fits.

**While the waiting room restarts**, MRadmin refuses restarts and stops of your server (browser, `/mradmin restart|stop`, `mrserver`) - players would have nowhere to wait. That usually takes only a few seconds.

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
