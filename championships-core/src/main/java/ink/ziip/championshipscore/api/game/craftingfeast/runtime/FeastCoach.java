package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastDelivery;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastGuest;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastOrder;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastRequest;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial.Task;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles;

import net.kyori.adventure.title.Title;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Shows one actionable goal at a time while the restaurant owns all real game mechanics. */
final class FeastCoach implements AutoCloseable {
    private static final int PRACTICE_TABLES = 2;
    private static final int PRACTICE_INTERVAL = 8;
    private static final double BOARD_SIDE_OFFSET = 5;
    private static final double BOARD_KITCHEN_OFFSET = 1.6;

    private final FeastRestaurant restaurant;
    private final FeastTutorial tutorial;
    private final List<FeastRequest> menu;
    private final FeastRequest crafted;
    private final FeastRequest cooked;
    private final FeastRequest takeaway;
    private final FeastRequest impossible;
    private final FeastRequest deliveryIron;
    private TextDisplay board;
    private String boardText = "";
    private boolean welcomed;
    private boolean finishedShown;
    private boolean closed;
    private int nextTicket = FeastTutorial.FAILURE_TICKET + 1;
    private int practiceCountdown = 3;

    FeastCoach(FeastRestaurant restaurant, CraftingFeastArea area, boolean delivery) {
        this.restaurant = restaurant;
        tutorial = new FeastTutorial(delivery);
        menu = List.copyOf(area.getGameConfig().menu());
        crafted = FeastTutorial.craftDish(menu);
        cooked = FeastTutorial.cookDish(menu);
        takeaway = delivery ? FeastTutorial.deliveryDish(menu) : null;
        impossible = FeastTutorial.failureDish(text("failure-dish"));
        deliveryIron = FeastTutorial.deliveryIronDish(text("iron-dish"));
    }

    FeastRequest delivery(int index) {
        return index % 2 == 0 ? takeaway : deliveryIron;
    }

    boolean deliveryOpen() {
        return !closed && tutorial.deliveryOpen();
    }

    /** Beside the order window, preserving the counter and the player's view of the guests. */
    void open(Location counter) {
        Location at = counter.clone().add(0, 2.2, 0);
        var kitchenward = counter.getDirection().setY(0);
        if (kitchenward.lengthSquared() > 1e-6) {
            kitchenward.normalize();
            at.add(kitchenward.clone().multiply(BOARD_KITCHEN_OFFSET));
            at.add(
                    new org.bukkit.util.Vector(kitchenward.getZ(), 0, -kitchenward.getX())
                            .multiply(-BOARD_SIDE_OFFSET));
        }
        board = restaurant.label(at, boardText, WorldTextStyles.Profile.CRAFTING_FEAST_TUTORIAL);
    }

    void tick(boolean second) {
        if (closed || !second) return;
        Task current = tutorial.current();
        if (current != null) {
            switch (current) {
                case SERVE -> ensureGuest(FeastTutorial.FIRST_TICKET, crafted, false);
                case COOK -> ensureGuest(FeastTutorial.SECOND_TICKET, cooked, true);
                case FAILURE -> ensureGuest(FeastTutorial.FAILURE_TICKET, impossible, false);
                case DELIVERY -> {
                    if (restaurant.delivery != null && restaurant.delivery.delivered >= 2)
                        tutorial.complete(Task.DELIVERY);
                }
            }
        }
        // Let the first guest reach the counter before introducing their order or the task board.
        if (!welcomed) {
            FeastOrder.Phase first = restaurant.phaseOf(FeastTutorial.FIRST_TICKET);
            if (first == null || first == FeastOrder.Phase.ENTERING) return;
            welcomed = true;
            restaurant.teamTitle(text("welcome-title"), text("welcome-subtitle"));
        }
        if (tutorial.finished()) {
            if (!finishedShown) {
                finishedShown = true;
                restaurant.teamTitle(text("finished-title"), text("practice"));
            }
            practice();
        }
        Guidance guidance = guidance();
        refreshBoard(guidance);
        if (restaurant.quiet()) restaurant.teamActionBarQuietly(text("hint", guidance.action()));
    }

    void served(int ticket) {
        if (!closed) tutorial.served(ticket);
    }

    void lost(int ticket) {
        if (!closed && tutorial.lost(ticket)) restaurant.teamMessage(text("failure-result"));
    }

    private void ensureGuest(int ticket, FeastRequest request, boolean vip) {
        if (restaurant.phaseOf(ticket) == null)
            restaurant.arrive(
                    new FeastGuest(ticket, request, vip, null),
                    ticket == FeastTutorial.FAILURE_TICKET
                            ? FeastTutorial.FAILURE_PATIENCE_SECONDS
                            : FeastTutorial.PATIENCE_SECONDS);
    }

