package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.SharedSidebar;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/** Viewer-specific sidebar composition from the frozen manifest and an authoritative result. */
final class WorkerSidebar {
    record Frame(Component title, List<Component> lines) {
        Frame {
            lines = List.copyOf(lines);
        }
    }

    private final MatchManifest manifest;
    private final Map<Integer, TeamSnapshot> teams;
    private final SharedSidebar sidebar;

    WorkerSidebar(MatchManifest manifest, Consumer<String> warning) {
        this.manifest = manifest;
        this.teams = manifest.teamsById();
        this.sidebar =
                new SharedSidebar(
                        "cc_bingo",
                        WorkerPresentationService.component(
                                manifest.runtimeRules()
                                        .presentation()
                                        .messages()
                                        .getOrDefault(
                                                "sidebar.title", plainMessage("board.title"))),
                        warning);
    }

    void show(Player player) {
        sidebar.show(player);
    }

    void hide(Player player) {
        sidebar.hide(player);
    }

    void render(Player player, PlayerSnapshot viewer, BingoResult result, MatchState state) {
        Frame frame = frame(viewer, result, state);
        sidebar.render(player, frame.title(), frame.lines());
    }

    Frame frame(PlayerSnapshot viewer, BingoResult result, MatchState currentState) {
        if (manifest.runtimeRules().presentation().messages().containsKey("sidebar.line-count")) {
            return configuredFrame(viewer, result, currentState);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(message("board.separator"));
        lines.add(message("board.current_game", "{0}", gameName()));
        lines.add(
                message(
                        "board.current_status",
                        "{0}",
                        WorkerPresentationService.sidebarStatus(
                                manifest.runtimeRules().presentation(), currentState)));
        lines.add(message("board.teams_header"));
        Integer viewerTeamId = viewer.role() == ParticipantRole.PLAYER ? viewer.teamId() : null;
        for (WorkerSidebarRanking.Entry entry :
                WorkerSidebarRanking.select(result, teams, viewerTeamId)) {
            TeamSnapshot team = entry.team();
            String key = entry.viewerTeam() ? "board.own_team_score" : "board.team_score";
            Component teamName = LegacyText.component(team.name(), teamColor(team));
            if (entry.viewerTeam()) teamName = teamName.decorate(TextDecoration.BOLD);
            Component row =
                    message(
                            key,
                            "{0}",
                            Integer.toString(entry.rank()),
                            "{2}",
                            Integer.toString(result.teamScores().getOrDefault(team.id(), 0)),
                            "{3}",
                            Integer.toString(result.completedCells().getOrDefault(team.id(), 0)),
                            "{4}",
                            Integer.toString(manifest.tasks().size()));
            Component finalTeamName = teamName;
            lines.add(
                    row.replaceText(
                            builder -> builder.matchLiteral("{1}").replacement(finalTeamName)));
        }
        lines.add(Component.empty());
        lines.add(message("board.footer"));
        return new Frame(message("board.title"), lines);
    }

    private Frame configuredFrame(
            PlayerSnapshot viewer, BingoResult result, MatchState currentState) {
        BingoPresentation presentation = manifest.runtimeRules().presentation();
        int count;
        try {
            count =
                    Integer.parseInt(
                            presentation.messages().getOrDefault("sidebar.line-count", "0"));
        } catch (NumberFormatException ignored) {
            count = 0;
        }
        String status = WorkerPresentationService.sidebarStatus(presentation, currentState);
        Integer viewerTeamId = viewer.role() == ParticipantRole.PLAYER ? viewer.teamId() : null;
        int viewerTasks =
                viewerTeamId == null ? 0 : result.completedCells().getOrDefault(viewerTeamId, 0);
        List<Component> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String raw = presentation.messages().getOrDefault("sidebar.line." + index, "");
            if ("{ranking}".equals(raw)) {
                appendConfiguredRanking(lines, result, viewerTeamId, presentation);
                continue;
            }
            raw = WorkerPresentationService.sidebarLine(raw, gameName(), status, viewerTasks);
            lines.add(WorkerPresentationService.component(raw));
        }
        if (lines.size() > 15) lines = new ArrayList<>(lines.subList(0, 15));
        return new Frame(
                WorkerPresentationService.component(
                        presentation
                                .messages()
                                .getOrDefault("sidebar.title", plainMessage("board.title"))),
                lines);
    }

    private void appendConfiguredRanking(
            List<Component> lines,
            BingoResult result,
            Integer viewerTeamId,
            BingoPresentation presentation) {
        String normal =
                presentation
                        .messages()
                        .getOrDefault(
                                "sidebar.ranking-line",
                                WorkerPresentationService.legacyText("sidebar.ranking-line"));
        String own = presentation.messages().getOrDefault("sidebar.own-ranking-line", normal);
        for (WorkerSidebarRanking.Entry entry :
                WorkerSidebarRanking.select(result, teams, viewerTeamId)) {
            TeamSnapshot team = entry.team();
            String raw =
                    (entry.viewerTeam() ? own : normal)
                            .replace("{rank.team-color}", team.colorCode())
                            .replace("{rank.position}", Integer.toString(entry.rank()))
                            .replace("{rank.team}", team.name())
                            .replace(
                                    "{rank.score}",
                                    Integer.toString(
                                            result.teamScores().getOrDefault(team.id(), 0)))
                            .replace(
                                    "{rank.tasks}",
                                    Integer.toString(
                                            result.completedCells().getOrDefault(team.id(), 0)));
            lines.add(WorkerPresentationService.component(raw));
        }
    }

    private String plainMessage(String key) {
        return manifest.runtimeRules().presentation().messages().getOrDefault(key, "");
    }

    private Component message(String key, String... replacements) {
        return WorkerPresentationService.message(
                manifest.runtimeRules().presentation(), key, replacements);
    }

    private String gameName() {
        return manifest.runtimeRules().presentation().message("game.name");
    }

    private static TextColor teamColor(TeamSnapshot team) {
        TextColor color = team == null ? null : TextColor.fromHexString(team.colorCode());
        return color == null ? NamedTextColor.WHITE : color;
    }
}
