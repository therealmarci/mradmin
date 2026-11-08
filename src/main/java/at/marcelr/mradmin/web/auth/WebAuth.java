package at.marcelr.mradmin.web.auth;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.web.self.PlayerSessions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Accounts of the web interface - one per player (UUID) - and their own sign-ins. Thread-safe (web threads and the
 * server thread use it).
 * <p>
 * Everyone gets in with a code from {@code /web} in game; the account is made then. On top, a player can set up their
 * own sign-in to get in without the game: a password (with their name or a confirmed e-mail address), a passkey or
 * Discord - optionally with 2FA (authenticator app, then a password sign-in also asks for a code). The team needs at
 * least one own sign-in for the administration ({@link #teamReady}). Signed-in browsers are kept in
 * {@link PlayerSessions} - one kind of session for everyone. Cookie tokens, recovery codes and the tokens of e-mail
 * links are stored only as SHA-256 hashes.
 */
public final class WebAuth {

    /** Failed sign-ins in a row before the account is locked for {@link #LOCK_MILLIS}. */
    private static final int MAX_FAILED = 5;
    private static final long LOCK_MILLIS = 15 * 60_000L;
    private static final int RECOVERY_CODES = 8;
    /** How long the links in e-mails (confirm the address, new password) work. */
    public static final long MAIL_LINK_MILLIS = 60 * 60_000L;
    private static final String ACCOUNTS = "web-accounts";
    /** Team sessions of MRadmin before 1.5 - taken over once into {@link PlayerSessions}. */
    private static final String OLD_SESSIONS = "web-sessions";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}$");

    /** An account; changed only inside WebAuth (synchronized). */
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
        /** Random, fixed ID for passkeys (base64url) - never the UUID, so passkeys do not reveal the player. */
        String userHandle;
        List<Passkey> passkeys = new ArrayList<>();
        /** The linked Discord account (ID and the name it had when linked), or null. */
        String discordId;
        String discordName;
        /** Confirmed e-mail address (sign-in, "forgot password"), or null. */
        String email;

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

        public String email() {
            return email;
        }

        public boolean hasPassword() {
            return password != null;
        }

        /** Whether the player can sign in without the game: password, passkey or Discord. */
        public boolean ownLogin() {
            return password != null || !passkeys.isEmpty() || discordId != null;
        }
    }

    /**
     * A passkey of an account: credential ID and public key (COSE) as base64url, the authenticator's signature
     * counter, a name the member chose, and when it was added and last used. Only public keys - nothing secret.
     */
    public record Passkey(String id, String publicKey, long signCount, String name, long created, long lastUsed) {
    }

    /** Result of a sign-in: the cookie token (null on failure) and an error key. */
    public record Result(String token, PlayerSessions.Session session, String error, List<String> recoveryCodes) {

        public static Result fail(String error) {
            return new Result(null, null, error, List.of());
        }

        public boolean ok() {
            return token != null;
        }
    }

    /** A pending e-mail link: whose, for which address (confirm) and until when. */
    private record MailLink(UUID account, String email, long expires) {
    }

    private final MRAdmin plugin;
    private final Map<UUID, Account> accounts = new HashMap<>();
    /** hash of the token -> address to confirm */
    private final Map<String, MailLink> emailLinks = new HashMap<>();
    /** hash of the token -> new password for this account */
    private final Map<String, MailLink> resetLinks = new HashMap<>();
    private PlayerSessions sessions;

    public WebAuth(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** The one place where signed-in browsers are kept (set by the web server). */
    public synchronized void useSessions(PlayerSessions sessions) {
        this.sessions = sessions;
    }

    private Storage storage() {
        return plugin.storage();
    }

    // ------------------------------------------------------------------- accounts

    public synchronized Account account(UUID id) {
        return accounts.get(id);
    }

    /** The player's account, made if they have none yet (first /web sign-in); the name is kept up to date. */
    public synchronized Account ensure(UUID id, String name) {
        Account account = accounts.get(id);
        if (account == null) {
            account = new Account(id, name);
            account.created = System.currentTimeMillis();
            accounts.put(id, account);
            saveAccounts();
        } else if (name != null && !name.isBlank() && !name.equals(account.name)) {
            account.name = name;
            saveAccounts();
        }
        return account;
    }

    public synchronized Account byName(String name) {
        if (name == null) {
            return null;
        }
        return accounts.values().stream().filter(a -> a.name.equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    /** The account with this confirmed e-mail address, or null. */
    public synchronized Account byEmail(String email) {
        if (email == null) {
            return null;
        }
        String wanted = email.trim().toLowerCase(Locale.ROOT);
        return accounts.values().stream().filter(a -> wanted.equals(a.email)).findFirst().orElse(null);
    }

    public synchronized Collection<Account> accounts() {
        return List.copyOf(accounts.values());
    }

    /** Accounts with an own sign-in (password, passkey or Discord) - the former "web accounts". */
    public synchronized long ownLoginCount() {
        return accounts.values().stream().filter(Account::ownLogin).count();
    }

    /**
     * Whether the account may open the administration (besides the permission): an own sign-in, and 2FA if
     * {@code web.login.require-2fa} asks for it.
     */
    public synchronized boolean teamReady(UUID id) {
        Account account = accounts.get(id);
        if (account == null || !account.ownLogin()) {
            return false;
        }
        return !plugin.settings().web().require2fa() || account.totp != null;
    }

    /** Deletes an account with all its browsers. @return whether it existed */
    public synchronized boolean remove(UUID id) {
        boolean existed = accounts.remove(id) != null;
        if (sessions != null) {
            sessions.revokeAll(id);
        }
        if (existed) {
            saveAccounts();
        }
        return existed;
    }

    /**
     * Removes all own sign-ins of a player (password, 2FA, passkeys, Discord, e-mail) and signs out all their browsers
     * - for a lost device or a taken-over account. Afterwards they get in again only with /web.
     * @return whether the account existed
     */
    public synchronized boolean resetLogins(UUID id) {
        Account account = accounts.get(id);
        if (account == null) {
            return false;
        }
        account.password = null;
        account.totp = null;
        account.recovery = new ArrayList<>();
        account.passkeys = new ArrayList<>();
        account.discordId = null;
        account.discordName = null;
        account.email = null;
        account.failed = 0;
        account.lockedUntil = 0;
        emailLinks.values().removeIf(l -> l.account().equals(id));
        resetLinks.values().removeIf(l -> l.account().equals(id));
        if (sessions != null) {
            sessions.revokeAll(id);
        }
        saveAccounts();
        return true;
    }

    // ------------------------------------------------------------------- password sign-in

    /**
     * Name or confirmed e-mail address + password, plus a 2FA code (or a recovery code) if the account has 2FA. The
     * answer never says whether the name or the password was wrong. Slow on purpose (password hashing) - call it from
     * a web thread. Error "code-needed": password right, but the account wants its 2FA code (the page asks for it).
     */
    public Result login(String identifier, String password, String code, String ip, String agent) {
        Account account;
        synchronized (this) {
            account = identifier != null && identifier.contains("@") ? byEmail(identifier) : byName(identifier);
            if (account != null && account.lockedUntil > System.currentTimeMillis()) {
                return Result.fail("locked");
            }
        }
        if (account == null || account.password == null) {
            // Same work as for a real account, so the answer time does not tell whether the name exists.
            Passwords.verify(password == null ? "" : password, Dummy.HASH);
            return Result.fail("invalid");
        }
        boolean passwordOk = Passwords.verify(password == null ? "" : password, account.password);
        synchronized (this) {
            if (passwordOk && account.totp != null && (code == null || code.isBlank())) {
                return Result.fail("code-needed");
            }
            if (!passwordOk || (account.totp != null && !secondFactor(account, code))) {
                if (++account.failed >= MAX_FAILED) {
                    account.failed = 0;
                    account.lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
                }
                saveAccounts();
                return Result.fail("invalid");
            }
            account.failed = 0;
            account.lastLogin = System.currentTimeMillis();
            saveAccounts();
            return startSession(account, ip, agent);
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
        long step = Totp.verify(account.totp, code.replace(" ", ""), System.currentTimeMillis());
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

    /**
     * Sets or changes the password. Slow (hashing) - web thread. The caller checks who it is (signed in, and for a
     * change the current password or another confirmation). @return null or "password-short"
     */
    public String setPassword(UUID id, String password) {
        if (password == null || password.length() < Passwords.MIN_LENGTH || password.length() > 200) {
            return "password-short";
        }
        String hash = Passwords.hash(password);
        synchronized (this) {
            Account account = accounts.get(id);
            if (account == null) {
                return "invalid";
            }
            account.password = hash;
            account.failed = 0;
            account.lockedUntil = 0;
            saveAccounts();
            return null;
        }
    }

    /** Removes the password (the player keeps /web and their other sign-ins). */
    public synchronized void removePassword(UUID id) {
        Account account = accounts.get(id);
        if (account != null && account.password != null) {
            account.password = null;
            saveAccounts();
        }
    }

    /** Checks the account's password (confirming a sensitive step). Slow - web thread. */
    public boolean checkPassword(UUID id, String password) {
        String hash;
        synchronized (this) {
            Account account = accounts.get(id);
            hash = account == null ? null : account.password;
        }
        if (hash == null || password == null || password.isEmpty()) {
            return false;
        }
        return Passwords.verify(password, hash);
    }

    // ------------------------------------------------------------------- e-mail

    public static boolean validEmail(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email.trim()).matches();
    }

    /**
     * Step 1 of a new address: a token for the confirmation link (sent by mail). The address counts only after the
     * link. @return the token, or an error ("email-invalid", "email-taken")
     */
    public synchronized String[] requestEmail(UUID id, String email) {
        if (!validEmail(email)) {
            return new String[] {null, "email-invalid"};
        }
        String clean = email.trim().toLowerCase(Locale.ROOT);
        Account other = byEmail(clean);
        if (other != null && !other.id.equals(id)) {
            return new String[] {null, "email-taken"};
        }
        long now = System.currentTimeMillis();
        emailLinks.values().removeIf(l -> l.account().equals(id) || l.expires() < now);
        String token = Secrets.token(24);
        emailLinks.put(Secrets.sha256(token), new MailLink(id, clean, now + MAIL_LINK_MILLIS));
        return new String[] {token, null};
    }

    /** Step 2: the link was opened. @return the account's name, or null if the link is invalid or the address taken */
    public synchronized String confirmEmail(String token) {
        if (token == null) {
            return null;
        }
        MailLink link = emailLinks.remove(Secrets.sha256(token));
        Account account = link == null || link.expires() < System.currentTimeMillis() ? null : accounts.get(link.account());
        if (account == null) {
            return null;
        }
        Account other = byEmail(link.email());
        if (other != null && other != account) {
            return null;
        }
        account.email = link.email();
        saveAccounts();
        return account.name;
    }

    public synchronized void removeEmail(UUID id) {
        Account account = accounts.get(id);
        if (account != null && account.email != null) {
            account.email = null;
            saveAccounts();
        }
    }

    /**
     * "Forgot password": a token for the link, if the name or address belongs to an account with a confirmed address.
     * @return {account, token} or null (the page says the same either way)
     */
    public synchronized Object[] requestReset(String identifier) {
        Account account = identifier != null && identifier.contains("@") ? byEmail(identifier) : byName(identifier);
        if (account == null || account.email == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        resetLinks.values().removeIf(l -> l.account().equals(account.id) || l.expires() < now);
        String token = Secrets.token(24);
        resetLinks.put(Secrets.sha256(token), new MailLink(account.id, account.email, now + MAIL_LINK_MILLIS));
        return new Object[] {account, token};
    }

    /** Whether a reset link still works (the page shows the form only then). */
    public synchronized boolean resetValid(String token) {
        MailLink link = token == null ? null : resetLinks.get(Secrets.sha256(token));
        return link != null && link.expires() >= System.currentTimeMillis();
    }

    /**
     * Sets the new password of a reset link and signs out all other browsers of the account. Slow - web thread.
     * @return null, "reset-invalid" or "password-short"
     */
    public String finishReset(String token, String password) {
        UUID id;
        synchronized (this) {
            MailLink link = token == null ? null : resetLinks.get(Secrets.sha256(token));
            if (link == null || link.expires() < System.currentTimeMillis()) {
                return "reset-invalid";
            }
            id = link.account();
        }
        String error = setPassword(id, password);
        if (error != null) {
            return error;
        }
        synchronized (this) {
            resetLinks.remove(Secrets.sha256(token));
            if (sessions != null) {
                sessions.revokeAll(id);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- passkeys

    /** The account's passkey ID (made on first use). */
    public synchronized String userHandle(UUID id) {
        Account account = accounts.get(id);
        if (account == null) {
            return null;
        }
        if (account.userHandle == null) {
            account.userHandle = Secrets.token(32);
            saveAccounts();
        }
        return account.userHandle;
    }

    /** The account with this passkey ID, or null. */
    public synchronized Account byUserHandle(String handle) {
        if (handle == null) {
            return null;
        }
        return accounts.values().stream().filter(a -> handle.equals(a.userHandle)).findFirst().orElse(null);
    }

    public synchronized List<Passkey> passkeys(UUID id) {
        Account account = accounts.get(id);
        return account == null ? List.of() : List.copyOf(account.passkeys);
    }

    /** The passkey with this credential ID and its account, or null. */
    public synchronized Map.Entry<Account, Passkey> passkey(String credentialId) {
        for (Account account : accounts.values()) {
            for (Passkey p : account.passkeys) {
                if (p.id().equals(credentialId)) {
                    return Map.entry(account, p);
                }
            }
        }
        return null;
    }

    public synchronized void addPasskey(UUID id, Passkey passkey) {
        Account account = accounts.get(id);
        if (account != null) {
            account.passkeys.removeIf(p -> p.id().equals(passkey.id()));
            account.passkeys.add(passkey);
            saveAccounts();
        }
    }

    /** @return null or "not-found" (/web always works, so the last passkey may go too) */
    public synchronized String removePasskey(UUID id, String credentialId) {
        Account account = accounts.get(id);
        if (account == null || account.passkeys.stream().noneMatch(p -> p.id().equals(credentialId))) {
            return "not-found";
        }
        account.passkeys.removeIf(p -> p.id().equals(credentialId));
        saveAccounts();
        return null;
    }

    /** After a passkey check (not a sign-in): its new signature counter and "last used". */
    public synchronized void passkeyUsed(UUID id, String credentialId, long signCount) {
        Account account = accounts.get(id);
        if (account != null) {
            long now = System.currentTimeMillis();
            account.passkeys.replaceAll(p -> p.id().equals(credentialId)
                    ? new Passkey(p.id(), p.publicKey(), signCount, p.name(), p.created(), now) : p);
            saveAccounts();
        }
    }

    /**
     * Signs in with a verified passkey (the WebAuthn check happened in {@link Passkeys}): stores the new signature
     * counter and starts a session. A passkey with user verification counts as two factors - no 2FA code.
     */
    public synchronized Result passkeyLogin(UUID id, String credentialId, long signCount, String ip, String agent) {
        Account account = accounts.get(id);
        if (account == null) {
            return Result.fail("invalid");
        }
        if (account.lockedUntil > System.currentTimeMillis()) {
            return Result.fail("locked");
        }
        long now = System.currentTimeMillis();
        account.passkeys.replaceAll(p -> p.id().equals(credentialId)
                ? new Passkey(p.id(), p.publicKey(), signCount, p.name(), p.created(), now) : p);
        account.failed = 0;
        account.lastLogin = now;
        saveAccounts();
        return startSession(account, ip, agent);
    }

    // ----------------------------------------------------------------- Discord

    /** The linked Discord account: [id, name], or null. */
    public synchronized String[] discord(UUID id) {
        Account account = accounts.get(id);
        return account == null || account.discordId == null ? null : new String[] {account.discordId, account.discordName};
    }

    /** Links a Discord account (one Discord account belongs to one account). @return null or "discord-taken" */
    public synchronized String linkDiscord(UUID id, String discordId, String discordName) {
        Account account = accounts.get(id);
        if (account == null) {
            return "invalid";
        }
        for (Account other : accounts.values()) {
            if (other != account && discordId.equals(other.discordId)) {
                return "discord-taken";
            }
        }
        account.discordId = discordId;
        account.discordName = discordName;
        saveAccounts();
        return null;
    }

    /** @return null or "not-found" */
    public synchronized String unlinkDiscord(UUID id) {
        Account account = accounts.get(id);
        if (account == null || account.discordId == null) {
            return "not-found";
        }
        account.discordId = null;
        account.discordName = null;
        saveAccounts();
        return null;
    }

    public synchronized Account byDiscord(String discordId) {
        return accounts.values().stream().filter(a -> discordId.equals(a.discordId)).findFirst().orElse(null);
    }

    /** Sign-in with the linked Discord account (checked by Discord itself) - counts like a passkey. */
    public synchronized Result discordLogin(UUID id, String discordName, String ip, String agent) {
        Account account = accounts.get(id);
        if (account == null || account.discordId == null) {
            return Result.fail("discord-unknown");
        }
        if (account.lockedUntil > System.currentTimeMillis()) {
            return Result.fail("locked");
        }
        account.discordName = discordName;
        account.failed = 0;
        account.lastLogin = System.currentTimeMillis();
        saveAccounts();
        return startSession(account, ip, agent);
    }

    // ---------------------------------------------------------------- 2FA on/off

    /** account -> 2FA secret being set up (until the first code confirms it) */
    private final Map<UUID, String> pendingTotp = new HashMap<>();

    public synchronized boolean hasTotp(UUID id) {
        Account account = accounts.get(id);
        return account != null && account.totp != null;
    }

    /** Switches 2FA off; the recovery codes go too. @return null */
    public synchronized String disableTotp(UUID id) {
        Account account = accounts.get(id);
        if (account != null) {
            account.totp = null;
            account.recovery = new ArrayList<>();
            saveAccounts();
        }
        return null;
    }

    /** Switching 2FA on, step 1: a new secret for the app (QR). Null if 2FA is on already. */
    public synchronized Map<String, Object> startTotp(UUID id) {
        Account account = accounts.get(id);
        if (account == null || account.totp != null) {
            return null;
        }
        String secret = Totp.newSecret();
        pendingTotp.put(id, secret);
        return Map.of("secret", secret, "uri", Totp.uri(issuer(), account.name, secret));
    }

    /** Step 2: the first code from the app confirms it; new recovery codes. @return the codes, or null if wrong */
    public synchronized List<String> enableTotp(UUID id, String code) {
        Account account = accounts.get(id);
        String secret = pendingTotp.get(id);
        if (account == null || secret == null || code == null) {
            return null;
        }
        long step = Totp.verify(secret, code.replace(" ", ""), System.currentTimeMillis());
        if (step < 0) {
            return null;
        }
        pendingTotp.remove(id);
        account.totp = secret;
        account.lastTotpStep = step;
        List<String> codes = newCodes(account);
        saveAccounts();
        return codes;
    }

    /**
     * Confirms a sensitive step (e.g. opening the web console) with a fresh 2FA code from the app - recovery codes
     * do not count here. A code works once (same replay protection as at sign-in).
     */
    public synchronized boolean confirmCode(UUID id, String code) {
        Account account = accounts.get(id);
        if (account == null || code == null || account.totp == null) {
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
        if (account == null || account.totp == null) {
            return null;
        }
        long step = Totp.verify(account.totp, code, System.currentTimeMillis());
        if (step < 0 || step <= account.lastTotpStep) {
            return null;
        }
        account.lastTotpStep = step;
        List<String> codes = newCodes(account);
        saveAccounts();
        return codes;
    }

    private static List<String> newCodes(Account account) {
        List<String> codes = new ArrayList<>();
        account.recovery = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = Secrets.base32(Secrets.random(7)).substring(0, 10);
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
            account.recovery.add(Secrets.sha256(raw));
        }
        return codes;
    }

    private String issuer() {
        String url = plugin.settings().web().publicUrl();
        String name = plugin.branding().name();
        return url.isEmpty() ? name : name + " (" + url.replaceFirst("^https?://", "") + ")";
    }

    // ---------------------------------------------------------------- sessions (kept in PlayerSessions)

    private Result startSession(Account account, String ip, String agent) {
        if (sessions == null) {
            return Result.fail("invalid");
        }
        PlayerSessions.Result opened = sessions.open(account.id, account.name, ip, agent);
        return new Result(opened.token(), opened.session(), opened.error(), List.of());
    }

    /** The session of a cookie token (its use noted), or null. */
    public PlayerSessions.Session session(String token, String ip) {
        return sessions == null ? null : sessions.session(token, ip);
    }

    public void logout(String token) {
        if (sessions != null) {
            sessions.logout(token);
        }
    }

    /** Signed-in browsers of an account, newest activity first. */
    public List<PlayerSessions.Session> sessions(UUID account) {
        return sessions == null ? List.of() : sessions.devices(account);
    }

    /** Ends one session of the account by its public id. @return whether it existed */
    public boolean revoke(UUID account, String id) {
        return sessions != null && sessions.revoke(account, id);
    }

    /** Ends all sessions of the account except {@code keep} (null = all). @return how many */
    public int revokeAll(UUID account, String keep) {
        return sessions == null ? 0 : sessions.revokeAllExcept(account, keep);
    }

    public int sessionCount() {
        return sessions == null ? 0 : sessions.count();
    }

    /** Nothing to write here any more (sessions: PlayerSessions#flush). */
    public void flush() {
    }

    // ------------------------------------------------------------- persistence

    public synchronized void load() {
        accounts.clear();
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
                    account.userHandle = s.getString("user-handle");
                    account.discordId = s.getString("discord-id");
                    account.discordName = s.getString("discord-name");
                    account.email = s.getString("email");
                    ConfigurationSection keys = s.getConfigurationSection("passkeys");
                    if (keys != null) {
                        for (String k : keys.getKeys(false)) {
                            ConfigurationSection p = keys.getConfigurationSection(k);
                            account.passkeys.add(new Passkey(p.getString("id"), p.getString("public-key"),
                                    p.getLong("sign-count"), p.getString("name", "Passkey"), p.getLong("created"),
                                    p.getLong("last-used")));
                        }
                    }
                    accounts.put(account.id, account);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Web account " + key + " is broken, skipped: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Once after the update: team sessions of the old team interface go into the one session list, so nobody is
     * signed out. Afterwards the old list is empty.
     */
    public void takeOverOldSessions() {
        if (sessions == null) {
            return;
        }
        YamlConfiguration y = storage().loadYaml(OLD_SESSIONS, plugin.getLogger());
        ConfigurationSection list = y.getConfigurationSection("sessions");
        if (list == null || list.getKeys(false).isEmpty()) {
            return;
        }
        int taken = 0;
        for (String hash : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(hash);
            try {
                UUID id = UUID.fromString(s.getString("account"));
                Account account = account(id);
                if (account != null && sessions.adopt(hash, s.getString("id"), id, account.name, s.getLong("created"),
                        s.getLong("last-seen"), s.getString("ip", ""), s.getString("agent", ""))) {
                    taken++;
                }
            } catch (RuntimeException ignored) {
                // broken entry - that browser simply signs in again
            }
        }
        YamlConfiguration empty = new YamlConfiguration();
        empty.options().setHeader(List.of("MRadmin - no longer used (sessions are in player-sessions since 1.5)."));
        storage().saveYaml(OLD_SESSIONS, empty);
        plugin.getLogger().info("Took over " + taken + " signed-in browsers of the old team interface.");
    }

    private void saveAccounts() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - accounts of the web interface (managed by the plugin, do not edit)."));
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
            y.set(p + "user-handle", a.userHandle);
            y.set(p + "discord-id", a.discordId);
            y.set(p + "discord-name", a.discordName);
            y.set(p + "email", a.email);
            for (int i = 0; i < a.passkeys.size(); i++) {
                Passkey k = a.passkeys.get(i);
                String q = p + "passkeys." + i + ".";
                y.set(q + "id", k.id());
                y.set(q + "public-key", k.publicKey());
                y.set(q + "sign-count", k.signCount());
                y.set(q + "name", k.name());
                y.set(q + "created", k.created());
                y.set(q + "last-used", k.lastUsed());
            }
        }
        storage().saveYaml(ACCOUNTS, y);
    }
}
