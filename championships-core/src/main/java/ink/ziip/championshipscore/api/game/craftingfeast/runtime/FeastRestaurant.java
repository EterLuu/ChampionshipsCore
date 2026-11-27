package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.game.craftingfeast.geometry.FeastRoutes;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastGaze;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastGuest;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastOrder;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastReputation;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastRequest;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastSeating;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial;
import ink.ziip.championshipscore.api.game.spatial.TeleportPositions;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.display.WorldTextStyles;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** A team's tables and kitchen, sharing the owning area's clock, roster and scoring. */
final class FeastRestaurant implements AutoCloseable {
    private static final int BAR_CELLS = 5;

    /** Effective navigation speed for every guest (a villager walks at multiplier 0.7). */
    static final double WALK_SPEED = 0.35;

    final ChampionshipTeam team;
    final int index;
    final FeastKitchen kitchen;
    private final CraftingFeastArea area;
    private final CraftingFeastConfig config;
    private final Map<UUID, Location> playerSpawns;
    private final List<Location> seats;
    private final List<Boolean> privateSeats;
    private final Map<Integer, Customer> customers = new LinkedHashMap<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private final FeastReputation reputation = new FeastReputation();
    private TextDisplay board;
    private String boardText = "";
    private int guests;
    private int streak;
    private int bestStreak;
    private Mob pathRequester;
    private TextDisplay deliveryBoard;
    private String deliveryBoardText = "";
    final FeastDeliveryDesk delivery;

    /** The beginner lesson's coach; null in a real game. */
    private final FeastCoach coach;

    /** Seconds the team's action bar stays reserved for a mechanic notice over a lesson hint. */
    private int noticeHold;

    private boolean closed;

    int completed;
    int missed;

    /**
     * A {@code lesson} restaurant serves the rule-introduction lesson: scripted guests, a delivery
     * pair and no score. It is closed and replaced before the real game opens.
     */
    FeastRestaurant(CraftingFeastArea area, ChampionshipTeam team, int index, boolean lesson) {
        this.area = area;
        this.team = team;
        this.index = index;
        config = area.getGameConfig();
        var origin = config.grid().origin(index);
        var bounds = BoundingBox.of(origin, origin.clone().add(config.getCopySize()));
        playerSpawns =
                TeleportPositions.getTeamSpawnLocations(
                        at(config.getSpawn()),
                        area.getParticipantUniqueIds().stream().filter(team::isTeamMember).toList(),
                        location -> bounds.contains(location.toVector()));
        seats = config.seatLocations().stream().map(this::at).toList();
        int hall = seats.size() - config.privateSeatCount();
        List<Boolean> rooms = new ArrayList<>();
        for (int seat = 0; seat < seats.size(); seat++) rooms.add(seat >= hall);
        privateSeats = List.copyOf(rooms);
        kitchen = new FeastKitchen(area, index);
        coach = lesson ? new FeastCoach(this, area, config.isDeliveryEnabled()) : null;
        delivery =
                !config.isDeliveryEnabled()
                        ? null
                        : coach != null
                                ? new FeastDeliveryDesk(
                                        this, area, FeastTutorial.DELIVERY_TIMING, coach::delivery)
                                : new FeastDeliveryDesk(
                                        this,
                                        area,
                                        config.deliveryTiming(),
                                        i -> area.sequence().delivery(i));
    }

    boolean isLesson() {
        return coach != null;
    }

    void open() {
        kitchen.open();
        board =
                label(
                        at(config.getOrderBoard()),
                        boardText(),
                        WorldTextStyles.Profile.CRAFTING_FEAST_BOARD);
        if (delivery != null) {
            delivery.open();
            deliveryBoard =
                    label(
                            at(config.getDeliveryBoard()),
                            delivery.boardText(),
                            WorldTextStyles.Profile.CRAFTING_FEAST_BOARD);
        }
        config.supplyBarrels()
                .forEach(
                        supply ->
                                labelAt(
                                        supply.location(),
                                        FeastKitchen.text("supply-label", supply.name())));
        if (coach != null) coach.open(at(config.getOrderCounter()));
    }

