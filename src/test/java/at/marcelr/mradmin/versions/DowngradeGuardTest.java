package at.marcelr.mradmin.versions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DowngradeGuardTest {

    @TempDir
    Path dir;

    private File jar(int config, int lang) throws Exception {
        File jar = dir.resolve("new-" + config + "-" + lang + ".jar").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            put(zip, "config.yml", "# comment\nconfig-version: " + config + "\nfoo: 1\n");
            put(zip, "lang/de.yml", "lang-version: " + lang + "\n");
            put(zip, "lang/en.yml", "lang-version: " + lang + "\n");
        }
        return jar;
    }

    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private File server(int config, int lang) throws Exception {
        File folder = dir.resolve("data-" + config + "-" + lang).toFile();
        Files.createDirectories(folder.toPath().resolve("lang"));
        // the BOM MRadmin writes must not hide the number
        Files.writeString(folder.toPath().resolve("config.yml"), "﻿config-version: " + config + "\n");
        Files.writeString(folder.toPath().resolve("lang/de.yml"), "lang-version: '" + lang + "'\n");
        return folder;
    }

    @Test
    void sameOrNewerMayBeInstalled() throws Exception {
        assertNull(DowngradeGuard.migrations(jar(50, 20), server(50, 20)));
        assertNull(DowngradeGuard.migrations(jar(51, 21), server(50, 20)));
    }

    @Test
    void olderMigrationsAreBlocked() throws Exception {
        DowngradeGuard.Block config = DowngradeGuard.migrations(jar(49, 20), server(50, 20));
        assertEquals("config", config.reason());
        assertEquals("50", config.have());
        assertEquals("49", config.offered());
        assertEquals("lang", DowngradeGuard.migrations(jar(50, 19), server(50, 20)).reason());
    }

    @Test
    void freshServerOrMissingNumbersDoNotBlock() throws Exception {
        assertNull(DowngradeGuard.migrations(jar(10, 1), dir.resolve("nothing-here").toFile()));
    }

    @Test
    void codeOrder() {
        assertNull(DowngradeGuard.code("ahead", "aaaaaaaa", "bbbbbbbb"));
        assertNull(DowngradeGuard.code("identical", "aaaaaaaa", "aaaaaaaa"));
        assertNull(DowngradeGuard.code(null, "aaaaaaaa", "bbbbbbbb"));
        assertEquals("code", DowngradeGuard.code("behind", "aaaaaaaa", "bbbbbbbb").reason());
        assertEquals("bbbbbbb (diverged)", DowngradeGuard.code("diverged", "aaaaaaaa", "bbbbbbbb").offered());
    }

    @Test
    void numbers() {
        assertEquals(51, DowngradeGuard.number("a: 1\nconfig-version: \"51\"\n", "config-version"));
        assertNull(DowngradeGuard.number("# config-version: 51\n", "config-version"));
        assertNull(DowngradeGuard.number(null, "config-version"));
    }
}
