package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TagFilters;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSource;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** MineBingo's frozen vote chain, hosted inside CC's existing DAILY lobby. */
final class DailyBingoVoteController {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // Keep the dynamically selected menu keys visible to the language-resource contract test.
    private static final List<String> MENU_LANGUAGE_KEYS = List.of(
            "mode.domination.name", "mode.domination.lore", "mode.speedrun.name", "mode.speedrun.lore",
            "mode.quantity.name", "mode.quantity.lore", "mode.points.name", "mode.points.lore",
            "mode.random.name", "mode.random.lore", "card_difficulty.easy.name", "card_difficulty.easy.lore",
            "card_difficulty.lite.name", "card_difficulty.lite.lore", "card_difficulty.normal.name",
	            "card_difficulty.normal.lore", "card_difficulty.hard.name", "card_difficulty.hard.lore",
	            "card_difficulty.extreme.name", "card_difficulty.extreme.lore");
	    // Worker sources use these shared Bingo locale keys, but the Core resource contract test scans Core sources only.
	    private static final List<String> WORKER_SPECTATOR_LANGUAGE_KEYS = List.of(
	            "spectator.teleport.name", "spectator.teleport.hint", "spectator.teleport.menu_title",
	            "spectator.teleport.player", "spectator.teleport.team", "spectator.teleport.click",
	            "spectator.teleport.none");
	    enum Phase { ALL, GENESIS }
    private enum ModeChoice { DOMINATION, SPEEDRUN, QUANTITY, POINTS, RANDOM }

    record VoteHolder(UUID voter, Phase phase) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final ChampionshipsCore plugin;
    private final DailyManager daily;
    private final Set<UUID> voters = new LinkedHashSet<>();
    private final Map<UUID, ModeChoice> modeVotes = new HashMap<>();
    private final Map<UUID, BingoDifficulty> difficultyVotes = new HashMap<>();
    private final Map<UUID, Integer> lineVotes = new HashMap<>();
    private final Map<UUID, List<Material>> genesisOptions = new HashMap<>();
    private final Map<UUID, Material> genesisPicks = new HashMap<>();
    private CompletableFuture<BingoVariantRules> result;
    private List<ChampionshipTeam> teams = List.of();
    private Phase phase;
    private BukkitTask timer;
    private BossBar bossBar;
    private int secondsLeft;
    private BingoMode selectedMode;
    private BingoDifficulty selectedDifficulty;
    private int selectedWinLines = 1;

    DailyBingoVoteController(ChampionshipsCore plugin, DailyManager daily) {
        this.plugin = plugin;
        this.daily = daily;
    }

    synchronized CompletableFuture<BingoVariantRules> begin(List<ChampionshipTeam> matchTeams) {
        if (result != null && !result.isDone()) return CompletableFuture.completedFuture(null);
        reset();
        teams = List.copyOf(matchTeams);
        teams.forEach(team -> voters.addAll(team.getMembers()));
        result = new CompletableFuture<>();
        beginPhase(Phase.ALL);
        return result;
    }

    synchronized void cancel() {
        if (result != null && !result.isDone()) result.complete(null);
        reset();
    }

    boolean owns(InventoryHolder holder) { return holder instanceof VoteHolder; }

