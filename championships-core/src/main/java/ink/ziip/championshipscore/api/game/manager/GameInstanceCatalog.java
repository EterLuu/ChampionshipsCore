package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoInstance;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

/** Read-only queries over the registered local copies and Core's remote match handles. */
final class GameInstanceCatalog {
    private GameInstanceCatalog() {}

    static List<BaseGameInstance> select(
            Map<GameTypeEnum, BaseGameInstanceManager<? extends BaseGameInstance>> managers,
            Collection<? extends BaseGameInstance> remote,
            Predicate<GameTypeEnum> loaded,
            Predicate<GameTypeEnum> enabled,
            Predicate<BaseGameInstance> include,
            Function<BaseGameInstance, String> name) {
        var instances = new ArrayList<BaseGameInstance>();
        managers.forEach(
                (game, manager) -> {
                    if (loaded.test(game) && enabled.test(game))
                        manager.getRuntimeInstances().stream()
                                .filter(include)
                                .forEach(instances::add);
                });
        remote.stream()
                .filter(instance -> enabled.test(instance.getGameTypeEnum()))
                .filter(include)
                .forEach(instances::add);
        instances.sort(
                Comparator.comparing(BaseGameInstance::getGameTypeEnum)
                        .thenComparing(name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(COPY_ORDER));
        return List.copyOf(instances);
    }

    private static final Comparator<BaseGameInstance> COPY_ORDER =
            Comparator.comparingInt(BaseGameInstance::getCopyIndex)
                    .thenComparing(GameInstanceCatalog::stableInstanceKey);

    static List<BaseGameInstance> forMap(
            List<BaseGameInstance> source, GameTypeEnum type, String name) {
        return source.stream()
                .filter(
                        instance ->
                                instance.getGameTypeEnum() == type && mapMatches(instance, name))
                .sorted(COPY_ORDER)
                .toList();
    }

    static @NotNull String stableInstanceKey(@NotNull BaseGameInstance instance) {
        return instance instanceof RemoteBingoInstance remote ? remote.matchId().toString() : "";
    }

    static @NotNull String canonicalMapName(@NotNull BaseGameInstance instance) {
        String configName = instance.getGameConfig().getConfigName();
        if (configName != null && !configName.isBlank()) return configName;
        String areaName = instance.getGameConfig().getAreaName();
        return areaName == null ? "" : areaName;
    }

    static boolean mapMatches(@NotNull BaseGameInstance instance, @NotNull String mapName) {
        String configName = instance.getGameConfig().getConfigName();
        String areaName = instance.getGameConfig().getAreaName();
        return configName != null && configName.equalsIgnoreCase(mapName)
                || areaName != null && areaName.equalsIgnoreCase(mapName);
    }
}
