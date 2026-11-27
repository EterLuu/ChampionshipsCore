package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

class FeastDeliveryTest {
    private static final FeastRequest BREAD =
            new FeastRequest("bread", "面包", Material.BREAD, 1, 100);
    private static final IntFunction<FeastRequest> MENU = index -> BREAD;

    /** first 3s, eta 10s, an order every 4s, at most 3 orders, wait 5s, cooldown 2s. */
    private static FeastDelivery delivery(int slots) {
        return new FeastDelivery(new FeastDelivery.Timing(3, 10, 4, 3, 5, 2), slots);
    }

    private static List<FeastDelivery.Tick> run(FeastDelivery delivery, int seconds) {
        List<FeastDelivery.Tick> ticks = new ArrayList<>();
        for (int i = 0; i < seconds; i++) ticks.add(delivery.tick(MENU));
        return ticks;
    }

    @Test
    void dispatchesWithAFirstOrderAndAddsOrdersUntilTheRiderArrives() {
        var delivery = delivery(5);
        var ticks = run(delivery, 3);
        assertEquals(FeastDelivery.Event.DISPATCHED, ticks.getLast().event());
        assertEquals(FeastDelivery.Phase.DISPATCHED, delivery.phase());
        assertEquals(10, delivery.countdown(), "The board shows the rider's arrival time");
        assertEquals(1, delivery.orders().size());

        ticks = run(delivery, 10);
        assertEquals(
                2,
                ticks.stream().filter(t -> t.event() == FeastDelivery.Event.ORDER).count(),
                "Orders at 4s and 8s into the ride");
        assertEquals(FeastDelivery.Event.ARRIVED, ticks.getLast().event());
        assertEquals(FeastDelivery.Phase.WAITING, delivery.phase());
        assertEquals(5, delivery.countdown());
        assertEquals(List.of(1, 2, 3), delivery.orders().stream().map(o -> o.number()).toList());
        assertEquals(List.of(0, 1, 2), delivery.orders().stream().map(o -> o.slot()).toList());
    }

    @Test
    void noOrderAppearsOnceTheRiderHasArrived() {
        var delivery = new FeastDelivery(new FeastDelivery.Timing(1, 10, 5, 9, 30, 2), 9);
        run(delivery, 1);
        var ticks = run(delivery, 10);
        assertEquals(FeastDelivery.Event.ARRIVED, ticks.getLast().event(), "Not a new order");
        int batch = delivery.orders().size();
        assertTrue(
                run(delivery, 29).stream()
                        .noneMatch(
                                t ->
                                        t.event() == FeastDelivery.Event.ORDER
                                                || t.event() == FeastDelivery.Event.DISPATCHED));
        assertEquals(batch, delivery.orders().size());
    }

    @Test
    void ordersNeverExceedTheShelfOrTheBatchLimit() {
        var delivery = new FeastDelivery(new FeastDelivery.Timing(1, 60, 5, 9, 30, 2), 2);
        run(delivery, 59);
        assertEquals(2, delivery.orders().size(), "Two slots, two orders");
    }

    @Test
    void theRiderLeavesAfterTheWaitAndTheWholeBatchIsLost() {
        var delivery = delivery(5);
        run(delivery, 13);
        assertEquals(FeastDelivery.Phase.WAITING, delivery.phase());
        var ticks = run(delivery, 5);
        var expired = ticks.getLast();
        assertEquals(FeastDelivery.Event.EXPIRED, expired.event());
        assertEquals(3, expired.orders().size());
        assertTrue(delivery.orders().isEmpty());
        assertEquals(FeastDelivery.Phase.IDLE, delivery.phase());
        assertEquals(2, delivery.countdown(), "Cooldown before the next rider");
        assertTrue(delivery.pickUp().isEmpty(), "Nothing left to pick up");
    }

    @Test
    void pickUpTakesTheWholeBatchOnlyWhileTheRiderWaits() {
        var delivery = delivery(5);
        run(delivery, 3);
        assertTrue(delivery.pickUp().isEmpty(), "The rider is still on the way");
        run(delivery, 10);
        assertEquals(3, delivery.pickUp().size());
        assertEquals(FeastDelivery.Phase.IDLE, delivery.phase());
        var next = run(delivery, 2).getLast();
        assertEquals(FeastDelivery.Event.DISPATCHED, next.event());
        assertEquals(4, next.orders().getFirst().number(), "Delivery numbers keep counting");
        assertEquals(0, next.orders().getFirst().slot(), "Slots are reused from the first");
    }

    @Test
    void everyRestaurantDrawsTheSameDeliverySequence() {
        List<FeastRequest> menu = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            menu.add(new FeastRequest("r" + i, "r" + i, Material.BREAD, 1, 100));
        var first = new FeastOrderSequence(menu, 42, 5);
        var second = new FeastOrderSequence(menu, 42, 5);
        for (int i = 0; i < 20; i++) assertEquals(first.delivery(i), second.delivery(i));
        var guests = new FeastOrderSequence(menu, 42, 5);
        for (int i = 0; i < 20; i++) first.delivery(i);
        for (int i = 0; i < 10; i++)
            assertEquals(
                    guests.guest(i).request(),
                    first.guest(i).request(),
                    "Delivery draws never shift the guests");
    }

    @Test
    void rejectsInvalidTimelines() {
        assertThrows(
                IllegalArgumentException.class, () -> new FeastDelivery.Timing(0, 10, 4, 3, 30, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastDelivery.Timing(1, 10, 4, 3, 121, 2));
        assertThrows(
                IllegalArgumentException.class, () -> new FeastDelivery.Timing(1, 10, 4, 0, 30, 2));
        assertThrows(IllegalArgumentException.class, () -> delivery(0));
    }
}
