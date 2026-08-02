package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.api.game.config.model.GameLifecycleSettings;
import ink.ziip.championshipscore.api.game.config.model.GamePresentationSettings;
import ink.ziip.championshipscore.api.game.config.model.GameVariant;
import org.jetbrains.annotations.NotNull;

/** Fully resolved SkyWars rules profile. It is immutable for the lifetime of a running instance. */
public record SkyWarsVariant(@NotNull String id, @NotNull GameLifecycleSettings lifecycle,
                             @NotNull GamePresentationSettings presentation,
                             @NotNull SkyWarsRules rules, @NotNull SkyWarsScoring scoring)
        implements GameVariant {
    public static @NotNull SkyWarsVariant from(@NotNull SkyWarsConfig config) {
        String id = config.getVariantId();
        if (id == null || id.isBlank()) id = config.getAreaName();
        if (id == null || id.isBlank()) id = "default";
        return new SkyWarsVariant(id,
                new GameLifecycleSettings(config.getPrepareTime(), 5, config.getTimer()),
                new GamePresentationSettings(config.getRules()),
                new SkyWarsRules(config.isGlassCage(),
                        new SkyWarsBoundaryRules(config.getBoundaryDefaultHeight(),
                                config.getBoundaryMiddleHeight(), config.getBoundaryLowestHeight(),
                                config.getBoundaryRadius(), config.getTimeEnableBoundaryShrink(),
                                config.getShrinkTime()),
                        config.getTimeDisableHealthRegain(), config.getSpawnHappyGhast()),
                new SkyWarsScoring(config.getKillPoints(), config.getSurvivalPoints(),
                        config.getPlayerEliminationSurvivalPoints(),
                        config.getTeamEliminationSurvivalPoints()));
    }
}
