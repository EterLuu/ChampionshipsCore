package ink.ziip.championshipscore.api.game.skywars;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.skywars.SkyWarsShrink;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.player.ChampionshipPlayer;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SkyWarsTeamArea extends BaseSingleTeamArea {
    @Getter
    private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    private final List<SkyWarsShrink> shrinkTimes = new ArrayList<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private BukkitTask borderCheckTask;
    private double radius;
    private double shrink;
    private double height;
    private double heightShrink;
    private double low;
    private double lowShrink;

    @Override
    public void resetArea() {
        deathPlayer.clear();
        teamDeathPlayers.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        borderCheckTask = null;

        loadMap(World.Environment.NORMAL);
    }

    @Override
    public void resetGame() {
        resetBaseArea();
        playerPoints.clear();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    public SkyWarsTeamArea(ChampionshipsCore plugin, SkyWarsConfig skyWarsConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.SkyWars, new SkyWarsHandler(plugin), skyWarsConfig);

        getGameHandler().setSkyWarsArea(this);
        skyWarsConfig.setAreaName(areaName);

        if (!firstTime) {
            loadMap(World.Environment.NORMAL);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }

        if (!getGameConfig().getShrinkTime().isEmpty()) {
            for (String key : getGameConfig().getShrinkTime()) {
                String[] shrinkTimeSetting = key.split(":");
                if (shrinkTimeSetting.length == 4) {
                    try {
                        int start = Integer.parseInt(shrinkTimeSetting[0]);
                        int end = Integer.parseInt(shrinkTimeSetting[1]);
                        int toRadius = Integer.parseInt(shrinkTimeSetting[2]);
                        int toHeight = Integer.parseInt(shrinkTimeSetting[3]);
                        shrinkTimes.add(new SkyWarsShrink(start, end, toRadius, toHeight));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().log(Level.WARNING, "Invalid shrink time format: " + key);
                    }
                } else {
                    plugin.getLogger().log(Level.WARNING, "Invalid shrink time format: " + key);
                }
            }
        }
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        teleportAllPlayers(getGameConfig().getPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_START_PREPARATION_TITLE, MessageConfig.SKY_WARS_START_PREPARATION_SUBTITLE);

        setBorderShrinkTask(getGameConfig().getTimeEnableBoundaryShrink(),
                0,
                getGameConfig().getBoundaryRadius(),
                getGameConfig().getBoundaryDefaultHeight(),
                getGameConfig().getBoundaryLowestHeight(),
                getGameConfig().getBoundaryRadius(),
                getGameConfig().getBoundaryMiddleHeight()
        );

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            // changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGamePlayers(MessageConfig.SKY_WARS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllTeamPlayersToSpawnPoints();

        int offlinePlayers = 0;
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                offlinePlayers++;
            }
        }
        addPointsToAllSurvivePlayers(offlinePlayers * 10);

        for (UUID uuid : deathPlayer) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null) {
                addTeamDeathPlayer(championshipTeam, false);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.SkyWars + ", " + getGameConfig().getAreaName() + ", " + "Player " + playerManager.getPlayerName(uuid) + " (" + uuid + "), not online, added to death players");
            }
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_SOON_TITLE, MessageConfig.SKY_WARS_GAME_START_SOON_SUBTITLE);

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        timer = getGameConfig().getTimer() + 5;
        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.SKY_WARS_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {

                if (getGameConfig().isGlassCage()) {
                    scheduler.runTaskAsynchronously(plugin, () -> {
                        for (ChampionshipTeam championshipTeam : gameTeams) {
                            for (Player player : championshipTeam.getOnlinePlayers()) {
                                clearGlassCages(player);
                                break;
                            }
                        }
                    });
                }

                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_START_TITLE, MessageConfig.SKY_WARS_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            // changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGamePlayers(MessageConfig.SKY_WARS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == getGameConfig().getTimeEnableBoundaryShrink()) {
                startBorderShrink();
            }

            for (SkyWarsShrink skyWarsShrink : shrinkTimes) {
                if (timer == skyWarsShrink.getStartTime()) {
                    setBorderShrinkTask(skyWarsShrink.getStartTime(),
                            skyWarsShrink.getEndTime(),
                            radius,
                            height,
                            low,
                            skyWarsShrink.getToRadius(),
                            skyWarsShrink.getToHeight()
                    );
                    sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_BOARD_SHRINK);
                    playSoundToAllGamePlayers(Sound.BLOCK_ANVIL_USE, 1, 12F);
                }
                if (timer == skyWarsShrink.getEndTime()) {
                    lowShrink = 0;
                    heightShrink = 0;
                    shrink = 0;
                    sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_STOP_BOARD_SHRINK);
                    playSoundToAllGamePlayers(Sound.BLOCK_BELL_USE, 1, 12F);
                }
            }

            if (timer == getGameConfig().getTimeDisableHealthRegain()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_DEDUCT_FOOD_LEVEL);
            }

            if (timer <= getGameConfig().getTimeDisableHealthRegain()) {
                damageAllPlayers();
            }

            if (timer == 0) {
                // changeLevelForAllGamePlayers(timer);
                sendActionBarToAllGamePlayers(MessageConfig.SKY_WARS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    private void setBorderShrinkTask(int start, int end, double startRadius, double startHeight, double startLow, int toRadius, int toHeight) {
        radius = startRadius;
        shrink = (radius - toRadius) / (start - end);

        height = startHeight;
        low = startLow;

        heightShrink = (height - toHeight) / (start - end);
        lowShrink = heightShrink;
    }

    protected void startBorderShrink() {
        final List<UUID> gamePlayersCopy = new ArrayList<>(gamePlayers);
        borderCheckTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {
            Location center = getGameConfig().getPreSpawnPoint();

            for (UUID uuid : gamePlayersCopy) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    Location location = player.getLocation();
                    ChampionshipPlayer championshipPlayer = plugin.getPlayerManager().getPlayer(player);

                    double distance = Math.hypot(center.getX() - location.getX(), center.getZ() - location.getZ());

                    if (radius - 10 < distance && distance < radius + 10) {
                        setParticles(player, !(radius <= 20));
                    }

                    if (location.getY() > height - 10 || location.getY() < low + 10) {
                        setHeightParticles(player, height);
                        setHeightParticles(player, low);
                    }

                    if (distance >= radius || location.getY() > height || location.getY() < low) {
                        scheduler.runTask(plugin, () -> player.damage(1));
                        championshipPlayer.setRedScreen();
                        championshipPlayer.sendActionBar(MessageConfig.SKY_WARS_OUT_OF_BORDER);
                    } else {
                        championshipPlayer.removeRedScreen();
                    }
                }
            }
            height = height - heightShrink;
            low = low + lowShrink;
            radius = radius - shrink;
            if (radius < 0)
                radius = 0;
            if (height < getGameConfig().getBoundaryMiddleHeight())
                height = getGameConfig().getBoundaryMiddleHeight();
            if (low > getGameConfig().getBoundaryMiddleHeight())
                low = getGameConfig().getBoundaryMiddleHeight();

        }, 0, 20L);
    }

    private void setParticles(Player player, boolean byAngle) {
        scheduler.runTaskAsynchronously(plugin, () -> {
            Location center = getGameConfig().getPreSpawnPoint();
            Location location = player.getLocation();
            World world = location.getWorld();

            double x = center.getX();
            double z = center.getZ();
            double x1 = location.getX();
            double z1 = location.getZ();
            double y = location.getY();

            if (world != null) {

                double alpha = Math.atan2(z1 - z, x1 - x);

                for (double h = y - 3; h < y + 5; h++) {
                    double beta, endBeta, increment;
                    if (byAngle) {
                        beta = alpha - 0.0872;
                        endBeta = alpha + 0.0872;
                        increment = 0.01;
                    } else {
                        beta = 0;
                        endBeta = 20;
                        increment = 1;
                    }
                    for (; beta <= endBeta; beta += increment) {
                        double x2 = center.getX() + radius * Math.cos(beta);
                        double z2 = center.getZ() + radius * Math.sin(beta);
                        Location particleLoc = new Location(center.getWorld(), x2, h, z2);
                        player.spawnParticle(Particle.DUST, particleLoc, 1, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                    }
                }
            }
        });
    }

    private void setHeightParticles(Player player, double y) {
        scheduler.runTaskAsynchronously(plugin, () -> {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world != null) {
                for (int radius = 1; radius < 5; radius++) {
                    for (double beta = 0; beta <= 20; beta += 1) {
                        double x2 = location.getX() + radius * Math.cos(beta);
                        double z2 = location.getZ() + radius * Math.sin(beta);
                        Location particleLoc = new Location(location.getWorld(), x2, y, z2);
                        player.spawnParticle(Particle.DUST, particleLoc, 1, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));
                    }
                }
            }
        });
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (borderCheckTask != null)
            borderCheckTask.cancel();

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.SKY_WARS_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.SKY_WARS_GAME_END_TITLE, MessageConfig.SKY_WARS_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

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
        addPlayerPointsToDatabase();
    }

    private void addTeamDeathPlayer(ChampionshipTeam championshipTeam, boolean addedPoints) {
        teamDeathPlayers.put(championshipTeam, teamDeathPlayers.getOrDefault(championshipTeam, 0) + 1);
        Integer deathPlayer = teamDeathPlayers.get(championshipTeam);
        plugin.getLogger().log(Level.INFO, GameTypeEnum.SkyWars + ", " + getGameConfig().getAreaName() + ", " + "Added team " + championshipTeam.getName() + " death player, now: " + deathPlayer);
        if (deathPlayer != null) {
            if (deathPlayer == championshipTeam.getMembers().size()) {
                sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_WHOLE_TEAM_WAS_KILLED.replace("%team%", championshipTeam.getColoredName()));
                if (addedPoints)
                    addPointsToAllSurvivePlayers(2);
            }
        }
    }

    protected void addDeathPlayer(Player player) {
        addDeathPlayer(player.getUniqueId());
    }

    private synchronized void addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid))
            return;

        deathPlayer.add(uuid);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (championshipTeam != null) {
            addTeamDeathPlayer(championshipTeam, true);
        }
        addPointsToAllSurvivePlayers(10);
    }

    private void addPointsToAllSurvivePlayers(int points) {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, points);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getSpectatorSpawnLocation());
            });
            player.teleport(getGameConfig().getPreSpawnPoint());
            return;
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        spawnTomb(player, event.getDrops());

        addDeathPlayer(player);

        Player assailant = player.getKiller();
        EntityDamageEvent entityDamageEvent = player.getLastDamageCause();

        if (assailant != null) {
            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

            if (playerTeam == null || assailantTeam == null)
                return;

            String message = MessageConfig.SKY_WARS_KILL_PLAYER;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;
                }
            }

            if (playerTeam.equals(assailantTeam)) {
                message = MessageConfig.SKY_WARS_KILL_TEAM_PLAYER;
                message = message
                        .replace("%player%", playerTeam.getColoredColor() + player.getName())
                        .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());
                sendMessageToAllGamePlayers(message);
                return;
            }

            message = message
                    .replace("%player%", playerTeam.getColoredColor() + player.getName())
                    .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

            sendMessageToAllGamePlayers(message);

            addPlayerPoints(assailant.getUniqueId(), 40);
        } else {

            String message = MessageConfig.SKY_WARS_PLAYER_DEATH;

            if (entityDamageEvent != null) {
                EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                    message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;
                }
            }

            message = message.replace("%player%", player.getName());
            sendMessageToAllGamePlayers(message);
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

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        sendMessageToAllGamePlayers(MessageConfig.SKY_WARS_PLAYER_LEAVE.replace("%player%", player.getName()));
        addDeathPlayer(player);
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

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    public int getPlayerBoarderDistance(Player player) {
        Location location = player.getLocation();
        Location center = getSpectatorSpawnLocation();
        double distance = Math.hypot(center.getX() - location.getX(), center.getZ() - location.getZ());
        return (int) Math.abs(radius - distance);
    }

    public void spawnTomb(Player player, List<ItemStack> items) {
        if (player == null || !gamePlayers.contains(player.getUniqueId())) {
            return;
        }

        Location location = player.getLocation();
        World world = location.getWorld();

        if (world != null) {
            if (world.getBlockAt(location).getType() != Material.AIR) {
                for (int i = 1; i <= 5; i++) {
                    Location belowLocation = location.clone().add(0, i, 0);
                    if (world.getBlockAt(belowLocation).getType() == Material.AIR) {
                        location = belowLocation;
                        break;
                    }
                }
            }

            if (world.getBlockAt(location).getType() != Material.AIR) {
                return;
            }

            world.getBlockAt(location).setType(Material.CHEST);
            world.spawnParticle(Particle.DUST, location.clone().add(0.5, 0.5, 0.5), 100, new Particle.DustOptions(Color.fromRGB(0xff0000), 1));

            Chest chest = (Chest) world.getBlockAt(location).getState();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    chest.getInventory().addItem(item);
                }
            }

            items.clear();
        }
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public int getSurvivedTeamNums() {
        int i = 0;
        for (ChampionshipTeam championshipTeam : teamDeathPlayers.keySet()) {
            if (teamDeathPlayers.get(championshipTeam) == championshipTeam.getMembers().size())
                i++;
        }
        return gameTeams.size() - i;
    }

    private void teleportAllTeamPlayersToSpawnPoints() {
        Iterator<String> spawnPointsI = getGameConfig().getTeamSpawnPoints().iterator();

        Collections.shuffle(gameTeams);

        for (ChampionshipTeam championshipTeam : gameTeams) {
            if (spawnPointsI.hasNext()) {
                Location location = Utils.getLocation(spawnPointsI.next());
                for (int i = 0; i < championshipTeam.getOnlinePlayers().size(); i++) {
                    Player player = championshipTeam.getOnlinePlayers().get(i);
                    if (player != null) {
                        Location spawnLocation = location.clone();
                        spawnLocation.setX(spawnLocation.getX() + (i % 2 == 0 ? -1 : 1));
                        spawnLocation.setZ(spawnLocation.getZ() + (i < 2 ? -1 : 1));
                        player.teleport(spawnLocation);
                    }
                }
            } else {
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

    private void clearGlassCages(Player player) {
        com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(player.getWorld());

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            Location playerLocation = player.getLocation();
            int radius = 5;
            BlockVector3 pos1 = BukkitAdapter.asBlockVector(playerLocation.clone().add(-radius, -radius, -radius));
            BlockVector3 pos2 = BukkitAdapter.asBlockVector(playerLocation.clone().add(radius, radius, radius));

            Region region = new CuboidRegion(pos1, pos2);
            Set<BaseBlock> baseBlocks = new HashSet<>();
            baseBlocks.add(new BaseBlock(BukkitAdapter.asBlockState(new ItemStack(Material.GLASS))));
            editSession.replaceBlocks(region, baseBlocks, BlockTypes.AIR);
        } catch (Exception ignored) {
        }
    }

    private void giveItemToAllGamePlayers() {
//        ItemStack bread = new ItemStack(Material.BREAD);
//        bread.setAmount(8);
//
//        ItemStack sword = new ItemStack(Material.IRON_SWORD);
//        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
//        pickaxe.addEnchantment(Enchantment.EFFICIENCY, 3);
//        ItemStack bow = new ItemStack(Material.BOW);
//        ItemStack arrows = new ItemStack(Material.ARROW);
//        arrows.setAmount(4);
//        ItemStack chestPlate = new ItemStack(Material.IRON_CHESTPLATE);

        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerInventory inventory = player.getInventory();
//                inventory.addItem(bread.clone());
//                inventory.addItem(sword.clone());
//                inventory.addItem(pickaxe.clone());
//                inventory.addItem(bow.clone());
//                inventory.addItem(arrows.clone());
//                inventory.setChestplate(chestPlate);
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
//                    inventory.addItem(championshipTeam.getConcrete());
//                    inventory.addItem(championshipTeam.getConcrete());
//                    inventory.setLeggings(championshipTeam.getLeggings());
                    inventory.setBoots(championshipTeam.getBoots());
                }
            }
        }
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
        return "skywars_" + gameConfig.getAreaName();
    }
}
