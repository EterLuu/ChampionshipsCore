package ink.ziip.championshipscore.api.game.craftingfeast.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastDelivery;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastGuestProfile;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastPace;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastRequest;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastSupply;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.LocationConfig;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.*;

@Getter
@Setter
public final class CraftingFeastConfig extends BaseGameConfig {
    /**
     * Queue points a map may define. Guests are not capped by a count: a restaurant takes a guest
     * while a seat is free and the line still has a spot, and arrivals are paced by the flow.
     */
    public static final int MAX_QUEUE = 32;

    public static final int LATEST_VERSION = 15;

    private final String resourceName = "craftingfeast/area.yml";
    private final String folderName = "craftingfeast/areas/";

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "world-name", nullable = true)
    private String worldName = "";

    /** Runs the interactive beginner lesson in each team's restaurant during the introduction. */
    @ConfigOption(path = "tutorial-enabled")
    private boolean tutorialEnabled = true;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "restaurants")
    private int restaurants;

    @ConfigOption(path = "arrival-seconds")
    private int arrivalSeconds;

    @ConfigOption(path = "patience-seconds")
    private int patienceSeconds;

    @ConfigOption(path = "maximum-tip")
    private int maximumTip;

    @ConfigOption(path = "vip-every")
    private int vipEvery;

    @ConfigOption(path = "vip-patience-percent")
    private int vipPatiencePercent;

    @ConfigOption(path = "vip-bonus-percent")
    private int vipBonusPercent;

    @ConfigOption(path = "arrival-start-percent")
    private int arrivalStartPercent;

    @ConfigOption(path = "arrival-peak-percent")
    private int arrivalPeakPercent;

    @ConfigOption(path = "arrival-peak-seconds")
    private int arrivalPeakSeconds;

    @ConfigOption(path = "special-chance-percent")
    private int specialChancePercent;

    @ConfigOption(path = "special-guests", nullable = true)
    private ConfigurationSection specialGuests;

    @ConfigOption(path = "vip-guests", nullable = true)
    private ConfigurationSection vipGuests;

    @ConfigOption(path = "copy-origin", nullable = true)
    private Vector copyOrigin;

    @ConfigOption(path = "copy-step", nullable = true)
    private Vector copyStep;

    @ConfigOption(path = "copy-size", nullable = true)
    private Vector copySize;

    @ConfigOption(path = "spawn", nullable = true)
    private Location spawn;

    @ConfigOption(path = "entrance", nullable = true)
    private Location entrance;

    @ConfigOption(path = "aisle", nullable = true)
    private Location aisle;

    @ConfigOption(path = "order-counter", nullable = true)
    private Location orderCounter;

    @ConfigOption(path = "order-board", nullable = true)
    private Location orderBoard;

    @ConfigOption(path = "queue", nullable = true)
    private List<String> queue = new ArrayList<>();

    @ConfigOption(path = "delivery-board", nullable = true)
    private Location deliveryBoard;

    /** Numbered delivery shelf barrels, in slot order; an empty list disables delivery. */
    @ConfigOption(path = "delivery-slots", nullable = true)
    private List<String> deliverySlots = new ArrayList<>();

    /** Rider waypoints: the first is where the rider appears, the last the pickup window. */
    @ConfigOption(path = "delivery-route", nullable = true)
    private List<String> deliveryRoute = new ArrayList<>();

    @ConfigOption(path = "delivery-first-seconds")
    private int deliveryFirstSeconds;

    @ConfigOption(path = "delivery-eta-seconds")
    private int deliveryEtaSeconds;

    @ConfigOption(path = "delivery-order-seconds")
    private int deliveryOrderSeconds;

    @ConfigOption(path = "delivery-max-orders")
    private int deliveryMaxOrders;

    @ConfigOption(path = "delivery-wait-seconds")
    private int deliveryWaitSeconds;

    @ConfigOption(path = "delivery-cooldown-seconds")
    private int deliveryCooldownSeconds;

    @ConfigOption(path = "delivery-bonus-percent")
    private int deliveryBonusPercent;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "seats", nullable = true)
    private List<String> seats = new ArrayList<>();

    @ConfigOption(path = "private-seats", nullable = true)
    private List<String> privateSeats = new ArrayList<>();

    @ConfigOption(path = "resources", nullable = true)
    private ConfigurationSection resources;

    @ConfigOption(path = "supplies", nullable = true)
    private ConfigurationSection supplies;

    @ConfigOption(path = "workstations", nullable = true)
    private ConfigurationSection workstations;

    @ConfigOption(path = "requests", nullable = true)
    private ConfigurationSection requests;

    public CraftingFeastConfig(ChampionshipsCore plugin, String name) {
        super(plugin, name);
    }

    @Override
    public int getLatestVersion() {
        return LATEST_VERSION;
    }

    @Override
    public Vector getAreaPos1() {
        return copyOrigin == null ? null : copyOrigin.clone();
    }

    @Override
    public Vector getAreaPos2() {
        if (copyOrigin == null || copyStep == null || copySize == null || restaurants < 1)
            return null;
        return grid().origin(restaurants - 1).add(copySize);
    }

    public RowArenaGrid grid() {
        Vector origin = copyOrigin == null ? new Vector() : copyOrigin;
        Vector step = copyStep == null ? new Vector(64, 0, 0) : copyStep;
        return new RowArenaGrid(origin, step);
    }

    public Location at(Location location, int restaurant) {
        return location.clone().add(grid().delta(restaurant));
    }

    /** Main-hall seats followed by private-room seats; see {@link #privateSeatCount()}. */
    public List<Location> seatLocations() {
        List<Location> result = new ArrayList<>();
        if (seats != null) seats.stream().map(LocationConfig::readLocation).forEach(result::add);
        if (privateSeats != null)
            privateSeats.stream().map(LocationConfig::readLocation).forEach(result::add);
        return List.copyOf(result);
    }

    public int privateSeatCount() {
        return privateSeats == null ? 0 : privateSeats.size();
    }

    public List<Location> queueLocations() {
        if (queue == null) return List.of();
        return queue.stream().map(LocationConfig::readLocation).toList();
    }

    public boolean isDeliveryEnabled() {
        return deliverySlots != null && !deliverySlots.isEmpty();
    }

    public List<Location> deliverySlotLocations() {
        if (deliverySlots == null) return List.of();
        return deliverySlots.stream().map(LocationConfig::readLocation).toList();
    }

    public List<Location> deliveryRouteLocations() {
        if (deliveryRoute == null) return List.of();
        return deliveryRoute.stream().map(LocationConfig::readLocation).toList();
    }

    public FeastDelivery.Timing deliveryTiming() {
        return new FeastDelivery.Timing(
                deliveryFirstSeconds,
                deliveryEtaSeconds,
                deliveryOrderSeconds,
                deliveryMaxOrders,
                deliveryWaitSeconds,
                deliveryCooldownSeconds);
    }

    public Map<Material, List<Location>> resourceLocations() {
        return blockLocations(resources);
    }

    public Map<Material, List<Location>> workstationLocations() {
        return blockLocations(workstations);
    }

    private static Map<Material, List<Location>> blockLocations(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<Material, List<Location>> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isBlock() || material.isAir())
                throw new IllegalArgumentException("Invalid workspace block: " + key);
            List<Location> points =
                    section.getStringList(key).stream().map(LocationConfig::readLocation).toList();
            if (points.isEmpty())
                throw new IllegalArgumentException("Missing workspace points: " + key);
            result.put(material, points);
        }
        return result;
    }

    public List<FeastSupply> supplyBarrels() {
        if (supplies == null) return List.of();
        List<FeastSupply> result = new ArrayList<>();
        for (String key : supplies.getKeys(false)) {
            ConfigurationSection supply =
                    Objects.requireNonNull(supplies.getConfigurationSection(key));
            ConfigurationSection items =
                    Objects.requireNonNull(supply.getConfigurationSection("items"));
            Map<Material, Integer> materials = new LinkedHashMap<>();
            for (String item : items.getKeys(false)) {
                Material material = Material.matchMaterial(item);
                if (material == null) throw new IllegalArgumentException("Invalid supply: " + item);
                materials.put(material, items.getInt(item));
            }
            result.add(
                    new FeastSupply(
                            supply.getString("name", key),
                            LocationConfig.readLocation(supply.get("location")),
                            materials));
        }
        return List.copyOf(result);
    }

    public List<FeastRequest> menu() {
        return requests(requests);
    }

    private static List<FeastRequest> requests(ConfigurationSection section) {
        if (section == null) return List.of();
        List<FeastRequest> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection request =
                    Objects.requireNonNull(section.getConfigurationSection(key));
            boolean clue = request.contains("answers");
            if (clue && (request.contains("material") || !request.isString("name")))
                throw new IllegalArgumentException("Clue needs a description and answers: " + key);
            List<?> names =
                    clue
                            ? request.getList("answers", List.of())
                            : List.of(request.getString("material", ""));
            Set<Material> answers = EnumSet.noneOf(Material.class);
            for (Object entry : names) {
                Material material =
                        entry instanceof String name ? Material.matchMaterial(name) : null;
                if (material == null)
                    throw new IllegalArgumentException("Invalid answer for " + key + ": " + entry);
                answers.add(material);
            }
            result.add(
                    new FeastRequest(
                            key,
                            request.getString("name", key),
                            answers,
                            clue,
                            request.getInt("amount", 1),
                            request.getInt("points"),
                            request.getString("profession"),
                            request.getString("biome")));
        }
        return List.copyOf(result);
    }

    public List<FeastGuestProfile> specialGuestList() {
        return profiles(specialGuests, false);
    }

    public List<FeastGuestProfile> vipGuestList() {
        return profiles(vipGuests, true);
    }

    private List<FeastGuestProfile> profiles(ConfigurationSection section, boolean vip) {
        if (section == null) return List.of();
        List<FeastGuestProfile> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection guest =
                    Objects.requireNonNull(section.getConfigurationSection(key));
            ConfigurationSection orders = guest.getConfigurationSection("requests");
            String entity = vip ? "VILLAGER" : guest.getString("entity", "");
            if (!vip && "VILLAGER".equals(entity))
                throw new IllegalArgumentException(
                        "Villager profiles belong in vip-guests: " + key);
            result.add(
                    new FeastGuestProfile(
                            key,
                            guest.getString("name", key),
                            entity,
                            vip ? vipBonusPercent : guest.getInt("bonus-percent"),
                            orders == null ? List.of() : requests(orders)));
        }
        return List.copyOf(result);
    }

    /** Every request a guest can make, for reachability and stack-size checks. */
    public List<FeastRequest> allRequests() {
        List<FeastRequest> result = new ArrayList<>(menu());
        specialGuestList().forEach(guest -> result.addAll(guest.requests()));
        vipGuestList().forEach(guest -> result.addAll(guest.requests()));
        return List.copyOf(result);
    }

    public FeastPace pace() {
        return new FeastPace(
                arrivalSeconds, arrivalStartPercent, arrivalPeakPercent, arrivalPeakSeconds, timer);
    }

    public void validate() {
        if (worldName == null
                || worldName.isBlank()
                || copyOrigin == null
                || copyStep == null
                || copySize == null
                || spawn == null
                || spawn.getWorld() == null
                || entrance == null
                || aisle == null
                || orderCounter == null
                || orderBoard == null
                || spectatorSpawnPoint == null)
            throw new IllegalArgumentException("Missing required Crafting Feast locations");
        int seatCount = seats == null ? 0 : seats.size();
        int queueCount = queue == null ? 0 : queue.size();
        if (timer < 30
                || timer > 3600
                || restaurants < 1
                || restaurants > 16
                || arrivalSeconds < 5
                || patienceSeconds < 10
                || maximumTip < 0
                || maximumTip > 1000
                || vipEvery < 0
                || vipEvery > 100
                || vipPatiencePercent < 30
                || vipPatiencePercent > 100
                || vipBonusPercent < 0
                || vipBonusPercent > 300
                || arrivalStartPercent < 20
                || arrivalStartPercent > 400
                || arrivalPeakPercent < arrivalStartPercent
                || arrivalPeakPercent > 400
                || arrivalPeakSeconds < 0
                || arrivalPeakSeconds > 3600
                || seatCount == 0
                || seatCount + privateSeatCount() > 128
                || queueCount > MAX_QUEUE
                || specialChancePercent < 0
                || specialChancePercent > 50
                || workstationLocations().isEmpty())
            throw new IllegalArgumentException("Invalid Crafting Feast timing/capacity");
        // Adjacent restaurants may share a barrier plane, but it must not enter either interior.
        ink.ziip.championshipscore.api.game.arena.ArenaPreparer.validateNonOverlappingCopies(
                grid(), restaurants, copySize.clone().add(new Vector(1, 1, 1)));
        for (int i = 0; i < restaurants; i++) {
            int floor = grid().origin(i).getBlockY();
            if (floor < spawn.getWorld().getMinHeight()
                    || floor + copySize.getBlockY() >= spawn.getWorld().getMaxHeight())
                throw new IllegalArgumentException("Crafting Feast enclosure outside world height");
        }
        List<Location> locations =
                new ArrayList<>(
                        List.of(
                                spawn,
                                entrance,
                                aisle,
                                orderCounter,
                                orderBoard,
                                spectatorSpawnPoint));
        locations.addAll(seatLocations());
        Set<Vector> chairs = new HashSet<>();
        for (Location seat : seatLocations())
            if (!chairs.add(new Vector(seat.getBlockX(), seat.getBlockY(), seat.getBlockZ())))
                throw new IllegalArgumentException("Duplicate Crafting Feast seat: " + seat);
        locations.addAll(queueLocations());
        List<Location> waiting = new ArrayList<>(queueLocations());
        waiting.add(orderCounter);
        for (int i = 0; i < waiting.size(); i++)
            for (int j = i + 1; j < waiting.size(); j++)
                if (waiting.get(i).getWorld() == waiting.get(j).getWorld()
                        && waiting.get(i).distanceSquared(waiting.get(j)) < 2.25)
                    throw new IllegalArgumentException(
                            "Customer queue points must be 1.5 blocks apart");
        Set<org.bukkit.util.Vector> occupied = new HashSet<>();
        List<Location> blocks = new ArrayList<>();
        resourceLocations().values().forEach(blocks::addAll);
        workstationLocations().values().forEach(blocks::addAll);
        supplyBarrels().forEach(s -> blocks.add(s.location()));
        if (isDeliveryEnabled()) {
            if (deliveryBoard == null
                    || deliverySlots.size() > 9
                    || deliveryRoute == null
                    || deliveryRoute.size() < 2
                    || deliveryBonusPercent < 0
                    || deliveryBonusPercent > 300)
                throw new IllegalArgumentException("Invalid Crafting Feast delivery counter");
            deliveryTiming(); // throws on an invalid timeline
            blocks.addAll(deliverySlotLocations());
            locations.add(deliveryBoard);
            locations.addAll(deliveryRouteLocations());
        }
        for (Location block : blocks)
            if (!occupied.add(new Vector(block.getBlockX(), block.getBlockY(), block.getBlockZ())))
                throw new IllegalArgumentException("Overlapping workspace points: " + block);
        locations.addAll(blocks);
        for (Material material : workstationLocations().keySet())
            if (!Set.of(
                            Material.CRAFTING_TABLE,
                            Material.FURNACE,
                            Material.SMOKER,
                            Material.BLAST_FURNACE,
                            Material.STONECUTTER,
                            Material.CHEST,
                            Material.BARREL)
                    .contains(material))
                throw new IllegalArgumentException("Unsupported workstation: " + material);
        for (Location location : locations) {
            if (location.getWorld() == null
                    || !worldName.equals(location.getWorld().getName())
                    || !org.bukkit.util.BoundingBox.of(copyOrigin, copyOrigin.clone().add(copySize))
                            .contains(location.toVector()))
                throw new IllegalArgumentException(
                        "Crafting Feast point outside restaurant: " + location);
        }
        if (menu().isEmpty()) throw new IllegalArgumentException("Empty Crafting Feast menu");
        Set<String> ids = new HashSet<>();
        for (FeastRequest request : allRequests()) {
            if (!ids.add(request.id()))
                throw new IllegalArgumentException("Duplicate request id: " + request.id());
            if (request.answers().stream()
                    .anyMatch(item -> !item.isItem() || request.amount() > item.getMaxStackSize()))
                throw new IllegalArgumentException(
                        "Invalid request item or stack size: " + request.id());
            if (request.profession() != null
                    && org.bukkit.Registry.VILLAGER_PROFESSION.get(
                                    org.bukkit.NamespacedKey.minecraft(request.profession()))
                            == null)
                throw new IllegalArgumentException("Unknown villager profession: " + request.id());
            if (request.biome() != null
                    && org.bukkit.Registry.VILLAGER_TYPE.get(
                                    org.bukkit.NamespacedKey.minecraft(request.biome()))
                            == null)
                throw new IllegalArgumentException("Unknown villager biome: " + request.id());
        }
    }

    @Override
    public boolean isPrepareReady() {
        if (!super.isPrepareReady() || !isPrepareWorldBuilt()) return false;
        try {
            validate();
            ink.ziip.championshipscore.api.game.craftingfeast.mechanics.FeastVanillaRecipes
                    .validate(this);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