    /**
     * Mounts a fixed sign above a supply block, facing its open side (the kitchen) and set flush
     * against the wall behind it when there is one.
     */
    TextDisplay labelAt(Location location, String text) {
        return labelAt(location, text, openSide(at(location).getBlock()));
    }

    /** Same sign with an explicit facing, for labels above open air that has no single front. */
    TextDisplay labelAt(Location location, String text, BlockFace front) {
        Block block = at(location).getBlock();
        Location sign = block.getLocation().add(0.5, 2.1, 0.5);
        if (block.getRelative(front.getOppositeFace()).getRelative(BlockFace.UP, 2).isSolid())
            sign.add(front.getModX() * -WALL_OFFSET, 0, front.getModZ() * -WALL_OFFSET);
        sign.setDirection(front.getDirection());
        return label(sign, text, WorldTextStyles.Profile.CRAFTING_FEAST_SUPPLY);
    }

    /** The horizontal side of a block that players can walk up to, nearest the kitchen spawn. */
    BlockFace openSide(Block block) {
        Location spawn = at(config.getSpawn());
        BlockFace best = BlockFace.NORTH;
        double closest = Double.MAX_VALUE;
        for (BlockFace face :
                List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block side = block.getRelative(face);
            double distance =
                    side.getLocation().add(0.5, 0, 0.5).distanceSquared(spawn)
                            - (side.isPassable() ? 1e6 : 0);
            if (distance < closest) {
                closest = distance;
                best = face;
            }
        }
        return best;
    }

    /** Guests turn their heads towards a cook within this many blocks. */
    private static final double LOOK_RANGE = 5;

    /** Fixed signs stop this far in front of the wall so the text never z-fights it. */
    private static final double WALL_OFFSET = 0.46;

    private TextDisplay label(Location location, String text) {
        return label(location, text, WorldTextStyles.Profile.CRAFTING_FEAST_STATION);
    }

    TextDisplay label(Location location, String text, WorldTextStyles.Profile profile) {
        TextDisplay label =
                location.getWorld()
                        .spawn(
                                location,
                                TextDisplay.class,
                                display -> {
                                    display.setPersistent(false);
                                    MessageConfig.worldTextStyle(profile).apply(display);
                                    display.text(LegacyText.component(text));
                                });
        labels.add(label);
        return label;
    }

    Location at(Location location) {
        return config.at(location, index);
    }

    Location playerSpawn(UUID player) {
        Location location = playerSpawns.get(player);
        return location == null ? null : location.clone();
    }

    // ------------------------------------------------------------------ arrivals

    void arrive() {
        if (!area.running() || coach != null) return;
        FeastGuest guest = area.sequence().guest(guests);
        int patience =
                guest.vip()
                        ? Math.max(
                                1,
                                config.getPatienceSeconds() * config.getVipPatiencePercent() / 100)
                        : config.getPatienceSeconds();
        arrive(guest, patience);
    }

    /**
     * Seats {@code guest} through the normal queue; lesson guests bring their own patience. There
     * is no head count: a guest comes in while a seat is free and the line has a spot to stand on.
     */
    void arrive(FeastGuest guest, int patience) {
        if (closed || !area.serving() || inLine() > config.getQueue().size()) return;
        Set<Integer> unavailable = new HashSet<>(customers.keySet());
        Location counter = at(config.getOrderCounter());
        int seat =
                coach != null && FeastTutorial.scriptedGuest(guest.ticket())
                        ? FeastSeating.nearest(
                                privateSeats,
                                unavailable,
                                guest.vip(),
                                index -> seats.get(index).distanceSquared(counter))
                        : FeastSeating.choose(
                                privateSeats,
                                unavailable,
                                guest.vip(),
                                ThreadLocalRandom.current());
        if (seat < 0) return;
        guests++;
        Customer customer = new Customer(spawn(guest), guest, seat, patience);
        customers.put(seat, customer);
        customer.label();
        if (guest.isSpecial()) {
            customer.particles(Particle.END_ROD, 12);
        } else if (guest.vip()) {
            customer.particles(Particle.TOTEM_OF_UNDYING, 12);
        }
    }

