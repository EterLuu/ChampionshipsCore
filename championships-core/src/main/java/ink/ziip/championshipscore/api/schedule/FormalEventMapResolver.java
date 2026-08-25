package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/** Resolves formal-event map registration names from the single CCConfig contract. */
public final class FormalEventMapResolver {
    private FormalEventMapResolver() {
    }

    public static @NotNull List<String> maps(@NotNull ChampionshipsCore plugin,
                                             @NotNull GameTypeEnum game) {
        List<String> configured = plugin.getConfigurationManager().getCCConfig().formalEventMaps(game);
        if (!configured.isEmpty()) return configured.stream()
                .map(name -> canonicalName(plugin, game, name))
                .toList();

        BaseGameInstanceManager<?> manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null) return List.of();
        return manager.getAreaNameList().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static String map(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum game, int round) {
        List<String> maps = maps(plugin, game);
        return round < 1 || round > maps.size() ? null : maps.get(round - 1);
    }

    private static String canonicalName(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum game,
                                        @NotNull String configured) {
        BaseGameInstanceManager<?> manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null || manager.getArea(configured) != null) return configured;
        return manager.getAreaNameList().stream()
                .filter(name -> name.equalsIgnoreCase(configured))
                .min(Comparator.comparing(String::toString))
                .orElse(configured);
    }
}
