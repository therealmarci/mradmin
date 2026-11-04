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
        defaultsUntil = 0;
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

    /**
     * Like Bukkit for a player without permissions of their own: the plugin.yml defaults (true, op, not op, false)
     * together with the children they bring - an operator gets e.g. mradmin.web.console.execute through
     * mradmin.groups.admin (default op), although that one is "default: false" itself. Unknown permissions only
     * for operators.
     */
    private boolean byDefault(String permission, boolean op) {
        Boolean value = defaults(op).get(permission.toLowerCase(Locale.ROOT));
        if (value != null) {
            return value;
        }
        return Bukkit.getPluginManager().getPermission(permission) == null && op;
    }

    private volatile java.util.Map<String, Boolean> opDefaults;
    private volatile java.util.Map<String, Boolean> playerDefaults;
    private volatile long defaultsUntil;

    /** All permissions an operator (or a normal player) has by default, children included; built again every minute. */
    private java.util.Map<String, Boolean> defaults(boolean op) {
        long now = System.currentTimeMillis();
        if (now > defaultsUntil || opDefaults == null) {
            opDefaults = resolve(true);
            playerDefaults = resolve(false);
            defaultsUntil = now + CACHE_MILLIS;
        }
        return op ? opDefaults : playerDefaults;
    }

    private static java.util.Map<String, Boolean> resolve(boolean op) {
        java.util.Map<String, Boolean> out = new java.util.HashMap<>();
        for (org.bukkit.permissions.Permission perm : Bukkit.getPluginManager().getDefaultPermissions(op)) {
            out.put(perm.getName().toLowerCase(Locale.ROOT), true);
            children(perm.getChildren(), true, out, 0);
        }
        return java.util.Map.copyOf(out);
    }

    /** As in Bukkit's PermissibleBase: a child is set to its value, inverted below a parent that is false. */
    private static void children(java.util.Map<String, Boolean> children, boolean parent, java.util.Map<String, Boolean> out, int depth) {
        if (depth > 32) {
            return;
        }
        for (java.util.Map.Entry<String, Boolean> child : children.entrySet()) {
            boolean value = child.getValue() == parent;
            String name = child.getKey().toLowerCase(Locale.ROOT);
            out.put(name, value);
            org.bukkit.permissions.Permission perm = Bukkit.getPluginManager().getPermission(name);
            if (perm != null) {
                children(perm.getChildren(), value, out, depth + 1);
            }
        }
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
