package at.marcelr.mradmin.map;

import org.bukkit.World;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;

import java.nio.file.Path;

/**
 * The little MRadmin needs from squaremap's API: where the rendered pictures are and which worlds it renders. Only
 * loaded by {@link MapService} when squaremap is installed.
 */
final class SquaremapBridge {

    private final Squaremap api;

    SquaremapBridge() {
        this.api = SquaremapProvider.get();
    }

    /** squaremap's rendered map pictures: tiles/&lt;world&gt;/&lt;zoom&gt;/&lt;x&gt;_&lt;z&gt;.png, plus settings.json per world. */
    Path tilesDir() {
        return api.webDir().resolve("tiles");
    }

    /** Whether squaremap renders the world. */
    boolean onMap(World world) {
        return api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).isPresent();
    }
}
