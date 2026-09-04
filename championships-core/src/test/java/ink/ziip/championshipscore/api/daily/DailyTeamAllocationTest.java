package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyTeamAllocationTest {
    @org.junit.jupiter.api.BeforeAll
    static void configureTeamNames() {
        MessageConfig.DAILY_TEAM_NAMES = java.util.List.of(
                "红", "绿", "蓝", "黄", "青", "紫", "橙", "白",
                "黄绿", "粉红", "淡蓝", "品红", "灰", "黑", "棕", "浅灰");
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
        assertTrue(teams.stream().anyMatch(team -> team.size() == 3
                && groups.get(0).players().stream().allMatch(team::contains)));
        assertTrue(teams.stream().anyMatch(team -> team.size() == 3
                && groups.get(1).players().stream().allMatch(team::contains)));
    }

    @Test
    void refusesToCreateAWinBearingMatchFromOnlyOneQueueGroup() {
        assertTrue(DailyManager.allocate(List.of(group(Set.of(UUID.randomUUID(), UUID.randomUUID()))), RULES)
                .isEmpty());
    }

    @Test
    void allowsSinglePlayerAllocationWhenRulesPermitSoloMatches() {
        assertEquals(List.of(1), sizes(DailyManager.allocate(
                List.of(group(Set.of(UUID.randomUUID()))), SOLO_RULES)));
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
