package ink.ziip.championshipscore.api.game.bingo.game;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.config.BingoCardMenuConfig;
import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.task.EventProgressTracker;
import ink.ziip.championshipscore.api.game.bingo.task.EventSubject;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
import ink.ziip.championshipscore.api.game.bingo.task.EventTrigger;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
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
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.bingo.engine.PlayerAward;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoEventObjectiveEvaluator;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoEventObjectiveRule;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoRidingTravel;
import ink.ziip.championshipscore.platform.bukkit.gui.BingoTaskItems;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.TeamSnapshot;

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
import java.util.function.Function;

/**
 * One active bingo round: a generated task layout shared by every team, each tracked on the shared
 * {@link BingoCard} via per-team completion records on each {@link GameTask}. The shared scoring
 * engine owns accepted claims, totals and win detection; card records project those decisions for
 * rendering. Participant membership and individual awards are frozen for the round.
 *
 * <p>Variant rules control cell locking, chain adjacency, cooperative claims and differential
 * layouts. Win/timeout resolution belongs to the arena coordinator.
 */
public final class BingoRound {
    private final CardSize size;
    private final BingoVariantRules variant;
    private final boolean locksTasks;
    private final Map<ChampionshipTeam, int[]> parallaxPermutations;
    private final CardDisplayInfo displayInfo;
    private final List<GameTask> layout;
    private final BingoCard card;
    private final Map<ChampionshipTeam, BingoCard> differentialTeamCards;
    private final Map<UUID, BingoCard> differentialPlayerCards;
    private final Map<UUID, ChampionshipTeam> playerTeams = new HashMap<>();
    private final UUID scoringId = UUID.randomUUID();
    private final Map<ChampionshipTeam, Integer> scoringTeamIds = new HashMap<>();
    private final BingoScoringEngine scoring;
    private long completionSequence;
    private final Function<TaskGenerator.GeneratorSettings, List<GameTask>> taskGenerator;
    private final List<ChampionshipTeam> teams;
    private final Set<TaskData.TaskType> includedTypes;
    private final Set<String> extraExcludedTags;
    private final Map<String, Integer> extraTagCaps;

    /** The live card-map item per team, kept so it can be re-issued when a player loses theirs. */
    private final Map<ChampionshipTeam, ItemStack> teamMapItems = new HashMap<>();

    private final Map<UUID, ItemStack> playerMapItems = new HashMap<>();

    /** statistic baselines: player -> (statistic -> value at the moment tracking began). */
    private final Map<UUID, Map<StatisticHandle, Integer>> statBaselines = new HashMap<>();

    /** Shared riding compensation for stale vanilla statistics. */
    private final BingoRidingTravel ridingTravel = new BingoRidingTravel();

    /** Per-round state for cumulative/distinct EventTask objectives. */
    private final EventProgressTracker eventTracker = new EventProgressTracker();

    /**
     * Set once when the round ends so the renderer can paint the win-state overlay; null while
     * running.
     */
    private RoundOutcome outcome;

    /** One accepted completion with frozen individual awards from the shared scoring engine. */
    public record CompletionResult(
            GameTask task,
            ChampionshipTeam team,
            int cellDelta,
            int lineDelta,
            List<PlayerAward> awards) {
        public CompletionResult {
            awards = List.copyOf(awards);
        }
    }

    /**
     * @param itemPoints points per claim rank (index 0 = first team to claim a cell), never null.
     */
    public BingoRound(
            CardSize size,
            long seed,
            Set<TaskData.TaskType> includedTypes,
            Set<String> extraExcludedTags,
            Map<String, Integer> extraTagCaps,
            List<ChampionshipTeam> teams,
            int[] itemPoints,
            int lineBonus,
            int lineBonusMajorCount,
            int lineBonusMinor) {
        this(
                size,
                seed,
                includedTypes,
                extraExcludedTags,
                extraTagCaps,
                teams,
                itemPoints,
                lineBonus,
                lineBonusMajorCount,
                lineBonusMinor,
                BingoVariantRules.FIXED_POINTS);
    }

