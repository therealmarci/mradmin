package at.marcelr.mradmin.news;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Brings the team's posts to the players (see {@link News}), main thread only:
 * <ul>
 *   <li><b>Discord</b> (community webhook) - only posts whose author ticked it, with a link to the whole post</li>
 *   <li><b>News and changelogs</b> once per player as a short preview in the chat with a link to the player page: to
 *   everyone online when published, otherwise on the next join (at most {@code player-web.news.chat-preview.max},
 *   not older than {@code max-age-days}). Only for players who may open the page (mradmin.web.self).</li>
 *   <li><b>Warnings</b> (written in MRadmin or pinned in Uptime Kuma, see {@link KumaIncidents}) to everyone online in
 *   the chat and as a title, and in the chat on every join while they are on.</li>
 * </ul>
 * Who saw which post is kept in the storage file "news-seen" (the highest post id per player).
 */
public final class NewsDelivery implements Listener {

    private static final String SEEN_FILE = "news-seen";

    private final MRAdmin plugin;
    private final News news;
    private final KumaIncidents incidents;
    private final Map<UUID, Integer> seen = new HashMap<>();
    private boolean loaded;

    public NewsDelivery(MRAdmin plugin, News news) {
        this.plugin = plugin;
        this.news = news;
        this.incidents = new KumaIncidents(plugin, this::incident);
    }

    public KumaIncidents incidents() {
        return incidents;
    }

    private Settings.PlayerWebSettings settings() {
        return plugin.settings().playerWeb();
    }

    /** The whole post on the player page, or null without web.public-url. */
    public String link(News.Post post) {
        String url = plugin.settings().web().publicUrl();
        return url.isEmpty() ? null : url + "/me/news#post-" + post.id();
    }

    // ---------------------------------------------------------------- publishing

