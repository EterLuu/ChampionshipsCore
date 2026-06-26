package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link TaskData} placed on a card, plus its mutable completion/void state. Renders straight to a
 * Paper {@link ItemStack} — the card menu is read-only and tracks tasks by slot index.
 */
public final class GameTask {
    /** Lightweight record of who completed a task, decoupled from the team classes. */
    public record Completion(UUID playerId, Component playerName, TextColor teamColor, String teamId, long completedAt) {
    }

    public TaskData data;
    /** Completion per team id, in completion order. Empty until at least one team finishes the task. */
    private final java.util.LinkedHashMap<String, Completion> completions = new java.util.LinkedHashMap<>();
    private boolean voided;
    /** BLIND remix carryover: the task is hidden (shown as bedrock) until revealed or completed. */
    private boolean hidden;

    public GameTask(@NotNull TaskData data) {
        this.data = data;
        this.voided = false;
    }

    public void setVoided(boolean value) {
        if (isCompleted()) return;
        voided = value;
    }

    public boolean isVoided() {
        return voided;
    }

    /** Hides this task; never hides an already-completed task. Pass {@code false} to reveal it. */
    public void setHidden(boolean value) {
        if (value && isCompleted()) return;
        hidden = value;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** True once any team has completed (claimed) this task. */
    public boolean isCompleted() {
        return !completions.isEmpty();
    }

    /**
     * Records a completion for the completing team.
     *
     * @param locked when true, the task locks to the first team — a second team cannot claim it.
     *               When false, each team may complete it once independently.
     * @return true if this call newly completed the task for {@code by}'s team.
     */
    public boolean complete(@NotNull Completion by, boolean locked) {
        if (isVoided()) return false;
        if (locked && !completions.isEmpty()) return false;
        if (completions.containsKey(by.teamId())) return false;
        completions.put(by.teamId(), by);
        hidden = false; // completing a task reveals it
        return true;
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

    /** Display name from {@code viewerTeamId}'s perspective: struck-through once that team completed it. */
    public Component getName(@Nullable String viewerTeamId) {
        if (isVoided()) {
            TextComponent.Builder b = Component.text()
                    .color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.STRIKETHROUGH);
            b.append(Component.text("A").decorate(TextDecoration.OBFUSCATED));
            b.append(data.getName().color(NamedTextColor.DARK_GRAY));
            b.append(Component.text("A").decorate(TextDecoration.OBFUSCATED));
            return b.build();
        } else if (viewerTeamId != null && isCompletedByTeam(viewerTeamId)) {
            return Component.text().color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.STRIKETHROUGH)
                    .append(data.getName()).build();
        }
        return data.getName();
    }

    /** Builds the chest-GUI item representing this task's state for the viewing team. */
    public ItemStack toItem(CardDisplayInfo displayInfo, @Nullable String viewerTeamId) {
        Material material;
        Component name;
        List<Component> lore = new ArrayList<>();
        boolean glow;

        Completion own = viewerTeamId == null ? null : completions.get(viewerTeamId);
        MessageService msg = MessageService.global();
        boolean anyCompleted = isCompleted();
        if (isHidden()) {
            // Hidden cell: reveal nothing about the task — not even its name or count.
            material = Material.BEDROCK;
            name = msg.component("card.hidden");
            glow = false;
        } else if (isVoided()) {
            name = getName(viewerTeamId);
            material = Material.STRUCTURE_VOID;
            lore.add(msg.component("card.voided"));
            glow = true;
        } else if (own != null) {
            name = getName(viewerTeamId);
            material = Material.BARRIER;
            lore.add(Component.text()
                    .append(msg.component("card.completed_by"))
                    .append(own.playerName().color(own.teamColor() == null ? NamedTextColor.WHITE : own.teamColor()))
                    .decoration(TextDecoration.ITALIC, false).build());
            lore.add(msg.component("card.completed_at", formatTime(own.completedAt())));
            glow = true;
        } else if (displayInfo.locksTasks() && isCompleted()) {
            // In domination mode (locksTasks), a cell completed by another team is locked to them.
            name = getName(viewerTeamId);
            material = Material.BARRIER;
            Completion claimer = completions.values().iterator().next();
            lore.add(Component.text()
                    .append(msg.component("card.occupied_by"))
                    .decoration(TextDecoration.ITALIC, false).build());
            lore.add(Component.text()
                    .append(msg.component("card.completed_by"))
                    .append(claimer.playerName().color(claimer.teamColor() == null ? NamedTextColor.WHITE : claimer.teamColor()))
                    .decoration(TextDecoration.ITALIC, false).build());
            glow = true;
        } else {
            name = getName(viewerTeamId);
            material = icon(displayInfo);
            for (Component line : data.getItemDescription()) {
                lore.add(line);
            }
            glow = data.shouldItemGlow();
        }

        // When any team has finished this cell, list every completor (in claim order) so viewers can read
        // the race state — most useful in points mode, where claim order is the score.
        if (anyCompleted) {
            lore.add(Component.empty());
            TextComponent.Builder cl = Component.text()
                    .append(msg.component("card.completed_by")).decoration(TextDecoration.ITALIC, false);
            boolean first = true;
            for (Completion c : completions.values()) {
                if (!first) cl.append(Component.text(", ", NamedTextColor.GRAY));
                cl.append(Component.text().color(c.teamColor() == null ? NamedTextColor.WHITE : c.teamColor())
                        .append(c.playerName()).build());
                first = false;
            }
            lore.add(cl.build());
        }

        // Guard against block-only materials reaching the GUI: ItemStack(Material) throws for non-items.
        if (!material.isItem()) material = Material.PAPER;
        ItemStack stack = new ItemStack(material);
        boolean lockedByOther = displayInfo.locksTasks() && isCompleted() && own == null;
        boolean active = !isHidden() && own == null && !isVoided() && !lockedByOther;
        boolean statistic = data.getType() == TaskData.TaskType.STATISTIC;
        int required = Math.max(1, data.getRequiredAmount());
        if (active) {
            stack.setAmount(statistic ? required : Math.min(required, 64));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) meta.lore(lore);
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.values());
            if (active && statistic && required > 1) {
                meta.setMaxStackSize(Math.min(required, 99));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String formatTime(long seconds) {
        if (seconds < 0) return "--:--";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
