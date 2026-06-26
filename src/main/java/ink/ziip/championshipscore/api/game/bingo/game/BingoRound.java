package ink.ziip.championshipscore.api.game.bingo.game;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategories;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategory;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.task.TaskDisplayMode;
import ink.ziip.championshipscore.api.game.bingo.task.TaskGenerator;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import net.kyori.adventure.text.Component;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One active bingo round: a generated task layout shared by every team, each tracked on the shared
 * {@link BingoCard} via per-team completion state on each {@link GameTask}. Holds points scoring and
 * per-player statistic baselines.
 *
 * <p>Simplified port for CC's single fixed points mode: cells never lock (every team may claim each
 * cell once, independently), so the differential-card, domination-lock, blind and starter-kit machinery
 * from minebingo is gone. Win/timeout resolution is left to the caller (the {@code BingoArea}).
 */
public final class BingoRound {
    /** Points mode never locks cells: each team completes a cell once, independently. */
    private static final boolean LOCKS_TASKS = false;

    private final CardSize size;
    private final CardDisplayInfo displayInfo;
    private final List<GameTask> layout;
    private final BingoCard card;
    private final List<ChampionshipTeam> teams;

    /** The live card-map item per team, kept so it can be re-issued when a player loses theirs. */
    private final Map<ChampionshipTeam, ItemStack> teamMapItems = new HashMap<>();

    /** statistic baselines: player -> (statistic -> value at the moment tracking began). */
    private final Map<UUID, Map<StatisticHandle, Integer>> statBaselines = new HashMap<>();

    /** Set once when the round ends so the renderer can paint the win-state overlay; null while running. */
    private RoundOutcome outcome;

    /** Points scoring state. */
    private final int[] itemPoints;
    private final int lineBonus;
    private final int lineBonusMajorCount;
    private final int lineBonusMinor;
    private final Map<ChampionshipTeam, Integer> scores = new HashMap<>();
    private final Map<ChampionshipTeam, Integer> awardedLines = new HashMap<>();
    private int lastScoreDelta;

    /**
     * @param itemPoints points per claim rank (index 0 = first team to claim a cell), never null.
     */
    public BingoRound(CardSize size, long seed, Set<TaskData.TaskType> includedTypes,
                      Set<String> extraExcludedTags, Map<String, Integer> extraTagCaps,
                      List<ChampionshipTeam> teams, int[] itemPoints, int lineBonus,
                      int lineBonusMajorCount, int lineBonusMinor) {
        this.size = size;
        this.itemPoints = itemPoints;
        this.lineBonus = lineBonus;
        this.lineBonusMajorCount = lineBonusMajorCount;
        this.lineBonusMinor = lineBonusMinor;
        this.displayInfo = new CardDisplayInfo(size,
                TaskDisplayMode.UNIQUE_TASK_ITEMS,
                TaskDisplayMode.UNIQUE_TASK_ITEMS,
                false,
                LOCKS_TASKS);
        this.layout = TaskGenerator.generateCardTasks(
                new TaskGenerator.GeneratorSettings(seed, includedTypes, size, extraExcludedTags, extraTagCaps));
        List<ChampionshipTeam> playable = new ArrayList<>();
        for (ChampionshipTeam team : teams) {
            playable.add(team);
            scores.put(team, 0);
            awardedLines.put(team, 0);
        }
        this.teams = List.copyOf(playable);
        this.card = copyLayout();
    }

    private BingoCard copyLayout() {
        List<GameTask> copy = new ArrayList<>(layout.size());
        for (GameTask task : layout) {
            copy.add(task.copy());
        }
        return new BingoCard(size, copy);
    }

    public CardSize size() {
        return size;
    }

    public CardDisplayInfo displayInfo() {
        return displayInfo;
    }

    /** The distinct task data on the card. */
    public List<GameTask> layout() {
        return layout;
    }

    /** Every team shares one board. */
    public Optional<BingoCard> cardFor(ChampionshipTeam team) {
        return Optional.of(card);
    }

    public BingoCard card() {
        return card;
    }

    public void setMapItem(ChampionshipTeam team, ItemStack item) {
        teamMapItems.put(team, item);
    }

    public Optional<ItemStack> mapItem(ChampionshipTeam team) {
        return Optional.ofNullable(teamMapItems.get(team));
    }

    public int countCompletedLines(ChampionshipTeam team) {
        return card.countCompletedLines(BingoTeamAdapter.id(team));
    }

    /** Grid indices of cells the team has completed on the shared card. */
    public int[] completedIndices(ChampionshipTeam team) {
        return card.completedIndices(BingoTeamAdapter.id(team));
    }

