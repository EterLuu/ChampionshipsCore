package ink.ziip.championshipscore.api.game.craftingfeast.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** One ordered lesson: serve bread, smelt glass, observe a timeout, then fill a delivery pair. */
public final class FeastTutorial {
    public enum Task {
        /** Hand the crafted dish to the seated first guest. */
        SERVE,
        /** Cook and serve the second, VIP guest. */
        COOK,
        /** Observe an impossible order time out after the guest sits down. */
        FAILURE,
        /** Fill both numbered delivery slots before the rider leaves. */
        DELIVERY
    }

    /** The lesson's guests are numbered like the real queue. */
    public static final int FIRST_TICKET = 1;

    public static final int SECOND_TICKET = 2;
    public static final int FAILURE_TICKET = 3;
    public static final int FAILURE_PATIENCE_SECONDS = 10;

    /** The shared introduction owns the complete lesson deadline. */
    public static final int DURATION_SECONDS = 180;

    /** Normal lesson guests outwait the introduction; only the failure guest times out. */
    public static final int PATIENCE_SECONDS = DURATION_SECONDS + 60;

    /** Two orders, a fixed arrival deadline and a generous wait at the window. */
    public static final FeastDelivery.Timing DELIVERY_TIMING =
            new FeastDelivery.Timing(1, 45, 1, 2, 60, 20);

    private final List<Task> tasks;
    private int completed;

    public FeastTutorial(boolean delivery) {
        List<Task> list = new ArrayList<>(List.of(Task.values()));
        if (!delivery) list.remove(Task.DELIVERY);
        tasks = List.copyOf(list);
    }

    public List<Task> tasks() {
        return tasks;
    }

    public boolean done(Task task) {
        int index = tasks.indexOf(task);
        return index >= 0 && index < completed;
    }

    /** Only the current goal can advance; duplicate or future events cannot skip a lesson. */
    public boolean complete(Task task) {
        if (task == null || task != current()) return false;
        completed++;
        return true;
    }

    /** The first open task in lesson order, or null once every task is done. */
    public Task current() {
        return finished() ? null : tasks.get(completed);
    }

    public boolean finished() {
        return completed == tasks.size();
    }

    public int completed() {
        return completed;
    }

    /** Only the deliberate guest's timeout can complete the failure demonstration. */
    public boolean lost(int ticket) {
        return ticket == FAILURE_TICKET && complete(Task.FAILURE);
    }

    public boolean served(int ticket) {
        return switch (ticket) {
            case FIRST_TICKET -> complete(Task.SERVE);
            case SECOND_TICKET -> complete(Task.COOK);
            default -> false;
        };
    }

    /** Scripted guests stay close to the counter; random practice guests keep normal seating. */
    public static boolean scriptedGuest(int ticket) {
        return ticket >= FIRST_TICKET && ticket <= FAILURE_TICKET;
    }

    public static FeastRequest failureDish(String name) {
        return new FeastRequest(
                "tutorial_failure", name, Material.BEDROCK, 1, 100, "mason", "plains");
    }

    /** Delivery starts only when the timeout demonstration is over, and closes after pickup. */
    public boolean deliveryOpen() {
        return current() == Task.DELIVERY;
    }

    public static FeastRequest deliveryIronDish(String name) {
        return new FeastRequest(
                "tutorial_iron_ingot", name, Material.IRON_INGOT, 1, 100, "armorer", "desert");
    }

    /** Fixed simple recipes, independent of menu order, price or additional dishes. */
    public static FeastRequest craftDish(List<FeastRequest> menu) {
        return dish(menu, Material.BREAD);
    }

    public static FeastRequest cookDish(List<FeastRequest> menu) {
        return dish(menu, Material.GLASS);
    }

    public static FeastRequest deliveryDish(List<FeastRequest> menu) {
        return dish(menu, Material.BAKED_POTATO);
    }

    private static FeastRequest dish(List<FeastRequest> menu, Material material) {
        FeastRequest recipe =
                menu.stream()
                        .filter(request -> request.material() == material)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Missing tutorial dish: " + material));
        return new FeastRequest(
                recipe.id(),
                recipe.name(),
                material,
                1,
                recipe.points(),
                recipe.profession(),
                recipe.biome());
    }
}
