package at.marcelr.mradmin.api;

import java.io.File;
import java.util.function.Consumer;

/**
 * A config file the web interface may edit (since API 12, see {@link WebModule#webConfig()}). MRadmin draws a form from
 * {@code form}, checks every value against it, writes the file (comments and order stay, UTF-8 with BOM) and then calls
 * {@code reload} on the main thread. Editing the file by hand keeps working as before.
 *
 * @param file     the config file, usually {@code plugins/<Plugin>/config.yml}
 * @param form     text of the form description (YAML, usually the bundled {@code web-config.yml}): sections, fields,
 *                 types, limits and the names and help texts in German and English - see MRadmin's docs/MODULE-API.md
 * @param defaults text of the bundled config.yml: default values ("reset to default") and the rows of tables
 * @param reload   loads the saved file like the plugin's reload command; gets the name of who saved it (e.g.
 *                 "Marcel (web)") for the plugin's own logs
 */
public record WebConfig(File file, String form, String defaults, Consumer<String> reload) {
}