    synchronized void click(Player player, int rawSlot, VoteHolder holder) {
        if (result == null || result.isDone() || holder.phase() != phase
                || !holder.voter().equals(player.getUniqueId()) || !voters.contains(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        switch (phase) {
            case ALL -> {
                if (rawSlot >= 11 && rawSlot <= 15) castMode(player, rawSlot);
                else if (rawSlot >= 19 && rawSlot <= 23) castDifficulty(player, rawSlot - 8);
                else if (rawSlot >= 28 && rawSlot <= 32) castLines(player, rawSlot - 17);
            }
            case GENESIS -> castGenesis(player, rawSlot);
        }
    }

    private void castMode(Player player, int slot) {
        ModeChoice choice = switch (slot) {
            case 11 -> ModeChoice.DOMINATION;
            case 12 -> ModeChoice.SPEEDRUN;
            case 13 -> ModeChoice.QUANTITY;
            case 14 -> ModeChoice.POINTS;
            case 15 -> ModeChoice.RANDOM;
            default -> null;
        };
        if (choice == null || (choice == ModeChoice.POINTS && teams.size() < 4)) return;
        modeVotes.put(player.getUniqueId(), choice);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        refreshOpenMenus();
    }

    private void castDifficulty(Player player, int slot) {
        BingoDifficulty choice = switch (slot) {
            case 11 -> BingoDifficulty.EASY;
            case 12 -> BingoDifficulty.LITE;
            case 13 -> BingoDifficulty.NORMAL;
            case 14 -> BingoDifficulty.HARD;
            case 15 -> BingoDifficulty.EXTREME;
            default -> null;
        };
        boolean hasSoloTeam = teams.stream().anyMatch(team -> team.getMembers().size() <= 1);
        if (choice == null || (choice == BingoDifficulty.EXTREME && hasSoloTeam)) return;
        difficultyVotes.put(player.getUniqueId(), choice);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        refreshOpenMenus();
    }

    private void castLines(Player player, int slot) {
        if (slot < 11 || slot > 15) return;
        lineVotes.put(player.getUniqueId(), slot - 10);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        refreshOpenMenus();
    }

    private void castGenesis(Player player, int slot) {
        if (slot < 9 || slot > 17) return;
        List<Material> options = genesisOptions.get(player.getUniqueId());
        int index = slot - 9;
        if (options == null || index >= options.size()) return;
        genesisPicks.put(player.getUniqueId(), options.get(index));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        open(player);
    }

    private void beginPhase(Phase next) {
        phase = next;
        secondsLeft = next == Phase.GENESIS ? 30
                : plugin.getGameManager().getBingoManager().dailyVoteSeconds();
        MessageService messages = MessageService.global();
        int phaseSeconds = secondsLeft;
        broadcast(messages == null ? (next == Phase.GENESIS
                ? "&d创世奇遇：每位玩家选择一个物品加入本局卡片。"
                : "&d宾果投票开始！请选择模式、难度和胜利线数。")
                : messages.tr(next == Phase.GENESIS ? "genesis.started" : "vote.started", phaseSeconds));
        updateBossBar();
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null) open(player);
        }
        if (timer != null) timer.cancel();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (DailyBingoVoteController.this) {
                if (result == null || result.isDone()) return;
                long online = voters.stream().filter(uuid -> Bukkit.getPlayer(uuid) != null).count();
                if (!daily.isDailyLobby() || online < 2) {
                    broadcast("&c投票因参与人数不足而取消，玩家将返回匹配队列。");
                    cancel();
                    return;
                }
                secondsLeft--;
                updateBossBar();
                if (secondsLeft <= 0) finishPhase();
                else if (secondsLeft <= 5 || secondsLeft == 10)
                    broadcast("&7投票剩余 &e" + secondsLeft + " &7秒");
            }
        }, 20L, 20L);
    }

    private void finishPhase() {
        if (timer != null) timer.cancel();
        timer = null;
        switch (phase) {
            case ALL -> {
                List<ModeChoice> choices = new ArrayList<>(List.of(ModeChoice.values()));
                if (teams.size() < 4) choices.remove(ModeChoice.POINTS);
                ModeChoice winner = winner(choices, modeVotes);
                if (winner == ModeChoice.RANDOM) {
                    choices.remove(ModeChoice.RANDOM);
                    winner = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                }
                selectedMode = BingoMode.valueOf(winner.name());
                MessageService messages = MessageService.global();
                broadcast(messages == null ? "&6本局模式：&f" + modeName(selectedMode)
                        : messages.tr(winner == ModeChoice.RANDOM ? "vote.result_random" : "vote.result",
                        messages.tr("mode." + selectedMode.name().toLowerCase() + ".name")));
                List<BingoDifficulty> difficultyChoices = new ArrayList<>(List.of(BingoDifficulty.values()));
                if (teams.stream().anyMatch(team -> team.getMembers().size() <= 1))
                    difficultyChoices.remove(BingoDifficulty.EXTREME);
                selectedDifficulty = winner(difficultyChoices, difficultyVotes);
                MessageService difficultyMessages = MessageService.global();
                broadcast(difficultyMessages == null ? "&6本局难度：&f" + difficultyName(selectedDifficulty)
                        : difficultyMessages.tr("difficulty_vote.result", difficultyMessages.tr(
                        "card_difficulty." + selectedDifficulty.name().toLowerCase() + ".name")));
                int lines = selectedMode == BingoMode.SPEEDRUN
                        ? winner(List.of(1, 2, 3, 4, 5), lineVotes) : 1;
                MessageService lineMessages = MessageService.global();
                if (selectedMode == BingoMode.SPEEDRUN)
                    broadcast(lineMessages == null ? "&6本局胜利条件：&f" + lines + " 条线"
                            : lineMessages.tr("lines_vote.result", lines));
                complete(lines);
            }
            case GENESIS -> finishGenesis();
        }
    }

    private void complete(int winLines) {
        selectedWinLines = winLines;
        BingoRemix remix = BingoRemix.NONE;
        var manager = plugin.getGameManager().getBingoManager();
        if (manager.dailyRemixEnabled()
                && ThreadLocalRandom.current().nextDouble() < manager.dailyRemixChance()) {
            remix = BingoRemix.random();
        }
        if (remix != BingoRemix.NONE) {
            broadcast("&5✦ 奇遇降临！本局触发【" + remixName(remix) + "】 &7" + remixDescription(remix));
            for (UUID voter : voters) {
                Player player = Bukkit.getPlayer(voter);
                if (player != null) player.showTitle(Title.title(
                        Component.text("奇遇", NamedTextColor.LIGHT_PURPLE),
                        Component.text(remixName(remix) + " · " + remixDescription(remix), NamedTextColor.GRAY)));
            }
        }
        if (remix == BingoRemix.GENESIS) {
            prepareGenesisOptions();
            beginPhase(Phase.GENESIS);
            return;
        }
        finishResult(new BingoVariantRules(selectedMode, selectedDifficulty, winLines, remix));
    }

    private void finishGenesis() {
        List<String> picks = new ArrayList<>();
        for (UUID voter : voters) {
            Material picked = genesisPicks.get(voter);
            if (picked == null) {
                List<Material> options = genesisOptions.getOrDefault(voter, List.of());
                if (!options.isEmpty()) picked = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            }
            if (picked != null && !picks.contains(picked.name())) picks.add(picked.name());
        }
        finishResult(new BingoVariantRules(selectedMode, selectedDifficulty, selectedWinLines,
                BingoRemix.GENESIS, picks));
    }

    private void finishResult(BingoVariantRules rules) {
        CompletableFuture<BingoVariantRules> completion = result;
        closeMenus();
        resetStateOnly();
        completion.complete(rules);
    }

    private void prepareGenesisOptions() {
        LinkedHashSet<Material> candidates = new LinkedHashSet<>();
        for (var entry : TaskPoolSource.pool().entries()) {
            TaskData task = entry.task();
            if (TagFilters.active().isExcluded(task)) continue;
            switch (task) {
                case ItemTask item -> candidates.add(item.itemType());
                case OneOfTask one -> candidates.addAll(one.items());
                case AllOfTask all -> candidates.addAll(all.items());
                default -> { }
            }
        }
        List<Material> source = new ArrayList<>(candidates);
        for (UUID voter : voters) {
            java.util.Collections.shuffle(source);
            genesisOptions.put(voter, List.copyOf(source.subList(0, Math.min(9, source.size()))));
        }
    }

    private <T> T winner(Collection<T> candidates, Map<UUID, T> votes) {
        Map<T, Integer> counts = new HashMap<>();
        candidates.forEach(candidate -> counts.put(candidate, 0));
        votes.values().forEach(vote -> { if (counts.containsKey(vote)) counts.merge(vote, 1, Integer::sum); });
        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<T> tied = candidates.stream().filter(candidate -> counts.get(candidate) == best).toList();
        return tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
    }

    private void open(Player player) {
        VoteHolder holder = new VoteHolder(player.getUniqueId(), phase);
        MessageService messages = MessageService.global();
        String title = messages == null ? (phase == Phase.GENESIS ? "创世物品选择" : "宾果玩法投票")
                : messages.tr(phase == Phase.GENESIS ? "genesis.menu_title" : "vote.menu_title");
        Inventory inventory = Bukkit.createInventory(holder, phase == Phase.GENESIS ? 27 : 54,
                LEGACY.deserialize(title));
        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.GRAY, false);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        switch (phase) {
            case ALL -> {
                renderModesAt(inventory, player.getUniqueId(), 11);
                renderDifficultiesAt(inventory, player.getUniqueId(), 19);
                renderLinesAt(inventory, player.getUniqueId(), 28);
            }
            case GENESIS -> renderGenesis(inventory, player.getUniqueId());
        }
        player.openInventory(inventory);
    }

    private void renderModes(Inventory inventory, UUID viewer) {
        renderModesAt(inventory, viewer, 11);
    }

    private void renderModesAt(Inventory inventory, UUID viewer, int start) {
        put(inventory, start, ModeChoice.DOMINATION, Material.GRASS_BLOCK, "mode.domination", viewer, modeVotes,
                false);
        put(inventory, start + 1, ModeChoice.SPEEDRUN, Material.FEATHER, "mode.speedrun", viewer, modeVotes,
                false);
        put(inventory, start + 2, ModeChoice.QUANTITY, Material.GOLD_INGOT, "mode.quantity", viewer, modeVotes,
                false);
        put(inventory, start + 3, ModeChoice.POINTS, Material.SUNFLOWER, "mode.points", viewer, modeVotes,
                teams.size() < 4);
        put(inventory, start + 4, ModeChoice.RANDOM, Material.NETHER_STAR, "mode.random", viewer, modeVotes,
                false);
    }

    private void renderDifficulties(Inventory inventory, UUID viewer) {
        renderDifficultiesAt(inventory, viewer, 11);
    }

    private void renderDifficultiesAt(Inventory inventory, UUID viewer, int start) {
        boolean lockExtreme = teams.stream().anyMatch(team -> team.getMembers().size() <= 1);
        Material[] icons = {Material.GRASS_BLOCK, Material.OAK_LOG, Material.IRON_INGOT,
                Material.DIAMOND, Material.NETHERITE_INGOT};
        int index = 0;
        for (BingoDifficulty difficulty : BingoDifficulty.values()) {
            put(inventory, start + index, difficulty, icons[index], "card_difficulty." + difficulty.name().toLowerCase(), viewer,
                    difficultyVotes,
                    difficulty == BingoDifficulty.EXTREME && lockExtreme);
            index++;
        }
    }

    private void renderLines(Inventory inventory, UUID viewer) {
        renderLinesAt(inventory, viewer, 10);
    }

    private void renderLinesAt(Inventory inventory, UUID viewer, int start) {
        MessageService messages = MessageService.global();
        for (int lines = 1; lines <= 5; lines++) {
            int line = lines;
            int count = (int) lineVotes.values().stream().filter(value -> value == line).count();
            boolean picked = Integer.valueOf(line).equals(lineVotes.get(viewer));
            List<String> lore = new ArrayList<>(List.of(messages == null
                    ? "完成 " + line + " 条宾果线即可赢得本局"
                    : messages.tr("lines_vote.menu_desc", line), ""));
            lore.add(messages == null ? "当前票数：" + count : messages.tr("lines_vote.menu_count", count));
            lore.add(messages == null ? (picked ? "✔ 你的选择" : "点击投票")
                    : messages.tr(picked ? "lines_vote.menu_picked" : "lines_vote.menu_hint"));
            ItemStack stack = item(Material.LIGHT,
                    messages == null ? line + " 条线获胜" : messages.tr("lines_vote.menu_option", line),
                    lore, NamedTextColor.YELLOW, picked);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (meta instanceof org.bukkit.inventory.meta.BlockDataMeta blockMeta) {
                    org.bukkit.block.data.type.Light light = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
                    light.setLevel(lines);
                    blockMeta.setBlockData(light);
                }
                meta.setMaxStackSize(99);
                stack.setItemMeta(meta);
            }
            stack.setAmount(Math.max(1, count));
            inventory.setItem(start + line - 1, stack);
        }
    }

    private void renderGenesis(Inventory inventory, UUID viewer) {
        List<Material> options = genesisOptions.getOrDefault(viewer, List.of());
        Material selected = genesisPicks.get(viewer);
        for (int index = 0; index < options.size(); index++) {
            Material material = options.get(index);
            inventory.setItem(9 + index, item(material, material.key().asString(),
                    List.of(material == selected ? "✔ 你的选择" : "点击把该物品加入卡片"),
                    NamedTextColor.LIGHT_PURPLE, material == selected));
        }
    }

    private <T> void put(Inventory inventory, int slot, T choice, Material icon, String langKey,
                         UUID viewer, Map<UUID, T> votes, boolean locked) {
        int count = (int) votes.values().stream().filter(choice::equals).count();
        MessageService messages = MessageService.global();
        String name = messages == null ? langKey : messages.tr(langKey + ".name");
        List<String> lore = new ArrayList<>(messages == null ? List.of() : messages.lines(langKey + ".lore"));
        lore.add("");
        if (locked) lore.add(messages == null ? "当前阵容不可选择" : messages.tr(
                langKey.startsWith("mode.") ? "vote.menu_locked" : "difficulty_vote.menu_locked"));
        else {
            String countKey = langKey.startsWith("mode.") ? "vote.menu_count"
                    : langKey.startsWith("card_difficulty.") ? "difficulty_vote.menu_count" : "lines_vote.menu_count";
            String hintKey = langKey.startsWith("mode.") ? "vote.menu_hint"
                    : langKey.startsWith("card_difficulty.") ? "difficulty_vote.menu_hint" : "lines_vote.menu_hint";
            lore.add(messages == null ? "当前票数：" + count : messages.tr(countKey, count));
            boolean picked = choice.equals(votes.get(viewer));
            lore.add(messages == null ? (picked ? "✔ 你的选择" : "点击投票")
                    : messages.tr(picked ? (langKey.startsWith("mode.") ? "vote.menu_picked"
                    : langKey.startsWith("card_difficulty.") ? "difficulty_vote.menu_picked" : "lines_vote.menu_picked") : hintKey));
        }
        ItemStack stack = item(locked ? Material.BARRIER : icon, name, lore,
                locked ? NamedTextColor.RED : NamedTextColor.YELLOW, choice.equals(votes.get(viewer)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(99);
            stack.setItemMeta(meta);
        }
        stack.setAmount(Math.max(1, locked ? 1 : count));
        inventory.setItem(slot, stack);
    }

    private ItemStack item(Material material, String name, List<String> lore, NamedTextColor color, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name == null ? "" : name)
                .colorIfAbsent(color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> LEGACY.deserialize(line == null ? "" : line)
                .colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        if (glow) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private void refreshOpenMenus() {
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof VoteHolder holder
                    && holder.phase() == phase) open(player);
        }
    }

    private void closeMenus() {
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof VoteHolder)
                player.closeInventory();
        }
    }

    private void broadcast(String message) { daily.broadcastDaily(voters, message); }

    private void reset() {
        closeMenus();
        resetStateOnly();
    }

    private void resetStateOnly() {
        if (timer != null) timer.cancel();
        timer = null;
        voters.clear();
        modeVotes.clear();
        difficultyVotes.clear();
        lineVotes.clear();
        genesisOptions.clear();
        genesisPicks.clear();
        teams = List.of();
        phase = null;
        secondsLeft = 0;
        selectedMode = null;
        selectedDifficulty = null;
        selectedWinLines = 1;
        result = null;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    synchronized boolean reopen(Player player) {
        if (result == null || result.isDone() || phase == null || !voters.contains(player.getUniqueId())) return false;
        open(player);
        return true;
    }

    private void updateBossBar() {
        if (result == null || result.isDone() || phase == null) return;
        MessageService messages = MessageService.global();
        String title = messages == null
                ? "宾果投票 · 剩余 " + secondsLeft + " 秒"
                : messages.tr("vote.bossbar", secondsLeft);
        String legacyTitle = LEGACY.serialize(LEGACY.deserialize(title));
        if (bossBar == null) bossBar = Bukkit.createBossBar(legacyTitle, BarColor.PURPLE, BarStyle.SOLID);
        else bossBar.setTitle(legacyTitle);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0,
                secondsLeft / (double) Math.max(1, plugin.getGameManager().getBingoManager().dailyVoteSeconds()))));
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null && !bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        }
    }

    private static String modeName(BingoMode mode) { return switch (mode) {
        case DOMINATION -> "占据"; case SPEEDRUN -> "速通"; case QUANTITY -> "竞量"; case POINTS -> "积分";
    }; }
    private static String difficultyName(BingoDifficulty difficulty) { return switch (difficulty) {
        case EASY -> "新手"; case LITE -> "简单"; case NORMAL -> "标准"; case HARD -> "困难"; case EXTREME -> "极难";
    }; }
    private static String remixName(BingoRemix remix) { return switch (remix) {
        case NONE -> "无"; case NETHER -> "下界"; case SCALE -> "缩放"; case DIFFERENTIAL -> "差分";
        case UPGRADE -> "升级"; case BLIND -> "视障"; case FEAST -> "盛宴"; case COOP -> "合作";
        case GENESIS -> "创世"; case COLORFUL -> "缤纷"; case CHAIN -> "连锁"; case VARIATION -> "变奏";
        case FINALE -> "终曲"; case ETERNAL_NIGHT -> "永夜"; case POLAR_DAY -> "极昼";
        case PARALLAX -> "视差"; case SPEEDRUN -> "速通";
    }; }
    private static String remixDescription(BingoRemix remix) { return switch (remix) {
        case NONE -> "";
        case NETHER -> "全员在下界开局，卡片偏向下界任务";
        case SCALE -> "卡片缩为 3×3 或 4×4";
        case DIFFERENTIAL -> "每位玩家看到不同任务，队友共享格位进度";
        case UPGRADE -> "获得下界合金、鞘翅和烟花升级套装";
        case BLIND -> "任务初始隐藏，并随时间逐项揭示";
        case FEAST -> "整张卡只由进度，或统计与事件任务组成";
        case COOP -> "全场合作完成同一张卡";
        case GENESIS -> "每位玩家选一个物品加入卡片";
        case COLORFUL -> "卡片优先包含十六种染料颜色任务";
        case CHAIN -> "首格后只能完成相邻格";
        case VARIATION -> "每四分钟刷新所有未完成任务";
        case FINALE -> "第五分钟起每分钟永久封锁一格";
        case ETERNAL_NIGHT -> "时间锁定深夜，难度设为困难";
        case POLAR_DAY -> "时间锁定白昼，难度设为简单";
        case PARALLAX -> "各队卡位被打乱，完成后逐格归位";
        case SPEEDRUN -> "使用固定 3×3 末地冲刺卡和速通套装";
    }; }
}
