package at.marcelr.mradmin.web.auth;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.maintenance.LuckPermsHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permissions of web users, who are usually not online: with LuckPerms its stored permissions (also offline), otherwise
 * the roles from {@code web.roles} / {@code web.players}, and operators may everything. Results are cached for a minute,
 * so a click in the web does not ask LuckPerms every time. Call it from web threads (LuckPerms may load the user).
 */
public final class WebPermissions {

    public static final String PERM_WEB = at.marcelr.mradmin.Perms.WEB;
    private static final long CACHE_MILLIS = 60_000L;

    private record Cached(boolean value, long until) {
    }

    private final MRAdmin plugin;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public WebPermissions(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean has(UUID player, String permission) {
        String key = player + "|" + permission;
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached != null && cached.until() > now) {
            return cached.value();
        }
        boolean value = check(player, permission);
        cache.put(key, new Cached(value, now + CACHE_MILLIS));
        return value;
    }

    /** After a reload or permission change: ask again. */
    public void clear() {
        cache.clear();
    }

    private boolean check(UUID player, String permission) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                Boolean value = LuckPermsHook.check(player, permission);
                // Not set in LuckPerms: like on the server, the plugin.yml default (op) decides.
                return value != null ? value : byDefault(permission, offline.isOp());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check " + permission + " of " + player + " with LuckPerms: " + e.getMessage());
                return false;
            }
        }
        Settings.WebSettings web = plugin.settings().web();
        String role = web.players().get(player.toString());
        if (role == null && offline.getName() != null) {
            role = web.players().get(offline.getName().toLowerCase(Locale.ROOT));
        }
        if (role != null && matches(web.roles().getOrDefault(role, List.of()), permission)) {
            return true;
        }
        return byDefault(permission, offline.isOp());
    }

    /** The default from the plugin.yml (true, op, not op, false); unknown permissions only for operators. */
    private static boolean byDefault(String permission, boolean op) {
        org.bukkit.permissions.Permission perm = Bukkit.getPluginManager().getPermission(permission);
        return perm == null ? op : perm.getDefault().getValue(op);
    }

    /**
     * "*" grants everything, "mradmin.*" everything below mradmin., otherwise the permission itself or a bundle that
     * contains it (as in the plugin.yml, e.g. mradmin.groups.support -> mradmin.tickets.reply).
     */
    static boolean matches(List<String> granted, String permission) {
        for (String g : granted) {
            String p = g.trim().toLowerCase(Locale.ROOT);
            if (p.equals("*") || p.equals(permission)
                    || (p.endsWith(".*") && permission.startsWith(p.substring(0, p.length() - 1)))
                    || contains(p, permission, 0)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the bundle {@code node} grants {@code permission} through its children (a few levels deep). */
    private static boolean contains(String node, String permission, int depth) {
        org.bukkit.permissions.Permission perm = Bukkit.getPluginManager().getPermission(node);
        if (perm == null || depth > 5) {
            return false;
        }
        for (Map.Entry<String, Boolean> child : perm.getChildren().entrySet()) {
            if (child.getValue() && (child.getKey().equals(permission) || contains(child.getKey(), permission, depth + 1))) {
                return true;
            }
        }
        return false;
    }
}
