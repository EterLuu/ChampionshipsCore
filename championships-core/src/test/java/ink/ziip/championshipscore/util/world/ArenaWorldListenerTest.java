package ink.ziip.championshipscore.util.world;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
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