    private Mob spawn(FeastGuest guest) {
        Location entrance = at(config.getEntrance());
        Class<? extends Entity> type =
                guest.isSpecial()
                        ? EntityType.valueOf(guest.profile().entity()).getEntityClass()
                        : Villager.class;
        return (Mob) entrance.getWorld().spawn(entrance, type, entity -> prepare(entity, guest));
    }

    private static void prepare(Entity entity, FeastGuest guest) {
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setCustomNameVisible(true);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setCollidable(false);
        }
        if (entity instanceof Ageable ageable) ageable.setAdult();
        if (entity instanceof Breedable breedable) breedable.setAgeLock(true);
        switch (entity) {
            case Villager villager -> mark(villager, guest.request());
            case WanderingTrader trader -> {
                trader.setDespawnDelay(0); // never walks off mid-order
                trader.setRecipes(List.of());
            }
            case Snowman golem -> golem.setDerp(false);
            case Fox fox -> fox.setSleeping(false);
            default -> {}
        }
    }

    /**
     * Profession marks the order's category, badge level its tier and the biome outfit its main
     * process, so the kitchen can read an order from across the hall.
     */
    private static void mark(Villager villager, FeastRequest request) {
        Villager.Type biome =
                request.biome() == null
                        ? null
                        : Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(request.biome()));
        villager.setVillagerType(biome == null ? Villager.Type.PLAINS : biome);
        Villager.Profession profession =
                request.profession() == null
                        ? null
                        : Registry.VILLAGER_PROFESSION.get(
                                NamespacedKey.minecraft(request.profession()));
        villager.setProfession(profession == null ? Villager.Profession.NONE : profession);
        if (profession != null) {
            villager.setVillagerLevel(request.tier());
            // Experience above zero keeps a level-1 job without a workstation.
            villager.setVillagerExperience(1);
        }
        villager.setRecipes(List.of());
    }

    void tick(boolean second) {
        if (closed || !area.serving()) return;
        if (second) kitchen.tick();
        if (delivery != null) delivery.tick(second, coach == null || coach.deliveryOpen());
        List<Customer> queue =
                customers.values().stream()
                        .filter(
                                c ->
                                        c.order.phase() == FeastOrder.Phase.ENTERING
                                                || c.order.phase() == FeastOrder.Phase.ORDERING)
                        .toList();
        Iterator<Customer> iterator = customers.values().iterator();
        while (iterator.hasNext()) {
            Customer customer = iterator.next();
            if (!customer.entity.isValid()) {
                // Removed by the server, not by the team: release the table without a penalty.
                customer.close();
                iterator.remove();
                continue;
            }
            if (second && customer.order.tick()) lose(customer);
            customer.walk(queue.indexOf(customer));
            customer.look();
            if (second) {
                customer.label();
                customer.mood();
            }
            if (customer.order.phase() == FeastOrder.Phase.FINISHED) {
                customer.close();
                iterator.remove();
            }
        }
        if (second) refreshBoard();
        if (coach != null) coach.tick(second);
        if (second && noticeHold > 0) noticeHold--;
    }

    /** The lesson phase of the guest holding {@code ticket}, or null when none is present. */
    FeastOrder.Phase phaseOf(int ticket) {
        for (Customer customer : customers.values())
            if (customer.ticket() == ticket) return customer.order.phase();
        return null;
    }

    /** Guests walking to, standing in or ordering at the line; the counter is the first spot. */
    private int inLine() {
        int count = 0;
        for (Customer customer : customers.values())
            if (customer.order.phase() == FeastOrder.Phase.ENTERING
                    || customer.order.phase() == FeastOrder.Phase.ORDERING) count++;
        return count;
    }

    int customerCount() {
        return customers.size();
    }

    /** -1 while walking/ordering; the real patience clock only begins at the seat. */
    int waitingSeconds(int ticket) {
        return customers.values().stream()
                .filter(c -> c.ticket() == ticket && c.order.phase() == FeastOrder.Phase.WAITING)
                .mapToInt(c -> c.order.remaining())
                .findFirst()
                .orElse(-1);
    }

    // ---------------------------------------------------------------- service

    boolean owns(Entity entity) {
        return customers.values().stream()
                        .anyMatch(c -> c.entity.equals(entity) || c.chair == entity)
                || delivery != null && delivery.owns(entity);
    }

    boolean allowsPath(Entity entity) {
        return pathRequester == entity;
    }

    /** Starts a native path that the pathfinding guard lets through for this one request. */
    void navigate(Mob mob, Location target, double speed) {
        // Villager brains otherwise replace this route with workstation/roaming paths.
        pathRequester = mob;
        try {
            mob.getPathfinder().moveTo(target, speed);
        } finally {
            pathRequester = null;
        }
    }

    /** Delivery shelves first, then the kitchen's workstations and supplies. */
    boolean interact(Player player, Block block) {
        if (delivery != null && delivery.interact(player, block)) return false;
        return kitchen.interact(player, block);
    }

    boolean canUse(Player player, org.bukkit.inventory.Inventory inventory) {
        return kitchen.canUse(player, inventory)
                || delivery != null && delivery.canUse(player, inventory);
    }

    int delivered() {
        return delivery == null ? 0 : delivery.delivered;
    }

    int deliveryLost() {
        return delivery == null ? 0 : delivery.lost;
    }

    void serve(Player player, Entity entity) {
        if (!area.canPlay(player) || !team.getMembers().contains(player.getUniqueId())) return;
        Customer customer =
                customers.values().stream()
                        .filter(c -> c.entity.equals(entity) || c.chair == entity)
                        .findFirst()
                        .orElse(null);
        if (customer == null) return;
        if (customer.order.phase() == FeastOrder.Phase.ORDERING) {
            if (customer.order.takeOrder()) {
                noticeHold = 2;
                kitchen.actionBar(player, "order-taken", customer.ticket(), customer.dish());
                kitchen.sound(player, Sound.ENTITY_VILLAGER_YES, 1.2f);
            }
            return;
        }
        if (customer.order.phase() != FeastOrder.Phase.WAITING) return;
        FeastRequest request = customer.request;
        ItemStack held = player.getInventory().getItemInMainHand();
        int base = customer.guest.basePoints(config.getVipBonusPercent());
        boolean quick = customer.order.remaining() * 2 >= customer.order.patience();
        int points =
                customer.order.serve(
                        request.accepts(held) ? request.id() : "",
                        base,
                        config.getMaximumTip(),
                        streak,
                        reputation.tipPercent());
        if (points < 0) {
            noticeHold = 2;
            kitchen.actionBar(player, "wrong-dish", customer.ticket(), customer.dish());
            kitchen.sound(player, Sound.ENTITY_VILLAGER_NO, 1f);
            return;
        }
        held.setAmount(held.getAmount() - request.amount());
        completed++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        reputation.served(quick);
        award(points);
        teamActionBar(
                FeastKitchen.text("served", customer.ticket(), customer.dish(), points, streak));
        teamSound(
                Sound.ENTITY_PLAYER_LEVELUP,
                customer.guest.vip() || customer.guest.isSpecial() ? 1.6f : 1.9f);
        customer.particles(Particle.HAPPY_VILLAGER, 10);
        customer.served = true;
        customer.depart();
        if (coach != null) coach.served(customer.ticket());
    }

    private void lose(Customer customer) {
        missed++;
        streak = 0;
        reputation.lost();
        customer.angry = true;
        teamActionBar(FeastKitchen.text("lost", customer.ticket()));
        teamSound(Sound.ENTITY_VILLAGER_NO, 0.8f);
        customer.particles(Particle.ANGRY_VILLAGER, 6);
        customer.depart();
        if (coach != null) coach.lost(customer.ticket());
    }

    /** The whole team earns this amount, split evenly into the existing player-points ledger. */
    void award(int points) {
        // The lesson shows what an order would earn but never writes to the points ledger.
        if (!area.running()) return;
        for (UUID member : team.getMembers())
            area.addPlayerPoints(member, points / (double) team.getMembers().size());
    }

    void teamActionBar(String text) {
        noticeHold = 2;
        teamActionBarQuietly(text);
    }

    /** Sends without reserving the bar, for repeating hints that must not hide real notices. */
    void teamActionBarQuietly(String text) {
        for (Player player : area.onlineTeamPlayers(team))
            player.sendActionBar(LegacyText.component(text));
    }

    boolean quiet() {
        return noticeHold == 0;
    }

    void teamMessage(String text) {
        for (Player player : area.onlineTeamPlayers(team)) FeastKitchen.message(player, text);
    }

    void teamTitle(String title, String subtitle) {
        var shown = FeastCoach.screenTitle(title, subtitle);
        for (Player player : area.onlineTeamPlayers(team)) player.showTitle(shown);
    }

    void teamSound(Sound sound, float pitch) {
        for (Player player : area.onlineTeamPlayers(team))
            player.playSound(player.getLocation(), sound, 0.8f, pitch);
    }

    // ----------------------------------------------------------- presentation

    String stars() {
        int stars = reputation.stars();
        return FeastKitchen.text("star-on").repeat(stars)
                + FeastKitchen.text("star-off").repeat(5 - stars);
    }

    int streak() {
        return streak;
    }

    int bestStreak() {
        return bestStreak;
    }

    private void refreshBoard() {
        if (board != null && board.isValid()) {
            String text = boardText();
            if (!text.equals(boardText)) {
                boardText = text;
                board.text(LegacyText.component(text));
            }
        }
        if (deliveryBoard != null && deliveryBoard.isValid()) {
            String text = delivery.boardText();
            if (!text.equals(deliveryBoardText)) {
                deliveryBoardText = text;
                deliveryBoard.text(LegacyText.component(text));
            }
        }
    }

    /** Most pickup orders the board lists before it folds the rest into a count. */
    private static final int BOARD_LINES = 10;

    /**
     * The pickup half of the kitchen board shows only seated orders and their remaining time, most
     * urgent first; the delivery half belongs to {@link FeastDeliveryDesk}.
     */
    private String boardText() {
        List<Customer> waiting =
                customers.values().stream()
                        .filter(c -> c.order.phase() == FeastOrder.Phase.WAITING)
                        .sorted(Comparator.comparingInt(c -> c.order.remaining()))
                        .toList();
        StringBuilder text = new StringBuilder(FeastKitchen.text("board-header"));
        if (waiting.isEmpty()) text.append('\n').append(FeastKitchen.text("board-none"));
        for (Customer customer : waiting.subList(0, Math.min(BOARD_LINES, waiting.size())))
            text.append('\n')
                    .append(
                            FeastKitchen.text(
                                    "board-time",
                                    customer.ticket(),
                                    patienceColor(customer.order),
                                    customer.order.remaining()));
        if (waiting.size() > BOARD_LINES)
            text.append('\n').append(FeastKitchen.text("board-more", waiting.size() - BOARD_LINES));
        return text.toString();
    }

    private static String patienceColor(FeastOrder order) {
        int quarter = order.remaining() * 4;
        if (quarter > order.patience() * 2) return "&a";
        return quarter > order.patience() ? "&e" : "&c";
    }

    static String patienceBar(FeastOrder order) {
        int filled =
                Math.clamp(
                        (order.remaining() * BAR_CELLS + order.patience() - 1) / order.patience(),
                        0,
                        BAR_CELLS);
        return patienceColor(order) + "▮".repeat(filled) + "&8" + "▮".repeat(BAR_CELLS - filled);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (coach != null) coach.close();
        customers.values().forEach(Customer::close);
        customers.clear();
        if (delivery != null) delivery.close();
        kitchen.close();
        labels.forEach(Entity::remove);
        labels.clear();
        board = null;
        deliveryBoard = null;
    }

    private final class Customer implements AutoCloseable {
        final Mob entity;
        final FeastGuest guest;
        final FeastRequest request;
        final FeastOrder order;
        final int table;
        final Location seat;
        final double speed;
        List<Location> route;
        ArmorStand chair;
        int waypoint;
        int movingTicks;
        int pathTicks;
        int queuePosition = -1;
        boolean enteredAisle;
        boolean served;
        boolean angry;
        boolean warned;
        String name = "";

        /** Head and body while the guest stands or sits with its AI off; null while walking. */
        FeastGaze gaze;

        Customer(Mob entity, FeastGuest guest, int table, int patience) {
            this.entity = entity;
            this.guest = guest;
            this.request = guest.request();
            this.table = table;
            order = new FeastOrder(request.id(), patience);
            seat = seats.get(table);
            // Every guest walks at the same pace whatever its mob type's base speed: a fixed
            // multiplier made snow golems, foxes and cats crawl and held up the whole queue.
            var movement = entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            double base = movement == null ? 0.5 : movement.getBaseValue();
            speed = WALK_SPEED / Math.max(0.05, base);
        }

        int ticket() {
            return guest.ticket();
        }

        String dish() {
            String key = guest.isSpecial() ? "special-dish" : guest.vip() ? "vip-dish" : "dish";
            return FeastKitchen.text(key, request.name(), request.amount());
        }

        /** Special guests carry their name in front of every head label. */
        private String who() {
            return guest.profile() == null
                    ? ""
                    : FeastKitchen.text(
                                    guest.vip() ? "vip-tag" : "special-tag", guest.profile().name())
                            + " ";
        }

        void walk(int position) {
            if (order.phase() == FeastOrder.Phase.WAITING
                    || order.phase() == FeastOrder.Phase.FINISHED) return;
            if (order.phase() == FeastOrder.Phase.ORDERING) {
                if (order.advanceOrdering(5)) {
                    follow(FeastRoutes.seating(at(config.getOrderCounter()), seat));
                    label();
                }
                return;
            }
            if (order.phase() == FeastOrder.Phase.ENTERING && queuePosition != position) {
                queuePosition = position;
                Location waiting =
                        at(
                                position == 0
                                        ? config.getOrderCounter()
                                        : config.queueLocations().get(position - 1));
                follow(enteredAisle ? List.of(waiting) : List.of(at(config.getAisle()), waiting));
            }
            // Foxes nap in daylight through their own goals; a sleeping guest would never arrive.
            if (entity instanceof Fox fox && fox.isSleeping()) fox.setSleeping(false);
            Location target = route.get(waypoint);
            if (entity.getLocation().distanceSquared(target) > 0.64) {
                if ((movingTicks += 5) > 600) {
                    // A blocked path is the map's fault, not the team's: release without penalty.
                    order.finish();
                    return;
                }
                // Keep a path alive instead of restarting its acceleration every five ticks.
                if ((pathTicks -= 5) <= 0) {
                    navigate(entity, target, speed);
                    pathTicks = 20;
                }
                return;
            }
            if (order.phase() == FeastOrder.Phase.ENTERING) enteredAisle = true;
            if (waypoint + 1 < route.size()) {
                waypoint++;
                movingTicks = 0;
                pathTicks = 0;
                return;
            }
            entity.getPathfinder().stopPathfinding();
            if (order.phase() == FeastOrder.Phase.ENTERING) {
                entity.setAI(false);
                if (position == 0) {
                    order.reachCounter();
                    label();
                    teamSound(Sound.BLOCK_BELL_USE, 1.4f);
                }
                return;
            }
            if (order.phase() == FeastOrder.Phase.LEAVING) {
                order.finish();
                return;
            }
            chair =
                    seat.getWorld()
                            .spawn(
                                    seat,
                                    ArmorStand.class,
                                    entity -> {
                                        entity.setInvisible(true);
                                        entity.setMarker(true);
                                        entity.setGravity(false);
                                        entity.setInvulnerable(true);
                                        entity.setPersistent(false);
                                    });
            entity.setAI(false);
            if (!chair.addPassenger(entity)) {
                order.finish();
                return;
            }
            gaze = new FeastGaze(seat.getYaw());
            look();
            order.seat();
            label();
        }

        /**
         * Drives the head and body of a guest that stands in the queue, orders or sits: the body
         * settles on the counter or the table and the head glances around and follows a cook who
         * comes close. Walking guests keep their native look.
         */
        void look() {
            if (entity.hasAI() || !entity.isValid()) {
                gaze = null;
                return;
            }
            boolean seated = order.phase() == FeastOrder.Phase.WAITING;
            float rest = seated ? seat.getYaw() : at(config.getOrderCounter()).getYaw();
            // A guest that stopped mid-turn starts from its real body yaw, not a snapped one.
            if (gaze == null) gaze = new FeastGaze(entity.getBodyYaw());
            gaze.rest(rest);
            Location eye = entity.getEyeLocation();
            float attentionYaw = Float.NaN;
            float attentionPitch = 0;
            double closest = LOOK_RANGE * LOOK_RANGE;
            for (Player cook : area.onlineTeamPlayers(team)) {
                if (cook.getWorld() != eye.getWorld()) continue;
                Location target = cook.getEyeLocation();
                double distance = target.distanceSquared(eye);
                if (distance >= closest) continue;
                closest = distance;
                double dx = target.getX() - eye.getX();
                double dy = target.getY() - eye.getY();
                double dz = target.getZ() - eye.getZ();
                attentionYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                attentionPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            }
            boolean restless = seated && order.remaining() * 4 <= order.patience();
            gaze.update(5, attentionYaw, attentionPitch, restless, ThreadLocalRandom.current());
            entity.setRotation(gaze.head(), gaze.pitch());
            entity.setBodyYaw(gaze.body());
        }

        private void follow(List<Location> points) {
            route = points;
            waypoint = 0;
            movingTicks = 0;
            pathTicks = 0;
            entity.setAI(true);
        }

        void depart() {
            if (chair != null) {
                chair.eject();
                chair.remove();
                chair = null;
            }
            follow(FeastRoutes.leaving(at(config.getEntrance()), seat));
            label();
        }

        /** Low patience is announced once to the team and keeps fuming until served. */
        void mood() {
            if (order.phase() != FeastOrder.Phase.WAITING
                    || order.remaining() * 4 > order.patience()) return;
            if (!warned) {
                warned = true;
                teamActionBar(FeastKitchen.text("impatient", ticket()));
            }
            if (order.remaining() % 2 == 0) particles(Particle.ANGRY_VILLAGER, 1);
        }

        void particles(Particle particle, int count) {
            Location head = entity.getLocation().add(0, entity.getHeight() + 0.2, 0);
            head.getWorld().spawnParticle(particle, head, count, 0.3, 0.2, 0.3, 0);
        }

        void label() {
            String label =
                    who()
                            + switch (order.phase()) {
                                case ENTERING -> FeastKitchen.text("name-queued");
                                case ORDERING ->
                                        FeastKitchen.text(
                                                "name-ordering", dish(), order.orderingSeconds());
                                case SEATING -> FeastKitchen.text("name-seating", ticket());
                                case WAITING ->
                                        FeastKitchen.text(
                                                "name-waiting",
                                                ticket(),
                                                dish(),
                                                patienceBar(order));
                                default ->
                                        FeastKitchen.text(
                                                served
                                                        ? "name-happy"
                                                        : angry ? "name-angry" : "name-leaving");
                            };
            if (label.equals(name)) return;
            name = label;
            entity.customName(LegacyText.component(label));
        }

        @Override
        public void close() {
            if (chair != null) chair.remove();
            entity.remove();
        }
    }
}
