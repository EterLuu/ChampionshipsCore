package ink.ziip.championshipscore.api.game.craftingfeast.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * A customer's vanilla item request, not a crafting recipe. The optional villager profession and
 * biome keys mark the order's category and main process on the customer; {@link #tier()} marks its
 * value through the villager badge level.
 */
public record FeastRequest(
        String id,
        String name,
        Set<Material> answers,
        boolean clue,
        int amount,
        int points,
        String profession,
        String biome) {
    public FeastRequest {
        answers = Set.copyOf(answers);
        if (id == null
                || id.isBlank()
                || name == null
                || name.isBlank()
                || answers.isEmpty()
                || (!clue && answers.size() != 1)
                || (clue && amount != 1)
                || answers.contains(Material.AIR)
                || answers.contains(Material.CAVE_AIR)
                || answers.contains(Material.VOID_AIR)
                || amount < 1
                || amount > 64
                || points < 1
                || points > 10000
                || (profession != null && profession.isBlank())
                || (biome != null && biome.isBlank()))
            throw new IllegalArgumentException("Invalid Crafting Feast request: " + id);
    }

    public FeastRequest(
            String id,
            String name,
            Material material,
            int amount,
            int points,
            String profession,
            String biome) {
        this(
                id,
                name,
                material == null ? Set.of() : Set.of(material),
                false,
                amount,
                points,
                profession,
                biome);
    }

    /** The named item of an exact order; clue orders have no designated answer. */
    public Material material() {
        return clue ? null : answers.iterator().next();
    }

    public FeastRequest(String id, String name, Material material, int amount, int points) {
        this(id, name, material, amount, points, null, null);
    }

    /** Badge level 1..5 (stone, iron, gold, emerald, diamond) from the order's base points. */
    public int tier() {
        if (points <= 100) return 1;
        if (points <= 130) return 2;
        if (points <= 160) return 3;
        if (points <= 190) return 4;
        return 5;
    }

    public boolean matchesItem(ItemStack stack) {
        return stack != null && answers.contains(stack.getType()) && (clue || !stack.hasItemMeta());
    }

    public boolean accepts(ItemStack stack) {
        return matchesItem(stack) && stack.getAmount() >= amount;
    }

    /** A shelf can hold split stacks, but every deposited item must match the order. */
    public boolean fills(Iterable<ItemStack> contents) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1) continue;
            if (!matchesItem(stack)) return false;
            total += stack.getAmount();
        }
        return total >= amount;
    }

    /** Delivery orders pay their base points plus the map's delivery bonus, without tips. */
    public int deliveryPoints(int bonusPercent) {
        return points * (100 + bonusPercent) / 100;
    }
}
