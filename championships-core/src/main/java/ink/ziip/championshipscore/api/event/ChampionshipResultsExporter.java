package ink.ziip.championshipscore.api.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ChampionshipResultsExporter {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ChampionshipResultsExporter() {}

    public static @NotNull Path export(
            @NotNull Path dataDirectory,
            @NotNull EventStateStore.ActiveEvent event,
            @NotNull ChampionshipArchiveSnapshot snapshot,
            @Nullable String championTeamName)
            throws IOException {
        String eventSlug = event.slug();
        if (!eventSlug.matches("[a-z0-9][a-z0-9-]{1,31}"))
            throw new IllegalArgumentException("Invalid championship slug");
        Map<GameTypeEnum, EventStateStore.EventGame> games =
                event.games().stream()
                        .collect(
                                Collectors.toMap(
                                        EventStateStore.EventGame::type, Function.identity()));
        String champion =
                championTeamName == null
                        ? null
                        : snapshot.teams().stream()
                                .map(ChampionshipArchiveSnapshot.TeamScore::name)
                                .filter(name -> name.equalsIgnoreCase(championTeamName))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Champion team is not in the score table"));
        List<TeamResult> teams =
                snapshot.teams().stream()
                        .map(
                                team ->
                                        new TeamResult(
                                                team.name(),
                                                team.rank(),
                                                team.totalScore(),
                                                scores(team.gameScores(), games)))
                        .toList();
        List<PlayerResult> players =
                snapshot.players().stream()
                        .map(
                                player ->
                                        new PlayerResult(
                                                player.name(),
                                                player.uuid(),
                                                player.teamName(),
                                                player.totalScore(),
                                                player.isSubstitute(),
                                                scores(player.gameScores(), games)))
                        .toList();
        Results results = new Results(event.id(), event.slug(), champion, teams, players);
        Path exports = dataDirectory.resolve("exports");
        Files.createDirectories(exports);
        Path target = exports.resolve(eventSlug + "-results.json");
        Path temporary = Files.createTempFile(exports, eventSlug + "-results-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    GSON.toJson(results) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toAbsolutePath().normalize();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<GameResult> scores(
            List<ChampionshipArchiveSnapshot.GameScore> scores,
            Map<GameTypeEnum, EventStateStore.EventGame> games) {
        return scores.stream()
                .map(
                        score -> {
                            GameTypeEnum type = GameTypeEnum.valueOf(score.gameKey());
                            EventStateStore.EventGame game = games.get(type);
                            if (game == null || FinaleGameRegistry.isRegistered(type))
                                throw new IllegalArgumentException(
                                        "Scored game is not in the event: " + score.gameKey());
                            return new GameResult(
                                    type.name(),
                                    game.variantKey(),
                                    score.sortOrder(),
                                    score.score());
                        })
                .toList();
    }

    private record Results(
            String eventId,
            String eventSlug,
            String championTeamName,
            List<TeamResult> teams,
            List<PlayerResult> players) {}

    private record GameResult(String gameKey, String variantKey, int sortOrder, double score) {}

    private record TeamResult(
            String name, int rank, double totalScore, List<GameResult> gameScores) {}

    private record PlayerResult(
            String name,
            String uuid,
            String teamName,
            double totalScore,
            boolean isSubstitute,
            List<GameResult> gameScores) {}
}
