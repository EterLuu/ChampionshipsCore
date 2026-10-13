package ink.ziip.championshipscore.api.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ExtendWith(ConfigurationStateExtension.class)
class DailyTeamAllocationTest {
    @org.junit.jupiter.api.BeforeAll
    static void configureTeamNames() {
        MessageConfig.DAILY_TEAM_NAMES =
                java.util.List.of(
                        "红", "绿", "蓝", "黄", "青", "紫", "橙", "白", "黄绿", "粉红", "淡蓝", "品红", "灰", "黑",
                        "棕", "浅灰");
        MessageConfig.DAILY_TEAM_SUFFIX = "队";
    }

    private static final DailyRules RULES = new DailyRules(2, 16, 4, 4, 5);
    private static final DailyRules SOLO_RULES = new DailyRules(1, 16, 4, 4, 60);

    @Test
    void doesNotCreateAnUnbalancedTwoVersusOneSoloMatch() {
        assertEquals(List.of(1, 1, 1), sizes(DailyManager.allocate(solos(3), RULES)));
        assertEquals(List.of(2, 2), sizes(DailyManager.allocate(solos(4), RULES)));
        assertEquals(List.of(2, 2, 2), sizes(DailyManager.allocate(solos(6), RULES)));
    }

    @Test
    void keepsPartyTogetherAndBalancesOtherGroupsAroundIt() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> party = Set.of(first, second);
        List<DailyQueue.Group> groups = new ArrayList<>();
        groups.add(group(party));
        groups.addAll(solos(3));

        List<Set<UUID>> teams = DailyManager.allocate(groups, RULES);
        assertEquals(List.of(1, 2, 2), sizes(teams));
        assertTrue(teams.stream().anyMatch(team -> team.containsAll(party)));
    }

    @Test
    void choosesBalancedTeamsWhenPartiesMakeThePreferredCountUneven() {
        List<DailyQueue.Group> groups = new ArrayList<>();
        groups.add(group(sizedPlayers(3)));
        groups.add(group(sizedPlayers(3)));

        List<Set<UUID>> teams = DailyManager.allocate(groups, RULES);
        assertEquals(List.of(3, 3), sizes(teams));
        assertTrue(
                teams.stream()
                        .anyMatch(
                                team ->
                                        team.size() == 3
                                                && groups.get(0).players().stream()
                                                        .allMatch(team::contains)));
        assertTrue(
                teams.stream()
                        .anyMatch(
                                team ->
                                        team.size() == 3
                                                && groups.get(1).players().stream()
                                                        .allMatch(team::contains)));
    }

    @Test
    void refusesToCreateAWinBearingMatchFromOnlyOneQueueGroup() {
        assertTrue(
                DailyManager.allocate(
                                List.of(group(Set.of(UUID.randomUUID(), UUID.randomUUID()))), RULES)
                        .isEmpty());
    }

    @Test
    void allowsSinglePlayerAllocationWhenRulesPermitSoloMatches() {
        assertEquals(
                List.of(1),
                sizes(
                        DailyManager.allocate(
                                List.of(group(Set.of(UUID.randomUUID()))), SOLO_RULES)));
    }

    private static final DailyRules LASER_RULES = new DailyRules(2, 16, 1, 16, 60);

    @Test
    void laserUsesEveryAvailableTeamBeforeAddingSecondPlayers() {
        for (int count = 2; count <= 16; count++) {
            List<DailyQueue.Group> groups = solos(count);
            List<Set<UUID>> teams =
                    DailyManager.allocate(groups, LASER_RULES, GameTypeEnum.LaserShowdown);
            assertEquals(Math.min(count, 16), teams.size(), "players=" + count);
            assertEquals(count, teams.stream().mapToInt(Set::size).sum());
            assertEquals(count, teams.stream().flatMap(Set::stream).distinct().count());
            assertTrue(teams.stream().allMatch(team -> !team.isEmpty() && team.size() == 1));
            assertTrue(
                    teams.stream().mapToInt(Set::size).max().orElseThrow()
                                    - teams.stream().mapToInt(Set::size).min().orElseThrow()
                            <= 1);
        }
    }

    @Test
    void laserSplitsOnePartyButQueueStillMovesAndRemovesItAsAWhole() {
        Set<UUID> party = sizedPlayers(4);
        DailyQueue queue = new DailyQueue(GameTypeEnum.LaserShowdown);
        assertTrue(queue.add(UUID.randomUUID(), party, LASER_RULES));
        assertTrue(DailyManager.hasEnoughQueueGroups(queue.game(), queue.groupCount()));
        List<DailyQueue.Group> selected = queue.take(16);
        assertEquals(
                List.of(1, 1, 1, 1),
                sizes(DailyManager.allocate(selected, LASER_RULES, queue.game())));
        assertEquals(0, queue.size());
        queue.restore(selected, LASER_RULES);
        assertEquals(party, queue.removePlayer(party.iterator().next()));
        assertEquals(0, queue.size());
    }

    @Test
    void laserSpreadsMixedPartiesWhileOtherGamesKeepTheirExistingRules() {
        List<DailyQueue.Group> groups = new ArrayList<>();
        groups.add(group(sizedPlayers(4)));
        groups.add(group(sizedPlayers(3)));
        groups.addAll(solos(9));
        assertEquals(
                16, DailyManager.allocate(groups, LASER_RULES, GameTypeEnum.LaserShowdown).size());
        assertEquals(
                List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                sizes(DailyManager.allocate(groups, LASER_RULES, GameTypeEnum.LaserShowdown)));
        assertEquals(
                List.of(2, 2), sizes(DailyManager.allocate(solos(4), RULES, GameTypeEnum.Bingo)));
        assertFalse(DailyManager.hasEnoughQueueGroups(GameTypeEnum.Bingo, 1));
        assertFalse(DailyManager.hasEnoughQueueGroups(GameTypeEnum.LaserShowdown, 0));
        assertTrue(
                DailyManager.allocate(solos(1), LASER_RULES, GameTypeEnum.LaserShowdown).isEmpty());
        assertTrue(
                DailyManager.allocate(solos(17), LASER_RULES, GameTypeEnum.LaserShowdown)
                        .isEmpty());
    }

    @Test
    void presentsDailyTeamsByMinecraftColorOnly() {
        assertEquals("青队", DailyManager.teamNameForColor("CYAN"));
        assertEquals("淡蓝队", DailyManager.teamNameForColor("LIGHT_BLUE"));
        assertEquals("蓝队", DailyManager.teamNameForColor("blue"));
    }

    private static List<DailyQueue.Group> solos(int count) {
        List<DailyQueue.Group> groups = new ArrayList<>();
        for (int index = 0; index < count; index++) groups.add(group(Set.of(UUID.randomUUID())));
        return groups;
    }

    private static DailyQueue.Group group(Set<UUID> players) {
        return new DailyQueue.Group(UUID.randomUUID(), new LinkedHashSet<>(players));
    }

    private static Set<UUID> sizedPlayers(int count) {
        Set<UUID> players = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) players.add(UUID.randomUUID());
        return players;
    }

    private static List<Integer> sizes(List<Set<UUID>> teams) {
        return teams.stream().map(Set::size).sorted(Comparator.naturalOrder()).toList();
    }
}
