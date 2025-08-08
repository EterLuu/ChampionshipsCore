package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameStatusEnum;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteManager extends BaseManager {
    private final Map<UUID, GameTypeEnum> playerVotes = new ConcurrentHashMap<>();
    private final BukkitScheduler scheduler;
    private final RankManager rankManager;
    private BukkitTask voteTask;
    private int timer;
    private boolean vote;

    public VoteManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();
        rankManager = championshipsCore.getRankManager();
        vote = false;
    }

    public void startVote() {
        if (vote)
            return;

        playerVotes.clear();

        vote = true;

        timer = 120;

        Utils.sendMessageToAllPlayers(MessageConfig.VOTE_START_VOTE);
        Utils.sendTitleToAllPlayers(MessageConfig.VOTE_START_VOTE_TITLE, MessageConfig.VOTE_START_VOTE_SUBTITLE);

        plugin.getGameApiClient().sendGlobalEvent(GameStatusEnum.VOTING, GameTypeEnum.Bingo, plugin.getRankManager().getRound());

        voteTask = scheduler.runTaskTimer(plugin, () -> {

            Utils.changeLevelForAllPlayers(timer);
            Utils.sendTitleToAllPlayers(MessageConfig.VOTE_START_VOTE_TITLE, MessageConfig.VOTE_START_VOTE_SUBTITLE);

            Map<GameTypeEnum, Integer> votes = new HashMap<>();
            for (GameTypeEnum gameTypeEnum : playerVotes.values()) {
                votes.put(gameTypeEnum, votes.getOrDefault(gameTypeEnum, 0) + 1);
            }

            plugin.getGameApiClient().sendVoteEvent(votes, timer);

            if (timer == 0) {
                Utils.changeLevelForAllPlayers(timer);
                endVote();
                if (voteTask != null)
                    voteTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    public void endVote() {
        if (!vote)
            return;
        vote = false;
        if (voteTask != null) {
            voteTask.cancel();
            Utils.changeLevelForAllPlayers(0);
        }

        Utils.sendMessageToAllPlayers(MessageConfig.VOTE_END_VOTE);

        Map<GameTypeEnum, Integer> votes = new HashMap<>();
        for (GameTypeEnum gameTypeEnum : playerVotes.values()) {
            votes.put(gameTypeEnum, votes.getOrDefault(gameTypeEnum, 0) + 1);
        }

        ArrayList<Map.Entry<GameTypeEnum, Integer>> list;
        list = new ArrayList<>(votes.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        plugin.getGameApiClient().sendVoteEvent(votes, 0);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.VOTE_VOTE_BOARD_BAR).append("\n");

        int i = 1;
        for (Map.Entry<GameTypeEnum, Integer> entry : list) {
            String row = MessageConfig.VOTE_VOTE_BOARD_ROW
                    .replace("%game_rank%", String.valueOf(i))
                    .replace("%game%", entry.getKey().toString())
                    .replace("%game_votes%", String.valueOf(entry.getValue()));

            stringBuilder.append(row).append("\n");
            i++;
        }

        Utils.sendMessageToAllPlayers(stringBuilder.toString());

        playerVotes.clear();
    }

    public GameTypeEnum getPlayerVote(Player player) {
        return playerVotes.getOrDefault(player.getUniqueId(), null);
    }

    public int getVoteNums(GameTypeEnum gameTypeEnum) {
        int i = 0;
        for (GameTypeEnum voted : playerVotes.values()) {
            if (gameTypeEnum == voted)
                i++;
        }
        return i;
    }

    public void vote(Player player, GameTypeEnum gameTypeEnum) {
        if (!vote) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_TIME);
            return;
        }

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_PLAYER);
            return;
        }

        if (rankManager.getGameOrder(gameTypeEnum) != -1) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_ALREADY_PLAYED);
            return;
        }

        if (gameTypeEnum == null) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
            return;
        }

        playerVotes.put(player.getUniqueId(), gameTypeEnum);
        player.sendMessage(MessageConfig.VOTE_PLAYER_VOTE.replace("%game%", gameTypeEnum.toString()));
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {
        endVote();
    }
}
