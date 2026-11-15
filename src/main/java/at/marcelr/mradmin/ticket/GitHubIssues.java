package at.marcelr.mradmin.ticket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The GitHub issue a ticket was forwarded to (since 1.7): state, labels and comments for the team, read from the public
 * GitHub API without a token. Kept 10 minutes per issue (GitHub allows 60 calls per hour and IP without sign-in); on
 * an error the last answer stays and the next try waits 2 minutes.
 */
public final class GitHubIssues {

    private static final Pattern ISSUE = Pattern.compile(
            "https://github\\.com/(therealmarci/[a-z]+)/issues/(\\d{1,7})");
    private static final long KEEP_MS = 10 * 60_000L;
    private static final long RETRY_MS = 2 * 60_000L;
    private static final long REFRESH_MS = 60_000L;

    private record Entry(long time, Map<String, Object> data, String error) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * State, labels and comments of the issue at {@code url}. Blocking (network) - not on the server thread.
     *
     * @param refresh asked for by the team: fetched again if the answer is older than a minute
     */
    public Map<String, Object> get(String url, boolean refresh) {
        Matcher m = url == null ? null : ISSUE.matcher(url);
        if (m == null || !m.matches()) {
            return Map.of("error", "ticket-issue-invalid");
        }
        long now = System.currentTimeMillis();
        Entry old = cache.get(url);
        long age = old == null ? Long.MAX_VALUE : now - old.time();
        boolean fresh = old != null && (old.error() == null ? age < (refresh ? REFRESH_MS : KEEP_MS) : age < RETRY_MS);
        if (fresh) {
            return answer(old);
        }
        Entry next;
        try {
            next = new Entry(now, fetch(m.group(1), m.group(2), url), null);
        } catch (IOException e) {
            next = new Entry(now, old == null ? null : old.data(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return old == null ? Map.of("error", "github-unreachable") : answer(old);
        }
        cache.put(url, next);
        return answer(next);
    }

    private static Map<String, Object> answer(Entry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entry.data() != null) {
            result.putAll(entry.data());
        }
        result.put("fetched", entry.time());
        if (entry.error() != null) {
            result.put("error", entry.error().startsWith("rate") ? "github-rate-limit" : "github-unreachable");
        }
        return result;
    }

    private Map<String, Object> fetch(String repo, String number, String url) throws IOException, InterruptedException {
        JsonObject issue = JsonParser.parseString(call("https://api.github.com/repos/" + repo + "/issues/" + number)).getAsJsonObject();
        JsonArray comments = JsonParser.parseString(call("https://api.github.com/repos/" + repo + "/issues/" + number
                + "/comments?per_page=100")).getAsJsonArray();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("number", Integer.parseInt(number));
        data.put("title", text(issue, "title"));
        data.put("state", text(issue, "state"));
        data.put("stateReason", text(issue, "state_reason"));
        data.put("created", time(issue, "created_at"));
        data.put("closed", time(issue, "closed_at"));
        List<Map<String, Object>> labels = new ArrayList<>();
        for (JsonElement e : issue.getAsJsonArray("labels")) {
            JsonObject l = e.getAsJsonObject();
            labels.add(Map.of("name", text(l, "name"), "color", "#" + text(l, "color")));
        }
        data.put("labels", labels);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonElement e : comments) {
            JsonObject c = e.getAsJsonObject();
            Map<String, Object> comment = new LinkedHashMap<>();
            comment.put("user", c.has("user") && c.get("user").isJsonObject() ? text(c.getAsJsonObject("user"), "login") : "?");
            comment.put("role", text(c, "author_association").toLowerCase(java.util.Locale.ROOT));
            comment.put("body", text(c, "body"));
            comment.put("created", time(c, "created_at"));
            comment.put("url", text(c, "html_url"));
            list.add(comment);
        }
        data.put("comments", list);
        return data;
    }

    private String call(String address) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "MRadmin").GET().build();
        HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 403 || r.statusCode() == 429) {
            throw new IOException("rate limit (HTTP " + r.statusCode() + ")");
        }
        if (r.statusCode() != 200) {
            throw new IOException("HTTP " + r.statusCode());
        }
        return r.body();
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static Long time(JsonObject o, String key) {
        String value = text(o, key);
        return value.isEmpty() ? null : Instant.parse(value).toEpochMilli();
    }
}
