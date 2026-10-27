package at.marcelr.mradmin.announce;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Shows an announcement on every player's screen (title + subtitle), optionally also in the chat and with a sound.
 * <p>
 * Presets live in the language file under {@code announce-presets.<name>} with the texts {@code title},
 * {@code subtitle} and {@code chat}. Values given after the preset name fill {@code <arg1>}, {@code <arg2>} ...
 * and all of them together {@code <args>}, e.g. {@code /mradmin announce restart 5m}.
 */
public final class Announcer {

    public static final String PRESETS = "announce-presets";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final MRAdmin plugin;

    public Announcer(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Names of all presets in the language file. */
    public List<String> presets() {
        return new ArrayList<>(plugin.messages().keys(PRESETS));
    }

    /** Whether a preset uses values (<args>, <arg1> ... or {args} in its Discord text), e.g. "restart 5 Minuten". */
    public boolean needsValues(String preset) {
        String base = PRESETS + "." + preset + ".";
        for (String part : List.of("title", "subtitle", "chat", "discord")) {
            String raw = plugin.messages().optional(base + part);
            if (raw.contains("<args>") || raw.contains("<arg") || raw.contains("{arg")) {
                return true;
            }
        }
        return false;
    }

    /** Whether a preset also posts to the community Discord (it has a discord text). */
    public boolean postsToDiscord(String preset) {
        return plugin.messages().has(PRESETS + "." + preset + ".discord");
    }

    /** The preset's title and subtitle as plain text (for the web interface's list). */
    public String preview(String preset) {
        Messages messages = plugin.messages();
        String base = PRESETS + "." + preset + ".";
        String title = PLAIN.serialize(messages.get(base + "title"));
        String subtitle = messages.has(base + "subtitle") ? PLAIN.serialize(messages.get(base + "subtitle")) : "";
        return subtitle.isBlank() ? title : title + " - " + subtitle;
    }

    /** The preset with this name (case-insensitive), if there is one. */
    public Optional<String> preset(String name) {
        return presets().stream().filter(p -> p.equalsIgnoreCase(name)).findFirst();
    }

    /** Announces a preset with its arguments; {@code toDiscord} false keeps it off the community Discord (-q). */
    public void announcePreset(String preset, List<String> args, String by, boolean toDiscord) {
        announcePreset(preset, args, Map.of(), by, toDiscord);
    }

    /**
     * Announces a preset with values by position ({@code <args>}, {@code <arg1>} ...) and by name
     * ({@code time="5m"} fills {@code <time>} and {@code {time}} in the Discord text).
     */
    public void announcePreset(String preset, List<String> args, Map<String, String> named, String by, boolean toDiscord) {
        Messages messages = plugin.messages();
        TagResolver[] resolvers = argResolvers(args, named);
        String base = PRESETS + "." + preset + ".";
        // Built once per player in their language, and once in the server language for log and Discord.
        Supplier<Shown> build = () -> new Shown(messages.get(base + "title", resolvers),
                messages.has(base + "subtitle") ? messages.get(base + "subtitle", resolvers) : Component.empty(),
                messages.has(base + "chat") ? messages.get(base + "chat", resolvers) : null);
        Component title = show(build, by);
        // Only presets with a "discord" text go to the community Discord (with a cooldown per preset).
        String discordText = messages.server(() -> messages.optional(base + "discord"));
        if (toDiscord && !discordText.isEmpty()) {
            String filled = discordText.replace("{args}", DiscordAnnouncer.escape(String.join(" ", args)));
            for (int i = 1; i <= 9; i++) {
                filled = filled.replace("{arg" + i + "}", i <= args.size() ? DiscordAnnouncer.escape(args.get(i - 1)) : "");
            }
            for (Map.Entry<String, String> value : named.entrySet()) {
                filled = filled.replace("{" + value.getKey() + "}", DiscordAnnouncer.escape(value.getValue()));
            }
            plugin.discordAnnounce().announce(preset, PLAIN.serialize(title), filled);
        }
    }

    /**
     * Announces a free text. {@code "Title | Subtitle"} splits it into both lines, otherwise the text becomes the
     * subtitle under the default heading {@code announce-custom-title}.
     */
    public void announceText(String text, String by, boolean toDiscord) {
        Messages messages = plugin.messages();
        int split = text.indexOf('|');
        Supplier<Shown> build = () -> {
            Component title = split >= 0 ? messages.parse(text.substring(0, split).trim()) : messages.get("announce-custom-title");
            Component subtitle = messages.parse(split >= 0 ? text.substring(split + 1).trim() : text);
            return new Shown(title, subtitle, messages.get("announce-custom-chat", tag("title", title), tag("message", subtitle)));
        };
        Component title = show(build, by);
        if (toDiscord) {
            Component subtitle = messages.server(build).subtitle();
            plugin.discordAnnounce().announce(null, PLAIN.serialize(title), DiscordAnnouncer.escape(PLAIN.serialize(subtitle)));
        }
    }

    /** Title, subtitle and chat line of an announcement in one language. */
    private record Shown(Component title, Component subtitle, Component chat) {
    }

    /**
     * Shows the announcement to every player in their language; logs it in the server language.
     * @return the title in the server language (for Discord)
     */
    private Component show(Supplier<Shown> build, String by) {
        Settings.AnnounceSettings s = plugin.settings().announce();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(s.fadeIn() * 50L), Duration.ofMillis(s.stay() * 50L), Duration.ofMillis(s.fadeOut() * 50L));
        Sound sound = s.sound() == null ? null : Sound.sound(s.sound(), Sound.Source.MASTER, s.volume(), s.pitch());
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.messages().run(player, () -> {
                Shown shown = build.get();
                player.showTitle(Title.title(shown.title(), shown.subtitle(), times));
                if (s.chat() && shown.chat() != null && !shown.chat().equals(Component.empty())) {
                    player.sendMessage(shown.chat());
                }
            });
            if (sound != null) {
                player.playSound(sound, Sound.Emitter.self());
            }
        }
        Shown server = plugin.messages().server(build);
        String text = PLAIN.serialize(server.title()) + " - " + PLAIN.serialize(server.subtitle());
        plugin.getLogger().info("Announcement by " + by + ": " + text);
        plugin.discord().log("announce", "admin", by, "text", text);
        return server.title();
    }

    // ------------------------------------------------------------------ parts (scripts)

    /**
     * An announcement put together from parts - for scripts, e.g. the countdown of a restart script. Every part is
     * optional and only shown if given (MiniMessage):
     * <ul>
     *   <li>{@code title}, {@code subtitle} - on the screen ({@code stay}, {@code fade-in}, {@code fade-out} in
     *       seconds, default from the config)</li>
     *   <li>{@code chat}, {@code actionbar}</li>
     *   <li>{@code discord} - posted to the community Discord (not with -q); {@code id} names it for the cooldown,
     *       so a countdown that sends it every step still posts only once</li>
     *   <li>{@code sound} (Bukkit name or key, "none" = silent; default from the config), {@code volume}, {@code pitch}</li>
     *   <li>{@code to} - "all" (default) or a permission, e.g. {@code to=mradmin.staffchat}</li>
     * </ul>
     *
     * @return null if it was shown, otherwise the name of the invalid part
     */
    public String announceParts(Map<String, String> parts, String by, boolean toDiscord) {
        // The parts are typed by the script, not from the language file - built once, in the server language.
        return plugin.messages().server(() -> buildParts(parts, by, toDiscord));
    }

    private String buildParts(Map<String, String> parts, String by, boolean toDiscord) {
        Settings.AnnounceSettings s = plugin.settings().announce();
        Component title = part(parts, "title");
        Component subtitle = part(parts, "subtitle");
        Component chat = part(parts, "chat");
        Component actionbar = part(parts, "actionbar");

        Duration fadeIn = seconds(parts.get("fade-in"), s.fadeIn());
        Duration stay = seconds(parts.get("stay"), s.stay());
        Duration fadeOut = seconds(parts.get("fade-out"), s.fadeOut());
        if (fadeIn == null || stay == null || fadeOut == null) {
            return fadeIn == null ? "fade-in" : stay == null ? "stay" : "fade-out";
        }
        Sound sound;
        String soundName = parts.get("sound");
        if (soundName == null) {
            sound = s.sound() == null ? null : Sound.sound(s.sound(), Sound.Source.MASTER, s.volume(), s.pitch());
        } else if (soundName.isBlank() || soundName.equalsIgnoreCase("none")) {
            sound = null;
        } else {
            Key key = Settings.sound(soundName, "sound", plugin.getLogger());
            Float volume = number(parts.get("volume"), s.volume());
            Float pitch = number(parts.get("pitch"), s.pitch());
            if (key == null || volume == null || pitch == null) {
                return key == null ? "sound" : volume == null ? "volume" : "pitch";
            }
            sound = Sound.sound(key, Sound.Source.MASTER, volume, pitch);
        }
        String to = parts.getOrDefault("to", "all").trim();

        Title shown = title == null && subtitle == null ? null : Title.title(
                title == null ? Component.empty() : title, subtitle == null ? Component.empty() : subtitle,
                Title.Times.times(fadeIn, stay, fadeOut));
        int reached = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!to.equalsIgnoreCase("all") && !player.hasPermission(to)) {
                continue;
            }
            reached++;
            if (shown != null) {
                player.showTitle(shown);
            }
            if (chat != null) {
                player.sendMessage(chat);
            }
            if (actionbar != null) {
                player.sendActionBar(actionbar);
            }
            if (sound != null) {
                player.playSound(sound, Sound.Emitter.self());
            }
        }

        String text = String.join(" - ", Stream.of(title, subtitle, chat, actionbar)
                .filter(Objects::nonNull).map(PLAIN::serialize).filter(t -> !t.isBlank()).toList());
        plugin.getLogger().info("Announcement by " + by + " (" + reached + " players): " + text);
        plugin.discord().log("announce", "admin", by, "text", text);
        String discord = parts.get("discord");
        if (toDiscord && discord != null && !discord.isBlank()) {
            plugin.discordAnnounce().announce(parts.get("id"), title == null ? "" : PLAIN.serialize(title), discord);
        }
        return null;
    }

    private Component part(Map<String, String> parts, String name) {
        String raw = parts.get(name);
        return raw == null ? null : plugin.messages().parse(raw);
    }

    /** Seconds ("2.5") as duration; {@code fallbackTicks} if not given, null if invalid. */
    private static Duration seconds(String raw, int fallbackTicks) {
        if (raw == null) {
            return Duration.ofMillis(fallbackTicks * 50L);
        }
        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            return value < 0 || value > 600 ? null : Duration.ofMillis(Math.round(value * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float number(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw.trim().replace(',', '.'));
            return value < 0 || value > 10 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@code <args>} = all values, {@code <arg1>} ... {@code <arg9>} = single values (empty if not given), plus the
     * named values ({@code <time>} ...).
     */
    private static TagResolver[] argResolvers(List<String> args, Map<String, String> named) {
        List<TagResolver> list = new ArrayList<>();
        named.forEach((name, value) -> list.add(tag(name, value)));
        list.add(tag("args", String.join(" ", args)));
        for (int i = 1; i <= 9; i++) {
            list.add(tag("arg" + i, i <= args.size() ? args.get(i - 1) : ""));
        }
        return list.toArray(TagResolver[]::new);
    }
}
