package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

class WorkerObjectivesTest {
    @Test
    void completedCellsCanBeExcludedBeforeInventoryScanning() {
        WorkerObjectives objectives =
                new WorkerObjectives(
                        java.util.List.of(
                                new BingoTaskSpec(
                                        3,
                                        "jumps",
                                        "statistic",
                                        Map.of("statistic", "JUMP", "target", "2"))));
        Player player = playerWithStatistic(2);

        assertEquals(java.util.List.of(3), objectives.matching(player, cell -> true));
        assertTrue(objectives.matching(player, cell -> cell != 3).isEmpty());
    }

    @Test
    void allOfRequiresTheConfiguredAmountOfEveryMember() {
        WorkerObjectives objectives =
                new WorkerObjectives(
                        java.util.List.of(
                                new BingoTaskSpec(
                                        4,
                                        "all-furnaces",
                                        "all_of",
                                        Map.of("materials", "FURNACE,SMOKER", "count", "2"))));

        assertTrue(
                objectives
                        .matching(
                                playerWithInventory(
                                        stack(Material.FURNACE, 2), stack(Material.SMOKER, 1)),
                                cell -> true)
                        .isEmpty());
        assertEquals(
                java.util.List.of(4),
                objectives.matching(
                        playerWithInventory(stack(Material.FURNACE, 2), stack(Material.SMOKER, 2)),
                        cell -> true));
    }

    @Test
    void eventSignalsAndDistinctProgressAreEvaluatedIndependently() {
        WorkerObjectives objectives =
                new WorkerObjectives(
                        java.util.List.of(
                                new BingoTaskSpec(
                                        5,
                                        "eat-cookie",
                                        "event",
                                        Map.of("trigger", "eat", "param", "COOKIE", "count", "1")),
                                new BingoTaskSpec(
                                        6,
                                        "craft-unique",
                                        "event",
                                        Map.of(
                                                "trigger",
                                                "craft_unique",
                                                "param",
                                                "",
                                                "count",
                                                "2"))));
        Player player = playerWithInventory();

        assertEquals(
                java.util.List.of(5),
                objectives.matchingEventSignal(player, "eat", "cookie", cell -> true));
        assertTrue(objectives.matching(player, cell -> true).isEmpty());
        objectives.recordDistinct(player, "craft_unique", "STONE_PICKAXE");
        objectives.recordDistinct(player, "craft_unique", "STONE_PICKAXE");
        assertTrue(objectives.matching(player, cell -> true).isEmpty());
        objectives.recordDistinct(player, "craft_unique", "FURNACE");
        assertEquals(java.util.List.of(6), objectives.matching(player, cell -> true));
    }

    @Test
    void uniqueCollectScansAllConfiguredWorkBlocks() {
        WorkerObjectives objectives =
                new WorkerObjectives(
                        java.util.List.of(
                                new BingoTaskSpec(
                                        7,
                                        "work-blocks",
                                        "event",
                                        Map.of(
                                                "trigger", "unique_collect",
                                                "members",
                                                        "CRAFTING_TABLE,FURNACE,BLAST_FURNACE,SMOKER,STONECUTTER,LOOM,CARTOGRAPHY_TABLE",
                                                "count", "7"))));
        assertEquals(
                java.util.List.of(7),
                objectives.matching(
                        playerWithInventory(
                                stack(Material.CRAFTING_TABLE, 1),
                                stack(Material.FURNACE, 1),
                                stack(Material.BLAST_FURNACE, 1),
                                stack(Material.SMOKER, 1),
                                stack(Material.STONECUTTER, 1),
                                stack(Material.LOOM, 1),
                                stack(Material.CARTOGRAPHY_TABLE, 1)),
                        cell -> true));
    }

