# Announcements

```text
/mradmin announce Event at spawn tonight!
/mradmin announce Attention | The Nether will be reset shortly
/mradmin announce restart 5 minutes
```

- **Own text** appears as a title on the screen. With `|` you separate **title | subtitle**. Colors in [MiniMessage format](https://docs.advntr.dev/minimessage/format.html) are allowed.
- You create **your own templates** in the web interface under **Communication → Announcements** (`mradmin.announce.templates`): title, subtitle, chat and action bar each in German and English with a preview, optionally a Discord text, display time of the title, sound and **audience** (players, bots, support, staff, admin - every group also gets what is meant for the ones below; "players" = everyone). They work in the web, in the announcement menu and with `/mradmin announce <name>`. Values after the name fill `<arg1>`, `<arg2>` … or `<args>`.
- **Built-in templates** (`restart`, `restart-now`, `update`, `event`) are in the language file under `announce-presets`. MRadmin uses `restart` itself (restart countdown); they are not in the menus, but keep working with the command and in scripts.
- Every player sees the announcement in their language. `-q` plays no sound.

Announcing needs `mradmin.announce`, the settings are under `announce` in the [configuration](konfiguration.md).

## Example: restart script with a countdown
```bash
mcrcon -p "$RCON_PW" "mradmin announce restart 5 minutes"; sleep 240
mcrcon -p "$RCON_PW" "mradmin announce restart 1 minute";  sleep 60
mcrcon -p "$RCON_PW" "mradmin announce restart-now" "stop"
```

## On the community Discord
An **own webhook** for your public channel (`discord-announce`), separate from the admin log. It posts sparingly:
- **One message per maintenance**, at most 24 hours before (`announce-ahead`). It is kept up to date instead of posted again: **"Maintenance scheduled"** → **"Maintenance running"** → **"Server back online"** with the actual time (with a note on an early end) or **"Maintenance cancelled"**. New times are written into the same message. The role from `ping-role-id` is only mentioned in the first post.
- **Announcements**, if the template has a `discord` text. Own texts only with `-d`: `/mradmin announce -d Event tonight!`. With `-q` a template does not go to Discord.
- Optionally **news and changelogs** of the player page and **incidents from Uptime Kuma** (`discord-announce.kuma`).

Times appear as Discord timestamps - everyone sees them in their time zone. Test: `/mradmin discord test announce`.

> [!NOTE]
> Discord does not notify about edited messages. So the start and end of a maintenance is seen by whoever looks into the channel - the ping only comes when the maintenance is posted for the first time.
