package ink.ziip.championshipscore.api.game.bingo.game;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.EventProgressTracker;
import ink.ziip.championshipscore.api.game.bingo.task.EventSubject;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
import ink.ziip.championshipscore.api.game.bingo.task.EventTrigger;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.PotionTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategories;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticCategory;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.task.TaskDisplayMode;
import ink.ziip.championshipscore.api.game.bingo.task.TaskGenerator;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

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
 * <p>CC runs a single fixed points mode: cells never lock (every team may claim each cell once,
 * independently). Win/timeout resolution is left to the caller (the {@code BingoArea}).
 */
public final class BingoRound {
    private final CardSize size;
    private final BingoVariantRules variant;
    private final boolean locksTasks;
    private final boolean chain;
    private final Map<ChampionshipTeam, int[]> parallaxPermutations;
    private final CardDisplayInfo displayInfo;
    private final List<GameTask> layout;
    private final BingoCard card;
    private final Map<ChampionshipTeam, BingoCard> differentialTeamCards;
    private final Map<UUID, BingoCard> differentialPlayerCards;
    private final Map<UUID, ChampionshipTeam> playerTeams = new HashMap<>();
    private final Map<Integer, java.util.LinkedHashMap<String, Long>> differentialClaims = new HashMap<>();
    private int lastDifferentialClaimRank;
    private final List<ChampionshipTeam> teams;
    private final Set<TaskData.TaskType> includedTypes;
    private final Set<String> extraExcludedTags;
    private final Map<String, Integer> extraTagCaps;

    /** The live card-map item per team, kept so it can be re-issued when a player loses theirs. */
    private final Map<ChampionshipTeam, ItemStack> teamMapItems = new HashMap<>();
    private final Map<UUID, ItemStack> playerMapItems = new HashMap<>();

    /** statistic baselines: player -> (statistic -> value at the moment tracking began). */
    private final Map<UUID, Map<StatisticHandle, Integer>> statBaselines = new HashMap<>();

    /** Per-round state for cumulative/distinct EventTask objectives. */
    private final EventProgressTracker eventTracker = new EventProgressTracker();

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
    /** Cell points earned by the completing player in the most recent completion (by claim rank). */
    private int lastCellDelta;
    /** Per-member line bonus triggered by the most recent completion; caller credits every team member. */
    private int lastLineDelta;

    /**
     * @param itemPoints points per claim rank (index 0 = first team to claim a cell), never null.
     */
    public BingoRound(CardSize size, long seed, Set<TaskData.TaskType> includedTypes,
                      Set<String> extraExcludedTags, Map<String, Integer> extraTagCaps,
                      List<ChampionshipTeam> teams, int[] itemPoints, int lineBonus,
                      int lineBonusMajorCount, int lineBonusMinor) {
        this(size, seed, includedTypes, extraExcludedTags, extraTagCaps, teams, itemPoints,
                lineBonus, lineBonusMajorCount, lineBonusMinor, BingoVariantRules.FIXED_POINTS);
    }

