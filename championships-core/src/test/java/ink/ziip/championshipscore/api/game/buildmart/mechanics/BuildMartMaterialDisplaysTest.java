package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.state.BuildSlot;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ExtendWith(ConfigurationStateExtension.class)
class BuildMartMaterialDisplaysTest {
    private final Map<Material, Object> originalItemTypes = new EnumMap<>(Material.class);
    private Field itemTypeField;

    @BeforeEach
    void installMaterialNames() throws Exception {
        ink.ziip.championshipscore.support.MessageConfigurations.defaults()
                .loadFromConfiguration(new YamlConfiguration());
        itemTypeField = Material.class.getDeclaredField("itemType");
        itemTypeField.setAccessible(true);
        for (Material material :
                List.of(Material.STONE, Material.OAK_PLANKS, Material.CUT_COPPER_SLAB)) {
            originalItemTypes.put(material, itemTypeField.get(material));
            ItemType item =
                    (ItemType)
                            Proxy.newProxyInstance(
                                    ItemType.class.getClassLoader(),
                                    new Class<?>[] {ItemType.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("translationKey"))
                                            return "block.minecraft." + material.getKey().getKey();
                                        throw new UnsupportedOperationException(method.getName());
                                    });
            itemTypeField.set(material, (Supplier<ItemType>) () -> item);
        }
    }

    @AfterEach
    void restoreMaterialRegistry() throws Exception {
        for (var entry : originalItemTypes.entrySet())
            itemTypeField.set(entry.getKey(), entry.getValue());
    }

    @Test
    void materialCountsHandleMultipartBlocksWallItemsAndDisplayOnlyParts() {
        assertAll(
                () -> {
                    BuildMartBlueprint blueprint =
                            blueprint(
                                    data(Material.OAK_PLANKS),
                                    data(Material.OAK_PLANKS),
                                    data(
                                            Material.CUT_COPPER_SLAB,
                                            Slab.class,
                                            "getType",
                                            Slab.Type.DOUBLE),
                                    data(
                                            Material.CUT_COPPER_SLAB,
                                            Slab.class,
                                            "getType",
                                            Slab.Type.TOP),
                                    data(
                                            Material.OAK_DOOR,
                                            Bisected.class,
                                            "getHalf",
                                            Bisected.Half.BOTTOM),
                                    data(
                                            Material.OAK_DOOR,
                                            Bisected.class,
                                            "getHalf",
                                            Bisected.Half.TOP),
                                    data(Material.RED_BED, Bed.class, "getPart", Bed.Part.FOOT),
                                    data(Material.RED_BED, Bed.class, "getPart", Bed.Part.HEAD),
                                    data(
                                            Material.PEONY,
                                            Bisected.class,
                                            "getHalf",
                                            Bisected.Half.BOTTOM),
                                    data(
                                            Material.PEONY,
                                            Bisected.class,
                                            "getHalf",
                                            Bisected.Half.TOP));
                    assertEquals(
                            Map.of(
                                    Material.OAK_PLANKS,
                                    2,
                                    Material.CUT_COPPER_SLAB,
                                    3,
                                    Material.OAK_DOOR,
                                    1,
                                    Material.RED_BED,
                                    1,
                                    Material.PEONY,
                                    1),
                            BuildMartMaterialDisplays.materials(blueprint));
                },
                () -> {
                    assertEquals(
                            Map.of(Material.OAK_STAIRS, 1, Material.OAK_TRAPDOOR, 1),
                            BuildMartMaterialDisplays.materials(
                                    blueprint(
                                            data(
                                                    Material.OAK_STAIRS,
                                                    Stairs.class,
                                                    "getHalf",
                                                    Bisected.Half.TOP),
                                            data(
                                                    Material.OAK_TRAPDOOR,
                                                    TrapDoor.class,
                                                    "getHalf",
                                                    Bisected.Half.TOP))));
                },
                () -> {
                    assertEquals(
                            Map.of(
                                    Material.OAK_SIGN,
                                    1,
                                    Material.RED_BANNER,
                                    1,
                                    Material.WARPED_HANGING_SIGN,
                                    1,
                                    Material.FLOWER_POT,
                                    4,
                                    Material.PINK_TULIP,
                                    1,
                                    Material.DEAD_BUSH,
                                    1,
                                    Material.PISTON,
                                    1,
                                    Material.AZALEA,
                                    1,
                                    Material.FLOWERING_AZALEA,
                                    1),
                            BuildMartMaterialDisplays.materials(
                                    blueprint(
                                            data(Material.OAK_WALL_SIGN),
                                            data(Material.RED_WALL_BANNER),
                                            data(Material.WARPED_WALL_HANGING_SIGN),
                                            data(Material.POTTED_PINK_TULIP),
                                            data(Material.POTTED_DEAD_BUSH),
                                            data(Material.PISTON),
                                            data(Material.PISTON_HEAD),
                                            data(Material.POTTED_AZALEA_BUSH),
                                            data(Material.POTTED_FLOWERING_AZALEA_BUSH))));
                });
    }

    @Test
    void namesUseClientTranslationsAndRowsContainTwoMaterials() {
        Component text =
                BuildMartMaterialDisplays.text(
                        blueprint(
                                data(Material.OAK_PLANKS),
                                data(Material.OAK_PLANKS),
                                data(Material.STONE),
                                data(Material.CUT_COPPER_SLAB)));
        assertEquals(
                "block.minecraft.cut_copper_slab × 1  block.minecraft.oak_planks × 2\n"
                        + "block.minecraft.stone × 1",
                flatten(text));
        assertEquals(3, translationCount(text));
    }

    @Test
    void configurableRowsKeepTranslationNodesAndReadTheLatestAcceptedFormat() throws Exception {
        var config = ink.ziip.championshipscore.support.MessageConfigurations.defaults();
        var document = new YamlConfiguration();
        document.set("buildmart.material-display.columns", 1);
        document.set("buildmart.material-display.style", "&6");
        document.set("buildmart.material-display.entry", "%count% of &b%material%");
        config.loadFromConfiguration(document);
        var blueprint = blueprint(data(Material.STONE), data(Material.OAK_PLANKS));
        var text = BuildMartMaterialDisplays.text(blueprint);
        assertEquals("1 of block.minecraft.oak_planks\n1 of block.minecraft.stone", flatten(text));
        assertEquals(2, translationCount(text));
        document.set("buildmart.material-display.columns", 3);
        document.set("buildmart.material-display.separator", " | ");
        config.loadFromConfiguration(document);
        assertEquals(
                "1 of block.minecraft.oak_planks | 1 of block.minecraft.stone",
                flatten(BuildMartMaterialDisplays.text(blueprint)));
        document.set("buildmart.material-display.entry", "");
        document.set("buildmart.material-display.separator", "");
        config.loadFromConfiguration(document);
        assertEquals("", flatten(BuildMartMaterialDisplays.text(blueprint)));
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals(
                "block.minecraft.oak_planks × 1  block.minecraft.stone × 1",
                flatten(BuildMartMaterialDisplays.text(blueprint)));
    }

    private static long translationCount(Component component) {
        return (component instanceof TranslatableComponent ? 1 : 0)
                + component.children().stream()
                        .mapToLong(BuildMartMaterialDisplaysTest::translationCount)
                        .sum();
    }

    @Test
    void spawnsThreeBlocksAboveButtonAndUpdatesExistingDisplayForNewOrder() {
        Fixture fixture = new Fixture();
        BuildMartMaterialDisplays displays = new BuildMartMaterialDisplays();
        BuildSlot slot = slot(false, Material.STONE);
        displays.update(slot, new Location(fixture.world, -10.2, 73, 20.8));
        assertEquals(1, fixture.spawned.size());
        EntityState entity = fixture.spawned.getFirst();
        assertEquals(-10.5, entity.location.getX());
        assertEquals(76, entity.location.getY());
        assertEquals(20.5, entity.location.getZ());
        assertEquals("block.minecraft.stone × 1", flatten(entity.text));
        assertFalse(entity.persistent);
        slot.setBlueprint(blueprint(data(Material.OAK_PLANKS)));
        displays.update(slot, entity.location);
        assertEquals(1, fixture.spawned.size());
        assertEquals("block.minecraft.oak_planks × 1", flatten(entity.text));
    }

    @Test
    void goldenPlotsNeverSpawnMaterialDisplays() {
        Fixture fixture = new Fixture();
        new BuildMartMaterialDisplays()
                .update(slot(true, Material.STONE), new Location(fixture.world, 1, 73, 2));
        assertTrue(fixture.spawned.isEmpty());
    }

    @Test
    void completedOrEmptyOrderRemovesItsEntityAndNewOrderCanSpawnAgain() {
        Fixture fixture = new Fixture();
        BuildMartMaterialDisplays displays = new BuildMartMaterialDisplays();
        BuildSlot slot = slot(false, Material.STONE);
        Location button = new Location(fixture.world, 1, 73, 2);
        displays.update(slot, button);
        slot.clear();
        displays.update(slot, button);
        assertFalse(fixture.spawned.getFirst().valid);
        slot.setBlueprint(blueprint(data(Material.STONE)));
        displays.update(slot, button);
        displays.remove(slot);
        assertEquals(2, fixture.spawned.size());
        assertFalse(fixture.spawned.getLast().valid);
    }

    @Test
    void gameCleanupRemovesEveryDisplayAndIsIdempotent() {
        Fixture fixture = new Fixture();
        BuildMartMaterialDisplays displays = new BuildMartMaterialDisplays();
        for (int index = 0; index < 3; index++) {
            displays.update(slot(false, Material.STONE), new Location(fixture.world, index, 73, 2));
        }
        displays.clear();
        displays.clear();
        assertEquals(3, fixture.spawned.size());
        assertTrue(fixture.spawned.stream().noneMatch(entity -> entity.valid));
    }

    private static BuildSlot slot(boolean golden, Material material) {
        BuildSlot slot = new BuildSlot(0, golden, null, null);
        slot.setBlueprint(blueprint(data(material)));
        return slot;
    }

    private static BuildMartBlueprint blueprint(BlockData... data) {
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int index = 0; index < data.length; index++) {
            blocks.add(new BlueprintBlock(index % 7, index / 7, 0, data[index]));
        }
        return new BuildMartBlueprint("test", "test", 1, blocks);
    }

    private static BlockData data(Material material) {
        return data(material, BlockData.class, "unused", null);
    }

    private static BlockData data(Material material, Class<?> type, String property, Object value) {
        return (BlockData)
                Proxy.newProxyInstance(
                        BlockData.class.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMaterial")) return material;
                            if (method.getName().equals(property)) return value;
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static String flatten(Component component) {
        String own =
                component instanceof TextComponent text
                        ? text.content()
                        : component instanceof TranslatableComponent translation
                                ? translation.key()
                                : "";
        return own
                + component.children().stream()
                        .map(BuildMartMaterialDisplaysTest::flatten)
                        .collect(java.util.stream.Collectors.joining());
    }

    private static final class Fixture {
        final List<EntityState> spawned = new ArrayList<>();
        final World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) -> {
                                    if (!method.getName().equals("spawn"))
                                        throw new UnsupportedOperationException(method.getName());
                                    EntityState state = new EntityState((Location) args[0]);
                                    spawned.add(state);
                                    @SuppressWarnings("unchecked")
                                    Consumer<TextDisplay> initialize =
                                            (Consumer<TextDisplay>) args[2];
                                    initialize.accept(state.entity);
                                    return state.entity;
                                });
    }

    private static final class EntityState {
        final Location location;
        boolean valid = true;
        boolean persistent = true;
        Component text;
        final TextDisplay entity =
                (TextDisplay)
                        Proxy.newProxyInstance(
                                TextDisplay.class.getClassLoader(),
                                new Class<?>[] {TextDisplay.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "isValid" -> valid;
                                            case "remove" -> {
                                                valid = false;
                                                yield null;
                                            }
                                            case "text" -> {
                                                text = (Component) args[0];
                                                yield null;
                                            }
                                            case "setPersistent" -> {
                                                persistent = (boolean) args[0];
                                                yield null;
                                            }
                                            default -> {
                                                if (method.getName().startsWith("set")) yield null;
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                            }
                                        });

        EntityState(Location location) {
            this.location = location;
        }
    }
}
