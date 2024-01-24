package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SkyWarsTeamArea extends BaseSingleTeamArea {
    @Getter
    private final List<BlockState> blockStates = new ArrayList<>();
    @Getter
    private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private int startGamePreparationTaskId;
    private int startGameProgressTaskId;
    private int borderCheckTaskId;
    private double radius;
    private double shrink;

    @Override
    public void resetArea() {
        blockStates.clear();
        teamDeathPlayers.clear();

        loadMap(getGameConfig().getAreaName());
        getGameConfig().initializeConfiguration(plugin.getFolder());
    }

    public SkyWarsTeamArea(ChampionshipsCore plugin, SkyWarsConfig skyWarsConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.SkyWars, new SkyWarsHandler(plugin), skyWarsConfig);

        getGameHandler().setSkyWarsArea(this);

        if (!firstTime) {
            loadMap(areaName);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        teleportAllPlayers(getGameConfig().getPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        cleanInventoryForAllGamePlayers();
        setHealthForAllGamePlayers(20);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_START_PREPARATION_TITLE, MessageConfig.SKY_WARS_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                scheduler.cancelTask(startGamePreparationTaskId);
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllTeamPlayersToSpawnPoints();

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (player == null || !player.isOnline()) {
                if (championshipTeam != null) {
                    addTeamDeathPlayer(championshipTeam, false);
                    plugin.getLogger().log(Level.INFO, GameTypeEnum.SkyWars + ", " + getGameConfig().getAreaName() + ", " + "Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + "), not online, added to death players");
                }
                deathPlayer.add(uuid);
            }
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_SOON_TITLE, MessageConfig.SKY_WARS_GAME_START_SOON_SUBTITLE);

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        cleanInventoryForAllGamePlayers();

        giveItemToAllGamePlayers();

        timer = getGameConfig().getTimer() + 5;
        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.SKY_WARS_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == getGameConfig().getTimer()) {

                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_TITLE, MessageConfig.SKY_WARS_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.SKY_WARS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == getGameConfig().getTimeEnableBoundaryShrink()) {
                startBorderShrink();
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_BOARD_SHRINK);
            }

            if (timer == getGameConfig().getTimeDisableHealthRegain()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_DEDUCT_FOOD_LEVEL);
            }

            if (timer <= getGameConfig().getTimeDisableHealthRegain()) {
                damageAllPlayers();
            }

            if (timer == 0) {
                changeLevelForAllGamePlayers(timer);
                endGame();
                scheduler.cancelTask(startGameProgressTaskId);
            }

            timer--;
        }, 0, 20L);
    }

    protected void startBorderShrink() {
        radius = getGameConfig().getBoundaryRadius();
        shrink = radius / (getGameConfig().getTimeEnableBoundaryShrink() - 5);

        borderCheckTaskId = scheduler.runTaskTimerAsynchronously(plugin, () -> {
            Location center = getGameConfig().getPreSpawnPoint();
            List<Location> locations = getParticleLocations(center);

            for (UUID uuid : gamePlayers) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    Location location = player.getLocation();
                    ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(player);

                    for (Location particleLocation : locations) {
                        if (Math.hypot(location.getX() - particleLocation.getX(), location.getZ() - particleLocation.getZ()) < 10) {
                            if (Math.abs(location.getY() - particleLocation.getY()) < 5)
                                player.spawnParticle(Particle.REDSTONE, particleLocation, 1, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                        }
                    }

                    if (Math.hypot(center.getX() - location.getX(), center.getZ() - location.getZ()) >= radius) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.damage(1);
                            }
                        }.runTask(plugin);
                        championshipPlayer.setRedScreen();
                    } else {
                        championshipPlayer.removeRedScreen();
                    }
                }
            }
            radius = radius - shrink;
            if (radius < 0)
                radius = 0;

        }, 0, 20L).getTaskId();
    }

    @NotNull
    private List<Location> getParticleLocations(Location center) {
        World world = center.getWorld();
        List<Location> locations = new ArrayList<>();

        if (world != null) {
            for (int h = getGameConfig().getBoundaryLowestHeight(); h < getGameConfig().getBoundaryDefaultHeight(); h++) {
                for (double d = 0; d <= (int) (2 * radius * Math.PI + 2); d += 1) {
                    Location particleLoc = new Location(center.getWorld(), center.getX(), h, center.getZ());
                    particleLoc.setX(center.getX() + Math.cos(d) * radius);
                    particleLoc.setZ(center.getZ() + Math.sin(d) * radius);
                    locations.add(particleLoc);
                }
            }
        }
        return locations;
    }

    protected void endGame() {
        scheduler.cancelTask(startGamePreparationTaskId);
        scheduler.cancelTask(startGameProgressTaskId);
        scheduler.cancelTask(borderCheckTaskId);

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_END_TITLE, MessageConfig.SKY_WARS_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        resetGame();
    }

    protected void calculatePoints() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 50);
            }
        }

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    event.getEntity().spigot().respawn();
                    event.getEntity().teleport(getGameConfig().getSpectatorSpawnPoint());
                    event.getEntity().setGameMode(GameMode.SPECTATOR);
                }
            }.runTask(plugin);
            player.teleport(getGameConfig().getPreSpawnPoint());
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getGameConfig().getSpectatorSpawnPoint());
                event.getEntity().setGameMode(GameMode.SPECTATOR);
            }
        }.runTask(plugin);

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        Player assailant = player.getKiller();
        EntityDamageEvent entityDamageEvent = player.getLastDamageCause();

        if (assailant != null) {
            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

            if (playerTeam == null || assailantTeam == null)
                return;

            if (playerTeam.equals(assailantTeam)) {
                return;
            }

            deathPlayer.add(player.getUniqueId());

            String message = MessageConfig.SKY_WARS_KILL_PLAYER;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;
                }
            }

            message = message
                    .replace("%player%", playerTeam.getColoredColor() + player.getName())
                    .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

            sendMessageToAllGamePlayers(message);
            addPlayerPoints(assailant.getUniqueId(), 40);
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null)
                addTeamDeathPlayer(championshipTeam, true);
            addPointsToAllSurvivePlayers();
        } else {

            String message = MessageConfig.SKY_WARS_PLAYER_DEATH;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;
                }
            }

            message = message.replace("%player%", player.getName());

            deathPlayer.add(player.getUniqueId());

            sendMessageToAllGamePlayers(message);

            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam != null)
                addTeamDeathPlayer(championshipTeam, true);
            addPointsToAllSurvivePlayers();
        }
    }

    private void addTeamDeathPlayer(ChampionshipTeam championshipTeam, boolean addPoints) {
        teamDeathPlayers.put(championshipTeam, teamDeathPlayers.getOrDefault(championshipTeam, 0) + 1);
        Integer deathPlayer = teamDeathPlayers.get(championshipTeam);
        plugin.getLogger().log(Level.INFO, GameTypeEnum.SkyWars + ", " + getGameConfig().getAreaName() + ", " + "Added team " + championshipTeam.getName() + " death player, now: " + deathPlayer);
        if (deathPlayer != null) {
            if (deathPlayer == championshipTeam.getMembers().size()) {
                sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_WHOLE_TEAM_WAS_KILLED.replace("%team%", championshipTeam.getColoredName()));
                if (addPoints)
                    addPointsToAllSurvivePlayers();
            }
        }
    }

    private void addPointsToAllSurvivePlayers() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 2);
            }
        }
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        deathPlayer.add(player.getUniqueId());
        sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_PLAYER_LEAVE.replace("%player%", player.getName()));
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null)
            addTeamDeathPlayer(championshipTeam, true);
        addPointsToAllSurvivePlayers();
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getGameConfig().getPreSpawnPoint());
            return;
        }

        player.teleport(getGameConfig().getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void teleportAllTeamPlayersToSpawnPoints() {
        Iterator<String> spawnPointsI = getGameConfig().getTeamSpawnPoints().iterator();

        Collections.shuffle(gameTeams);

        for (ChampionshipTeam championshipTeam : gameTeams) {
            if (spawnPointsI.hasNext())
                championshipTeam.teleportAllPlayers(Utils.getLocation(spawnPointsI.next()));
            else {
                spawnPointsI = getGameConfig().getTeamSpawnPoints().iterator();
                championshipTeam.teleportAllPlayers(Utils.getLocation(spawnPointsI.next()));
            }
        }
    }

    private void damageAllPlayers() {
        Collections.shuffle(gamePlayers);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                if (!deathPlayer.contains(player.getUniqueId())) {
                    int level = player.getFoodLevel() - 1;
                    player.setFoodLevel(Math.max(level, 0));
                }
            }
        }
    }

    private void giveItemToAllGamePlayers() {
        ItemStack bread = new ItemStack(Material.BREAD);
        bread.setAmount(3);

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerInventory inventory = player.getInventory();
                inventory.setItem(0, sword.clone());
                inventory.setItem(1, pickaxe.clone());
                inventory.setItem(2, bread.clone());
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
                    inventory.setItem(3, championshipTeam.getConcrete());
                    inventory.setItem(4, championshipTeam.getConcrete());
                }
            }
        }
    }

    public void loadMap(String areaName) {
        getGameHandler().unRegister();

        String rootWorldFolder = plugin.getServer().getWorldContainer().getAbsolutePath();
        File rootWorldDirectory = new File(rootWorldFolder);
        File target = new File(rootWorldDirectory, "skywars_" + areaName);
        if (target.isDirectory()) {
            String[] list = target.list();
            if (list != null && list.length > 0) {
                plugin.getWorldManager().deleteWorld("skywars_" + areaName, true);
            }
        }

        File dataDirectory = plugin.getDataFolder();
        File maps = new File(dataDirectory, "maps");
        File source = new File(maps, "skywars_" + areaName);

        plugin.getWorldManager().copyWorld(source, target);

        plugin.getWorldManager().loadWorld("skywars_" + areaName, World.Environment.NORMAL, false);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().register();
    }

    public void saveMap() {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return;

        setGameStageEnum(GameStageEnum.END);

        World editWorld = plugin.getServer().getWorld("skywars_" + getWorldName());
        if (editWorld != null) {
            for (Player player : editWorld.getPlayers()) {
                player.teleport(CCConfig.LOBBY_LOCATION);
            }
            plugin.getWorldManager().unloadWorld("skywars_" + getWorldName(), true);
            File dataDirectory = new File(plugin.getDataFolder(), "maps");
            File target = new File(dataDirectory, "skywars_" + getWorldName());
            plugin.getWorldManager().deleteWorld(target);
            File source = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), "skywars_" + getWorldName());
            plugin.getWorldManager().copyWorld(source, target);
            plugin.getWorldManager().deleteWorld(source);
        }

        loadMap(getGameConfig().getAreaName());
        getGameConfig().initializeConfiguration(plugin.getFolder());

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public SkyWarsConfig getGameConfig() {
        return (SkyWarsConfig) gameConfig;
    }

    @Override
    public SkyWarsHandler getGameHandler() {
        return (SkyWarsHandler) gameHandler;
    }

    public String getWorldName() {
        return "skywars_" + getGameConfig().getAreaName();
    }
}
