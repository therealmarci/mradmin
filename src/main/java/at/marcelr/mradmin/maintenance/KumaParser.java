package at.marcelr.mradmin.maintenance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the maintenance objects Uptime Kuma sends ({@code maintenanceList} event / {@code getMaintenance}) into
 * {@link Window}s. Pure logic so it can be unit-tested.
 * <p>
 * Relevant fields: {@code id}, {@code title}, {@code active}, {@code status} ("scheduled", "under-maintenance",
 * "inactive", "ended", "unknown"), {@code timeslotList} ([{startDate, endDate}], either ISO instants like
 * "2026-10-18T18:00:00.000Z" or wall-clock times like "2026-10-18 20:00:00" in the maintenance's timezone),
 * {@code timezone} ("Europe/Vienna") and {@code timezoneOffset} ("+02:00").
 */
public final class KumaParser {

    private KumaParser() {
    }

    /** @return the window of this maintenance that is running or comes next, or null if there is none */
    public static Window parse(JsonObject m, Instant now, ZoneId serverZone) {
        if (m == null || !m.has("id")) {
            return null;
        }
        int id = m.get("id").getAsInt();
        String title = string(m, "title");
        String status = string(m, "status").toLowerCase(Locale.ROOT);
        if (m.has("active") && !m.get("active").isJsonNull() && !truthy(m.get("active"))) {
            return null;
        }
        boolean running = status.equals("under-maintenance");
        if (!running && !status.equals("scheduled")) {
            return null;
        }
        ZoneId zone = zone(m, serverZone);
        List<Instant[]> slots = new ArrayList<>();
        JsonArray list = m.has("timeslotList") && m.get("timeslotList").isJsonArray()
                ? m.getAsJsonArray("timeslotList") : new JsonArray();
        for (JsonElement element : list) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject slot = element.getAsJsonObject();
            Instant start = time(string(slot, "startDate"), zone);
            Instant end = time(string(slot, "endDate"), zone);
            if (start != null && (end == null || end.isAfter(start))) {
                slots.add(new Instant[]{start, end});
            }
        }

        if (running) {
            // The slot we are in decides the end; a manual maintenance has no slot and runs until it is stopped.
            for (Instant[] slot : slots) {
                if (!now.isBefore(slot[0]) && (slot[1] == null || now.isBefore(slot[1]))) {
                    return new Window(Window.KUMA, id, title, slot[0], slot[1], true);
                }
            }
            Instant[] closest = slots.stream()
                    .filter(s -> !s[0].isAfter(now))
                    .max((a, b) -> a[0].compareTo(b[0]))
                    .orElse(null);
            if (closest != null && closest[1] != null && closest[1].isAfter(now)) {
                return new Window(Window.KUMA, id, title, closest[0], closest[1], true);
            }
            return new Window(Window.KUMA, id, title, null, null, true);
        }
        // Scheduled: the next slot. A slot that has already begun counts too (Kuma may report it a moment late).
        Instant[] next = null;
        for (Instant[] slot : slots) {
            boolean notOver = slot[1] == null || now.isBefore(slot[1]);
            if (notOver && (next == null || slot[0].isBefore(next[0]))) {
                next = slot;
            }
        }
        return next == null ? null : new Window(Window.KUMA, id, title, next[0], next[1], false);
    }

    /** The maintenance's own timezone; "SAME_AS_SERVER"/"UTC" names and offsets are understood. */
    public static ZoneId zone(JsonObject m, ZoneId fallback) {
        String name = string(m, "timezone");
        if (!name.isEmpty() && !name.equalsIgnoreCase("SAME_AS_SERVER") && !name.equalsIgnoreCase("auto")) {
            try {
                return ZoneId.of(name);
            } catch (DateTimeException ignored) {
                // try the offset below
            }
        }
        String offset = string(m, "timezoneOffset");
        if (!offset.isEmpty()) {
            try {
                return ZoneOffset.of(offset);
            } catch (DateTimeException ignored) {
                // fall back
            }
        }
        return fallback;
    }

    /** ISO instant / offset date-time, or a wall-clock time ("2026-10-18 20:00[:00]") in {@code zone}. */
    public static Instant time(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException ignored) {
            // no offset in the text
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException ignored) {
            // wall-clock time
        }
        try {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean truthy(JsonElement value) {
        if (!value.isJsonPrimitive()) {
            return true;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsInt() != 0;
        }
        return !primitive.getAsString().equalsIgnoreCase("false") && !primitive.getAsString().equals("0");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
