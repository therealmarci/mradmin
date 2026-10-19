package at.marcelr.mradmin.web.auth;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.storage.Storage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Web accounts, setup links and sessions. Thread-safe (web threads and the server thread use it).
 * <p>
 * An account belongs to a Minecraft player. It is created - or reset - with a one-time setup link that only someone
 * in game ({@code /mradmin web setup}) or at the console ({@code mradmin web invite}) can make. Sign-in is with a
 * password <b>plus</b> a code from an authenticator app (or a one-time recovery code), later also with passkeys.
 * Session cookies and recovery codes are stored only as SHA-256 hashes.
 */
public final class WebAuth {

    /** How long a setup link works. */
    public static final long SETUP_MILLIS = 10 * 60_000L;
    /** Failed sign-ins in a row before the account is locked for {@link #LOCK_MILLIS}. */
    private static final int MAX_FAILED = 5;
    private static final long LOCK_MILLIS = 15 * 60_000L;
    private static final int RECOVERY_CODES = 8;
    /** lastSeen of sessions is saved at most this often. */
    private static final long TOUCH_SAVE_MILLIS = 60_000L;
    private static final String ACCOUNTS = "web-accounts";
    private static final String SESSIONS = "web-sessions";

    /** A web account; changed only inside WebAuth (synchronized). */
    public static final class Account {
        final UUID id;
        String name;
        String password;
        String totp;
        List<String> recovery = new ArrayList<>();
        long created;
        long lastLogin;
        int failed;
        long lockedUntil;
        /** Time step of the last accepted 2FA code - the same code is never accepted twice. */
        long lastTotpStep;

        Account(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public long lastLogin() {
            return lastLogin;
        }

        public int recoveryLeft() {
            return recovery.size();
        }
    }

    /** A signed-in browser. {@code id} is public (shown in lists), the cookie token itself is stored only hashed. */
    public record Session(String id, String hash, UUID account, long created, long lastSeen, long expires, String ip,
                          String agent) {

        Session touched(long now, String ip) {
            return new Session(id, hash, account, created, now, expires, ip, agent);
        }
    }

    /** What a setup link stands for; the 2FA secret is made once per link, so reloading the page keeps the QR code. */
    private record Setup(UUID player, String name, long expires, String totp) {
    }

    /** Result of sign-in and setup: the cookie token (null on failure) and an error key. */
    public record Result(String token, Session session, String error, List<String> recoveryCodes) {

        static Result fail(String error) {
            return new Result(null, null, error, List.of());
        }

        public boolean ok() {
            return token != null;
        }
    }

    private final MRAdmin plugin;
    private final Map<UUID, Account> accounts = new HashMap<>();
    /** hash of the cookie token -> session */
    private final Map<String, Session> sessions = new HashMap<>();
    /** hash of the setup token -> setup */
    private final Map<String, Setup> setups = new HashMap<>();
    private long lastTouchSave;
    private boolean touchDirty;