    public BingoRound(CardSize size, long seed, Set<TaskData.TaskType> includedTypes,
                      Set<String> extraExcludedTags, Map<String, Integer> extraTagCaps,
                      List<ChampionshipTeam> teams, int[] itemPoints, int lineBonus,
                      int lineBonusMajorCount, int lineBonusMinor, BingoVariantRules variant) {
        this.size = size;
        this.variant = variant == null ? BingoVariantRules.FIXED_POINTS : variant;
        this.locksTasks = this.variant.mode().locksCells();
        this.chain = this.variant.remix() == BingoRemix.CHAIN;
        this.includedTypes = Set.copyOf(includedTypes);
        this.extraExcludedTags = Set.copyOf(extraExcludedTags);
        this.extraTagCaps = Map.copyOf(extraTagCaps);
        this.itemPoints = itemPoints;
        this.lineBonus = lineBonus;
        this.lineBonusMajorCount = lineBonusMajorCount;
        this.lineBonusMinor = lineBonusMinor;
        this.displayInfo = new CardDisplayInfo(size,
                TaskDisplayMode.UNIQUE_TASK_ITEMS,
                TaskDisplayMode.UNIQUE_TASK_ITEMS,
                false,
                locksTasks);
        this.layout = this.variant.remix() == BingoRemix.SPEEDRUN
                ? speedrunLayout()
                : TaskGenerator.generateCardTasks(
                new TaskGenerator.GeneratorSettings(seed, includedTypes, size, extraExcludedTags, extraTagCaps,
                        this.variant.difficulty().tierWeights(),
                        this.variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                        this.variant.remix() == BingoRemix.COLORFUL,
                        this.variant.genesisItems().stream()
                                .map(name -> org.bukkit.Material.matchMaterial(name))
                                .filter(java.util.Objects::nonNull)
                                .map(ItemTask::new).map(TaskData.class::cast).toList()));
        List<ChampionshipTeam> playable = new ArrayList<>();
        for (ChampionshipTeam team : teams) {
            playable.add(team);
            scores.put(team, 0);
            awardedLines.put(team, 0);
        }
        this.teams = List.copyOf(playable);
        this.card = copyLayout();
        if (this.variant.remix() == BingoRemix.DIFFERENTIAL) {
            this.differentialTeamCards = new HashMap<>();
            for (int index = 0; index < this.teams.size(); index++) {
                ChampionshipTeam team = this.teams.get(index);
                this.differentialTeamCards.put(team, index == 0 ? card
                        : generatedCard(seed == 0L ? 0L : seed + (long) team.getId() * 1_000_003L));
            }
            this.differentialPlayerCards = new HashMap<>();
        } else {
            this.differentialTeamCards = Map.of();
            this.differentialPlayerCards = Map.of();
        }
        this.parallaxPermutations = this.variant.remix() == BingoRemix.PARALLAX
                ? createParallaxPermutations(seed) : Map.of();
        if (this.variant.remix() == BingoRemix.BLIND)
            this.card.getTasks().forEach(task -> task.setHidden(true));
    }

    private BingoCard copyLayout() {
        List<GameTask> copy = new ArrayList<>(layout.size());
        for (GameTask task : layout) {
            copy.add(task.copy());
        }
        return new BingoCard(size, copy);
    }

    private BingoCard generatedCard(long generatedSeed) {
        List<GameTask> tasks = TaskGenerator.generateCardTasks(new TaskGenerator.GeneratorSettings(
                generatedSeed, includedTypes, size, extraExcludedTags, extraTagCaps,
                variant.difficulty().tierWeights(), variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                variant.remix() == BingoRemix.COLORFUL, List.of()));
        return new BingoCard(size, tasks);
    }

    private BingoCard cardOf(ChampionshipTeam team) {
        return differentialTeamCards.getOrDefault(team, card);
    }

    private BingoCard contentCardOf(UUID playerId, ChampionshipTeam team) {
        return differentialPlayerCards.getOrDefault(playerId, cardOf(team));
    }

    public boolean isDifferential() { return variant.remix() == BingoRemix.DIFFERENTIAL; }

    private static List<GameTask> speedrunLayout() {
        TaskData[] cells = new TaskData[9];
        cells[4] = advancement("end/dragon_egg");
        List<TaskData> edges = new ArrayList<>(List.of(advancement("story/enter_the_end"),
                advancement("story/follow_ender_eye"), advancement("end/kill_dragon"),
                advancement("end/enter_end_gateway")));
        List<TaskData> corners = new ArrayList<>(List.of(advancement("story/enter_the_nether"),
                advancement("nether/obtain_blaze_rod"), advancement("nether/find_fortress"),
                advancement("end/dragon_breath")));
        java.util.Collections.shuffle(edges);
        java.util.Collections.shuffle(corners);
        int[] edgeIndexes = {1, 3, 5, 7};
        int[] cornerIndexes = {0, 2, 6, 8};
        for (int index = 0; index < 4; index++) {
            cells[edgeIndexes[index]] = edges.get(index);
            cells[cornerIndexes[index]] = corners.get(index);
        }
        return java.util.Arrays.stream(cells).map(GameTask::new).toList();
    }

    private static TaskData advancement(String path) {
        var advancement = org.bukkit.Bukkit.getAdvancement(org.bukkit.NamespacedKey.minecraft(path));
        var dimension = path.startsWith("nether/")
                ? ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension.NETHER
                : path.startsWith("end/")
                ? ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension.THE_END
                : ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension.OVERWORLD;
        return new AdvancementTask(advancement, dimension);
    }

    private Map<ChampionshipTeam, int[]> createParallaxPermutations(long seed) {
        Map<ChampionshipTeam, int[]> result = new HashMap<>();
        int cells = size.fullCardSize;
        for (ChampionshipTeam team : teams) {
            int[] order = new int[cells];
            for (int index = 0; index < cells; index++) order[index] = index;
            java.util.Random random = seed == 0L ? new java.util.Random()
                    : new java.util.Random(seed + team.getId() * 7919L);
            for (int index = cells - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                int value = order[index]; order[index] = order[swap]; order[swap] = value;
            }
            result.put(team, order);
        }
        return result;
    }

