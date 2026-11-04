package at.marcelr.mradmin.versions;

import java.util.List;

/**
 * All MRtechnology plugins MRadmin knows - also those that are not installed, so the page Plugins can offer them.
 * Installed plugins that are missing here (newer ones) still show up through their {@code build-info.properties}.
 */
public final class Catalog {

    private Catalog() {
    }

    /**
     * @param repository GitHub repository with the official releases
     * @param slug       short name on the website (/download/&lt;slug&gt;/)
     */
    public record Known(String name, String repository, String slug) {
    }

    public static final List<Known> PLUGINS = List.of(
            new Known("MRadmin", "therealmarci/mradmin", "mradmin"),
            new Known("MRchunks", "therealmarci/mrchunks", "mrchunks"),
            new Known("MRpvp", "therealmarci/mrpvp", "mrpvp"),
            new Known("MRsleep", "therealmarci/mrsleep", "mrsleep"),
            new Known("MRtoolbar", "therealmarci/mrtoolbar", "mrtoolbar"),
            new Known("MRwaypoints", "therealmarci/mrwaypoints", "mrwaypoints"));
}
