package ink.ziip.championshipscore.api.schedule.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.List;

public class BattleBoxScheduleManager extends BaseManager {
    private final List<List<String>> battleBoxRounds = new ArrayList<>();
    private final BukkitScheduler scheduler;
    private final BattleBoxScheduleHandler handler;
    @Getter
    private int subRound;
    private int timer;
    @Getter
    private boolean enabled;
    private int completedAreaNum;

    public BattleBoxScheduleManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        handler = new BattleBoxScheduleHandler(championshipsCore, this);
        scheduler = championshipsCore.getServer().getScheduler();
        subRound = 0;
        completedAreaNum = 0;
    }

    @Override
    public void load() {
        ConfigurationSection configurationSection = CCConfig.BATTLE_BOX_ROUNDS;
        for (String key : configurationSection.getKeys(false)) {
            battleBoxRounds.add(new ArrayList<>(configurationSection.getStringList(key)));
        }
    }

    @Override
    public void unload() {

    }

    public void startBattleBox() {
        if (enabled)
            return;

        plugin.getScheduleManager().addRound(GameTypeEnum.BattleBox);
        enabled = true;
        timer = 10;
        subRound = 0;
        completedAreaNum = 0;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX));
            }

            if (timer == 5) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BATTLE_BOX_POINTS));
            }

            if (timer < 5 && timer > 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }
            if (timer == 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                subRound = 0;
                startBattleBoxRound();
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public void startBattleBoxRound() {
        if (!enabled)
            return;

        subRound++;
        if (subRound > battleBoxRounds.size()) {
            return;
        }

        handler.register();

        for (String command : battleBoxRounds.get(subRound - 1)) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    public void nextBattleBoxRound() {
        if (!enabled)
            return;

        completedAreaNum = 0;
        subRound++;
        if (subRound > battleBoxRounds.size()) {
            Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
            scheduler.runTaskAsynchronously(plugin, task -> Utils.sendMessageToAllPlayers(plugin.getRankManager().getGameTeamPoints(GameTypeEnum.BattleBox)));
            Utils.sendMessageToAllPlayers(plugin.getRankManager().getTeamRankString());
            handler.unRegister();
            return;
        }
        Utils.playSoundToAllPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1, 1F);

        timer = 10;
        scheduler.runTaskTimer(plugin, (task) -> {

            Utils.changeLevelForAllPlayers(timer);

            if (timer == 10) {
                Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.NEXT_ROUND_SOON));
            }

            if (timer < 5 && timer > 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }
            if (timer == 1) {
                Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(0);
                for (String command : battleBoxRounds.get(subRound - 1)) {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                task.cancel();
            }
            timer--;
        }, 0, 20L);
    }

    public synchronized void addCompletedAreaNum() {
        completedAreaNum++;

        if (completedAreaNum == plugin.getGameManager().getBattleBoxManager().getAreaNameList().size()) {
            nextBattleBoxRound();
        }
    }
}
