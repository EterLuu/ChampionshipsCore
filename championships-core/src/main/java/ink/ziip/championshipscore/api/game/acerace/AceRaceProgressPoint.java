package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** An ordered progress gate and the rules for the course segment after it. */
record AceRaceProgressPoint(int order, @NotNull Vector pos1, @NotNull Vector pos2, int fallY,
                            @NotNull AceRaceEquipment equipment) {
    private static final int MINIMUM_GATE_LENGTH_BLOCKS = 20;
    private static final int BELOW_GATE_TOLERANCE_BLOCKS = 3;

    AceRaceProgressPoint {
        Vector[] expanded = expandToMinimumLength(pos1, pos2, MINIMUM_GATE_LENGTH_BLOCKS);
        pos1 = expanded[0];
        pos2 = expanded[1];
    }

    boolean crossed(@NotNull Location from, @NotNull Location to) {
        return new AceRaceLine(pos1, pos2).crossedAtOrAbove(from, to, BELOW_GATE_TOLERANCE_BLOCKS);
    }

    /** Keeps the configured centre while widening short horizontal gates on both ends. */
    private static @NotNull Vector[] expandToMinimumLength(@NotNull Vector first, @NotNull Vector second,
                                                            int minimumLength) {
        Vector expandedFirst = first.clone();
        Vector expandedSecond = second.clone();
        boolean alongX = Math.abs(first.getBlockX() - second.getBlockX())
                >= Math.abs(first.getBlockZ() - second.getBlockZ());
        int firstCoordinate = alongX ? first.getBlockX() : first.getBlockZ();
        int secondCoordinate = alongX ? second.getBlockX() : second.getBlockZ();
        int missing = minimumLength - Math.abs(firstCoordinate - secondCoordinate) - 1;
        if (missing <= 0) return new Vector[]{expandedFirst, expandedSecond};

        int lowerExtension = missing / 2;
        int upperExtension = missing - lowerExtension;
        boolean firstIsLower = firstCoordinate <= secondCoordinate;
        if (alongX) {
            expandedFirst.setX(firstCoordinate + (firstIsLower ? -lowerExtension : upperExtension));
            expandedSecond.setX(secondCoordinate + (firstIsLower ? upperExtension : -lowerExtension));
        } else {
            expandedFirst.setZ(firstCoordinate + (firstIsLower ? -lowerExtension : upperExtension));
            expandedSecond.setZ(secondCoordinate + (firstIsLower ? upperExtension : -lowerExtension));
        }
        return new Vector[]{expandedFirst, expandedSecond};
    }
}
