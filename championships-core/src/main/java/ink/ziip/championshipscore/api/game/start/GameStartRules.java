package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

/** Start topology belongs to gameplay ownership, independently of the command spelling. */
public final class GameStartRules {
    public enum Topology {
        PAIRED_COPIES,
        PAIRED_FINAL,
        MULTI_TEAM
    }

    private GameStartRules() {}

    public static Topology topology(GameTypeEnum game) {
        if (FinaleGameRegistry.isRegistered(game)) return Topology.PAIRED_FINAL;
        return switch (game) {
            case BattleBox, ParkourTag, LaserBox -> Topology.PAIRED_COPIES;
            default -> Topology.MULTI_TEAM;
        };
    }

    public static boolean internalArenas(GameTypeEnum game) {
        return switch (game) {
            case TNTRun, HotyCodyDusky, FrostbiteFrenzy, SnowballShowdown -> true;
            default -> false;
        };
    }

    public static void validateTeamCount(GameTypeEnum game, int count) {
        if (count < 1) throw new IllegalArgumentException("没有参赛队伍");
        switch (topology(game)) {
            case PAIRED_FINAL -> {
                if (count != 2) throw new IllegalArgumentException("最终对决必须指定两支不同的队伍");
            }
            case PAIRED_COPIES -> {
                if (count < 2 || count % 2 != 0) {
                    throw new IllegalArgumentException("双队比赛需要双数队伍，每两队分配一个场地副本");
                }
            }
            case MULTI_TEAM -> {
                if (game == GameTypeEnum.FrostbiteFrenzy && (count < 2 || count > 16)) {
                    throw new IllegalArgumentException("霜冻决斗需要 2–16 支队伍");
                }
            }
        }
    }
}