    public BingoRound(
            CardSize size,
            long seed,
            Set<TaskData.TaskType> includedTypes,
            Set<String> extraExcludedTags,
            Map<String, Integer> extraTagCaps,
            List<ChampionshipTeam> teams,
            int[] itemPoints,
            int lineBonus,
            int lineBonusMajorCount,
            int lineBonusMinor,
            BingoVariantRules variant) {
        this(
                size,
                seed,
                includedTypes,
                extraExcludedTags,
                extraTagCaps,
                teams,
                itemPoints,
                lineBonus,
                lineBonusMajorCount,
                lineBonusMinor,
                variant,
                TaskGenerator::generateCardTasks);
    }

    BingoRound(
            CardSize size,
            long seed,
            Set<TaskData.TaskType> includedTypes,
            Set<String> extraExcludedTags,
            Map<String, Integer> extraTagCaps,
            List<ChampionshipTeam> teams,
            int[] itemPoints,
            int lineBonus,
            int lineBonusMajorCount,
            int lineBonusMinor,
            BingoVariantRules variant,
            Function<TaskGenerator.GeneratorSettings, List<GameTask>> taskGenerator) {
        this.taskGenerator = java.util.Objects.requireNonNull(taskGenerator);
        this.size = size;
        this.variant = variant == null ? BingoVariantRules.FIXED_POINTS : variant;
        this.locksTasks = this.variant.mode().locksCells();
        this.includedTypes = Set.copyOf(includedTypes);
        this.extraExcludedTags = Set.copyOf(extraExcludedTags);
        this.extraTagCaps = Map.copyOf(extraTagCaps);
        this.displayInfo =
                new CardDisplayInfo(
                        size,
                        TaskDisplayMode.UNIQUE_TASK_ITEMS,
                        TaskDisplayMode.UNIQUE_TASK_ITEMS,
                        false,
                        locksTasks);
        this.layout =
                this.variant.remix() == BingoRemix.SPEEDRUN
                        ? speedrunLayout()
                        : taskGenerator.apply(
                                new TaskGenerator.GeneratorSettings(
                                        seed,
                                        includedTypes,
                                        size,
                                        extraExcludedTags,
                                        extraTagCaps,
                                        this.variant.difficulty().tierWeights(),
                                        this.variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                                        this.variant.remix() == BingoRemix.COLORFUL,
                                        this.variant.genesisItems().stream()
                                                .map(
                                                        name ->
                                                                org.bukkit.Material.matchMaterial(
                                                                        name))
                                                .filter(java.util.Objects::nonNull)
                                                .map(ItemTask::new)
                                                .map(TaskData.class::cast)
                                                .toList()));
        if (layout.size() != size.fullCardSize)
            throw new IllegalArgumentException("Generated layout does not match the card size");
        this.teams = List.copyOf(teams);
        var snapshots = new ArrayList<TeamSnapshot>();
        var participants = new HashMap<UUID, Integer>();
        for (int id = 0; id < this.teams.size(); id++) {
            ChampionshipTeam team = this.teams.get(id);
            if (scoringTeamIds.putIfAbsent(team, id) != null)
                throw new IllegalArgumentException("Duplicate round team: " + team.getName());
            // Local DAILY team ids may be negative; scoring uses this round's stable ordinal.
            snapshots.add(
                    new TeamSnapshot(
                            id,
                            team.getName(),
                            team.getColorName(),
                            BingoTeamAdapter.color(team).asHexString(),
                            List.copyOf(team.getMembers())));
            for (UUID member : team.getMembers())
                if (participants.putIfAbsent(member, id) != null)
                    throw new IllegalArgumentException(
                            "Player belongs to multiple round teams: " + member);
        }
        scoring =
                new BingoScoringEngine(
                        scoringId,
                        1,
                        new BingoScoringRules(
                                size.size,
                                java.util.Arrays.stream(itemPoints).boxed().toList(),
                                lineBonus,
                                lineBonusMajorCount,
                                lineBonusMinor,
                                this.variant),
                        snapshots,
                        participants);
        this.card = copyLayout();
        if (this.variant.remix() == BingoRemix.DIFFERENTIAL) {
            this.differentialTeamCards = new HashMap<>();
            for (int index = 0; index < this.teams.size(); index++) {
                ChampionshipTeam team = this.teams.get(index);
                this.differentialTeamCards.put(
                        team,
                        index == 0
                                ? card
                                : generatedCard(
                                        seed == 0L ? 0L : seed + (long) team.getId() * 1_000_003L));
            }
            this.differentialPlayerCards = new HashMap<>();
        } else {
            this.differentialTeamCards = Map.of();
            this.differentialPlayerCards = Map.of();
        }
        this.parallaxPermutations =
                this.variant.remix() == BingoRemix.PARALLAX
                        ? createParallaxPermutations(seed)
                        : Map.of();
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
        List<GameTask> tasks =
                taskGenerator.apply(
                        new TaskGenerator.GeneratorSettings(
                                generatedSeed,
                                includedTypes,
                                size,
                                extraExcludedTags,
                                extraTagCaps,
                                variant.difficulty().tierWeights(),
                                variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                                variant.remix() == BingoRemix.COLORFUL,
                                List.of()));
        return new BingoCard(size, tasks);
    }

