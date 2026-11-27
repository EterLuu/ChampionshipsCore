package ink.ziip.championshipscore.util.world;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

class ArenaWorldListenerTest {
    @ParameterizedTest(name = "{0} {1} fightWorld={2} -> unwanted={3}")
    @MethodSource("classifications")
    void classifiesMobsWithoutReadingEntityState(
            Class<? extends Entity> type,
            SpawnReason reason,
            boolean fightWorld,
            boolean unwanted) {
        assertEquals(unwanted, ArenaWorldListener.isUnwantedMob(entity(type), reason, fightWorld));
    }

    static Stream<Arguments> classifications() {
        var rows = new ArrayList<Arguments>();
        // Drowned from saved maps and every non-plugin spawn path are removed.
        for (SpawnReason reason : SpawnReason.values())
            rows.add(Arguments.of(Drowned.class, reason, false, reason != SpawnReason.CUSTOM));
        // Enemies outside the Monster hierarchy are still enemies.
        for (Class<? extends Entity> type :
                List.of(Zombie.class, Slime.class, Ghast.class, Phantom.class))
            rows.add(Arguments.of(type, SpawnReason.DEFAULT, false, true));
        // Plugin mobs survive both initial spawning and saved chunk cleanup.
        for (Class<? extends Entity> type :
                List.of(
                        Chicken.class,
                        Zombie.class,
                        Husk.class,
                        Skeleton.class,
                        Spider.class,
                        Creeper.class,
                        Cod.class,
                        Bat.class)) rows.add(Arguments.of(type, SpawnReason.CUSTOM, false, false));
        // Fight worlds allow hostiles but still reject environmental animals.
        rows.add(Arguments.of(Enderman.class, SpawnReason.NATURAL, true, false));
        for (Class<? extends Entity> type : List.of(Chicken.class, Cod.class, Bat.class))
            rows.add(Arguments.of(type, SpawnReason.DEFAULT, true, true));
        // The vanilla dragon before fight preparation and non-mob map entities are retained.
        for (Class<? extends Entity> type :
                List.of(
                        EnderDragon.class,
                        EnderCrystal.class,
                        ArmorStand.class,
                        ItemFrame.class,
                        TextDisplay.class,
                        Player.class,
                        Minecart.class,
                        Villager.class))
            rows.add(Arguments.of(type, SpawnReason.DEFAULT, false, false));
        return rows.stream();
    }

    @Test
    void protectsLiveCoralFromFadingOnlyInArenaWorlds() throws Exception {
        for (Material material : Material.values()) {
            String name = material.name();
            boolean expected = name.contains("CORAL") && !name.startsWith("DEAD_");
            assertEquals(expected, ArenaWorldListener.isLiveCoral(material), name);
        }
        assertFalse(ArenaWorldListener.isLiveCoral(null));

        WorldManager worldManager = allocate(WorldManager.class);
        set(worldManager, "arenaWorlds", Set.of("hoty_codusk_basin"));
        ArenaWorldListener listener = new ArenaWorldListener(null, worldManager);

        World arenaWorld = world("hoty_codusk_basin");
        World bingoWorld = world("bingo");

        BlockFadeEvent arenaCoral = fadeEvent(arenaWorld, Material.TUBE_CORAL_FAN);
        listener.onCoralFade(arenaCoral);
        assertTrue(arenaCoral.isCancelled());

        BlockFadeEvent arenaIce = fadeEvent(arenaWorld, Material.ICE);
        listener.onCoralFade(arenaIce);
        assertFalse(arenaIce.isCancelled());

        BlockFadeEvent bingoCoral = fadeEvent(bingoWorld, Material.HORN_CORAL_BLOCK);
        listener.onCoralFade(bingoCoral);
        assertFalse(bingoCoral.isCancelled());
    }

    private static World world(String name) {
        return (World)
                Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[] {World.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> name;
                                    case "equals" -> proxy == args[0];
                                    case "hashCode" -> name.hashCode();
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    private static BlockFadeEvent fadeEvent(World world, Material material) {
        Block block =
                (Block)
                        Proxy.newProxyInstance(
                                Block.class.getClassLoader(),
                                new Class<?>[] {Block.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getWorld" -> world;
                                            case "getType" -> material;
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        BlockState state =
                (BlockState)
                        Proxy.newProxyInstance(
                                BlockState.class.getClassLoader(),
                                new Class<?>[] {BlockState.class},
                                (proxy, method, args) -> {
                                    throw new UnsupportedOperationException(method.getName());
                                });
        return new BlockFadeEvent(block, state);
    }

    private static Entity entity(Class<? extends Entity> type) {
        return (Entity)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            throw new AssertionError(
                                    "Classification must not access entity state: " + method);
                        });
    }
}
