package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.state.BuildSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Owns the normal plots' material labels; labels change only when an order changes. */
final class BuildMartMaterialDisplays {
    private final Map<BuildSlot, TextDisplay> displays = new HashMap<>();

    void update(BuildSlot slot, Location button) {
        if (slot.isGolden() || slot.getBlueprint() == null || button == null || button.getWorld() == null) {
            remove(slot);
            return;
        }
        Component text = text(slot.getBlueprint());
        TextDisplay display = displays.get(slot);
        if (display == null || !display.isValid()) {
            Location location = new Location(button.getWorld(), button.getBlockX() + .5,
                    button.getBlockY() + 3.0, button.getBlockZ() + .5);
            display = button.getWorld().spawn(location, TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setLineWidth(4096); // Only the explicit two-material rows may wrap.
                spawned.setShadowed(true);
                spawned.setDefaultBackground(false);
                spawned.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setDisplayWidth(16f);
                spawned.setDisplayHeight(16f);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.text(text);
            });
            displays.put(slot, display);
        } else {
            display.text(text);
        }
    }

    void remove(BuildSlot slot) {
        TextDisplay display = displays.remove(slot);
        if (display != null) display.remove();
    }

    void clear() {
        displays.values().forEach(TextDisplay::remove);
        displays.clear();
    }

    static Map<Material, Integer> materials(BuildMartBlueprint blueprint) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (BlueprintBlock block : blueprint.getBlocks()) {
            BlockData data = block.getBlockData();
            if (data instanceof Bed bed && bed.getPart() == Bed.Part.HEAD) continue;
            if (data instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP
                    && !(data instanceof Stairs) && !(data instanceof TrapDoor)) continue;
            Material material = data.getMaterial();
            if (material == Material.PISTON_HEAD) continue; // Created by its piston, not placed from an item.
            if (material.name().startsWith("POTTED_")) {
                counts.merge(Material.FLOWER_POT, 1, Integer::sum);
                material = switch (material) {
                    case POTTED_AZALEA_BUSH -> Material.AZALEA;
                    case POTTED_FLOWERING_AZALEA_BUSH -> Material.FLOWERING_AZALEA;
                    default -> Material.valueOf(material.name().substring("POTTED_".length()));
                };
            } else if (material.name().contains("_WALL_")) {
                // Wall signs, banners and heads use the same inventory item as their standing form.
                Material item = Material.matchMaterial(material.name().replace("_WALL_", "_"));
                if (item != null) material = item;
            }
            int amount = data instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE ? 2 : 1;
            counts.merge(material, amount, Integer::sum);
        }
        return counts;
    }

    static Component text(BuildMartBlueprint blueprint) {
        Component text = Component.empty().color(NamedTextColor.WHITE);
        var materials = materials(blueprint).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Material::name))).toList();
        for (int index = 0; index < materials.size(); index++) {
            if (index > 0) text = text.append(Component.text(index % 2 == 0 ? "\n" : "  "));
            var entry = materials.get(index);
            text = text.append(Component.translatable(entry.getKey().translationKey()))
                    .append(Component.text(" × " + entry.getValue()));
        }
        return text;
    }
}
