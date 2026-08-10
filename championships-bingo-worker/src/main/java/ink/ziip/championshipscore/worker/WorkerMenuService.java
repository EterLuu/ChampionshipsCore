package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read-only worker UI rendered exclusively from the immutable match manifest and replay state. */
final class WorkerMenuService {
    private WorkerMenuService() {
    }

    static void openCard(Player player, MatchManifest manifest,
                         Set<Integer> completedByViewer, Map<Integer, List<Integer>> completions) {
        int width = manifest.scoring().cardWidth();
        int rows = Math.clamp(width, 3, 6);
        CardHolder holder = new CardHolder();
        var presentation = manifest.runtimeRules().presentation();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                WorkerPresentationService.message(presentation, "card.title")
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inventory;
        ItemStack info = new ItemStack(Material.MAP);
        info.editMeta(meta -> {
            meta.displayName(WorkerPresentationService.message(presentation, "card.title")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(WorkerPresentationService.message(presentation, "card.win_hint")
                    .decoration(TextDecoration.ITALIC, false)));
        });
        inventory.setItem(0, info);
        int left = (9 - width) / 2;
        for (BingoTaskSpec task : manifest.tasks()) {
            int row = task.cellIndex() / width;
            int column = task.cellIndex() % width;
            if (row >= rows) continue;
            inventory.setItem(row * 9 + left + column,
                    taskItem(task, manifest, completedByViewer, completions));
        }
        player.openInventory(inventory);
    }