    public WebAuth(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Storage storage() {
        return plugin.storage();
    }

    // ------------------------------------------------------------------- setup

    /** A new one-time setup link token for this player (any older link of them stops working). */
    public synchronized String createSetup(UUID player, String name) {
        setups.values().removeIf(s -> s.player().equals(player) || s.expires() < System.currentTimeMillis());
        String token = Secrets.token(24);
        setups.put(Secrets.sha256(token), new Setup(player, name, System.currentTimeMillis() + SETUP_MILLIS, Totp.newSecret()));
        return token;
    }

    /** For the setup page: name, whether an account exists (reset) and the 2FA secret. Null if the link is invalid. */
    public synchronized Map<String, Object> setupInfo(String token) {
        Setup setup = validSetup(token);
        if (setup == null) {
            return null;
        }
        return Map.of("name", setup.name(), "existing", accounts.containsKey(setup.player()),
                "secret", setup.totp(), "uri", Totp.uri(issuer(), setup.name(), setup.totp()),
                "expires", setup.expires(), "minPassword", Passwords.MIN_LENGTH);
    }

    /**
     * Finishes a setup: checks the 2FA code against the new secret, sets password and 2FA, makes new recovery codes,
     * signs out all other sessions of the account and signs this browser in. The link is used up.
     * Slow (password hashing) - call it from a web thread.
     */
    public Result finishSetup(String token, String password, String code, String ip, String agent) {
        Setup setup;
        synchronized (this) {
            setup = validSetup(token);
        }
        if (setup == null) {
            return Result.fail("setup-invalid");
        }
        if (password == null || password.length() < Passwords.MIN_LENGTH || password.length() > 200) {
            return Result.fail("password-short");
        }
        long step = Totp.verify(setup.totp(), code, System.currentTimeMillis());
        if (step < 0) {
            return Result.fail("code-wrong");
        }
        String hash = Passwords.hash(password);
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = Secrets.base32(Secrets.random(7)).substring(0, 10);
            String pretty = raw.substring(0, 5) + "-" + raw.substring(5);
            codes.add(pretty);
            hashes.add(Secrets.sha256(raw));
        }
        synchronized (this) {
            if (setups.remove(Secrets.sha256(token)) == null) {
                return Result.fail("setup-invalid");
            }
            Account account = accounts.computeIfAbsent(setup.player(), id -> new Account(id, setup.name()));
            account.name = setup.name();
            account.password = hash;
            account.totp = setup.totp();
            account.recovery = hashes;
            account.failed = 0;
            account.lockedUntil = 0;
            account.lastTotpStep = step;
            if (account.created == 0) {
                account.created = System.currentTimeMillis();
            }
            sessions.values().removeIf(s -> s.account().equals(account.id));
            Result result = startSession(account, ip, agent);
            saveAccounts();
            saveSessions();
            return new Result(result.token(), result.session(), null, List.copyOf(codes));
        }
    }

    private Setup validSetup(String token) {
        if (token == null) {
            return null;
        }
        Setup setup = setups.get(Secrets.sha256(token));
        return setup == null || setup.expires() < System.currentTimeMillis() ? null : setup;
    }

    // ------------------------------------------------------------------- login

    /**
     * Password + 2FA code (or a recovery code). The answer never says whether the name or the password was wrong.
     * Slow on purpose (password hashing) - call it from a web thread.
     */
    public Result login(String name, String password, String code, String ip, String agent) {
        Account account;
        synchronized (this) {
            account = byName(name);
            if (account != null && account.lockedUntil > System.currentTimeMillis()) {
                return Result.fail("locked");
            }
        }
        if (account == null || account.password == null) {
            // Same work as for a real account, so the answer time does not tell whether the name exists.
            Passwords.verify(password == null ? "" : password, Dummy.HASH);
            return Result.fail("invalid");
        }
        boolean passwordOk = Passwords.verify(password, account.password);
        synchronized (this) {
            if (!passwordOk || !secondFactor(account, code)) {
                if (++account.failed >= MAX_FAILED) {
                    account.failed = 0;
                    account.lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
                }
                saveAccounts();
                return Result.fail("invalid");
            }
            account.failed = 0;
            account.lastLogin = System.currentTimeMillis();
            Result result = startSession(account, ip, agent);
            saveAccounts();
            saveSessions();
            return result;
        }
    }

    /** Hash of a random password - only compared against for unknown names. Made on first use (web thread). */
    private static final class Dummy {
        static final String HASH = Passwords.hash(Secrets.token(16));
    }

    /** A fresh 2FA code (not used before) or an unused recovery code, which is then used up. */
    private boolean secondFactor(Account account, String code) {
        if (code == null) {
            return false;
        }
        long step = Totp.verify(account.totp, code, System.currentTimeMillis());
        if (step >= 0) {
            if (step <= account.lastTotpStep) {
                return false;
            }
            account.lastTotpStep = step;
            return true;
        }
        String recovery = Secrets.sha256(code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT));
        return account.recovery.remove(recovery);
    }

