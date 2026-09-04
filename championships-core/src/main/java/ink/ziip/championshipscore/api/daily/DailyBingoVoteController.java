package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.gui.GuiMenu;
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
import ink.ziip.championshipscore.configuration.config.message.GuiText;
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
import java.util.Locale;
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
    enum Phase { MODE, DIFFICULTY, LINES, GENESIS }
    private enum ModeChoice { DOMINATION, SPEEDRUN, QUANTITY, POINTS, RANDOM }
    private static final int INVENTORY_SIZE = 27;
    private static final int OPTION_FIRST_SLOT = 11;
    private static final int OPTION_LAST_SLOT = 15;
    private static final int PREVIOUS_SLOT = 18;
    private static final int PAGE_SLOT = 22;
    private static final int NEXT_SLOT = 26;
    private static final int CLOSE_SLOT = 24;

    final class VoteHolder extends GuiMenu {
        private Phase votePhase;

        private VoteHolder(UUID voter, Phase votePhase) {
            super(voter);
            this.votePhase = votePhase;
        }

        UUID viewerId() { return viewer; }

        void setInventory(Inventory inventory) { this.inventory = inventory; }

        Phase votePhase() { return votePhase; }

        void votePhase(Phase votePhase) { this.votePhase = votePhase; }
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
        beginPhase(Phase.MODE);
        return result;
    }

    synchronized void cancel() {
        if (result != null && !result.isDone()) result.complete(null);
        reset();
    }

    boolean owns(InventoryHolder holder) { return holder instanceof VoteHolder; }

    synchronized void click(Player player, int rawSlot, VoteHolder holder) {
        if (result == null || result.isDone() || !holder.viewerId().equals(player.getUniqueId())
                || !voters.contains(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (phase == Phase.GENESIS) {
            if (holder.votePhase() != Phase.GENESIS) {
                player.closeInventory();
                return;
            }
            castGenesis(player, rawSlot);
            return;
        }
        if (rawSlot == PREVIOUS_SLOT) {
            navigate(player, holder, -1);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            navigate(player, holder, 1);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        switch (holder.votePhase()) {
            case MODE -> castMode(player, rawSlot);
            case DIFFICULTY -> castDifficulty(player, rawSlot);
            case LINES -> castLines(player, rawSlot);
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
        open(player, Phase.DIFFICULTY);
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
        open(player, Phase.LINES);
    }

    private void castLines(Player player, int slot) {
        if (slot < OPTION_FIRST_SLOT || slot > OPTION_LAST_SLOT) return;
        lineVotes.put(player.getUniqueId(), slot - OPTION_FIRST_SLOT + 1);
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
        open(player, Phase.GENESIS);
    }

    private void navigate(Player player, VoteHolder holder, int direction) {
        Phase[] pages = {Phase.MODE, Phase.DIFFICULTY, Phase.LINES};
        int current = java.util.Arrays.asList(pages).indexOf(holder.votePhase());
        if (current < 0) return;
        int next = Math.max(0, Math.min(pages.length - 1, current + direction));
        if (next != current) {
            holder.votePhase(pages[next]);
            open(player, holder);
        }
    }

    private void beginPhase(Phase next) {
        if (next != Phase.MODE && next != Phase.GENESIS)
            throw new IllegalArgumentException("Only the initial vote page or Genesis may start a timer");
        phase = next;
        secondsLeft = next == Phase.GENESIS ? 30
                : plugin.getGameManager().getBingoManager().dailyVoteSeconds();
        MessageService messages = MessageService.global();
        int phaseSeconds = secondsLeft;
        broadcast(messages.tr(switch (next) {
            case MODE -> "vote.started";
            case DIFFICULTY, LINES -> throw new IllegalStateException("Intermediate vote pages do not have timers");
            case GENESIS -> "genesis.started";
        }, phaseSeconds));
        updateBossBar();
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null) open(player, next);
        }
        if (timer != null) timer.cancel();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (DailyBingoVoteController.this) {
                if (result == null || result.isDone()) return;
                long online = voters.stream().filter(uuid -> Bukkit.getPlayer(uuid) != null).count();
                if (!daily.isDailyLobby() || online < 2) {
                    broadcast(MessageService.global().tr("vote.cancelled-insufficient"));
                    cancel();
                    return;
                }
                secondsLeft--;
                updateBossBar();
                if (secondsLeft <= 0) finishPhase();
                else if (secondsLeft <= 5 || secondsLeft == 10)
                    broadcast(MessageService.global().tr("vote.remaining", secondsLeft));
            }
        }, 20L, 20L);
    }

    private void finishPhase() {
        if (timer != null) timer.cancel();
        timer = null;
        switch (phase) {
            case MODE, DIFFICULTY, LINES -> {
                List<ModeChoice> choices = new ArrayList<>(List.of(ModeChoice.values()));
                if (teams.size() < 4) choices.remove(ModeChoice.POINTS);
                ModeChoice winner = winner(choices, modeVotes);
                if (winner == ModeChoice.RANDOM) {
                    choices.remove(ModeChoice.RANDOM);
                    winner = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                }
                selectedMode = BingoMode.valueOf(winner.name());
                MessageService messages = MessageService.global();
                broadcast(messages.tr(winner == ModeChoice.RANDOM ? "vote.result_random" : "vote.result",
                        messages.tr("mode." + selectedMode.name().toLowerCase() + ".name")));
                List<BingoDifficulty> difficultyChoices = new ArrayList<>(List.of(BingoDifficulty.values()));
                if (teams.stream().anyMatch(team -> team.getMembers().size() <= 1))
                    difficultyChoices.remove(BingoDifficulty.EXTREME);
                selectedDifficulty = winner(difficultyChoices, difficultyVotes);
                broadcast(messages.tr("difficulty_vote.result",
                        messages.tr("card_difficulty." + selectedDifficulty.name().toLowerCase() + ".name")));
                int lines = selectedMode == BingoMode.SPEEDRUN
                        ? winner(List.of(1, 2, 3, 4, 5), lineVotes) : 1;
                if (selectedMode == BingoMode.SPEEDRUN)
                    broadcast(messages.tr("lines_vote.result", lines));
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
            MessageService remixMessages = MessageService.global();
            String remixName = remixMessages.tr("remix." + remix.name().toLowerCase(Locale.ROOT) + ".name");
            String remixDescription = remixMessages.tr("remix." + remix.name().toLowerCase(Locale.ROOT) + ".description");
            broadcast(remixMessages.tr("genesis.triggered", remixName, remixDescription));
            for (UUID voter : voters) {
                Player player = Bukkit.getPlayer(voter);
                if (player != null) player.showTitle(Title.title(
                        Component.text(remixMessages.tr("genesis.title"), NamedTextColor.LIGHT_PURPLE),
                        Component.text(remixName, NamedTextColor.GRAY)
                                .append(Component.text(GuiText.SEPARATOR, NamedTextColor.GRAY))
                                .append(Component.text(remixDescription, NamedTextColor.GRAY))));
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

    private void open(Player player, Phase votePhase) {
        VoteHolder holder = new VoteHolder(player.getUniqueId(), votePhase);
        open(player, holder);
    }

    private void open(Player player, VoteHolder holder) {
        MessageService messages = MessageService.global();
        Phase votePhase = holder.votePhase();
        String title = messages.tr(switch (votePhase) {
            case MODE -> "vote.menu_title";
            case DIFFICULTY -> "difficulty_vote.menu_title";
            case LINES -> "lines_vote.menu_title";
            case GENESIS -> "genesis.menu_title";
        });
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                LEGACY.deserialize(title));
        ItemStack border = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.GRAY, false);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        holder.setInventory(inventory);
        switch (votePhase) {
            case MODE -> renderModes(inventory, player.getUniqueId());
            case DIFFICULTY -> renderDifficulties(inventory, player.getUniqueId());
            case LINES -> renderLines(inventory, player.getUniqueId());
            case GENESIS -> renderGenesis(inventory, player.getUniqueId());
        }
        if (votePhase != Phase.GENESIS) renderNavigation(inventory, holder);
        player.openInventory(inventory);
    }

    private void renderModes(Inventory inventory, UUID viewer) {
        put(inventory, 11, ModeChoice.DOMINATION, Material.GRASS_BLOCK, "mode.domination", viewer, modeVotes,
                false);
        put(inventory, 12, ModeChoice.SPEEDRUN, Material.FEATHER, "mode.speedrun", viewer, modeVotes,
                false);
        put(inventory, 13, ModeChoice.QUANTITY, Material.GOLD_INGOT, "mode.quantity", viewer, modeVotes,
                false);
        put(inventory, 14, ModeChoice.POINTS, Material.SUNFLOWER, "mode.points", viewer, modeVotes,
                teams.size() < 4);
        put(inventory, 15, ModeChoice.RANDOM, Material.NETHER_STAR, "mode.random", viewer, modeVotes,
                false);
    }

    private void renderDifficulties(Inventory inventory, UUID viewer) {
        boolean lockExtreme = teams.stream().anyMatch(team -> team.getMembers().size() <= 1);
        Material[] icons = {Material.GRASS_BLOCK, Material.OAK_LOG, Material.IRON_INGOT,
                Material.DIAMOND, Material.NETHERITE_INGOT};
        int index = 0;
        for (BingoDifficulty difficulty : BingoDifficulty.values()) {
            put(inventory, 11 + index, difficulty, icons[index], "card_difficulty." + difficulty.name().toLowerCase(), viewer,
                    difficultyVotes,
                    difficulty == BingoDifficulty.EXTREME && lockExtreme);
            index++;
        }
    }

    private void renderLines(Inventory inventory, UUID viewer) {
        MessageService messages = MessageService.global();
        for (int lines = 1; lines <= 5; lines++) {
            int line = lines;
            int count = (int) lineVotes.values().stream().filter(value -> value == line).count();
            boolean picked = Integer.valueOf(line).equals(lineVotes.get(viewer));
            List<String> lore = new ArrayList<>(List.of(messages.tr("lines_vote.menu_desc", line), ""));
            lore.add(messages.tr("lines_vote.menu_count", count));
            lore.add(messages.tr(picked ? "lines_vote.menu_picked" : "lines_vote.menu_hint"));
            ItemStack stack = item(Material.LIGHT, messages.tr("lines_vote.menu_option", line),
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
            inventory.setItem(OPTION_FIRST_SLOT + line - 1, stack);
        }
    }

    private void renderGenesis(Inventory inventory, UUID viewer) {
        List<Material> options = genesisOptions.getOrDefault(viewer, List.of());
        Material selected = genesisPicks.get(viewer);
        for (int index = 0; index < options.size(); index++) {
            Material material = options.get(index);
            inventory.setItem(9 + index, item(material, material.key().asString(),
                    List.of(MessageService.global().tr(selected == material
                            ? "vote.menu_picked" : "genesis.menu_hint")),
                    NamedTextColor.LIGHT_PURPLE, material == selected));
        }
    }

    private void renderNavigation(Inventory inventory, VoteHolder holder) {
        Phase current = holder.votePhase();
        MessageService messages = MessageService.global();
        String previous = messages.tr("vote.previous");
        String next = messages.tr("vote.next");
        String close = messages.tr("vote.close");
        String page = messages.tr(switch (current) {
            case MODE -> "vote.page_mode";
            case DIFFICULTY -> "vote.page_difficulty";
            case LINES -> "vote.page_lines";
            case GENESIS -> "vote.page_mode";
        });
        String freeNavigation = messages.tr("vote.free_navigation");
        inventory.setItem(PREVIOUS_SLOT, current == Phase.MODE
                ? item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.GRAY, false)
                : item(Material.ARROW, previous, List.of(), NamedTextColor.WHITE, false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER, page, List.of(freeNavigation), NamedTextColor.AQUA, false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, close, List.of(), NamedTextColor.RED, false));
        inventory.setItem(NEXT_SLOT, current == Phase.LINES
                ? item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), NamedTextColor.GRAY, false)
                : item(Material.ARROW, next, List.of(), NamedTextColor.WHITE, false));
    }

    private <T> void put(Inventory inventory, int slot, T choice, Material icon, String langKey,
                         UUID viewer, Map<UUID, T> votes, boolean locked) {
        int count = (int) votes.values().stream().filter(choice::equals).count();
        MessageService messages = MessageService.global();
        String name = messages.tr(langKey + ".name");
        List<String> lore = new ArrayList<>(messages.lines(langKey + ".lore"));
        lore.add("");
        if (locked) lore.add(messages.tr(
                langKey.startsWith("mode.") ? "vote.menu_locked" : "difficulty_vote.menu_locked"));
        else {
            String countKey = langKey.startsWith("mode.") ? "vote.menu_count"
                    : langKey.startsWith("card_difficulty.") ? "difficulty_vote.menu_count" : "lines_vote.menu_count";
            String hintKey = langKey.startsWith("mode.") ? "vote.menu_hint"
                    : langKey.startsWith("card_difficulty.") ? "difficulty_vote.menu_hint" : "lines_vote.menu_hint";
            lore.add(messages.tr(countKey, count));
            boolean picked = choice.equals(votes.get(viewer));
            lore.add(messages.tr(picked ? (langKey.startsWith("mode.") ? "vote.menu_picked"
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
                    && phase != Phase.GENESIS && holder.votePhase() != Phase.GENESIS) open(player, holder);
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
        open(player, phase);
        return true;
    }

    private void updateBossBar() {
        if (result == null || result.isDone() || phase == null) return;
        MessageService messages = MessageService.global();
        String title = messages.tr("vote.bossbar", secondsLeft);
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


}
