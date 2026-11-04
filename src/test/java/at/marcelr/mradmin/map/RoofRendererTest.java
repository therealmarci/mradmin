package at.marcelr.mradmin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoofRendererTest {

    @TempDir
    Path dir;

    private Path region(String path) throws IOException {
        Path folder = dir.resolve(path);
        Files.createDirectories(folder);
        Files.write(folder.resolve("r.0.0.mca"), new byte[8192]);
        return folder;
    }

    @Test
    void newLayoutRegionBesideTheWorld() throws IOException {
        Path world = dir.resolve("world/dimensions/minecraft/the_nether");
        Path expected = region("world/dimensions/minecraft/the_nether/region");
        assertEquals(expected, RoofRenderer.regionFolder(world, dir.resolve("world_nether")));
    }

    @Test
    void oldBukkitLayoutWithDim1() throws IOException {
        Path world = dir.resolve("world_nether");
        Path expected = region("world_nether/DIM-1/region");
        assertEquals(expected, RoofRenderer.regionFolder(world, world));
    }

    @Test
    void somewhereBelowPrefersTheNether() throws IOException {
        Path world = dir.resolve("world");
        region("world/dimensions/minecraft/overworld/region");
        Path expected = region("world/dimensions/minecraft/the_nether/region");
        assertEquals(expected, RoofRenderer.regionFolder(world, dir.resolve("missing")));
    }

    @Test
    void emptyRegionFoldersDoNotCount() throws IOException {
        Path world = dir.resolve("world_nether");
        Files.createDirectories(world.resolve("region"));
        assertNull(RoofRenderer.regionFolder(world, world));
    }
}