    private Result startSession(Account account, String ip, String agent) {
        String token = Secrets.token(32);
        long now = System.currentTimeMillis();
        Session session = new Session(Secrets.token(9), Secrets.sha256(token), account.id, now, now,
                now + plugin.settings().web().sessionDays() * 86_400_000L, ip, agent == null ? "" : cut(agent, 200));
        sessions.put(session.hash(), session);
        return new Result(token, session, null, List.of());
    }

    // ---------------------------------------------------------------- sessions

    /** The session of a cookie token (and marks it as seen), or null if it is unknown or expired. */
    public synchronized Session session(String token, String ip) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String hash = Secrets.sha256(token);
        Session session = sessions.get(hash);
        long now = System.currentTimeMillis();
        if (session == null) {
            return null;
        }
        if (session.expires() < now || !accounts.containsKey(session.account())) {
            sessions.remove(hash);
            saveSessions();
            return null;
        }
        Session touched = session.touched(now, ip);
        sessions.put(hash, touched);
        touchDirty = true;
        if (now - lastTouchSave > TOUCH_SAVE_MILLIS) {
            saveSessions();
        }
        return touched;
    }

    public synchronized void logout(String token) {
        if (token != null && sessions.remove(Secrets.sha256(token)) != null) {
            saveSessions();
        }
    }

    /** Sessions of an account, newest activity first. */
    public synchronized List<Session> sessions(UUID account) {
        return sessions.values().stream().filter(s -> s.account().equals(account))
                .sorted(Comparator.comparingLong(Session::lastSeen).reversed()).toList();
    }

    /** Ends one session of the account by its public id. @return whether it existed */
    public synchronized boolean revoke(UUID account, String id) {
        boolean removed = sessions.values().removeIf(s -> s.account().equals(account) && s.id().equals(id));
        if (removed) {
            saveSessions();
        }
        return removed;
    }

    /** Ends all sessions of the account except {@code keep} (null = all). @return how many */
    public synchronized int revokeAll(UUID account, String keep) {
        int before = sessions.size();
        sessions.values().removeIf(s -> s.account().equals(account) && !s.id().equals(keep));
        int removed = before - sessions.size();
        if (removed > 0) {
            saveSessions();
        }
        return removed;
    }

    // ---------------------------------------------------------------- accounts

    public synchronized Account account(UUID id) {
        return accounts.get(id);
    }

    public synchronized Account byName(String name) {
        if (name == null) {
            return null;
        }
        return accounts.values().stream().filter(a -> a.name.equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    public synchronized Collection<Account> accounts() {
        return List.copyOf(accounts.values());
    }

    public synchronized int sessionCount() {
        return sessions.size();
    }

    /** Deletes an account with all its sessions. @return whether it existed */
    public synchronized boolean remove(UUID id) {
        boolean existed = accounts.remove(id) != null;
        sessions.values().removeIf(s -> s.account().equals(id));
        setups.values().removeIf(s -> s.player().equals(id));
        if (existed) {
            saveAccounts();
            saveSessions();
        }
        return existed;
    }

    /**
     * Confirms a sensitive step (e.g. opening the web console) with a fresh 2FA code from the app - recovery codes
     * do not count here. A code works once (same replay protection as at sign-in).
     */
    public synchronized boolean confirmCode(UUID id, String code) {
        Account account = accounts.get(id);
        if (account == null || code == null) {
            return false;
        }
        long step = Totp.verify(account.totp, code.replace(" ", ""), System.currentTimeMillis());
        if (step < 0 || step <= account.lastTotpStep) {
            return false;
        }
        account.lastTotpStep = step;
        saveAccounts();
        return true;
    }

    /** New recovery codes after confirming with a 2FA code. @return the codes, or null if the code was wrong */
    public synchronized List<String> newRecoveryCodes(UUID id, String code) {
        Account account = accounts.get(id);
        if (account == null) {
            return null;
        }
        long step = Totp.verify(account.totp, code, System.currentTimeMillis());
        if (step < 0 || step <= account.lastTotpStep) {
            return null;
        }
        account.lastTotpStep = step;
        List<String> codes = new ArrayList<>();
        account.recovery = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = Secrets.base32(Secrets.random(7)).substring(0, 10);
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
            account.recovery.add(Secrets.sha256(raw));
        }
        saveAccounts();
        return codes;
    }

    private String issuer() {
        String url = plugin.settings().web().publicUrl();
        return url.isEmpty() ? "MRadmin" : "MRadmin (" + url.replaceFirst("^https?://", "") + ")";
    }

    // ------------------------------------------------------------- persistence

    public synchronized void load() {
        accounts.clear();
        sessions.clear();
        YamlConfiguration a = storage().loadYaml(ACCOUNTS, plugin.getLogger());
        ConfigurationSection section = a.getConfigurationSection("accounts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                try {
                    Account account = new Account(UUID.fromString(key), s.getString("name", "?"));
                    account.password = s.getString("password");
                    account.totp = s.getString("totp");
                    account.recovery = new ArrayList<>(s.getStringList("recovery"));
                    account.created = s.getLong("created");
                    account.lastLogin = s.getLong("last-login");
                    account.failed = s.getInt("failed");
                    account.lockedUntil = s.getLong("locked-until");
                    account.lastTotpStep = s.getLong("last-code");
                    accounts.put(account.id, account);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Web account " + key + " is broken, skipped: " + e.getMessage());
                }
            }
        }
        YamlConfiguration y = storage().loadYaml(SESSIONS, plugin.getLogger());
        ConfigurationSection list = y.getConfigurationSection("sessions");
        long now = System.currentTimeMillis();
        if (list != null) {
            for (String hash : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(hash);
                try {
                    Session session = new Session(s.getString("id"), hash, UUID.fromString(s.getString("account")),
                            s.getLong("created"), s.getLong("last-seen"), s.getLong("expires"), s.getString("ip", ""),
                            s.getString("agent", ""));
                    if (session.expires() > now && accounts.containsKey(session.account())) {
                        sessions.put(hash, session);
                    }
                } catch (RuntimeException ignored) {
                    // broken entry - that browser simply signs in again
                }
            }
        }
    }

    private void saveAccounts() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - web accounts (managed by the plugin, do not edit)."));
        for (Account a : accounts.values()) {
            String p = "accounts." + a.id + ".";
            y.set(p + "name", a.name);
            y.set(p + "password", a.password);
            y.set(p + "totp", a.totp);
            y.set(p + "recovery", a.recovery);
            y.set(p + "created", a.created);
            y.set(p + "last-login", a.lastLogin);
            y.set(p + "failed", a.failed);
            y.set(p + "locked-until", a.lockedUntil);
            y.set(p + "last-code", a.lastTotpStep);
        }
        storage().saveYaml(ACCOUNTS, y);
    }

    private void saveSessions() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - signed-in browsers (managed by the plugin, do not edit)."));
        for (Session s : sessions.values()) {
            String p = "sessions." + s.hash() + ".";
            y.set(p + "id", s.id());
            y.set(p + "account", s.account().toString());
            y.set(p + "created", s.created());
            y.set(p + "last-seen", s.lastSeen());
            y.set(p + "expires", s.expires());
            y.set(p + "ip", s.ip());
            y.set(p + "agent", s.agent());
        }
        storage().saveYaml(SESSIONS, y);
        lastTouchSave = System.currentTimeMillis();
        touchDirty = false;
    }

    /** On shutdown: the last "seen" times. */
    public synchronized void flush() {
        if (touchDirty) {
            saveSessions();
        }
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
