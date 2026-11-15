package ink.ziip.championshipscore.command.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.EventMessages;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@ExtendWith(ConfigurationStateExtension.class)
class EventCommandSupportTest {
    private static final EventTeamImport.Game BINGO =
            new EventTeamImport.Game("Bingo", "default", "宾果");

    @BeforeAll
    static void stubValidationReminders() {
        EventMessages.EVENT_IMPORT_TEAM_COLOR_DUPLICATE = "队伍颜色重复：%color%。";
        EventMessages.EVENT_IMPORT_TEAM_COLOR_FIXED = "队伍颜色不是固定羊毛色：%color%。";
        EventMessages.EVENT_IMPORT_PLAYER_DUPLICATE = "玩家在阵容中重复：%player%。";
        EventMessages.EVENT_IMPORT_TEAM_SIZE_INVALID = "队伍人数不符合服务器限制：%team%，必须在%min%到%max%之间。";
    }

    @BeforeEach
    void configureTeamSize() {
        CCConfig.TEAM_MAX_MEMBERS = 4;
        CCConfig.TEAM_MAX_EVENT_ROSTER_MEMBERS = 16;
    }

    @Test
    void validatesFixedWoolColorAndMembers() {
        var teams =
                EventCommandSupport.validateImport(
                        new EventTeamImport(
                                event("READY", List.of(BINGO), List.of(1D)),
                                List.of(red(member("PlayerOne"), member("PlayerTwo")))));
        assertEquals("red", teams.getFirst().colorName());
        assertEquals(2, teams.getFirst().members().size());
    }

    @Test
    void acceptsEventRosterSubstitutesBeyondNormalTeamLimit() {
        List<EventTeamImport.Member> members = new ArrayList<>();
        for (int index = 0; index < 5; index++)
            members.add(
                    new EventTeamImport.Member(
                            "Player" + index, UUID.randomUUID().toString(), index == 4));
        var teams =
                EventCommandSupport.validateImport(
                        new EventTeamImport(
                                event("READY", List.of(BINGO), List.of(1D)),
                                List.of(
                                        new EventTeamImport.Team(
                                                "红队", "red", "#B02E26", members))));
        assertEquals(5, teams.getFirst().members().size());
        assertTrue(teams.getFirst().members().get(4).isSubstitute());
    }

    static Stream<Named<EventTeamImport>> acceptedImports() {
        return Stream.of(
                // The server owns runtime state, so an import that is not READY is still valid.
                Named.of(
                        "not ready",
                        new EventTeamImport(
                                event("TEAMING", List.of(BINGO), List.of(1D)),
                                List.of(red(member("PlayerOne"))))),
                Named.of(
                        "finale without an extra multiplier",
                        new EventTeamImport(
                                event(
                                        "READY",
                                        List.of(
                                                BINGO,
                                                new EventTeamImport.Game(
                                                        "Dodgebolt", "s3cc", "Dodgebolt")),
                                        List.of(1D)),
                                List.of(red(member("PlayerOne"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedImports")
    void acceptsImport(EventTeamImport imported) {
        assertEquals(1, EventCommandSupport.validateImport(imported).size());
    }

    static Stream<Named<EventTeamImport>> rejectedImports() {
        String repeated = UUID.randomUUID().toString();
        return Stream.of(
                Named.of(
                        "changed color code",
                        new EventTeamImport(
                                event("READY", List.of(BINGO), List.of(1D)),
                                List.of(
                                        new EventTeamImport.Team(
                                                "红队",
                                                "red",
                                                "#FFFFFF",
                                                List.of(member("PlayerOne")))))),
                Named.of(
                        "duplicate player across teams",
                        new EventTeamImport(
                                event("READY", List.of(BINGO), List.of(1D)),
                                List.of(
                                        red(new EventTeamImport.Member("PlayerOne", repeated)),
                                        new EventTeamImport.Team(
                                                "蓝队",
                                                "blue",
                                                "#3C44AA",
                                                List.of(
                                                        new EventTeamImport.Member(
                                                                "PlayerTwo", repeated)))))),
                Named.of(
                        "missing round multiplier",
                        new EventTeamImport(
                                event(
                                        "READY",
                                        List.of(
                                                BINGO,
                                                new EventTeamImport.Game(
                                                        "BuildMart", "default", "建造市场")),
                                        List.of(1D)),
                                List.of(red(member("PlayerOne"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedImports")
    void rejectsInvalidImport(EventTeamImport imported) {
        assertThrows(
                IllegalArgumentException.class, () -> EventCommandSupport.validateImport(imported));
    }

    private static EventTeamImport.Event event(
            String status, List<EventTeamImport.Game> games, List<Double> multipliers) {
        return new EventTeamImport.Event(
                UUID.randomUUID().toString(), "s4cc", "赛事", status, games, multipliers);
    }

    private static EventTeamImport.Member member(String name) {
        return new EventTeamImport.Member(name, UUID.randomUUID().toString());
    }

    private static EventTeamImport.Team red(EventTeamImport.Member... members) {
        return new EventTeamImport.Team("红队", "red", "#B02E26", List.of(members));
    }
}
