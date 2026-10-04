package ink.ziip.championshipscore.api.game.skywars.mechanics;

import org.jetbrains.annotations.Nullable;

/** Mechanics that may vary independently of the physical SkyWars map. */
public record SkyWarsRules(
        boolean glassCage,
        SkyWarsBoundaryRules boundary,
        int disableHealthRegainAtRemainingSeconds,
        @Nullable Integer spawnHappyGhastAtRemainingSeconds) {}
