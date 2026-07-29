package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteManager extends BaseManager {
    private static final int VOTE_DURATION_SECONDS = 120;
    private final Map<UUID, GameTypeEnum> playerVotes = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> voteBars = new ConcurrentHashMap<>();
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

        timer = VOTE_DURATION_SECONDS;

        Utils.sendMessageToAllPlayers(MessageConfig.VOTE_START_VOTE);
        Utils.sendTitleToAllPlayers(MessageConfig.VOTE_START_VOTE_TITLE, MessageConfig.VOTE_START_VOTE_SUBTITLE);
        Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.2F);

        updateVoteBars();

        voteTask = scheduler.runTaskTimer(plugin, () -> {
            updateVoteBars();
            if (timer <= 0) {
                endVote();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    private void updateVoteBars() {
        int eligiblePlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(player) != null)
                eligiblePlayers++;
        }

        String time = String.format(Locale.ROOT, "%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60);
        double progress = Math.max(0D, Math.min(1D, timer / (double) VOTE_DURATION_SECONDS));
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            onlinePlayers.add(uuid);

            BossBar voteBar = voteBars.computeIfAbsent(uuid,
                    ignored -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_12));
            voteBar.addPlayer(player);
            String selectedGame = Optional.ofNullable(playerVotes.get(uuid))
                    .map(GameTypeEnum::toString)
                    .orElse(MessageConfig.VOTE_NOT_VOTED);
            voteBar.setTitle(Utils.translateColorCodes(MessageConfig.VOTE_BOSS_BAR
                    .replace("%time%", time)
                    .replace("%votes%", String.valueOf(playerVotes.size()))
                    .replace("%players%", String.valueOf(eligiblePlayers))
                    .replace("%vote%", selectedGame)));
            voteBar.setProgress(progress);
        }

        voteBars.entrySet().removeIf(entry -> {
            if (onlinePlayers.contains(entry.getKey()))
                return false;
            entry.getValue().removeAll();
            return true;
        });
    }

    private void removeVoteBars() {
        voteBars.values().forEach(BossBar::removeAll);
        voteBars.clear();
    }

    public void endVote() {
        if (!vote)
            return;
        vote = false;
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }
        removeVoteBars();

        Map<GameTypeEnum, Integer> votes = new HashMap<>();
        for (GameTypeEnum gameTypeEnum : playerVotes.values()) {
            votes.put(gameTypeEnum, votes.getOrDefault(gameTypeEnum, 0) + 1);
        }

        ArrayList<Map.Entry<GameTypeEnum, Integer>> list;
        list = new ArrayList<>(votes.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(MessageConfig.VOTE_END_VOTE).append("\n");

        int i = 1;
        for (Map.Entry<GameTypeEnum, Integer> entry : list) {
            if (i > 3)
                break;
            String row = MessageConfig.VOTE_VOTE_BOARD_ROW
                    .replace("%game_rank%", String.valueOf(i))
                    .replace("%game%", entry.getKey().toString())
                    .replace("%game_votes%", String.valueOf(entry.getValue()));

            stringBuilder.append(row).append("\n");
            i++;
        }

        if (list.isEmpty()) {
            stringBuilder.append("#ededed  本轮没有有效投票");
            Utils.sendTitleToAllPlayers(MessageConfig.VOTE_END_VOTE_TITLE, "#ededed本轮没有有效投票", 40);
        } else {
            Map.Entry<GameTypeEnum, Integer> winner = list.get(0);
            Utils.sendTitleToAllPlayers(MessageConfig.VOTE_END_VOTE_TITLE,
                    MessageConfig.VOTE_END_VOTE_SUBTITLE
                            .replace("%game%", winner.getKey().toString())
                            .replace("%votes%", String.valueOf(winner.getValue())), 60);
        }

        Utils.sendMessageToAllPlayers(Utils.translateColorCodes(stringBuilder.toString()));
        Utils.playSoundToAllPlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1F);

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

        if (gameTypeEnum == null || !plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
            return;
        }

        playerVotes.put(player.getUniqueId(), gameTypeEnum);
        Utils.sendActionBar(player, MessageConfig.VOTE_PLAYER_VOTE.replace("%game%", gameTypeEnum.toString()));
        updateVoteBars();
    }

    @Override
    public void load() {

    }

    @Override
    public void unload() {
        vote = false;
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }
        removeVoteBars();
        playerVotes.clear();
    }
}
