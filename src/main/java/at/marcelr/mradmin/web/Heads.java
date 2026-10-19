package at.marcelr.mradmin.web;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player heads for the web interface, made by MRadmin itself: the face (plus hat layer) of the player's skin, 64x64.
 * The browser loads them from MRadmin only - no head service sees the visitors' addresses (privacy). Skins come
 * from Mojang (as for the server itself) and are cached for 6 hours. Runs on web threads (it may download).
 */
final class Heads {

    private static final int SIZE = 64;
    private static final long CACHE_MILLIS = 6 * 3_600_000L;
    private static final int MAX_CACHED = 1000;

    private record Cached(byte[] png, long until) {
    }

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private byte[] fallback;

    byte[] head(UUID uuid) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(uuid);
        if (cached != null && cached.until() > now) {
            return cached.png();
        }
        byte[] png;
        try {
            png = render(skin(uuid));
        } catch (Exception e) {
            png = fallback();
        }
        if (cache.size() >= MAX_CACHED) {
            cache.clear();
        }
        cache.put(uuid, new Cached(png, now + CACHE_MILLIS));
        return png;
    }

    /** The skin image: from the online player's profile, otherwise looked up at Mojang (cached by Paper). */
    private BufferedImage skin(UUID uuid) throws IOException, InterruptedException {
        Player online = Bukkit.getPlayer(uuid);
        PlayerProfile profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(uuid);
        if (!profile.hasTextures()) {
            profile.complete(true);
        }
        URL url = profile.getTextures().getSkin();
        if (url == null) {
            throw new IOException("no skin");
        }
        URI uri = URI.create(url.toString().replace("http://", "https://"));
        // Only Mojang's texture server - never fetch an address someone else could choose.
        if (!"textures.minecraft.net".equals(uri.getHost())) {
            throw new IOException("unexpected skin host " + uri.getHost());
        }
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > 64 * 1024) {
            throw new IOException("skin download failed: " + response.statusCode());
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null || image.getWidth() < 64 || image.getHeight() < 16) {
            throw new IOException("not a skin");
        }
        return image;
    }

    /** Face (8x8 at 8,8) with the hat layer (8x8 at 40,8) on top, scaled up without blurring. */
    private static byte[] render(BufferedImage skin) throws IOException {
        BufferedImage head = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = head.createGraphics();
        g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, SIZE, SIZE, null);
        g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, SIZE, SIZE, null);
        g.dispose();
        return png(head);
    }

    /** A plain head (grey with eyes) for players without a skin or when Mojang cannot be reached. */
    private synchronized byte[] fallback() {
        if (fallback == null) {
            int[] face = {
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 1, 1, 0, 0, 1, 1, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 2, 2, 0, 0, 0,
                    0, 0, 2, 0, 0, 2, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0};
            int[] colors = {0xFF8A8F98, 0xFF2B2F36, 0xFF6B7078};
            BufferedImage small = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < 64; i++) {
                small.setRGB(i % 8, i / 8, colors[face[i]]);
            }
            BufferedImage head = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = head.createGraphics();
            g.drawImage(small, 0, 0, SIZE, SIZE, null);
            g.dispose();
            try {
                fallback = png(head);
            } catch (IOException e) {
                fallback = new byte[0];
            }
        }
        return fallback;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
