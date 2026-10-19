package at.marcelr.mradmin.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Live updates for open pages (Server-Sent Events on /api/events): when a ticket changes, every connected page that
 * may see tickets gets {@code event: ticket} with its id and loads what it shows again. The events carry no content,
 * only "look again" - so nobody gets data they may not see. Any thread may publish.
 */
public final class WebEvents {

    /** Per account (tabs, devices) and overall - keeps a misbehaving page from using up threads. */
    private static final int MAX_PER_ACCOUNT = 8;
    private static final int MAX_TOTAL = 200;
    private static final long PING_SECONDS = 20;

    private record Client(UUID account, boolean tickets, BlockingQueue<String> queue) {
    }

    private final Set<Client> clients = ConcurrentHashMap.newKeySet();

    /** A ticket was created or changed. */
    public void ticket(int id) {
        publish(Client::tickets, "ticket", "{\"id\":" + id + "}");
    }

    private void publish(Predicate<Client> to, String event, String data) {
        String message = "event: " + event + "\ndata: " + data + "\n\n";
        for (Client client : clients) {
            if (to.test(client)) {
                client.queue().offer(message);
            }
        }
    }

    /** GET /api/events: keeps the answer open and writes events (and a ping every 20 s) until the page closes. */
    void stream(Http http, boolean tickets) throws IOException {
        UUID account = http.account().id();
        if (clients.size() >= MAX_TOTAL || clients.stream().filter(c -> c.account().equals(account)).count() >= MAX_PER_ACCOUNT) {
            throw new Http.WebException(429, "too-many-connections");
        }
        Client client = new Client(account, tickets, new LinkedBlockingQueue<>(100));
        clients.add(client);
        try (OutputStream out = http.stream("text/event-stream; charset=utf-8")) {
            write(out, "retry: 5000\n\n");
            while (!Thread.currentThread().isInterrupted()) {
                String message = client.queue().poll(PING_SECONDS, TimeUnit.SECONDS);
                write(out, message == null ? ": ping\n\n" : message);
            }
        } catch (IOException | InterruptedException e) {
            // page closed or server stopping
        } finally {
            clients.remove(client);
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public int connected() {
        return clients.size();
    }
}
