package at.marcelr.mradmin.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Posts to and edits messages of the public announcement webhook. All requests run one after another on a
 * background thread: an edit that is queued after a post therefore always sees the message id of that post.
 */
final class AnnounceWebhook {

    private static final int MAX_RETRIES = 3;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-Discord-Announce");
        thread.setDaemon(true);
        return thread;
    });
    private final String userAgent;
    private final Consumer<String> onError;
    private final Runnable onPosted;

    /** A posted message; the id is filled in by the background thread once Discord has answered. */
    static final class Post {
        volatile String id;

        Post(String id) {
            this.id = id;
        }
    }

    /** @param onPosted runs on the background thread whenever a post got its id (so it can be saved right away) */
    AnnounceWebhook(String userAgent, Consumer<String> onError, Runnable onPosted) {
        this.userAgent = userAgent;
        this.onError = onError;
        this.onPosted = onPosted;
    }

    /** Posts a message ({@code ?wait=true}, so Discord returns it) and stores its id in the returned post. */
    Post post(URI webhook, JsonObject payload) {
        Post post = new Post(null);
        String body = payload.toString();
        executor.execute(() -> create(webhook, post, body));
        return post;
    }

    /**
     * Edits a posted message. If it has no id (the post failed or was never saved) or was deleted in Discord, it is
     * posted anew instead - the announcement must never get stuck on an old state.
     */
    void edit(URI webhook, Post post, JsonObject payload) {
        String body = payload.toString();
        executor.execute(() -> {
            if (post.id == null) {
                create(webhook, post, body);
                return;
            }
            String base = webhook.toString();
            int query = base.indexOf('?');
            String url = (query < 0 ? base : base.substring(0, query)) + "/messages/" + post.id
                    + (query < 0 ? "" : base.substring(query));
            int[] status = new int[1];
            send(HttpRequest.newBuilder(URI.create(url)).method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
                    status, onError);
            if (status[0] == 404) {
                post.id = null;
                create(webhook, post, body);
            }
        });
    }

    /** Runs on the background thread: posts and stores the new message id in {@code post}. */
    private void create(URI webhook, Post post, String body) {
        URI url = URI.create(webhook + (webhook.getQuery() == null ? "?" : "&") + "wait=true");
        String answer = send(HttpRequest.newBuilder(url).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        if (answer != null) {
            try {
                post.id = JsonParser.parseString(answer).getAsJsonObject().get("id").getAsString();
                onPosted.run();
            } catch (RuntimeException e) {
                onError.accept("unexpected answer from Discord: " + answer);
            }
        }
    }

    /** Sends a test message and waits for the answer. @return null on success, otherwise the error */
    CompletableFuture<String> test(URI webhook, JsonObject payload) {
        String body = payload.toString();
        return CompletableFuture.supplyAsync(() -> {
            URI url = URI.create(webhook + (webhook.getQuery() == null ? "?" : "&") + "wait=true");
            String[] error = new String[1];
            String answer = send(HttpRequest.newBuilder(url).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)),
                    new int[1], e -> error[0] = e);
            return answer != null ? null : error[0];
        }, executor);
    }

    private String send(HttpRequest.Builder builder) {
        return send(builder, new int[1], onError);
    }

    /**
     * @param status receives the last HTTP status (0 if there was no answer)
     * @return the response body on success, otherwise null (the error goes to {@code errors}; a missing message,
     * HTTP 404, is left to the caller)
     */
    private String send(HttpRequest.Builder builder, int[] status, Consumer<String> errors) {
        HttpRequest request = builder.timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .build();
        try {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                status[0] = code;
                if (code / 100 == 2) {
                    return response.body();
                }
                if (code == 404 && request.method().equals("PATCH")) {
                    return null;
                }
                if (code != 429) {
                    String text = response.body() == null ? "" : response.body().replaceAll("\\s+", " ").trim();
                    errors.accept("HTTP " + code + (text.isEmpty() ? "" : " " + (text.length() > 200 ? text.substring(0, 200) + "…" : text)));
                    return null;
                }
                Thread.sleep(retryAfter(response));
            }
            errors.accept("Discord rate limit");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            errors.accept(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        return null;
    }

    private static long retryAfter(HttpResponse<String> response) {
        try {
            double seconds = JsonParser.parseString(response.body()).getAsJsonObject().get("retry_after").getAsDouble();
            return Math.max(250L, (long) (seconds * 1000));
        } catch (RuntimeException e) {
            return 2_000L;
        }
    }

    /** Payload with one embed; {@code pingRole} (role id or null) is mentioned in the content and allowed to ping. */
    static JsonObject payload(String username, String avatar, String pingRole, JsonObject embed) {
        JsonObject json = new JsonObject();
        if (!username.isEmpty()) {
            json.addProperty("username", username);
        }
        if (!avatar.isEmpty()) {
            json.addProperty("avatar_url", avatar);
        }
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        JsonArray roles = new JsonArray();
        if (pingRole != null) {
            json.addProperty("content", "<@&" + pingRole + ">");
            roles.add(pingRole);
        } else {
            json.addProperty("content", "");
        }
        mentions.add("roles", roles);
        json.add("allowed_mentions", mentions);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        json.add("embeds", embeds);
        return json;
    }

    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
