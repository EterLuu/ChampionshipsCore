package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastDelivery;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastRequest;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.entity.TeleportFlag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Vindicator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;
import java.util.function.IntFunction;

/**
 * A restaurant's delivery counter: numbered shelf slots in the kitchen, the rider who walks the
 * delivery route to the pickup window, and the delivery half of the order board. Delivery orders
 * have no guest; the board lists ticket status and the dispatched rider's arrival time.
 */
final class FeastDeliveryDesk implements AutoCloseable {
    /** Assumed rider pace for the walk-in lead time; arrival itself follows the board clock. */
    private static final double RIDER_BLOCKS_PER_SECOND = 2.5;

    private static final double RIDER_SPEED = 0.45;
    private static final int LESSON_PICKUP_TICKS = 3 * 20;

    private final FeastRestaurant restaurant;
    private final CraftingFeastArea area;
    private final CraftingFeastConfig config;
    private final FeastDelivery delivery;
    private final IntFunction<FeastRequest> orders;
    private final List<Block> slots = new ArrayList<>();
    private final List<TextDisplay> slotLabels = new ArrayList<>();
    private final Map<Integer, Slot> inventories = new HashMap<>();
    private final List<Location> route;
    private final int walkSeconds;
    private Rider rider;
    int delivered;
    int lost;

    /**
     * {@code timing} and {@code orders} come from the map and the shared guest sequence in a real
     * game, and from the beginner lesson during the rule introduction.
     */
    FeastDeliveryDesk(
            FeastRestaurant restaurant,
            CraftingFeastArea area,
            FeastDelivery.Timing timing,
            IntFunction<FeastRequest> orders) {
        this.restaurant = restaurant;
        this.orders = orders;
        this.area = area;
        config = area.getGameConfig();
        route = config.deliveryRouteLocations().stream().map(restaurant::at).toList();
        double length = 0;
        for (int i = 1; i < route.size(); i++) length += route.get(i - 1).distance(route.get(i));
        walkSeconds = (int) Math.ceil(length / RIDER_BLOCKS_PER_SECOND) + 2;
        delivery = new FeastDelivery(timing, config.deliverySlotLocations().size());
    }

    void open() {
        List<Location> points = config.deliverySlotLocations();
        // Every shelf sign faces the kitchen side of the shelf. The area sign hangs above open air
        // (the window), where the nearest walkable side would turn it sideways along the wall.
        BlockFace front = restaurant.openSide(restaurant.at(points.getFirst()).getBlock());
        for (Location point : points) {
            Block block = restaurant.at(point).getBlock();
            if (block.getType() != Material.BARREL)
                throw new IllegalStateException(
                        "Crafting Feast delivery slot needs BARREL at " + block.getLocation());
            slots.add(block);
            // Copy-local point: the label helper applies the restaurant offset itself.
            slotLabels.add(
                    restaurant.labelAt(point, FeastKitchen.text("delivery-slot-free"), front));
        }
        Location centre = points.getFirst().clone().add(points.getLast()).multiply(0.5);
        centre.setY(points.getFirst().getY() + 1);
        restaurant.labelAt(centre, FeastKitchen.text("delivery-area-label"), front);
    }

    // ------------------------------------------------------------------ clock

    void tick(boolean second, boolean acceptingOrders) {
        if (second && (acceptingOrders || delivery.phase() != FeastDelivery.Phase.IDLE)) {
            FeastDelivery.Tick tick = delivery.tick(orders);
            switch (tick.event()) {
                case DISPATCHED, ORDER -> tick.orders().forEach(this::announce);
                case ARRIVED -> arrive(restaurant.isLesson() || !complete());
                case EXPIRED -> expire(tick.orders());
                default -> {}
            }
        }
        if (restaurant.isLesson()) {
            FeastDelivery.Order first = delivery.slot(0);
            if (first != null && filled(first)) delivery.approachEarly(walkSeconds);
        }
        if (delivery.phase() == FeastDelivery.Phase.DISPATCHED
                && rider == null
                && delivery.countdown() <= walkSeconds) rider = new Rider(route.getFirst());
        if (waiting()
                && complete()
                && (!restaurant.isLesson()
                        || rider != null && rider.waitingTicks >= LESSON_PICKUP_TICKS)) pickUp();
        if (rider != null && !rider.move()) {
            rider.close();
            rider = null;
        }
        if (second && rider != null) rider.label();
    }

