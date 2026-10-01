package ink.ziip.championshipscore.api.game.riptiderush;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned, offline, map-owned adaptations. Loading never contacts the wiki or modifies a world. */
public final class RiptideHitwCatalog {
    public record Passage(double lateral, double sill, boolean crouch) { }
    private static final Map<String,List<Passage>> ROUTES = new HashMap<>();
    private static final List<RiptideLevelTemplate> TEMPLATES = load();
    private static final Map<RiptideBlueprint, List<Passage>> SNAPSHOT_ROUTES = new HashMap<>();
    private static final Map<RiptideBlueprint, List<Passage>> MIRRORED_SNAPSHOT_ROUTES = new HashMap<>();
    private static final Map<String, RiptideBlueprint> ORIGINALS = new HashMap<>();
    static {
        for (var t : TEMPLATES) {
            SNAPSHOT_ROUTES.putIfAbsent(t.blueprint(), ROUTES.get(t.id()));
            ORIGINALS.put(t.id(), t.blueprint());
        }
        SNAPSHOT_ROUTES.forEach((blueprint, routes) -> MIRRORED_SNAPSHOT_ROUTES.put(blueprint,
                routes.stream().map(p -> new Passage(-p.lateral(), p.sill(), p.crouch())).toList()));
    }
    private RiptideHitwCatalog() { }

    public static List<RiptideLevelTemplate> templates() { return TEMPLATES; }

    /** Metadata is valid only for the exact supplied snapshot, never for an administrator's edits. */
    public static List<Passage> passages(RiptideLevelTemplate template, boolean mirrored) {
        if (template.blueprint() == null) return List.of();
        return (mirrored ? MIRRORED_SNAPSHOT_ROUTES : SNAPSHOT_ROUTES)
                .getOrDefault(template.blueprint(), List.of());
    }

    public static boolean isOriginal(RiptideLevelTemplate template) {
        return template.blueprint() != null && template.blueprint().equals(ORIGINALS.get(template.id()));
    }

    private static List<RiptideLevelTemplate> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(RiptideHitwCatalog.class
                .getResourceAsStream("/riptiderush/hitw-walls.json")), StandardCharsets.UTF_8)) {
            var result = new ArrayList<RiptideLevelTemplate>();
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pool")) {
                var row = element.getAsJsonObject(); var b = row.getAsJsonObject("building");
                var routes = new ArrayList<Passage>();
                for (var route : row.getAsJsonArray("routes")) {
                    var p = route.getAsJsonObject();
                    routes.add(new Passage(p.get("lateral").getAsDouble(), p.get("sill").getAsDouble(), p.get("crouch").getAsBoolean()));
                }
                ROUTES.put(row.get("id").getAsString(), List.copyOf(routes));
                var blueprint = new RiptideBlueprint(b.get("schematic").getAsString(), b.get("extent").getAsInt(),
                        b.get("width").getAsInt(), b.get("height").getAsInt(), List.of());
                result.add(new RiptideLevelTemplate(row.get("id").getAsString(), row.get("name").getAsString(),
                        RiptideLevelType.PASS, "CUSTOM", true, row.get("weight").getAsInt(),
                        1, row.get("difficulty").getAsInt(), blueprint));
            }
            if (result.size() != 354 || result.stream().map(RiptideLevelTemplate::id).distinct().count() != result.size())
                throw new IllegalStateException("Incomplete HITW wall catalogue");
            return List.copyOf(result);
        } catch (Exception failure) { throw new IllegalStateException("Cannot load bundled HITW walls", failure); }
    }

}
