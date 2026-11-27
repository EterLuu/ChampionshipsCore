package ink.ziip.championshipscore.api.game.craftingfeast.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * One restaurant's delivery counter. A rider is dispatched with a fixed arrival time; delivery
 * orders appear on the board until the rider arrives and never after that. The rider then waits at
 * most {@link Timing#waitSeconds()}: the batch pays out only when every order is in its numbered
 * slot before the rider leaves, otherwise the whole batch is lost.
 */
public final class FeastDelivery {
    public enum Phase {
        /** No rider: counting down to the next dispatch. */
        IDLE,
        /** A rider is on the way: counting down to arrival while orders come in. */
        DISPATCHED,
        /** The rider is at the pickup window: counting down to departure. */
        WAITING
    }

    public enum Event {
        NONE,
        /** A rider was dispatched together with the batch's first order. */
        DISPATCHED,
        ORDER,
        ARRIVED,
        /** The rider left without the batch; {@link Tick#orders()} holds the lost orders. */
        EXPIRED
    }

    /** {@code number} is the delivery ticket (W1, W2, ...); {@code slot} the 0-based shelf. */
    public record Order(int number, FeastRequest request, int slot) {}

    public record Tick(Event event, List<Order> orders) {
        static final Tick NONE = new Tick(Event.NONE, List.of());
    }

    public record Timing(
            int firstSeconds,
            int etaSeconds,
            int orderSeconds,
            int maxOrders,
            int waitSeconds,
            int cooldownSeconds) {
        public Timing {
            if (firstSeconds < 1
                    || firstSeconds > 3600
                    || etaSeconds < 10
                    || etaSeconds > 600
                    || orderSeconds < 1
                    || orderSeconds > 600
                    || maxOrders < 1
                    || maxOrders > 9
                    || waitSeconds < 5
                    || waitSeconds > 120
                    || cooldownSeconds < 1
                    || cooldownSeconds > 600)
                throw new IllegalArgumentException("Invalid Crafting Feast delivery timing");
        }
    }

    private final Timing timing;
    private final int capacity;
    private final List<Order> orders = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private int countdown;
    private int nextOrder;
    private int issued;

    public FeastDelivery(Timing timing, int slots) {
        if (timing == null || slots < 1) throw new IllegalArgumentException("Invalid delivery");
        this.timing = timing;
        this.capacity = Math.min(slots, timing.maxOrders());
        countdown = timing.firstSeconds();
    }

    public Phase phase() {
        return phase;
    }

    /** Seconds to the next dispatch (IDLE), the rider's arrival or the rider's departure. */
    public int countdown() {
        return countdown;
    }

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    public Timing timing() {
        return timing;
    }

    /** The order waiting in {@code slot}, or null. */
    public Order slot(int slot) {
        for (Order order : orders) if (order.slot() == slot) return order;
        return null;
    }

    /**
     * Advances one second. {@code draw} returns the n-th request of the shared delivery sequence,
     * so every restaurant receives the same delivery orders in the same order.
     */
    public Tick tick(IntFunction<FeastRequest> draw) {
        switch (phase) {
            case IDLE -> {
                if (--countdown > 0) return Tick.NONE;
                phase = Phase.DISPATCHED;
                countdown = timing.etaSeconds();
                nextOrder = timing.orderSeconds();
                return new Tick(Event.DISPATCHED, List.of(add(draw)));
            }
            case DISPATCHED -> {
                // Arrival closes the batch before any order due in the same second.
                if (--countdown <= 0) {
                    phase = Phase.WAITING;
                    countdown = timing.waitSeconds();
                    return new Tick(Event.ARRIVED, orders());
                }
                if (--nextOrder > 0) return Tick.NONE;
                nextOrder = timing.orderSeconds();
                if (orders.size() >= capacity) return Tick.NONE;
                return new Tick(Event.ORDER, List.of(add(draw)));
            }
            default -> {
                if (--countdown > 0) return Tick.NONE;
                return new Tick(Event.EXPIRED, release());
            }
        }
    }

    /** Shortens the ride after all orders are issued, retaining time to approach the window. */
    public boolean approachEarly(int seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Arrival needs a positive travel time");
        if (phase != Phase.DISPATCHED || orders.size() != capacity || countdown <= seconds)
            return false;
        countdown = seconds;
        return true;
    }

    /** The rider takes the batch: only while waiting at the window, and only all at once. */
    public List<Order> pickUp() {
        return phase == Phase.WAITING ? release() : List.of();
    }

    private List<Order> release() {
        List<Order> batch = orders();
        orders.clear();
        phase = Phase.IDLE;
        countdown = timing.cooldownSeconds();
        return batch;
    }

    private Order add(IntFunction<FeastRequest> draw) {
        int slot = 0;
        while (slot(slot) != null) slot++;
        Order order = new Order(issued + 1, draw.apply(issued), slot);
        issued++;
        orders.add(order);
        return order;
    }
}
