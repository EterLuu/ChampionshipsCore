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
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.advancement.AdvancementDisplayType;
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
    @Getter
    private int timer;
    private int goal;
    private int task;
    private int challenge;
    private BukkitTask startGameProgressTask;

    public AdvancementCCArea(ChampionshipsCore plugin, AdvancementCCConfig advancementCCConfig) {
        super(plugin, GameTypeEnum.AdvancementCC, new AdvancementCCHandler(plugin), advancementCCConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setAdvancementCCArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void startGamePreparation() {
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
        timer = getGameConfig().getTimer() + 5;

        getWorld().setTime(0);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
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

            if (timer == 0) {
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    private void teleportPlayersToSpawnLocation() {
        teleportAllPlayers(getWorld().getSpawnLocation());
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
        });
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

        calculatePoints();

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
        startGameProgressTask = null;
    }

    protected synchronized void handlePlayerAdvancementDone(UUID uuid, Advancement advancement) {
        String name = advancement.getKey().toString();

        if (!advanceentSet.contains(name)) {
            advanceentSet.add(name);

            AdvancementDisplay advancementDisplay = advancement.getDisplay();
            if (advancementDisplay != null) {
                if (advancementDisplay.getType() == AdvancementDisplayType.TASK) {
                    addPlayerPoints(uuid, 1);
                    task++;
                }
                if (advancementDisplay.getType() == AdvancementDisplayType.GOAL) {
                    addPlayerPoints(uuid, 3);
                    goal++;
                }
                if (advancementDisplay.getType() == AdvancementDisplayType.CHALLENGE) {
                    addPlayerPoints(uuid, 5);
                    challenge++;
                }

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
        return getWorld().getSpawnLocation();
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
}