    private void announce(FeastDelivery.Order order) {
        Slot slot = new Slot(order);
        inventories.put(order.slot(), slot);
        slotLabels
                .get(order.slot())
                .text(
                        LegacyText.component(
                                FeastKitchen.text("delivery-slot-label", order.number())));
        restaurant.teamActionBar(
                FeastKitchen.text(
                        "delivery-new",
                        order.number(),
                        order.request().name(),
                        order.request().amount(),
                        delivery.countdown()));
        restaurant.teamSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
    }

    private void arrive(boolean announce) {
        if (rider == null) rider = new Rider(route.getLast());
        rider.arrive();
        if (!announce) return;
        restaurant.teamActionBar(
                FeastKitchen.text(
                        "delivery-arrived",
                        delivery.orders().size(),
                        delivery.timing().waitSeconds()));
        restaurant.teamSound(Sound.BLOCK_BELL_USE, 1.0f);
    }

    private boolean complete() {
        List<FeastDelivery.Order> orders = delivery.orders();
        if (orders.isEmpty()) return false;
        for (FeastDelivery.Order order : orders) if (!filled(order)) return false;
        return true;
    }

    boolean filled(FeastDelivery.Order order) {
        Slot slot = inventories.get(order.slot());
        return slot != null
                && order.request().fills(Arrays.asList(slot.getInventory().getContents()));
    }

    List<FeastDelivery.Order> orders() {
        return delivery.orders();
    }

    int countdown() {
        return delivery.countdown();
    }

    boolean waiting() {
        return delivery.phase() == FeastDelivery.Phase.WAITING;
    }

    boolean misplaced(FeastDelivery.Order order) {
        Slot slot = inventories.get(order.slot());
        if (slot == null) return false;
        for (var item : slot.getInventory().getContents())
            if (item != null && !item.getType().isAir() && !order.request().matchesItem(item))
                return true;
        return false;
    }

