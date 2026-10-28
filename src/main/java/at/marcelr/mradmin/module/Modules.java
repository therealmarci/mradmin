package at.marcelr.mradmin.module;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.HealthEntry;
import at.marcelr.mradmin.api.MRadminService;
import at.marcelr.mradmin.api.WebItems;
import at.marcelr.mradmin.api.Report;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.hook.Integration;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The plugins MRadmin works with ("modules") - one place for every part of MRadmin that shows or does something of
 * another plugin: dashboard button, player menu, server health, tickets, web interface. Also MRadmin's side of the
 * module API ({@link MRadminService}, offered in Bukkit's services manager).
 * <p>
 * Plugins register an {@link AdminModule} (module API, see docs/MODULE-API.md). Every call into a module is guarded: a
 * module that throws is logged and skipped, it never breaks MRadmin.
 */
public final class Modules implements MRadminService {

    private final MRAdmin plugin;
    /** Registered modules by id. */
    private final Map<String, AdminModule> registered = new ConcurrentHashMap<>();
    private final WebItems items = new at.marcelr.mradmin.items.ItemCodec();

    public Modules(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Offers the API to other plugins. Call early in onEnable - plugins with softdepend MRadmin enable later. */
    public void offer() {
        Bukkit.getServicesManager().register(MRadminService.class, this, plugin, ServicePriority.Normal);
    }

    /** Removes the modules of a plugin that was disabled. @return whether it had one */
    public boolean pluginDisabled(Plugin other) {
        return registered.values().removeIf(module -> module.plugin() == other);
    }

    // ------------------------------------------------------------ MRadminService

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public WebItems items() {
        return items;
    }

    @Override
    public void register(AdminModule module) {
        String id = module.id().toLowerCase(Locale.ROOT);
        AdminModule old = registered.put(id, module);
        if (old == null || old.plugin() != module.plugin()) {
            plugin.getLogger().info(module.plugin().getName() + " connected as module '" + id + "'"
                    + (enabled(id) ? "." : " (switched off: integrations." + id + ": false)."));
        }
    }

    @Override
    public void unregister(String moduleId) {
        registered.remove(moduleId.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean adminMode(Player player) {
        return plugin.isEnabled() && plugin.adminMode() != null && plugin.adminMode().active(player);
    }

    @Override
    public @Nullable String busy(Player player) {
        for (AdminModule module : registered.values()) {
            if (module.plugin().isEnabled() && call(module, () -> module.busy(player), false)) {
                return module.id();
            }
        }
        return null;
    }

    @Override
    public @Nullable String worldOwner(World world) {
        for (AdminModule module : registered.values()) {
            if (module.plugin().isEnabled() && call(module, () -> module.ownsWorld(world), false)) {
                return module.id();
            }
        }
        return null;
    }

    @Override
    public Map<String, List<Map<String, Object>>> storedItems(Player player) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (AdminModule module : registered.values()) {
            List<Map<String, Object>> items = module.plugin().isEnabled()
                    ? call(module, () -> module.storedItems(player), List.of()) : List.of();
            if (!items.isEmpty()) {
                result.put(module.id(), items);
            }
        }
        return result;
    }

    @Override
    public void webChanged(String moduleId, String type, String id) {
        if (plugin.isEnabled() && plugin.web() != null) {
            plugin.web().events().plugin(moduleId, type, id);
        }
    }

    @Override
    public void adminLog(String moduleId, String text) {
        if (plugin.isEnabled()) {
            plugin.discord().log("plugin-alert", "plugin", displayName(moduleId), "text", text);
        }
    }

    @Override
    public void report(String moduleId, Report report) {
        if (plugin.isEnabled() && plugin.feature(Feature.TICKETS) && enabled(moduleId)) {
            plugin.tickets().report(source(moduleId), report);
        }
    }

    @Override
    public void resolve(String moduleId, String key, String note) {
        if (plugin.isEnabled() && plugin.feature(Feature.TICKETS)) {
            plugin.tickets().resolve(source(moduleId), key, note);
        }
    }

    /** Tickets name their source by plugin name ("MRchunks") - as before the module API. */
    private String source(String moduleId) {
        return displayName(moduleId);
    }

    /** Plugin name of a module ("MRchunks"), or the id. */
    public String displayName(String moduleId) {
        AdminModule module = registered.get(moduleId.toLowerCase(Locale.ROOT));
        if (module != null) {
            return module.plugin().getName();
        }
        Integration integration = Integration.ALL.stream().filter(i -> i.id().equals(moduleId)).findFirst().orElse(null);
        return integration != null ? integration.plugin() : moduleId;
    }

    // ------------------------------------------------------------ buttons

    /** Not switched off with {@code integrations.<id>: false}. */
    public boolean enabled(String moduleId) {
        return plugin.getConfig().getBoolean("integrations." + moduleId.toLowerCase(Locale.ROOT), true);
    }

    /**
     * The dashboard buttons: one per connected module (MRtechnology plugins in their usual order, then others) with
     * the module's icon and view permission, followed by the owner's own buttons - at most 6.
     */
    public List<Settings.HubButton> buttons() {
        List<AdminModule> modules = new ArrayList<>(registered.values());
        modules.removeIf(module -> !enabled(module.id()));
        modules.sort(java.util.Comparator.comparingInt((AdminModule m) -> {
            int index = Integration.ALL.stream().map(Integration::id).toList().indexOf(m.id());
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(AdminModule::id));
        List<Settings.HubButton> buttons = new ArrayList<>();
        for (AdminModule module : modules) {
            // A plugin version whose module opens no menu itself: the button runs its admin command.
            Integration known = Integration.ALL.stream().filter(i -> i.id().equals(module.id())).findFirst().orElse(null);
            buttons.add(new Settings.HubButton(module.id(), module.plugin().getName(), module.icon(),
                    known == null ? "" : known.command(), module.viewPermission()));
        }
        for (Settings.HubButton own : plugin.settings().adminMode().buttons()) {
            if (buttons.stream().noneMatch(b -> b.id().equals(own.id()))) {
                buttons.add(own);
            }
        }
        return buttons.size() > 6 ? List.copyOf(buttons.subList(0, 6)) : buttons;
    }

    /** The module behind a dashboard button, or null if its plugin registered none (or is not running). */
    public AdminModule module(Settings.HubButton button) {
        AdminModule module = registered.get(button.id());
        return module != null && module.plugin().isEnabled() ? module : null;
    }

    /** The connected module with this id (switched on, plugin running), or null. */
    public AdminModule module(String id) {
        for (Settings.HubButton button : buttons()) {
            if (button.id().equals(id)) {
                return module(button);
            }
        }
        return null;
    }

    /** All connected modules, in button order. */
    public List<AdminModule> all() {
        List<AdminModule> modules = new ArrayList<>();
        for (Settings.HubButton button : buttons()) {
            AdminModule module = module(button);
            if (module != null) {
                modules.add(module);
            }
        }
        return modules;
    }

    // ------------------------------------------------------- guarded calls

    public List<Component> summary(Settings.HubButton button, Player viewer) {
        AdminModule module = module(button);
        return module == null ? List.of() : call(module, () -> module.summary(viewer), List.of());
    }

    public List<Component> playerSummary(Settings.HubButton button, Player viewer, UUID target) {
        AdminModule module = module(button);
        return module == null ? List.of() : call(module, () -> module.playerSummary(viewer, target), List.of());
    }

    /** @return true if the module opened its player menu */
    public boolean openPlayerMenu(Settings.HubButton button, Player viewer, UUID target, Runnable back) {
        AdminModule module = module(button);
        return module != null && call(module, () -> module.openPlayerMenu(viewer, target, back), false);
    }

    /** @return true if the module opened its own menu (otherwise the button runs its command) */
    public boolean openMenu(Settings.HubButton button, Player viewer, Runnable back) {
        AdminModule module = module(button);
        return module != null && call(module, () -> module.openMenu(viewer, back), false);
    }

    /** Whether the module reports conspicuous things for the server health menu. */
    public boolean hasHealth(Settings.HubButton button) {
        AdminModule module = module(button);
        return module != null;
    }

    public List<HealthEntry> health(Settings.HubButton button, Player viewer) {
        AdminModule module = module(button);
        return module == null ? List.of() : call(module, () -> module.health(viewer), List.of());
    }

    public boolean openHealth(Settings.HubButton button, Player viewer, String id, Runnable back) {
        AdminModule module = module(button);
        return module != null && call(module, () -> module.openHealth(viewer, id, back), false);
    }

    /** The web part of a connected module, or null. */
    public WebModule web(String id) {
        AdminModule module = module(id);
        return module == null ? null : call(module, module::web, null);
    }

    /** Web parts of all connected modules that have one, by id. */
    public Map<String, WebModule> webModules() {
        Map<String, WebModule> result = new LinkedHashMap<>();
        for (AdminModule module : all()) {
            WebModule web = call(module, module::web, null);
            if (web != null) {
                result.put(module.id(), web);
            }
        }
        return result;
    }

    /**
     * Runs an action of a module ({@link WebModule#action} and the like): its answer as it is - null means done, a
     * text is an error key. Only if the module throws, it is logged and "error" returned.
     */
    public @Nullable String attempt(AdminModule module, Supplier<@Nullable String> action) {
        try {
            return action.get();
        } catch (Throwable t) {
            plugin.getLogger().warning("Module '" + module.id() + "' (" + module.plugin().getName() + ") failed: " + t);
            return "error";
        }
    }

    /** Ticket categories of all modules' reports (kind -> name in the module's language), see {@link WebModule#reportCategories}. */
    public Map<String, String> reportCategories() {
        Map<String, String> result = new LinkedHashMap<>();
        for (AdminModule module : all()) {
            WebModule web = call(module, module::web, null);
            if (web != null) {
                result.putAll(call(module, web::reportCategories, Map.of()));
            }
        }
        return result;
    }

    /** Whether one of the modules calls the player its NPC (e.g. MRchunks' dummies), see {@link AdminModule#isNpc}. */
    public boolean npc(org.bukkit.entity.Player player) {
        for (AdminModule module : registered.values()) {
            if (call(module, () -> module.isNpc(player), false)) {
                return true;
            }
        }
        return false;
    }

    /** Chunks the modules keep loaded themselves (e.g. chunk loaders) together, -1 if none does. */
    public int loadedChunks() {
        int sum = -1;
        for (AdminModule module : registered.values()) {
            int chunks = call(module, module::loadedChunks, -1);
            if (chunks >= 0) {
                sum = Math.max(sum, 0) + chunks;
            }
        }
        return sum;
    }

    /** Runs a call into another plugin that answers with a value; if it throws or answers null, {@code fallback}. */
    public <T> T call(AdminModule module, Supplier<T> call, T fallback) {
        try {
            T result = call.get();
            return result == null ? fallback : result;
        } catch (Throwable t) {
            plugin.getLogger().warning("Module '" + module.id() + "' (" + module.plugin().getName() + ") failed: " + t);
            return fallback;
        }
    }
}
