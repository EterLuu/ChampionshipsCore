package ink.ziip.championshipscore.api.visibility;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVisibilityPolicyTest {
    private final UUID viewer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void spectatorAndUnjoinedOverrideEveryRestriction() {
        PlayerVisibilityState self = PlayerVisibilityState.self("test", "restricted");

        assertTrue(allows(self, true, false, null, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void teammatesModeOnlyAllowsSameTeam() {
        PlayerVisibilityState teammates = PlayerVisibilityState.teammates("test", "team only");

        assertTrue(allows(teammates, false, true, 1, null, null));
        assertFalse(allows(teammates, false, false, 2, null, null));
    }

    @Test
    void participantCannotSeeCorrespondingSpectator() {
        PlayerVisibilityState all = PlayerVisibilityState.all("test", "all");

        assertFalse(PlayerVisibilityPolicy.allows(all, viewer, target, false, true,
                true, 1, null, null));
        assertTrue(PlayerVisibilityPolicy.allows(all, viewer, target, true, true,
                true, 1, null, null));
    }

    @Test
    void explicitTeamAndPlayerSetsAreApplied() {
        assertTrue(allows(PlayerVisibilityState.teams(Set.of(2, 3), "test", "teams"),
                false, false, 2, null, null));
        assertFalse(allows(PlayerVisibilityState.teams(Set.of(2, 3), "test", "teams"),
                false, false, 4, null, null));
        assertTrue(allows(PlayerVisibilityState.players(Set.of(target), "test", "players"),
                false, false, null, null, null));
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
        assertTrue(PlayerVisibilityPolicy.allows(self, viewer, viewer, false,
                false, false, null, null, null));
    }

    private boolean allows(PlayerVisibilityState state, boolean forcedAll, boolean sameTeam,
                           Integer targetTeam, UUID viewerSession, UUID targetSession) {
        return PlayerVisibilityPolicy.allows(state, viewer, target, forcedAll, false, sameTeam,
                targetTeam, viewerSession, targetSession);
    }
}
