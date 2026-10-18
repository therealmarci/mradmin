package at.marcelr.mradmin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class YamlErrorsTest {

    @Test
    void describesAMissingQuote() {
        // What SnakeYAML reports for  url: "https://status.example.at/   (closing quote missing in line 84)
        String message = """
                while parsing a block mapping
                 in 'string', line 82, column 3:
                      enabled: false
                      ^
                expected <block end>, but found '<scalar>'
                 in 'string', line 86, column 14:
                      username: ""
                                 ^
                """;
        assertArrayEquals(new String[]{"expected <block end>, but found '<scalar>'", "86"},
                YamlErrors.describe(message));
    }

    @Test
    void withoutPosition() {
        assertArrayEquals(new String[]{"broken", "?"}, YamlErrors.describe("broken"));
    }
}
