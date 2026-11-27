package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastSupply;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;

import java.util.*;

/** Native workstations and infinite material sources owned by one restaurant. */
final class FeastKitchen {
    private final CraftingFeastArea area;
    private final int restaurant;
    private final Map<Block, Material> resources = new LinkedHashMap<>();
    private final Map<Block, Material> workstations = new LinkedHashMap<>();
    private final Map<Block, SupplyInventory> supplies = new LinkedHashMap<>();
    private final Set<UUID> equipped = new HashSet<>();
    private boolean open;

    FeastKitchen(CraftingFeastArea area, int restaurant) {
        this.area = area;
        this.restaurant = restaurant;
    }

    private static final String MESSAGE_PREFIX = "crafting-feast.";

    static String text(String key, Object... args) {
        return MessageConfig.codeText(MESSAGE_PREFIX + key, args);
    }

    static void message(Player player, String text) {
        player.sendMessage(LegacyText.component(text));
    }

    void tell(Player player, String key, Object... args) {
        message(player, text(key, args));
    }

    void actionBar(Player player, String key, Object... args) {
        player.sendActionBar(LegacyText.component(text(key, args)));
    }

    void sound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 0.8f, pitch);
    }

    private Block block(Location location) {
        return area.getGameConfig().at(location, restaurant).getBlock();
    }

    void open() {
        var config = area.getGameConfig();
        config.resourceLocations()
                .forEach(
                        (material, points) ->
                                points.forEach(point -> resources.put(block(point), material)));
        config.workstationLocations()
                .forEach(
                        (material, points) ->
                                points.forEach(point -> workstations.put(block(point), material)));
        config.supplyBarrels()
                .forEach(
                        supply ->
                                supplies.put(
                                        block(supply.location()), new SupplyInventory(supply)));
        resources.forEach(FeastKitchen::requireBlock);
        workstations.forEach(FeastKitchen::requireBlock);
        supplies.keySet().forEach(block -> requireBlock(block, Material.BARREL));
        clearStations();
        open = true;
    }

    private static void requireBlock(Block block, Material material) {
        if (block.getType() != material)
            throw new IllegalStateException(
                    "Crafting Feast workspace needs " + material + " at " + block.getLocation());
    }

    boolean interact(Player player, Block block) {
        if (!open || !area.canPlay(player)) return false;
        SupplyInventory supply = supplies.get(block);
        if (supply != null) {
            player.openInventory(supply.getInventory());
            return false;
        }
        return workstations.containsKey(block) && workstations.get(block) == block.getType();
    }

    boolean canUse(Player player, Inventory inventory) {
        if (!open || !area.canPlay(player)) return false;
        if (inventory.getHolder() instanceof SupplyInventory supply)
            return supplies.containsValue(supply);
        if (inventory.getType() == InventoryType.CRAFTING) return true;
        Location location = inventory.getLocation();
        return location != null
                && workstations.containsKey(location.getBlock())
                && location.distanceSquared(player.getLocation()) <= 64;
    }

    void supply(Player player, Inventory inventory, int slot) {
        if (!canUse(player, inventory)
                || !(inventory.getHolder() instanceof SupplyInventory supply)) return;
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(slot);
        if (item != null) give(player, item.clone());
    }

    boolean isSupply(Inventory inventory) {
        return inventory.getHolder() instanceof SupplyInventory;
    }

    boolean isResource(Block block) {
        return open && resources.get(block) == block.getType();
    }

    void harvest(Player player, Block block) {
        if (!open
                || !area.canPlay(player)
                || !resources.containsKey(block)
                || block.getType() != resources.get(block)
                || block.getLocation().distanceSquared(player.getLocation()) > 36) return;
        // Cancel the physical break: the resource remains in place and there is no delayed respawn
        // that could affect the next game. Drops still come from the server's native loot table.
        for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand(), player))
            give(player, drop);
    }

    private void give(Player player, ItemStack stack) {
        if (!player.getInventory().addItem(stack).isEmpty()) tell(player, "full");
    }

    void tick() {
        if (!open || !area.serving()) return;
        // Let vanilla ignite real fuel: current servers establish the smoker/blast furnace's
        // speed when fuel ignites. Writing burn time directly bypasses that initialization.
        for (Block block : workstations.keySet()) {
            if (!(block.getState(false) instanceof Furnace furnace)) continue;
            ItemStack fuel = furnace.getInventory().getFuel();
            if (fuel == null || fuel.getType().isAir())
                furnace.getInventory().setFuel(new ItemStack(Material.COAL));
        }
    }

    void equip(Player player) {
        if (!area.isGameplayParticipant(player)) return;
        // The lesson clears each admitted inventory. In a live match a reconnect retains it.
        if (!area.lessonRunning() && !equipped.add(player.getUniqueId())) return;
        for (Material tool :
                List.of(
                        Material.NETHERITE_PICKAXE,
                        Material.NETHERITE_AXE,
                        Material.NETHERITE_SHOVEL)) {
            ItemStack item = new ItemStack(tool);
            item.editMeta(meta -> meta.setUnbreakable(true));
            player.getInventory().addItem(item);
        }
    }

    private void clearStations() {
        for (Block block : workstations.keySet()) {
            if (!(block.getState() instanceof Container container)) continue;
            for (var viewer : List.copyOf(container.getInventory().getViewers()))
                viewer.closeInventory();
            container.getInventory().clear();
            if (container instanceof Furnace furnace) {
                furnace.getSnapshotInventory().clear();
                var data = (org.bukkit.block.data.Lightable) furnace.getBlockData();
                data.setLit(false);
                furnace.setBlockData(data);
                furnace.setBurnTime((short) 0);
                furnace.setCookTime((short) 0);
                furnace.update(true, false);
            }
        }
    }

    void close() {
        open = false;
        for (SupplyInventory supply : supplies.values())
            for (var viewer : List.copyOf(supply.getInventory().getViewers()))
                viewer.closeInventory();
        clearStations();
        supplies.clear();
        resources.clear();
        workstations.clear();
    }

    private static final class SupplyInventory implements InventoryHolder {
        private final Inventory inventory;

        SupplyInventory(FeastSupply supply) {
            inventory = Bukkit.createInventory(this, 27, LegacyText.component(supply.name()));
            int slot = 0;
            for (var entry : supply.items().entrySet())
                inventory.setItem(slot++, new ItemStack(entry.getKey(), entry.getValue()));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
