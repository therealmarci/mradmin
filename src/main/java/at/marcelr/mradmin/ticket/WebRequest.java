package at.marcelr.mradmin.ticket;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * What a ticket from the public web forms carries in addition: unban requests (the banned player proves who they are
 * with the code from their ban message) and contact requests from the imprint (without an account, only visible in
 * the web interface). Stored as JSON in the ticket's {@code extra} column.
 *
 * @param kind        {@link #UNBAN} or {@link #CONTACT}
 * @param email       address for answers (contact: required, unban: optional), may be empty
 * @param token       the secret in the status link (/anfrage/<token>)
 * @param banId       LibertyBans id of the ban (unban only)
 * @param banReason   the ban as it was when the request was made (unban only), so it stays readable after the ban
 *                    is lifted or expired
 * @param banOperator name of who banned (empty = console)
 * @param banStart    epoch ms
 * @param banEnd      epoch ms, 0 = permanent
 * @param stage       {@link #STAGE_REQUEST} or {@link #STAGE_APPEAL} (unban only)
 * @param decision    null while open, {@link #ACCEPTED} or {@link #REJECTED} (unban only)
 */
public record WebRequest(String kind, String email, String token, long banId, String banReason, String banOperator,
                         long banStart, long banEnd, String stage, String decision) {

    public static final String UNBAN = "unban";
    public static final String CONTACT = "contact";
    public static final String STAGE_REQUEST = "request";
    public static final String STAGE_APPEAL = "appeal";
    public static final String ACCEPTED = "accepted";
    public static final String REJECTED = "rejected";

    private static final Gson GSON = new Gson();

    public boolean unban() {
        return UNBAN.equals(kind);
    }

    public boolean contact() {
        return CONTACT.equals(kind);
    }

    public boolean appeal() {
        return STAGE_APPEAL.equals(stage);
    }

    public boolean hasEmail() {
        return email != null && !email.isEmpty();
    }

    WebRequest withDecision(String decision) {
        return new WebRequest(kind, email, token, banId, banReason, banOperator, banStart, banEnd, stage, decision);
    }

    WebRequest asAppeal() {
        return new WebRequest(kind, email, token, banId, banReason, banOperator, banStart, banEnd, STAGE_APPEAL, null);
    }

    WebRequest withEmail(String email) {
        return new WebRequest(kind, email, token, banId, banReason, banOperator, banStart, banEnd, stage, decision);
    }

    String json() {
        return GSON.toJson(this);
    }

    /** null for normal tickets (no or unreadable data). */
    static WebRequest parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            WebRequest r = GSON.fromJson(json, WebRequest.class);
            return r == null || r.kind() == null ? null : r;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
