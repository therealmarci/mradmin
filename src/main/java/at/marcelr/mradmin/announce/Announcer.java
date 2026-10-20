package at.marcelr.mradmin.announce;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
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
import java.util.Optional;

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
        plugin.messages().forPlayers(() -> {
            buildPreset(preset, args, by, toDiscord);
            return null;
        });
    }

    private void buildPreset(String preset, List<String> args, String by, boolean toDiscord) {
        Messages messages = plugin.messages();
        TagResolver[] resolvers = argResolvers(args);
        String base = PRESETS + "." + preset + ".";
        Component title = messages.get(base + "title", resolvers);
        Component subtitle = messages.has(base + "subtitle") ? messages.get(base + "subtitle", resolvers) : Component.empty();
        Component chat = messages.has(base + "chat") ? messages.get(base + "chat", resolvers) : null;
        show(title, subtitle, chat, by);
        // Only presets with a "discord" text go to the community Discord (with a cooldown per preset).
        String discordText = messages.optional(base + "discord");
        if (toDiscord && !discordText.isEmpty()) {
            String filled = discordText.replace("{args}", DiscordAnnouncer.escape(String.join(" ", args)));
            for (int i = 1; i <= 9; i++) {
                filled = filled.replace("{arg" + i + "}", i <= args.size() ? DiscordAnnouncer.escape(args.get(i - 1)) : "");
            }
            plugin.discordAnnounce().announce(preset, PLAIN.serialize(title), filled);
        }
    }

    /**
     * Announces a free text. {@code "Title | Subtitle"} splits it into both lines, otherwise the text becomes the
     * subtitle under the default heading {@code announce-custom-title}.
     */
    public void announceText(String text, String by, boolean toDiscord) {
        plugin.messages().forPlayers(() -> {
            buildText(text, by, toDiscord);
            return null;
        });
    }

    private void buildText(String text, String by, boolean toDiscord) {
        Messages messages = plugin.messages();
        Component title;
        Component subtitle;
        int split = text.indexOf('|');
        if (split >= 0) {
            title = messages.parse(text.substring(0, split).trim());
            subtitle = messages.parse(text.substring(split + 1).trim());
        } else {
            title = messages.get("announce-custom-title");
            subtitle = messages.parse(text);
        }
        Component chat = messages.get("announce-custom-chat", tag("title", title), tag("message", subtitle));
        show(title, subtitle, chat, by);
        if (toDiscord) {
            plugin.discordAnnounce().announce(null, PLAIN.serialize(title), DiscordAnnouncer.escape(PLAIN.serialize(subtitle)));
        }
    }

    private void show(Component title, Component subtitle, Component chat, String by) {
        Settings.AnnounceSettings s = plugin.settings().announce();
        Title shown = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(s.fadeIn() * 50L), Duration.ofMillis(s.stay() * 50L), Duration.ofMillis(s.fadeOut() * 50L)));
        Sound sound = s.sound() == null ? null : Sound.sound(s.sound(), Sound.Source.MASTER, s.volume(), s.pitch());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(shown);
            if (s.chat() && chat != null && !chat.equals(Component.empty())) {
                player.sendMessage(chat);
            }
            if (sound != null) {
                player.playSound(sound, Sound.Emitter.self());
            }
        }
        String text = PLAIN.serialize(title) + " - " + PLAIN.serialize(subtitle);
        plugin.getLogger().info("Announcement by " + by + ": " + text);
        plugin.discord().log("announce", "admin", by, "text", text);
    }

    /** {@code <args>} = all values, {@code <arg1>} ... {@code <arg9>} = single values (empty if not given). */
    private static TagResolver[] argResolvers(List<String> args) {
        List<TagResolver> list = new ArrayList<>();
        list.add(tag("args", String.join(" ", args)));
        for (int i = 1; i <= 9; i++) {
            list.add(tag("arg" + i, i <= args.size() ? args.get(i - 1) : ""));
        }
        return list.toArray(TagResolver[]::new);
    }
}
