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
 * A {@link TaskData} placed on a card plus its mutable completion state. Renders straight to a
 * Paper {@link ItemStack} — the card menu is read-only and tracks tasks by slot index.
 */
public final class GameTask {
    /** Lightweight record of who completed a task, decoupled from the team classes. */
    public record Completion(UUID playerId, Component playerName, TextColor teamColor, String teamId, long completedAt) {
    }

    public TaskData data;
    /** Completion per team id, in completion order. Empty until at least one team finishes the task. */
    private final java.util.LinkedHashMap<String, Completion> completions = new java.util.LinkedHashMap<>();
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
     *               When false, each team may complete it once independently.
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

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

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
        if (viewerTeamId != null && isCompletedByTeam(viewerTeamId)) {
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
        if (own != null) {
            name = getName(viewerTeamId);
            material = Material.BARRIER;
            lore.add(Component.text()
                    .append(msg.component("card.completed_by"))
                    .append(own.playerName())
                    .decoration(TextDecoration.ITALIC, false).build());
            lore.add(msg.component("card.completed_at", formatTime(own.completedAt())));
            glow = true;
        } else if (viewerTeamId != null && displayInfo.locksTasks() && isCompleted()) {
            // In domination mode (locksTasks), a cell completed by another team is locked to them.
            name = getName(viewerTeamId);
            material = Material.BARRIER;
            Completion claimer = completions.values().iterator().next();
            lore.add(Component.text()
                    .append(msg.component("card.occupied_by"))
                    .decoration(TextDecoration.ITALIC, false).build());
            lore.add(Component.text()
                    .append(msg.component("card.completed_by"))
                    .append(claimer.playerName())
                    .decoration(TextDecoration.ITALIC, false).build());
            glow = true;
        } else {
            name = getName(viewerTeamId);
            material = !isCompleted() && (hidden || locked) ? Material.BEDROCK : icon(displayInfo);
            for (Component line : data.getItemDescription()) {
                lore.add(line);
            }
            glow = !isCompleted() && !hidden && !locked && data.shouldItemGlow();
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
                cl.append(c.playerName());
                first = false;
            }
            lore.add(cl.build());
        }

        // Guard against block-only materials reaching the GUI: ItemStack(Material) throws for non-items.
        if (!material.isItem()) material = Material.PAPER;
        ItemStack stack = new ItemStack(material);
        // Keep the advancement's own display components when possible - Voluntary Exile's icon is a
        // white_banner carrying the ominous-banner pattern data, which a bare Material would lose.
        if (data instanceof AdvancementTask advancement) {
            ItemStack iconStack = advancement.displayIconStack();
            if (iconStack != null && iconStack.getType() == material) {
                stack = iconStack.clone();
            }
        }
        boolean lockedByOther = viewerTeamId != null && displayInfo.locksTasks() && isCompleted() && own == null;
        boolean active = own == null && !lockedByOther;
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
            // Effect potions: stamp the base potion type so the chest-GUI item shows the right liquid colour.
            if (data instanceof PotionTask potion && meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
                org.bukkit.potion.PotionType type = potion.potionType();
                if (type != null) pm.setBasePotionType(type);
            }
            // Event subjects shown as potions (die by magic -> splash potion of harming).
            if (data instanceof EventTask event && meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
                org.bukkit.potion.PotionType type = event.displayPotionType();
                if (type != null) pm.setBasePotionType(type);
            }
            // Advancement icons shown as potions (Local Brewery -> instant-health potion).
            if (data instanceof AdvancementTask advancement && meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
                org.bukkit.potion.PotionType type = advancement.displayPotionType();
                if (type != null) pm.setBasePotionType(type);
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
