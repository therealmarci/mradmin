package at.marcelr.mradmin.versions;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerSettingsTimeTest {

    @Test
    void readsTimesAlsoWhenYamlMadeThemNumbers() {
        assertEquals(LocalTime.of(5, 0), ServerSettings.time("05:00"));
        assertEquals(LocalTime.of(5, 30), ServerSettings.time("5:30"));
        assertEquals(LocalTime.of(5, 30), ServerSettings.time(330));
        assertEquals(LocalTime.of(23, 59), ServerSettings.time(1439));
        assertNull(ServerSettings.time(1440));
        assertNull(ServerSettings.time("24:00"));
        assertNull(ServerSettings.time("abends"));
    }
}
