package ink.ziip.championshipscore.api.vote;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
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
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VoteManager extends BaseManager {
    private static final int VOTE_DURATION_SECONDS = 90;
    private final Map<UUID, GameTypeEnum> playerVotes = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> voteBars = new ConcurrentHashMap<>();
    private final BukkitScheduler scheduler;
    private final RankManager rankManager;
    private final VoteMenu voteMenu;
    private BukkitTask voteTask;
    private int timer;
    private boolean vote;

    public VoteManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        scheduler = championshipsCore.getServer().getScheduler();
        rankManager = championshipsCore.getRankManager();
        voteMenu = new VoteMenu(this);
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
        String time = String.format(Locale.ROOT, "%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60);
        double progress = Math.max(0D, Math.min(1D, timer / (double) VOTE_DURATION_SECONDS));
        int totalVotes = getTotalVoteCount();
        int eligibleVoters = getEligibleVoterCount();
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            onlinePlayers.add(uuid);

            BossBar voteBar = voteBars.computeIfAbsent(uuid,
                    ignored -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_12));
            voteBar.addPlayer(player);
            String selectedGame = Optional.ofNullable(getPlayerVote(uuid))
                    .map(GameTypeEnum::toString)
                    .orElse(MessageConfig.VOTE_NOT_VOTED);
            voteBar.setTitle(Utils.translateColorCodes(MessageConfig.VOTE_BOSS_BAR
                    .replace("%time%", time)
                    .replace("%votes%", String.valueOf(totalVotes))
                    .replace("%players%", String.valueOf(eligibleVoters))
                    .replace("%vote%", selectedGame)));
            voteBar.setProgress(progress);
        }

        voteBars.entrySet().removeIf(entry -> {
            if (onlinePlayers.contains(entry.getKey()))
                return false;
            entry.getValue().removeAll();
            return true;
        });
        voteMenu.refreshOpenMenus();
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
        voteMenu.closeAll();

        playerVotes.entrySet().removeIf(entry -> plugin.getTeamManager().getTeamByPlayer(entry.getKey()) == null
                || !canVoteFor(entry.getValue()));

        Map<GameTypeEnum, Integer> votes = new EnumMap<>(GameTypeEnum.class);
        for (GameTypeEnum gameTypeEnum : playerVotes.values()) {
            votes.put(gameTypeEnum, votes.getOrDefault(gameTypeEnum, 0) + 1);
        }

        ArrayList<Map.Entry<GameTypeEnum, Integer>> list;
        list = new ArrayList<>(votes.entrySet());
        list.sort(Comparator.<Map.Entry<GameTypeEnum, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(entry -> entry.getKey().name()));

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
            stringBuilder.append(MessageConfig.VOTE_NO_VALID_VOTES);
            Utils.sendTitleToAllPlayers(MessageConfig.VOTE_END_VOTE_TITLE, MessageConfig.VOTE_NO_VALID_VOTES, 40);
        } else {
            int highestVotes = list.getFirst().getValue();
            List<Map.Entry<GameTypeEnum, Integer>> tied = list.stream()
                    .filter(entry -> entry.getValue() == highestVotes)
                    .toList();
            Map.Entry<GameTypeEnum, Integer> winner = tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
            if (tied.size() > 1) {
                stringBuilder.append(MessageConfig.VOTE_TIED_WINNER
                        .replace("%game%", winner.getKey().toString())).append("\n");
            }
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
        return getPlayerVote(player.getUniqueId());
    }

    GameTypeEnum getPlayerVote(UUID uuid) {
        GameTypeEnum gameType = playerVotes.get(uuid);
        return isValidVote(uuid, gameType) ? gameType : null;
    }

    public int getVoteNums(GameTypeEnum gameTypeEnum) {
        int i = 0;
        for (Map.Entry<UUID, GameTypeEnum> entry : playerVotes.entrySet()) {
            if (gameTypeEnum == entry.getValue() && isValidVote(entry.getKey(), entry.getValue()))
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

        if (gameTypeEnum == null || FinaleGameRegistry.isRegistered(gameTypeEnum)
                || !plugin.getGameManager().isGameEnabled(gameTypeEnum)
                || !hasPublishedArea(gameTypeEnum)) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
            return;
        }

        if (rankManager.getGameOrder(gameTypeEnum) != -1) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_ALREADY_PLAYED);
            return;
        }

        playerVotes.put(player.getUniqueId(), gameTypeEnum);
        Utils.sendActionBar(player, MessageConfig.VOTE_PLAYER_VOTE.replace("%game%", gameTypeEnum.toString()));
        updateVoteBars();
    }

    public void openVoteMenu(Player player) {
        if (!vote) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_TIME);
            return;
        }
        if (plugin.getTeamManager().getTeamByPlayer(player) == null) {
            player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_PLAYER);
            return;
        }
        voteMenu.open(player);
    }

    int getRemainingSeconds() {
        return timer;
    }

    int getTotalVoteCount() {
        int votes = 0;
        for (Map.Entry<UUID, GameTypeEnum> entry : playerVotes.entrySet()) {
            if (isValidVote(entry.getKey(), entry.getValue())) votes++;
        }
        return votes;
    }

    int getEligibleVoterCount() {
        Set<UUID> players = new HashSet<>();
        for (ChampionshipTeam team : plugin.getTeamManager().getTeamList()) {
            players.addAll(team.getMembers());
        }
        return players.size();
    }

    public boolean canVoteFor(GameTypeEnum gameTypeEnum) {
        return gameTypeEnum != null
                && !FinaleGameRegistry.isRegistered(gameTypeEnum)
                && plugin.getGameManager().isGameEnabled(gameTypeEnum)
                && rankManager.getGameOrder(gameTypeEnum) == -1
                && hasPublishedArea(gameTypeEnum);
    }

    private boolean hasPublishedArea(GameTypeEnum gameTypeEnum) {
        var manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
        if (manager == null) return false;
        return manager.getAreaNameList().stream()
                .anyMatch(area -> plugin.getPrepareSessionManager().canStart(gameTypeEnum, area));
    }

    private boolean isValidVote(UUID uuid, GameTypeEnum gameTypeEnum) {
        return gameTypeEnum != null
                && plugin.getTeamManager().getTeamByPlayer(uuid) != null
                && canVoteFor(gameTypeEnum);
    }

    @Override
    public void load() {
        Bukkit.getPluginManager().registerEvents(voteMenu, plugin);
    }

    @Override
    public void unload() {
        vote = false;
        if (voteTask != null) {
            voteTask.cancel();
            voteTask = null;
        }
        removeVoteBars();
        voteMenu.closeAll();
        HandlerList.unregisterAll(voteMenu);
        playerVotes.clear();
    }
}
