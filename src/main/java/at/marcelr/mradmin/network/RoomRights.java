package at.marcelr.mradmin.network;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permissible;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Who may do what around the waiting room (section {@code waiting-room.rights}): a table team role x right, each cell
 * "never", "always" or "admin-mode" (only while in admin mode). Normal players have none of the rights. Only used
 * while the waiting room is on - without it the maintenance keeps its bypass permission.
 * <p>
 * The waiting room gets the table with the status answer and decides the same way (the role comes from the shared
 * LuckPerms there too).
 */
public final class RoomRights {

    /** The team roles of MRadmin, highest first. */
    public enum Role {
        ADMIN("admin", "mradmin.groups.admin"),
        STAFF("staff", "mradmin.groups.staff"),
        SUPPORT("support", "mradmin.groups.support");

        public final String key;
        public final String permission;

        Role(String key, String permission) {
            this.key = key;
            this.permission = permission;
        }
    }

    public enum Right {
        /** During a maintenance stay on (or join) the main server instead of waiting in the waiting room. */
        MAINTENANCE("maintenance"),
        /** Go to the waiting room while nothing is going on (button in the /mradmin menu). */
        VISIT("visit"),
        /** Stay in the waiting room: not sent back to the main server by itself. */
        STAY("stay"),
        /** Switch the admin mode on in the waiting room (item in the hotbar). */
        ADMIN_MODE("admin-mode");

        public final String key;

        Right(String key) {
            this.key = key;
        }
    }

    public enum Value {
        NEVER("never"), ALWAYS("always"), ADMIN_MODE("admin-mode");

        public final String key;

        Value(String key) {
            this.key = key;
        }

        static Value parse(Object raw, Value fallback) {
            String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "never", "no", "false" -> NEVER;
                case "always", "yes", "true" -> ALWAYS;
                case "admin-mode" -> ADMIN_MODE;
                default -> fallback;
            };
        }
    }

    private final Map<Role, Map<Right, Value>> table;

    private RoomRights(Map<Role, Map<Right, Value>> table) {
        this.table = table;
    }

    /** The defaults: supporters and staff nothing, admins only in admin mode (admin mode in the waiting room always). */
    public static Value fallback(Role role, Right right) {
        if (role != Role.ADMIN) {
            return Value.NEVER;
        }
        return right == Right.ADMIN_MODE ? Value.ALWAYS : Value.ADMIN_MODE;
    }

    public static RoomRights from(FileConfiguration c) {
        Map<Role, Map<Right, Value>> table = new EnumMap<>(Role.class);
        ConfigurationSection section = c.getConfigurationSection("waiting-room.rights");
        for (Role role : Role.values()) {
            Map<Right, Value> row = new EnumMap<>(Right.class);
            for (Right right : Right.values()) {
                Object raw = section == null ? null : section.get(role.key + "." + right.key);
                row.put(right, raw == null ? fallback(role, right) : Value.parse(raw, fallback(role, right)));
            }
            table.put(role, row);
        }
        return new RoomRights(table);
    }

    /** The highest team role, null for normal players. */
    public static Role role(Permissible who) {
        return role(who::hasPermission);
    }

    /** The highest team role by a permission check (also for players that are not online yet). */
    public static Role role(Predicate<String> has) {
        for (Role role : Role.values()) {
            if (has.test(role.permission)) {
                return role;
            }
        }
        return null;
    }

    public Value value(Role role, Right right) {
        return role == null ? Value.NEVER : table.get(role).get(right);
    }

    /** Whether the role has the right - with "admin-mode" only while in admin mode. */
    public boolean allows(Role role, Right right, boolean adminMode) {
        return switch (value(role, right)) {
            case NEVER -> false;
            case ALWAYS -> true;
            case ADMIN_MODE -> adminMode;
        };
    }

    /** For the status answer: role -> right -> value. */
    public Map<String, Map<String, String>> json() {
        Map<String, Map<String, String>> json = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Right right : Right.values()) {
                row.put(right.key, table.get(role).get(right).key);
            }
            json.put(role.key, row);
        }
        return json;
    }
}
