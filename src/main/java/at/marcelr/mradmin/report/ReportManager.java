package at.marcelr.mradmin.report;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static at.marcelr.mradmin.Messages.tag;

/** Player reports: /report creates them, staff is notified and handles them in the reports menu. */
public final class ReportManager {

    public static final String PERM_REPORT = "mradmin.report";
    public static final String PERM_NOTIFY = "mradmin.report.notify";
    public static final String PERM_MANAGE = "mradmin.report.manage";

    /** One report; {@code closedBy} is null while it is open. */
    public record Report(int id, UUID reporter, String reporterName, UUID target, String targetName, String reason,
                         Instant time, String world, double x, double y, double z,
                         String closedBy, Instant closedAt) {

        public boolean open() {
            return closedBy == null;
        }

        public Location location() {
            var w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final MRAdmin plugin;
    private final File file;
    private final Map<Integer, Report> reports = new HashMap<>();
    private final Map<UUID, Instant> lastReport = new HashMap<>();
    private int nextId = 1;

    public ReportManager(MRAdmin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
    }

    private Settings.ReportSettings settings() {
        return plugin.settings().reports();
    }

    // ------------------------------------------------------------------ create

    /** /report &lt;target&gt; &lt;reason&gt; - checks, stores and notifies. */
    public void create(Player reporter, OfflinePlayer target, String reason) {
        var messages = plugin.messages();
        Settings.ReportSettings s = settings();
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            messages.send(reporter, "report-self");
            return;
        }
        if (reason.length() < s.minReasonLength()) {
            messages.send(reporter, "report-reason-short", tag("min", s.minReasonLength()));
            return;
        }
        if (reason.length() > s.maxReasonLength()) {
            reason = reason.substring(0, s.maxReasonLength());
        }
        Instant last = lastReport.get(reporter.getUniqueId());
        if (last != null && !reporter.hasPermission(PERM_MANAGE)
                && Instant.now().isBefore(last.plusMillis(s.cooldown()))) {
            messages.send(reporter, "report-cooldown", tag("time",
                    Texts.duration(last.plusMillis(s.cooldown()).toEpochMilli() - System.currentTimeMillis())));
            return;
        }
        long openByReporter = reports.values().stream().filter(r -> r.open() && r.reporter().equals(reporter.getUniqueId())).count();
        if (openByReporter >= s.maxOpenPerPlayer() && !reporter.hasPermission(PERM_MANAGE)) {
            messages.send(reporter, "report-too-many", tag("max", s.maxOpenPerPlayer()));
            return;
        }
        lastReport.put(reporter.getUniqueId(), Instant.now());

        Player online = target.getPlayer();
        Location where = online != null ? online.getLocation() : reporter.getLocation();
        String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Report report = new Report(nextId++, reporter.getUniqueId(), reporter.getName(), target.getUniqueId(), targetName,
                reason, Instant.now(), where.getWorld().getName(), where.getX(), where.getY(), where.getZ(), null, null);
        reports.put(report.id(), report);
        save();

        messages.send(reporter, "report-sent", tag("player", targetName));
        notifyStaff(report);
        plugin.getLogger().info("Report #" + report.id() + " by " + reporter.getName() + " about " + targetName + ": " + reason);
        plugin.discord().log("report", "id", report.id(), "reporter", reporter.getName(), "player", targetName,
                "reason", reason, "world", report.world(), "x", (int) report.x(), "y", (int) report.y(), "z", (int) report.z());
    }

    private void notifyStaff(Report report) {
        var messages = plugin.messages();
        Component teleport = messages.get("report-notify-teleport")
                .clickEvent(ClickEvent.runCommand("/mradmin reports tp " + report.id()))
                .hoverEvent(HoverEvent.showText(messages.get("report-notify-teleport-hover")));
        Component list = messages.get("report-notify-list")
                .clickEvent(ClickEvent.runCommand("/mradmin reports"))
                .hoverEvent(HoverEvent.showText(messages.get("report-notify-list-hover")));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(PERM_NOTIFY)) {
                messages.send(staff, "report-notify", tag("id", report.id()), tag("reporter", report.reporterName()),
                        tag("player", report.targetName()), tag("reason", report.reason()),
                        tag("teleport", teleport), tag("list", list));
            }
        }
    }

    /** Join notice for staff: how many reports are open. */
    public void notifyOnJoin(Player player) {
        int open = openCount();
        if (open > 0 && player.hasPermission(PERM_NOTIFY)) {
            plugin.messages().send(player, "report-join", tag("count", open),
                    tag("list", plugin.messages().get("report-notify-list").clickEvent(ClickEvent.runCommand("/mradmin reports"))));
        }
    }

    // ------------------------------------------------------------------ handle

    public void close(Player staff, Report report) {
        if (!report.open()) {
            return;
        }
        Report closed = new Report(report.id(), report.reporter(), report.reporterName(), report.target(), report.targetName(),
                report.reason(), report.time(), report.world(), report.x(), report.y(), report.z(), staff.getName(), Instant.now());
        reports.put(report.id(), closed);
        save();
        plugin.messages().send(staff, "report-closed", tag("id", report.id()));
        Player reporter = Bukkit.getPlayer(report.reporter());
        if (reporter != null && settings().tellReporter()) {
            plugin.messages().send(reporter, "report-handled", tag("player", report.targetName()));
        }
        plugin.discord().log("report-closed", "id", report.id(), "admin", staff.getName(), "player", report.targetName());
    }

    /** Teleports staff to the reported player (if online) or to where the report was made. */
    public void teleport(Player staff, Report report) {
        Player target = Bukkit.getPlayer(report.target());
        Location to = target != null ? target.getLocation() : report.location();
        if (to == null) {
            plugin.messages().send(staff, "report-no-location");
            return;
        }
        staff.teleport(to);
        plugin.messages().send(staff, target != null ? "report-teleported-player" : "report-teleported-place",
                tag("id", report.id()), tag("player", report.targetName()));
    }

    // ------------------------------------------------------------------ access

    public Report get(int id) {
        return reports.get(id);
    }

    /** Open reports, newest first; {@code about} null = all players. */
    public List<Report> open(UUID about) {
        return reports.values().stream()
                .filter(Report::open)
                .filter(r -> about == null || r.target().equals(about))
                .sorted(Comparator.comparing(Report::time).reversed())
                .toList();
    }

    public int openCount() {
        return (int) reports.values().stream().filter(Report::open).count();
    }

    public int openAbout(UUID target) {
        return (int) reports.values().stream().filter(r -> r.open() && r.target().equals(target)).count();
    }

    // ------------------------------------------------------------- persistence

    public void load() {
        reports.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, y.getInt("next-id", 1));
        ConfigurationSection section = y.getConfigurationSection("reports");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            try {
                String closedAt = r.getString("closed-at", "");
                Report report = new Report(Integer.parseInt(key), UUID.fromString(r.getString("reporter")),
                        r.getString("reporter-name", "?"), UUID.fromString(r.getString("target")),
                        r.getString("target-name", "?"), r.getString("reason", ""), Instant.parse(r.getString("time")),
                        r.getString("world", ""), r.getDouble("x"), r.getDouble("y"), r.getDouble("z"),
                        r.getString("closed-by"), closedAt.isEmpty() ? null : Instant.parse(closedAt));
                reports.put(report.id(), report);
                nextId = Math.max(nextId, report.id() + 1);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("reports.yml: report " + key + " is broken, skipped (" + e.getMessage() + ").");
            }
        }
        cleanup();
    }

    /** Closed reports older than keep-closed are removed. */
    private void cleanup() {
        long keep = settings().keepClosed();
        Instant limit = Instant.now().minusMillis(keep);
        boolean removed = reports.values().removeIf(r -> !r.open() && r.closedAt() != null && r.closedAt().isBefore(limit));
        if (removed) {
            save();
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - player reports (managed by the plugin)."));
        y.set("next-id", nextId);
        for (Report r : new ArrayList<>(reports.values())) {
            String p = "reports." + r.id() + ".";
            y.set(p + "reporter", r.reporter().toString());
            y.set(p + "reporter-name", r.reporterName());
            y.set(p + "target", r.target().toString());
            y.set(p + "target-name", r.targetName());
            y.set(p + "reason", r.reason());
            y.set(p + "time", r.time().toString());
            y.set(p + "world", r.world());
            y.set(p + "x", r.x());
            y.set(p + "y", r.y());
            y.set(p + "z", r.z());
            if (!r.open()) {
                y.set(p + "closed-by", r.closedBy());
                y.set(p + "closed-at", r.closedAt() == null ? "" : r.closedAt().toString());
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save reports.yml: " + e.getMessage());
        }
    }
}
