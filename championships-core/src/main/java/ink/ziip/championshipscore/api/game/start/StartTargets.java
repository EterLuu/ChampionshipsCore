package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only target selection used by command preflight and runtime batch admission. */
public final class StartTargets {
    private StartTargets() {}

    public static <T extends BaseGameInstance> List<T> copies(
            List<T> pool, ArenaSelection selection, int needed) {
        if (pool.isEmpty() || needed < 1) throw new IllegalArgumentException("地图没有可用比赛副本");
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
                                        () -> new IllegalArgumentException("找不到副本：" + (index + 1)));
                if (slot.getGameStageEnum() != GameStageEnum.WAITING)
                    throw new IllegalArgumentException("指定副本正在比赛：" + (index + 1));
                selected.add(slot);
            }
        }
        if (selected.size() < needed)
            throw new IllegalArgumentException(
                    "空闲副本不足：需要 " + needed + " 个，实际 " + selected.size() + " 个");
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
            throw new IllegalArgumentException("该游戏每次选择一个运行副本");
        }
        return copies(
                pool,
                selection,
                GameStartRules.topology(game) == GameStartRules.Topology.PAIRED_COPIES
                        ? teams / 2
                        : 1);
    }
}
