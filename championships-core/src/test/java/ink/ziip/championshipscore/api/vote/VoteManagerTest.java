package ink.ziip.championshipscore.api.vote;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.RankManager;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

class VoteManagerTest {
    @Test
    void allEnabledScoringGamesRemainVisibleWithoutPublishedMaps() throws Exception {
        var fixture = new Fixture();
        var games =
                List.of(GameTypeEnum.values()).stream().filter(fixture.vote::isBallotGame).toList();
        assertEquals(14, games.size());
        assertTrue(
                games.containsAll(
                        List.of(
                                GameTypeEnum.AceRace,
                                GameTypeEnum.RiptideRush,
                                GameTypeEnum.FrostbiteFrenzy,
                                GameTypeEnum.LaserBox)));
        assertTrue(games.stream().noneMatch(FinaleGameRegistry::isRegistered));
        assertTrue(games.stream().noneMatch(fixture.vote::canVoteFor));
        assertFalse(fixture.vote.isBallotGame(null));
    }

    @Test
    void playedGamesStayVisibleAndDisabledGamesStayExcluded() throws Exception {
        var fixture = new Fixture();
        fixture.rank.played = GameTypeEnum.RiptideRush;
        fixture.games.enabled.remove(GameTypeEnum.BattleBox);
        assertTrue(fixture.vote.isBallotGame(GameTypeEnum.RiptideRush));
        assertFalse(fixture.vote.canVoteFor(GameTypeEnum.RiptideRush));
        assertFalse(fixture.vote.isBallotGame(GameTypeEnum.BattleBox));
    }

    private static final class Fixture {
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final EnabledGames games = allocate(EnabledGames.class);
        final PlayedRank rank = allocate(PlayedRank.class);
        final VoteManager vote = allocate(VoteManager.class);

        Fixture() throws Exception {
            games.enabled = EnumSet.allOf(GameTypeEnum.class);
            set(plugin, "gameManager", games);
            set(vote, "plugin", plugin);
            set(vote, "rankManager", rank);
        }
    }

    private static class EnabledGames extends GameManager {
        EnumSet<GameTypeEnum> enabled;

        EnabledGames() {
            super(null);
        }

        @Override
        public boolean isGameEnabled(GameTypeEnum game) {
            return enabled.contains(game);
        }

        @Override
        public BaseGameInstanceManager<? extends BaseGameInstance> getAreaManager(
                GameTypeEnum game) {
            return null;
        }
    }

    private static class PlayedRank extends RankManager {
        GameTypeEnum played;

        PlayedRank() {
            super(null);
        }

        @Override
        public int getGameOrder(GameTypeEnum game) {
            return game == played ? 1 : -1;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
