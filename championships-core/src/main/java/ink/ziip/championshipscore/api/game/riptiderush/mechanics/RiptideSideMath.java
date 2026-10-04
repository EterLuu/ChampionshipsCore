package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCoursePlan;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

/** Seeded per-wall questions and exact block-column answers; shared by rounds and trials. */
public final class RiptideSideMath {
    private RiptideSideMath() {}

    public static RiptideQuestion question(
            RiptideCoursePlan.Level level, int beat, int minimum, int maximum) {
        if (!level.sideMath() || beat < 1 || beat > level.sideWalls().size())
            throw new IllegalArgumentException("invalid side math beat");
        var random = new Random(level.contentSeed());
        RiptideQuestion question = null;
        var variants = RiptideQuestion.variants();
        for (int i = 0; i < beat; i++)
            question =
                    RiptideQuestion.generate(
                            level.variant().startsWith("OBSERVE_")
                                    ? level.variant()
                                    : variants.get(random.nextInt(variants.size())),
                            random,
                            minimum,
                            maximum,
                            4);
        return question;
    }

    public static List<Material> floor(RiptideCourseGeometry g) {
        var result = new ArrayList<Material>();
        for (int forward = -g.halfLength(); forward <= g.halfLength(); forward++)
            for (int lateral = -g.halfWidth(); lateral <= g.halfWidth(); lateral++)
                result.add(lateral >= 0 ? Material.RED_CONCRETE : Material.LIGHT_BLUE_CONCRETE);
        return List.copyOf(result);
    }

    public static boolean matches(
            Location feet, RiptideCourseGeometry g, int step, RiptideQuestion question) {
        if (feet.getWorld() == null
                || !feet.getWorld().equals(g.centerAt(step).getWorld())
                || !Double.isFinite(feet.getY())
                || feet.getY() < g.floorY() + 1) return false;
        int dx = feet.getBlockX() - g.blockX(step, 0), dz = feet.getBlockZ() - g.blockZ(step, 0);
        int lateral = dx * g.stepZ() - dz * g.stepX();
        int forward = dx * g.stepX() + dz * g.stepZ();
        return Math.abs(lateral) <= g.halfWidth()
                && Math.abs(forward) <= g.halfLength()
                && question.accepts(lateral >= 0);
    }
}
