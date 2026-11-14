package at.marcelr.mradmin.versions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The version comparison for plugins not from MRtechnology (installed version against the newest GitHub release). */
class ForeignPluginsTest {

    @Test
    void sameVersionIsCurrent() {
        assertEquals("current", ForeignPlugins.compare("5.5.17", "v5.5.17"));
        assertEquals("current", ForeignPlugins.compare("1.1.4", "1.1.4"));
        assertEquals("current", ForeignPlugins.compare("2.0", "v2.0.0"));
    }

    @Test
    void higherReleaseIsNewer() {
        assertEquals("newer", ForeignPlugins.compare("5.5.17", "v5.5.18"));
        assertEquals("newer", ForeignPlugins.compare("1.1.4", "1.2"));
        assertEquals("newer", ForeignPlugins.compare("4.9.9", "5.0.0"));
    }

    @Test
    void installedAheadIsCurrent() {
        assertEquals("current", ForeignPlugins.compare("2.1.0-SNAPSHOT", "v2.0.5"));
    }

    @Test
    void suffixesAndPrefixesAreIgnored() {
        assertEquals("current", ForeignPlugins.compare("1.4.3+mc26.3", "squaremap-1.4.3"));
        assertEquals("newer", ForeignPlugins.compare("5.12.2-SNAPSHOT", "5.13.0"));
    }

    @Test
    void withoutNumbersUnknown() {
        assertEquals("unknown", ForeignPlugins.compare("dev", "v1.0"));
        assertEquals("unknown", ForeignPlugins.compare("1.0", "latest"));
    }
}
