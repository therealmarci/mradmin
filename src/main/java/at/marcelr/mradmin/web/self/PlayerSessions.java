package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.web.auth.Secrets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sign-in of players to their own page (the player area of the web interface, see {@link SelfRoutes}) - separate from
 * the team's web accounts. There is no password: the page (always the same address, it can be bookmarked) asks for a
 * 6-digit code that {@code /web} gives in game (valid {@code player-web.code-seconds}, once). A signed-in browser
 * stays signed in for at most {@code player-web.session-days} after the code, and is signed out earlier after
 * {@code player-web.idle-days} without use - then the page asks for a new code. Only hashes of codes and cookie tokens
 * are kept. Guessing is limited per address (in {@link SelfRoutes}) and for everyone together here. Thread-safe (web
 * threads and the server thread).
 */
public final class PlayerSessions {

    private static final String FILE = "player-sessions";
    /** Every use of a session counts at once, but it is written to disk at most once per hour (or on a new address). */
    private static final long SEEN_EVERY = 3_600_000L;
    /** Wrong codes from all addresses together per minute - more and every code is refused for a moment. */
    private static final int MAX_WRONG_PER_MINUTE = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A signed-in browser of a player. {@code id} is public (device list), the token only stored hashed.
     * {@code verified}: when the code was entered; {@code expires}: the earlier of verified + session-days and
     * lastSeen + idle-days.
     */
    public record Session(String id, String hash, UUID player, String name, long created, long verified, long lastSeen,
                          long expires, String ip, String agent) {
    }

    /** Result of redeeming a code: the cookie token, or an error key. */
    public record Result(String token, Session session, String error) {
    }

    private record Code(UUID player, String name, long expires) {
    }

    private final MRAdmin plugin;
    /** hash of the cookie token -> session */
    private final Map<String, Session> sessions = new HashMap<>();
    /** hash of the code -> who it signs in (one per player) */
    private final Map<String, Code> codes = new HashMap<>();
    /** times of wrong codes in the last minute (all addresses) */
    private final Deque<Long> wrong = new ArrayDeque<>();
    /** when the uses were last written to disk */
    private long seenSaved;

    public PlayerSessions(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** When a session verified at {@code verified} and last used at {@code lastSeen} runs out. */
    private long expiry(long verified, long lastSeen) {
        var s = plugin.settings().playerWeb();
        return Math.min(verified + s.sessionDays() * 86_400_000L, lastSeen + s.idleDays() * 86_400_000L);
    }

    /** When the session runs out at the latest (code + session-days) and when without use (last use + idle-days). */
    public long[] limits(Session s) {
        var settings = plugin.settings().playerWeb();
        return new long[] {s.verified() + settings.sessionDays() * 86_400_000L, s.lastSeen() + settings.idleDays() * 86_400_000L};
    }

    /** Seconds a signed-in browser may keep its cookie at most (session-days). */
    public long cookieSeconds() {
        return plugin.settings().playerWeb().sessionDays() * 86_400L;
    }

    // --------------------------------------------------------------- codes

    /** A new 6-digit code for the player; an older one of them is no longer valid. Unique among the valid codes. */
    public synchronized String code(UUID player, String name) {
        long now = System.currentTimeMillis();
        codes.values().removeIf(c -> c.expires() < now || c.player().equals(player));
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        } while (codes.containsKey(Secrets.sha256(code)));
        codes.put(Secrets.sha256(code), new Code(player, name, now + plugin.settings().playerWeb().codeSeconds() * 1000L));
        return code;
    }