    public void setOutcome(RoundOutcome outcome) {
        this.outcome = outcome;
    }

    public RoundOutcome outcome() {
        return outcome;
    }

    public int completedCount(ChampionshipTeam team) {
        return card.getCompleteCount(BingoTeamAdapter.id(team));
    }

    public int taskCount() {
        return layout.size();
    }

    /** The playable teams competing this round. */
    public List<ChampionshipTeam> teams() {
        return teams;
    }

    /** True once every cell on the board has been claimed by at least one team. */
    public boolean boardFullyClaimed() {
        for (GameTask task : card.getTasks()) {
            if (!task.isCompleted()) return false;
        }
        return true;
    }

    /**
     * Game-time (seconds) at which the team reached its current completed count. Used to break "same
     * completed count" ties: earlier wins. A team with no completions returns {@link Long#MAX_VALUE}.
     */
    public long lastCompletionTime(ChampionshipTeam team) {
        String teamId = BingoTeamAdapter.id(team);
        long last = -1L;
        for (GameTask task : card.getTasks()) {
            if (task.isCompletedByTeam(teamId)) last = Math.max(last, task.completedAt(teamId));
        }
        return last < 0 ? Long.MAX_VALUE : last;
    }

    /** Current score for the team. */
    public int score(ChampionshipTeam team) {
        return scores.getOrDefault(team, 0);
    }

    /** Points gained in the most recent completion (for the broadcast). */
    public int lastScoreDelta() {
        return lastScoreDelta;
    }

    /** Team with the highest score; ties broken by earliest last-completion time. Null if all zero. */
    public ChampionshipTeam resolveTopScore() {
        ChampionshipTeam best = null;
        int bestScore = -1;
        long bestTime = Long.MAX_VALUE;
        for (ChampionshipTeam team : teams) {
            int s = scores.getOrDefault(team, 0);
            long time = lastCompletionTime(team);
            if (s > bestScore || (s == bestScore && time < bestTime)) {
                best = team;
                bestScore = s;
                bestTime = time;
            }
        }
        return bestScore <= 0 ? null : best;
    }

    /** Awards points for completing {@code task} and for any newly-earned lines. Sets {@link #lastScoreDelta}. */
    private void awardPoints(ChampionshipTeam team, GameTask task) {
        lastScoreDelta = 0;

        int rank = task.claimRank(BingoTeamAdapter.id(team));
        if (rank >= 0) {
            lastScoreDelta += rank < itemPoints.length ? itemPoints[rank] : itemPoints[itemPoints.length - 1];
        }

        int totalLines = countCompletedLines(team);
        int prevLines = awardedLines.getOrDefault(team, 0);
        if (totalLines > prevLines) {
            for (int i = prevLines; i < totalLines; i++) {
                lastScoreDelta += (i < lineBonusMajorCount) ? lineBonus : lineBonusMinor;
            }
            awardedLines.put(team, totalLines);
        }

        scores.merge(team, lastScoreDelta, Integer::sum);
    }

    // ── round-start setup ────────────────────────────────────────────────────────────────────

    /** Revokes all advancements and snapshots statistic baselines for a round participant. */
    public void prepareParticipant(Player player, ChampionshipTeam team) {
        java.util.Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
        while (it.hasNext()) revokeAdvancement(player, it.next());

        Map<StatisticHandle, Integer> baselines = statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        for (GameTask task : card.getTasks()) {
            if (task.data.getType() == TaskData.TaskType.STATISTIC) {
                StatisticHandle h = ((StatisticTask) task.data).statistic();
                baselines.putIfAbsent(h, readStatistic(player, h));
            }
        }
    }

