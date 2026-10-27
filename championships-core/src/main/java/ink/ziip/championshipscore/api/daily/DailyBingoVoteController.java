package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TagFilters;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSource;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.ComponentTemplates;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.shared.presentation.DurationText;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** MineBingo's frozen vote chain, hosted inside CC's existing DAILY lobby. */
final class DailyBingoVoteController {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();
    // Keep the dynamically selected menu keys visible to the language-resource contract test.
    private static final List<String> MENU_LANGUAGE_KEYS =
            List.of(
                    "mode.domination.name",
                    "mode.domination.lore",
                    "mode.speedrun.name",
                    "mode.speedrun.lore",
                    "mode.quantity.name",
                    "mode.quantity.lore",
                    "mode.points.name",
                    "mode.points.lore",
                    "mode.random.name",
                    "mode.random.lore",
                    "card_difficulty.easy.name",
                    "card_difficulty.easy.lore",
                    "card_difficulty.lite.name",
                    "card_difficulty.lite.lore",
                    "card_difficulty.normal.name",
                    "card_difficulty.normal.lore",
                    "card_difficulty.hard.name",
                    "card_difficulty.hard.lore",
                    "card_difficulty.extreme.name",
                    "card_difficulty.extreme.lore");
    // Worker sources use these shared Bingo locale keys, but the Core resource contract test scans
    // Core sources only.
    private static final List<String> WORKER_SPECTATOR_LANGUAGE_KEYS =
            List.of(
                    "spectator.teleport.name",
                    "spectator.teleport.hint",
                    "spectator.teleport.menu_title",
                    "spectator.teleport.player",
                    "spectator.teleport.team",
                    "spectator.teleport.click",
                    "spectator.teleport.none");

    enum Phase {
        MODE,
        DIFFICULTY,
        LINES,
        GENESIS
    }

    private enum ModeChoice {
        DOMINATION,
        SPEEDRUN,
        QUANTITY,
        POINTS,
        RANDOM
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
    private int phaseDuration;
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
        CompletableFuture<BingoVariantRules> completion = result;
        reset();
        if (completion != null && !completion.isDone()) completion.complete(null);
    }

    private boolean currentView(Player player, DailyBingoVoteMenu menu) {
        return menu.owner == this
                && menu.vote == result
                && result != null
                && !result.isDone()
                && player == menu.connection
                && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && voters.contains(player.getUniqueId())
                && player.getOpenInventory().getTopInventory() == menu.getInventory()
                && phase != null
                && (phase == Phase.GENESIS) == (menu.phase == Phase.GENESIS);
    }

    synchronized void click(Player player, int rawSlot, DailyBingoVoteMenu menu) {
        if (!currentView(player, menu)) return;
        if (rawSlot == menu.layout.control("options-previous")) {
            if (menu.changePage(-1)) render(menu);
            return;
        }
        if (rawSlot == menu.layout.control("options-next")) {
            if (menu.changePage(1)) render(menu);
            return;
        }
        if (menu.phase != Phase.GENESIS) {
            if (rawSlot == menu.layout.control("previous")) {
                navigate(player, menu, -1);
                return;
            }
            if (rawSlot == menu.layout.control("next")) {
                navigate(player, menu, 1);
                return;
            }
            if (rawSlot == menu.layout.control("close")) {
                player.closeInventory();
                return;
            }
        }
        Object choice = menu.targets.get(rawSlot);
        switch (menu.phase) {
            case MODE -> {
                if (choice instanceof ModeChoice mode) castMode(player, mode);
            }
            case DIFFICULTY -> {
                if (choice instanceof BingoDifficulty difficulty)
                    castDifficulty(player, difficulty);
            }
            case LINES -> {
                if (choice instanceof Integer lines) castLines(player, lines);
            }
            case GENESIS -> {
                if (choice instanceof Material material) castGenesis(player, material);
            }
        }
    }

    private void castMode(Player player, ModeChoice choice) {
        if (choice == ModeChoice.POINTS && teams.size() < 4) return;
        modeVotes.put(player.getUniqueId(), choice);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        open(player, Phase.DIFFICULTY);
        refreshOpenMenus();
    }

