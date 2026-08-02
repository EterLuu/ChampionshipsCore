package ink.ziip.championshipscore.api.game.area;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public abstract class BaseArea {
    protected final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    protected final Map<UUID, Double> playerPoints = new ConcurrentHashMap<>();
    protected final ChampionshipsCore plugin;
    protected final FoliaScheduler scheduler;
    protected final BaseAreaHandler baseAreaHandler;
    protected final PlayerManager playerManager;
    private final Map<String, BossBarState> bossBars = new ConcurrentHashMap<>();
    private CompletableFuture<Void> worldTransition = CompletableFuture.completedFuture(null);

    protected BaseListener gameHandler;
    protected BaseGameConfig gameConfig;

    protected volatile GameStageEnum gameStageEnum;
    protected GameTypeEnum gameTypeEnum;

    public BaseArea(ChampionshipsCore plugin, GameTypeEnum gameTypeEnum, BaseListener gameHandler, BaseGameConfig gameConfig) {
        this.playerManager = plugin.getPlayerManager();

        this.gameStageEnum = GameStageEnum.END;
        this.plugin = plugin;
        this.scheduler = FoliaScheduler.region(plugin, this::getSpectatorSpawnLocation);
        this.gameTypeEnum = gameTypeEnum;

        this.gameHandler = gameHandler;
        this.gameConfig = gameConfig;

        baseAreaHandler = new BaseAreaHandler(plugin, this);
        baseAreaHandler.register();
    }

    public void resetGame() {
        resetBaseArea();
        playerPoints.clear();
        new HashSet<>(bossBars.keySet()).forEach(this::removeBossBar);

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public void resetPlayerHealthFoodEffectLevelInventory() {
        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();
        cleanInventoryForAllGamePlayers();
        changeLevelForAllGamePlayers(0);
    }

    public void addPlayerPoints(UUID uuid, double points) {
        playerPoints.merge(uuid, points, Double::sum);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + "Player " + plugin.getPlayerManager().getPlayerName(uuid) + " (" + uuid + ") get points " + points);
        ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
        if (championshipPlayer != null)
            championshipPlayer.sendActionBar("&e[+] " + points);
    }

    public void addPlayerPointsToAllTeamMembers(ChampionshipTeam championshipTeam, int points) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerPoints.merge(uuid, (double) points, Double::sum);
            plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", " + "Player " + plugin.getPlayerManager().getPlayerName(uuid) + " (" + uuid + ") get points " + points);
        }
    }

    public void sendMessageToConsole(String message) {
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", " + message);
    }

    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> playerPointEntry : playerPoints.entrySet()) {
            if (playerPointEntry.getValue() != 0)
                plugin.getRankManager().addPlayerPoints(playerPointEntry.getKey(), null, gameTypeEnum, gameConfig.getAreaName(), playerPointEntry.getValue());
        }
    }

    public int getTeamPoints(ChampionshipTeam championshipTeam) {
        int points = 0;
        for (UUID uuid : championshipTeam.getMembers()) {
            points += playerPoints.getOrDefault(uuid, 0d);
        }

        return points;
    }

    public String getPlayerPointsRank() {
        ArrayList<Map.Entry<UUID, Double>> list;
        list = new ArrayList<>(playerPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.GAME_BOARD_BAR
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%area%", gameConfig.getAreaName()))
                .append("\n");

        int i = 1;
        for (Map.Entry<UUID, Double> entry : list) {
            String username = plugin.getPlayerManager().getPlayerName(entry.getKey());
            if (username != null) {
                String row = MessageConfig.RANK_PLAYER_BOARD_ROW
                        .replace("%player_rank%", String.valueOf(i))
                        .replace("%player%", username)
                        .replace("%player_point%", String.valueOf(entry.getValue()));

                stringBuilder.append(row).append("\n");
                i++;
            }
        }

        return stringBuilder.toString();
    }

    public GameStageEnum getGameStageEnum() {
        synchronized (this) {
            return this.gameStageEnum;
        }
    }

    public void setGameStageEnum(GameStageEnum gameStageEnum) {
        synchronized (this) {
            this.gameStageEnum = gameStageEnum;
        }
    }

    public void loadMap(World.Environment environment) {
        loadMapAsync(environment);
    }

    public synchronized CompletableFuture<Void> loadMapAsync(World.Environment environment) {
        if (!plugin.isLoaded())
            return CompletableFuture.completedFuture(null);
        if (!worldTransition.isDone())
            return worldTransition;

        teleportAllSpectators(getLobbyLocation());
        setGameStageEnum(GameStageEnum.END);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", start loading world " + getWorldName());

        File target = plugin.getWorldManager().getWorldFolder(getWorldName());
        File maps = new File(plugin.getDataFolder(), "maps");
        File source = new File(maps, getWorldName());
        FoliaScheduler global = FoliaScheduler.global(plugin);

        worldTransition = global.runGlobalFuture(() -> getGameHandler().unRegister())
                .thenCompose(ignored -> plugin.getWorldManager().deleteWorldAsync(getWorldName(), true))
                .thenCompose(ignored -> global.runAsyncFuture(() ->
                        plugin.getWorldManager().copyWorldFiles(source, target)))
                .thenCompose(ignored -> plugin.getWorldManager().loadWorldAsync(getWorldName(), environment, false))
                .thenCompose(ignored -> global.runGlobalFuture(() -> {
                    getGameConfig().initializeConfiguration(plugin.getFolder());
                    getGameHandler().register();
                    setGameStageEnum(GameStageEnum.WAITING);
                    plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName()
                            + ", world " + getWorldName() + " loaded");
                    teleportAllSpectators(getSpectatorSpawnLocation());
                }))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to load arena world " + getWorldName(), throwable);
                    }
                });
        return worldTransition;
    }

    public void saveMap(World.Environment environment) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return;

        setGameStageEnum(GameStageEnum.END);
        plugin.getLogger().log(Level.INFO, gameTypeEnum + ", " + gameConfig.getAreaName() + ", start saving world " + getWorldName());
        teleportAllSpectators(getLobbyLocation());

        File dataDirectory = new File(plugin.getDataFolder(), "maps");
        File target = new File(dataDirectory, getWorldName());
        File source = plugin.getWorldManager().getWorldFolder(getWorldName());
        FoliaScheduler global = FoliaScheduler.global(plugin);

        plugin.getWorldManager().unloadWorldAsync(getWorldName(), true)
                .thenCompose(ignored -> global.runAsyncFuture(() -> {
                    plugin.getWorldManager().deleteWorldFiles(target);
                    plugin.getWorldManager().copyWorldFiles(source, target);
                    plugin.getWorldManager().deleteWorldFiles(source);
                }))
                .thenRun(() -> loadMap(environment))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save arena world " + getWorldName(), throwable);
                    return null;
                });
    }

    public void createBossBar(String name, String title, BarColor color, BarStyle style) {
        removeBossBar(name);
        CompletableFuture<BossBar> created = FoliaScheduler.global(plugin).supplyGlobal(() ->
                Bukkit.createBossBar(Utils.translateColorCodes(title), color, style));
        BossBarState state = new BossBarState(created);
        bossBars.put(name, state);
        created.exceptionally(throwable -> {
            bossBars.remove(name, state);
            plugin.getLogger().log(Level.SEVERE, "Failed to create BossBar " + name, throwable);
            return null;
        });
    }

    public void removeBossBar(String name) {
        BossBarState state = bossBars.remove(name);
        if (state != null) {
            state.execute(BossBar::removeAll);
        }
    }

    public void setBossBar(String name, String title) {
        withBossBar(name, bossBar -> bossBar.setTitle(Utils.translateColorCodes(title)));
    }

    public void addBossBarPlayer(String name, Player player) {
        if (player == null)
            return;

        withBossBar(name, bossBar -> bossBar.addPlayer(player));
    }

    public void removeBossBarPlayer(String name, Player player) {
        if (player == null)
            return;
        withBossBar(name, bossBar -> bossBar.removePlayer(player));
    }

    public void setBossBarProgress(String name, double progress) {
        withBossBar(name, bossBar -> bossBar.setProgress(progress));
    }

    private void withBossBar(String name, Consumer<BossBar> operation) {
        BossBarState state = bossBars.get(name);
        if (state == null) {
            plugin.getLogger().log(Level.WARNING, "BossBar with name " + name + " does not exist.");
            return;
        }
        state.execute(operation);
    }

    /** Serialises shared BossBar mutations on the global region for both Paper and Folia. */
    private final class BossBarState {
        private final CompletableFuture<BossBar> bossBar;
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

        private BossBarState(CompletableFuture<BossBar> bossBar) {
            this.bossBar = bossBar;
        }

        private synchronized void execute(Consumer<BossBar> operation) {
            tail = tail.handle((ignored, previousFailure) -> null)
                    .thenCompose(ignored -> bossBar.thenCompose(value ->
                    FoliaScheduler.global(plugin).runGlobalFuture(() -> operation.accept(value))));
            tail.exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to update BossBar", throwable);
                return null;
            });
        }
    }

    public Location getLobbyLocation() {
        return CCConfig.LOBBY_LOCATION;
    }

    public boolean isSpectator(@NotNull Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public void handleSpectatorDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isSpectator(player)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            scheduler.runEntity(player, () -> {
                player.spigot().respawn();
                removeSpectator(player);
            });
        }
    }

    public void handleSpectatorJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            player.teleportAsync(getSpectatorSpawnLocation()).thenAccept(success -> {
                if (success) scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
            });
        }
    }

    public void teleportAllSpectators(@NotNull Location location) {
        for (Player player : getOnlineSpectators()) {
            player.teleportAsync(location).thenAccept(success -> {
                if (success) scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
            });
        }
    }

    public void addSpectator(@NotNull Player player) {
        spectators.add(player.getUniqueId());
        player.teleportAsync(getSpectatorSpawnLocation()).thenAccept(success -> {
            if (success) scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
        });
    }

    public void removeAllSpectator() {
        for (Player player : getOnlineSpectators()) {
            removeSpectator(player);
        }
        spectators.clear();
    }

    public void endGameFinally() {
        removeAllSpectator();
        removeAllPlayers();
        endGame();
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (spectators.contains(uuid)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectator(player);
            } else {
                onlyRemoveSpectatorFromList(uuid);
            }
        }
    }

    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (spectators.contains(uuid)) {
            spectators.remove(player.getUniqueId());
            player.teleportAsync(getLobbyLocation()).thenAccept(success -> {
                if (success) scheduler.runEntity(player, () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.setLevel(0);
                });
            });
        }
    }

    public void onlyRemoveSpectatorFromList(@NotNull UUID uuid) {
        spectators.remove(uuid);
    }

    public List<Player> getOnlineSpectators() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }

        return list;
    }

    public List<ChampionshipPlayer> getOnlineCCSpectators() {
        List<ChampionshipPlayer> list = new ArrayList<>();
        for (UUID uuid : spectators) {
            ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(uuid);
            if (championshipPlayer != null) {
                list.add(championshipPlayer);
            }
        }

        return list;
    }

    public void sendMessageToAllSpectators(String message) {
        for (Player player : getOnlineSpectators()) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        }
        Bukkit.getServer().getLogger().log(Level.INFO, Utils.stripColorCodes(message));
    }

    public void sendActionBarToAllSpectators(String message) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendActionBar(message);
        }
    }

    public void changeLevelToAllSpectators(int level) {
        for (Player player : getOnlineSpectators()) {
            scheduler.runEntity(player, () -> player.setLevel(Math.abs(level)));
        }
    }

    public void sendTitleToAllSpectators(String title, String subTitle) {
        for (ChampionshipPlayer championshipPlayer : getOnlineCCSpectators()) {
            championshipPlayer.sendTitle(title, subTitle);
        }
    }

    public void cleanDroppedItems() {
        cleanEntities(Item.class);
    }

    protected void cleanEntities(Class<? extends Entity> entityType) {
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        World world = getSpectatorSpawnLocation().getWorld();
        if (world != null) {
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());
            cleanEntities(world, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), entityType);
        }
    }

    protected void cleanEntities(World world, BoundingBox bounds, Class<? extends Entity> entityType) {
        int minChunkX = ((int) Math.floor(bounds.getMinX())) >> 4;
        int maxChunkX = ((int) Math.floor(bounds.getMaxX())) >> 4;
        int minChunkZ = ((int) Math.floor(bounds.getMinZ())) >> 4;
        int maxChunkZ = ((int) Math.floor(bounds.getMaxZ())) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                double chunkMinX = chunkX << 4;
                double chunkMinZ = chunkZ << 4;
                BoundingBox chunkBox = new BoundingBox(
                        Math.max(bounds.getMinX(), chunkMinX), bounds.getMinY(),
                        Math.max(bounds.getMinZ(), chunkMinZ),
                        Math.min(bounds.getMaxX(), chunkMinX + 15.999), bounds.getMaxY(),
                        Math.min(bounds.getMaxZ(), chunkMinZ + 15.999));
                Location owner = new Location(world, chunkMinX + 8, bounds.getMinY(), chunkMinZ + 8);
                scheduler.runAtLocation(owner, () -> world.getNearbyEntities(
                                chunkBox, entityType::isInstance)
                        .forEach(Entity::remove));
            }
        }
    }

    public boolean notInArea(Location location) {
        if (location.getWorld() != null && getSpectatorSpawnLocation().getWorld() != null && location.getWorld().getName().equals(getSpectatorSpawnLocation().getWorld().getName())) {
            return !location.toVector().isInAABB(getGameConfig().getAreaPos1(), getGameConfig().getAreaPos2());
        }

        return true;
    }

    public abstract Location getSpectatorSpawnLocation();

    public abstract int getTimer();

    public abstract void endGame();

    public abstract void resetBaseArea();

    public abstract void resetArea();

    public abstract BaseGameConfig getGameConfig();

    public abstract BaseListener getGameHandler();

    public abstract String getWorldName();

    public abstract void removeAllPlayers();

    public abstract void startGamePreparation();

    public abstract void sendMessageToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGamePlayers(String message);

    public abstract void sendActionBarToAllGameSpectators(String message);

    public abstract void sendMessageToAllGamePlayersInActionbarAndMessage(String message);

    public abstract void sendTitleToAllGamePlayers(String title, String subTitle);

    public abstract void changeLevelForAllGamePlayers(int level);

    public abstract void changeGameModelForAllGamePlayers(GameMode gameMode);

    public abstract void setHealthForAllGamePlayers(double health);

    public abstract void revokeAllGamePlayersAdvancements();

    public abstract void setFoodLevelForAllGamePlayers(int level);

    public abstract void teleportAllPlayers(Location location);

    public abstract void clearEffectsForAllGamePlayers();

    public abstract void cleanInventoryForAllGamePlayers();

    public abstract void playSoundToAllGamePlayers(Sound sound, float volume, float pitch);

    public abstract boolean notAreaPlayer(@NotNull Player player);

    public abstract void handlePlayerDeath(@NotNull PlayerDeathEvent event);

    public abstract void handlePlayerQuit(@NotNull PlayerQuitEvent event);

    public abstract void handlePlayerJoin(@NotNull PlayerJoinEvent event);
}
