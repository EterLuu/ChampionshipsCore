package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.util.List;

class GameStartRulesTest {
    @Test
    void everyGameHasAStartTopologyIncludingAllFinals() {
        for (GameTypeEnum game : GameTypeEnum.values())
            assertNotNull(GameStartRules.topology(game));
        for (GameTypeEnum game :
                List.of(GameTypeEnum.BattleBox, GameTypeEnum.ParkourTag, GameTypeEnum.LaserBox)) {
            GameStartRules.validateTeamCount(game, 6);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GameStartRules.validateTeamCount(game, 3));
        }
        for (GameTypeEnum game :
                List.of(
                        GameTypeEnum.Dodgebolt,
                        GameTypeEnum.SulfurSoccer,
                        GameTypeEnum.DragonEggCarnival)) {
            GameStartRules.validateTeamCount(game, 2);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GameStartRules.validateTeamCount(game, 4));
        }
    }
}
