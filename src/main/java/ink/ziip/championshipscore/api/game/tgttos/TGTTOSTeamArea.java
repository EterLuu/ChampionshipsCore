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
import org.bukkit.scheduler.BukkitTask;
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
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private int arrivedTeamNumbers = 0;

    public TGTTOSTeamArea(ChampionshipsCore plugin, TGTTOSConfig tgttosConfig) {
        super(plugin, GameTypeEnum.TGTTOS, new TGTTOSHandler(plugin), tgttosConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setTgttosTeamArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void resetArea() {
        cleanDroppedItems();

        arrivedPlayers.clear();
        teamArrivedPlayers.clear();

        arrivedTeamNumbers = 0;

        for (BlockState blockState : blockStates) {
            blockState.setType(Material.AIR);
            blockState.update(true);
        }

        blockStates.clear();

        World world = getGameConfig().getSpectatorSpawnPoint().getWorld();
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
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

        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        teleportAllPlayers(getGameConfig().getSpectatorSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_START_PREPARATION_TITLE, MessageConfig.TGTTOS_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllPlayerToSpawnPoints();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_SOON_TITLE, MessageConfig.TGTTOS_GAME_START_SOON_SUBTITLE);

        timer = getGameConfig().getTimer() + 5;

        resetPlayerHealthFoodEffectInventory();

        if (getGameConfig().getAreaType().equals("BOAT")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveBoatToAllPlayers();
        }
        if (getGameConfig().getAreaType().equals("ROAD")) {
            changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            giveRoadToolsToAllPlayers();
        }
        if (getGameConfig().getAreaType().equals("NONE")) {
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        }

        spawnChicken();
        spawnMonsters();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.TGTTOS_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_TITLE, MessageConfig.TGTTOS_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.TGTTOS_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_END_TITLE, MessageConfig.TGTTOS_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

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

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getGameConfig().getSpectatorSpawnPoint());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });
        player.teleport(getGameConfig().getSpectatorSpawnPoint());
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
            player.teleport(getGameConfig().getSpectatorSpawnPoint());
            player.setGameMode(GameMode.ADVENTURE);
        }
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleport(getGameConfig().getSpectatorSpawnPoint());
            if (getGameConfig().getAreaType().equals("ROAD")) {
                player.setGameMode(GameMode.SURVIVAL);
            } else {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
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
        player.teleport(getGameConfig().getSpectatorSpawnPoint());
    }

    private void teleportAllPlayerToSpawnPoints() {
        Iterator<String> playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (player.isOnline()) {
                    if (!playerSpawnPointsI.hasNext())
                        playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
                    player.teleport(Utils.getLocation(playerSpawnPointsI.next()));
                }
            }
        }
    }

    private void spawnMonsters() {
        World world = getGameConfig().getSpectatorSpawnPoint().getWorld();
        if (world == null)
            return;
        for (String stringLocation : getGameConfig().getMonsterSpawnPoints()) {
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(stringLocation), EntityType.STRAY);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private void spawnChicken() {
        World world = getGameConfig().getSpectatorSpawnPoint().getWorld();
        if (world == null)
            return;

        Iterator<String> chickenSpawnPointsI = getGameConfig().getChickenSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            if (!chickenSpawnPointsI.hasNext())
                chickenSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(chickenSpawnPointsI.next()), EntityType.CHICKEN);
            entity.setRemoveWhenFarAway(false);
        }
    }

    @Override
    public TGTTOSConfig getGameConfig() {
        return (TGTTOSConfig) gameConfig;
    }

    @Override
    public TGTTOSHandler getGameHandler() {
        return (TGTTOSHandler) gameHandler;
    }
}
