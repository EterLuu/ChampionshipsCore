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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TGTTOSTeamArea extends BaseSingleTeamArea {
    @Getter
    private final List<BlockState> blockStates = new CopyOnWriteArrayList<>();
    @Getter
    private final List<UUID> arrivedPlayers = new CopyOnWriteArrayList<>();
    private final Map<ChampionshipTeam, Integer> teamArrivedPlayers = new ConcurrentHashMap<>();
    @Getter
    private volatile int timer;
    private volatile ScheduledTask startGamePreparationTask;
    private volatile ScheduledTask startGameProgressTask;
    private volatile int arrivedTeamNumbers = 0;

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
            Location location = blockState.getLocation();
            scheduler.runAtLocation(location, () -> location.getBlock().setType(Material.AIR));
        }

        blockStates.clear();

        World world = getSpectatorSpawnLocation().getWorld();
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        BoundingBox boundingBox = BoundingBox.of(pos1, pos2);
        if (world != null) {
            cleanEntities(world, boundingBox, Boat.class);
            cleanEntities(world, boundingBox, Stray.class);
            cleanEntities(world, boundingBox, Chicken.class);
        }

        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_START_PREPARATION_TITLE, MessageConfig.TGTTOS_START_PREPARATION_SUBTITLE);

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(() -> {
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

        resetPlayerHealthFoodEffectLevelInventory();

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

        startGameProgressTask = scheduler.runTaskTimer(() -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.TGTTOS_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_START_TITLE, MessageConfig.TGTTOS_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
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

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    @Override
    public synchronized void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;
        setGameStageEnum(GameStageEnum.END);

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.TGTTOS_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.TGTTOS_GAME_END_TITLE, MessageConfig.TGTTOS_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        resetGame();
    }

    public synchronized void playerArrivedAtEndPoint(Player player) {
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
        int arrivedPlayers = teamArrivedPlayers.merge(championshipTeam, 1, Integer::sum);
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

        scheduler.runEntity(player, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleportAsync(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });
        player.teleportAsync(getSpectatorSpawnLocation());
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
            player.teleportAsync(getSpectatorSpawnLocation());
            scheduler.runEntity(player, () -> player.setGameMode(GameMode.ADVENTURE));
        }
        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleportAsync(getSpectatorSpawnLocation());
            if (getGameConfig().getAreaType().equals("ROAD")) {
                scheduler.runEntity(player, () -> player.setGameMode(GameMode.SURVIVAL));
            } else {
                scheduler.runEntity(player, () -> player.setGameMode(GameMode.ADVENTURE));
            }
        }
    }

    private void giveRoadToolsToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) scheduler.runEntity(player, () -> giveRoadToolToPlayer(player));
        }
    }

    public void giveRoadToolToPlayer(Player player) {
        if (player != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();

            ItemStack itemStack = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null)
                itemMeta.setUnbreakable(true);
            itemStack.setItemMeta(itemMeta);
            inventory.addItem(itemStack);

            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
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
            inventory.addItem(itemStack);
        }
    }

    public int getArrivedPlayerNums() {
        return arrivedPlayers.size();
    }

    public int getPlayerTeamNotArrived(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (championshipTeam == null)
            return 0;

        return championshipTeam.getMembers().size() - teamArrivedPlayers.getOrDefault(championshipTeam, 0);
    }

    private void giveBoatToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scheduler.runEntity(player, () -> giveBoatToPlayer(player));
            }
        }
    }

    public void giveBoatToPlayer(Player player) {
        ItemStack itemStack = new ItemStack(Material.OAK_BOAT);
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        playerInventory.addItem(itemStack);
    }

    public void teleportPlayerToSpawnPoint(Player player) {
        player.teleportAsync(getSpectatorSpawnLocation());
    }

    private void teleportAllPlayerToSpawnPoints() {
        Iterator<String> playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!playerSpawnPointsI.hasNext())
                    playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
                player.teleportAsync(Utils.getLocation(playerSpawnPointsI.next()));
            }
        }
    }

    private void spawnMonsters() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;
        for (String stringLocation : getGameConfig().getMonsterSpawnPoints()) {
            Location location = Utils.getLocation(stringLocation);
            if (location != null) scheduler.runAtLocation(location, () -> {
                LivingEntity entity = (LivingEntity) world.spawnEntity(location, EntityType.STRAY);
                entity.setRemoveWhenFarAway(false);
            });
        }
    }

    private void spawnChicken() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;

        Iterator<String> chickenSpawnPointsI = getGameConfig().getChickenSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            if (!chickenSpawnPointsI.hasNext())
                chickenSpawnPointsI = getGameConfig().getChickenSpawnPoints().iterator();
            Location location = Utils.getLocation(chickenSpawnPointsI.next());
            if (location != null) scheduler.runAtLocation(location, () -> {
                LivingEntity entity = (LivingEntity) world.spawnEntity(location, EntityType.CHICKEN);
                entity.setRemoveWhenFarAway(false);
            });
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

    @Override
    public String getWorldName() {
        return "tgttos";
    }
}