    private void pickUp() {
        List<FeastDelivery.Order> batch = delivery.pickUp();
        if (batch.isEmpty()) return;
        int points =
                batch.stream()
                        .mapToInt(o -> o.request().deliveryPoints(config.getDeliveryBonusPercent()))
                        .sum();
        for (FeastDelivery.Order order : batch) {
            if (order.request().clue()) {
                Block shelf = slots.get(order.slot());
                inventories.get(order.slot()).returnClueSurplus(shelf);
            }
        }
        clearSlots();
        delivered += batch.size();
        restaurant.award(points);
        restaurant.teamActionBar(FeastKitchen.text("delivery-picked", batch.size(), points));
        restaurant.teamSound(Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
        if (rider != null) rider.leave(true);
    }

    private void expire(List<FeastDelivery.Order> batch) {
        clearSlots();
        lost += batch.size();
        restaurant.teamActionBar(FeastKitchen.text("delivery-expired", batch.size()));
        restaurant.teamSound(Sound.ENTITY_VILLAGER_NO, 0.8f);
        if (rider != null) rider.leave(false);
    }

    /** The rider takes (or the expired batch voids) everything left on the shelf. */
    private void clearSlots() {
        for (Slot slot : inventories.values()) slot.discard();
        inventories.clear();
        for (TextDisplay label : slotLabels)
            if (label.isValid())
                label.text(LegacyText.component(FeastKitchen.text("delivery-slot-free")));
    }

    // ---------------------------------------------------------------- players

    /** Opens the slot's shelf; returns true when {@code block} belongs to this counter. */
    boolean interact(Player player, Block block) {
        int index = slots.indexOf(block);
        if (index < 0) return false;
        Slot slot = inventories.get(index);
        if (slot == null) {
            player.sendActionBar(LegacyText.component(FeastKitchen.text("delivery-slot-empty")));
            return true;
        }
        player.openInventory(slot.getInventory());
        return true;
    }

    boolean canUse(Player player, Inventory inventory) {
        return inventory.getHolder() instanceof Slot slot
                && inventories.get(slot.order.slot()) == slot
                && area.canPlay(player);
    }

    boolean owns(Entity entity) {
        return rider != null && rider.owns(entity);
    }

    // ----------------------------------------------------------- presentation

    String boardText() {
        StringBuilder text = new StringBuilder(FeastKitchen.text("delivery-header"));
        if (delivery.phase() != FeastDelivery.Phase.IDLE)
            text.append('\n')
                    .append(
                            FeastKitchen.text(
                                    delivery.phase() == FeastDelivery.Phase.DISPATCHED
                                            ? "delivery-eta"
                                            : "delivery-waiting",
                                    delivery.countdown()));
        List<FeastDelivery.Order> orders = delivery.orders();
        if (orders.isEmpty()) text.append('\n').append(FeastKitchen.text("delivery-none"));
        for (FeastDelivery.Order order : orders) {
            text.append('\n')
                    .append(
                            FeastKitchen.text(
                                    "delivery-ticket",
                                    order.number(),
                                    filled(order) ? FeastKitchen.text("delivery-ready") : ""));
        }
        return text.toString();
    }

    @Override
    public void close() {
        for (Slot slot : inventories.values()) slot.discard();
        inventories.clear();
        if (rider != null) rider.close();
        rider = null;
        slots.clear();
        slotLabels.clear(); // removed with the restaurant's other labels
    }

    /** One numbered shelf compartment holding a single delivery order's items. */
    private static final class Slot implements InventoryHolder {
        final FeastDelivery.Order order;
        private final Inventory inventory;

        Slot(FeastDelivery.Order order) {
            this.order = order;
            inventory =
                    Bukkit.createInventory(
                            this,
                            9,
                            LegacyText.component(
                                    FeastKitchen.text(
                                            "delivery-slot-title",
                                            order.number(),
                                            order.request().name(),
                                            order.request().amount())));
        }

        /** A successful clue ticket costs one item even if the team deposited a stack. */
        void returnClueSurplus(Block shelf) {
            boolean taken = false;
            Location pickup = shelf.getLocation().add(0.5, 1, 0.5);
            for (var stack : inventory.getContents()) {
                if (stack == null || stack.getType().isAir()) continue;
                int surplus = stack.getAmount() - (taken ? 0 : 1);
                taken = true;
                if (surplus > 0) shelf.getWorld().dropItem(pickup, stack.asQuantity(surplus));
            }
        }

        void discard() {
            for (var viewer : List.copyOf(inventory.getViewers())) viewer.closeInventory();
            inventory.clear();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * The rider is a vindicator on a saddled pig. The pig walks the delivery route in, waits at the
     * window and walks it back out; the vindicator only rides, faces the way the pig goes and wears
     * the name tag.
     */
    private final class Rider implements AutoCloseable {
        enum State {
            COMING,
            WAITING,
            LEAVING
        }

        final Pig mount;
        final Vindicator rider;
        final double speed;
        State state = State.COMING;
        List<Location> path;
        int waypoint;
        int movingTicks;
        int pathTicks;
        int waitingTicks;
        boolean happy;
        String name = "";

        Rider(Location start) {
            World world = start.getWorld();
            // No random spawn data: a randomized vindicator would be handed an iron axe.
            mount =
                    world.spawn(
                            start,
                            Pig.class,
                            false,
                            pig -> {
                                protect(pig);
                                pig.setAdult();
                                pig.setAgeLock(true);
                                pig.setSaddle(true);
                            });
            rider =
                    world.spawn(
                            start,
                            Vindicator.class,
                            false,
                            vindicator -> {
                                protect(vindicator);
                                vindicator.setAI(false);
                                vindicator.setCustomNameVisible(true);
                                vindicator.setCanJoinRaid(false);
                                vindicator.setPatrolLeader(false);
                                vindicator.setCelebrating(false);
                                vindicator.setJohnny(false);
                                vindicator.getEquipment().clear();
                            });
            if (!mount.addPassenger(rider)) {
                rider.remove();
                mount.remove();
            }
            var movement = mount.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            double base = movement == null ? 0.25 : movement.getBaseValue();
            speed = RIDER_SPEED / Math.max(0.05, base);
            follow(route.size() > 1 && start.equals(route.getFirst()) ? route : List.of(start));
            label();
        }

        private static void protect(LivingEntity entity) {
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
            entity.setCollidable(false);
        }

        boolean owns(Entity entity) {
            return mount.equals(entity) || rider.equals(entity);
        }

        private void follow(List<Location> points) {
            path = points;
            waypoint = 0;
            movingTicks = 0;
            pathTicks = 0;
            mount.setAI(true);
        }

        private void place(Location target) {
            mount.teleport(target, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }

        /** Rider and pig face {@code yaw} together, head and body alike. */
        private void face(float yaw) {
            for (LivingEntity entity : List.of(mount, rider)) {
                entity.setRotation(yaw, 0);
                entity.setBodyYaw(yaw);
            }
        }

        /** The board clock decides arrival; a late rider is placed at the window. */
        void arrive() {
            state = State.WAITING;
            waitingTicks = 0;
            Location window = route.getLast();
            if (mount.getLocation().distanceSquared(window) > 0.64) place(window);
            stop(window.getYaw());
            label();
            particles(Particle.NOTE, 3);
        }

        private void stop(float yaw) {
            mount.getPathfinder().stopPathfinding();
            mount.setAI(false);
            face(yaw);
        }

        void leave(boolean happy) {
            this.happy = happy;
            state = State.LEAVING;
            List<Location> back = new ArrayList<>(route);
            Collections.reverse(back);
            follow(back);
            label();
            particles(happy ? Particle.HAPPY_VILLAGER : Particle.ANGRY_VILLAGER, happy ? 8 : 4);
        }

        private void particles(Particle particle, int count) {
            Location head = rider.getLocation().add(0, rider.getHeight() + 0.2, 0);
            head.getWorld().spawnParticle(particle, head, count, 0.3, 0.2, 0.3, 0);
        }

        /** Returns false once the rider is gone: walked out or removed by the server. */
        boolean move() {
            if (!mount.isValid() || !rider.isValid() || rider.getVehicle() != mount) return false;
            if (state == State.WAITING) {
                waitingTicks += 5;
                return true;
            }
            // The vindicator has no AI of its own: keep it looking where the pig is going.
            float yaw = mount.getLocation().getYaw();
            rider.setRotation(yaw, 0);
            rider.setBodyYaw(yaw);
            Location target = path.get(waypoint);
            if (mount.getLocation().distanceSquared(target) > 0.64) {
                movingTicks += 5;
                // A blocked route must not hold up the board clock: skip to the waypoint.
                if (movingTicks > 200) {
                    place(target);
                    return true;
                }
                if ((pathTicks -= 5) <= 0) {
                    restaurant.navigate(mount, target, speed);
                    pathTicks = 20;
                }
                return true;
            }
            if (waypoint + 1 < path.size()) {
                waypoint++;
                movingTicks = 0;
                pathTicks = 0;
                return true;
            }
            if (state == State.LEAVING) return false;
            // Early at the window: stand and wait for the arrival time on the board.
            stop(target.getYaw());
            return true;
        }

        void label() {
            String label =
                    switch (state) {
                        case COMING -> FeastKitchen.text("rider-coming", delivery.countdown());
                        case WAITING -> FeastKitchen.text("rider-waiting", delivery.countdown());
                        case LEAVING -> FeastKitchen.text(happy ? "rider-happy" : "rider-angry");
                    };
            if (label.equals(name)) return;
            name = label;
            rider.customName(LegacyText.component(label));
        }

        @Override
        public void close() {
            rider.remove();
            mount.remove();
        }
    }
}
