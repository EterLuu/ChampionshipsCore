package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.gui.BingoCardMenu;
import ink.ziip.championshipscore.platform.bukkit.gui.BingoTaskItems;
import ink.ziip.championshipscore.platform.bukkit.gui.PlayerListMenu;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import ink.ziip.championshipscore.shared.presentation.BingoCardPresentation;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;
import ink.ziip.championshipscore.shared.presentation.PlayerListPresentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only worker UI rendered exclusively from the immutable match manifest and replay state. */
final class WorkerMenuService {
    private WorkerMenuService() {}

    static void openCard(
            Player player,
            MatchManifest manifest,
            Set<Integer> completedByViewer,
            Map<Integer, List<Integer>> completions) {
        openCard(
                player,
                manifest,
                manifest.tasks(),
                completedByViewer,
                completions,
                Set.of(),
                Set.of(),
                null,
                false);
    }

    static void openCard(
            Player player,
            MatchManifest manifest,
            List<BingoTaskSpec> tasks,
            Set<Integer> completedByViewer,
            Map<Integer, List<Integer>> completions,
            Set<Integer> hidden,
            Set<Integer> locked,
            int[] displayOrder,
            boolean neutralView) {
        openCard(
                player,
                manifest,
                tasks,
                completedByViewer,
                completions,
                hidden,
                locked,
                displayOrder,
                neutralView,
                WorkerPresentationService.message(
                        manifest.runtimeRules().presentation(), "card.title"));
    }

    static void openCard(
            Player player,
            MatchManifest manifest,
            List<BingoTaskSpec> tasks,
            Set<Integer> completedByViewer,
            Map<Integer, List<Integer>> completions,
            Set<Integer> hidden,
            Set<Integer> locked,
            int[] displayOrder,
            boolean neutralView,
            Component title) {
        openCard(
                player,
                manifest,
                tasks,
                completedByViewer,
                completions,
                hidden,
                locked,
                displayOrder,
                neutralView,
                title,
                Map.of(),
                null);
    }

