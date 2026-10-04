package ink.ziip.championshipscore.api.game.frostbite.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

class FrostbiteScoringTest {
    private static final List<Integer> RANK_POINTS =
            List.of(60, 55, 50, 45, 40, 35, 30, 25, 20, 15, 10);

    private List<List<UUID>> teams(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        team ->
                                IntStream.range(0, 4)
                                        .mapToObj(member -> new UUID(team + 1, member + 1))
                                        .toList())
                .toList();
    }

    private void kills(
            FrostbiteRound round, List<List<UUID>> teams, int team, int member, int count) {
        UUID killer = teams.get(team).get(member);
        UUID victim = teams.get((team + 1) % teams.size()).get(member);
        for (int kill = 0; kill < count; kill++)
            assertEquals(killer, round.instantKill(killer, victim, kill));
    }

    private void assertTeamReward(Map<UUID, Integer> rewards, List<UUID> team, int expected) {
        for (UUID member : team) assertEquals(expected, rewards.get(member));
    }

    @Test
    void ranksByAllFourMembersKillsAndAwardsEveryMemberIncludingThoseWithoutKills() {
        var teams = teams(4);
        var round = new FrostbiteRound(teams, 0);
        for (int member = 0; member < 4; member++) kills(round, teams, 0, member, 1);
        kills(round, teams, 1, 0, 3);
        kills(round, teams, 2, 0, 2);
        kills(round, teams, 3, 0, 1);

        var rewards = round.rankingRewards(RANK_POINTS);
        assertEquals(16, rewards.size());
        assertTeamReward(rewards, teams.get(0), 60);
        assertTeamReward(rewards, teams.get(1), 55);
        assertTeamReward(rewards, teams.get(2), 50);
        assertTeamReward(rewards, teams.get(3), 45);
    }

    @Test
    void paysSixtyThroughTenAndNothingFromTwelfthPlace() {
        var teams = teams(16);
        var round = new FrostbiteRound(teams, 0);
        for (int team = 0; team < 16; team++) kills(round, teams, team, 0, team);

        var rewards = round.rankingRewards(RANK_POINTS);
        int[] expected = {60, 55, 50, 45, 40, 35, 30, 25, 20, 15, 10, 0, 0, 0, 0, 0};
        for (int place = 0; place < expected.length; place++)
            assertTeamReward(rewards, teams.get(15 - place), expected[place]);
    }

    @Test
    void equalTeamKillsShareRewardsAndNextTeamSkipsTheOccupiedPlaces() {
        var teams = teams(4);
        var round = new FrostbiteRound(teams, 0);
        kills(round, teams, 0, 0, 2);
        kills(round, teams, 1, 0, 2);
        kills(round, teams, 2, 0, 1);

        var rewards = round.rankingRewards(RANK_POINTS);
        assertTeamReward(rewards, teams.get(0), 60);
        assertTeamReward(rewards, teams.get(1), 60);
        assertTeamReward(rewards, teams.get(2), 50);
        assertTeamReward(rewards, teams.get(3), 45);
    }

    @Test
    void teamsTiedForEleventhBothReceiveTenAndLowerTeamsReceiveNothing() {
        var teams = teams(16);
        var round = new FrostbiteRound(teams, 0);
        for (int team = 0; team < 16; team++)
            kills(round, teams, team, 0, team == 11 ? 6 : 16 - team);

        var rewards = round.rankingRewards(RANK_POINTS);
        assertTeamReward(rewards, teams.get(9), 15);
        assertTeamReward(rewards, teams.get(10), 10);
        assertTeamReward(rewards, teams.get(11), 10);
        for (int team = 12; team < 16; team++) assertTeamReward(rewards, teams.get(team), 0);
    }

    @Test
    void freezingWithoutAKillDoesNotImproveTeamRank() {
        var teams = teams(4);
        var round = new FrostbiteRound(teams, 0);
        kills(round, teams, 1, 0, 1);
        assertTrue(round.freeze(teams.get(0).get(1), teams.get(2).get(1), 0, 100));
        assertTrue(round.freeze(teams.get(0).get(2), teams.get(3).get(2), 0, 100));

        assertEquals(0, round.kills(teams.get(0).get(1)));
        var rewards = round.rankingRewards(RANK_POINTS);
        assertTeamReward(rewards, teams.get(1), 60);
        for (int team : new int[] {0, 2, 3}) assertTeamReward(rewards, teams.get(team), 55);
    }

    @Test
    void departingPlayersKeepTheirKillsAndRewardsAndNextRoundStartsItsOwnRanking() {
        var teams = teams(4);
        var round = new FrostbiteRound(teams, 0);
        kills(round, teams, 0, 0, 2);
        kills(round, teams, 1, 0, 1);
        UUID departed = teams.get(0).get(0);
        assertNull(round.leave(departed));
        assertFalse(round.active(departed));

        var rewards = round.rankingRewards(RANK_POINTS);
        assertTeamReward(rewards, teams.get(0), 60);
        assertTeamReward(rewards, teams.get(1), 55);
        assertEquals(60, rewards.get(departed));
        var nextRound = new FrostbiteRound(teams, 1);
        assertTrue(nextRound.kills().isEmpty());
        assertTrue(
                nextRound.rankingRewards(RANK_POINTS).values().stream()
                        .allMatch(points -> points == 60));
    }

    @Test
    void bundledConfigurationGivesSixPerKillPlusThePerPlayerRankReward() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream("frostbite/area.yml");
        assertNotNull(resource);
        YamlConfiguration configuration;
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            configuration = YamlConfiguration.loadConfiguration(reader);
        }
        assertEquals(6, configuration.getInt("points-per-kill"));
        assertEquals(RANK_POINTS, configuration.getIntegerList("rank-points-per-player"));

        var teams = teams(2);
        var round = new FrostbiteRound(teams, 0);
        kills(round, teams, 0, 0, 3);
        var rewards = round.rankingRewards(configuration.getIntegerList("rank-points-per-player"));
        UUID killer = teams.get(0).get(0);
        assertEquals(
                78,
                round.kills(killer) * configuration.getInt("points-per-kill")
                        + rewards.get(killer));
        assertEquals(60, rewards.get(teams.get(0).get(1)));
        assertTeamReward(rewards, teams.get(1), 55);
    }

    @Test
    void honorsTheConfiguredRewardTableWithoutExtendingItsLastReward() {
        var teams = teams(3);
        var round = new FrostbiteRound(teams, 0);
        kills(round, teams, 0, 0, 2);
        kills(round, teams, 1, 0, 1);

        var rewards = round.rankingRewards(List.of(12, 4));
        assertTeamReward(rewards, teams.get(0), 12);
        assertTeamReward(rewards, teams.get(1), 4);
        assertTeamReward(rewards, teams.get(2), 0);
    }
}
