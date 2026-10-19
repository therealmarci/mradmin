package at.marcelr.mradmin.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandFilterTest {

    private static final List<String> BLOCKED = List.of("op", "stop", "lp", "mradmin web", "whitelist off");

    @Test
    void blocksPlainCommands() {
        assertEquals("op", CommandFilter.blocked("op Steve", BLOCKED));
        assertEquals("stop", CommandFilter.blocked("stop", BLOCKED));
        assertEquals("op", CommandFilter.blocked("/OP  Steve", BLOCKED));
    }

    @Test
    void blocksNamespaces() {
        assertEquals("op", CommandFilter.blocked("minecraft:op Steve", BLOCKED));
        assertEquals("lp", CommandFilter.blocked("luckperms:lp user Steve permission set *", BLOCKED));
    }

    @Test
    void blocksInsideExecute() {
        assertEquals("op", CommandFilter.blocked("execute as @a run op @s", BLOCKED));
        assertEquals("op", CommandFilter.blocked("execute if entity @a run execute run minecraft:op Steve", BLOCKED));
    }

    @Test
    void blocksSeveralWords() {
        assertEquals("mradmin web", CommandFilter.blocked("mradmin web invite Steve", BLOCKED));
        assertEquals("whitelist off", CommandFilter.blocked("whitelist   OFF", BLOCKED));
        assertNull(CommandFilter.blocked("mradmin reload", BLOCKED));
        assertNull(CommandFilter.blocked("whitelist add Steve", BLOCKED));
    }

    @Test
    void allowsSimilarCommands() {
        assertNull(CommandFilter.blocked("opsay hello", BLOCKED));
        assertNull(CommandFilter.blocked("stopwatch", BLOCKED));
        assertNull(CommandFilter.blocked("say we stop now", BLOCKED));
        assertNull(CommandFilter.blocked("list", BLOCKED));
    }
}
