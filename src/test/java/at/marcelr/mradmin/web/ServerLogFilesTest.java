package at.marcelr.mradmin.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLogFilesTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @TempDir
    Path dir;

    private void gz(String name, String text) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dir.resolve(name)))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void files() throws IOException {
        gz("2026-11-03-1.log.gz", "[10:00:00] [Server thread/INFO]: yesterday one\n");
        gz("2026-11-04-1.log.gz", "[08:00:00] [Server thread/INFO]: first start\n[08:00:00] [Server thread/INFO]: same second\n");
        gz("2026-11-04-2.log.gz", "[09:00:00] [Server thread/WARN]: error\n\tat some.Trace\n");
        Files.writeString(dir.resolve("latest.log"), "[10:00:00] [Server thread/INFO]: player from 192.168.1.20 https://x.y/z\n");
        gz("2026-10-01-1.log.gz", "[10:00:00] [Server thread/INFO]: far too old\n");
    }

    private ServerLogFiles.Result find(long since, long before, String q, int limit) throws IOException {
        return new ServerLogFiles(dir, ZONE).find(TODAY, since, before, q, limit, true, true);
    }

    private static List<String> texts(ServerLogFiles.Result r) {
        return r.lines().stream().map(ServerLogFiles.Line::text).toList();
    }

    @Test
    void newestFirstOverDaysAndFiles() throws IOException {
        files();
        List<String> t = texts(find(0, 0, "", 100));
        assertEquals(List.of(
                "[10:00:00] [Server thread/INFO]: player from [IP] [Link]",
                "\tat some.Trace",
                "[09:00:00] [Server thread/WARN]: error",
                "[08:00:00] [Server thread/INFO]: same second",
                "[08:00:00] [Server thread/INFO]: first start",
                "[10:00:00] [Server thread/INFO]: yesterday one"), t);
    }

    @Test
    void searchSinceAndLoadingOlder() throws IOException {
        files();
        long today = TODAY.atStartOfDay(ZONE).toInstant().toEpochMilli();
        assertEquals(List.of("[09:00:00] [Server thread/WARN]: error"), texts(find(today, 0, "ERROR", 100)));

        ServerLogFiles.Result page = find(0, 0, "", 3);
        assertTrue(page.more());
        ServerLogFiles.Result older = find(0, page.lines().get(2).key(), "", 3);
        assertEquals(List.of("[08:00:00] [Server thread/INFO]: same second", "[08:00:00] [Server thread/INFO]: first start",
                "[10:00:00] [Server thread/INFO]: yesterday one"), texts(older));
        assertFalse(older.more());
        // the stack trace line keeps the time of the line before it
        assertEquals(page.lines().get(2).time(), page.lines().get(1).time());
    }
}
