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

    /** Cells this team claimed before every other team: own completion time strictly earliest. */
    public int countFirstCompletions(ChampionshipTeam team) {
        String teamId = BingoTeamAdapter.id(team);
        List<String> rivals = teams.stream().map(BingoTeamAdapter::id)
                .filter(id -> !id.equals(teamId)).toList();
        int first = 0;
        for (GameTask task : card.getTasks()) {
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

        int rank = task.claimRank(BingoTeamAdapter.id(team));
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

        Map<StatisticHandle, Integer> baselines = statBaselines.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        for (GameTask task : card.getTasks()) {
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
        for (GameTask task : card.getTasks()) {
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
        List<GameTask> tasks = card.getTasks();
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId)) continue;
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
                if (task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                    awardPoints(team, task);
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
        List<GameTask> tasks = card.getTasks();
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId)) continue;
            if (!(task.data instanceof PotionTask pt)) continue;
            if (pt.form().material == material && pt.effect().equals(effect) && heldAmount >= pt.count()) {
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
            if (task.isCompletedByTeam(teamId) || task.taskType() != TaskData.TaskType.ADVANCEMENT) continue;
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

    /** Completes a count-one event task from a discrete Bukkit event. */
    public Optional<GameTask> tryCompleteEventSignal(Player player, ChampionshipTeam team,
                                                     String trigger, String param, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        for (GameTask task : card.getTasks()) {
            if (task.isCompletedByTeam(teamId) || !(task.data instanceof EventTask event)) continue;
            if (event.count() != 1) continue;
            if (!event.trigger().equalsIgnoreCase(trigger) || !event.param().equalsIgnoreCase(param)) continue;
            if (task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                awardPoints(team, task);
                return Optional.of(task);
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
            if (task.isCompletedByTeam(teamId) || task.taskType() != TaskData.TaskType.STATISTIC) continue;
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

    /** Checks every state/tracked EventTask for the player's team. */
    public List<GameTask> tryCompletePollableEvents(Player player, ChampionshipTeam team, long gameTime) {
        String teamId = BingoTeamAdapter.id(team);
        List<GameTask> completed = new ArrayList<>();
        for (GameTask task : card.getTasks()) {
            if (task.isCompletedByTeam(teamId) || !(task.data instanceof EventTask event)) continue;
            if (!EventTrigger.isPollable(event.trigger()) || !pollableMet(player, event)) continue;
            if (task.complete(completion(player, team, gameTime), LOCKS_TASKS)) {
                awardPoints(team, task);
                completed.add(task);
            }
        }
        return completed;
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