    @Test
    void horseMovementCompensatesStaleStatisticsWithoutAddingBothSources() {
        WorkerObjectives objectives =
                new WorkerObjectives(
                        java.util.List.of(
                                new BingoTaskSpec(
                                        9,
                                        "horse",
                                        "statistic",
                                        Map.of("statistic", "HORSE_ONE_CM", "target", "20000"))));
        Player player = playerWithStatistic(100);
        objectives.captureBaselines(player);
        objectives.recordRidingMovement(
                player,
                org.bukkit.Statistic.HORSE_ONE_CM,
                19999,
                ink.ziip.championshipscore.platform.bukkit.bingo.BingoRidingTravel.Source.PLAYER);
        assertEquals(java.util.List.of(), objectives.matching(player, cell -> true));
        objectives.recordRidingMovement(
                player,
                org.bukkit.Statistic.HORSE_ONE_CM,
                1,
                ink.ziip.championshipscore.platform.bukkit.bingo.BingoRidingTravel.Source.PLAYER);
        assertEquals(java.util.List.of(9), objectives.matching(player, cell -> true));
        assertEquals(java.util.List.of(), objectives.matching(player, cell -> false));
        objectives.replace(
                java.util.List.of(
                        new BingoTaskSpec(
                                9,
                                "horse",
                                "statistic",
                                Map.of("statistic", "HORSE_ONE_CM", "target", "20000"))));
        assertEquals(java.util.List.of(), objectives.matching(player, cell -> true));
    }

    @Test
    void everyRidingTaskCompletesFromVehicleOrPlayerObservationsWithStaleVanillaStats() {
        for (org.bukkit.Statistic statistic :
                new org.bukkit.Statistic[] {
                    org.bukkit.Statistic.HORSE_ONE_CM, org.bukkit.Statistic.BOAT_ONE_CM,
                    org.bukkit.Statistic.MINECART_ONE_CM, org.bukkit.Statistic.PIG_ONE_CM,
                    org.bukkit.Statistic.STRIDER_ONE_CM, org.bukkit.Statistic.HAPPY_GHAST_ONE_CM,
                    org.bukkit.Statistic.NAUTILUS_ONE_CM
                }) {
            for (var source :
                    ink.ziip.championshipscore.platform.bukkit.bingo.BingoRidingTravel.Source
                            .values()) {
                WorkerObjectives objectives =
                        new WorkerObjectives(
                                java.util.List.of(
                                        new BingoTaskSpec(
                                                0,
                                                "riding",
                                                "statistic",
                                                Map.of(
                                                        "statistic",
                                                        statistic.name(),
                                                        "target",
                                                        "10000"))));
                Player player = playerWithStatistic(123);
                objectives.captureBaselines(player);
                objectives.recordRidingMovement(player, statistic, 9999, source);
                assertEquals(java.util.List.of(), objectives.matching(player, cell -> true));
                objectives.recordRidingMovement(player, statistic, 1, source);
                assertEquals(
                        java.util.List.of(0),
                        objectives.matching(player, cell -> true),
                        statistic.name());
            }
        }
    }

    private static Player playerWithStatistic(int value) {
        UUID playerId = UUID.randomUUID();
        return proxy(
                Player.class,
                (method, args) ->
                        switch (method.getName()) {
                            case "getUniqueId" -> playerId;
                            case "getStatistic" -> value;
                            default -> defaultValue(method.getReturnType());
                        });
    }

    private static Player playerWithInventory(ItemStack... contents) {
        UUID playerId = UUID.randomUUID();
        PlayerInventory inventory =
                proxy(
                        PlayerInventory.class,
                        (method, args) ->
                                switch (method.getName()) {
                                    case "getContents" -> contents;
                                    default -> defaultValue(method.getReturnType());
                                });
        return proxy(
                Player.class,
                (method, args) ->
                        switch (method.getName()) {
                            case "getUniqueId" -> playerId;
                            case "getInventory" -> inventory;
                            default -> defaultValue(method.getReturnType());
                        });
    }

    private static ItemStack stack(Material material, int amount) {
        // Paper's public ItemStack constructor needs a live registry. The protected empty
        // constructor
        // lets this headless unit test provide only the two properties the objective scanner reads.
        return new ItemStack() {
            @Override
            public Material getType() {
                return material;
            }

            @Override
            public int getAmount() {
                return amount;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (ignored, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new IllegalArgumentException("Unsupported primitive " + type);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
