package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FeastOrderTest {
    @Test
    void patienceStartsOnlyAfterSeating() {
        FeastOrder order = new FeastOrder("fish", 3);
        for (int i = 0; i < 100; i++) assertFalse(order.tick());
        assertEquals(3, order.remaining());
        assertEquals(-1, order.serve("fish", 100, 40, 0, 100));
        seat(order);
        assertFalse(order.tick());
        assertFalse(order.tick());
        assertTrue(order.tick());
        assertEquals(FeastOrder.Phase.LEAVING, order.phase());
        assertFalse(order.tick());
        assertEquals(-1, order.serve("fish", 100, 40, 0, 100));
    }

    @Test
    void wrongDishDoesNotConsumeOrderAndDuplicateServingCannotScore() {
        FeastOrder order = new FeastOrder("fish", 10);
        seat(order);
        order.tick();
        assertEquals(-1, order.serve("bun", 100, 40, 1, 100));
        assertEquals(FeastOrder.Phase.WAITING, order.phase());
        assertEquals(141, order.serve("fish", 100, 40, 1, 100));
        assertEquals(-1, order.serve("fish", 100, 40, 1, 100));
        seat(order);
        assertEquals(FeastOrder.Phase.LEAVING, order.phase());
    }

    @Test
    void tipsFollowRemainingPatienceAndStreakBonusIsCapped() {
        for (int elapsed = 0; elapsed < 90; elapsed++) {
            FeastOrder order = new FeastOrder("soup", 90);
            seat(order);
            for (int i = 0; i < elapsed; i++) order.tick();
            int score = order.serve("soup", 120, 40, 100, 100);
            assertEquals(120 + 40 * (90 - elapsed) / 90 + 25, score);
        }
    }

    @Test
    void reputationScalesOnlyThePatienceTip() {
        for (int percent : new int[] {50, 75, 100, 125, 150}) {
            FeastOrder order = new FeastOrder("soup", 90);
            seat(order);
            for (int i = 0; i < 30; i++) order.tick();
            assertEquals(
                    120 + 40 * 60 * percent / 9000 + 10, order.serve("soup", 120, 40, 2, percent));
        }
        FeastOrder order = new FeastOrder("soup", 90);
        seat(order);
        assertThrows(IllegalArgumentException.class, () -> order.serve("soup", 1, 1, 0, -1));
        assertEquals(FeastOrder.Phase.WAITING, order.phase());
    }

    @Test
    void takingTheOrderSkipsOnlyTheRemainingCounterPause() {
        FeastOrder order = new FeastOrder("fish", 90);
        assertFalse(order.takeOrder(), "Cannot take an order before reaching the counter");
        order.reachCounter();
        order.advanceOrdering(40);
        assertTrue(order.takeOrder());
        assertFalse(order.takeOrder(), "A taken order cannot be taken twice");
        assertEquals(FeastOrder.Phase.ORDERING, order.phase());
        assertTrue(order.advanceOrdering(5));
        assertEquals(FeastOrder.Phase.SEATING, order.phase());
        assertEquals(90, order.remaining(), "Patience still starts only after seating");
        assertFalse(order.takeOrder());
    }

    @Test
    void cleanupAndDepartureCannotReopenOrders() {
        FeastOrder order = new FeastOrder("fish", 1);
        order.leave();
        seat(order);
        assertEquals(-1, order.serve("fish", 1, 0, 0, 100));
        order.finish();
        seat(order);
        assertFalse(order.tick());
        assertEquals(FeastOrder.Phase.FINISHED, order.phase());
    }

    private static void seat(FeastOrder order) {
        order.reachCounter();
        order.advanceOrdering(160);
        order.seat();
    }

    @Test
    void mustStopAtCounterForExactlyEightSecondsBeforeSeating() {
        FeastOrder order = new FeastOrder("fish", 90);
        order.seat();
        assertEquals(FeastOrder.Phase.ENTERING, order.phase());
        order.reachCounter();
        for (int i = 0; i < 31; i++) {
            order.reachCounter(); // repeated arrival cannot reset the countdown
            assertFalse(order.advanceOrdering(5));
            assertFalse(order.tick());
            order.seat();
            assertEquals(FeastOrder.Phase.ORDERING, order.phase());
            assertEquals(-1, order.serve("fish", 100, 40, 0, 100));
        }
        assertEquals(1, order.orderingSeconds());
        assertTrue(order.advanceOrdering(5));
        assertEquals(FeastOrder.Phase.SEATING, order.phase());
        assertEquals(90, order.remaining());
        assertFalse(order.tick());
        assertEquals(-1, order.serve("fish", 100, 40, 0, 100));
        order.seat();
        assertEquals(FeastOrder.Phase.WAITING, order.phase());
    }

    @Test
    void cancelledCounterWaitCannotRestartTheCustomer() {
        for (boolean finish : new boolean[] {false, true}) {
            FeastOrder order = new FeastOrder("fish", 90);
            order.reachCounter();
            order.advanceOrdering(80);
            if (finish) order.finish();
            else order.leave();
            assertFalse(order.advanceOrdering(80));
            order.reachCounter();
            order.seat();
            assertEquals(
                    finish ? FeastOrder.Phase.FINISHED : FeastOrder.Phase.LEAVING, order.phase());
        }
    }

    @Test
    void rejectsInvalidOrders() {
        assertThrows(IllegalArgumentException.class, () -> new FeastOrder("fish", 0));
        assertThrows(IllegalArgumentException.class, () -> new FeastOrder("", 3));
    }
}
