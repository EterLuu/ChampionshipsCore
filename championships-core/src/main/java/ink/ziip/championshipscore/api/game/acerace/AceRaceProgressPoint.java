package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** An ordered progress gate and the rules for the course segment after it. */
record AceRaceProgressPoint(int order, @NotNull Vector pos1, @NotNull Vector pos2, int fallY,
                            @NotNull AceRaceEquipment equipment) {

    boolean crossed(@NotNull Location from, @NotNull Location to) {
        return new AceRaceLine(pos1, pos2).crossedAtOrAbove(from, to);
    }

}