    static void openTeammates(Player player, MatchManifest manifest, TeamSnapshot team,
                              List<PlayerSnapshot> participants) {
        List<PlayerSnapshot> teammates = participants.stream()
                .filter(candidate -> candidate.teamId() != null && candidate.teamId() == team.id())
                .filter(candidate -> !candidate.uuid().equals(player.getUniqueId()))
                .filter(candidate -> {
                    Player online = Bukkit.getPlayer(candidate.uuid());
                    return online != null && online.isOnline();
                })
                .sorted(java.util.Comparator.comparing(PlayerSnapshot::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var presentation = manifest.runtimeRules().presentation();
        if (teammates.isEmpty()) {
            player.sendMessage(WorkerPresentationService.message(presentation, "compass.no_teammates"));
            return;
        }
        int rows = Math.max(1, Math.min(6, (teammates.size() + 8) / 9));
        TeamHolder holder = new TeamHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                WorkerPresentationService.message(presentation, "compass.menu_title")
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inventory;
        for (int slot = 0; slot < teammates.size() && slot < inventory.getSize(); slot++) {
            PlayerSnapshot teammate = teammates.get(slot);
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(teammate.username(), teamColor(team))
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(WorkerPresentationService.message(presentation, "compass.teammate_hint")
                        .decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            holder.targets.put(slot, teammate.uuid());
        }
        player.openInventory(inventory);
    }

    static boolean isReadOnly(Inventory inventory) {
        return inventory.getHolder(false) instanceof CardHolder || inventory.getHolder(false) instanceof TeamHolder;
    }

    static UUID teammateTarget(Inventory inventory, int rawSlot) {
        if (!(inventory.getHolder(false) instanceof TeamHolder holder)) return null;
        return holder.targets.get(rawSlot);
    }

    private static ItemStack taskItem(BingoTaskSpec task, MatchManifest manifest,
                                      Set<Integer> completedByViewer,
                                      Map<Integer, List<Integer>> completions) {
        boolean own = completedByViewer.contains(task.cellIndex());
        Material icon = own ? Material.BARRIER : WorkerTaskDisplay.icon(task);
        if (!icon.isItem()) icon = Material.PAPER;
        int required = WorkerTaskDisplay.amount(task);
        ItemStack item = new ItemStack(icon, own ? 1 : Math.min(required, 64));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        Component name = displayName(task);
        if (own) name = name.color(NamedTextColor.GRAY).decorate(TextDecoration.STRIKETHROUGH);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (!own) lore.addAll(taskDescription(task));
        var presentation = manifest.runtimeRules().presentation();
        List<Integer> teams = completions.getOrDefault(task.cellIndex(), List.of());
        if (!teams.isEmpty()) {
            Component completed = WorkerPresentationService.message(presentation, "card.completed_by");
            for (int index = 0; index < teams.size(); index++) {
                TeamSnapshot team = manifest.teamsById().get(teams.get(index));
                if (index > 0) completed = completed.append(Component.text(", ", NamedTextColor.GRAY));
                if (team != null) completed = completed.append(Component.text(team.name(), teamColor(team)));
            }
            lore.add(completed);
        }
        meta.lore(lore);
        if (own || WorkerTaskDisplay.glows(task)) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        if (!own && "statistic".equalsIgnoreCase(task.taskType()) && required > 1) {
            meta.setMaxStackSize(Math.min(required, 99));
        }
        if (meta instanceof PotionMeta potionMeta) {
            String effect = task.attributes().get("effect");
            if (effect != null) {
                try {
                    potionMeta.setBasePotionType(org.bukkit.potion.PotionType.valueOf(effect.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // The objective validator will reject unknown effects during PREPARE.
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    static Component displayName(BingoTaskSpec task) {
        String serialized = task.attributes().get("display.name");
        if (serialized != null) {
            try {
                return GsonComponentSerializer.gson().deserialize(serialized);
            } catch (RuntimeException ignored) {
                // Fall through for a manifest produced by an older mapper.
            }
        }
        Map<String, String> attributes = task.attributes();
        return switch (task.taskType().toLowerCase(Locale.ROOT)) {
            case "item" -> Component.translatable(WorkerTaskDisplay.icon(task).translationKey()).color(NamedTextColor.YELLOW);
            case "potion" -> Component.translatable("item.minecraft."
                    + WorkerTaskDisplay.icon(task).key().value() + ".effect."
                    + attributes.getOrDefault("effect", "water"))
                    .color(NamedTextColor.YELLOW);
            case "item_set" -> Component.translatable(WorkerTaskDisplay.icon(task).translationKey())
                    .color(NamedTextColor.YELLOW);
            case "advancement" -> advancementTitle(attributes.get("key"));
            case "statistic" -> statisticName(attributes);
            default -> Component.text(task.taskId()).color(NamedTextColor.YELLOW);
        };
    }

    private static List<Component> taskDescription(BingoTaskSpec task) {
        List<Component> result = new ArrayList<>();
        int size;
        try {
            size = Integer.parseInt(task.attributes().getOrDefault("display.lore-size", "0"));
        } catch (NumberFormatException ignored) {
            size = 0;
        }
        for (int index = 0; index < size; index++) {
            String serialized = task.attributes().get("display.lore." + index);
            if (serialized == null) continue;
            try {
                result.add(GsonComponentSerializer.gson().deserialize(serialized));
            } catch (RuntimeException ignored) {
                // Ignore one malformed optional display line; objective execution remains valid.
            }
        }
        return result;
    }

    private static TextColor teamColor(TeamSnapshot team) {
        TextColor color = team == null ? null : TextColor.fromHexString(team.colorCode());
        return color == null ? NamedTextColor.WHITE : color;
    }


    private static Component advancementTitle(String key) {
        Advancement advancement = WorkerTaskDisplay.advancement(key);
        if (advancement != null && advancement.getDisplay() != null) {
            return advancement.getDisplay().title().color(NamedTextColor.GREEN);
        }
        return Component.text(key == null ? "advancement" : key).color(NamedTextColor.GREEN);
    }

    private static Component statisticName(Map<String, String> attributes) {
        int amount = requiredAmount(attributes);
        String statistic = attributes.getOrDefault("statistic", "STATISTIC").toLowerCase(Locale.ROOT);
        Component subject = Component.empty();
        Material material = WorkerTaskDisplay.material(attributes.get("material"), null);
        if (material != null) subject = Component.text(" ").append(Component.translatable(material.translationKey()));
        String entity = attributes.get("entity");
        if (entity != null) subject = Component.text(" " + entity.toLowerCase(Locale.ROOT));
        return Component.text(statistic + " × " + amount).color(NamedTextColor.LIGHT_PURPLE).append(subject);
    }

    private static int requiredAmount(Map<String, String> attributes) {
        String raw = attributes.getOrDefault("count", attributes.getOrDefault("target", "1"));
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static final class CardHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class TeamHolder implements InventoryHolder {
        private final Map<Integer, UUID> targets = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
