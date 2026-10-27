package ink.ziip.championshipscore.api.game.bingo.game;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.bingo.card.*;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.junit.jupiter.api.Test;

import java.util.*;

class BingoCompletionScoreTest {
    @Test
    void aBatchKeepsEachClaimRankAndLineAwardInsteadOfReusingTheLastDelta() throws Exception {
        var round = allocate(BingoRound.class);
        UUID player = UUID.randomUUID();
        var team =
                new ChampionshipTeam(
                        1, "red", "red", "", Set.of(player, UUID.randomUUID()), null) {};
        List<GameTask> tasks = new ArrayList<>();
        for (int i = 0; i < 9; i++) tasks.add(new GameTask(null));
        set(round, "size", CardSize.X3);
        set(round, "variant", BingoVariantRules.FIXED_POINTS);
        set(round, "card", new BingoCard(CardSize.X3, tasks));
        set(round, "differentialTeamCards", Map.of());
        set(round, "itemPoints", new int[] {30, 20, 10});
        set(round, "lineBonus", 50);
        set(round, "lineBonusMinor", 25);
        set(round, "lineBonusMajorCount", 2);
        set(round, "scores", new HashMap<>());
        set(round, "awardedLines", new HashMap<>());
        var award =
                BingoRound.class.getDeclaredMethod(
                        "awardPoints", ChampionshipTeam.class, GameTask.class, int.class);
        award.setAccessible(true);
        var own =
                new GameTask.Completion(
                        player,
                        Component.text("p"),
                        NamedTextColor.RED,
                        BingoTeamAdapter.id(team),
                        1);
        var rival =
                new GameTask.Completion(
                        UUID.randomUUID(), Component.text("q"), NamedTextColor.BLUE, "rival", 0);
        tasks.get(0).complete(own, false);
        tasks.get(1).complete(own, false);
        tasks.get(2).complete(own, false);
        var first = (BingoRound.CompletionResult) award.invoke(round, team, tasks.get(2), 2);
        tasks.get(3).complete(rival, false);
        tasks.get(3).complete(own, false);
        var second = (BingoRound.CompletionResult) award.invoke(round, team, tasks.get(3), 3);
        assertEquals(30, first.cellDelta());
        assertEquals(50, first.lineDelta());
        assertEquals(20, second.cellDelta());
        assertEquals(0, second.lineDelta());
        assertEquals(150, round.score(team));
        assertSame(tasks.get(2), first.task());
        assertSame(tasks.get(3), second.task());
    }
}
