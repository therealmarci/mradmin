package at.marcelr.mradmin.web.self;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * Reads a player's XP level ("XpLevel") from their saved player data ({@code <uuid>.dat}, gzipped NBT) - also of
 * players who are offline. Only the top level of the file is looked at; everything else is skipped without being kept.
 */
final class PlayerDataLevel {

    private static final int END = 0;
    private static final int COMPOUND = 10;
    private static final int INT = 3;

    private PlayerDataLevel() {
    }

    /** The XP level, 0 if the file is missing or unreadable. */
    static double read(File file) {
        if (!file.isFile()) {
            return 0;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            if (in.readUnsignedByte() != COMPOUND) {
                return 0;
            }
            in.readUTF(); // name of the root
            while (true) {
                int type = in.readUnsignedByte();
                if (type == END) {
                    return 0;
                }
                String name = in.readUTF();
                if (type == INT && name.equals("XpLevel")) {
                    return in.readInt();
                }
                skip(in, type);
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** Skips the payload of one tag of {@code type}. */
    private static void skip(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {
                int element = in.readUnsignedByte();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    skip(in, element);
                }
            }
            case 10 -> {
                while (true) {
                    int inner = in.readUnsignedByte();
                    if (inner == END) {
                        return;
                    }
                    in.skipNBytes(in.readUnsignedShort());
                    skip(in, inner);
                }
            }
            case 11 -> in.skipNBytes(4L * in.readInt());
            case 12 -> in.skipNBytes(8L * in.readInt());
            default -> throw new IOException("Unknown NBT tag " + type);
        }
    }
}
