package ink.ziip.championshipscore.api.game.riptiderush;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned, offline, map-owned adaptations. Loading never contacts the wiki or modifies a world. */
public final class RiptideHitwCatalog {
    public static final int VERSION = 4;
    public record Passage(double lateral, double sill, boolean crouch) { }
    private static final Map<String,List<Passage>> ROUTES = new HashMap<>();
    private static final Map<String,Set<String>> PREVIOUS = new HashMap<>();
    private static final List<RiptideLevelTemplate> TEMPLATES = load();
    private static final Map<RiptideBlueprint, List<Passage>> SNAPSHOT_ROUTES = new HashMap<>();
    private static final Map<String, RiptideBlueprint> ORIGINALS = new HashMap<>();
    static {
        for (var t : TEMPLATES) {
            SNAPSHOT_ROUTES.putIfAbsent(t.blueprint(), ROUTES.get(t.id()));
            ORIGINALS.put(t.id(), t.blueprint());
        }
    }
    private RiptideHitwCatalog() { }

    public static List<RiptideLevelTemplate> templates() { return TEMPLATES; }

    /** Metadata is valid only for the exact supplied snapshot, never for an administrator's edits. */
    public static List<Passage> passages(RiptideLevelTemplate template, boolean mirrored) {
        if (template.blueprint() == null) return List.of();
        return SNAPSHOT_ROUTES.getOrDefault(template.blueprint(), List.of()).stream()
                .map(p -> new Passage(mirrored ? -p.lateral() : p.lateral(), p.sill(), p.crouch())).toList();
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
                var previous = new HashSet<String>();
                for (String key : List.of("previous-schematic", "previous-v2-schematic"))
                    if (row.has(key)) previous.add(row.get(key).getAsString());
                PREVIOUS.put(row.get("id").getAsString(), Set.copyOf(previous));
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

    /** Upgrade v1/v2 snapshots and append newly introduced E walls, preserving existing edits and deletions. */
    public static void migrate(YamlConfiguration yaml) {
        int previous = yaml.getInt("course.hitw-catalog-version", 0);
        if (previous >= VERSION) return;
        var rows = new ArrayList<Map<?, ?>>(yaml.getMapList("course.pool"));
        var ids = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); String id = String.valueOf(row.get("id")); ids.add(id);
            if (previous < 3 && row.get("building") instanceof Map<?,?> b && PREVIOUS.getOrDefault(id, Set.of()).contains(b.get("schematic"))) {
                var replacement = new LinkedHashMap<Object,Object>(row);
                var current = templates().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
                replacement.put("building", current.blueprint().serialize());
                replacement.put("difficulty", current.difficulty());
                rows.set(i, replacement);
            }
        }
        // v4 introduces E (Very Easy–Easy). Never resurrect deleted D/X walls, and
        // never reset v3 custom ratings even when their snapshot matches an older catalogue.
        for (var template : templates())
            if ((previous == 0 || template.id().matches(".*_e[0-9]+")) && ids.add(template.id()))
                rows.add(template.serialize());
        if (rows.size() > RiptideRushConfig.MAX_POOL_SIZE)
            throw new IllegalArgumentException("导入墙体后关卡池超过" + RiptideRushConfig.MAX_POOL_SIZE + "项");
        yaml.set("course.pool", rows);
        yaml.set("course.hitw-catalog-version", VERSION);
    }
}
