package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.config.BingoCardMenuConfig;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.platform.bukkit.gui.BingoTaskItems;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link TaskData} placed on a card plus its mutable completion state. Renders straight to a
 * Paper {@link ItemStack} — the card menu is read-only and tracks tasks by slot index.
 */
public final class GameTask {
    /** Lightweight record of who completed a task, decoupled from the team classes. */
    public record Completion(
            UUID playerId,
            Component playerName,
            TextColor teamColor,
            String teamId,
            long completedAt) {}

    public TaskData data;

    /**
     * Completion per team id, in completion order. Empty until at least one team finishes the task.
     */
    private final java.util.LinkedHashMap<String, Completion> completions =
            new java.util.LinkedHashMap<>();

    private boolean hidden;
    private boolean locked;

    public GameTask(@NotNull TaskData data) {
        this.data = data;
    }

    /** True once any team has completed (claimed) this task. */
    public boolean isCompleted() {
        return !completions.isEmpty();
    }

    /**
     * Records a completion for the completing team.
     *
     * @param locked when true, the task locks to the first team — a second team cannot claim it.
     *     When false, each team may complete it once independently.
     * @return true if this call newly completed the task for {@code by}'s team.
     */
    public boolean complete(@NotNull Completion by, boolean locked) {
        if (this.locked) return false;
        if (locked && !completions.isEmpty()) return false;
        if (completions.containsKey(by.teamId())) return false;
        completions.put(by.teamId(), by);
        hidden = false;
        return true;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isCompletedByTeam(@NotNull String teamId) {
        return completions.containsKey(teamId);
    }

    /** Game-time the given team completed this task, or -1 if it has not. */
    public long completedAt(@NotNull String teamId) {
        Completion c = completions.get(teamId);
        return c == null ? -1L : c.completedAt();
    }

    /**
     * 0-based claim rank for a team that has completed this task (0 = first team to claim it).
     * Returns -1 if this team has not completed this task.
     */
    public int claimRank(@NotNull String teamId) {
        int rank = 0;
        for (Map.Entry<String, Completion> entry : completions.entrySet()) {
            if (entry.getKey().equals(teamId)) return rank;
            rank++;
        }
        return -1;
    }

    /** All completions for this task in claim order (immutable snapshot). */
    public List<Completion> allCompletions() {
        return List.copyOf(completions.values());
    }

    public GameTask copy() {
        return new GameTask(data);
    }

    public TaskData.TaskType taskType() {
        return data.getType();
    }

    public Material icon(CardDisplayInfo displayInfo) {
        return data.getDisplayMaterial(displayInfo);
    }

    /** Builds the chest-GUI item using the same state contract as the remote worker. */
    public ItemStack toItem(CardDisplayInfo displayInfo, @Nullable String viewerTeamId) {
        return toItem(displayInfo, viewerTeamId, BingoCardMenuConfig.taskSnapshot());
    }

    public ItemStack toItem(
            CardDisplayInfo displayInfo,
            @Nullable String viewerTeamId,
            BingoTaskPresentation theme) {
        Completion own = viewerTeamId == null ? null : completions.get(viewerTeamId);
        var state =
                BingoTaskPresentation.state(
                        own != null,
                        isCompleted(),
                        viewerTeamId == null,
                        displayInfo.locksTasks(),
                        hidden,
                        locked);
        org.bukkit.potion.PotionType potion = null;
        ItemStack nativeIcon = null;
        if (data instanceof AdvancementTask advancement) {
            nativeIcon = advancement.displayIconStack();
            potion = advancement.displayPotionType();
        } else if (data instanceof PotionTask potions) potion = potions.potionType();
        else if (data instanceof EventTask event) potion = event.displayPotionType();
        var task =
                new BingoTaskItems.Task(
                        BingoTaskItems.icon(icon(displayInfo), nativeIcon, potion, theme),
                        data.getName(),
                        List.of(data.getItemDescription()),
                        data.getRequiredAmount(),
                        data.getType() == TaskData.TaskType.STATISTIC,
                        data.shouldItemGlow());
        return BingoTaskItems.render(
                theme,
                task,
                state,
                completions.values().stream().map(GameTask::displayCompletion).toList(),
                own == null ? null : displayCompletion(own),
                Map.of(
                        "card.completed_by",
                        MessageService.global().tr("card.completed_by"),
                        "card.completed_at",
                        MessageService.global().tr("card.completed_at"),
                        "card.occupied_by",
                        MessageService.global().tr("card.occupied_by")));
    }

    private static BingoTaskItems.Completion displayCompletion(Completion completion) {
        return new BingoTaskItems.Completion(completion.playerName(), completion.completedAt());
    }
}
