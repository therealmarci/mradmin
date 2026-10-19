package at.marcelr.mradmin.hook;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bans, mutes, warns and kicks through LibertyBans - optional. This interface knows no LibertyBans class, so MRadmin
 * loads without it; {@link #get} creates the bridge ({@code LibertyBansAccess}) only while LibertyBans runs and
 * integrations.libertybans is on. All methods may block (LibertyBans answers asynchronously) - never call them on the
 * server thread.
 */
public interface Bans {

    /** The punishment types, as LibertyBans names them (also in its permissions: libertybans.&lt;type&gt;...). */
    List<String> TYPES = List.of("ban", "mute", "warn", "kick");

    /**
     * One punishment of a player. end = 0: permanent (kicks have no end). operator = null: the console.
     * active = still in force (not expired, not revoked).
     */
    record Entry(long id, String type, String reason, UUID operator, String scope, long start, long end, boolean active) {
    }

    /** All punishments of the player (newest first, at most {@code limit}). */
    List<Entry> history(UUID player, int limit) throws Exception;

    /** Players with an active ban or mute: uuid -> "ban" / "mute" (for the marks in the player list). */
    Map<UUID, Set<String>> activeBansAndMutes() throws Exception;

    /**
     * Punishes the player like the command would (kick, broadcast to the team, LibertyBans' own messages).
     * @param duration zero = permanent (kicks: always zero)
     * @return null or an error key ("already-punished" if a ban/mute is already active)
     */
    String punish(String type, UUID victim, UUID operator, Duration duration, String reason) throws Exception;

    /** Lifts an active ban, mute or warn by its ID. @return null or an error key ("not-active") */
    String undo(long id, String type) throws Exception;

    // ------------------------------------------------------------------ access

    /** The bridge while LibertyBans runs and the integration is on, otherwise null. */
    static Bans get(MRAdmin plugin) {
        if (!plugin.getConfig().getBoolean("integrations.libertybans", true)) {
            return null;
        }
        Plugin libertyBans = Bukkit.getPluginManager().getPlugin("LibertyBans");
        if (libertyBans == null || !libertyBans.isEnabled()) {
            return null;
        }
        // Created by name, so no LibertyBans class is touched without LibertyBans. Fresh each time (one registry
        // lookup) - that way a /libertybans restart is no problem.
        try {
            return (Bans) Class.forName("at.marcelr.mradmin.hook.LibertyBansAccess").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (!WARNED.getAndSet(true)) {
                plugin.getLogger().warning("LibertyBans found, but its API could not be reached (" + e
                        + ") - the punishments part of the player page stays off.");
            }
            return null;
        }
    }

    java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();

    // ---------------------------------------------------------- duration rules

    /**
     * LibertyBans' duration permissions (its config: duration-permissions.enable / permissions-to-check). Off: every
     * duration is allowed. On: the longest granted libertybans.&lt;type&gt;.dur.&lt;timespan&gt; counts, "perm" for
     * permanent - the same rule as LibertyBans' own commands.
     */
    record DurationRules(boolean enabled, List<String> toCheck) {

        public static DurationRules read() {
            File file = new File(Bukkit.getPluginsFolder(), "LibertyBans/config.yml");
            if (!file.isFile()) {
                return new DurationRules(false, List.of());
            }
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
            return new DurationRules(c.getBoolean("duration-permissions.enable", false),
                    List.copyOf(c.getStringList("duration-permissions.permissions-to-check")));
        }

        /** @param has asks one permission of the member @param duration zero = permanent */
        public boolean allows(String type, Duration duration, java.util.function.Predicate<String> has) {
            if (!enabled || type.equals("kick")) {
                return true;
            }
            Duration best = Duration.ofNanos(-1);
            for (String value : toCheck) {
                Duration d = parse(value);
                if (d == null || !has.test("libertybans." + type + ".dur." + value)) {
                    continue;
                }
                if (d.isZero()) {
                    return true;
                }
                if (d.compareTo(best) > 0) {
                    best = d;
                }
            }
            return !duration.isZero() && !best.isNegative() && best.compareTo(duration) >= 0;
        }
    }

    Pattern PART = Pattern.compile("(\\d+)(mo|y|w|d|h|m|s)");

    /** LibertyBans' timespans: "perm" = zero (permanent), otherwise e.g. 30d, 4h, 1mo, 1y; null if not readable. */
    static Duration parse(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent")) {
            return Duration.ZERO;
        }
        Matcher m = PART.matcher(s);
        Duration total = Duration.ZERO;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return null;
            }
            long n = Long.parseLong(m.group(1));
            total = total.plus(switch (m.group(2)) {
                case "y" -> Duration.ofDays(365 * n);
                case "mo" -> Duration.ofDays(30 * n);
                case "w" -> Duration.ofDays(7 * n);
                case "d" -> Duration.ofDays(n);
                case "h" -> Duration.ofHours(n);
                case "m" -> Duration.ofMinutes(n);
                default -> Duration.ofSeconds(n);
            });
            end = m.end();
        }
        return end == s.length() && end > 0 && !total.isZero() ? total : null;
    }

    /** The quick-select durations from MRadmin's config (libertybans.durations), readable ones only. */
    static List<String> durations(MRAdmin plugin) {
        List<String> result = new ArrayList<>();
        for (String value : plugin.getConfig().getStringList("libertybans.durations")) {
            if (parse(value) != null) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? List.of("1d", "7d", "perm") : result;
    }
}
