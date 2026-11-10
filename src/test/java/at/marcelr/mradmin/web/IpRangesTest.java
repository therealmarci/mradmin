package at.marcelr.mradmin.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRangesTest {

    @Test
    void plainAddresses() {
        assertTrue(IpRanges.matches(List.of("127.0.0.1"), "127.0.0.1"));
        assertFalse(IpRanges.matches(List.of("127.0.0.1"), "127.0.0.2"));
        assertTrue(IpRanges.matches(List.of("::1"), "0:0:0:0:0:0:0:1"));
    }

    @Test
    void ranges() {
        List<String> docker = List.of("172.16.0.0/12");
        assertTrue(IpRanges.matches(docker, "172.18.0.5"));
        assertTrue(IpRanges.matches(docker, "172.31.255.255"));
        assertFalse(IpRanges.matches(docker, "172.32.0.1"));
        assertFalse(IpRanges.matches(docker, "10.0.152.101"));
        assertTrue(IpRanges.matches(List.of("10.0.152.0/24"), "10.0.152.101"));
        assertTrue(IpRanges.matches(List.of("0.0.0.0/0"), "8.8.8.8"));
        assertTrue(IpRanges.matches(List.of("fd00::/8"), "fd12:3456::1"));
        assertFalse(IpRanges.matches(List.of("fd00::/8"), "10.0.0.1"));
    }

    @Test
    void neverHostNamesOrJunk() {
        assertFalse(IpRanges.matches(List.of("localhost"), "127.0.0.1"));
        assertFalse(IpRanges.matches(List.of("10.0.0.0/33"), "10.0.0.1"));
        assertFalse(IpRanges.matches(List.of("10.0.0.0/8"), "evil.example"));
        assertFalse(IpRanges.valid("example.com"));
        assertTrue(IpRanges.valid("172.16.0.0/12"));
        assertFalse(IpRanges.valid("172.16.0.0/40"));
    }
}
