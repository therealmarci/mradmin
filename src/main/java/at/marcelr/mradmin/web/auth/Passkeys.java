package at.marcelr.mradmin.web.auth;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.exception.Base64UrlException;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passkeys (WebAuthn) for the web accounts, with Yubico's library: adding a passkey to a signed-in account, and
 * signing in with one - without a name (the passkey knows the account). Passkeys are bound to the address of the
 * web interface (web.public-url, without one "localhost" for tests); user verification (fingerprint, face, PIN) is
 * always required, so a passkey counts as both factors.
 */
public final class Passkeys {

    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final int MAX_PENDING = 500;

    private record Pending<T>(T request, long expires) {
    }

    private final MRAdmin plugin;
    private final WebAuth auth;
    /** account -> registration in progress */
    private final Map<UUID, Pending<PublicKeyCredentialCreationOptions>> registrations = new ConcurrentHashMap<>();
    /** request id -> sign-in in progress */
    private final Map<String, Pending<AssertionRequest>> assertions = new ConcurrentHashMap<>();
    private volatile RelyingParty party;
    private volatile String partyKey = "";

    public Passkeys(MRAdmin plugin, WebAuth auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    public boolean enabled() {
        return plugin.settings().web().passkeys();
    }

    // ------------------------------------------------------------ registration

    /** Starts adding a passkey: the options for navigator.credentials.create() as JSON. */
    public String startRegistration(UUID account) throws Exception {
        WebAuth.Account a = auth.account(account);
        String handle = auth.userHandle(account);
        PublicKeyCredentialCreationOptions options = party().startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder().name(a.name()).displayName(a.name() + " (MRadmin)")
                        .id(ByteArray.fromBase64Url(handle)).build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED).build())
                .timeout(TIMEOUT_MILLIS)
                .build());
        registrations.put(account, new Pending<>(options, System.currentTimeMillis() + TIMEOUT_MILLIS));
        return options.toCredentialsCreateJson();
    }

    /** Finishes adding a passkey with the browser's answer. @return null or an error key */
    public String finishRegistration(UUID account, String responseJson, String name) {
        Pending<PublicKeyCredentialCreationOptions> pending = registrations.remove(account);
        if (pending == null || pending.expires() < System.currentTimeMillis()) {
            return "passkey-expired";
        }
        try {
            RegistrationResult result = party().finishRegistration(FinishRegistrationOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                    .build());
            String label = name == null || name.isBlank() ? "Passkey" : name.strip();
            auth.addPasskey(account, new WebAuth.Passkey(result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBase64Url(), result.getSignatureCount(),
                    label.length() > 40 ? label.substring(0, 40) : label, System.currentTimeMillis(), 0));
            return null;
        } catch (Exception e) {
            plugin.getLogger().info("A passkey could not be added: " + e.getMessage());
            return "passkey-failed";
        }
    }

    // ----------------------------------------------------------------- sign-in

    /** Starts a sign-in: a request id and the options for navigator.credentials.get() as JSON. */
    public Map<String, String> startAssertion() throws Exception {
        long now = System.currentTimeMillis();
        assertions.values().removeIf(p -> p.expires() < now);
        if (assertions.size() > MAX_PENDING) {
            throw new IllegalStateException("too many sign-ins in progress");
        }
        AssertionRequest request = party().startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(TIMEOUT_MILLIS)
                .build());
        String id = Secrets.token(18);
        assertions.put(id, new Pending<>(request, now + TIMEOUT_MILLIS));
        return Map.of("request", id, "options", request.toCredentialsGetJson());
    }

    /** Checks the browser's answer; on success signs the account in (WebAuth.passkeyLogin). */
    public WebAuth.Result finishAssertion(String requestId, String responseJson, String ip, String agent) {
        Pending<AssertionRequest> pending = requestId == null ? null : assertions.remove(requestId);
        if (pending == null || pending.expires() < System.currentTimeMillis()) {
            return WebAuth.Result.fail("passkey-expired");
        }
        try {
            AssertionResult result = party().finishAssertion(FinishAssertionOptions.builder()
                    .request(pending.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                    .build());
            if (!result.isSuccess() || !result.isUserVerified()) {
                return WebAuth.Result.fail("passkey-failed");
            }
            WebAuth.Account account = auth.byName(result.getUsername());
            if (account == null) {
                return WebAuth.Result.fail("passkey-failed");
            }
            return auth.passkeyLogin(account.id(), result.getCredential().getCredentialId().getBase64Url(), result.getSignatureCount(),
                    ip, agent);
        } catch (Exception e) {
            return WebAuth.Result.fail("passkey-failed");
        }
    }

    // ----------------------------------------------------------- confirmation

    /** Confirmations in progress: request id -> account and request. */
    private record Confirm(UUID account, AssertionRequest request, long expires) {
    }

    private final Map<String, Confirm> confirms = new ConcurrentHashMap<>();

    /**
     * Confirming a sensitive step with a passkey of the signed-in account (instead of a 2FA code - e.g. when 2FA is
     * off): only this account's passkeys are offered and accepted.
     */
    public Map<String, String> startConfirm(UUID account) throws Exception {
        WebAuth.Account a = auth.account(account);
        if (a == null || auth.passkeys(account).isEmpty()) {
            throw new IllegalStateException("no passkey");
        }
        long now = System.currentTimeMillis();
        confirms.values().removeIf(c -> c.expires() < now);
        AssertionRequest request = party().startAssertion(StartAssertionOptions.builder()
                .username(a.name())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(TIMEOUT_MILLIS)
                .build());
        String id = Secrets.token(18);
        confirms.put(id, new Confirm(account, request, now + TIMEOUT_MILLIS));
        return Map.of("request", id, "options", request.toCredentialsGetJson());
    }

    /** @return whether the passkey answer belongs to this account and is valid */
    public boolean finishConfirm(UUID account, String requestId, String responseJson) {
        Confirm confirm = requestId == null ? null : confirms.remove(requestId);
        if (confirm == null || !confirm.account().equals(account) || confirm.expires() < System.currentTimeMillis()) {
            return false;
        }
        try {
            AssertionResult result = party().finishAssertion(FinishAssertionOptions.builder()
                    .request(confirm.request())
                    .response(PublicKeyCredential.parseAssertionResponseJson(responseJson))
                    .build());
            WebAuth.Account a = auth.account(account);
            if (!result.isSuccess() || !result.isUserVerified() || a == null || !a.name().equals(result.getUsername())) {
                return false;
            }
            auth.passkeyUsed(account, result.getCredential().getCredentialId().getBase64Url(), result.getSignatureCount());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ relying party

    /**
     * Bound to the address of the web interface: web.public-url (its host is the passkey's "domain"); without one,
     * http://localhost:&lt;port&gt; for tests. Made again when the settings change.
     */
    private RelyingParty party() {
        Settings.WebSettings web = plugin.settings().web();
        String origin;
        String id;
        if (web.publicUrl().isEmpty()) {
            id = "localhost";
            origin = "http://localhost:" + web.port();
        } else {
            URI uri = URI.create(web.publicUrl());
            id = uri.getHost();
            origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        }
        String key = id + "|" + origin;
        if (!key.equals(partyKey) || party == null) {
            party = RelyingParty.builder()
                    .identity(RelyingPartyIdentity.builder().id(id).name("MRadmin").build())
                    .credentialRepository(new Repository())
                    .origins(Set.of(origin))
                    .build();
            partyKey = key;
        }
        return party;
    }

    /** The library's view of the stored passkeys. "Username" is the account's Minecraft name. */
    private final class Repository implements CredentialRepository {

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            WebAuth.Account account = auth.byName(username);
            Set<PublicKeyCredentialDescriptor> result = new HashSet<>();
            if (account != null) {
                for (WebAuth.Passkey p : auth.passkeys(account.id())) {
                    bytes(p.id()).ifPresent(id -> result.add(PublicKeyCredentialDescriptor.builder().id(id).build()));
                }
            }
            return result;
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            WebAuth.Account account = auth.byName(username);
            return account == null ? Optional.empty() : bytes(auth.userHandle(account.id()));
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            WebAuth.Account account = auth.byUserHandle(userHandle.getBase64Url());
            return account == null ? Optional.empty() : Optional.of(account.name());
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return lookupAll(credentialId).stream().filter(c -> c.getUserHandle().equals(userHandle)).findFirst();
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            Map.Entry<WebAuth.Account, WebAuth.Passkey> found = auth.passkey(credentialId.getBase64Url());
            if (found == null) {
                return Set.of();
            }
            Optional<ByteArray> handle = bytes(auth.userHandle(found.getKey().id()));
            Optional<ByteArray> key = bytes(found.getValue().publicKey());
            if (handle.isEmpty() || key.isEmpty()) {
                return Set.of();
            }
            return Set.of(RegisteredCredential.builder()
                    .credentialId(credentialId)
                    .userHandle(handle.get())
                    .publicKeyCose(key.get())
                    .signatureCount(found.getValue().signCount())
                    .build());
        }
    }

    private static Optional<ByteArray> bytes(String base64url) {
        try {
            return base64url == null ? Optional.empty() : Optional.of(ByteArray.fromBase64Url(base64url));
        } catch (Base64UrlException e) {
            return Optional.empty();
        }
    }

    /** For the account page. */
    public List<WebAuth.Passkey> list(UUID account) {
        return auth.passkeys(account);
    }
}
