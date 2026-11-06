package at.marcelr.mradmin.starter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the Java options that can be set in MRadmin's config.yml (and so from the web interface). Only tuning
 * options get through: a web admin must not be able to run programs on the machine - Java can do that with options
 * like {@code -XX:OnOutOfMemoryError="..."}, {@code -javaagent:} or a log4j configuration. Options the owner sets in
 * {@code mrserver.properties} on the machine itself are not checked.
 */
public final class JavaFlags {

    private static final Pattern XX = Pattern.compile("^-XX:([+-]?)([A-Za-z][A-Za-z0-9]*)(=[A-Za-z0-9._:,/%+-]*)?$");
    private static final Pattern SIZE = Pattern.compile("^-X(ss|mn)[0-9]+[kKmMgG]?$");
    private static final Pattern PROPERTY = Pattern.compile("^-D([A-Za-z0-9][A-Za-z0-9._-]*)(=[^\\s\"'`$;&|<>]*)?$");
    /** -XX options that run commands or write/read files at freely chosen places. */
    private static final Set<String> BLOCKED_XX = Set.of("onerror", "onoutofmemoryerror", "flags", "vmoptionsfile",
            "errorfile", "heapdumppath", "logfile", "craccheckpointto","cracrestorefrom",
            "sharedarchivefile", "archiveclassesatexit", "sharedclasslistfile", "startflightrecording",
            "flightrecorderoptions", "jvmciliblpath", "abortvmonexception");
    private static final List<String> BLOCKED_PROPERTY_PREFIXES = List.of("log4j", "java.", "jdk.", "sun.", "javax.",
            "jna.", "org.slf4j", "com.sun.");
    private static final Set<String> ALLOWED_PROPERTIES = Set.of("java.net.preferipv4stack", "java.net.preferipv6addresses",
            "java.awt.headless");
    private static final Set<String> ALLOWED_PLAIN = Set.of("--enable-native-access=ALL-UNNAMED",
            "--add-modules=jdk.incubator.vector");

    private JavaFlags() {
    }

    /** Splits the option text at spaces. */
    public static List<String> split(String text) {
        List<String> list = new ArrayList<>();
        if (text != null) {
            for (String part : text.trim().split("\\s+")) {
                if (!part.isEmpty()) {
                    list.add(part);
                }
            }
        }
        return list;
    }

    /** @return null if the option may be used, otherwise why not (English, for console and web) */
    public static String problem(String flag) {
        if (ALLOWED_PLAIN.contains(flag)) {
            return null;
        }
        var xx = XX.matcher(flag);
        if (xx.matches()) {
            return BLOCKED_XX.contains(xx.group(2).toLowerCase(Locale.ROOT)) ? "not allowed (runs or writes files)" : null;
        }
        if (SIZE.matcher(flag).matches()) {
            return null;
        }
        var p = PROPERTY.matcher(flag);
        if (p.matches()) {
            String name = p.group(1).toLowerCase(Locale.ROOT);
            if (ALLOWED_PROPERTIES.contains(name)) {
                return null;
            }
            return BLOCKED_PROPERTY_PREFIXES.stream().anyMatch(name::startsWith) ? "system property not allowed" : null;
        }
        if (flag.startsWith("-Xms") || flag.startsWith("-Xmx")) {
            return "set the memory with memory-min / memory-max";
        }
        return "unknown or not allowed";
    }

    /** Only the allowed options. */
    public static List<String> allowed(List<String> flags) {
        return flags.stream().filter(f -> problem(f) == null).toList();
    }

    /** Memory like 1G, 4096M - null if invalid. */
    public static String memory(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        return v.matches("[1-9][0-9]{0,6}[KMG]") ? v : null;
    }
}
