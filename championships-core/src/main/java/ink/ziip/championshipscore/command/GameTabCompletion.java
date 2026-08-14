package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Shared enablement boundary for game and map command completions. */
public final class GameTabCompletion {
    private GameTabCompletion() {}

    public static @NotNull List<String> gameNames(@NotNull Set<GameTypeEnum> enabledGames) {
        return enabledGames.stream()
                .map(GameTypeEnum::commandName)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public static @NotNull List<String> mapNames(@Nullable GameTypeEnum game,
                                                  @NotNull Set<GameTypeEnum> enabledGames,
                                                  @NotNull Collection<String> mapNames) {
        if (game == null || !enabledGames.contains(game)) return List.of();
        List<String> candidates = new ArrayList<>();
        for (String mapName : mapNames) {
            if (mapName != null && !candidates.contains(mapName)) candidates.add(mapName);
        }
        candidates.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(candidates);
    }
}
