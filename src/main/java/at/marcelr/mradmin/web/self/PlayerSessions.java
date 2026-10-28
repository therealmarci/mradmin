package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.web.auth.Secrets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sign-in of players to their own page (the player area of the web interface, see {@link SelfRoutes}) - separate from
 * the team's web accounts. There is no password: {@code /web} in game gives a link with a one-time code (a few minutes,
 * once), and opening it signs that browser in for {@code player-web.session-days}. Every use extends it, so a
 * bookmark of the page keeps working. Only hashes of codes and cookie tokens are kept. Thread-safe (web threads and the
 * server thread).
 */
public final class PlayerSessions {

    private static final String FILE = "player-sessions";
    /** Extend a session at most once per hour (and save then), not on every request. */
    private static final long EXTEND_EVERY = 3_600_000L;

    /** A signed-in browser of a player. {@code id} is public (device list), the token only stored hashed. */
    public record Session(String id, String hash, UUID player, String name, long created, long lastSeen, long expires,
                          String ip, String agent) {
    }

    /** Result of redeeming a code: the cookie token, or an error key. */
    public record Result(String token, Session session, String error) {
    }

    private record Code(UUID player, String name, long expires) {
    }

    private final MRAdmin plugin;
    /** hash of the cookie token -> session */
    private final Map<String, Session> sessions = new HashMap<>();
    /** hash of the one-time code -> who it signs in */
    private final Map<String, Code> codes = new HashMap<>();

    public PlayerSessions(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private long sessionMillis() {
        return plugin.settings().playerWeb().sessionDays() * 86_400_000L;
    }

    // --------------------------------------------------------------- codes

    /** A new one-time code for the player (an older unused one stays valid until it runs out). */
    public synchronized String code(UUID player, String name) {
        long now = System.currentTimeMillis();
        codes.values().removeIf(c -> c.expires() < now);
        String code = Secrets.token(18);
        codes.put(Secrets.sha256(code), new Code(player, name,
                now + plugin.settings().playerWeb().linkMinutes() * 60_000L));
        return code;
    }

    /** Redeems a one-time code: a new session for this browser. Too many devices: the oldest one is signed out. */
    public synchronized Result redeem(String code, String ip, String agent) {
        if (code == null || code.isBlank()) {
            return new Result(null, null, "code-invalid");
        }
        Code c = codes.remove(Secrets.sha256(code.strip()));
        long now = System.currentTimeMillis();
        if (c == null || c.expires() < now) {
            return new Result(null, null, "code-invalid");
        }
        String token = Secrets.token(32);
        Session session = new Session(Secrets.token(9), Secrets.sha256(token), c.player(), c.name(), now, now,
                now + sessionMillis(), ip, agent == null ? "" : agent.length() > 200 ? agent.substring(0, 200) : agent);
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

    /** The session of a cookie, extended if used (sliding), or null if unknown or run out. */
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
        if (s.expires() < now) {
            sessions.remove(hash);
            save();
            return null;
        }
        if (now - s.lastSeen() >= EXTEND_EVERY || !s.ip().equals(ip)) {
            s = new Session(s.id(), s.hash(), s.player(), s.name(), s.created(), now, now + sessionMillis(), ip, s.agent());
            sessions.put(hash, s);
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
                        s.getString("name", "?"), s.getLong("created"), s.getLong("last-seen"), s.getLong("expires"),
                        s.getString("ip", ""), s.getString("agent", ""));
                if (session.expires() > now) {
                    sessions.put(hash, session);
                }
            } catch (RuntimeException ignored) {
                // broken entry - that browser simply signs in again
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - browsers of players signed in to their own page (managed by the plugin, do not edit)."));
        long now = System.currentTimeMillis();
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (s.expires() < now) {
                it.remove();
                continue;
            }
            String p = "sessions." + s.hash() + ".";
            y.set(p + "id", s.id());
            y.set(p + "player", s.player().toString());
            y.set(p + "name", s.name());
            y.set(p + "created", s.created());
            y.set(p + "last-seen", s.lastSeen());
            y.set(p + "expires", s.expires());
            y.set(p + "ip", s.ip());
            y.set(p + "agent", s.agent());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}
