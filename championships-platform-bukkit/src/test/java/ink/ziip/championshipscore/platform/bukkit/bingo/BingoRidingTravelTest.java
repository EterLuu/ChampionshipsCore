package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BingoRidingTravelTest {
    @Test
    void onlyCountsAcceptedHorseRidingAndExcludesTeleports() {
        org.bukkit.entity.AbstractHorse horse = proxy(org.bukkit.entity.AbstractHorse.class, null);
        org.bukkit.entity.Player rider = proxy(org.bukkit.entity.Player.class, horse);
        Location from = new Location(null, 0, 64, 0);
        Location to = new Location(null, 3, 64, 4);
        var move = new org.bukkit.event.player.PlayerMoveEvent(rider, from, to);
        assertEquals(500, BingoRidingTravel.distance(move));
        move.setCancelled(true);
        assertEquals(0, BingoRidingTravel.distance(move));
        assertEquals(0, BingoRidingTravel.distance(new org.bukkit.event.player.PlayerTeleportEvent(
                rider, from, to)));
        assertEquals(0, BingoRidingTravel.distance(new org.bukkit.event.player.PlayerMoveEvent(
                proxy(org.bukkit.entity.Player.class, null), from, to)));
    }

    @Test
    void mapsAllMountFamiliesAndVariantsToVanillaStatistics() {
        java.util.Map<Class<? extends org.bukkit.entity.Entity>, org.bukkit.Statistic> mapping = java.util.Map.ofEntries(
                java.util.Map.entry(org.bukkit.entity.Horse.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Donkey.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Mule.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.SkeletonHorse.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.ZombieHorse.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Camel.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Llama.class, org.bukkit.Statistic.HORSE_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Pig.class, org.bukkit.Statistic.PIG_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Strider.class, org.bukkit.Statistic.STRIDER_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.HappyGhast.class, org.bukkit.Statistic.HAPPY_GHAST_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Nautilus.class, org.bukkit.Statistic.NAUTILUS_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.ZombieNautilus.class, org.bukkit.Statistic.NAUTILUS_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Boat.class, org.bukkit.Statistic.BOAT_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.ChestBoat.class, org.bukkit.Statistic.BOAT_ONE_CM),
                java.util.Map.entry(org.bukkit.entity.Minecart.class, org.bukkit.Statistic.MINECART_ONE_CM));
        mapping.forEach((type, expected) -> assertEquals(expected,
                BingoRidingTravel.statistic(proxy(type, null)), type.getSimpleName()));
        assertEquals(null, BingoRidingTravel.statistic(null));
        assertEquals(null, BingoRidingTravel.statistic(proxy(org.bukkit.entity.Player.class, null)));
    }

    @Test
    void independentStreamsNeverDoubleCountAndSwitchingMountsNeverMixesTasks() {
        BingoRidingTravel travel = new BingoRidingTravel();
        UUID player = UUID.randomUUID();
        travel.record(player, org.bukkit.Statistic.NAUTILUS_ONE_CM, 600, BingoRidingTravel.Source.PLAYER);
        travel.record(player, org.bukkit.Statistic.NAUTILUS_ONE_CM, 700, BingoRidingTravel.Source.VEHICLE);
        travel.record(player, org.bukkit.Statistic.STRIDER_ONE_CM, 900, BingoRidingTravel.Source.VEHICLE);
        assertEquals(700, travel.delta(player, org.bukkit.Statistic.NAUTILUS_ONE_CM, 200));
        assertEquals(800, travel.delta(player, org.bukkit.Statistic.NAUTILUS_ONE_CM, 800));
        assertEquals(900, travel.delta(player, org.bukkit.Statistic.STRIDER_ONE_CM, 0));
        assertEquals(0, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 0));
    }

    private static <T> T proxy(Class<T> type, Object vehicle) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, (instance, method, args) ->
                        method.getName().equals("getVehicle") ? vehicle : null));
    }

    @Test
    void countsTheWholeRouteIncludingReturnTripsAndFractionalCentimeters() {
        BingoRidingTravel travel = new BingoRidingTravel();
        UUID player = UUID.randomUUID();
        Location a = new Location(null, 0, 64, 0);
        Location b = new Location(null, 0.123, 64, 0);
        for (int i = 0; i < 1000; i++) {
            travel.record(player, org.bukkit.Statistic.HORSE_ONE_CM, BingoRidingTravel.distance(a, b), BingoRidingTravel.Source.PLAYER);
            travel.record(player, org.bukkit.Statistic.HORSE_ONE_CM, BingoRidingTravel.distance(b, a), BingoRidingTravel.Source.PLAYER);
        }
        assertEquals(24600, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 100), 1);
        assertEquals(0, travel.delta(UUID.randomUUID(), org.bukkit.Statistic.HORSE_ONE_CM, 0));
    }

    @Test
    void usesMaximumInsteadOfDoubleCountingAndClearsBetweenRounds() {
        BingoRidingTravel travel = new BingoRidingTravel();
        UUID player = UUID.randomUUID();
        travel.record(player, org.bukkit.Statistic.HORSE_ONE_CM, 1000, BingoRidingTravel.Source.PLAYER);
        assertEquals(1000, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 900));
        assertEquals(1100, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 1100));
        travel.clear();
        assertEquals(0, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 0));
    }

    @Test
    void ignoresInvalidDistancesAndRetainsThreeDimensionalVanillaUnits() {
        BingoRidingTravel travel = new BingoRidingTravel();
        UUID player = UUID.randomUUID();
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -100, 0})
            travel.record(player, org.bukkit.Statistic.HORSE_ONE_CM, invalid, BingoRidingTravel.Source.PLAYER);
        assertEquals(0, travel.delta(player, org.bukkit.Statistic.HORSE_ONE_CM, 0));
        assertEquals(500, BingoRidingTravel.distance(new Location(null, 0, 0, 0),
                new Location(null, 3, 4, 0)));
        assertEquals(0, BingoRidingTravel.distance(new Location(null, 0, 0, 0),
                new Location(null, Double.NaN, 0, 0)));
    }
}