    private void castDifficulty(Player player, BingoDifficulty choice) {
        boolean hasSoloTeam = teams.stream().anyMatch(team -> team.getMembers().size() <= 1);
        if (choice == BingoDifficulty.EXTREME && hasSoloTeam) return;
        difficultyVotes.put(player.getUniqueId(), choice);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        open(player, Phase.LINES);
        refreshOpenMenus();
    }

    private void castLines(Player player, int lines) {
        if (lines < 1 || lines > 5) return;
        lineVotes.put(player.getUniqueId(), lines);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        refreshOpenMenus();
    }

    private void castGenesis(Player player, Material material) {
        if (!genesisOptions.getOrDefault(player.getUniqueId(), List.of()).contains(material))
            return;
        genesisPicks.put(player.getUniqueId(), material);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        refreshOpenMenus();
    }

    private void navigate(Player player, DailyBingoVoteMenu menu, int direction) {
        Phase[] pages = {Phase.MODE, Phase.DIFFICULTY, Phase.LINES};
        int current = java.util.Arrays.asList(pages).indexOf(menu.phase);
        if (current < 0) return;
        int next = Math.max(0, Math.min(pages.length - 1, current + direction));
        if (next != current) open(player, pages[next]);
    }

    private void beginPhase(Phase next) {
        if (next != Phase.MODE && next != Phase.GENESIS)
            throw new IllegalArgumentException(
                    "Only the initial vote page or Genesis may start a timer");
        phase = next;
        CompletableFuture<BingoVariantRules> currentVote = result;
        secondsLeft =
                next == Phase.GENESIS
                        ? plugin.getGameManager().getBingoManager().dailyGenesisSeconds()
                        : plugin.getGameManager().getBingoManager().dailyVoteSeconds();
        phaseDuration = secondsLeft;
        MessageService messages = MessageService.global();
        int phaseSeconds = secondsLeft;
        broadcast(
                messages.tr(
                        switch (next) {
                            case MODE -> "vote.started";
                            case DIFFICULTY, LINES ->
                                    throw new IllegalStateException(
                                            "Intermediate vote pages do not have timers");
                            case GENESIS -> "genesis.started";
                        },
                        phaseSeconds));
        updateBossBar();
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null) open(player, next);
        }
        if (timer != null) timer.cancel();
        timer =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    synchronized (DailyBingoVoteController.this) {
                                        if (result != currentVote
                                                || currentVote.isDone()
                                                || phase != next) return;
                                        long online =
                                                voters.stream()
                                                        .filter(
                                                                uuid ->
                                                                        Bukkit.getPlayer(uuid)
                                                                                != null)
                                                        .count();
                                        if (!daily.isDailyLobby() || online < 2) {
                                            broadcast(
                                                    MessageService.global()
                                                            .tr("vote.cancelled-insufficient"));
                                            cancel();
                                            return;
                                        }
                                        secondsLeft--;
                                        updateBossBar();
                                        if (secondsLeft <= 0) finishPhase();
                                        else if (secondsLeft <= 5 || secondsLeft == 10)
                                            broadcast(
                                                    MessageService.global()
                                                            .tr("vote.remaining", secondsLeft));
                                    }
                                },
                                20L,
                                20L);
    }

    private void finishPhase() {
        if (timer != null) timer.cancel();
        timer = null;
        switch (phase) {
            case MODE, DIFFICULTY, LINES -> {
                List<ModeChoice> choices = new ArrayList<>(List.of(ModeChoice.values()));
                if (teams.size() < 4) choices.remove(ModeChoice.POINTS);
                ModeChoice winner = winner(choices, modeVotes);
                boolean randomized = winner == ModeChoice.RANDOM;
                if (randomized) {
                    choices.remove(ModeChoice.RANDOM);
                    winner = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                }
                selectedMode = BingoMode.valueOf(winner.name());
                MessageService messages = MessageService.global();
                broadcast(
                        messages.tr(
                                randomized ? "vote.result_random" : "vote.result",
                                messages.tr(
                                        "mode."
                                                + selectedMode.name().toLowerCase(Locale.ROOT)
                                                + ".name")));
                List<BingoDifficulty> difficultyChoices =
                        new ArrayList<>(List.of(BingoDifficulty.values()));
                if (teams.stream().anyMatch(team -> team.getMembers().size() <= 1))
                    difficultyChoices.remove(BingoDifficulty.EXTREME);
                selectedDifficulty = winner(difficultyChoices, difficultyVotes);
                broadcast(
                        messages.tr(
                                "difficulty_vote.result",
                                messages.tr(
                                        "card_difficulty."
                                                + selectedDifficulty.name().toLowerCase(Locale.ROOT)
                                                + ".name")));
                int lines =
                        selectedMode == BingoMode.SPEEDRUN
                                ? winner(List.of(1, 2, 3, 4, 5), lineVotes)
                                : 1;
                if (selectedMode == BingoMode.SPEEDRUN)
                    broadcast(messages.tr("lines_vote.result", lines));
                complete(lines);
            }
            case GENESIS -> finishGenesis();
        }
    }

    static Title remixTitle(MessageService messages, String name, String description) {
        return Title.title(
                ComponentTemplates.render(
                        MessageConfig.codeText("daily-bingo-vote-controller.remix-title"),
                        Map.of("%title%", LegacyText.component(messages.tr("genesis.title")))),
                ComponentTemplates.render(
                        MessageConfig.codeText("daily-bingo-vote-controller.remix-subtitle"),
                        Map.of(
                                "%name%", LegacyText.component(name),
                                "%description%", LegacyText.component(description))));
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
            String remixName =
                    remixMessages.tr("remix." + remix.name().toLowerCase(Locale.ROOT) + ".name");
            String remixDescription =
                    remixMessages.tr(
                            "remix." + remix.name().toLowerCase(Locale.ROOT) + ".description");
            broadcast(remixMessages.tr("genesis.triggered", remixName, remixDescription));
            for (UUID voter : voters) {
                Player player = Bukkit.getPlayer(voter);
                if (player != null)
                    player.showTitle(remixTitle(remixMessages, remixName, remixDescription));
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
                if (!options.isEmpty())
                    picked = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            }
            if (picked != null && !picks.contains(picked.name())) picks.add(picked.name());
        }
        finishResult(
                new BingoVariantRules(
                        selectedMode,
                        selectedDifficulty,
                        selectedWinLines,
                        BingoRemix.GENESIS,
                        picks));
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
                default -> {}
            }
        }
        List<Material> source = new ArrayList<>(candidates);
        for (UUID voter : voters) {
            java.util.Collections.shuffle(source);
            genesisOptions.put(
                    voter,
                    List.copyOf(
                            source.subList(
                                    0,
                                    Math.min(
                                            plugin.getGameManager()
                                                    .getBingoManager()
                                                    .dailyGenesisOptions(),
                                            source.size()))));
        }
    }

    private <T> T winner(Collection<T> candidates, Map<UUID, T> votes) {
        Map<T, Integer> counts = new HashMap<>();
        candidates.forEach(candidate -> counts.put(candidate, 0));
        votes.values()
                .forEach(
                        vote -> {
                            if (counts.containsKey(vote)) counts.merge(vote, 1, Integer::sum);
                        });
        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<T> tied =
                candidates.stream().filter(candidate -> counts.get(candidate) == best).toList();
        return tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
    }

    private void open(Player player, Phase votePhase) {
        if (result == null
                || result.isDone()
                || !voters.contains(player.getUniqueId())
                || !player.isOnline()
                || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        DailyBingoVoteMenu menu = new DailyBingoVoteMenu(this, player, result, votePhase);
        render(menu);
        player.openInventory(menu.getInventory());
    }

    private void render(DailyBingoVoteMenu menu) {
        UUID viewer = menu.connection.getUniqueId();
        List<?> choices =
                switch (menu.phase) {
                    case MODE -> List.of(ModeChoice.values());
                    case DIFFICULTY -> List.of(BingoDifficulty.values());
                    case LINES -> List.of(1, 2, 3, 4, 5);
                    case GENESIS -> genesisOptions.getOrDefault(viewer, List.of());
                };
        menu.render(choices, choice -> optionItem(menu.phase, viewer, choice));
    }

    private ItemStack optionItem(Phase page, UUID viewer, Object choice) {
        MessageService messages = MessageService.global();
        if (page == Phase.GENESIS) {
            Material material = (Material) choice;
            boolean selected = material == genesisPicks.get(viewer);
            return DailyBingoVoteMenu.option(
                    "genesis",
                    selected ? "selected" : "idle",
                    Map.of(
                            "name",
                            material.key().asString(),
                            "hint",
                            messages.tr(selected ? "vote.menu_picked" : "genesis.menu_hint")),
                    List.of(),
                    material,
                    1,
                    0);
        }
        String id;
        String prefix;
        String name;
        List<String> description;
        Map<UUID, ?> votes;
        boolean locked = false;
        int light = 0;
        switch (page) {
            case MODE -> {
                id = "modes." + ((ModeChoice) choice).name().toLowerCase(Locale.ROOT);
                String key = "mode." + ((ModeChoice) choice).name().toLowerCase(Locale.ROOT);
                prefix = "vote";
                name = messages.tr(key + ".name");
                description = messages.lines(key + ".lore");
                votes = modeVotes;
                locked = choice == ModeChoice.POINTS && teams.size() < 4;
            }
            case DIFFICULTY -> {
                id = "difficulties." + ((BingoDifficulty) choice).name().toLowerCase(Locale.ROOT);
                String key =
                        "card_difficulty."
                                + ((BingoDifficulty) choice).name().toLowerCase(Locale.ROOT);
                prefix = "difficulty_vote";
                name = messages.tr(key + ".name");
                description = messages.lines(key + ".lore");
                votes = difficultyVotes;
                locked =
                        choice == BingoDifficulty.EXTREME
                                && teams.stream().anyMatch(team -> team.getMembers().size() <= 1);
            }
            case LINES -> {
                id = "lines";
                prefix = "lines_vote";
                light = (Integer) choice;
                name = messages.tr("lines_vote.menu_option", light);
                description = List.of(messages.tr("lines_vote.menu_desc", light));
                votes = lineVotes;
            }
            case GENESIS -> throw new IllegalStateException("Genesis is rendered separately");
            default -> throw new IllegalStateException("Unknown phase: " + page);
        }
        int count = (int) votes.values().stream().filter(choice::equals).count();
        boolean picked = choice.equals(votes.get(viewer));
        return DailyBingoVoteMenu.option(
                id,
                locked ? "locked" : picked ? "selected" : "idle",
                Map.of(
                        "name",
                        name,
                        "count",
                        count,
                        "count-text",
                        messages.tr(prefix + ".menu_count", count),
                        "hint",
                        messages.tr(prefix + (picked ? ".menu_picked" : ".menu_hint")),
                        "locked-text",
                        locked ? messages.tr(prefix + ".menu_locked") : ""),
                description,
                Material.PAPER,
                locked ? 1 : count,
                light);
    }

    private void refreshOpenMenus() {
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null
                    && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof DailyBingoVoteMenu menu
                    && currentView(player, menu)) render(menu);
        }
    }

    private void closeMenus() {
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null
                    && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof DailyBingoVoteMenu menu
                    && menu.owner == this
                    && menu.vote == result
                    && player == menu.connection
                    && player.getOpenInventory().getTopInventory() == menu.getInventory())
                player.closeInventory();
        }
    }

    private void broadcast(String message) {
        daily.broadcastDaily(voters, message);
    }

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
        phaseDuration = 0;
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
        if (result == null
                || result.isDone()
                || phase == null
                || !voters.contains(player.getUniqueId())
                || !player.isOnline()
                || Bukkit.getPlayer(player.getUniqueId()) != player) return false;
        open(player, phase);
        return true;
    }

    private void updateBossBar() {
        if (result == null || result.isDone() || phase == null) return;
        MessageService messages = MessageService.global();
        String time =
                DurationText.bossBarSeconds(
                        secondsLeft,
                        messages.tr("vote.bossbar-time.seconds"),
                        messages.tr("vote.bossbar-time.minutes-seconds"));
        String title = messages.tr("vote.bossbar", time);
        String legacyTitle = LEGACY.serialize(LegacyText.component(title));
        if (bossBar == null)
            bossBar = Bukkit.createBossBar(legacyTitle, BarColor.PURPLE, BarStyle.SOLID);
        else bossBar.setTitle(legacyTitle);
        bossBar.setProgress(Math.clamp(secondsLeft / (double) Math.max(1, phaseDuration), 0D, 1D));
        for (UUID voter : voters) {
            Player player = Bukkit.getPlayer(voter);
            if (player != null && !bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        }
    }
}
