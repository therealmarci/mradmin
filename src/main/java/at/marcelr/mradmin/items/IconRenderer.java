package at.marcelr.mradmin.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws item icons the way the inventory shows them, from the game's own resources (item definitions, models and
 * textures of the client JAR - see {@link ItemAssets}). Flat items are their texture layers; blocks and other models
 * with elements are drawn in 3D with the model's "gui" view (stairs, fences ...). Items the game draws with code
 * (chests, shulker boxes, heads, banners, shield) are built from their entity textures here.
 * <p>
 * Without Bukkit, so it can be tested on its own. Not thread-safe - one renderer per run.
 */
final class IconRenderer {

    /** Edge of an icon in pixels (3 per texture pixel of a flat item). */
    static final int SIZE = 48;

    private static final String ASSETS = "assets/minecraft/";
    /** Shade of a face by its direction, as the game bakes it into block models. */
    private static final Map<String, Float> SHADE = Map.of("up", 1f, "down", 0.5f, "north", 0.8f, "south", 0.8f,
            "east", 0.6f, "west", 0.6f);
    /** Wool colours of the dyes (banners). */
    private static final Map<String, Integer> DYES = Map.ofEntries(Map.entry("white", 0xF9FFFE),
            Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD), Map.entry("light_blue", 0x3AB3DA),
            Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F), Map.entry("pink", 0xF38BAA),
            Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97), Map.entry("cyan", 0x169C9C),
            Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA), Map.entry("brown", 0x835432),
            Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26), Map.entry("black", 0x1D1D21));

    private final Map<String, byte[]> files;
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, BufferedImage> textures = new HashMap<>();
    private final Map<String, BufferedImage> painted = new HashMap<>();

    /** @param files the client JAR's entries ("assets/minecraft/...") - at least items, models and textures */
    IconRenderer(Map<String, byte[]> files) {
        this.files = files;
    }

    /** The item ids (e.g. "diamond_sword") that have a definition. */
    List<String> items() {
        List<String> ids = new ArrayList<>();
        String prefix = ASSETS + "items/";
        for (String path : files.keySet()) {
            if (path.startsWith(prefix) && path.endsWith(".json")) {
                ids.add(path.substring(prefix.length(), path.length() - 5));
            }
        }
        ids.sort(null);
        return ids;
    }

    /** The icon of an item, or null if the game has no definition for it. */
    BufferedImage render(String item) {
        JsonObject definition = json(ASSETS + "items/" + item + ".json");
        if (definition == null || !definition.has("model")) {
            return null;
        }
        List<Part> parts = new ArrayList<>();
        collect(definition.getAsJsonObject("model"), parts, new double[3]);
        if (parts.isEmpty()) {
            return null;
        }
        BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        try {
            List<Face> faces = new ArrayList<>();
            View view = null;
            for (Part part : parts) {
                Model model = model(part.model());
                if (model == null) {
                    continue;
                }
                if (view == null) {
                    view = model.view();
                }
                if (part.special() != null && special(part.special(), model, g, faces)) {
                    continue;
                }
                if (model.generated()) {
                    flat(model, part.tints(), g);
                } else if (model.elements() != null) {
                    elements(model, part.tints(), part.offset(), faces);
                } else {
                    // A model without anything to draw (e.g. made by code): its particle texture, flat.
                    String particle = model.texture("particle");
                    BufferedImage texture = particle == null ? null : texture(particle);
                    if (texture != null) {
                        g.drawImage(texture, 0, 0, SIZE, SIZE, null);
                    }
                }
            }
            if (!faces.isEmpty()) {
                draw(faces, view == null ? View.BLOCK : view, g);
            }
        } finally {
            g.dispose();
        }
        return icon;
    }

    // ------------------------------------------------------------------ item definitions

    /** A model to draw, with the tints of its definition, the "special" part (chest ...) and an offset (beds). */
    private record Part(String model, List<Integer> tints, JsonObject special, double[] offset) {
    }

    /** Walks an item definition to the models the inventory shows (no conditions, the "gui" case of a select). */
    private void collect(JsonObject node, List<Part> parts, double[] offset) {
        String type = id(string(node, "type"));
        switch (type) {
            case "model" -> parts.add(new Part(string(node, "model"), tints(node), null, offset));
            case "special" -> parts.add(new Part(string(node, "base"), List.of(), node.getAsJsonObject("model"), offset));
            case "composite" -> {
                for (JsonElement child : node.getAsJsonArray("models")) {
                    JsonObject c = child.getAsJsonObject();
                    double[] childOffset = offset.clone();
                    JsonObject transformation = c.getAsJsonObject("transformation");
                    if (transformation != null && transformation.has("translation")) {
                        JsonArray t = transformation.getAsJsonArray("translation");
                        for (int i = 0; i < 3; i++) {
                            childOffset[i] += t.get(i).getAsDouble() * 16;
                        }
                    }
                    collect(c, parts, childOffset);
                }
            }
            case "condition" -> collect(node.getAsJsonObject("on_false"), parts, offset);
            case "select" -> {
                JsonObject chosen = null;
                if ("display_context".equals(id(string(node, "property")))) {
                    for (JsonElement c : node.getAsJsonArray("cases")) {
                        JsonElement when = c.getAsJsonObject().get("when");
                        boolean gui = when.isJsonArray() ? when.getAsJsonArray().contains(new com.google.gson.JsonPrimitive("gui"))
                                : "gui".equals(when.getAsString());
                        if (gui) {
                            chosen = c.getAsJsonObject().getAsJsonObject("model");
                        }
                    }
                }
                if (chosen == null) {
                    chosen = node.has("fallback") ? node.getAsJsonObject("fallback")
                            : node.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model");
                }
                collect(chosen, parts, offset);
            }
            case "range_dispatch" -> collect(node.has("fallback") ? node.getAsJsonObject("fallback")
                    : node.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("model"), parts, offset);
            default -> {
                // bundle contents and other things that show nothing in an empty inventory slot
            }
        }
    }

    /** The colours of the definition's tints (index = tintindex), with the defaults the game uses without data. */
    private List<Integer> tints(JsonObject node) {
        List<Integer> result = new ArrayList<>();
        JsonArray tints = node.getAsJsonArray("tints");
        if (tints == null) {
            return result;
        }
        for (JsonElement element : tints) {
            JsonObject tint = element.getAsJsonObject();
            String type = id(string(tint, "type"));
            int color;
            if (tint.has("value")) {
                color = tint.get("value").getAsInt();
            } else if (tint.has("default")) {
                color = tint.get("default").getAsInt();
            } else if (type.equals("grass")) {
                color = grass(tint.has("temperature") ? tint.get("temperature").getAsDouble() : 0.5,
                        tint.has("downfall") ? tint.get("downfall").getAsDouble() : 1.0);
            } else {
                color = 0xFFFFFFFF;
            }
            result.add(color);
        }
        return result;
    }

    /** Grass colour from the game's colour map, like the client does for a temperature and downfall. */
    private int grass(double temperature, double downfall) {
        BufferedImage map = texture("minecraft:colormap/grass");
        if (map == null) {
            return 0xFF7CBD6B;
        }
        double t = Math.clamp(temperature, 0, 1);
        double d = Math.clamp(downfall, 0, 1) * t;
        int x = (int) ((1 - t) * 255);
        int y = (int) ((1 - d) * 255);
        return map.getRGB(Math.min(x, map.getWidth() - 1), Math.min(y, map.getHeight() - 1));
    }

    // ------------------------------------------------------------------ models

    /** How the inventory turns a model: the model's display "gui" (rotation in degrees, translation, scale). */
    private record View(double[] rotation, double[] translation, double[] scale) {
        static final View FLAT = new View(new double[3], new double[3], new double[]{1, 1, 1});
        static final View BLOCK = new View(new double[]{30, 225, 0}, new double[3], new double[]{0.625, 0.625, 0.625});
    }

    /** A model with its parents merged in. */
    private record Model(Map<String, String> textureMap, JsonArray elements, View view, boolean generated) {
        /** A texture variable ("all", "#side" ...) followed to the texture id, or null. */
        String texture(String variable) {
            if (variable == null) {
                return null;
            }
            String key = variable.startsWith("#") ? variable.substring(1) : variable;
            for (int i = 0; i < 16; i++) {
                String value = textureMap.get(key);
                if (value == null || !value.startsWith("#")) {
                    return value;
                }
                key = value.substring(1);
            }
            return null;
        }
    }

    private Model model(String id) {
        String path = path(id);
        if (models.containsKey(path)) {
            return models.get(path);
        }
        Map<String, String> map = new HashMap<>();
        JsonArray elements = null;
        View view = null;
        boolean generated = false;
        String current = path;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current.equals("builtin/generated")) {
                generated = true;
                break;
            }
            JsonObject json = json(ASSETS + "models/" + current + ".json");
            if (json == null) {
                break;
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        map.putIfAbsent(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
            if (elements == null && json.has("elements")) {
                elements = json.getAsJsonArray("elements");
            }
            if (view == null && json.has("display") && json.getAsJsonObject("display").has("gui")) {
                JsonObject gui = json.getAsJsonObject("display").getAsJsonObject("gui");
                view = new View(vector(gui, "rotation", 0), vector(gui, "translation", 0), vector(gui, "scale", 1));
            }
            current = json.has("parent") ? path(json.get("parent").getAsString()) : null;
        }
        Model model = json(ASSETS + "models/" + path + ".json") == null ? null
                : new Model(map, generated ? null : elements, view == null ? (generated ? View.FLAT : View.BLOCK) : view,
                generated);
        models.put(path, model);
        return model;
    }

    /** Texture layers of a flat item, scaled up without blurring; layer n takes tint n. */
    private void flat(Model model, List<Integer> tints, Graphics2D g) {
        for (int layer = 0; layer < 8; layer++) {
            String id = model.texture("layer" + layer);
            if (id == null) {
                break;
            }
            BufferedImage texture = paint(id, layer < tints.size() ? tints.get(layer) : -1, 1f);
            if (texture != null) {
                g.drawImage(texture, 0, 0, SIZE, SIZE, null);
            }
        }
    }

    /** The faces of a model's elements (cubes), in model space (0-16), before the view turns them. */
    private void elements(Model model, List<Integer> tints, double[] offset, List<Face> faces) {
        for (JsonElement e : model.elements()) {
            JsonObject element = e.getAsJsonObject();
            double[] from = vector(element, "from", 0);
            double[] to = vector(element, "to", 16);
            for (int i = 0; i < 3; i++) {
                from[i] += offset[i];
                to[i] += offset[i];
            }
            Rotation rotation = rotation(element.getAsJsonObject("rotation"), offset);
            boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
            JsonObject faceMap = element.getAsJsonObject("faces");
            if (faceMap == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> f : faceMap.entrySet()) {
                String direction = f.getKey();
                JsonObject face = f.getValue().getAsJsonObject();
                String textureId = model.texture(string(face, "texture"));
                if (textureId == null) {
                    continue;
                }
                int tintIndex = face.has("tintindex") ? face.get("tintindex").getAsInt() : -1;
                int tint = tintIndex >= 0 && tintIndex < tints.size() ? tints.get(tintIndex) : -1;
                BufferedImage texture = paint(textureId, tint, shade ? SHADE.getOrDefault(direction, 1f) : 1f);
                if (texture == null) {
                    continue;
                }
                double[] uv = face.has("uv") ? vector4(face.getAsJsonArray("uv")) : defaultUv(direction, from, to, offset);
                double sx = texture.getWidth() / 16.0;
                double sy = texture.getHeight() / 16.0;
                double[][] corners = corners(direction, from, to);
                if (corners == null) {
                    continue;
                }
                for (double[] c : corners) {
                    rotation.apply(c);
                }
                int turn = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4) : 0;
                faces.add(new Face(corners, texture, new double[]{uv[0] * sx, uv[1] * sy, uv[2] * sx, uv[3] * sy}, turn));
            }
        }
    }

    /** An element's rotation around one axis (with "rescale" stretching it back to full size, like the game). */
    private record Rotation(double[] origin, int axis, double angle, boolean rescale) {
        static final Rotation NONE = new Rotation(new double[3], 1, 0, false);

        void apply(double[] p) {
            if (angle == 0) {
                return;
            }
            double rad = Math.toRadians(angle);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double scale = rescale ? 1 / Math.cos(Math.toRadians(Math.abs(angle) > 45 ? 90 - Math.abs(angle) : Math.abs(angle))) : 1;
            double x = p[0] - origin[0];
            double y = p[1] - origin[1];
            double z = p[2] - origin[2];
            double nx = x, ny = y, nz = z;
            switch (axis) {
                case 0 -> {
                    ny = y * cos - z * sin;
                    nz = y * sin + z * cos;
                    ny *= scale;
                    nz *= scale;
                }
                case 1 -> {
                    nx = x * cos + z * sin;
                    nz = -x * sin + z * cos;
                    nx *= scale;
                    nz *= scale;
                }
                default -> {
                    nx = x * cos - y * sin;
                    ny = x * sin + y * cos;
                    nx *= scale;
                    ny *= scale;
                }
            }
            p[0] = nx + origin[0];
            p[1] = ny + origin[1];
            p[2] = nz + origin[2];
        }
    }

    private static Rotation rotation(JsonObject json, double[] offset) {
        if (json == null || !json.has("angle")) {
            return Rotation.NONE;
        }
        double[] origin = vector(json, "origin", 8);
        for (int i = 0; i < 3; i++) {
            origin[i] += offset[i];
        }
        int axis = switch (string(json, "axis")) {
            case "x" -> 0;
            case "z" -> 2;
            default -> 1;
        };
        return new Rotation(origin, axis, json.get("angle").getAsDouble(),
                json.has("rescale") && json.get("rescale").getAsBoolean());
    }

    /** UV the game takes for a face without "uv": the part of the texture the face covers. */
    private static double[] defaultUv(String direction, double[] from, double[] to, double[] offset) {
        double x1 = from[0] - offset[0], y1 = from[1] - offset[1], z1 = from[2] - offset[2];
        double x2 = to[0] - offset[0], y2 = to[1] - offset[1], z2 = to[2] - offset[2];
        return switch (direction) {
            case "down" -> new double[]{x1, 16 - z2, x2, 16 - z1};
            case "up" -> new double[]{x1, z1, x2, z2};
            case "north" -> new double[]{16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case "south" -> new double[]{x1, 16 - y2, x2, 16 - y1};
            case "west" -> new double[]{z1, 16 - y2, z2, 16 - y1};
            default -> new double[]{16 - z2, 16 - y2, 16 - z1, 16 - y1};
        };
    }

    /**
     * Corners of a face as seen from outside: top left, top right, bottom right, bottom left - the order in which
     * the UV's corners (u1 v1, u2 v1, u2 v2, u1 v2) lie on it.
     */
    private static double[][] corners(String direction, double[] f, double[] t) {
        double x1 = f[0], y1 = f[1], z1 = f[2], x2 = t[0], y2 = t[1], z2 = t[2];
        return switch (direction) {
            case "north" -> new double[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
            case "south" -> new double[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
            case "west" -> new double[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
            case "east" -> new double[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case "up" -> new double[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            case "down" -> new double[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
            default -> null;
        };
    }

    // ------------------------------------------------------------------ drawing in 3D

    /** A face to draw: corners in model space, its (painted) texture and UV in texture pixels, turned by 90° steps. */
    private record Face(double[][] corners, BufferedImage texture, double[] uv, int turn) {
    }

    /** Turns all faces by the view, drops the ones facing away and draws the rest from back to front. */
    private void draw(List<Face> faces, View view, Graphics2D g) {
        double[][] matrix = matrix(view.rotation());
        record Placed(double[][] screen, double depth, Face face) {
        }
        List<Placed> placed = new ArrayList<>();
        for (Face face : faces) {
            double[][] screen = new double[4][];
            double depth = 0;
            for (int i = 0; i < 4; i++) {
                double[] p = face.corners()[i];
                double[] v = {(p[0] - 8) * view.scale()[0], (p[1] - 8) * view.scale()[1], (p[2] - 8) * view.scale()[2]};
                double[] r = multiply(matrix, v);
                double x = r[0] + view.translation()[0];
                double y = r[1] + view.translation()[1];
                double z = r[2] + view.translation()[2];
                screen[i] = new double[]{(8 + x) * SIZE / 16.0, (8 - y) * SIZE / 16.0};
                depth += z;
            }
            // Facing the viewer: the corners run clockwise on screen (y points down).
            double cross = (screen[1][0] - screen[0][0]) * (screen[3][1] - screen[0][1])
                    - (screen[1][1] - screen[0][1]) * (screen[3][0] - screen[0][0]);
            if (cross <= 1e-6) {
                continue;
            }
            placed.add(new Placed(screen, depth / 4, face));
        }
        placed.sort(Comparator.comparingDouble(Placed::depth));
        for (Placed p : placed) {
            drawFace(p.face(), p.screen(), g);
        }
    }

    /** Maps the face's UV rectangle onto its corners on screen (an affine map - the faces are parallelograms). */
    private static void drawFace(Face face, double[][] screen, Graphics2D g) {
        double u1 = face.uv()[0], v1 = face.uv()[1], u2 = face.uv()[2], v2 = face.uv()[3];
        if (u1 == u2 || v1 == v2) {
            return;
        }
        // UV corner i lands on screen corner (i + turn) % 4.
        double[] tl = screen[face.turn() % 4];
        double[] tr = screen[(1 + face.turn()) % 4];
        double[] bl = screen[(3 + face.turn()) % 4];
        double ax = (tr[0] - tl[0]) / (u2 - u1), ay = (tr[1] - tl[1]) / (u2 - u1);
        double bx = (bl[0] - tl[0]) / (v2 - v1), by = (bl[1] - tl[1]) / (v2 - v1);
        AffineTransform transform = new AffineTransform(ax, ay, bx, by,
                tl[0] - ax * u1 - bx * v1, tl[1] - ay * u1 - by * v1);
        Path2D.Double outline = new Path2D.Double();
        // Half a pixel larger than the face, so neighbouring faces leave no gaps.
        double cx = 0, cy = 0;
        for (double[] p : screen) {
            cx += p[0] / 4;
            cy += p[1] / 4;
        }
        for (int i = 0; i < 4; i++) {
            double dx = screen[i][0] - cx, dy = screen[i][1] - cy;
            double length = Math.max(1e-6, Math.hypot(dx, dy));
            double x = screen[i][0] + dx / length * 0.35, y = screen[i][1] + dy / length * 0.35;
            if (i == 0) {
                outline.moveTo(x, y);
            } else {
                outline.lineTo(x, y);
            }
        }
        outline.closePath();
        var oldClip = g.getClip();
        g.clip(outline);
        // Only the UV rectangle of the texture (not its neighbours, which the outline would show at the edges).
        int sx = (int) Math.floor(Math.min(u1, u2)), sy = (int) Math.floor(Math.min(v1, v2));
        int ex = (int) Math.ceil(Math.max(u1, u2)), ey = (int) Math.ceil(Math.max(v1, v2));
        sx = Math.clamp(sx, 0, face.texture().getWidth() - 1);
        sy = Math.clamp(sy, 0, face.texture().getHeight() - 1);
        ex = Math.clamp(ex, sx + 1, face.texture().getWidth());
        ey = Math.clamp(ey, sy + 1, face.texture().getHeight());
        BufferedImage part = face.texture().getSubimage(sx, sy, ex - sx, ey - sy);
        AffineTransform at = new AffineTransform(transform);
        at.translate(sx, sy);
        g.drawImage(part, at, null);
        g.setClip(oldClip);
    }

    /** Rotation matrix of the display rotation (degrees around x, y, z - applied z first, like the game). */
    private static double[][] matrix(double[] degrees) {
        double[][] x = axis(0, degrees[0]);
        double[][] y = axis(1, degrees[1]);
        double[][] z = axis(2, degrees[2]);
        return times(times(x, y), z);
    }

    private static double[][] axis(int axis, double degrees) {
        double c = Math.cos(Math.toRadians(degrees));
        double s = Math.sin(Math.toRadians(degrees));
        return switch (axis) {
            case 0 -> new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
            case 1 -> new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
            default -> new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
        };
    }

    private static double[][] times(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    r[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return r;
    }

    private static double[] multiply(double[][] m, double[] v) {
        return new double[]{m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    // ------------------------------------------------------------------ items drawn by code

    /** Builds what the game draws with code. @return false if this kind is unknown (then the base model is drawn) */
    private boolean special(JsonObject special, Model base, Graphics2D g, List<Face> faces) {
        String type = id(string(special, "type"));
        switch (type) {
            case "chest" -> {
                BufferedImage t = texture("minecraft:entity/chest/" + id(string(special, "texture")));
                if (t == null) {
                    return false;
                }
                box(faces, t, 1, 0, 1, 15, 10, 15, 0, 19, 14, 10, 14, true);
                box(faces, t, 1, 9, 1, 15, 14, 15, 0, 0, 14, 5, 14, true);
                box(faces, t, 7, 7, 15, 9, 11, 16, 0, 0, 2, 4, 1, true);
                return true;
            }
            case "shulker_box" -> {
                BufferedImage t = texture("minecraft:entity/shulker/" + id(string(special, "texture")));
                if (t == null) {
                    return false;
                }
                box(faces, t, 0, 0, 0, 16, 8, 16, 0, 28, 16, 8, 16, false);
                box(faces, t, 0, 4, 0, 16, 16, 16, 0, 0, 16, 12, 16, false);
                return true;
            }
            case "head", "player_head" -> {
                String kind = type.equals("player_head") ? "player" : string(special, "kind");
                String path = switch (kind == null ? "" : kind) {
                    case "skeleton" -> "skeleton/skeleton";
                    case "wither_skeleton" -> "skeleton/wither_skeleton";
                    case "zombie" -> "zombie/zombie";
                    case "creeper" -> "creeper/creeper";
                    case "piglin" -> "piglin/piglin";
                    case "player" -> "player/wide/steve";
                    default -> null;
                };
                BufferedImage t = path == null ? null : texture("minecraft:entity/" + path);
                if (t == null) {
                    return false;
                }
                if (kind.equals("piglin")) {
                    box(faces, t, 3, 0, 4, 13, 8, 12, 0, 0, 10, 8, 8, true);
                } else {
                    box(faces, t, 4, 0, 4, 12, 8, 12, 0, 0, 8, 8, 8, true);
                    if (kind.equals("player")) {
                        box(faces, t, 3.75, -0.25, 3.75, 12.25, 8.25, 12.25, 32, 0, 8, 8, 8, true);
                    }
                }
                return true;
            }
            case "banner" -> {
                // The flag's front: the banner texture, the base colour (a white mask) dyed on top.
                BufferedImage flag = texture("minecraft:entity/banner/banner_base");
                BufferedImage mask = texture("minecraft:entity/banner/base");
                if (flag == null || mask == null) {
                    return false;
                }
                int color = DYES.getOrDefault(string(special, "color"), 0xF9FFFE);
                int h = SIZE - 4;
                int w = h / 2;
                g.drawImage(flag.getSubimage(1, 1, 20, 40), (SIZE - w) / 2, 2, w, h, null);
                g.drawImage(tinted(mask.getSubimage(1, 1, 20, 40), 0xFF000000 | color, 1f), (SIZE - w) / 2, 2, w, h, null);
                return true;
            }
            case "shield" -> {
                BufferedImage t = texture("minecraft:entity/shield/shield_base_nopattern");
                if (t == null) {
                    return false;
                }
                int h = SIZE - 4;
                int w = h * 12 / 22;
                g.drawImage(t.getSubimage(1, 1, 12, 22), (SIZE - w) / 2, 2, w, h, null);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * A cuboid as entity models have it: the texture holds its faces in the usual box layout from (u, v) for a size
     * of w x h x d pixels; the cuboid itself goes from (x1 y1 z1) to (x2 y2 z2) in model space.
     * Shulker boxes are not flipped like chests and heads.
     */
    private void box(List<Face> faces, BufferedImage t, double x1, double y1, double z1, double x2, double y2,
                     double z2, int u, int v, int w, int h, int d, boolean flipped) {
        double[] from = {x1, y1, z1};
        double[] to = {x2, y2, z2};
        // u-coordinates of the box layout: d | w | d | w across, top row d high, side row h high.
        int a = u, b = u + d, c = u + d + w, e = u + d + w + d, f = u + d + w + d + w;
        // Entity models face the other way: the front (second in the side row) faces south - towards the viewer in
        // the inventory. Most also stand upside down (flipped): then the layout's "down" is the top.
        addFace(faces, t, "up", from, to, flipped ? new double[]{c, v, c + w, v + d} : new double[]{b, v, c, v + d}, 1f);
        addFace(faces, t, "down", from, to, flipped ? new double[]{b, v, c, v + d} : new double[]{c, v, c + w, v + d}, 0.5f);
        addFace(faces, t, "east", from, to, new double[]{a, v + d, b, v + d + h}, 0.6f);
        addFace(faces, t, "south", from, to, new double[]{b, v + d, c, v + d + h}, 0.8f);
        addFace(faces, t, "west", from, to, new double[]{c, v + d, e, v + d + h}, 0.6f);
        addFace(faces, t, "north", from, to, new double[]{e, v + d, f, v + d + h}, 0.8f);
    }

    private void addFace(List<Face> faces, BufferedImage texture, String direction, double[] from, double[] to,
                         double[] uv, float shade) {
        faces.add(new Face(corners(direction, from, to), tinted(texture, -1, shade), uv, 0));
    }

    // ------------------------------------------------------------------ textures

    /** A texture with tint (ARGB, -1 = none) and shade multiplied in; cached. */
    private BufferedImage paint(String id, int tint, float shade) {
        String key = id + "|" + tint + "|" + shade;
        BufferedImage cached = painted.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage texture = texture(id);
        if (texture == null) {
            return null;
        }
        BufferedImage result = tinted(texture, tint, shade);
        painted.put(key, result);
        return result;
    }

    private static BufferedImage tinted(BufferedImage texture, int tint, float shade) {
        if (tint == -1 && shade == 1f) {
            return texture;
        }
        float r = tint == -1 ? 1 : ((tint >> 16) & 0xFF) / 255f;
        float gr = tint == -1 ? 1 : ((tint >> 8) & 0xFF) / 255f;
        float b = tint == -1 ? 1 : (tint & 0xFF) / 255f;
        BufferedImage result = new BufferedImage(texture.getWidth(), texture.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int argb = texture.getRGB(x, y);
                int pr = Math.round(((argb >> 16) & 0xFF) * r * shade);
                int pg = Math.round(((argb >> 8) & 0xFF) * gr * shade);
                int pb = Math.round((argb & 0xFF) * b * shade);
                result.setRGB(x, y, (argb & 0xFF000000) | (pr << 16) | (pg << 8) | pb);
            }
        }
        return result;
    }

    /** A texture by id ("minecraft:block/stone", "item/apple"), only the first frame of an animation; or null. */
    private BufferedImage texture(String id) {
        String path = path(id);
        if (textures.containsKey(path)) {
            return textures.get(path);
        }
        BufferedImage image = null;
        byte[] data = files.get(ASSETS + "textures/" + path + ".png");
        if (data != null) {
            try {
                BufferedImage read = ImageIO.read(new ByteArrayInputStream(data));
                if (read != null) {
                    BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = argb.createGraphics();
                    g.drawImage(read, 0, 0, null);
                    g.dispose();
                    image = argb;
                    // Animated: the frames lie below each other - keep the first.
                    if (files.containsKey(ASSETS + "textures/" + path + ".png.mcmeta") && image.getHeight() > image.getWidth()) {
                        image = image.getSubimage(0, 0, image.getWidth(), image.getWidth());
                    }
                }
            } catch (IOException e) {
                image = null;
            }
        }
        textures.put(path, image);
        return image;
    }

    // ------------------------------------------------------------------ helpers

    private JsonObject json(String path) {
        byte[] data = files.get(path);
        if (data == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "minecraft:block/stone" -> "block/stone". */
    private static String path(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    /** "minecraft:model" -> "model". */
    private static String id(String value) {
        return value == null ? "" : value.startsWith("minecraft:") ? value.substring(10) : value;
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static double[] vector(JsonObject json, String key, double fallback) {
        JsonArray array = json.getAsJsonArray(key);
        double[] result = {fallback, fallback, fallback};
        if (array != null) {
            for (int i = 0; i < 3 && i < array.size(); i++) {
                result[i] = array.get(i).getAsDouble();
            }
        }
        return result;
    }

    private static double[] vector4(JsonArray array) {
        double[] result = new double[4];
        for (int i = 0; i < 4 && i < array.size(); i++) {
            result[i] = array.get(i).getAsDouble();
        }
        return result;
    }
}
