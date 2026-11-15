package ink.ziip.championshipscore.integration.papi;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.runtime.BingoArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.ScoreMessages;
import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import ink.ziip.championshipscore.support.MessageConfigurations;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(ConfigurationStateExtension.class)
class BingoPlaceholderTextTest {
    @Test
    void configuredRankKeepsOrderingMissingRowsAndLiteralTeamTokens() throws Exception {
        var config = MessageConfigurations.defaults();
        config.loadFromConfiguration(new YamlConfiguration());
        var first = new ChampionshipTeam(1, "First%arg2%", "red", "&c", null) {};
        var second = new ChampionshipTeam(2, "Second", "blue", "&9", null) {};
        var round = TestObjects.allocate(BingoRound.class);
        TestObjects.set(round, "teams", List.of(second, first));
        UUID match = UUID.randomUUID(),
                firstPlayer = UUID.randomUUID(),
                secondPlayer = UUID.randomUUID();
        var scoring =
                new BingoScoringEngine(
                        match,
                        1,
                        new BingoScoringRules(3, List.of(45, 20), 0, 0, 0),
                        List.of(
                                new TeamSnapshot(
                                        1, first.getName(), "red", "#ff0000", List.of(firstPlayer)),
                                new TeamSnapshot(
                                        2,
                                        second.getName(),
                                        "blue",
                                        "#0000ff",
                                        List.of(secondPlayer))),
                        Map.of(firstPlayer, 1, secondPlayer, 2));
        scoring.apply(new CompletionObservation(match, 1, 1, 1, firstPlayer, 0, 1));
        scoring.apply(new CompletionObservation(match, 1, 2, 2, secondPlayer, 0, 2));
        TestObjects.set(round, "scoring", scoring);
        TestObjects.set(round, "scoringTeamIds", Map.of(first, 1, second, 2));
        var area = TestObjects.allocate(BingoArea.class);
        TestObjects.set(area, "round", round);
        var expansion = TestObjects.allocate(BingoPlaceholder.class);
        var rank =
                BingoPlaceholder.class.getDeclaredMethod("rankEntry", BingoArea.class, int.class);
        rank.setAccessible(true);
        assertEquals(first.getColoredName() + "&f: 45", rank.invoke(expansion, area, 0));
        assertEquals(second.getColoredName() + "&f: 20", rank.invoke(expansion, area, 1));
        assertEquals(ScoreMessages.PLACEHOLDER_NONE, rank.invoke(expansion, area, 2));
        assertEquals(ScoreMessages.PLACEHOLDER_NONE, rank.invoke(expansion, null, 0));
        var document = new YamlConfiguration();
        document.set("code-text.bingo-placeholder.rank-entry", "%arg2% / %arg1%");
        config.loadFromConfiguration(document);
        assertEquals("45 / " + first.getColoredName(), rank.invoke(expansion, area, 0));
        document.set("code-text.bingo-placeholder.rank-entry", "");
        config.loadFromConfiguration(document);
        assertEquals("", rank.invoke(expansion, area, 0));
        TestObjects.set(area, "round", null);
        assertEquals(ScoreMessages.PLACEHOLDER_NONE, rank.invoke(expansion, area, 0));
    }
}
