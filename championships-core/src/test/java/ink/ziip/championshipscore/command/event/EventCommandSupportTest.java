package ink.ziip.championshipscore.command.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

@ExtendWith(ConfigurationStateExtension.class)
class EventCommandSupportTest {
    @BeforeAll
    static void stubValidationReminders() {
        MessageConfig.EVENT_IMPORT_TEAM_COLOR_DUPLICATE = "队伍颜色重复：%color%。";
        MessageConfig.EVENT_IMPORT_TEAM_COLOR_FIXED = "队伍颜色不是固定羊毛色：%color%。";
        MessageConfig.EVENT_IMPORT_PLAYER_DUPLICATE = "玩家在阵容中重复：%player%。";
        MessageConfig.EVENT_IMPORT_TEAM_SIZE_INVALID = "队伍人数不符合服务器限制：%team%，必须在%min%到%max%之间。";
    }

    @BeforeEach
    void configureTeamSize() {
        CCConfig.TEAM_MAX_MEMBERS = 4;
    }

    @Test
    void validatesFixedWoolColorAndMembers() {
        EventTeamImport imported =
                new EventTeamImport(
                        event("CC"),
                        List.of(
                                new EventTeamImport.Team(
                                        "红队",
                                        "red",
                                        "#B02E26",
                                        List.of(
                                                new EventTeamImport.Member(
                                                        "PlayerOne", UUID.randomUUID().toString()),
                                                new EventTeamImport.Member(
                                                        "PlayerTwo",
                                                        UUID.randomUUID().toString())))));

        var teams = EventCommandSupport.validateImport(imported);
        assertEquals("red", teams.getFirst().colorName());
        assertEquals(2, teams.getFirst().members().size());
    }

    @Test
    void rejectsAChangedColorCode() {
        EventTeamImport imported =
                new EventTeamImport(
                        event("赛事"),
                        List.of(
                                new EventTeamImport.Team(
                                        "红队",
                                        "red",
                                        "#FFFFFF",
                                        List.of(
                                                new EventTeamImport.Member(
                                                        "PlayerOne",
                                                        UUID.randomUUID().toString())))));

        assertThrows(
                IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsDuplicatePlayersAcrossTeams() {
        String repeated = UUID.randomUUID().toString();
        EventTeamImport imported =
                new EventTeamImport(
                        event("赛事"),
                        List.of(
                                new EventTeamImport.Team(
                                        "红队",
                                        "red",
                                        "#B02E26",
                                        List.of(new EventTeamImport.Member("PlayerOne", repeated))),
                                new EventTeamImport.Team(
                                        "蓝队",
                                        "blue",
                                        "#3C44AA",
                                        List.of(
                                                new EventTeamImport.Member(
                                                        "PlayerTwo", repeated)))));

        assertThrows(
                IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsAnImportThatIsNotReady() {
        EventTeamImport.Event event =
                new EventTeamImport.Event(
                        UUID.randomUUID().toString(),
                        "s4cc",
                        "赛事",
                        "TEAMING",
                        List.of(new EventTeamImport.Game("Bingo", "default", "宾果")),
                        List.of(1D));
        EventTeamImport imported =
                new EventTeamImport(
                        event,
                        List.of(
                                new EventTeamImport.Team(
                                        "红队",
                                        "red",
                                        "#B02E26",
                                        List.of(
                                                new EventTeamImport.Member(
                                                        "PlayerOne",
                                                        UUID.randomUUID().toString())))));

        assertThrows(
                IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    @Test
    void rejectsMissingRoundMultipliers() {
        EventTeamImport.Event event =
                new EventTeamImport.Event(
                        UUID.randomUUID().toString(),
                        "s4cc",
                        "赛事",
                        "READY",
                        List.of(
                                new EventTeamImport.Game("Bingo", "default", "宾果"),
                                new EventTeamImport.Game("BuildMart", "default", "建造市场")),
                        List.of(1D));
        EventTeamImport imported =
                new EventTeamImport(
                        event,
                        List.of(
                                new EventTeamImport.Team(
                                        "红队",
                                        "red",
                                        "#B02E26",
                                        List.of(
                                                new EventTeamImport.Member(
                                                        "PlayerOne",
                                                        UUID.randomUUID().toString())))));

        assertThrows(
                IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    private static EventTeamImport.Event event(String title) {
        return new EventTeamImport.Event(
                UUID.randomUUID().toString(),
                "s4cc",
                title,
                "READY",
                List.of(new EventTeamImport.Game("Bingo", "default", "宾果")),
                List.of(1D));
    }

    @Test
    void importsAFinaleWithoutRequiringAnExtraScoringMultiplier() {
        int previous = CCConfig.TEAM_MAX_MEMBERS;
        try {
            CCConfig.TEAM_MAX_MEMBERS = 4;
            EventTeamImport imported =
                    new EventTeamImport(
                            new EventTeamImport.Event(
                                    "00000000-0000-4000-8000-000000000001",
                                    "s4cc",
                                    "Test",
                                    "READY",
                                    List.of(
                                            new EventTeamImport.Game("Bingo", "s3cc", "Bingo"),
                                            new EventTeamImport.Game(
                                                    "Dodgebolt", "s3cc", "Dodgebolt")),
                                    List.of(1D)),
                            List.of(
                                    new EventTeamImport.Team(
                                            "Red",
                                            "red",
                                            "#B02E26",
                                            List.of(
                                                    new EventTeamImport.Member(
                                                            "PlayerOne",
                                                            "00000000-0000-4000-8000-000000000002")))));
            assertEquals(1, EventCommandSupport.validateImport(imported).size());
        } finally {
            CCConfig.TEAM_MAX_MEMBERS = previous;
        }
    }
}
