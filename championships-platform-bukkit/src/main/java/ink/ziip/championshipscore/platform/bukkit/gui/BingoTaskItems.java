package ink.ziip.championshipscore.platform.bukkit.gui;

import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.shared.presentation.BingoTaskPresentation;
import ink.ziip.championshipscore.shared.presentation.InventoryItemTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Both game adapters supply immutable facts to the same native task-item renderer. */
public final class BingoTaskItems {
    private BingoTaskItems() {}

    public record Task(
            ItemStack icon,
            Component name,
            List<Component> description,
            int required,
            boolean statistic,
            boolean glow) {
        public Task {
            description = List.copyOf(description);
        }
    }

    public record Completion(Component playerName, long seconds) {}

    public static void validate(BingoTaskPresentation theme) {
        material(theme.fallbackMaterial());
        theme.states().values().stream()
                .map(InventoryItemTemplate::material)
                .filter(value -> !BingoTaskPresentation.TASK_MATERIAL.equals(value))
                .forEach(BingoTaskItems::material);
    }

    public static Material material(String value) {
        return InventoryItems.material(new InventoryItemTemplate(value, "", List.of(), false));
    }

    /** Clone native advancement components; apply potion colour before decorating task state. */
    public static ItemStack icon(
            Material material,
            ItemStack nativeIcon,
            PotionType potion,
            BingoTaskPresentation theme) {
        if (!material.isItem() || material.isAir()) material = material(theme.fallbackMaterial());
        var result =
                nativeIcon != null && nativeIcon.getType() == material
                        ? nativeIcon.clone()
                        : new ItemStack(material);
        result.setAmount(1);
        if (potion != null)
            result.editMeta(
                    meta -> {
                        if (meta instanceof PotionMeta potions) potions.setBasePotionType(potion);
                    });
        return result;
    }

    public static Component playerName(BingoTaskPresentation theme, String name, Component team) {
        return ComponentTemplates.render(
                theme.player(), Map.of("%player%", Component.text(name), "%team%", team));
    }

    private static Component label(Map<String, String> messages, String key, String time) {
        return InventoryItems.text(
                messages.getOrDefault(key, key),
                key.equals("card.completed_at") ? Map.of("{0}", time) : Map.of());
    }

    public static ItemStack render(
            BingoTaskPresentation theme,
            Task task,
            BingoTaskPresentation.State state,
            List<Completion> completions,
            Completion own,
            Map<String, String> messages) {
        var template = theme.states().get(state);
        boolean masked =
                state == BingoTaskPresentation.State.HIDDEN
                        || state == BingoTaskPresentation.State.LOCKED;
        Material iconMaterial =
                BingoTaskPresentation.TASK_MATERIAL.equals(template.material())
                        ? task.icon().getType()
                        : material(template.material());
        var item =
                !masked && task.icon().getType() == iconMaterial
                        ? task.icon().clone()
                        : new ItemStack(iconMaterial);
        boolean active = state == BingoTaskPresentation.State.NORMAL;
        int required = Math.max(1, task.required());
        // Preserve Core's statistic count display, including values larger than a normal stack.
        item.setAmount(active ? (task.statistic() ? required : Math.min(required, 64)) : 1);
        var values = new java.util.HashMap<String, Component>();
        if (!masked) {
            values.put("%task%", task.name());
            values.put("%completed-by%", label(messages, "card.completed_by", ""));
            values.put("%occupied-by%", label(messages, "card.occupied_by", ""));
            var first = own != null ? own : completions.isEmpty() ? null : completions.getFirst();
            values.put("%player%", first == null ? Component.empty() : first.playerName());
            values.put(
                    "%completed-at%",
                    label(
                            messages,
                            "card.completed_at",
                            theme.clock(first == null ? -1 : first.seconds())));
            Component names = Component.empty();
            for (Completion completion : completions) {
                if (!names.equals(Component.empty()))
                    names = names.append(LegacyText.component(theme.separator()));
                names = names.append(completion.playerName());
            }
            values.put("%completions%", names);
        }
        List<Component> lore = new ArrayList<>();
        for (String line : template.lore()) {
            if (!masked && line.equals("%description%")) lore.addAll(task.description());
            else if (!masked && line.equals("%completion-summary%")) {
                if (!completions.isEmpty()) {
                    for (String summary : theme.completionSummary())
                        lore.add(ComponentTemplates.render(summary, values));
                }
            } else lore.add(ComponentTemplates.render(line, values));
        }
        item.editMeta(
                meta -> {
                    meta.displayName(
                            ComponentTemplates.renderStyled(template.title(), values)
                                    .decorationIfAbsent(
                                            TextDecoration.ITALIC, TextDecoration.State.FALSE));
                    meta.lore(
                            lore.stream()
                                    .map(
                                            line ->
                                                    line.decorationIfAbsent(
                                                            TextDecoration.ITALIC,
                                                            TextDecoration.State.FALSE))
                                    .toList());
                    meta.setEnchantmentGlintOverride(
                            template.glint() && (!active || completions.isEmpty() && task.glow()));
                    if (!masked) meta.addItemFlags(ItemFlag.values());
                    if (active && task.statistic() && required > 1)
                        meta.setMaxStackSize(Math.min(required, 99));
                });
        return item;
    }
}
