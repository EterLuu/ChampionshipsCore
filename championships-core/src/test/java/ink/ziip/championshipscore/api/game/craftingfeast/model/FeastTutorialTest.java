package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial.Task;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

class FeastTutorialTest {
    private static FeastRequest request(Material material, int amount, int points) {
        return new FeastRequest(material.name(), material.name(), material, amount, points);
    }

    @Test
    void eachGoalUnlocksOnlyAfterThePreviousMechanicCompletes() {
        var tutorial = new FeastTutorial(true);
        assertEquals(List.of(Task.SERVE, Task.COOK, Task.FAILURE, Task.DELIVERY), tutorial.tasks());
        assertEquals(Task.SERVE, tutorial.current());
        assertFalse(tutorial.served(FeastTutorial.SECOND_TICKET));
        assertFalse(tutorial.lost(FeastTutorial.FAILURE_TICKET));
        assertFalse(tutorial.complete(Task.DELIVERY));
        assertFalse(tutorial.deliveryOpen());
        assertTrue(tutorial.served(FeastTutorial.FIRST_TICKET));
        assertFalse(tutorial.served(FeastTutorial.FIRST_TICKET));
        assertEquals(Task.COOK, tutorial.current());
        assertTrue(tutorial.served(FeastTutorial.SECOND_TICKET));
        assertEquals(Task.FAILURE, tutorial.current());
        assertFalse(
                tutorial.deliveryOpen(), "no delivery countdown during the failure demonstration");
        assertFalse(tutorial.lost(FeastTutorial.FIRST_TICKET));
        assertTrue(tutorial.lost(FeastTutorial.FAILURE_TICKET));
        assertTrue(tutorial.deliveryOpen());
        assertFalse(tutorial.lost(FeastTutorial.FAILURE_TICKET));
        assertTrue(tutorial.complete(Task.DELIVERY));
        assertFalse(tutorial.complete(Task.DELIVERY));
        assertFalse(tutorial.complete(null));
        assertTrue(tutorial.finished());
        assertEquals(4, tutorial.completed());
        assertNull(tutorial.current());
        assertFalse(tutorial.deliveryOpen(), "practice must not keep dispatching tutorial batches");
    }

    @Test
    void mapsWithoutDeliveryStillDemonstrateFailureThenFinish() {
        var tutorial = new FeastTutorial(false);
        assertEquals(List.of(Task.SERVE, Task.COOK, Task.FAILURE), tutorial.tasks());
        assertFalse(tutorial.complete(Task.DELIVERY));
        for (Task task : tutorial.tasks()) assertTrue(tutorial.complete(task));
        assertFalse(tutorial.deliveryOpen());
        assertTrue(tutorial.finished());
        assertFalse(tutorial.complete(Task.DELIVERY));
    }

    @Test
    void fixedSingleItemsDoNotChangeWithMenuPricesOrderOrAmounts() {
        var menu =
                List.of(
                        request(Material.BOOK, 1, 1),
                        request(Material.GLASS, 8, 300),
                        request(Material.BAKED_POTATO, 3, 200),
                        request(Material.BREAD, 4, 100));
        for (var choices : List.of(menu, menu.reversed())) {
            assertEquals(Material.BREAD, FeastTutorial.craftDish(choices).material());
            assertEquals(Material.GLASS, FeastTutorial.cookDish(choices).material());
            assertEquals(Material.BAKED_POTATO, FeastTutorial.deliveryDish(choices).material());
            assertEquals(1, FeastTutorial.craftDish(choices).amount());
            assertEquals(1, FeastTutorial.cookDish(choices).amount());
            assertEquals(1, FeastTutorial.deliveryDish(choices).amount());
        }
        var iron = FeastTutorial.deliveryIronDish("铁锭");
        assertEquals(Material.IRON_INGOT, iron.material());
        assertEquals(1, iron.amount());
        assertThrows(
                IllegalArgumentException.class,
                () -> FeastTutorial.cookDish(List.of(menu.getFirst())));
    }

