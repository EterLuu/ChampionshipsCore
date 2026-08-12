package ink.ziip.championshipscore.api.finale;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

/** Describes one game exposed through the championship finale command surface. */
public record FinaleGameDefinition(@NotNull GameTypeEnum gameType,
                                   @NotNull String commandName,
                                   @NotNull String defaultArea,
                                   boolean supportsPartialRoster) {
}