    /** A post was created (not changed): Discord if asked for, chat previews or the warning in game. */
    public void published(News.Post post, boolean discord) {
        if (discord && discord(post)) {
            news.markDiscord(post.id());
        }
        if (post.alert()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                alert(player, post, true);
            }
            return;
        }
        if (!settings().enabled() || !settings().news() || !settings().chatPreview()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(Perms.WEB_SELF)) {
                preview(player, post);
                markSeen(player.getUniqueId(), post.id());
            }
        }
    }

    /** Posts it to the community Discord. @return whether it went out */
    public boolean discord(News.Post post) {
        DiscordAnnouncer discord = plugin.discordAnnounce();
        if (discord == null || !discord.enabled()) {
            return false;
        }
        String lang = plugin.messages().serverLanguage();
        String title = post.heading(lang);
        StringBuilder body = new StringBuilder();
        if ("changelog".equals(post.kind())) {
            if (!post.version().isEmpty() && !post.version().equals(title)) {
                title = post.version() + " · " + title;
            }
            for (String type : News.ENTRY_TYPES) {
                List<String> lines = post.entries().stream().filter(e -> e.type().equals(type))
                        .map(e -> "• " + DiscordAnnouncer.escape(post.in(e.text(), lang))).toList();
                if (!lines.isEmpty()) {
                    body.append("**").append(discord.label("changelog-" + type)).append("**\n")
                            .append(String.join("\n", lines)).append("\n\n");
                }
            }
            String text = post.in(post.text(), lang);
            if (body.isEmpty() && !text.isEmpty()) {
                body.append(DiscordAnnouncer.escape(text));
            }
        } else {
            body.append(DiscordAnnouncer.escape(post.in(post.text(), lang)));
        }
        String head = post.alert() ? "⚠️ " + title : "changelog".equals(post.kind()) ? "📝 " + title : "📣 " + title;
        return discord.postNews(post.kind(), head, body.toString().strip(), link(post));
    }

    /** A new or changed incident on an Uptime Kuma status page: like a warning written in MRadmin. */
    private void incident(KumaIncidents.Incident incident) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            incidentAlert(player, incident, true);
        }
    }

    // --------------------------------------------------------------------- join

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A moment later, so it is not lost among the join messages.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (News.Post alert : news.activeAlerts()) {
                alert(player, alert, false);
            }
            for (KumaIncidents.Incident incident : incidents.current()) {
                incidentAlert(player, incident, false);
            }
            if (settings().enabled() && settings().news() && settings().chatPreview()
                    && player.hasPermission(Perms.WEB_SELF)) {
                unseen(player);
            }
        }, 60L);
    }

    /** The newest posts the player has not had yet (at most chat-preview.max, then a hint how many more). */
    private void unseen(Player player) {
        int last = seen(player.getUniqueId());
        long since = System.currentTimeMillis() - settings().chatPreviewDays() * 86_400_000L;
        List<News.Post> fresh = new ArrayList<>(news.visible().stream()
                .filter(p -> !p.alert() && p.id() > last && p.created() >= since).toList());
        int newest = news.all().stream().mapToInt(News.Post::id).max().orElse(last);
        if (!fresh.isEmpty()) {
            fresh.sort(Comparator.comparingLong(News.Post::created).reversed());
            int max = settings().chatPreviewMax();
            for (News.Post post : fresh.subList(0, Math.min(max, fresh.size())).reversed()) {
                preview(player, post);
            }
            if (fresh.size() > max) {
                plugin.messages().run(player, () -> plugin.messages().send(player, "news-preview-more",
                        tag("count", fresh.size() - max), tag("link", linkButton(pageLink()))));
            }
        }
        markSeen(player.getUniqueId(), newest);
    }

    // -------------------------------------------------------------------- messages

    private void preview(Player player, News.Post post) {
        plugin.messages().run(player, () -> {
            String lang = plugin.messages().languageOf(player);
            String key = "changelog".equals(post.kind()) ? "news-preview-changelog" : "news-preview";
            plugin.messages().send(player, key, tag("title", post.heading(lang)), tag("preview", post.preview(lang, 120)),
                    tag("link", linkButton(link(post))));
        });
    }

    private void alert(Player player, News.Post post, boolean fresh) {
        if (!settings().alertChat() && !(fresh && settings().alertTitle())) {
            return;
        }
        plugin.messages().run(player, () -> {
            String lang = plugin.messages().languageOf(player);
            String link = player.hasPermission(Perms.WEB_SELF) ? link(post) : null;
            show(player, post.heading(lang), post.preview(lang, 160), link, fresh);
        });
    }

    private void incidentAlert(Player player, KumaIncidents.Incident incident, boolean fresh) {
        String url = plugin.settings().kuma().url() + "/status/" + incident.slug();
        plugin.messages().run(player, () -> {
            String text = incident.content().replace('\n', ' ');
            show(player, incident.title(), text.length() > 160 ? text.substring(0, 159) + "…" : text, url, fresh);
        });
    }

    /** Chat line (always, if alerts.chat) and, when it is new, the title on the screen (alerts.title). */
    private void show(Player player, String title, String text, String link, boolean fresh) {
        Messages m = plugin.messages();
        if (settings().alertChat()) {
            m.send(player, "alert-chat", tag("title", title), tag("text", text), tag("link", linkButton(link)));
        }
        if (fresh && settings().alertTitle()) {
            player.showTitle(Title.title(m.get("alert-title", tag("title", title)), m.get("alert-subtitle", tag("text", text)),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(6), Duration.ofMillis(800))));
        }
    }

    private String pageLink() {
        String url = plugin.settings().web().publicUrl();
        return url.isEmpty() ? null : url + "/me/news";
    }

    /** "[Read]" opening the link, or nothing without a link. */
    private Component linkButton(String link) {
        if (link == null) {
            return Component.empty();
        }
        return plugin.messages().get("news-preview-link").clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(plugin.messages().get("news-preview-hover"));
    }

    // ------------------------------------------------------------------- storage

    private int seen(UUID player) {
        load();
        return seen.getOrDefault(player, 0);
    }

    private void markSeen(UUID player, int id) {
        load();
        if (id > seen.getOrDefault(player, 0)) {
            seen.put(player, id);
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("MRadmin - the newest post each player got as a chat preview (managed by the plugin)."));
            seen.forEach((uuid, last) -> y.set(uuid.toString(), last));
            plugin.storage().saveYaml(SEEN_FILE, y);
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        YamlConfiguration y = plugin.storage().loadYaml(SEEN_FILE, plugin.getLogger());
        for (String key : y.getKeys(false)) {
            try {
                seen.put(UUID.fromString(key), y.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
    }
}