    /**
     * Redeems a code (spaces and the like are ignored): a new session for this browser. Too many devices: the one
     * used longest ago is signed out. Errors: code-invalid, rate-limit (too many wrong codes from everyone).
     */
    public synchronized Result redeem(String raw, String ip, String agent) {
        long now = System.currentTimeMillis();
        while (!wrong.isEmpty() && wrong.peekFirst() < now - 60_000L) {
            wrong.pollFirst();
        }
        if (wrong.size() >= MAX_WRONG_PER_MINUTE) {
            return new Result(null, null, "rate-limit");
        }
        String code = raw == null ? "" : raw.replaceAll("\\D", "");
        Code c = code.length() == 6 ? codes.remove(Secrets.sha256(code)) : null;
        if (c == null || c.expires() < now) {
            wrong.addLast(now);
            return new Result(null, null, "code-invalid");
        }
        String token = Secrets.token(32);
        Session session = new Session(Secrets.token(9), Secrets.sha256(token), c.player(), c.name(), now, now, now,
                expiry(now, now), ip, agent == null ? "" : agent.length() > 200 ? agent.substring(0, 200) : agent);
        sessions.put(session.hash(), session);
        List<Session> own = devices(c.player());
        int max = plugin.settings().playerWeb().maxDevices();
        for (int i = max; i < own.size(); i++) {
            sessions.remove(own.get(i).hash());
        }
        save();
        return new Result(token, session, null);
    }

    // ------------------------------------------------------------ sessions

    /** The session of a cookie (its use noted), or null if unknown or run out (session-days or idle-days). */
    public synchronized Session session(String token, String ip) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String hash = Secrets.sha256(token);
        Session s = sessions.get(hash);
        long now = System.currentTimeMillis();
        if (s == null) {
            return null;
        }
        // Again from the settings, so a shorter session-days or idle-days applies to signed-in browsers at once.
        if (expiry(s.verified(), s.lastSeen()) < now) {
            sessions.remove(hash);
            save();
            return null;
        }
        boolean moved = !s.ip().equals(ip);
        s = new Session(s.id(), s.hash(), s.player(), s.name(), s.created(), s.verified(), now,
                expiry(s.verified(), now), ip, s.agent());
        sessions.put(hash, s);
        if (moved || now - seenSaved >= SEEN_EVERY) {
            seenSaved = now;
            save();
        }
        return s;
    }

    /** The player's signed-in browsers, newest first. */
    public synchronized List<Session> devices(UUID player) {
        List<Session> result = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (s.player().equals(player)) {
                result.add(s);
            }
        }
        result.sort(Comparator.comparingLong(Session::lastSeen).reversed());
        return result;
    }

    /** Signs one browser of the player out (by its public id). @return whether it existed */
    public synchronized boolean revoke(UUID player, String id) {
        boolean removed = sessions.values().removeIf(s -> s.player().equals(player) && s.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    /** Signs all browsers of the player out. @return how many */
    public synchronized int revokeAll(UUID player) {
        int before = sessions.size();
        sessions.values().removeIf(s -> s.player().equals(player));
        codes.values().removeIf(c -> c.player().equals(player));
        int removed = before - sessions.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    /** Signs out the browser with this cookie. */
    public synchronized void logout(String token) {
        if (token != null && sessions.remove(Secrets.sha256(token)) != null) {
            save();
        }
    }

    // ------------------------------------------------------------- storage

    public synchronized void load() {
        sessions.clear();
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        ConfigurationSection list = y.getConfigurationSection("sessions");
        long now = System.currentTimeMillis();
        if (list == null) {
            return;
        }
        for (String hash : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(hash);
            try {
                Session session = new Session(s.getString("id"), hash, UUID.fromString(s.getString("player")),
                        s.getString("name", "?"), s.getLong("created"), s.getLong("verified", s.getLong("created")),
                        s.getLong("last-seen"), 0,
                        s.getString("ip", ""), s.getString("agent", ""));
                if (expiry(session.verified(), session.lastSeen()) > now) {
                    sessions.put(hash, session);
                }
            } catch (RuntimeException ignored) {
                // broken entry - that browser simply signs in again
            }
        }
    }

    /** Writes the uses noted since the last save (on shutdown). */
    public synchronized void flush() {
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - browsers of players signed in to their own page (managed by the plugin, do not edit)."));
        long now = System.currentTimeMillis();
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (expiry(s.verified(), s.lastSeen()) < now) {
                it.remove();
                continue;
            }
            String p = "sessions." + s.hash() + ".";
            y.set(p + "id", s.id());
            y.set(p + "player", s.player().toString());
            y.set(p + "name", s.name());
            y.set(p + "created", s.created());
            y.set(p + "verified", s.verified());
            y.set(p + "last-seen", s.lastSeen());
            y.set(p + "expires", expiry(s.verified(), s.lastSeen()));
            y.set(p + "ip", s.ip());
            y.set(p + "agent", s.agent());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}