    private BingoCard cardOf(ChampionshipTeam team) {
        return differentialTeamCards.getOrDefault(team, card);
    }

    private BingoCard contentCardOf(UUID playerId, ChampionshipTeam team) {
        return differentialPlayerCards.getOrDefault(playerId, cardOf(team));
    }

    public boolean isDifferential() {
        return variant.remix() == BingoRemix.DIFFERENTIAL;
    }

    private static List<GameTask> speedrunLayout() {
        TaskData[] cells = new TaskData[9];
        cells[4] = advancement("end/dragon_egg");
        List<TaskData> edges =
                new ArrayList<>(
                        List.of(
                                advancement("story/enter_the_end"),
                                advancement("story/follow_ender_eye"),
                                advancement("end/kill_dragon"),
                                advancement("end/enter_end_gateway")));
        List<TaskData> corners =
                new ArrayList<>(
                        List.of(
                                advancement("story/enter_the_nether"),
                                advancement("nether/obtain_blaze_rod"),
                                advancement("nether/find_fortress"),
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
        var advancement =
                org.bukkit.Bukkit.getAdvancement(org.bukkit.NamespacedKey.minecraft(path));
        var dimension =
                path.startsWith("nether/")
                        ? ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension.NETHER
                        : path.startsWith("end/")
                                ? ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension
                                        .THE_END
                                : ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension
                                        .OVERWORLD;
        return new AdvancementTask(advancement, dimension);
    }

    private Map<ChampionshipTeam, int[]> createParallaxPermutations(long seed) {
        Map<ChampionshipTeam, int[]> result = new HashMap<>();
        int cells = size.fullCardSize;
        for (ChampionshipTeam team : teams) {
            int[] order = new int[cells];
            for (int index = 0; index < cells; index++) order[index] = index;
            java.util.Random random =
                    seed == 0L
                            ? new java.util.Random()
                            : new java.util.Random(seed + team.getId() * 7919L);
            for (int index = cells - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                int value = order[index];
                order[index] = order[swap];
                order[swap] = value;
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
            if (order[index] == trueIndex) {
                displayedAt = index;
                break;
            }
        }
        if (displayedAt < 0 || displayedAt == trueIndex) return;
        int displaced = order[trueIndex];
        order[trueIndex] = trueIndex;
        order[displayedAt] = displaced;
    }

    public CardSize size() {
        return size;
    }

    public BingoVariantRules variant() {
        return variant;
    }

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

    public void setPlayerMapItem(UUID playerId, ItemStack item) {
        playerMapItems.put(playerId, item);
    }

    public Optional<ItemStack> mapItem(UUID playerId, ChampionshipTeam team) {
        return Optional.ofNullable(playerMapItems.getOrDefault(playerId, teamMapItems.get(team)));
    }

    public int countCompletedLines(ChampionshipTeam team) {
        return scoring.completedLines(scoringTeamId(team));
    }

    /** Grid indices of cells the team has completed on the shared card. */
    public int[] completedIndices(ChampionshipTeam team) {
        return scoring.completedIndices(scoringTeamId(team));
    }

    public void setOutcome(RoundOutcome outcome) {
        this.outcome = outcome;
    }

    public RoundOutcome outcome() {
        return outcome;
    }

    public int completedCount(ChampionshipTeam team) {
        return scoring.completedCount(scoringTeamId(team));
    }

    /** Cells this team claimed before every other team: own completion time strictly earliest. */
    public int countFirstCompletions(ChampionshipTeam team) {
        String teamId = BingoTeamAdapter.id(team);
        if (isDifferential()) return scoring.firstClaimCount(scoringTeamId(team));
        List<String> rivals =
                teams.stream().map(BingoTeamAdapter::id).filter(id -> !id.equals(teamId)).toList();
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

    /** Records riding movement for a participant in centimetres. */
    public void recordRidingMovement(
            Player player,
            org.bukkit.Statistic statistic,
            double centimeters,
            BingoRidingTravel.Source source) {
        if (player == null || !Double.isFinite(centimeters) || centimeters <= 0.0D) return;
        ridingTravel.record(player.getUniqueId(), statistic, centimeters, source);
    }

    /** True once every cell on the board has been claimed by at least one team. */
    public boolean boardFullyClaimed() {
        return scoring.boardFullyClaimed();
    }

    /**
     * Game-time (seconds) at which the team reached its current completed count. Used to break
     * "same completed count" ties: earlier wins. A team with no completions returns {@link
     * Long#MAX_VALUE}.
     */
    public long lastCompletionTime(ChampionshipTeam team) {
        return scoring.lastCompletionTick(scoringTeamId(team));
    }

    /** Current score for the team. */
    public int score(ChampionshipTeam team) {
        return scoring.score(scoringTeamId(team));
    }

    /** Teams ranked by score descending, ties broken by earliest last-completion time. */
    public List<ChampionshipTeam> rankedTeams() {
        List<ChampionshipTeam> sorted = new ArrayList<>(teams);
        sorted.sort(
                (a, b) -> {
                    int diff = Integer.compare(score(b), score(a));
                    if (diff != 0) return diff;
                    return Long.compare(lastCompletionTime(a), lastCompletionTime(b));
                });
        return sorted;
    }

    /**
     * Team with the highest score; ties broken by earliest last-completion time. Null if all zero.
     */
    public ChampionshipTeam resolveTopScore() {
        for (ChampionshipTeam team : rankedTeams()) {
            if (score(team) > 0) return team;
        }
        return null;
    }

    private int scoringTeamId(ChampionshipTeam team) {
        return scoringTeamIds.getOrDefault(team, -1);
    }

    // ── round-start setup ────────────────────────────────────────────────────────────────────

    /** Revokes all advancements and snapshots statistic baselines for a round participant. */
    public void prepareParticipant(Player player, ChampionshipTeam team) {
        java.util.Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
        while (it.hasNext()) revokeAdvancement(player, it.next());

        if (isDifferential()) {
            differentialPlayerCards.computeIfAbsent(
                    player.getUniqueId(),
                    uuid -> {
                        long playerSeed =
                                variant == null ? 0L : (long) uuid.hashCode() * 1_000_003L;
                        return generatedCard(playerSeed);
                    });
            playerTeams.put(player.getUniqueId(), team);
            syncTeamStateToPlayer(player.getUniqueId(), team);
        }

        Map<StatisticHandle, Integer> baselines =
                statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        for (GameTask task : contentCardOf(player.getUniqueId(), team).getTasks()) {
            if (task.data.getType() == TaskData.TaskType.STATISTIC) {
                StatisticHandle h = ((StatisticTask) task.data).statistic();
                baselines.putIfAbsent(h, readStatistic(player, h));
            }
        }
    }

    /**
     * Snapshots statistic baselines for a participant <em>without</em> revoking advancements - used
     * when a player reconnects mid-round, where their earned advancements and card progress must be
     * preserved. Only baselines statistics that don't already have one, so a participant who was
     * online at round start (already prepared) is left untouched.
     */
    public void ensureStatBaselines(Player player) {
        Map<StatisticHandle, Integer> baselines =
                statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        ChampionshipTeam team = playerTeams.get(player.getUniqueId());
        List<GameTask> tasks =
                team == null
                        ? card.getTasks()
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
                        try {
                            base += player.getStatistic(h.statisticType(), variant);
                        } catch (IllegalArgumentException ignored) {
                        }
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

    /**
     * @return the task just completed for this team by collecting this item, if any.
     */
    public Optional<CompletionResult> tryCompleteItem(
            Player player,
            ChampionshipTeam team,
            org.bukkit.Material itemType,
            int heldAmount,
            long gameTime) {
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
                match =
                        set.items().contains(itemType)
                                && BingoEventObjectiveEvaluator.hasAllMembers(player, set.items());
                need = 1;
            } else {
                continue;
            }
            if (match && heldAmount >= need) {
                var completion = completeTask(task, index, player, team, gameTime);
                if (completion.isPresent()) return completion;
            }
        }
        return Optional.empty();
    }

    /**
     * @return the task just completed for this team by holding a potion of the given form+effect,
     *     if any. {@code effect} is the base effect key (strong/long variants already collapsed by
     *     the caller).
     */
    public Optional<CompletionResult> tryCompletePotion(
            Player player,
            ChampionshipTeam team,
            org.bukkit.Material material,
            String effect,
            int heldAmount,
            long gameTime) {
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task)) continue;
            if (!(task.data instanceof PotionTask pt)) continue;
            if (pt.form().material == material
                    && pt.effect().equals(effect)
                    && heldAmount >= pt.count()) {
                var completion = completeTask(task, index, player, team, gameTime);
                if (completion.isPresent()) return completion;
            }
        }
        return Optional.empty();
    }

