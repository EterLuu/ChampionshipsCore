package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only target selection used by command preflight and runtime batch admission. */
public final class StartTargets {
    private StartTargets() {}

    public static <T extends BaseGameInstance> List<T> copies(
            List<T> pool, ArenaSelection selection, int needed) {
        if (pool.isEmpty() || needed < 1)
            throw new IllegalArgumentException(MessageConfig.codeText("start-targets.copies-1"));
        List<T> selected = new ArrayList<>();
        if (selection.automatic()) {
            selected.addAll(
                    pool.stream()
                            .filter(slot -> slot.getGameStageEnum() == GameStageEnum.WAITING)
                            .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex))
                            .toList());
        } else {
            int count =
                    pool.stream().mapToInt(BaseGameInstance::getCopyIndex).max().orElseThrow() + 1;
            for (int index : selection.resolve(count)) {
                T slot =
                        pool.stream()
                                .filter(item -> item.getCopyIndex() == index)
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        MessageConfig.codeText(
                                                                "start-targets.copies-2",
                                                                (index + 1))));
                if (slot.getGameStageEnum() != GameStageEnum.WAITING)
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("start-targets.copies-3", (index + 1)));
                selected.add(slot);
            }
        }
        if (selected.size() < needed)
            throw new IllegalArgumentException(
                    MessageConfig.codeText("start-targets.copies-4", needed, selected.size()));
        return List.copyOf(selected.subList(0, needed));
    }

    public static List<? extends BaseGameInstance> forGame(
            GameTypeEnum game,
            List<? extends BaseGameInstance> pool,
            int teams,
            ArenaSelection selection) {
        if (GameStartRules.internalArenas(game)) {
            var target = copies(pool, ArenaSelection.all(), 1);
            selection.resolve(SubArenaSupport.count(target.getFirst()));
            return target;
        }
        if (GameStartRules.topology(game) != GameStartRules.Topology.PAIRED_COPIES
                && !selection.automatic()
                && selection.indices().size() != 1) {
            throw new IllegalArgumentException(MessageConfig.codeText("start-targets.for-game-1"));
        }
        return copies(
                pool,
                selection,
                GameStartRules.topology(game) == GameStartRules.Topology.PAIRED_COPIES
                        ? teams / 2
                        : 1);
    }
}