    static void openCard(
            Player player,
            MatchManifest manifest,
            List<BingoTaskSpec> tasks,
            Set<Integer> completedByViewer,
            Map<Integer, List<Integer>> completions,
            Set<Integer> hidden,
            Set<Integer> locked,
            int[] displayOrder,
            boolean neutralView,
            Component title,
            Map<Integer, List<WorkerTaskCompletion>> details,
            Integer viewerTeamId) {
        var taskTheme = BingoTaskPresentation.from(manifest.runtimeRules().presentation());
        int width = manifest.scoring().cardWidth();
        var presentation = manifest.runtimeRules().presentation();
        var indexed =
                tasks.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        BingoTaskSpec::cellIndex,
                                        java.util.function.Function.identity()));
        List<ItemStack> items = new ArrayList<>();
        for (int displaySlot = 0; displaySlot < tasks.size(); displaySlot++) {
            int trueIndex = displayOrder == null ? displaySlot : displayOrder[displaySlot];
            BingoTaskSpec task = indexed.get(trueIndex);
            if (task == null)
                throw new IllegalArgumentException("Missing displayed Bingo task: " + trueIndex);
            items.add(
                    taskItem(
                            task,
                            manifest,
                            completedByViewer.contains(trueIndex),
                            neutralView,
                            hidden.contains(trueIndex),
                            locked.contains(trueIndex),
                            completions.getOrDefault(trueIndex, List.of()),
                            details.getOrDefault(trueIndex, List.of()),
                            viewerTeamId,
                            taskTheme));
        }
        BingoCardMenu.open(
                player,
                BingoCardPresentation.from(presentation),
                width,
                Map.of(
                        "%title%",
                        presentation
                                        .messages()
                                        .containsKey(BingoCardPresentation.PREFIX + "version")
                                ? LegacyText.serialize(title)
                                : presentation.message("card.title"),
                        "%card-title%",
                        presentation.message("card.title"),
                        "%win-hint%",
                        presentation.message("card.win_hint")),
                items);
    }

    static void openTeammates(
            Player player,
            MatchManifest manifest,
            Object owner,
            java.util.function.BooleanSupplier admitted,
            java.util.function.Supplier<List<PlayerSnapshot>> participants,
            java.util.function.Consumer<UUID> select) {
        var presentation = manifest.runtimeRules().presentation();
        var source = participants.get();
        if (source.isEmpty()) {
            player.sendMessage(
                    WorkerPresentationService.message(presentation, "compass.no_teammates"));
            return;
        }
        PlayerListMenu.open(
                player,
                owner,
                PlayerListPresentation.teammates(presentation),
                presentation
                                .messages()
                                .containsKey(PlayerListPresentation.TEAMMATE_PREFIX + "version")
                        ? Map.of()
                        : Map.of("%prefix%", presentation.messages().getOrDefault("prefix", "")),
                admitted,
                () ->
                        participants.get().stream()
                                .sorted(
                                        java.util.Comparator.comparing(
                                                PlayerSnapshot::username,
                                                String.CASE_INSENSITIVE_ORDER))
                                .map(
                                        target ->
                                                new PlayerListMenu.Target(
                                                        target.uuid(),
                                                        Map.of(
                                                                "%player%",
                                                                target.username(),
                                                                "%formatted-player%",
                                                                LegacyText.serialize(
                                                                        Component.text(
                                                                                target.username(),
                                                                                teamColor(
                                                                                        manifest.teamsById()
                                                                                                .get(
                                                                                                        target
                                                                                                                .teamId())))))))
                                .toList(),
                select);
    }

    static void openSpectatorTargets(
            Player player,
            MatchManifest manifest,
            Object owner,
            java.util.function.BooleanSupplier admitted,
            java.util.function.Supplier<List<PlayerSnapshot>> participants,
            java.util.function.Consumer<UUID> select) {
        var presentation = manifest.runtimeRules().presentation();
        PlayerListMenu.open(
                player,
                owner,
                PlayerListPresentation.spectator(presentation),
                presentation
                                .messages()
                                .containsKey(PlayerListPresentation.SPECTATOR_PREFIX + "version")
                        ? Map.of()
                        : Map.of("%prefix%", presentation.messages().getOrDefault("prefix", "")),
                admitted,
                () ->
                        participants.get().stream()
                                .filter(candidate -> !candidate.uuid().equals(player.getUniqueId()))
                                .filter(
                                        candidate -> {
                                            Player online = Bukkit.getPlayer(candidate.uuid());
                                            return online != null && online.isOnline();
                                        })
                                .sorted(
                                        java.util.Comparator.comparing(
                                                PlayerSnapshot::username,
                                                String.CASE_INSENSITIVE_ORDER))
                                .map(
                                        target -> {
                                            TeamSnapshot team =
                                                    target.teamId() == null
                                                            ? null
                                                            : manifest.teamsById()
                                                                    .get(target.teamId());
                                            Component label =
                                                    team == null
                                                            ? WorkerPresentationService.message(
                                                                    presentation,
                                                                    "spectator.teleport.player")
                                                            : WorkerPresentationService.message(
                                                                    presentation,
                                                                    "spectator.teleport.team",
                                                                    "{0}",
                                                                    team.name());
                                            return new PlayerListMenu.Target(
                                                    target.uuid(),
                                                    Map.of(
                                                            "%player%",
                                                            target.username(),
                                                            "%formatted-player%",
                                                            LegacyText.serialize(
                                                                    Component.text(
                                                                            target.username(),
                                                                            teamColor(team))),
                                                            "%team-label%",
                                                            LegacyText.serialize(label),
                                                            "%click-hint%",
                                                            LegacyText.serialize(
                                                                    WorkerPresentationService
                                                                            .message(
                                                                                    presentation,
                                                                                    "spectator.teleport.click"))));
                                        })
                                .toList(),
                select);
    }

    static boolean isReadOnly(Inventory inventory) {
        return BingoCardMenu.owns(inventory) || PlayerListMenu.owns(inventory);
    }

    private static ItemStack taskItem(
            BingoTaskSpec task,
            MatchManifest manifest,
            boolean own,
            boolean neutral,
            boolean hidden,
            boolean locked,
            List<Integer> teamIds,
            List<WorkerTaskCompletion> details,
            Integer viewerTeamId,
            BingoTaskPresentation theme) {
        var completions = new ArrayList<BingoTaskItems.Completion>();
        BingoTaskItems.Completion ownCompletion = null;
        for (int teamId : teamIds) {
            var detail =
                    details.stream()
                            .filter(value -> value.teamId() == teamId)
                            .findFirst()
                            .orElse(null);
            TeamSnapshot team = manifest.teamsById().get(teamId);
            // Only old direct callers lack completion facts; never invent a player or timestamp.
            var value =
                    detail == null
                            ? new BingoTaskItems.Completion(
                                    team == null
                                            ? Component.empty()
                                            : LegacyText.component(team.name(), teamColor(team)),
                                    -1)
                            : new BingoTaskItems.Completion(detail.playerName(), detail.seconds());
            completions.add(value);
            if (Integer.valueOf(teamId).equals(viewerTeamId)) ownCompletion = value;
        }
        ItemStack nativeIcon = null;
        if ("advancement".equalsIgnoreCase(task.taskType())) {
            Advancement advancement = WorkerTaskDisplay.advancement(task.attributes().get("key"));
            if (advancement != null && advancement.getDisplay() != null)
                nativeIcon = advancement.getDisplay().icon();
        }
        org.bukkit.potion.PotionType potion = null;
        String effect =
                task.attributes()
                        .getOrDefault("display.potion-type", task.attributes().get("effect"));
        if (effect != null) {
            try {
                potion = org.bukkit.potion.PotionType.valueOf(effect.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                /* Objective validation owns unknown effects. */
            }
        }
        var display =
                new BingoTaskItems.Task(
                        BingoTaskItems.icon(
                                WorkerTaskDisplay.icon(task), nativeIcon, potion, theme),
                        displayName(task),
                        taskDescription(task),
                        WorkerTaskDisplay.amount(task),
                        "statistic".equalsIgnoreCase(task.taskType()),
                        WorkerTaskDisplay.glows(task));
        return BingoTaskItems.render(
                theme,
                display,
                BingoTaskPresentation.state(
                        own,
                        !teamIds.isEmpty(),
                        neutral,
                        manifest.scoring().variant().mode().locksCells(),
                        hidden,
                        locked),
                completions,
                ownCompletion,
                manifest.runtimeRules().presentation().messages());
    }

    static Component displayName(BingoTaskSpec task) {
        return WorkerTaskText.name(task);
    }

    private static List<Component> taskDescription(BingoTaskSpec task) {
        return WorkerTaskText.lore(task);
    }

    private static TextColor teamColor(TeamSnapshot team) {
        TextColor color = team == null ? null : TextColor.fromHexString(team.colorCode());
        return color == null ? NamedTextColor.WHITE : color;
    }
}
