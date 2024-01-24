package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxArea extends BaseTeamArea {
    @Getter
    private final BattleBoxConfig battleBoxConfig;
    private final Map<UUID, Integer> playerPoints = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private int startGamePreparationTaskId;
    private int startGameProgressTaskId;
    private int woolCheckerTaskId;

    protected void resetGame() {
        resetRegionBlocks();
        cleanDroppedItems();
        rightChampionshipTeam = null;
        leftChampionshipTeam = null;
        playerPoints.clear();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public BattleBoxArea(ChampionshipsCore championshipsCore, BattleBoxConfig battleBoxConfig) {
        super(championshipsCore);
        this.battleBoxConfig = battleBoxConfig;
        battleBoxConfig.initializeConfiguration(plugin.getFolder());
        BattleBoxHandler battleBoxHandler = new BattleBoxHandler(plugin, this);
        battleBoxHandler.register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    public boolean tryStartGame(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        startGamePreparation();
        return true;
    }

    protected void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        rightChampionshipTeam.teleportAllPlayers(battleBoxConfig.getRightPreSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(battleBoxConfig.getLeftPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_START_PREPARATION_TITLE, MessageConfig.BATTLE_BOX_START_PREPARATION_SUBTITLE);

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
        resetRegionBlocks();
        summonPotions();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE);

        timer = battleBoxConfig.getTimer() + 5;

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        rightChampionshipTeam.teleportAllPlayers(battleBoxConfig.getRightSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(battleBoxConfig.getLeftSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {

            if (timer > battleBoxConfig.getTimer()) {
                String countDown = MessageConfig.BATTLE_BOX_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - battleBoxConfig.getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == battleBoxConfig.getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.BATTLE_BOX_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                scheduler.cancelTask(startGameProgressTaskId);
            }

            timer--;
        }, 0, 20L);

        woolCheckerTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            if (timer == -1) {
                changeLevelForAllGamePlayers(0);
                endGame();
                scheduler.cancelTask(woolCheckerTaskId);
            }

            HashMap<Material, Integer> blockCount = countBlocksInRegion();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                int rightTeamWool = blockCount.getOrDefault(rightChampionshipTeam.getWool().getType(), 0);
                int leftTeamWool = blockCount.getOrDefault(leftChampionshipTeam.getWool().getType(), 0);
                if (rightTeamWool == 9 || leftTeamWool == 9) {
                    endGameInForm();
                }
            }
        }, 0, 1L);
    }

    protected void endGameInForm() {
        setGameStageEnum(GameStageEnum.STOPPING);
        cleanInventoryForAllGamePlayers();
        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        changeGameModelForAllGamePlayers(GameMode.SPECTATOR);
    }

    protected void endGame() {
        scheduler.cancelTask(startGamePreparationTaskId);
        scheduler.cancelTask(startGameProgressTaskId);
        scheduler.cancelTask(woolCheckerTaskId);

        calculatePoints();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        rightChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        leftChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {
        HashMap<Material, Integer> blockCount = countBlocksInRegion();
        int rightTeamWool = blockCount.getOrDefault(rightChampionshipTeam.getWool().getType(), 0);
        int leftTeamWool = blockCount.getOrDefault(leftChampionshipTeam.getWool().getType(), 0);

        int rightTeamPoints = 0;
        int leftTeamPoints = 0;

        for (Map.Entry<UUID, Integer> playerPointsEntry : playerPoints.entrySet()) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerPointsEntry.getKey());
            if (championshipTeam != null) {
                if (championshipTeam.equals(rightChampionshipTeam)) {
                    rightTeamPoints += playerPointsEntry.getValue();
                }
                if (championshipTeam.equals(leftChampionshipTeam)) {
                    leftTeamPoints += playerPointsEntry.getValue();
                }
            }
        }

        String message = MessageConfig.BATTLE_BOX_WIN;

        if (rightTeamWool > leftTeamWool) {
            rightTeamPoints += 40 * CCConfig.TEAM_MAX_MEMBERS;

            message = message.replace("%team%", rightChampionshipTeam.getColoredName())
                    .replace("%points%", String.valueOf(rightTeamPoints));

            for (UUID uuid : rightChampionshipTeam.getMembers()) {
                playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
            }
        } else if (leftTeamWool > rightTeamWool) {
            leftTeamPoints += 40 * CCConfig.TEAM_MAX_MEMBERS;

            message = message.replace("%team%", leftChampionshipTeam.getColoredName())
                    .replace("%points%", String.valueOf(leftTeamPoints));

            for (UUID uuid : leftChampionshipTeam.getMembers()) {
                playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
            }
        } else {
            rightTeamPoints += 15 * CCConfig.TEAM_MAX_MEMBERS;
            leftTeamPoints += 15 * CCConfig.TEAM_MAX_MEMBERS;

            message = MessageConfig.BATTLE_BOX_DRAW
                    .replace("%points%", String.valueOf(leftTeamPoints));

            for (UUID uuid : rightChampionshipTeam.getMembers()) {
                playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
            }
            for (UUID uuid : leftChampionshipTeam.getMembers()) {
                playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
            }
        }

        for (Map.Entry<UUID, Integer> playerPointsEntry : playerPoints.entrySet()) {
            plugin.getRankManager().addPlayerPoints(Bukkit.getOfflinePlayer(playerPointsEntry.getKey()), GameTypeEnum.BattleBox, getAreaName(), playerPointsEntry.getValue());
        }

        plugin.getRankManager().addTeamPoints(rightChampionshipTeam, leftChampionshipTeam, GameTypeEnum.BattleBox, getAreaName(), rightTeamPoints);
        plugin.getRankManager().addTeamPoints(leftChampionshipTeam, rightChampionshipTeam, GameTypeEnum.BattleBox, getAreaName(), leftTeamPoints);

        sendMessageToAllGamePlayers(message);
        sendActionBarToAllGamePlayers(message);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        Player killer = player.getKiller();
        if (playerChampionshipTeam != null && killer != null) {
            UUID uuid = killer.getUniqueId();
            playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
            event.setDeathMessage(null);

            String message = MessageConfig.BATTLE_BOX_KILL_PLAYER
                    .replace("%player_team%", playerChampionshipTeam.getColoredName())
                    .replace("%player%", player.getName())
                    .replace("%killer%", killer.getName());

            killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

            sendMessageToAllGamePlayers(message);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getSpectatorSpawnLocation());
                event.getEntity().setGameMode(GameMode.SPECTATOR);
            }
        }.runTask(plugin);

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }
        ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (playerChampionshipTeam != null) {
            String message = MessageConfig.BATTLE_BOX_PLAYER_LEAVE
                    .replace("%player_team%", playerChampionshipTeam.getColoredName())
                    .replace("%player%", player.getName());

            sendMessageToAllGamePlayers(message);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        player.teleport(getSpectatorSpawnLocation());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void giveItemToAllGamePlayers() {
        for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
            plugin.getGameManager().getBattleBoxManager().setWeaponKit(player);
        }
        for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
            plugin.getGameManager().getBattleBoxManager().setWeaponKit(player);
        }
    }

    private void summonPotions() {
        for (String stringLocation : battleBoxConfig.getPotionSpawnPoints()) {
            Location location = Utils.getLocation(stringLocation);
            World world = location.getWorld();
            ItemStack item = new ItemStack(Material.SPLASH_POTION);
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta != null) {
                potionMeta.setBasePotionType(PotionType.STRONG_HARMING);
                item.setItemMeta(potionMeta);
                Item itemDropped;
                if (world != null) {
                    itemDropped = world.dropItem(location, item);
                    itemDropped.setGlowing(true);
                }
            }
        }
    }

    private void cleanDroppedItems() {
        Vector pos1 = battleBoxConfig.getAreaPos1();
        Vector pos2 = battleBoxConfig.getAreaPos2();
        World world = battleBoxConfig.getRightSpawnPoint().getWorld();
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

    private HashMap<Material, Integer> countBlocksInRegion() {
        World world = battleBoxConfig.getLeftPreSpawnPoint().getWorld();
        Vector pos1 = battleBoxConfig.getWoolPos1();
        Vector pos2 = battleBoxConfig.getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        HashMap<Material, Integer> blockCount = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();

                    blockCount.put(material, blockCount.getOrDefault(material, 0) + 1);
                }
            }
        }

        return blockCount;
    }

    private void resetRegionBlocks() {
        World world = battleBoxConfig.getLeftPreSpawnPoint().getWorld();
        Vector pos1 = battleBoxConfig.getWoolPos1();
        Vector pos2 = battleBoxConfig.getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.BLACK_WOOL);
                    BlockState blockState = block.getState();
                    blockState.setType(Material.BLACK_WOOL);
                    blockState.update();
                }
            }
        }
    }

    public boolean notInArea(Location location) {
        return !location.toVector().isInAABB(battleBoxConfig.getAreaPos1(), battleBoxConfig.getAreaPos2());
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getBattleBoxConfig().getSpectatorSpawnPoint();
    }

    @Override
    public String getAreaName() {
        return battleBoxConfig.getAreaName();
    }
}