    private void practice() {
        if (--practiceCountdown > 0 || restaurant.customerCount() >= PRACTICE_TABLES) return;
        practiceCountdown = PRACTICE_INTERVAL;
        FeastRequest request = menu.get(ThreadLocalRandom.current().nextInt(menu.size()));
        restaurant.arrive(
                new FeastGuest(nextTicket++, request, false, null), FeastTutorial.PATIENCE_SECONDS);
    }

    private record Guidance(String action, List<String> rows) {
        Guidance(String action, String note) {
            this(action, List.of(note));
        }
    }

    /** Explains restaurant rules from live guest and shelf state, without recipe instructions. */
    private Guidance guidance() {
        Task current = tutorial.current();
        if (current == null) return new Guidance(text("practice"), text("practice-note"));
        return switch (current) {
            case SERVE -> counterGuidance(crafted, FeastTutorial.FIRST_TICKET);
            case COOK -> counterGuidance(cooked, FeastTutorial.SECOND_TICKET);
            case FAILURE -> {
                int remaining = restaurant.waitingSeconds(FeastTutorial.FAILURE_TICKET);
                yield new Guidance(
                        remaining < 0 ? text("failure-wait") : text("failure-watch", remaining),
                        text("failure-note"));
            }
            case DELIVERY -> deliveryGuidance();
        };
    }

    private Guidance counterGuidance(FeastRequest request, int ticket) {
        FeastOrder.Phase phase = restaurant.phaseOf(ticket);
        boolean vip = ticket == FeastTutorial.SECOND_TICKET;
        if (phase == null || phase == FeastOrder.Phase.ENTERING)
            return new Guidance(text("next-guest"), text(vip ? "vip-note" : "order-note"));
        String key =
                switch (phase) {
                    case ORDERING -> "order-request";
                    case WAITING -> "serve-now";
                    default -> "order-seating";
                };
        String note =
                vip
                        ? "vip-note"
                        : switch (phase) {
                            case ORDERING -> "order-note";
                            case WAITING -> "serve-note";
                            default -> "seat-note";
                        };
        return new Guidance(text(key, dish(request), ticket), text(note));
    }

    private Guidance deliveryGuidance() {
        FeastDeliveryDesk desk = restaurant.delivery;
        List<FeastDelivery.Order> orders = desk.orders();
        if (orders.isEmpty())
            return new Guidance(text("delivery-wait", desk.countdown()), text("delivery-note"));
        List<String> rows = new ArrayList<>();
        String action = null;
        for (var order : orders) {
            boolean ready = desk.filled(order);
            boolean wrong = desk.misplaced(order);
            rows.add(
                    text(
                            ready ? "delivery-ready" : wrong ? "delivery-wrong" : "delivery-needed",
                            order.number(),
                            dish(order.request())));
            if (!ready && action == null) {
                String key =
                        wrong
                                ? "delivery-correct"
                                : desk.waiting() ? "delivery-finish" : "delivery-place";
                action = text(key, order.number(), dish(order.request()));
            }
        }
        rows.add(text("delivery-note"));
        return new Guidance(
                action == null
                        ? text(desk.waiting() ? "delivery-pickup" : "delivery-on-way")
                        : action,
                List.copyOf(rows));
    }

    private String boardText(Guidance guidance) {
        Task current = tutorial.current();
        List<String> lines = new ArrayList<>();
        lines.add(
                text(
                        "board-header",
                        tutorial.finished() ? tutorial.tasks().size() : tutorial.completed() + 1,
                        tutorial.tasks().size()));
        if (current != null)
            lines.add(text("board-task", text("task." + current.name().toLowerCase(Locale.ROOT))));
        lines.add(text("board-action", guidance.action()));
        for (String row : guidance.rows()) lines.add(text("board-note", row));
        return String.join("\n", lines);
    }

    private void refreshBoard(Guidance guidance) {
        if (board == null || !board.isValid()) return;
        String next = boardText(guidance);
        if (next.equals(boardText)) return;
        boardText = next;
        board.text(LegacyText.component(next));
    }

    private static String dish(FeastRequest request) {
        return FeastKitchen.text("dish", request.name(), request.amount());
    }

    @Override
    public void close() {
        closed = true;
        board = null; // Restaurant owns and removes the label entity.
    }

    static String text(String key, Object... args) {
        return FeastKitchen.text("tutorial." + key, args);
    }

    static Title screenTitle(String title, String subtitle) {
        return Title.title(
                LegacyText.component(title),
                LegacyText.component(subtitle),
                Title.Times.times(
                        Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500)));
    }
}
