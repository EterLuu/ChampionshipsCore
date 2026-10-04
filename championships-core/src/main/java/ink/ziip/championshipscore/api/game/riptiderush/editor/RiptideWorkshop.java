package ink.ziip.championshipscore.api.game.riptiderush.editor;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorPage;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideDifficulty;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideLevelType;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * One bounded construction draft per locked map. Permanent content is captured only by Save
 * building.
 */
public final class RiptideWorkshop {
    public static final int BUILDING_EXTENT = 7;

    public static int halfWidth(RiptideRushConfig config, RiptideCourseGeometry geometry) {
        // Keep one block for the visible frame inside the owned generation corridor.
        return geometry.halfWidth() + config.getObstacleMargin() - 1;
    }

    public static Map<String, Object> dimensions(RiptideRushConfig config) {
        return Map.of(
                "length",
                BUILDING_EXTENT * 2 + 1,
                "width",
                halfWidth(config, config.resolveGeometry()) * 2 + 1,
                "height",
                config.getClearHeight());
    }

    public record Draft(
            UUID owner,
            PrepareSession session,
            RiptideEditorPage origin,
            int step,
            boolean enableOnSave) {}

    private static final Map<PrepareSession, Draft> ACTIVE = new HashMap<>();

    private RiptideWorkshop() {}

    public static Draft get(PrepareSession session) {
        return ACTIVE.get(session);
    }

    public static void begin(
            Player player,
            PrepareSession session,
            RiptideEditorPage origin,
            int step,
            boolean enableOnSave) {
        if (ACTIVE.containsKey(session)) throw new IllegalStateException("请先保存或放弃当前建筑");
        ACTIVE.put(session, new Draft(player.getUniqueId(), session, origin, step, enableOnSave));
    }

    public static Draft finish(PrepareSession session) {
        return ACTIVE.remove(session);
    }

    public static void cancelAll() {
        for (var session : List.copyOf(ACTIVE.keySet())) cancel(session);
    }

    public static void cancel(PrepareSession session) {
        var draft = ACTIVE.remove(session);
        if (draft == null) return;
        // Unsaved blocks are preview-only; the next generation clears this owned corridor.
        var player = Bukkit.getPlayer(draft.owner());
        if (player != null && player.isOnline()) player.closeInventory();
    }

    public static void decorate(
            PrepareSession session, RiptideCoursePlan.Level level, boolean blank) {
        var c = (RiptideRushConfig) session.getTarget().config();
        decorate(c, level, blank);
    }

    public static void decorate(RiptideRushConfig c, RiptideCoursePlan.Level level, boolean blank) {
        var g = c.resolveGeometry();
        var world = g.centerAt(level.step()).getWorld();
        if (blank && level.type() != RiptideLevelType.MATH) {
            for (int f = -BUILDING_EXTENT; f <= BUILDING_EXTENT; f++)
                for (int l = -halfWidth(c, g); l <= halfWidth(c, g); l++)
                    for (int y = 1; y <= c.getClearHeight(); y++)
                        world.getBlockAt(
                                        g.blockX(level.step() + f, l),
                                        g.floorY() + y,
                                        g.blockZ(level.step() + f, l))
                                .setType(Material.AIR, false);
        }
        List<Material> floor =
                level.template().blueprint() != null
                        ? level.template().blueprint().floorMaterials()
                        : List.of();
        if (level.type() == RiptideLevelType.COLOR_FLOOR && floor.isEmpty() && !blank) {
            var theme = RiptideColorFloorRun.Theme.valueOf(level.variant());
            floor =
                    new RiptideColorFloorRun(
                                    g.raftWidth(),
                                    g.raftLength(),
                                    RiptideDifficulty.floorRoundTicks(level.step(), g.totalSteps()),
                                    new Random(level.contentSeed()),
                                    theme)
                            .floor();
        }
        for (int f = -g.halfLength(); f <= g.halfLength(); f++)
            for (int l = -g.halfWidth(); l <= g.halfWidth(); l++) {
                int index = (f + g.halfLength()) * g.raftWidth() + l + g.halfWidth();
                world.getBlockAt(
                                g.blockX(level.step() + f, l),
                                g.floorY(),
                                g.blockZ(level.step() + f, l))
                        .setType(floor.isEmpty() ? Material.OAK_PLANKS : floor.get(index), false);
            }
        // The frame lies just outside the saved region; amber caps mark the length.
        int outer = halfWidth(c, g) + 1, end = BUILDING_EXTENT + 1;
        for (int f = -end; f <= end; f++)
            for (int l = -outer; l <= outer; l++) {
                if (Math.abs(l) != outer && Math.abs(f) != end) continue;
                world.getBlockAt(
                                g.blockX(level.step() + f, l),
                                g.floorY(),
                                g.blockZ(level.step() + f, l))
                        .setType(
                                Math.abs(f) == end
                                        ? Material.ORANGE_CONCRETE
                                        : Material.LIGHT_BLUE_CONCRETE,
                                false);
            }
        // Corner posts show the actual maximum building height without entering the capture.
        for (int f : new int[] {-end, end})
            for (int l : new int[] {-outer, outer})
                for (int y = 1; y <= c.getClearHeight(); y++)
                    world.getBlockAt(
                                    g.blockX(level.step() + f, l),
                                    g.floorY() + y,
                                    g.blockZ(level.step() + f, l))
                            .setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
    }
}
