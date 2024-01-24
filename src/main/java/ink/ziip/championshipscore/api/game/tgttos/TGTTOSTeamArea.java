package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TGTTOSTeamArea extends BaseSingleTeamArea {
    @Getter
    private final List<BlockState> blockStates = new ArrayList<>();
    @Getter
    private final List<UUID> arrivedPlayers = new ArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamArrivedPlayers = new ConcurrentHashMap<>();
    @Getter
    private final TGTTOSConfig tgttosConfig;
    @Getter
    private int timer;
    private int startGamePreparationTaskId;
    private int startGameProgressTaskId;
    private int arrivedTeamNumbers = 0;

    protected void resetGame() {
        cleanDroppedItems();

        gameTeams.clear();
        gamePlayers.clear();
        playerPoints.clear();
        arrivedPlayers.clear();
        teamArrivedPlayers.clear();
        arrivedTeamNumbers = 0;

        for (BlockState blockState : blockStates) {
            blockState.setType(Material.AIR);
            blockState.update(true);
        }

        blockStates.clear();

        World world = getTgttosConfig().getSpectatorSpawnPoint().getWorld();
        Vector pos1 = tgttosConfig.getAreaPos1();
        Vector pos2 = tgttosConfig.getAreaPos2();
        BoundingBox boundingBox = new BoundingBox(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
        if (world != null) {
            for (Entity entity : world.getNearbyEntities(boundingBox)) {
                if (entity instanceof Boat) {
                    entity.remove();
                }
                if (entity instanceof Stray) {
                    entity.remove();
                }
                if (entity instanceof Chicken) {
                    entity.remove();
                }
            }
        }

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public TGTTOSTeamArea(ChampionshipsCore plugin, TGTTOSConfig tgttosConfig) {
        super(plugin);
        this.tgttosConfig = tgttosConfig;
        tgttosConfig.initializeConfiguration(plugin.getFolder());
        TGTTOSHandler tgttosHandler = new TGTTOSHandler(plugin, this);
        tgttosHandler.register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        teleportAllPlayers(tgttosConfig.getSpectatorSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_START_PREPARATION_TITLE, MessageConfig.TGTTOS_START_PREPARATION_SUBTITLE);

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
        teleportAllPlayerToSpawnPoints();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_SOON_TITLE, MessageConfig.TGTTOS_GAME_START_SOON_SUBTITLE);

        timer = tgttosConfig.getTimer() + 5;

        if (tgttosConfig.getAreaType().equals("BOAT")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveBoatToAllPlayers();
        }
        if (tgttosConfig.getAreaType().equals("ROAD")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveRoadToolsToAllPlayers();
        }
        if (tgttosConfig.getAreaType().equals("NONE")) {
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        }

        setHealthForAllGamePlayers(20);
        setFoodLevelForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();

        spawnChicken();
        spawnMonsters();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {

            if (timer > tgttosConfig.getTimer()) {
                String countDown = MessageConfig.TGTTOS_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - tgttosConfig.getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == tgttosConfig.getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_TITLE, MessageConfig.TGTTOS_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.TGTTOS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                scheduler.cancelTask(startGameProgressTaskId);
                endGame();
            }

            timer--;
        }, 0, 20L);
    }

    public void endGame() {
        scheduler.cancelTask(startGamePreparationTaskId);
        scheduler.cancelTask(startGameProgressTaskId);

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_END_TITLE, MessageConfig.TGTTOS_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);

        setHealthForAllGamePlayers(20);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank(GameTypeEnum.TGTTOS));
        sendMessageToAllGamePlayers(getTeamPintsRank(GameTypeEnum.TGTTOS));
        addPlayerPointsToDatabase(GameTypeEnum.TGTTOS);

        resetGame();
    }

    public void playerArrivedAtEndPoint(Player player) {
        UUID uuid = player.getUniqueId();
        if (!arrivedPlayers.contains(uuid)) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            if (championshipTeam == null)
                return;

            addPlayerPoints(uuid, 48 - arrivedPlayers.size());

            if (arrivedPlayers.size() < 10) {
                addPlayerPoints(uuid, 80 - 5 * arrivedPlayers.size());
            }

            arrivedPlayers.add(uuid);

            addTeamArrivedPlayer(championshipTeam);

            sendMessageToAllGamePlayers(MessageConfig.TGTTOS_ARRIVED_AT_POINT.replace("%player%", championshipTeam.getColoredColor() + player.getName()));
        }
    }

    public void addTeamArrivedPlayer(ChampionshipTeam championshipTeam) {
        teamArrivedPlayers.put(championshipTeam, teamArrivedPlayers.getOrDefault(championshipTeam, 0) + 1);
        int arrivedPlayers = teamArrivedPlayers.get(championshipTeam);
        if (arrivedPlayers == championshipTeam.getMembers().size()) {
            if (arrivedTeamNumbers < 4) {
                addPlayerPointsToAllTeamMembers(championshipTeam, 24 - 6 * arrivedPlayers);
                sendMessageToAllGamePlayers(MessageConfig.TGTTOS_TEAM_ARRIVED_AT_POINT.replace("%team%", championshipTeam.getColoredName()));
            }
            arrivedTeamNumbers++;
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getSpectatorSpawnLocation());
                event.getEntity().setGameMode(GameMode.SPECTATOR);
            }
        }.runTask(plugin);
        player.teleport(getTgttosConfig().getSpectatorSpawnPoint());
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleport(getSpectatorSpawnLocation());
            if (tgttosConfig.getAreaType().equals("ROAD")) {
                player.setGameMode(GameMode.SURVIVAL);
            } else {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return tgttosConfig.getSpectatorSpawnPoint();
    }

    @Override
    public String getAreaName() {
        return tgttosConfig.getAreaName();
    }

    private void giveRoadToolsToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (player.isOnline()) {
                    ItemStack itemStack = new ItemStack(Material.DIAMOND_PICKAXE);
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    if (itemMeta != null)
                        itemMeta.setUnbreakable(true);
                    itemStack.setItemMeta(itemMeta);
                    player.getInventory().addItem(itemStack);

                    ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                    if (championshipTeam != null) {
                        itemStack = championshipTeam.getConcrete();
                    } else {
                        itemStack = new ItemStack(Material.COBBLESTONE);
                    }
                    itemStack.setAmount(64);
                    itemMeta = itemStack.getItemMeta();
                    if (itemMeta != null)
                        itemMeta.setUnbreakable(true);
                    itemStack.setItemMeta(itemMeta);
                    player.getInventory().addItem(itemStack);
                }
            }
        }
    }

    private void giveBoatToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                giveBoatToPlayer(player);
            }
        }
    }

    public void giveBoatToPlayer(Player player) {
        ItemStack itemStack = new ItemStack(Material.OAK_BOAT);
        player.getInventory().addItem(itemStack);
    }

    public void teleportPlayerToSpawnPoint(Player player) {
        player.teleport(getTgttosConfig().getSpectatorSpawnPoint());
    }

    private void teleportAllPlayerToSpawnPoints() {
        Iterator<String> playerSpawnPointsI = tgttosConfig.getPlayerSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (player.isOnline()) {
                    if (!playerSpawnPointsI.hasNext())
                        playerSpawnPointsI = tgttosConfig.getPlayerSpawnPoints().iterator();
                    player.teleport(Utils.getLocation(playerSpawnPointsI.next()));
                }
            }
        }
    }

    private void spawnMonsters() {
        World world = tgttosConfig.getSpectatorSpawnPoint().getWorld();
        if (world == null)
            return;
        for (String stringLocation : tgttosConfig.getMonsterSpawnPoints()) {
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(stringLocation), EntityType.STRAY);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private void spawnChicken() {
        World world = tgttosConfig.getSpectatorSpawnPoint().getWorld();
        if (world == null)
            return;

        Iterator<String> chickenSpawnPointsI = tgttosConfig.getChickenSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            if (!chickenSpawnPointsI.hasNext())
                chickenSpawnPointsI = tgttosConfig.getPlayerSpawnPoints().iterator();
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(chickenSpawnPointsI.next()), EntityType.CHICKEN);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private void cleanDroppedItems() {
        Vector pos1 = tgttosConfig.getAreaPos1();
        Vector pos2 = tgttosConfig.getAreaPos2();
        World world = tgttosConfig.getSpectatorSpawnPoint().getWorld();
        if (world != null) {
            world.getNearbyEntities(new BoundingBox(
                            pos1.getX(),
                            pos1.getY(),
                            pos1.getZ(),
                            pos2.getX(),
                            pos2.getY(),
                            pos2.getZ()))
                    .forEach(entity -> {
                        if (entity instanceof Item) {
                            entity.remove();
                        }
                    });
        }
    }

    public boolean notInArea(Location location) {
        return !location.toVector().isInAABB(tgttosConfig.getAreaPos1(), tgttosConfig.getAreaPos2());
    }
}