    public Optional<CompletionResult> tryCompleteAdvancement(
            Player player, ChampionshipTeam team, Advancement advancement, long gameTime) {
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || task.taskType() != TaskData.TaskType.ADVANCEMENT)
                continue;
            AdvancementTask data = (AdvancementTask) task.data;
            if (data.advancement() != null && data.advancement().key().equals(advancement.key())) {
                var completion = completeTask(task, index, player, team, gameTime);
                if (completion.isPresent()) return completion;
            }
        }
        return Optional.empty();
    }

    /** Completes a count-one event task from a discrete Bukkit event. */
    public Optional<CompletionResult> tryCompleteEventSignal(
            Player player, ChampionshipTeam team, String trigger, String param, long gameTime) {
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || !(task.data instanceof EventTask event)) continue;
            if (event.count() != 1) continue;
            if (!event.trigger().equalsIgnoreCase(trigger)
                    || !event.param().equalsIgnoreCase(param)) continue;
            var completion = completeTask(task, index, player, team, gameTime);
            if (completion.isPresent()) return completion;
        }
        return Optional.empty();
    }

    /** Checks all statistic tasks for this player against current values vs baseline. */
    public List<CompletionResult> tryCompleteStatistics(
            Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<CompletionResult> completed = new ArrayList<>();
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || task.taskType() != TaskData.TaskType.STATISTIC)
                continue;
            StatisticTask data = (StatisticTask) task.data;
            StatisticHandle h = data.statistic();
            int delta = statisticDelta(player, h);
            int target = statisticTarget(data);
            if (delta >= target)
                completeTask(task, index, player, team, gameTime).ifPresent(completed::add);
        }
        return completed;
    }

    private int statisticDelta(Player player, StatisticHandle handle) {
        int vanillaDelta = readStatistic(player, handle) - baseline(player.getUniqueId(), handle);
        return ridingTravel.delta(player.getUniqueId(), handle.statisticType(), vanillaDelta);
    }

    /** Checks every state/tracked EventTask for the player's team. */
    public List<CompletionResult> tryCompletePollableEvents(
            Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<CompletionResult> completed = new ArrayList<>();
        List<GameTask> tasks = contentCardOf(player.getUniqueId(), team).getTasks();
        for (int index = 0; index < tasks.size(); index++) {
            GameTask task = tasks.get(index);
            if (!canAttempt(team, index, task) || !(task.data instanceof EventTask event)) continue;
            if (!EventTrigger.isPollable(event.trigger()) || !pollableMet(player, event)) continue;
            completeTask(task, index, player, team, gameTime).ifPresent(completed::add);
        }
        return completed;
    }

    private boolean canAttempt(ChampionshipTeam team, int index, GameTask task) {
        if (!scoring.canAttempt(scoringTeamId(team), index)) return false;
        GameTask state = cardOf(team).getTasks().get(index);
        return !state.isLocked() && !task.isLocked();
    }

    private Optional<CompletionResult> completeTask(
            GameTask task, int index, Player player, ChampionshipTeam team, long gameTime) {
        GameTask.Completion completion = completion(player, team, gameTime);
        GameTask state = cardOf(team).getTasks().get(index);
        if (!canAttempt(team, index, task)) return Optional.empty();
        var decision =
                scoring.apply(
                        new CompletionObservation(
                                scoringId,
                                1,
                                ++completionSequence,
                                scoringTeamId(team),
                                player.getUniqueId(),
                                index,
                                gameTime));
        if (!decision.accepted()) return Optional.empty();
        if (!state.complete(completion, false))
            throw new IllegalStateException(
                    "Accepted Bingo completion could not be projected to the card");
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
                task.complete(
                        new GameTask.Completion(
                                player.getUniqueId(),
                                completion.playerName(),
                                BingoTeamAdapter.color(collaborator),
                                BingoTeamAdapter.id(collaborator),
                                gameTime),
                        false);
            }
        }
        return Optional.of(
                new CompletionResult(
                        task,
                        team,
                        decision.cellPoints(),
                        decision.linePointsPerMember(),
                        decision.awards()));
    }

    private void syncTeamStateToPlayer(UUID playerId, ChampionshipTeam team) {
        BingoCard playerCard = differentialPlayerCards.get(playerId);
        if (playerCard == null) return;
        List<GameTask> state = cardOf(team).getTasks();
        for (int index = 0; index < state.size(); index++) {
            GameTask source = state.get(index);
            GameTask target = playerCard.getTasks().get(index);
            for (GameTask.Completion completion : source.allCompletions())
                target.complete(completion, false);
            if (source.isLocked()) target.setLocked(true);
        }
    }

    public boolean hasWon(ChampionshipTeam team) {
        return scoring.hasWon(scoringTeamId(team));
    }

    public boolean revealRandomHiddenTask() {
        List<GameTask> hidden =
                card.getTasks().stream()
                        .filter(task -> task.isHidden() && !task.isCompleted())
                        .toList();
        if (hidden.isEmpty()) return false;
        hidden.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(hidden.size()))
                .setHidden(false);
        return true;
    }

    public boolean lockRandomTask() {
        List<GameTask> open =
                card.getTasks().stream()
                        .filter(task -> !task.isLocked() && !task.isCompleted())
                        .toList();
        if (open.isEmpty()) return false;
        open.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(open.size()))
                .setLocked(true);
        return true;
    }

    public boolean allTasksUncompletable() {
        return card.getTasks().stream().allMatch(task -> task.isLocked() || task.isCompleted());
    }

    public void refreshUncompletedTasks() {
        List<GameTask> replacements =
                taskGenerator.apply(
                        new TaskGenerator.GeneratorSettings(
                                0L,
                                includedTypes,
                                size,
                                extraExcludedTags,
                                extraTagCaps,
                                variant.difficulty().tierWeights(),
                                variant.remix() == BingoRemix.NETHER ? 0.5D : 0D,
                                variant.remix() == BingoRemix.COLORFUL,
                                List.of()));
        int replacement = 0;
        for (GameTask task : card.getTasks()) {
            if (task.isCompleted() || task.isLocked()) continue;
            task.data = replacements.get(replacement++ % replacements.size()).data;
        }
    }

    private boolean pollableMet(Player player, EventTask event) {
        Set<String> biomeKeys =
                event.subjects().stream()
                        .filter(subject -> subject.kind() == EventSubject.Kind.BIOME)
                        .map(EventSubject::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        BingoEventObjectiveRule rule =
                new BingoEventObjectiveRule(
                        event.trigger(), event.param(), event.count(), event.members(), biomeKeys);
        return BingoEventObjectiveEvaluator.matches(player, rule, eventTracker);
    }

    private static int statisticTarget(StatisticTask data) {
        if (StatisticCategories.of(data.statistic().statisticType()) == StatisticCategory.TRAVEL) {
            return data.count() * 1000; // travel stats are in cm; count is shown as count*10 blocks
        }
        return data.count();
    }

    private GameTask.Completion completion(Player player, ChampionshipTeam team, long gameTime) {
        Component name =
                BingoTaskItems.playerName(
                        BingoCardMenuConfig.taskSnapshot(),
                        player.getName(),
                        LegacyText.component(team.getColoredName()));
        return new GameTask.Completion(
                player.getUniqueId(),
                name,
                BingoTeamAdapter.color(team),
                BingoTeamAdapter.id(team),
                gameTime);
    }
}
