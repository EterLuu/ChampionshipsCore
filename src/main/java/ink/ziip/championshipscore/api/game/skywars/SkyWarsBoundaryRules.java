package ink.ziip.championshipscore.api.game.skywars;

import java.util.List;

/** Variant-owned SkyWars border dimensions and remaining-time transition schedule. */
public record SkyWarsBoundaryRules(int defaultHeight, int middleHeight, int lowestHeight, int radius,
                                   int enableAtRemainingSeconds, List<String> shrinkSchedule) {
    public SkyWarsBoundaryRules {
        shrinkSchedule = shrinkSchedule == null ? List.of() : List.copyOf(shrinkSchedule);
    }
}
