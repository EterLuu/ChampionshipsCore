package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.WarehouseCode;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles;

import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

final class Warehouse {
    private static final NamespacedKey KEY = new NamespacedKey("championshipscore", "warehouse");
    private final RobotProps props;
    private final World world;
    private final Vector origin;
    private final WarehouseCode code;
    private final String stockId = UUID.randomUUID().toString();
    private ItemStack pick;
    private boolean changed;
    private final List<Inventory> barrels = new ArrayList<>();
    private final List<TextDisplay> labels = new ArrayList<>();

    Warehouse(World world, Vector origin, WarehouseCode code, RobotProps props) {
        this.props = props;
        this.world = world;
        this.origin = origin;
        this.code = code;
    }

    static void configurePick(ItemStack pick, WarehouseCode code) {
        pick.editMeta(
                meta -> {
                    meta.setUnbreakable(true);
                    meta.displayName(LegacyText.component(RobotArea.text("pick")));
                });
        var blocks =
                RegistrySet.keySetFromValues(
                        RegistryKey.BLOCK,
                        code.stock().stream().map(Material::asBlockType).toList());
        pick.setData(
                DataComponentTypes.CAN_BREAK,
                ItemAdventurePredicate.itemAdventurePredicate(
                        List.of(BlockPredicate.predicate().blocks(blocks).build())));
    }

