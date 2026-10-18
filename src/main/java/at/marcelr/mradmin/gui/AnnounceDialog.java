package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static at.marcelr.mradmin.Messages.tag;

/** Native Minecraft dialogs of the announcement menu: values for a preset, or an own text. */
final class AnnounceDialog {

    private static final String VALUES = "values";
    private static final String TITLE = "title";
    private static final String SUBTITLE = "subtitle";
    private static final String DISCORD = "discord";
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private AnnounceDialog() {
    }

    /** Asks for the values of a preset (e.g. "5 Minuten" for restart), then announces it. */
    static void values(MRAdmin plugin, Player viewer, String preset, boolean toDiscord, Runnable back) {
        Messages messages = plugin.messages();
        DialogAction send = DialogAction.customClick((response, audience) -> {
            String text = response.getText(VALUES);
            // Run on the main thread: the click may arrive on the network thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                List<String> values = text == null || text.isBlank() ? List.of() : Arrays.asList(text.trim().split("\\s+"));
                plugin.announcer().announcePreset(preset, values, viewer.getName(), toDiscord);
            });
        }, ONCE);
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("announce-dialog-title", tag("preset", preset)))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("announce-dialog-hint", tag("preset", preset)))))
                        .inputs(List.of(DialogInput.text(VALUES, messages.get("announce-dialog-label"))
                                .maxLength(100).width(250).build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("announce-dialog-send"), null, 120, send),
                        ActionButton.create(messages.get("announce-dialog-cancel"), null, 120, cancel(plugin, viewer, back)))));
        viewer.closeInventory();
        viewer.showDialog(dialog);
    }

    /** Own text: title, subtitle and whether it also goes to the community Discord. */
    static void custom(MRAdmin plugin, Player viewer, Runnable back) {
        Messages messages = plugin.messages();
        boolean discordAvailable = plugin.discordAnnounce().enabled();
        DialogAction send = DialogAction.customClick((response, audience) -> {
            String title = response.getText(TITLE);
            String subtitle = response.getText(SUBTITLE);
            Boolean discord = discordAvailable ? response.getBoolean(DISCORD) : Boolean.FALSE;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                String t = title == null ? "" : title.trim();
                String s = subtitle == null ? "" : subtitle.trim();
                if (t.isEmpty() && s.isEmpty()) {
                    plugin.messages().send(viewer, "announce-dialog-empty");
                    back.run();
                    return;
                }
                // "Title | Subtitle", or just the text under the default heading.
                String text = t.isEmpty() ? s : s.isEmpty() ? t + " |" : t + " | " + s;
                plugin.announcer().announceText(text, viewer.getName(), Boolean.TRUE.equals(discord));
            });
        }, ONCE);
        List<DialogInput> inputs = new java.util.ArrayList<>(List.of(
                DialogInput.text(TITLE, messages.get("announce-custom-dialog-title-label")).maxLength(60).width(250).build(),
                DialogInput.text(SUBTITLE, messages.get("announce-custom-dialog-subtitle-label")).maxLength(150).width(250).build()));
        if (discordAvailable) {
            inputs.add(DialogInput.bool(DISCORD, messages.get("announce-custom-dialog-discord-label")).initial(false).build());
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("announce-custom-dialog-title"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("announce-custom-dialog-hint"))))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("announce-dialog-send"), null, 120, send),
                        ActionButton.create(messages.get("announce-dialog-cancel"), null, 120, cancel(plugin, viewer, back)))));
        viewer.closeInventory();
        viewer.showDialog(dialog);
    }

    private static DialogAction cancel(MRAdmin plugin, Player viewer, Runnable back) {
        return DialogAction.customClick((response, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                back.run();
            }
        }), ONCE);
    }
}
