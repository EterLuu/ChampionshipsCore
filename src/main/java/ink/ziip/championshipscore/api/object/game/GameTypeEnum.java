package ink.ziip.championshipscore.api.object.game;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public enum GameTypeEnum {
    Bingo, ParkourTag, BattleBox, TNTRun, SnowballShowdown, SkyWars, TGTTOS, DragonEggCarnival, AdvancementCC, ParkourWarrior, HotyCodyDusky;

    @Override
    public String toString() {
        if (this == Bingo)
            return MessageConfig.GAME_BINGO;
        if (this == ParkourTag)
            return MessageConfig.GAME_PARKOUR_TAG;
        if (this == BattleBox)
            return MessageConfig.GAME_BATTLE_BOX;
        if (this == TNTRun)
            return MessageConfig.GAME_TNT_RUN;
        if (this == SnowballShowdown)
            return MessageConfig.GAME_SNOWBALL_SNOW_DOWN;
        if (this == SkyWars)
            return MessageConfig.GAME_SKY_WARS;
        if (this == TGTTOS)
            return MessageConfig.GAME_TGTTOS;
        if (this == DragonEggCarnival)
            return MessageConfig.GAME_DRAGON_EGG_CARNIVAL;
        if (this == AdvancementCC)
            return MessageConfig.GAME_ADVANCEMENT_CC;
        if (this == ParkourWarrior)
            return MessageConfig.PARKOUR_WARRIOR;
        if (this == HotyCodyDusky)
            return MessageConfig.GAME_HOTY_CODY_DUSKY;

        return "Unknown";
    }

    public String toAPIString() {
        if (this == Bingo)
            return "bingo";
        if (this == ParkourTag)
            return "parkour_chase";
        if (this == BattleBox)
            return "battle_box";
        if (this == TNTRun)
            return "tntrun";
        if (this == SnowballShowdown)
            return MessageConfig.GAME_SNOWBALL_SNOW_DOWN;
        if (this == SkyWars)
            return "skywars";
        if (this == TGTTOS)
            return MessageConfig.GAME_TGTTOS;
        if (this == DragonEggCarnival)
            return MessageConfig.GAME_DRAGON_EGG_CARNIVAL;
        if (this == AdvancementCC)
            return MessageConfig.GAME_ADVANCEMENT_CC;
        if (this == ParkourWarrior)
            return "runaway_warrior";
        if (this == HotyCodyDusky)
            return "hot_cod";

        return "Unknown";
    }
}
