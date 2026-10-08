package ink.ziip.championshipscore.api.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

class PlayerVisibilityPolicyTest {
    private final UUID viewer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void teammatesModeOnlyAllowsSameTeam() {
        PlayerVisibilityState teammates = PlayerVisibilityState.teammates("test", "team only");

        assertTrue(allows(teammates, false, true, 1, null, null));
        assertFalse(allows(teammates, false, false, 2, null, null));
    }

    @Test
    void participantCannotSeeCorrespondingSpectator() {
        PlayerVisibilityState all = PlayerVisibilityState.all("test", "all");

        assertFalse(
                PlayerVisibilityPolicy.allows(
                        all, viewer, target, false, true, true, 1, null, null));
        assertTrue(
                PlayerVisibilityPolicy.allows(
                        all, viewer, target, true, true, true, 1, null, null));
    }

    @Test
    void explicitTeamAndPlayerSetsAreApplied() {
        assertTrue(
                allows(
                        PlayerVisibilityState.teams(Set.of(2, 3), "test", "teams"),
                        false,
                        false,
                        2,
                        null,
                        null));
        assertFalse(
                allows(
                        PlayerVisibilityState.teams(Set.of(2, 3), "test", "teams"),
                        false,
                        false,
                        4,
                        null,
                        null));
        assertTrue(
                allows(
                        PlayerVisibilityState.players(Set.of(target), "test", "players"),
                        false,
                        false,
                        null,
                        null,
                        null));
    }

    @Test
    void differentDailySessionsRemainIsolatedForParticipants() {
        PlayerVisibilityState all = PlayerVisibilityState.all("test", "all");
        UUID firstSession = UUID.randomUUID();

        assertFalse(allows(all, false, false, null, firstSession, UUID.randomUUID()));
        assertTrue(allows(all, false, false, null, firstSession, firstSession));
    }

    @Test
    void selfIsAlwaysVisible() {
        PlayerVisibilityState self = PlayerVisibilityState.self("test", "self");
        assertTrue(
                PlayerVisibilityPolicy.allows(
                        self, viewer, viewer, false, false, false, null, null, null));
    }

    @Test
    void selfModeHidesBothTeammatesAndOpponentsButAllowsSpectatorsToWatch() {
        PlayerVisibilityState self = PlayerVisibilityState.self("riptiderush:test", "solo view");
        assertFalse(allows(self, false, true, 1, null, null));
        assertFalse(allows(self, false, false, 2, null, null));
        assertTrue(allows(self, true, false, 2, null, null));
    }

    @Test
    void spectatorRuleWinsAcrossEveryPolicyTeamAndSessionCombination() {
        UUID viewerSession = UUID.randomUUID(), targetSession = UUID.randomUUID();
        for (PlayerVisibilityState policy :
                java.util.List.of(
                        PlayerVisibilityState.all("test", "all"),
                        PlayerVisibilityState.self("test", "self"),
                        PlayerVisibilityState.teammates("test", "team"),
                        PlayerVisibilityState.players(Set.of(target), "test", "players"),
                        PlayerVisibilityState.teams(Set.of(1), "test", "teams"))) {
            for (boolean teammate : new boolean[] {false, true}) {
                assertTrue(allows(policy, true, teammate, 2, viewerSession, targetSession));
                for (boolean corresponding : new boolean[] {false, true}) {
                    assertTrue(
                            PlayerVisibilityPolicy.allows(
                                    policy,
                                    viewer,
                                    target,
                                    false,
                                    true,
                                    true,
                                    corresponding,
                                    teammate,
                                    2,
                                    viewerSession,
                                    targetSession));
                    assertTrue(
                            PlayerVisibilityPolicy.allows(
                                    policy,
                                    viewer,
                                    target,
                                    false,
                                    true,
                                    false,
                                    corresponding,
                                    teammate,
                                    2,
                                    viewerSession,
                                    targetSession));
                    assertFalse(
                            PlayerVisibilityPolicy.allows(
                                    policy,
                                    viewer,
                                    target,
                                    false,
                                    false,
                                    true,
                                    corresponding,
                                    teammate,
                                    1,
                                    null,
                                    null));
                }
            }
        }
    }

    private boolean allows(
            PlayerVisibilityState state,
            boolean forcedAll,
            boolean sameTeam,
            Integer targetTeam,
            UUID viewerSession,
            UUID targetSession) {
        return PlayerVisibilityPolicy.allows(
                state,
                viewer,
                target,
                forcedAll,
                false,
                sameTeam,
                targetTeam,
                viewerSession,
                targetSession);
    }
}
