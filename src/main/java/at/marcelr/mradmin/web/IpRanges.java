package at.marcelr.mradmin.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Addresses and ranges for web.trusted-proxies: a plain address ("127.0.0.1", "::1") or a range in CIDR notation
 * ("172.16.0.0/12", "fd00::/8") - e.g. for a proxy in Docker whose address changes. Only literal addresses are
 * compared, never host names (no DNS lookups).
 */
public final class IpRanges {

    private IpRanges() {
    }

    /** Whether {@code ip} is one of the entries (address or range). */
    public static boolean matches(Collection<String> entries, String ip) {
        if (ip == null || ip.isBlank() || entries.isEmpty()) {
            return false;
        }
        if (entries.contains(ip)) {
            return true;
        }
        byte[] address = parse(ip);
        if (address == null) {
            return false;
        }
        for (String entry : entries) {
            if (inRange(entry, address)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the entry is a valid address or range (for checking the config). */
    public static boolean valid(String entry) {
        int slash = entry.indexOf('/');
        byte[] base = parse(slash < 0 ? entry : entry.substring(0, slash));
        if (base == null) {
            return false;
        }
        if (slash < 0) {
            return true;
        }
        try {
            int bits = Integer.parseInt(entry.substring(slash + 1));
            return bits >= 0 && bits <= base.length * 8;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean inRange(String entry, byte[] address) {
        int slash = entry.indexOf('/');
        if (slash < 0) {
            byte[] single = parse(entry);
            return single != null && java.util.Arrays.equals(single, address);
        }
        byte[] base = parse(entry.substring(0, slash));
        int bits;
        try {
            bits = Integer.parseInt(entry.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (base == null || base.length != address.length || bits < 0 || bits > base.length * 8) {
            return false;
        }
        for (int i = 0; i < bits; i++) {
            int mask = 0x80 >> (i % 8);
            if ((base[i / 8] & mask) != (address[i / 8] & mask)) {
                return false;
            }
        }
        return true;
    }

    /** The bytes of a literal IPv4 or IPv6 address (IPv4-mapped IPv6 as IPv4), null for anything else. */
    static byte[] parse(String text) {
        String s = text.trim();
        if (s.isEmpty() || !s.matches("[0-9a-fA-F:.%]+")) {
            return null;
        }
        if (s.indexOf(':') < 0 && !s.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        try {
            // Only literals reach this point - no DNS lookup.
            return InetAddress.getByName(s).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
