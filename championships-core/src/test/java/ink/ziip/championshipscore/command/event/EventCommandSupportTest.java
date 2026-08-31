package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventCommandSupportTest {
    @BeforeEach
    void configureTeamSize() {
        CCConfig.TEAM_MAX_MEMBERS = 4;
    }

    @Test
    void validatesFixedWoolColorAndMembers() {
        EventTeamImport imported = new EventTeamImport(
                event("第四届夏季联合锦标赛"),
                List.of(new EventTeamImport.Team("红队", "red", "#B02E26", List.of(
                        new EventTeamImport.Member("PlayerOne", UUID.randomUUID().toString()),
                        new EventTeamImport.Member("PlayerTwo", UUID.randomUUID().toString())))));

        var teams = EventCommandSupport.validateImport(imported);
        assertEquals("red", teams.getFirst().colorName());
        assertEquals(2, teams.getFirst().members().size());
    }

    @Test
    void rejectsAChangedColorCode() {
        EventTeamImport imported = new EventTeamImport(
                event("赛事"),
                List.of(new EventTeamImport.Team("红队", "red", "#FFFFFF", List.of(
                        new EventTeamImport.Member("PlayerOne", UUID.randomUUID().toString())))));

        assertThrows(IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsDuplicatePlayersAcrossTeams() {
        String repeated = UUID.randomUUID().toString();
        EventTeamImport imported = new EventTeamImport(
                event("赛事"),
                List.of(
                        new EventTeamImport.Team("红队", "red", "#B02E26", List.of(new EventTeamImport.Member("PlayerOne", repeated))),
                        new EventTeamImport.Team("蓝队", "blue", "#3C44AA", List.of(new EventTeamImport.Member("PlayerTwo", repeated)))));

        assertThrows(IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsAnImportThatIsNotReady() {
        EventTeamImport.Event event = new EventTeamImport.Event(UUID.randomUUID().toString(), "s4cc", "赛事",
                "TEAMING", List.of(new EventTeamImport.Game("Bingo", "default", "宾果")), List.of(1D));
        EventTeamImport imported = new EventTeamImport(event, List.of(new EventTeamImport.Team(
                "红队", "red", "#B02E26",
                List.of(new EventTeamImport.Member("PlayerOne", UUID.randomUUID().toString())))));

        assertThrows(IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsMissingRoundMultipliers() {
        EventTeamImport.Event event = new EventTeamImport.Event(UUID.randomUUID().toString(), "s4cc", "赛事",
                "READY", List.of(new EventTeamImport.Game("Bingo", "default", "宾果"),
                new EventTeamImport.Game("BuildMart", "default", "建造市场")), List.of(1D));
        EventTeamImport imported = new EventTeamImport(event, List.of(new EventTeamImport.Team(
                "红队", "red", "#B02E26",
                List.of(new EventTeamImport.Member("PlayerOne", UUID.randomUUID().toString())))));

        assertThrows(IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    private static EventTeamImport.Event event(String title) {
        return new EventTeamImport.Event(UUID.randomUUID().toString(), "s4cc", title, "READY",
                List.of(new EventTeamImport.Game("Bingo", "default", "宾果")), List.of(1D));
    }
}
