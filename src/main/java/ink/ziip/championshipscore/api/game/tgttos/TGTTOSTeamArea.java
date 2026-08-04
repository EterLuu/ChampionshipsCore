package ink.ziip.championshipscore.api.game.tgttos;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TGTTOSTeamArea extends BaseMultiTeamGameInstance {
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

        World world = getSpectatorSpawnLocation().getWorld();
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

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        teleportAllPlayers(getSpectatorSpawnLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.TGTTOS_START_PREPARATION,
                MessageConfig.TGTTOS_START_PREPARATION_TITLE, MessageConfig.TGTTOS_START_PREPARATION_SUBTITLE);

        timer = 10;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            showPreparationCountdown(timer);

            if (timer == 0) {
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
                startGameProgress();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        teleportAllPlayerToSpawnPoints();

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

        startFinalCountdown(MessageConfig.TGTTOS_GAME_START_SOON_TITLE,
                MessageConfig.TGTTOS_GAME_START_TITLE, MessageConfig.TGTTOS_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        spawnChicken();
        spawnMonsters();
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            changeLevelForAllGamePlayers(timer);
            updateSpectatorTimerBossBar(MessageConfig.TGTTOS_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
        }, this::endGame);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.TGTTOS_GAME_END_TITLE, MessageConfig.TGTTOS_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
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

            sendMessageToAllGamePlayers(MessageConfig.TGTTOS_ARRIVED_AT_POINT
                    .replace("%player%", Utils.formatPlayerName(player)));
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
            event.getEntity().teleport(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });
        player.teleport(getSpectatorSpawnLocation());
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
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.ADVENTURE);
            });
        }
        if (getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleport(getSpectatorSpawnLocation());
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            switch (getGameConfig().getAreaType()) {
                case "BOAT" -> championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    giveBoatToPlayer(player);
                });
                case "ROAD" -> championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    giveRoadToolToPlayer(player);
                });
                default -> championshipsCore.getServer().getScheduler().runTask(championshipsCore,
                        () -> player.setGameMode(GameMode.ADVENTURE));
            }
        }
    }

    private void giveRoadToolsToAllPlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            giveRoadToolToPlayer(player);
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
                giveBoatToPlayer(player);
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
        player.teleport(getSpectatorSpawnLocation());
    }

    private void teleportAllPlayerToSpawnPoints() {
        Iterator<String> playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!playerSpawnPointsI.hasNext())
                    playerSpawnPointsI = getGameConfig().getPlayerSpawnPoints().iterator();
                player.teleport(Utils.getLocation(playerSpawnPointsI.next()));
            }
        }
    }

    private void spawnMonsters() {
        World world = getSpectatorSpawnLocation().getWorld();
        if (world == null)
            return;
        for (String stringLocation : getGameConfig().getMonsterSpawnPoints()) {
            LivingEntity entity = (LivingEntity) world.spawnEntity(Utils.getLocation(stringLocation), EntityType.STRAY);
            entity.setRemoveWhenFarAway(false);
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

    @Override
    public String getWorldName() {
        return "tgttos";
    }
}
