package at.marcelr.mradmin.util;

/** Symmetric placement of buttons in a 9-slot menu row. */
public final class Layout {

    private Layout() {
    }

    /**
     * Slots for {@code count} buttons in {@code row}, symmetric around the middle: up to 5 with a gap between them
     * (1: center, 2: columns 3 and 5, 3: 2, 4 and 6 ...), more without gaps (6 and 8 leave the center free).
     */
    public static int[] centered(int row, int count) {
        int[] columns = switch (Math.clamp(count, 0, 9)) {
            case 0 -> new int[0];
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            case 7 -> new int[]{1, 2, 3, 4, 5, 6, 7};
            case 8 -> new int[]{0, 1, 2, 3, 5, 6, 7, 8};
            case 9 -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            default -> {
                int[] c = new int[count];
                for (int i = 0; i < count; i++) {
                    c[i] = 4 - (count - 1) + 2 * i;
                }
                yield c;
            }
        };
        int[] slots = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            slots[i] = row * 9 + columns[i];
        }
        return slots;
    }
}