    @Test
    void bedrockGuestTimesOutOnlyTenSecondsAfterSittingAndOnlyOnce() {
        var request = FeastTutorial.failureDish("基岩");
        assertEquals(Material.BEDROCK, request.material());
        var order = new FeastOrder(request.id(), FeastTutorial.FAILURE_PATIENCE_SECONDS);
        for (int i = 0; i < 30; i++) assertFalse(order.tick());
        order.reachCounter();
        assertTrue(order.advanceOrdering(FeastOrder.ORDERING_TICKS));
        for (int i = 0; i < 30; i++) assertFalse(order.tick());
        order.seat();
        for (int i = 0; i < 9; i++) assertFalse(order.tick());
        assertEquals(1, order.remaining());
        assertTrue(order.tick());
        assertEquals(FeastOrder.Phase.LEAVING, order.phase());
        assertFalse(order.tick());
        assertEquals(-1, order.serve(request.id(), 100, 40, 5, 100));
    }

    @Test
    void emptyShelvesDoNotDelayArrivalAndTheWholePairExpiresTogether() {
        var delivery = new FeastDelivery(FeastTutorial.DELIVERY_TIMING, 5);
        var potato = request(Material.BAKED_POTATO, 1, 100);
        var iron = FeastTutorial.deliveryIronDish("铁锭");
        var first = delivery.tick(i -> i % 2 == 0 ? potato : iron);
        assertEquals(FeastDelivery.Event.DISPATCHED, first.event());
        assertEquals(1, first.orders().size());
        assertFalse(delivery.approachEarly(6), "Both orders must be issued before early arrival");
        assertEquals(FeastDelivery.Event.ORDER, delivery.tick(i -> iron).event());
        assertEquals(
                List.of(potato, iron),
                delivery.orders().stream().map(FeastDelivery.Order::request).toList());
        for (int i = 1; i < 45; i++) delivery.tick(n -> fail("third order"));
        assertEquals(FeastDelivery.Phase.WAITING, delivery.phase());
        assertEquals(60, delivery.countdown());
        for (int i = 0; i < 59; i++)
            assertEquals(FeastDelivery.Event.NONE, delivery.tick(n -> fail("third order")).event());
        var expired = delivery.tick(n -> fail("third order"));
        assertEquals(FeastDelivery.Event.EXPIRED, expired.event());
        assertEquals(2, expired.orders().size());
        assertTrue(delivery.pickUp().isEmpty());
        for (int i = 0; i < 19; i++) delivery.tick(n -> potato);
        assertEquals(FeastDelivery.Event.DISPATCHED, delivery.tick(n -> potato).event());
    }

    @Test
    void earlyRiderStillTravelsThenWaitsAndCannotPickUpTwice() {
        var delivery = new FeastDelivery(FeastTutorial.DELIVERY_TIMING, 5);
        var dish = request(Material.BAKED_POTATO, 1, 100);
        assertFalse(delivery.approachEarly(6));
        delivery.tick(i -> dish);
        assertFalse(delivery.approachEarly(6));
        delivery.tick(i -> FeastTutorial.deliveryIronDish("铁锭"));
        assertTrue(delivery.approachEarly(6));
        assertEquals(FeastDelivery.Phase.DISPATCHED, delivery.phase());
        assertEquals(6, delivery.countdown());
        assertTrue(delivery.pickUp().isEmpty(), "Shortening the ride does not skip arrival");
        for (int i = 5; i > 0; i--) {
            assertEquals(FeastDelivery.Event.NONE, delivery.tick(n -> fail("third order")).event());
            assertEquals(i, delivery.countdown());
            assertFalse(delivery.approachEarly(6), "Repeated shelf checks cannot postpone arrival");
        }
        assertEquals(FeastDelivery.Event.ARRIVED, delivery.tick(n -> fail("third order")).event());
        assertEquals(60, delivery.countdown());
        assertFalse(delivery.approachEarly(6));
        assertEquals(2, delivery.pickUp().size());
        assertTrue(delivery.pickUp().isEmpty());
        assertFalse(delivery.approachEarly(6));
        assertThrows(IllegalArgumentException.class, () -> delivery.approachEarly(0));
    }
}
