package ink.ziip.championshipscore.api.game.advancementcc;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class AdvancementCCArea extends BaseSingleTeamArea {
    private final Set<String> advanceentSet = new ConcurrentSkipListSet<>();
    private final List<String> netherAdvancements = new LinkedList<>();
    private final Map<String, Long> advancementFinishTimes = new HashMap<>();
    private final Map<String, Integer> playerDeathTimes = new HashMap<>();
    @Getter
    private long timer;
    private int goal;
    private int task;
    private int challenge;
    @Getter
    private boolean allowTeleport;
    private BukkitTask startGameProgressTask;
    private final Set<String> completedPlayerCounts = new HashSet<>();

    public AdvancementCCArea(ChampionshipsCore plugin, AdvancementCCConfig advancementCCConfig) {
        super(plugin, GameTypeEnum.AdvancementCC, new AdvancementCCHandler(plugin), advancementCCConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setAdvancementCCArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);

        netherAdvancements.add("minecraft:nether/ride_strider");
        netherAdvancements.add("minecraft:nether/obtain_ancient_debris");
        netherAdvancements.add("minecraft:nether/explore_nether");
        netherAdvancements.add("minecraft:nether/find_fortress");
        netherAdvancements.add("minecraft:nether/charge_respawn_anchor");

        Location spawnLocation = getNether().getSpawnLocation();
        if (spawnLocation != null) {
            World world = spawnLocation.getWorld();
            if (world != null) {
                int x = spawnLocation.getBlockX();
                int y = spawnLocation.getBlockY();
                int z = spawnLocation.getBlockZ();
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        for (int k = -1; k <= 1; k++) {
                            world.getBlockAt(x + i, y + j, z + k).setType(Material.AIR);
                        }
                    }
                }
            }
        }

        getWorld().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        getNether().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        getEnd().setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
    }

    public synchronized void addCompletedPlayerCount(String playerName) {
        completedPlayerCounts.add(playerName);
    }

    @Override
    public void startGamePreparation() {
        allowTeleport = false;
        goal = 0;
        task = 0;
        challenge = 0;

        World world = getWorld();
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        World nether = Bukkit.getWorld(getWorldName() + "_nether");
        if (nether != null) {
            nether.setGameRule(GameRule.KEEP_INVENTORY, true);
        }
        World end = Bukkit.getWorld(getWorldName() + "_the_end");
        if (end != null) {
            end.setGameRule(GameRule.KEEP_INVENTORY, true);
        }

        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        teleportPlayersToSpawnLocation();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.ACC_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.ACC_START_PREPARATION_TITLE, MessageConfig.ACC_START_PREPARATION_SUBTITLE);

        revokeAllGamePlayersAdvancements();

        startGameProgress();
    }

    private void startGameProgress() {
        timer = 0;

        getWorld().setTime(0);

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        resetPlayerHealthFoodEffectLevelInventory();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.ACC_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.ACC_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.ACC_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.ACC_GAME_START_TITLE, MessageConfig.ACC_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
                changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
            }

            sendActionBarToAllGamePlayers(MessageConfig.ACC_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));
            sendActionBarToAllGameSpectators(MessageConfig.ACC_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (completedPlayerCounts.size() == gamePlayers.size()) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer++;
        }, 0, 20L);
    }

    private void teleportPlayersToSpawnLocation() {
        teleportAllPlayers(getNether().getSpawnLocation());
    }

    @Override
    public synchronized void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        //Add player death times, count ++
        playerDeathTimes.putIfAbsent(player.getName(), 0);
        int times = playerDeathTimes.get(player.getName());
        times++;
        playerDeathTimes.put(player.getName(), times);
    }

    public String isAdvancementCompleted(String advancementName) {
        if (advanceentSet.contains(advancementName))
            return "完成于" + String.valueOf(advancementFinishTimes.get(advancementName)) + "秒";
        return "未完成";
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {

    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {

    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        StringBuilder deathTimesMessage = new StringBuilder("§6=== 玩家死亡次数 ===\n");
        for (Map.Entry<String, Integer> entry : playerDeathTimes.entrySet()) {
            deathTimesMessage.append("§e").append(entry.getKey()).append(": §c").append(entry.getValue()).append("次\n");
        }
        sendMessageToAllGamePlayers(deathTimesMessage.toString());
        StringBuilder advancementTimesMessage = new StringBuilder("§6=== 成就完成时间 ===\n");
        for (Map.Entry<String, Long> entry : advancementFinishTimes.entrySet()) {
            advancementTimesMessage.append("§e").append(entry.getKey()).append(": §c").append(entry.getValue()).append("秒 \n");
        }
        sendMessageToAllGamePlayers(advancementTimesMessage.toString());

        // Send player game time to players
        long gameTime = timer;
        String gameTimeMessage = "§6=== 游戏时间 ===\n§e" +
                (gameTime / 3600) + "小时" +
                (gameTime % 3600 / 60) + "分" +
                (gameTime % 60) + "秒\n";
        sendMessageToAllGamePlayers(gameTimeMessage);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.ACC_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.ACC_GAME_END_TITLE, MessageConfig.ACC_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(getLobbyLocation());

        resetPlayerHealthFoodEffectLevelInventory();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        task = 0;
        goal = 0;
        challenge = 0;

        resetGame();
    }

    protected void calculatePoints() {
        sendMessageToAllGamePlayers(getPlayerPointsRank());
        sendMessageToAllGamePlayers(getTeamPointsRank());

        String message = MessageConfig.ACC_FINAL_COUNT_MESSAGE.replace("%task%", String.valueOf(task))
                .replace("%goal%", String.valueOf(goal))
                .replace("%challenge%", String.valueOf(challenge));

        sendMessageToAllGamePlayers(message);
        sendMessageToConsole(message);

        addPlayerPointsToDatabase();
    }

    @Override
    public void resetArea() {
        advanceentSet.clear();
        advancementFinishTimes.clear();
        playerDeathTimes.clear();
        allowTeleport = false;
        completedPlayerCounts.clear();
        startGameProgressTask = null;
    }

    protected synchronized void handlePlayerAdvancementDone(UUID uuid, Advancement advancement) {
        String name = advancement.getKey().toString();
        if (!netherAdvancements.contains(name)) {
            return;
        }

        if (!advanceentSet.contains(name)) {
            advanceentSet.add(name);
            advancementFinishTimes.put(name, timer);
            if (advanceentSet.size() == 5)
                allowTeleport = true;

            for (UUID gamePlayer : getGamePlayers()) {
                Player player = Bukkit.getPlayer(gamePlayer);
                if (player != null) {
                    AdvancementProgress advancementProgress = player.getAdvancementProgress(advancement);
                    for (String criteria : advancementProgress.getRemainingCriteria())
                        advancementProgress.awardCriteria(criteria);
                }
            }
        }
    }

    @Override
    public boolean notInArea(Location location) {
        World world = location.getWorld();
        if (world == null)
            return true;

        String name = world.getName();
        if (name.startsWith(getWorldName()))
            return false;

        if (name.startsWith(getWorldName() + "_nether"))
            return false;

        return !name.startsWith(getWorldName() + "_the_end");
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getNether().getSpawnLocation();
    }

    @Override
    public AdvancementCCConfig getGameConfig() {
        return (AdvancementCCConfig) gameConfig;
    }

    @Override
    public AdvancementCCHandler getGameHandler() {
        return (AdvancementCCHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return getGameConfig().getAreaName();
    }

    public World getWorld() {
        return Bukkit.getWorld(getWorldName());
    }

    public World getNether() {
        return Bukkit.getWorld(getWorldName() + "_nether");
    }

    public World getEnd() {
        return Bukkit.getWorld(getWorldName() + "_the_end");
    }
}
