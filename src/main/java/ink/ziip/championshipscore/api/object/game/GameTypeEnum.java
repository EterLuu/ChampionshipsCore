package ink.ziip.championshipscore.api.object.game;

public enum GameTypeEnum {
    Bingo, ParkourTag, BattleBox, TNTRun, SnowballFight, SkyWars, TGTTOS, DragonEggCarnival;

    @Override
    public String toString() {
        if (this == Bingo)
            return "宾果时速";
        if (this == ParkourTag)
            return "跑酷追击";
        if (this == BattleBox)
            return "斗战方框";
        if (this == TNTRun)
            return "TNT快跑";
        if (this == SnowballFight)
            return "雪球大战";
        if (this == SkyWars)
            return "空岛战争";
        if (this == TGTTOS)
            return "去到另一边";
        if (this == DragonEggCarnival)
            return "龙蛋狂欢";
        return "Unknown";
    }
}
