package at.marcelr.mradmin.web.deck;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.web.auth.Secrets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Connected Stream Decks (the MRadmin plugin for the Stream Deck). A Stream Deck gets its own key, stored here only
 * hashed; it is valid until the player signs it out (Account -> Devices, /web reset) - unlike browsers it does not
 * run out, it is a device on the desk.
 * <p>
 * Two ways to connect:
 * <ul>
 *   <li>Code: the Stream Deck asks for a code ({@link #start}) and shows it; the player confirms it in game
 *   (/web link &lt;code&gt;) or in the browser (/link?code=...). The Stream Deck asks every few seconds
 *   ({@link #poll}) and picks up its key once.</li>
 *   <li>Key: made under Account -> Devices ({@link #createKey}), shown once, pasted into the Stream Deck.</li>
 * </ul>
 * Thread-safe (web threads and the server thread).
 */
public final class DeckDevices {

    private static final String FILE = "deck-devices";
    /** Letters and digits that cannot be mixed up (no 0/O, 1/I/L). */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final long CODE_MILLIS = 5 * 60_000L;
    /** A confirmed key waits this long for the Stream Deck to pick it up. */
    private static final long PICKUP_MILLIS = 2 * 60_000L;
    private static final int MAX_PENDING = 200;
    private static final long SEEN_EVERY = 3_600_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A connected Stream Deck. {@code id} is public (device list), the key only stored hashed. {@code via}: "code" or
     * "key".
     */
    public record Device(String id, String hash, UUID player, String playerName, String name, long created,
                         long lastSeen, String ip, String via) {
    }

    /** A Stream Deck waiting for its code to be confirmed. */
    public record Pending(String code, String poll, String name, String ip, long created, long expires,
                          UUID player, String playerName, String token) {

        /** The code as shown: K7Q-4MX. */
        public String shown() {
            return code.substring(0, 3) + "-" + code.substring(3);
        }

        boolean confirmed() {
            return token != null;
        }
    }

    /** What a waiting Stream Deck learns: "pending", "done" (with the key, once) or "expired". */
    public record Poll(String state, String token, String playerName) {
    }

    private final MRAdmin plugin;
    /** hash of the key -> device */
    private final Map<String, Device> devices = new HashMap<>();
    /** code (without dash) -> waiting Stream Deck */
    private final Map<String, Pending> pending = new HashMap<>();
    private long seenSaved;

    public DeckDevices(MRAdmin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ connecting

    /** Normalises a typed code: upper case, only the letters of the alphabet ("k7q 4mx" -> "K7Q4MX"). */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (ALPHABET.indexOf(c) >= 0) {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** A new code for a Stream Deck that wants to connect, or null if too many wait at the moment. */
    public synchronized Pending start(String name, String ip) {
        long now = System.currentTimeMillis();
        prune(now);
        if (pending.size() >= MAX_PENDING) {
            return null;
        }
        String code;
        do {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = b.toString();
        } while (pending.containsKey(code));
        Pending p = new Pending(code, Secrets.token(24), clean(name), ip, now, now + CODE_MILLIS, null, null, null);
        pending.put(code, p);
        return p;
    }

    /** The waiting Stream Deck of a code that is still open, or null. */
    public synchronized Pending byCode(String raw) {
        Pending p = pending.get(normalise(raw));
        return p == null || p.confirmed() || p.expires() < System.currentTimeMillis() ? null : p;
    }

    /**
     * Confirms a code for the player: the Stream Deck becomes theirs and gets its key at its next poll.
     * @return the new device, or null if the code is unknown, run out or already used
     */
    public synchronized Device confirm(String raw, UUID player, String playerName) {
        Pending p = byCode(raw);
        if (p == null) {
            return null;
        }
        String token = Secrets.token(32);
        Device device = add(token, player, playerName, p.name(), p.ip(), "code");
        pending.put(p.code(), new Pending(p.code(), p.poll(), p.name(), p.ip(), p.created(),
                System.currentTimeMillis() + PICKUP_MILLIS, player, playerName, token));
        return device;
    }

    /** The Stream Deck asks whether its code was confirmed; the key is handed out once. */
    public synchronized Poll poll(String pollId) {
        long now = System.currentTimeMillis();
        prune(now);
        for (Pending p : pending.values()) {
            if (p.poll().equals(pollId)) {
                if (p.confirmed()) {
                    pending.remove(p.code());
                    return new Poll("done", p.token(), p.playerName());
                }
                return new Poll("pending", null, null);
            }
        }
        return new Poll("expired", null, null);
    }

    /** A key made in the browser (Account -> Devices): shown once. */
    public synchronized String createKey(UUID player, String playerName, String name, String ip) {
        String token = "mrd_" + Secrets.token(32);
        add(token, player, playerName, clean(name), ip, "key");
        return token;
    }

    private Device add(String token, UUID player, String playerName, String name, String ip, String via) {
        long now = System.currentTimeMillis();
        Device device = new Device(Secrets.token(9), Secrets.sha256(token), player, playerName, name, now, now,
                ip == null ? "" : ip, via);
        devices.put(device.hash(), device);
        List<Device> own = list(player);
        int max = plugin.settings().deck().maxDevices();
        for (int i = max; i < own.size(); i++) {
            devices.remove(own.get(i).hash());
        }
        save();
        return device;
    }

    private void prune(long now) {
        pending.values().removeIf(p -> p.expires() < now);
    }

    private static String clean(String name) {
        String n = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").strip();
        if (n.isEmpty()) {
            n = "Stream Deck";
        }
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    // --------------------------------------------------------------- devices

    /** The device of a key (its use noted), or null. */
    public synchronized Device device(String token, String ip) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Device d = devices.get(Secrets.sha256(token.strip()));
        if (d == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        boolean moved = !d.ip().equals(ip);
        d = new Device(d.id(), d.hash(), d.player(), d.playerName(), d.name(), d.created(), now, ip, d.via());
        devices.put(d.hash(), d);
        if (moved || now - seenSaved >= SEEN_EVERY) {
            seenSaved = now;
            save();
        }
        return d;
    }

    /** The player's Stream Decks, used last first. */
    public synchronized List<Device> list(UUID player) {
        List<Device> result = new ArrayList<>();
        for (Device d : devices.values()) {
            if (d.player().equals(player)) {
                result.add(d);
            }
        }
        result.sort(Comparator.comparingLong(Device::lastSeen).reversed());
        return result;
    }

    /** Signs one Stream Deck of the player out. @return whether it existed */
    public synchronized boolean revoke(UUID player, String id) {
        boolean removed = devices.values().removeIf(d -> d.player().equals(player) && d.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    /** Signs all Stream Decks of the player out. @return how many */
    public synchronized int revokeAll(UUID player) {
        int before = devices.size();
        devices.values().removeIf(d -> d.player().equals(player));
        int removed = before - devices.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    // --------------------------------------------------------------- storage

    public synchronized void load() {
        devices.clear();
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        ConfigurationSection list = y.getConfigurationSection("devices");
        if (list == null) {
            return;
        }
        for (String hash : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(hash);
            try {
                devices.put(hash, new Device(s.getString("id"), hash, UUID.fromString(s.getString("player")),
                        s.getString("player-name", "?"), s.getString("name", "Stream Deck"), s.getLong("created"),
                        s.getLong("last-seen"), s.getString("ip", ""), s.getString("via", "code")));
            } catch (RuntimeException ignored) {
                // broken entry - that Stream Deck simply connects again
            }
        }
    }

    /** Writes the uses noted since the last save (on shutdown). */
    public synchronized void flush() {
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - connected Stream Decks (managed by the plugin, do not edit)."));
        for (Device d : devices.values()) {
            String p = "devices." + d.hash() + ".";
            y.set(p + "id", d.id());
            y.set(p + "player", d.player().toString());
            y.set(p + "player-name", d.playerName());
            y.set(p + "name", d.name());
            y.set(p + "created", d.created());
            y.set(p + "last-seen", d.lastSeen());
            y.set(p + "ip", d.ip());
            y.set(p + "via", d.via());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}
