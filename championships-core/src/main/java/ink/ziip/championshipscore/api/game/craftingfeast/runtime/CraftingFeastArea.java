package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastOrderSequence;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastPace;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastTutorial;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.*;

public final class CraftingFeastArea extends BaseMultiTeamGameInstance {
    private final List<FeastRestaurant> restaurants = new ArrayList<>();
    private final FeastEnclosure enclosure = new FeastEnclosure();
    private final FeastRecipeBook recipeBook = new FeastRecipeBook();
    private BukkitTask task;
    private FeastOrderSequence sequence;
    private int timer;
    private int ticks;
    private int nextArrival;
    private boolean rush;
    private int flow;
    private FeastPace pace;
    private FeastLesson lesson;

    public CraftingFeastArea(ChampionshipsCore plugin, CraftingFeastConfig config) {
        super(plugin, GameTypeEnum.CraftingFeast, new CraftingFeastHandler(plugin), config);
        getGameHandler().attach(this);
    }

    public void initialize() {
        getGameHandler().register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public CraftingFeastConfig getGameConfig() {
        return (CraftingFeastConfig) gameConfig;
    }

    @Override
    public CraftingFeastHandler getGameHandler() {
        return (CraftingFeastHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return getGameConfig().getWorldName();
    }

    @Override
    public int getTimer() {
        return timer;
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        Location point = getGameConfig().getSpectatorSpawnPoint();
        return point == null ? null : point.clone();
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams) {
        if (!validRoster(teams, getGameConfig().getRestaurants())
                || !getGameConfig().isPrepareReady()) return false;
        return super.tryStartGame(teams);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams, List<UUID> players) {
        if (!validRoster(teams, getGameConfig().getRestaurants())
                || !getGameConfig().isPrepareReady()
                || players == null
                || players.size() != teams.stream().mapToInt(t -> t.getMembers().size()).sum()
                || !new HashSet<>(players)
                        .equals(
                                teams.stream()
                                        .flatMap(t -> t.getMembers().stream())
                                        .collect(java.util.stream.Collectors.toSet())))
            return false;
        return super.tryStartGame(teams, players);
    }

    static boolean validRoster(List<ChampionshipTeam> teams, int restaurants) {
        if (teams == null || teams.isEmpty() || teams.size() > restaurants) return false;
        Set<UUID> members = new HashSet<>();
        for (ChampionshipTeam team : teams) {
            if (team == null || team.getMembers().isEmpty()) return false;
            for (UUID member : team.getMembers())
                if (member == null || !members.add(member)) return false;
        }
        return true;
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> points = new ArrayList<>();
        CraftingFeastConfig config = getGameConfig();
        for (int i = 0; i < gameTeams.size(); i++) {
            var origin = config.grid().origin(i);
            for (int x = (origin.getBlockX() - 1) >> 4;
                    x <= (origin.getBlockX() + config.getCopySize().getBlockX()) >> 4;
                    x++)
                for (int z = (origin.getBlockZ() - 1) >> 4;
                        z <= (origin.getBlockZ() + config.getCopySize().getBlockZ()) >> 4;
                        z++)
                    points.add(
                            new Location(
                                    config.getSpawn().getWorld(),
                                    x * 16 + 8,
                                    config.getSpawn().getY(),
                                    z * 16 + 8));
        }
        return points;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        CraftingFeastConfig config = getGameConfig();
        try {
            for (int i = 0; i < gameTeams.size(); i++)
                enclosure.enclose(
                        config.getSpawn().getWorld(),
                        config.grid().origin(i),
                        config.getCopySize());
            openRestaurants(false);
            for (UUID id : gamePlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && isGameplayParticipant(player)) {
                    recipeBook.enter(player);
                    wearUniform(player, restaurant(id).team);
                }
            }
        } catch (RuntimeException failure) {
            abortAndReset();
            throw failure;
        }
        startGameIntroduction(
                () -> {
                    resetPlayerHealthFoodEffectLevelInventory();
                    changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
                    for (UUID id : gamePlayers) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null) {
                            spawn(player);
                            equip(player);
                        }
                    }
                    startFinalCountdown(
                            FeastKitchen.text("preparing"),
                            FeastKitchen.text("start"),
                            FeastKitchen.text("start-hint"),
                            this::begin);
                });
    }

    /** Replaces every restaurant; a lesson restaurant never survives into the real game. */
    private void openRestaurants(boolean lesson) {
        discardRestaurants();
        for (int i = 0; i < gameTeams.size(); i++)
            restaurants.add(new FeastRestaurant(this, gameTeams.get(i), i, lesson));
        restaurants.forEach(FeastRestaurant::open);
    }

    // ------------------------------------------------------------- beginner lesson

    @Override
    protected int getIntroductionDurationSeconds() {
        return getGameConfig().isTutorialEnabled()
                ? FeastTutorial.DURATION_SECONDS
                : super.getIntroductionDurationSeconds();
    }

    @Override
    protected boolean isInteractiveIntroduction() {
        return getGameConfig().isTutorialEnabled() && !restaurants.isEmpty();
    }

    @Override
    protected void onIntroductionStarted() {
        if (!isInteractiveIntroduction()) return;
        openRestaurants(true);
        lesson = new FeastLesson(plugin, this);
        lesson.start();
    }

    @Override
    protected void onIntroductionStopped() {
        if (lesson == null) return;
        lesson.close();
        lesson = null;
        // Discard every lesson guest, station item and dropped dish, then open clean restaurants
        // so the real game starts from the same state as a game without the lesson.
        discardRestaurants();
        if (!isDisposed() && getGameStageEnum() == GameStageEnum.PREPARATION)
            openRestaurants(false);
    }

    /** True while the rule-introduction lesson owns the restaurants. */
    boolean lessonRunning() {
        return lesson != null
                && !isDisposed()
                && getGameStageEnum() == GameStageEnum.PREPARATION
                && isIntroductionPhase();
    }

    /** Kitchens, guests and deliveries work in the real game and during the lesson. */
    boolean serving() {
        return running() || lessonRunning();
    }

    @Override
    public boolean allowsIntroductionWorldInteraction(Player player) {
        return lessonRunning() && canPlay(player);
    }

    public boolean managesRecipeBook(Player player) {
        return isCurrentParticipantConnection(captureGameGeneration(), player)
                && isGameplayParticipant(player);
    }

    @Override
    public boolean restoreSharedPreGameParticipant(Player player) {
        if (managesRecipeBook(player)) recipeBook.enter(player);
        if (lessonRunning() && isGameplayParticipant(player)) {
            lesson.admit(player);
            return true;
        }
        boolean restored = super.restoreSharedPreGameParticipant(player);
        if (restored && managesRecipeBook(player)) {
            FeastRestaurant restaurant = restaurant(player.getUniqueId());
            if (restaurant != null) wearUniform(player, restaurant.team);
        }
        return restored;
    }

    @Override
    public void teleportAllPlayers(Location location) {
        if (lesson == null) {
            super.teleportAllPlayers(location);
            return;
        }
        // Each team learns in its own restaurant instead of the shared introduction point.
        for (UUID id : gamePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) lesson.admit(player);
        }
    }

    private void begin() {
        CraftingFeastConfig config = getGameConfig();
        timer = config.getTimer();
        ticks = 0;
        rush = false;
        pace = config.pace();
        flow = pace.percent(0);
        nextArrival = pace.interval(0);
        sequence =
                new FeastOrderSequence(
                        config.menu(),
                        java.util.concurrent.ThreadLocalRandom.current().nextLong(),
                        config.getVipEvery(),
                        config.specialGuestList(),
                        config.getSpecialChancePercent(),
                        config.vipGuestList());
        restaurants.forEach(FeastRestaurant::arrive);
        long generation = captureGameGeneration();
        task =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            if (generation != captureGameGeneration() || !running()) return;
                            ticks += 5;
                            boolean second = ticks % 20 == 0;
                            if (second) {
                                timer--;
                                if (timer <= 0) {
                                    endGame();
                                    return;
                                }
                                int elapsed = config.getTimer() - timer;
                                int percent = pace.percent(elapsed);
                                if (!rush && pace.peak(elapsed)) {
                                    rush = true;
                                    sendTitleToAllGamePlayers(
                                            FeastKitchen.text("rush-title"),
                                            FeastKitchen.text("rush-subtitle"));
                                    playSoundToAllGamePlayers(Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
                                }
                                if (percent > flow) {
                                    flow = percent;
                                    nextArrival = Math.min(nextArrival, pace.interval(elapsed));
                                    if (!rush)
                                        sendActionBarToAllGamePlayers(
                                                FeastKitchen.text("flow-up", percent));
                                }
                                updateGameTimerBossBar(
                                        rush
                                                ? FeastKitchen.text("timer-rush", timer)
                                                : FeastKitchen.text("timer", timer, percent),
                                        timer,
                                        config.getTimer());
                                if (--nextArrival <= 0) {
                                    nextArrival = pace.interval(elapsed);
                                    restaurants.forEach(FeastRestaurant::arrive);
                                }
                            }
                            restaurants.forEach(restaurant -> restaurant.tick(second));
                        },
                        5L,
                        5L);
    }

    void forEachRestaurant(java.util.function.Consumer<FeastRestaurant> action) {
        restaurants.forEach(action);
    }

    void equip(Player player) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r == null || !isGameplayParticipant(player)) return;
        r.kitchen.equip(player);
        wearUniform(player, r.team);
    }

    private static void wearUniform(Player player, ChampionshipTeam team) {
        player.getInventory().setHelmet(team.getHelmet());
        player.getInventory().setBoots(team.getBoots());
    }

    FeastOrderSequence sequence() {
        return sequence;
    }

    List<Player> onlineTeamPlayers(ChampionshipTeam team) {
        List<Player> players = new ArrayList<>();
        for (UUID id : team.getMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null
                    && isGameplayParticipant(player)
                    && plugin.getGameManager().getBasePlayerArea(id) == this) players.add(player);
        }
        return players;
    }

    boolean running() {
        return !isDisposed() && getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    boolean canPlay(Player player) {
        return serving()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && player.isOnline()
                && !player.isDead()
                && isGameplayParticipant(player)
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == this
                && getWorldName().equals(player.getWorld().getName())
                && inside(player, player.getLocation());
    }

    private FeastRestaurant restaurant(UUID player) {
        return restaurants.stream()
                .filter(r -> r.team.getMembers().contains(player))
                .findFirst()
                .orElse(null);
    }

    public int completed(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? 0 : r.completed;
    }

    public int missed(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? 0 : r.missed;
    }

    public String rating(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? "" : r.stars();
    }

    public int streak(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? 0 : r.streak();
    }

    public int delivered(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? 0 : r.delivered();
    }

    public double score(UUID player) {
        FeastRestaurant r = restaurant(player);
        return r == null ? 0 : getTeamPoints(r.team);
    }

    boolean inside(Player player, Location location) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r == null
                || location == null
                || location.getWorld() == null
                || !getWorldName().equals(location.getWorld().getName())) return false;
        var origin = getGameConfig().grid().origin(r.index);
        return BoundingBox.of(origin, origin.clone().add(getGameConfig().getCopySize()))
                .contains(location.toVector());
    }

    void spawn(Player player) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r != null && !isManagedSpectator(player)) {
            Location kitchen = r.playerSpawn(player.getUniqueId());
            if (kitchen == null) return;
            player.teleport(lessonRunning() ? kitchen : getPreparationTeleportLocation(kitchen));
            player.setFallDistance(0);
            player.setFoodLevel(20);
        }
    }

    private void restoreKitchenSpawn(Player player) {
        if (isDisposed() || !isGameplayParticipant(player)) return;
        if (lessonRunning()) {
            lesson.admit(player);
            return;
        }
        if (!isIntroductionPhase()
                && (getGameStageEnum() == GameStageEnum.PREPARATION
                        || getGameStageEnum() == GameStageEnum.COUNTDOWN
                        || getGameStageEnum() == GameStageEnum.PROGRESS)) {
            player.setGameMode(GameMode.SURVIVAL);
            spawn(player);
            equip(player);
        }
    }

    boolean interact(Player player, Block block) {
        if (!canPlay(player)
                || block == null
                || block.getLocation().distanceSquared(player.getLocation()) > 36) return false;
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r == null) return false;
        return r.interact(player, block);
    }

    boolean canUseInventory(Player player, org.bukkit.inventory.Inventory inventory) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        return r != null && r.canUse(player, inventory);
    }

    boolean supplyInventory(Player player, org.bukkit.inventory.Inventory inventory, int slot) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r == null || !r.kitchen.isSupply(inventory)) return false;
        r.kitchen.supply(player, inventory, slot);
        return true;
    }

    boolean canHarvest(Player player, Block block) {
        if (!canPlay(player) || block == null) return false;
        FeastRestaurant r = restaurant(player.getUniqueId());
        return r != null && r.kitchen.isResource(block);
    }

    void harvest(Player player, Block block) {
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r != null) r.kitchen.harvest(player, block);
    }

    boolean owns(Entity entity) {
        return restaurants.stream().anyMatch(r -> r.owns(entity));
    }

    boolean allowsCustomerPath(Entity entity) {
        return serving() && restaurants.stream().anyMatch(r -> r.allowsPath(entity));
    }

    void serve(Player player, Entity entity) {
        if (!canPlay(player)
                || !player.getWorld().equals(entity.getWorld())
                || player.getLocation().distanceSquared(entity.getLocation()) > 36) return;
        FeastRestaurant r = restaurant(player.getUniqueId());
        if (r != null) r.serve(player, entity);
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;
        stopRuntime();
        if (isSettlementAllowed()) addPlayerPointsToDatabase();
        setGameStageEnum(GameStageEnum.END);
        announceGameEnd(FeastKitchen.text("end"), FeastKitchen.text("end-hint"));
        sendMessageToAllGamePlayers(getTeamPointsRank());
        // Read-only result: also reaches members who became spectators during the game.
        for (FeastRestaurant restaurant : restaurants) {
            String summary =
                    FeastKitchen.text(
                            "summary",
                            restaurant.completed,
                            restaurant.missed,
                            restaurant.bestStreak(),
                            restaurant.stars(),
                            restaurant.delivered(),
                            restaurant.deliveryLost());
            for (UUID id : restaurant.team.getMembers()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && plugin.getGameManager().getBasePlayerArea(id) == this)
                    FeastKitchen.message(player, summary);
            }
        }
        beginPostGameSettlement();
        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    private void stopRuntime() {
        recipeBook.close();
        enclosure.close();
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (lesson != null) {
            lesson.close();
            lesson = null;
        }
        // Closed restaurants keep their counters for the end-of-game summary until reset.
        closeRestaurants();
    }

    private void discardRestaurants() {
        closeRestaurants();
        restaurants.clear();
    }

    private void closeRestaurants() {
        if (restaurants.isEmpty()) return;
        for (UUID id : gamePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && plugin.getGameManager().getBasePlayerArea(id) == this)
                player.closeInventory();
        }
        restaurants.forEach(FeastRestaurant::close);
        World world = Bukkit.getWorld(getWorldName());
        if (world != null) {
            for (FeastRestaurant restaurant : restaurants) {
                var origin = getGameConfig().grid().origin(restaurant.index);
                world.getNearbyEntities(
                                BoundingBox.of(
                                        origin, origin.clone().add(getGameConfig().getCopySize())),
                                entity -> entity instanceof Item)
                        .forEach(Entity::remove);
            }
        }
    }

    @Override
    public void resetArea() {
        stopRuntime();
        restaurants.clear();
        sequence = null;
        rush = false;
        timer = 0;
    }

    @Override
    public void dispose() {
        stopRuntime();
        super.dispose();
    }

    @Override
    public void handlePlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isGameplayParticipant(player)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        scheduleParticipantRespawn(player, () -> restoreKitchenSpawn(player));
    }

    @Override
    public void removeRuntimePlayers(Set<UUID> players) {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) recipeBook.leave(player);
        }
        super.removeRuntimePlayers(players);
    }

    @Override
    public void handlePlayerQuit(PlayerQuitEvent event) {
        recipeBook.leave(event.getPlayer());
        removePlayerFromBossBars(event.getPlayer());
    }

    @Override
    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (restorePostGameParticipant(player)) return;
        if (lessonRunning() && isGameplayParticipant(player)) {
            lesson.admit(player);
            return;
        }
        if (restoreSharedPreGameParticipant(player)) return;
        if (isManagedSpectator(player)) {
            handleSpectatorJoin(event);
            return;
        }
        scheduleParticipantRecovery(player, () -> restoreKitchenSpawn(player), 1L);
    }
}