    public int[] parallaxDisplayOrder(ChampionshipTeam team) {
        int[] order = parallaxPermutations.get(team);
        return order == null ? null : order.clone();
    }

    public void revealParallax() {
        for (int[] order : parallaxPermutations.values())
            for (int index = 0; index < order.length; index++) order[index] = index;
    }

    private void settleParallax(ChampionshipTeam team, int trueIndex) {
        int[] order = parallaxPermutations.get(team);
        if (order == null) return;
        int displayedAt = -1;
        for (int index = 0; index < order.length; index++) {
            if (order[index] == trueIndex) { displayedAt = index; break; }
        }
        if (displayedAt < 0 || displayedAt == trueIndex) return;
        int displaced = order[trueIndex];
        order[trueIndex] = trueIndex;
        order[displayedAt] = displaced;
    }

    public CardSize size() {
        return size;
    }

    public BingoVariantRules variant() { return variant; }

    public CardDisplayInfo displayInfo() {
        return displayInfo;
    }

    /** The distinct task data on the card. */
    public List<GameTask> layout() {
        return layout;
    }

    public Optional<BingoCard> cardFor(ChampionshipTeam team) {
        return Optional.of(cardOf(team));
    }

    public Optional<BingoCard> cardForPlayer(UUID playerId, ChampionshipTeam team) {
        return Optional.of(contentCardOf(playerId, team));
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

    public void setPlayerMapItem(UUID playerId, ItemStack item) { playerMapItems.put(playerId, item); }

    public Optional<ItemStack> mapItem(UUID playerId, ChampionshipTeam team) {
        return Optional.ofNullable(playerMapItems.getOrDefault(playerId, teamMapItems.get(team)));
    }

    public int countCompletedLines(ChampionshipTeam team) {
        return cardOf(team).countCompletedLines(BingoTeamAdapter.id(team));
    }

    /** Grid indices of cells the team has completed on the shared card. */
    public int[] completedIndices(ChampionshipTeam team) {
        return cardOf(team).completedIndices(BingoTeamAdapter.id(team));
    }

    public void setOutcome(RoundOutcome outcome) {
        this.outcome = outcome;
    }

    public RoundOutcome outcome() {
        return outcome;
    }

    public int completedCount(ChampionshipTeam team) {
        return cardOf(team).getCompleteCount(BingoTeamAdapter.id(team));
    }

    /** Cells this team claimed before every other team: own completion time strictly earliest. */
    public int countFirstCompletions(ChampionshipTeam team) {
        String teamId = BingoTeamAdapter.id(team);
        if (isDifferential()) {
            int first = 0;
            for (var claims : differentialClaims.values())
                if (!claims.isEmpty() && claims.keySet().iterator().next().equals(teamId)) first++;
            return first;
        }
        List<String> rivals = teams.stream().map(BingoTeamAdapter::id)
                .filter(id -> !id.equals(teamId)).toList();
        int first = 0;
        for (GameTask task : cardOf(team).getTasks()) {
            long own = task.completedAt(teamId);
            if (own < 0L) continue;
            boolean beaten = false;
            for (String rival : rivals) {
                long other = task.completedAt(rival);
                if (other >= 0L && other <= own) {
                    beaten = true;
                    break;
                }
            }
            if (!beaten) first++;
        }
        return first;
    }

    /** The playable teams competing this round. */
    public List<ChampionshipTeam> teams() {
        return teams;
    }

    public EventProgressTracker eventTracker() {
        return eventTracker;
    }

    /** True once every cell on the board has been claimed by at least one team. */
    public boolean boardFullyClaimed() {
        if (isDifferential()) {
            for (int index = 0; index < size.fullCardSize; index++) {
                int cell = index;
                if (teams.stream().noneMatch(team -> cardOf(team).getTasks().get(cell).isCompleted())) return false;
            }
            return true;
        }
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
        for (GameTask task : cardOf(team).getTasks()) {
            if (task.isCompletedByTeam(teamId)) last = Math.max(last, task.completedAt(teamId));
        }
        return last < 0 ? Long.MAX_VALUE : last;
    }

    /** Current score for the team. */
    public int score(ChampionshipTeam team) {
        return scores.getOrDefault(team, 0);
    }

    /** Teams ranked by score descending, ties broken by earliest last-completion time. */
    public List<ChampionshipTeam> rankedTeams() {
        List<ChampionshipTeam> sorted = new ArrayList<>(teams);
        sorted.sort((a, b) -> {
            int diff = scores.getOrDefault(b, 0) - scores.getOrDefault(a, 0);
            if (diff != 0) return diff;
            return Long.compare(lastCompletionTime(a), lastCompletionTime(b));
        });
        return sorted;
    }

    /** Team with the highest score; ties broken by earliest last-completion time. Null if all zero. */
    public ChampionshipTeam resolveTopScore() {
        for (ChampionshipTeam team : rankedTeams()) {
            if (scores.getOrDefault(team, 0) > 0) return team;
        }
        return null;
    }

    /**
     * Awards points for completing {@code task} and for any newly-earned lines. Splits the delta into a
     * per-player cell portion ({@link #lastCellDelta}, by claim rank) and a per-member line bonus
     * ({@link #lastLineDelta}); the caller credits each accordingly - the completing player gets the cell
     * points, every team member gets the line bonus. The team-score tracker mirrors
     * {@code BaseGameInstance#getTeamPoints} (cell once + line bonus × team size) so winner resolution
     * stays consistent with the sum-of-members ranking.
     */
    private void awardPoints(ChampionshipTeam team, GameTask task) {
        lastCellDelta = 0;
        lastLineDelta = 0;

        if (!variant.mode().usesPoints() || variant.remix() == BingoRemix.COOP) {
            lastScoreDelta = 0;
            scores.put(team, completedCount(team));
            return;
        }

        int rank = isDifferential() ? lastDifferentialClaimRank
                : task.claimRank(BingoTeamAdapter.id(team));
        if (rank >= 0) {
            lastCellDelta = rank < itemPoints.length ? itemPoints[rank] : itemPoints[itemPoints.length - 1];
        }

        int totalLines = countCompletedLines(team);
        int prevLines = awardedLines.getOrDefault(team, 0);
        if (totalLines > prevLines) {
            for (int i = prevLines; i < totalLines; i++) {
                lastLineDelta += (i < lineBonusMajorCount) ? lineBonus : lineBonusMinor;
            }
            awardedLines.put(team, totalLines);
        }

        lastScoreDelta = lastCellDelta + lastLineDelta;
        int teamSize = Math.max(1, team.getMembers().size());
        scores.merge(team, lastCellDelta + lastLineDelta * teamSize, Integer::sum);
    }

    /** Points gained in the most recent completion (cell + one share of line bonus), for the broadcast. */
    public int lastScoreDelta() {
        return lastScoreDelta;
    }

    /** Cell points the completing player earned in the most recent completion (credited to that player). */
    public int lastCellDelta() {
        return lastCellDelta;
    }

    /** Per-member line bonus triggered by the most recent completion (credited to every team member). */
    public int lastLineDelta() {
        return lastLineDelta;
    }

    // ── round-start setup ────────────────────────────────────────────────────────────────────

    /** Revokes all advancements and snapshots statistic baselines for a round participant. */
    public void prepareParticipant(Player player, ChampionshipTeam team) {
        java.util.Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
        while (it.hasNext()) revokeAdvancement(player, it.next());

        if (isDifferential()) {
            differentialPlayerCards.computeIfAbsent(player.getUniqueId(), uuid -> {
                long playerSeed = variant == null ? 0L : (long) uuid.hashCode() * 1_000_003L;
                return generatedCard(playerSeed);
            });
            playerTeams.put(player.getUniqueId(), team);
            syncTeamStateToPlayer(player.getUniqueId(), team);
        }

        Map<StatisticHandle, Integer> baselines = statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        for (GameTask task : contentCardOf(player.getUniqueId(), team).getTasks()) {
            if (task.data.getType() == TaskData.TaskType.STATISTIC) {
                StatisticHandle h = ((StatisticTask) task.data).statistic();
                baselines.putIfAbsent(h, readStatistic(player, h));
            }
        }
    }

    /**
     * Snapshots statistic baselines for a participant <em>without</em> revoking advancements - used when
     * a player reconnects mid-round, where their earned advancements and card progress must be preserved.
     * Only baselines statistics that don't already have one, so a participant who was online at round
     * start (already prepared) is left untouched.
     */
    public void ensureStatBaselines(Player player) {
        Map<StatisticHandle, Integer> baselines = statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ChampionshipTeam team = playerTeams.get(player.getUniqueId());
        List<GameTask> tasks = team == null ? card.getTasks()
                : contentCardOf(player.getUniqueId(), team).getTasks();
        for (GameTask task : tasks) {
            if (task.data.getType() == TaskData.TaskType.STATISTIC) {
                StatisticHandle h = ((StatisticTask) task.data).statistic();
                baselines.putIfAbsent(h, readStatistic(player, h));
            }
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
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task)) continue;
            boolean match;
            int need;
            if (task.data instanceof ItemTask data) {
                match = data.itemType() == itemType;
                need = data.count();
            } else if (task.data instanceof OneOfTask set) {
                match = set.items().contains(itemType);
                need = set.count();
            } else if (task.data instanceof AllOfTask set) {
                match = set.items().contains(itemType) && hasAllMembers(player, set.items());
                need = 1;
            } else {
                continue;
            }
            if (match && heldAmount >= need) {
                if (completeTask(task, index, player, team, gameTime)) {
                    awardPoints(team, cardOf(team).getTasks().get(index));
                    return Optional.of(task);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @return the task just completed for this team by holding a potion of the given form+effect, if
     * any. {@code effect} is the base effect key (strong/long variants already collapsed by the caller).
     */
    public Optional<GameTask> tryCompletePotion(Player player, ChampionshipTeam team, org.bukkit.Material material,
                                                String effect, int heldAmount, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task)) continue;
            if (!(task.data instanceof PotionTask pt)) continue;
            if (pt.form().material == material && pt.effect().equals(effect) && heldAmount >= pt.count()) {
                if (completeTask(task, index, player, team, gameTime)) {
                    awardPoints(team, cardOf(team).getTasks().get(index));
                    return Optional.of(task);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<GameTask> tryCompleteAdvancement(Player player, ChampionshipTeam team, Advancement advancement, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || task.taskType() != TaskData.TaskType.ADVANCEMENT) continue;
            AdvancementTask data = (AdvancementTask) task.data;
            if (data.advancement() != null && data.advancement().key().equals(advancement.key())) {
                if (completeTask(task, index, player, team, gameTime)) {
                    awardPoints(team, cardOf(team).getTasks().get(index));
                    return Optional.of(task);
                }
            }
        }
        return Optional.empty();
    }

    /** Completes a count-one event task from a discrete Bukkit event. */
    public Optional<GameTask> tryCompleteEventSignal(Player player, ChampionshipTeam team,
                                                     String trigger, String param, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || !(task.data instanceof EventTask event)) continue;
            if (event.count() != 1) continue;
            if (!event.trigger().equalsIgnoreCase(trigger) || !event.param().equalsIgnoreCase(param)) continue;
            if (completeTask(task, index, player, team, gameTime)) {
                awardPoints(team, cardOf(team).getTasks().get(index));
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    /** Checks all statistic tasks for this player against current values vs baseline. */
    public List<GameTask> tryCompleteStatistics(Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> completed = new ArrayList<>();
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || task.taskType() != TaskData.TaskType.STATISTIC) continue;
            StatisticTask data = (StatisticTask) task.data;
            StatisticHandle h = data.statistic();
            int delta = readStatistic(player, h) - baseline(player.getUniqueId(), h);
            int target = statisticTarget(data);
            if (delta >= target && completeTask(task, index, player, team, gameTime)) {
                awardPoints(team, cardOf(team).getTasks().get(index));
                completed.add(task);
            }
        }
        return completed;
    }

    /** Checks every state/tracked EventTask for the player's team. */
    public List<GameTask> tryCompletePollableEvents(Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> completed = new ArrayList<>();
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || !(task.data instanceof EventTask event)) continue;
            if (!EventTrigger.isPollable(event.trigger()) || !pollableMet(player, event)) continue;
            if (completeTask(task, index, player, team, gameTime)) {
                awardPoints(team, cardOf(team).getTasks().get(index));
                completed.add(task);
            }
        }
        return completed;
    }

    private boolean canAttempt(ChampionshipTeam team, int index, GameTask task) {
        String teamId = BingoTeamAdapter.id(team);
        GameTask state = cardOf(team).getTasks().get(index);
        if (state.isCompletedByTeam(teamId) || state.isLocked() || task.isLocked()) return false;
        if (locksTasks && (state.isCompleted() || (!isDifferential() && task.isCompleted()))) return false;
        if (!chain) return true;
        List<GameTask> stateTasks = cardOf(team).getTasks();
        boolean any = stateTasks.stream().anyMatch(candidate -> candidate.isCompletedByTeam(teamId));
        if (!any) return true;
        int width = size.size;
        int x = index % width;
        int y = index / width;
        return (x > 0 && stateTasks.get(index - 1).isCompletedByTeam(teamId))
                || (x + 1 < width && stateTasks.get(index + 1).isCompletedByTeam(teamId))
                || (y > 0 && stateTasks.get(index - width).isCompletedByTeam(teamId))
                || (y + 1 < width && stateTasks.get(index + width).isCompletedByTeam(teamId));
    }

    private boolean completeTask(GameTask task, int index, Player player,
                                 ChampionshipTeam team, long gameTime) {
        GameTask.Completion completion = completion(player, team, gameTime);
        GameTask state = cardOf(team).getTasks().get(index);
        if (!state.complete(completion, false)) return false;
        if (isDifferential()) {
            java.util.LinkedHashMap<String, Long> claims = differentialClaims.computeIfAbsent(
                    index, ignored -> new java.util.LinkedHashMap<>());
            lastDifferentialClaimRank = claims.size();
            claims.putIfAbsent(BingoTeamAdapter.id(team), gameTime);
        }
        if (task != state) task.complete(completion, false);
        if (isDifferential()) {
            for (Map.Entry<UUID, BingoCard> entry : differentialPlayerCards.entrySet()) {
                if (team.equals(playerTeams.get(entry.getKey())))
                    entry.getValue().getTasks().get(index).complete(completion, false);
            }
            if (locksTasks) {
                for (ChampionshipTeam rival : teams) {
                    if (rival.equals(team)) continue;
                    cardOf(rival).getTasks().get(index).setLocked(true);
                }
                for (Map.Entry<UUID, BingoCard> entry : differentialPlayerCards.entrySet()) {
                    if (!team.equals(playerTeams.get(entry.getKey())))
                        entry.getValue().getTasks().get(index).setLocked(true);
                }
            }
        }
        settleParallax(team, index);
        if (variant.remix() == BingoRemix.COOP) {
            for (ChampionshipTeam collaborator : teams) {
                if (collaborator == team) continue;
                task.complete(new GameTask.Completion(player.getUniqueId(),
                        Utils.toComponent(Utils.formatPlayerName(player)), BingoTeamAdapter.color(collaborator),
                        BingoTeamAdapter.id(collaborator), gameTime), false);
                scores.put(collaborator, completedCount(collaborator));
            }
        }
        return true;
    }

    private void syncTeamStateToPlayer(UUID playerId, ChampionshipTeam team) {
        BingoCard playerCard = differentialPlayerCards.get(playerId);
        if (playerCard == null) return;
        List<GameTask> state = cardOf(team).getTasks();
        for (int index = 0; index < state.size(); index++) {
            GameTask source = state.get(index);
            GameTask target = playerCard.getTasks().get(index);
            for (GameTask.Completion completion : source.allCompletions()) target.complete(completion, false);
            if (source.isLocked()) target.setLocked(true);
        }
    }

    public boolean hasWon(ChampionshipTeam team) {
        if (variant.remix() == BingoRemix.COOP)
            return completedCount(team) == card.getTasks().size();
        BingoMode mode = variant.mode();
        if (mode.linesWin()) return countCompletedLines(team) >= variant.winLines();
        return mode.fullCardWins() && completedCount(team) == card.getTasks().size();
    }

    public boolean revealRandomHiddenTask() {
        List<GameTask> hidden = card.getTasks().stream()
                .filter(task -> task.isHidden() && !task.isCompleted()).toList();
        if (hidden.isEmpty()) return false;
        hidden.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(hidden.size())).setHidden(false);
        return true;
    }

    public boolean lockRandomTask() {
        List<GameTask> open = card.getTasks().stream()
                .filter(task -> !task.isLocked() && !task.isCompleted()).toList();
        if (open.isEmpty()) return false;
        open.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(open.size())).setLocked(true);
        return true;
    }

    public boolean allTasksUncompletable() {
        return card.getTasks().stream().allMatch(task -> task.isLocked() || task.isCompleted());
    }

    public void refreshUncompletedTasks() {
        List<GameTask> replacements = TaskGenerator.generateCardTasks(
                new TaskGenerator.GeneratorSettings(0L, includedTypes, size, extraExcludedTags, extraTagCaps,
                        variant.difficulty().tierWeights(), variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                        variant.remix() == BingoRemix.COLORFUL, List.of()));
        int replacement = 0;
        for (GameTask task : card.getTasks()) {
            if (task.isCompleted() || task.isLocked()) continue;
            task.data = replacements.get(replacement++ % replacements.size()).data;
        }
    }

    private boolean pollableMet(Player player, EventTask event) {
        return switch (event.trigger()) {
            case "wear" -> "CHAIN".equalsIgnoreCase(event.param())
                    ? wearsAny(player, event.param()) : wearsFull(player, event.param());
            case "wear_full_enchanted" -> wearsFullEnchanted(player);
            case "wear_dyed" -> event.count() >= 4
                    ? wearsFullDyedLeatherDistinct(player) : distinctDyedLeatherColors(player) >= event.count();
            case "wear_duration" -> wearDurationMet(player, event);
            case "effect" -> hasEffect(player, event.param());
            case "effect_at_once" -> player.getActivePotionEffects().size() >= event.count();
            case "reach_level" -> player.getLevel() >= parseIntOr(event.param(), Integer.MAX_VALUE);
            case "reach" -> atReach(player, event.param());
            case "hunger_empty" -> player.getFoodLevel() == 0;
            case "spy" -> spyOn(player, event.param());
            case "unique_collect" -> distinctMembersHeld(player, event.members()) >= event.count();
            case "all_collect" -> hasAllMembers(player, event.members());
            case "stack_of_64" -> hasStackOf64(player);
            case "fill_inventory_unique" -> fillInventoryUniqueMet(player);
            case "craft_unique" -> eventTracker.distinctCount(player, "craft_unique") >= event.count();
            case "eat_unique" -> eventTracker.distinctCount(player, "eat_unique") >= event.count();
            case "eat_all" -> eventTracker.distinctCount(player, "eat_all:" + event.param()) >= event.count();
            case "breed_unique" -> eventTracker.distinctCount(player, "breed_unique") >= event.count();
            case "leash_unique" -> distinctLeashedSpecies(player) >= event.count();
            case "compost_unique" -> eventTracker.distinctCount(player, "compost_unique") >= event.count();
            case "kill_family" -> eventTracker.count(player, "kill_family:" + event.param()) >= event.count();
            case "kill_unique" -> eventTracker.distinctCount(player, "kill_unique:" + event.param()) >= event.count();
            case "visit_biomes" -> visitBiomesMet(player, event);
            case "advancement_count" -> eventTracker.count(player, "advancement_count") >= event.count();
            case "spy_unique" -> spyUniqueMet(player, event.count());
            default -> false;
        };
    }

    private boolean wearDurationMet(Player player, EventTask event) {
        String bucket = "wear_duration:" + event.param();
        long elapsedMillis = eventTracker.observeElapsed(player, bucket,
                "CARVED_PUMPKIN".equalsIgnoreCase(event.param()) && wearingCarvedPumpkin(player));
        return elapsedMillis >= event.count() * 60_000L;
    }

    private static boolean wearingCarvedPumpkin(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.getType() == Material.CARVED_PUMPKIN;
    }

    private static int distinctDyedLeatherColors(Player player) {
        Set<Integer> colors = new java.util.HashSet<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getItemMeta() instanceof LeatherArmorMeta meta && meta.isDyed()) {
                colors.add(meta.getColor().asRGB());
            }
        }
        return colors.size();
    }

    private static boolean wearsFullDyedLeatherDistinct(Player player) {
        Set<Integer> colors = new java.util.HashSet<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !(piece.getItemMeta() instanceof LeatherArmorMeta meta) || !meta.isDyed()) {
                return false;
            }
            if (!colors.add(meta.getColor().asRGB())) return false;
        }
        return colors.size() == 4;
    }

    private static boolean fillInventoryUniqueMet(Player player) {
        Set<Material> distinct = new java.util.HashSet<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents.length == 0) return false;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir() || !distinct.add(stack.getType())) return false;
        }
        return true;
    }

    private static int distinctLeashedSpecies(Player player) {
        Set<EntityType> species = new java.util.HashSet<>();
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof LivingEntity living && living.isLeashed() && living.getLeashHolder() == player) {
                species.add(entity.getType());
            }
        }
        return species.size();
    }

    private boolean visitBiomesMet(Player player, EventTask event) {
        org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
        for (EventSubject subject : event.subjects()) {
            org.bukkit.block.Biome candidate = subject.biomeOrNull();
            if (candidate != null && candidate.getKey().equals(biome.getKey())) {
                eventTracker.recordDistinct(player, "visit_biomes:" + event.param(), biome.getKey().asString());
                break;
            }
        }
        return eventTracker.distinctCount(player, "visit_biomes:" + event.param()) >= event.count();
    }

    private static final Map<String, List<Material>> WEAR_FAMILIES = Map.of(
            "LEATHER", List.of(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
            "IRON", List.of(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
            "GOLDEN", List.of(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
            "DIAMOND", List.of(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
            "COPPER", List.of(Material.COPPER_HELMET, Material.COPPER_CHESTPLATE, Material.COPPER_LEGGINGS, Material.COPPER_BOOTS),
            "CHAIN", List.of(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS));

    private static boolean wearsAny(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(java.util.Locale.ROOT));
        if (pieces == null) return false;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && pieces.contains(item.getType())) return true;
        }
        return false;
    }

    private static boolean wearsFull(Player player, String family) {
        List<Material> pieces = WEAR_FAMILIES.get(family.toUpperCase(java.util.Locale.ROOT));
        if (pieces == null) return false;
        Set<Material> needed = new java.util.HashSet<>(pieces);
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !needed.remove(item.getType())) return false;
        }
        return needed.isEmpty();
    }

    private static boolean wearsFullEnchanted(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasEnchants()) return false;
        }
        return true;
    }

    private static boolean hasEffect(Player player, String effectName) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase(java.util.Locale.ROOT));
        return type != null && player.hasPotionEffect(type);
    }

    private static boolean atReach(Player player, String place) {
        World world = player.getWorld();
        Location location = player.getLocation();
        return switch (place.toUpperCase(java.util.Locale.ROOT)) {
            case "BEDROCK" -> location.getY() < world.getMinHeight() + 10.0
                    && location.clone().add(0, -1, 0).getBlock().getType() == Material.BEDROCK;
            case "HEIGHT_LIMIT" -> location.getY() >= world.getMaxHeight();
            case "NETHER_ROOF" -> world.getEnvironment() == World.Environment.NETHER && location.getY() >= 128.0;
            default -> false;
        };
    }

    private static int distinctMembersHeld(Player player, Set<Material> members) {
        if (members == null || members.isEmpty()) return 0;
        Set<Material> distinct = new java.util.HashSet<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && members.contains(item.getType())) distinct.add(item.getType());
        }
        return distinct.size();
    }

    private static boolean hasAllMembers(Player player, Set<Material> members) {
        if (members == null || members.isEmpty()) return false;
        Set<Material> have = new java.util.HashSet<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) have.add(item.getType());
        }
        return have.containsAll(members);
    }

    private static boolean hasStackOf64(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getAmount() >= 64) return true;
        }
        return false;
    }

    private static boolean spyOn(Player player, String entityType) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64.0,
                entity -> entity instanceof Mob && entity.getType().name().equalsIgnoreCase(entityType)
                        && player.hasLineOfSight(entity));
        return result != null && result.getHitEntity() != null;
    }

    private boolean spyUniqueMet(Player player, int target) {
        if (!usingSpyglass(player)) return false;
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 64.0,
                entity -> entity instanceof Mob && entity != player && player.hasLineOfSight(entity));
        if (result == null || result.getHitEntity() == null) return false;
        eventTracker.recordDistinct(player, "spy_unique", result.getHitEntity().getType().name());
        return eventTracker.distinctCount(player, "spy_unique") >= target;
    }

    private static boolean usingSpyglass(Player player) {
        return player.isHandRaised() && (player.getInventory().getItemInMainHand().getType() == Material.SPYGLASS
                || player.getInventory().getItemInOffHand().getType() == Material.SPYGLASS);
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int statisticTarget(StatisticTask data) {
        if (StatisticCategories.of(data.statistic().statisticType()) == StatisticCategory.TRAVEL) {
            return data.count() * 1000; // travel stats are in cm; count is shown as count*10 blocks
        }
        return data.count();
    }

    private GameTask.Completion completion(Player player, ChampionshipTeam team, long gameTime) {
        Component name = Utils.toComponent(Utils.formatPlayerName(player));
        return new GameTask.Completion(player.getUniqueId(), name,
                BingoTeamAdapter.color(team), BingoTeamAdapter.id(team), gameTime);
    }
}
