package at.marcelr.mradmin.hook;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Opens a ticket for the owner when MRchunks locks a chunk loader because of lag (and notes the unlock in it).
 * MRchunks offers its event class in its hook ({@code MRadminHook.EVENT}); the listener is registered for it by
 * reflection while MRchunks runs, so neither plugin needs the other. Whether a ticket is opened is decided per event:
 * tickets on, MRchunks integration on and {@code tickets.chunkloader-locks}.
 */
public final class LockTickets implements Listener, EventExecutor {

    private static final String INTEGRATION = "mrchunks";

    private final MRAdmin plugin;
    private Plugin registeredFor;

    public LockTickets(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Listens to MRchunks if it runs and offers the event (hook version 3); otherwise does nothing. */
    public void register(Plugin mrchunks) {
        unregister();
        Integration integration = Integration.ALL.stream().filter(i -> i.id().equals(INTEGRATION)).findFirst().orElseThrow();
        try {
            Class<?> hook = Class.forName(integration.hook(), true, mrchunks.getClass().getClassLoader());
            Object type = hook.getField("EVENT").get(null);
            if (!(type instanceof Class<?> eventClass) || !Event.class.isAssignableFrom(eventClass)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> events = (Class<? extends Event>) eventClass;
            plugin.getServer().getPluginManager().registerEvent(events, this, EventPriority.MONITOR, this, plugin, true);
            registeredFor = mrchunks;
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            // Older MRchunks without lock events - simply no tickets.
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Could not listen to chunk loader locks of " + mrchunks.getName() + ": " + e);
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        registeredFor = null;
    }

    /** Whether the listener belongs to this plugin instance. */
    public boolean registeredFor(Plugin other) {
        return registeredFor == other;
    }

    @Override
    public void execute(Listener listener, Event event) {
        if (!plugin.feature(Feature.TICKETS) || !plugin.settings().tickets().chunkloaderLocks()
                || plugin.settings().adminMode().buttons().stream().noneMatch(b -> b.id().equals(INTEGRATION))) {
            return;
        }
        try {
            Method data = event.getClass().getMethod("data");
            if (data.invoke(event) instanceof Map<?, ?> values) {
                plugin.tickets().loaderLock(registeredFor == null ? "MRchunks" : registeredFor.getName(), values);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Could not open a ticket for a chunk loader lock: " + e);
        }
    }
}
