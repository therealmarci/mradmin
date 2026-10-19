package at.marcelr.mradmin.hook;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional connection to other plugins without any build dependency in either direction. A plugin offers a public
 * class (named in admin-mode.menu.buttons.&lt;id&gt;.hook, e.g. {@code at.marcelr.mrchunks.api.MRadminHook}) with:
 * <pre>
 *   public static final int API_VERSION = 1;
 *   public static List&lt;Component&gt; summary(Player viewer)                                  // lines for its menu button
 *   public static List&lt;Component&gt; playerSummary(Player viewer, UUID target)                 // lines in the player menu
 *   public static boolean openPlayerMenu(Player viewer, UUID target, Runnable back)          // false = not allowed
 * </pre>
 * Version 2 may add (optional, used only if present):
 * <pre>
 *   public static List&lt;Map&lt;String, Object&gt;&gt; activity(Player viewer)   // entries "icon" (ItemStack), "id" (String)
 *   public static boolean openActivity(Player viewer, String id, Runnable back)
 * </pre>
 * MRchunks uses that for chunk loaders with conspicuous activity in the server health menu.
 * Version 3 may add {@code EVENT}: MRchunks' lock event, MRadmin opens a ticket for it (see {@link LockTickets}).
 * Version 4 may add {@code public static Object web(String request, Map<String, Object> args)}: plain data and
 * actions for the web interface (server thread only).
 * The class is looked up in that plugin's class loader when it is needed; a missing, outdated or broken hook simply
 * means the button works without live information (it still runs its command).
 */
public final class PluginHooks {

    /** Oldest hook version this MRadmin understands; newer ones are fine (extra methods are optional). */
    public static final int API_VERSION = 1;

    /** Resolved methods of one hook class. */
    private record Hook(Plugin owner, MethodHandle summary, MethodHandle playerSummary, MethodHandle openPlayerMenu,
                        MethodHandle activity, MethodHandle openActivity, MethodHandle web) {
    }

    private static final Hook NONE = new Hook(null, null, null, null, null, null, null);

    private final MRAdmin plugin;
    private final Map<String, Hook> hooks = new ConcurrentHashMap<>();

    public PluginHooks(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Forgets all resolved hooks (after a reload or when plugins were reloaded). */
    public void clear() {
        hooks.clear();
    }

    /** Whether the button's plugin offers a working hook. */
    public boolean available(Settings.HubButton button) {
        return hook(button) != NONE;
    }

    public List<Component> summary(Settings.HubButton button, Player viewer) {
        return lines(button, hook(button).summary(), viewer, null);
    }

    public List<Component> playerSummary(Settings.HubButton button, Player viewer, UUID target) {
        return lines(button, hook(button).playerSummary(), viewer, target);
    }

    /** @return true if the other plugin opened its player menu */
    public boolean openPlayerMenu(Settings.HubButton button, Player viewer, UUID target, Runnable back) {
        Hook hook = hook(button);
        if (hook.openPlayerMenu() == null) {
            return false;
        }
        try {
            return (boolean) hook.openPlayerMenu().invoke(viewer, target, back);
        } catch (Throwable t) {
            broken(button, t);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Component> lines(Settings.HubButton button, MethodHandle handle, Player viewer, UUID target) {
        if (handle == null) {
            return List.of();
        }
        try {
            Object result = target == null ? handle.invoke(viewer) : handle.invoke(viewer, target);
            return result instanceof List<?> list ? (List<Component>) list : List.of();
        } catch (Throwable t) {
            broken(button, t);
            return List.of();
        }
    }

    private void broken(Settings.HubButton button, Throwable t) {
        plugin.getLogger().warning("Hook of " + button.plugin() + " (" + button.hook() + ") failed, using the button without it: " + t);
        hooks.put(button.id(), NONE);
    }

    /** A method that only newer hook versions have; null if it is not there. */
    private static MethodHandle optional(MethodHandles.Lookup lookup, Class<?> type, String name, MethodType methodType) {
        try {
            return lookup.findStatic(type, name, methodType);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Whether the button's plugin reports activity (conspicuous objects for the server health menu). */
    public boolean hasActivity(Settings.HubButton button) {
        return hook(button).activity() != null;
    }

    /** Activity entries of the plugin: "icon" (ItemStack) and "id" (String). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> activity(Settings.HubButton button, Player viewer) {
        MethodHandle handle = hook(button).activity();
        if (handle == null) {
            return List.of();
        }
        try {
            Object result = handle.invoke(viewer);
            return result instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        } catch (Throwable t) {
            broken(button, t);
            return List.of();
        }
    }

    /** Opens what an activity entry stands for (e.g. a chunk loader's menu). */
    public boolean openActivity(Settings.HubButton button, Player viewer, String id, Runnable back) {
        MethodHandle handle = hook(button).openActivity();
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(viewer, id, back);
        } catch (Throwable t) {
            broken(button, t);
            return false;
        }
    }

    /** Whether the plugin offers data for the web interface (hook version 4). */
    public boolean hasWeb(Settings.HubButton button) {
        return hook(button).web() != null;
    }

    /**
     * Asks the plugin for web data or runs a web action (server thread only).
     * @return its answer (maps, lists, strings), or null if there is none
     */
    public Object web(Settings.HubButton button, String request, Map<String, Object> args) {
        MethodHandle handle = hook(button).web();
        if (handle == null) {
            return null;
        }
        try {
            return handle.invoke(request, args);
        } catch (Throwable t) {
            broken(button, t);
            return null;
        }
    }

    private Hook hook(Settings.HubButton button) {
        if (button.hook().isEmpty() || button.plugin().isEmpty()) {
            return NONE;
        }
        Plugin owner = Bukkit.getPluginManager().getPlugin(button.plugin());
        if (owner == null || !owner.isEnabled()) {
            return NONE;
        }
        Hook cached = hooks.get(button.id());
        if (cached != null && (cached == NONE || cached.owner() == owner)) {
            return cached;
        }
        Hook resolved = resolve(button, owner);
        hooks.put(button.id(), resolved);
        return resolved;
    }

    private Hook resolve(Settings.HubButton button, Plugin owner) {
        try {
            Class<?> type = Class.forName(button.hook(), true, owner.getClass().getClassLoader());
            int version = type.getField("API_VERSION").getInt(null);
            if (version < API_VERSION) {
                plugin.getLogger().warning(button.plugin() + " offers MRadmin hook version " + version + ", this MRadmin needs at least "
                        + API_VERSION + " - update both plugins. The button works without live information.");
                return NONE;
            }
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType listOfPlayer = MethodType.methodType(List.class, Player.class);
            MethodType listOfPlayerUuid = MethodType.methodType(List.class, Player.class, UUID.class);
            MethodType open = MethodType.methodType(boolean.class, Player.class, UUID.class, Runnable.class);
            return new Hook(owner,
                    lookup.findStatic(type, "summary", listOfPlayer),
                    lookup.findStatic(type, "playerSummary", listOfPlayerUuid),
                    lookup.findStatic(type, "openPlayerMenu", open),
                    optional(lookup, type, "activity", listOfPlayer),
                    optional(lookup, type, "openActivity", MethodType.methodType(boolean.class, Player.class, String.class, Runnable.class)),
                    optional(lookup, type, "web", MethodType.methodType(Object.class, String.class, Map.class)));
        } catch (ClassNotFoundException e) {
            // Older version of the plugin without hook - fine, the button simply runs its command.
            return NONE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Could not connect to " + button.plugin() + " (" + button.hook() + "): " + e);
            return NONE;
        }
    }
}
