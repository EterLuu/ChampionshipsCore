package ink.ziip.championshipscore.api.game.bingo;

import io.papermc.paper.registry.keys.EnchantmentKeys;
import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Enchants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bingo starter kit handed to every participant at round start (per the bingo design doc):
 * <ul>
 *   <li>team-coloured leather helmet/leggings - Unbreakable + Protection IV; boots additionally
 *       have Feather Falling IV (no chestplate, the slot holds the elytra);</li>
 *   <li>an Unbreakable elytra (chest slot);</li>
 *   <li>32 bread;</li>
 *   <li>Efficiency III + Unbreakable stone pickaxe / axe / shovel; no hoe;</li>
 *   <li>an Unbreakable iron sword with Sharpness II;</li>
 *   <li>a compass - left-click opens the team-teleport menu (see {@code BingoCompassListener}).</li>
 * </ul>
 *
 * <p>The kit can <em>trivialise</em> bingo tasks - handing a player a stone pickaxe auto-completes a
 * "collect stone pickaxe" item task and the "Getting an Upgrade" advancement the instant the round
 * starts. {@link #trivialises(TaskData)} reports those so the card generator can exclude them, keeping
 * every cell something the player still has to go and earn. {@link #conflictingAdvancementKeys()} lists
 * the possession-granted advancements so their criterion grants can be cancelled (else their toasts pop
 * at round start). Statistic tasks (mine/craft/kill/travel) are actions the player must still perform,
 * so they are never trivialised.
 */
public final class BingoStarterKit {
    private BingoStarterKit() {
    }

    /** Non-armour kit items; armour is team-coloured and built per-player in {@link #give}. */
    private static final List<ItemStack> KIT_ITEMS = List.of(
            tool(Material.STONE_PICKAXE),
            tool(Material.STONE_AXE),
            tool(Material.STONE_SHOVEL),
            ironSword(),
            new ItemStack(Material.BREAD, 32),
            compass());

    /** Total count of each material the kit provides (incl. team leather + elytra), for conflict checks. */
    private static final Map<Material, Integer> PROVIDED = buildProvided();

    /** Vanilla advancement paths the kit auto-completes by mere possession/equipment. */
    private static final Set<String> CONFLICTING_ADVANCEMENTS = gearAdvancementConflicts(PROVIDED.keySet());

    private static Map<Material, Integer> buildProvided() {
        Map<Material, Integer> p = new EnumMap<>(Material.class);
        for (ItemStack item : KIT_ITEMS) {
            if (item != null && !item.getType().isAir()) {
                p.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        // Team-coloured leather (helmet/leggings/boots) + the elytra are equipped in give(); their
        // materials still count for task-conflict detection.
        p.put(Material.LEATHER_HELMET, 1);
        p.put(Material.LEATHER_LEGGINGS, 1);
        p.put(Material.LEATHER_BOOTS, 1);
        p.put(Material.ELYTRA, 1);
        return Map.copyOf(p);
    }

    public static void give(Player player, ChampionshipTeam team) {
        if (player == null || team == null) return;
        PlayerInventory inv = player.getInventory();

        // Team-coloured leather armor (helmet/leggings/boots); the chest slot holds the elytra.
        inv.setHelmet(protective(team.getHelmet()));
        inv.setLeggings(protective(team.getLeggings()));
        inv.setBoots(protectiveBoots(team.getBoots()));
        inv.setChestplate(unbreakable(new ItemStack(Material.ELYTRA)));

        // Stone tools, sword, food and rockets go into the inventory; overflow is dropped at the
        // player's feet rather than silently lost (e.g. on a mid-round reconnect with a full inv).
        for (ItemStack item : KIT_ITEMS) {
            Map<Integer, ItemStack> overflow = inv.addItem(item.clone());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
            }
        }
    }

    /**
     * Whether the player already holds the starter kit, detected via the unbreakable stone pickaxe
     * <em>or</em> the unbreakable stone shovel - the kit's most distinctive items (player-crafted
     * stone tools are breakable, so this won't false-positive on loot). Either one counts so the
     * check stays reliable even if a tool was momentarily moved/lost: used on death-respawn and
     * mid-round reconnect to avoid re-issuing the kit, which would duplicate the non-stacking tools
     * (pickaxe/axe/shovel/sword) and drop the extras at their feet.
     */
    public static boolean hasKit(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.STONE_PICKAXE && item.getType() != Material.STONE_SHOVEL) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.isUnbreakable()) return true;
        }
        return false;
    }

    /**
     * Whether handing out this kit would complete {@code task} for free at round start, so the generator
     * should keep it off the card. Item tasks conflict when the kit supplies enough of the material
     * (count-aware); one-of sets when it supplies enough of any member; possession-based advancements
     * conflict per {@link #gearAdvancementConflicts}. Statistic tasks are actions, so never conflict.
     */
    public static boolean trivialises(TaskData task) {
        if (task instanceof ItemTask item) {
            return PROVIDED.getOrDefault(item.itemType(), 0) >= item.count();
        }
        if (task instanceof OneOfTask set) {
            // Auto-completable the moment the kit supplies enough of any one member.
            return set.items().stream().anyMatch(m -> PROVIDED.getOrDefault(m, 0) >= set.count());
        }
        if (task instanceof AdvancementTask adv && adv.advancement() != null) {
            return CONFLICTING_ADVANCEMENTS.contains(adv.advancement().getKey().getKey());
        }
        return false;
    }

    /**
     * Vanilla advancement paths the kit auto-grants by mere possession/equipment (e.g.
     * {@code story/obtain_armor} from the team-colour leather, {@code story/upgrade_tools} from the
     * stone pickaxe, {@code end/elytra} from the elytra). Used to silence the advancement <em>toasts</em>
     * that pop at round start and on the first inventory change during play - the chat broadcast is
     * already suppressed via the {@code SHOW_ADVANCEMENT_MESSAGES} gamerule on the bingo worlds, but the
     * on-screen toast needs the criterion grant itself cancelled.
     */
    public static Set<String> conflictingAdvancementKeys() {
        return CONFLICTING_ADVANCEMENTS;
    }

    /** Maps the gear in a kit to the vanilla advancements that completing-by-possession would grant. */
    private static Set<String> gearAdvancementConflicts(Set<Material> materials) {
        Set<String> keys = new HashSet<>();
        for (Material m : materials) {
            String name = m.name();
            if (name.endsWith("_PICKAXE") && !name.startsWith("WOODEN_") && !name.startsWith("GOLDEN_")) {
                keys.add("story/upgrade_tools"); // "Getting an Upgrade": a better-than-wood pickaxe
            }
            if (name.equals("IRON_PICKAXE")) {
                keys.add("story/iron_tools"); // "Isn't It Iron Pick": an iron pickaxe in hand
            }
            if (name.equals("ELYTRA")) {
                keys.add("end/elytra"); // "Sky's the Limit": an elytra in inventory
            }
            if (isArmor(name)) {
                keys.add("story/obtain_armor"); // "Suit Up": wearing any armor piece
                if (name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")) {
                    keys.add("story/shiny_gear"); // "Cover Me with Diamonds": diamond+ armor
                }
            }
        }
        return Set.copyOf(keys);
    }

    private static boolean isArmor(String name) {
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /** Unbreakable + Protection IV on a (team-coloured) leather armor piece. */
    private static ItemStack protective(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.PROTECTION), 4, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Unbreakable + Protection IV + Feather Falling IV on the starter boots. */
    private static ItemStack protectiveBoots(ItemStack item) {
        item = protective(item);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchants.get(EnchantmentKeys.FEATHER_FALLING), 4, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Efficiency III + Unbreakable stone tool (pickaxe / axe / shovel). */
    private static ItemStack tool(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.EFFICIENCY), 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Unbreakable iron sword with Sharpness II (per the design doc). */
    private static ItemStack ironSword() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchants.get(EnchantmentKeys.SHARPNESS), 2, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Compass used to teleport to teammates (left-click opens the team-teleport menu). */
    private static ItemStack compass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("队友传送指南针").color(NamedTextColor.AQUA));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