    private Block block(int x, int y, int z) {
        return world.getBlockAt(
                origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
    }

    void prepare(Deque<Runnable> steps, ItemStack commonPick) {
        steps.add(
                () -> {
                    pick = commonPick.clone();
                    clearLabels();
                });
        for (int x = -1; x <= 19; x++) {
            int column = x;
            steps.add(() -> stockColumn(column));
        }
        steps.add(() -> prepareChest(0));
        steps.add(() -> prepareChest(3));
        for (int i = 0; i < 4; i++) {
            int index = i;
            steps.add(() -> prepareBarrel(index));
        }
    }

    private void stockColumn(int x) {
        for (int y = 1; y <= 3; y += 2)
            for (int z = -1; z <= 9; z++) {
                Material material = WarehouseRoom.stock(x, y, z, code.stock());
                if (material != null) {
                    var shelf = block(x, y, z);
                    if (shelf.getType() != material) shelf.setType(material, false);
                }
            }
    }

    private void prepareChest(int slot) {
        boolean materials = slot == code.materialReader();
        var chest = (Chest) block(WarehouseRoom.chestX(slot), 2, 5).getState();
        chest.customName(
                LegacyText.component(
                        RobotArea.text(materials ? "material-console" : "count-console")));
        chest.update();
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        for (int i = 0; i < 4; i++) {
            var digit = code.digits().get(i);
            ItemStack clue =
                    new ItemStack(
                            materials ? digit.material() : Material.WHITE_CONCRETE,
                            materials ? 1 : digit.amount());
            inventory.setItem(10 + 2 * i, clue);
        }
    }

    private void prepareBarrel(int i) {
        int bx = WarehouseRoom.barrelX(i);
        var barrel = (Barrel) block(bx, 1, 12).getState();
        int number = i + 1;
        barrel.customName(LegacyText.component(RobotArea.text("barrel", number)));
        barrel.update();
        Inventory inventory = barrel.getInventory();
        inventory.clear();
        barrels.add(inventory);
    }

    void open() {
        for (int i = 0; i < barrels.size(); i++) createLabel(WarehouseRoom.barrelX(i), i + 1);
    }

    private void createLabel(int bx, int number) {
        Location labelLocation =
                origin.clone().add(new Vector(bx + 0.5, 2.25, 12.5)).toLocation(world);
        labelLocation.setDirection(BlockFace.NORTH.getDirection());
        TextDisplay label =
                world.spawn(
                        labelLocation,
                        TextDisplay.class,
                        display -> {
                            display.setPersistent(false);
                            display.getPersistentDataContainer()
                                    .set(KEY, PersistentDataType.STRING, stockId + "/label");
                            MessageConfig.worldTextStyle(
                                            WorldTextStyles.Profile.ROBOT_WAREHOUSE_BARREL)
                                    .apply(display);
                            display.text(
                                    LegacyText.component(RobotArea.text("barrel-label", number)));
                        });
        labels.add(label);
    }

    boolean inRole(Player player, int slot) {
        Vector pos = player.getLocation().toVector().subtract(origin);
        return WarehouseRoom.contains(
                        slot, pos.getBlockX(), pos.getBlockY(), pos.getBlockZ(), false)
                && pos.getBlockZ() >= WarehouseRoom.MIN_Z;
    }

    boolean container(Location loc) {
        if (loc == null || loc.getWorld() != world) return false;
        int x = loc.getBlockX() - origin.getBlockX(),
                y = loc.getBlockY() - origin.getBlockY(),
                z = loc.getBlockZ() - origin.getBlockZ();
        if (y == 2 && z == 5) return x == WarehouseRoom.chestX(0) || x == WarehouseRoom.chestX(3);
        if (y != 1 || z != 12) return false;
        for (int i = 0; i < 4; i++) if (x == WarehouseRoom.barrelX(i)) return true;
        return false;
    }

    boolean canOpen(Player player, int slot, Location loc) {
        if (!inRole(player, slot)
                || player.getLocation().distanceSquared(loc.clone().add(.5, .5, .5)) > 36)
            return false;
        return WarehouseRoom.miner(slot)
                ? loc.getBlockY() == origin.getBlockY() + 1
                : loc.getBlockX() == origin.getBlockX() + WarehouseRoom.chestX(slot);
    }

    void equip(Player p) {
        props.give(p, RobotProps.Kind.WAREHOUSE_PICK, pick, RobotProps.SlotPolicy.FREE);
    }

    Material mineable(Block block) {
        Material material =
                WarehouseRoom.stock(
                        block.getX() - origin.getBlockX(),
                        block.getY() - origin.getBlockY(),
                        block.getZ() - origin.getBlockZ(),
                        code.stock());
        return material == block.getType() ? material : null;
    }

    void mine(Player p, Material material) {
        // Keep the shelf block intact and grant the exact stock block directly to the miner
        // without dropping items on the ground, matching infinite non-breaking resource walls.
        ItemStack harvested = new ItemStack(material);
        tag(harvested, stockId);
        if (!p.getInventory().addItem(harvested).isEmpty()) {
            p.sendActionBar(LegacyText.component(RobotArea.text("inventory-full")));
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.45f, 1.25f);
    }

    void inventoryChanged() {
        changed = true;
    }

    boolean sample() {
        if (!changed) return false;
        changed = false;
        for (int i = 0; i < 4; i++) {
            var digit = code.digits().get(i);
            int total = 0;
            for (ItemStack item : barrels.get(i).getStorageContents()) {
                if (item == null || item.getType().isAir()) continue;
                if (item.getType() != digit.material() || !stockId.equals(id(item))) return false;
                total += item.getAmount();
            }
            if (total != digit.amount()) return false;
        }
        return true;
    }

    private boolean owns(ItemStack item) {
        String owner = id(item);
        return owner != null && owner.startsWith(stockId);
    }

    private static String id(ItemStack item) {
        return item == null
                ? null
                : item.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    private static void tag(ItemStack item, String value) {
        item.editPersistentDataContainer(data -> data.set(KEY, PersistentDataType.STRING, value));
    }

    private void clearLabels() {
        for (TextDisplay label : labels) if (label.isValid()) label.remove();
        labels.clear();
        BoundingBox box =
                BoundingBox.of(
                        origin.clone().add(new Vector(WarehouseRoom.MIN_X, 0, WarehouseRoom.MIN_Z)),
                        origin.clone()
                                .add(
                                        new Vector(
                                                WarehouseRoom.MAX_X + 1,
                                                10,
                                                WarehouseRoom.END_Z + 1)));
        world.getNearbyEntities(
                        box,
                        e ->
                                e instanceof TextDisplay display
                                        && display.getPersistentDataContainer()
                                                .has(KEY, PersistentDataType.STRING))
                .forEach(e -> e.remove());
    }

    void clearPlayer(Player player) {
        props.recover(player, RobotProps.Kind.WAREHOUSE_PICK);
        if (container(player.getOpenInventory().getTopInventory().getLocation()))
            player.closeInventory();
        if (owns(player.getItemOnCursor())) player.setItemOnCursor(null);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++)
            if (owns(inventory.getItem(slot))) inventory.setItem(slot, null);
    }

    void reset(Collection<UUID> members) {
        clearLabels();
        finish(members);
        for (Inventory inventory : barrels) inventory.clear();
        for (int slot : List.of(0, 3)) {
            if (block(WarehouseRoom.chestX(slot), 2, 5).getState() instanceof Chest chest)
                chest.getBlockInventory().clear();
        }
    }

    void finish(Collection<UUID> members) {
        clearLabels();
        for (UUID id : members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld() == world) clearPlayer(player);
        }
        BoundingBox box =
                BoundingBox.of(
                        origin.clone().add(new Vector(-11, 0, -124)),
                        origin.clone().add(new Vector(30, 12, 19)));
        world.getNearbyEntities(box, e -> e instanceof Item item && owns(item.getItemStack()))
                .forEach(e -> e.remove());
    }
}
