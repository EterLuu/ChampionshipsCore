package ink.ziip.championshipscore.api.object.game;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum GameTypeEnum {
    Bingo, ParkourTag, BattleBox, TNTRun, SnowballShowdown, SkyWars, TGTTOS, DragonEggCarnival,
    ParkourWarrior, HotyCodyDusky, BuildMart, Dodgebolt, AceRace;

    /** Canonical command token shared by command parsing and tab completion. */
    public @NotNull String commandName() {
        return this == SnowballShowdown ? "snowball" : name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses canonical command tokens as well as enum names, ignoring dashes and underscores.
     * Keeping this here prevents each command from maintaining a slightly different game-name map.
     */
    public static @Nullable GameTypeEnum fromCommand(@NotNull String raw) {
        String normalized = normalize(raw);
        for (GameTypeEnum game : values()) {
            if (normalize(game.commandName()).equals(normalized) || normalize(game.name()).equals(normalized))
                return game;
        }
        return null;
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    @Override
    public String toString() {
        return switch (this) {
            case Bingo -> MessageConfig.GAME_BINGO;
            case ParkourTag -> MessageConfig.GAME_PARKOUR_TAG;
            case BattleBox -> MessageConfig.GAME_BATTLE_BOX;
            case TNTRun -> MessageConfig.GAME_TNT_RUN;
            case SnowballShowdown -> MessageConfig.GAME_SNOWBALL_SNOW_DOWN;
            case SkyWars -> MessageConfig.GAME_SKY_WARS;
            case TGTTOS -> MessageConfig.GAME_TGTTOS;
            case DragonEggCarnival -> MessageConfig.GAME_DRAGON_EGG_CARNIVAL;
            case ParkourWarrior -> MessageConfig.PARKOUR_WARRIOR;
            case HotyCodyDusky -> MessageConfig.GAME_HOTY_CODY_DUSKY;
            case BuildMart -> MessageConfig.GAME_BUILD_MART;
            case Dodgebolt -> MessageConfig.GAME_DODGEBOLT;
            case AceRace -> MessageConfig.GAME_ACE_RACE;
        };
    }
}
