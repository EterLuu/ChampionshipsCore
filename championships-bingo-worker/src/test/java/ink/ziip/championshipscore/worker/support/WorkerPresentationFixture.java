package ink.ziip.championshipscore.worker.support;

import ink.ziip.championshipscore.protocol.*;

import java.util.*;

/** Frozen DAILY manifest with two single-player teams and one spectator for presentation tests. */
public final class WorkerPresentationFixture {
    private WorkerPresentationFixture() {}

    public static MatchManifest manifest(
            int width, BingoRemix remix, Map<String, String> overrides) {
        var messages = new HashMap<String, String>();
        messages.put("game.name", "Bingo");
        messages.put("board.title", "Legacy title");
        messages.put("board.separator", "---");
        messages.put("board.current_game", "Game {0}");
        messages.put("board.current_status", "State {0}");
        messages.put("board.teams_header", "Teams");
        messages.put("board.team_score", "{0}:{1}:{2}:{3}/{4}");
        messages.put("board.own_team_score", "Own {0}:{1}:{2}:{3}/{4}");
        messages.put("board.footer", "Footer");
        messages.put("card.map_name", "Card {0}");
        messages.put("card.map_hint", "Open card");
        messages.put("card.spectator_map_name", "All teams");
        for (String state :
                List.of(
                        "preparation",
                        "countdown",
                        "progress",
                        "stopping",
                        "end",
                        "loading",
                        "waiting")) messages.put("sidebar.status." + state, state);
        messages.putAll(overrides);
        UUID one = UUID.nameUUIDFromBytes(new byte[] {1}),
                two = UUID.nameUUIDFromBytes(new byte[] {2});
        var teams =
                List.of(
                        new TeamSnapshot(1, "Team1", "RED", "#ff5555", List.of(one)),
                        new TeamSnapshot(2, "Team2", "BLUE", "#5555ff", List.of(two)));
        var players =
                List.of(
                        new PlayerSnapshot(one, "One", ParticipantRole.PLAYER, 1),
                        new PlayerSnapshot(two, "Two", ParticipantRole.PLAYER, 2),
                        new PlayerSnapshot(
                                UUID.nameUUIDFromBytes(new byte[] {3}),
                                "Viewer",
                                ParticipantRole.SPECTATOR,
                                null));
        var tasks = new ArrayList<BingoTaskSpec>();
        for (int i = 0; i < width * width; i++)
            tasks.add(new BingoTaskSpec(i, "stone" + i, "item", Map.of("material", "STONE")));
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                UUID.randomUUID(),
                1,
                1,
                "worker",
                MatchRunMode.DAILY,
                60,
                1,
                "config",
                new BingoScoringRules(
                        width,
                        List.of(1),
                        0,
                        0,
                        0,
                        new BingoVariantRules(BingoMode.POINTS, BingoDifficulty.NORMAL, 1, remix)),
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages)),
                tasks,
                teams,
                players);
    }
}
