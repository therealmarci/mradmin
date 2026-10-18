package at.marcelr.mradmin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutTest {

    @Test
    void withGaps() {
        assertArrayEquals(new int[]{}, Layout.centered(0, 0));
        assertArrayEquals(new int[]{4}, Layout.centered(0, 1));
        assertArrayEquals(new int[]{3, 5}, Layout.centered(0, 2));
        assertArrayEquals(new int[]{2, 4, 6}, Layout.centered(0, 3));
        assertArrayEquals(new int[]{1, 3, 5, 7}, Layout.centered(0, 4));
        assertArrayEquals(new int[]{0, 2, 4, 6, 8}, Layout.centered(0, 5));
    }

    @Test
    void withoutGaps() {
        assertArrayEquals(new int[]{1, 2, 3, 5, 6, 7}, Layout.centered(0, 6));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7}, Layout.centered(0, 7));
        assertArrayEquals(new int[]{0, 1, 2, 3, 5, 6, 7, 8}, Layout.centered(0, 8));
    }

    @Test
    void symmetricInEveryRow() {
        for (int count = 0; count <= 9; count++) {
            int[] slots = Layout.centered(2, count);
            assertEquals(count, slots.length);
            for (int i = 0; i < slots.length; i++) {
                // mirror of column c is 8 - c
                assertEquals(8 - (slots[i] - 18), slots[slots.length - 1 - i] - 18, "count " + count);
            }
        }
    }
}