    /** Resets to zero the typed BLOCK/ITEM/ENTITY sub-statistics tracked by the card. */
    public void resetCardStatistics(Player player, ChampionshipTeam team) {
        for (GameTask task : card.getTasks()) {
            if (task.data.getType() != TaskData.TaskType.STATISTIC) continue;
            StatisticHandle h = ((StatisticTask) task.data).statistic();
            try {
                if (h.hasMaterial()) {
                    player.setStatistic(h.statisticType(), h.itemType(), 0);
                    org.bukkit.Material variant = oreVariant(h.itemType());
                    if (variant != null) player.setStatistic(h.statisticType(), variant, 0);
                } else if (h.hasEntity()) {
                    player.setStatistic(h.statisticType(), h.entityType(), 0);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void revokeAdvancement(Player player, Advancement advancement) {
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(criterion);
        }
    }

    public static int readStatistic(Player player, StatisticHandle h) {
        try {
            if (h.hasMaterial()) {
                int base = player.getStatistic(h.statisticType(), h.itemType());
                if (h.statisticType() == org.bukkit.Statistic.MINE_BLOCK) {
                    org.bukkit.Material variant = oreVariant(h.itemType());
                    if (variant != null) {
                        try { base += player.getStatistic(h.statisticType(), variant); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
                return base;
            }
            if (h.hasEntity()) return player.getStatistic(h.statisticType(), h.entityType());
            return player.getStatistic(h.statisticType());
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    /** Deepslate↔shallow ore counterpart, or null. */
    private static org.bukkit.Material oreVariant(org.bukkit.Material mat) {
        return switch (mat) {
            case COAL_ORE -> org.bukkit.Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE -> org.bukkit.Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE -> org.bukkit.Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE -> org.bukkit.Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE -> org.bukkit.Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE -> org.bukkit.Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> org.bukkit.Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE -> org.bukkit.Material.DEEPSLATE_EMERALD_ORE;
            case DEEPSLATE_COAL_ORE -> org.bukkit.Material.COAL_ORE;
            case DEEPSLATE_IRON_ORE -> org.bukkit.Material.IRON_ORE;
            case DEEPSLATE_COPPER_ORE -> org.bukkit.Material.COPPER_ORE;
            case DEEPSLATE_GOLD_ORE -> org.bukkit.Material.GOLD_ORE;
            case DEEPSLATE_REDSTONE_ORE -> org.bukkit.Material.REDSTONE_ORE;
            case DEEPSLATE_LAPIS_ORE -> org.bukkit.Material.LAPIS_ORE;
            case DEEPSLATE_DIAMOND_ORE -> org.bukkit.Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> org.bukkit.Material.EMERALD_ORE;
            default -> null;
        };
    }

    private int baseline(UUID playerId, StatisticHandle h) {
        Map<StatisticHandle, Integer> map = statBaselines.get(playerId);
        return map == null ? 0 : map.getOrDefault(h, 0);
    }

    // ── completion attempts ────────────────────────────────────────────────────────────────

    /** @return the task just completed for this team by collecting this item, if any. */
    public Optional<GameTask> tryCompleteItem(Player player, ChampionshipTeam team, org.bukkit.Material itemType, int heldAmount, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> tasks = card.getTasks();
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId) || task.isVoided()) continue;
            boolean match;
            int need;
            if (task.data instanceof ItemTask data) {
                match = data.itemType() == itemType;
                need = data.count();
            } else if (task.data instanceof OneOfTask set) {
                match = set.items().contains(itemType);
                need = set.count();
            } else {
                continue;
            }
            if (match && heldAmount >= need) {
                if (task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                    awardPoints(team, task);
                    return Optional.of(task);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<GameTask> tryCompleteAdvancement(Player player, ChampionshipTeam team, Advancement advancement, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> tasks = card.getTasks();
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId) || task.isVoided() || task.taskType() != TaskData.TaskType.ADVANCEMENT) continue;
            AdvancementTask data = (AdvancementTask) task.data;
            if (data.advancement() != null && data.advancement().key().equals(advancement.key())) {
                if (task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                    awardPoints(team, task);
                    return Optional.of(task);
                }
            }
        }
        return Optional.empty();
    }

    /** Checks all statistic tasks for this player against current values vs baseline. */
    public List<GameTask> tryCompleteStatistics(Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> completed = new ArrayList<>();
        List<GameTask> tasks = card.getTasks();
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId) || task.isVoided() || task.taskType() != TaskData.TaskType.STATISTIC) continue;
            StatisticTask data = (StatisticTask) task.data;
            StatisticHandle h = data.statistic();
            int delta = readStatistic(player, h) - baseline(player.getUniqueId(), h);
            int target = statisticTarget(data);
            if (delta >= target && task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                awardPoints(team, task);
                completed.add(task);
            }
        }
        return completed;
    }

    private static int statisticTarget(StatisticTask data) {
        if (StatisticCategories.of(data.statistic().statisticType()) == StatisticCategory.TRAVEL) {
            return data.count() * 1000; // travel stats are in cm; count is shown as count*10 blocks
        }
        return data.count();
    }

    private GameTask.Completion completion(Player player, ChampionshipTeam team, long gameTime) {
        Component name = Component.text(player.getName());
        return new GameTask.Completion(player.getUniqueId(), name,
                BingoTeamAdapter.color(team), BingoTeamAdapter.id(team), gameTime);
    }
}
