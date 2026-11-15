package ink.ziip.championshipscore.api.game.model;

import ink.ziip.championshipscore.configuration.config.message.GameMessages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum GameTypeEnum {
    Bingo,
    ParkourTag,
    BattleBox,
    TNTRun,
    LaserShowdown,
    SkyWars,
    TGTTOS,
    DragonEggCarnival,
    ParkourWarrior,
    HotyCodyDusky,
    BuildMart,
    Dodgebolt,
    AceRace,
    RiptideRush,
    FrostbiteFrenzy,
    LaserBox,
    SulfurSoccer;

    /** Canonical command token shared by command parsing and tab completion. */
    public @NotNull String commandName() {
        return switch (this) {
            case LaserShowdown -> "lasershowdown";
            case RiptideRush -> "riptide";
            case FrostbiteFrenzy -> "frostbite";
            default -> name().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Parses canonical command tokens as well as enum names, ignoring dashes and underscores.
     * Keeping this here prevents each command from maintaining a slightly different game-name map.
     */
    public static @Nullable GameTypeEnum fromCommand(@NotNull String raw) {
        String normalized = normalize(raw);
        for (GameTypeEnum game : values()) {
            if (normalize(game.commandName()).equals(normalized)
                    || normalize(game.name()).equals(normalized)) return game;
        }
        return null;
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    @Override
    public String toString() {
        return switch (this) {
            case Bingo -> GameMessages.GAME_BINGO;
            case ParkourTag -> GameMessages.GAME_PARKOUR_TAG;
            case BattleBox -> GameMessages.GAME_BATTLE_BOX;
            case TNTRun -> GameMessages.GAME_TNT_RUN;
            case LaserShowdown -> GameMessages.GAME_LASER_SHOWDOWN;
            case SkyWars -> GameMessages.GAME_SKY_WARS;
            case TGTTOS -> GameMessages.GAME_TGTTOS;
            case DragonEggCarnival -> GameMessages.GAME_DRAGON_EGG_CARNIVAL;
            case ParkourWarrior -> GameMessages.PARKOUR_WARRIOR;
            case HotyCodyDusky -> GameMessages.GAME_HOTY_CODY_DUSKY;
            case BuildMart -> GameMessages.GAME_BUILD_MART;
            case Dodgebolt -> GameMessages.GAME_DODGEBOLT;
            case AceRace -> GameMessages.GAME_ACE_RACE;
            case RiptideRush -> GameMessages.GAME_RIPTIDE_RUSH;
            case FrostbiteFrenzy -> GameMessages.GAME_FROSTBITE;
            case LaserBox -> GameMessages.GAME_LASER_BOX;
            case SulfurSoccer -> GameMessages.GAME_SULFUR_SOCCER;
        };
    }
}
