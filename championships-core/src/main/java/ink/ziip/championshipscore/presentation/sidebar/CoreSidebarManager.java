package ink.ziip.championshipscore.presentation.sidebar;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxMatch;
import ink.ziip.championshipscore.api.game.bingo.BingoArea;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagMatch;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.SharedSidebar;
import ink.ziip.championshipscore.util.Utils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/** Owns selection, rendering and cleanup for every Core-side sidebar. */
public final class CoreSidebarManager extends BaseManager implements Listener {
    private final Map<UUID, RenderedSidebar> rendered = new LinkedHashMap<>();
    private volatile SidebarConfiguration configuration;
    private SharedSidebar sidebar;
    private BukkitTask refreshTask;

    public CoreSidebarManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        File file = new File(plugin.getDataFolder(), "scoreboards.yml");
        if (!file.isFile()) plugin.saveResource("scoreboards.yml", false);
        reload();
        SidebarConfiguration config = configuration;
        Component initialTitle = config == null ? Component.text("SCC") : Utils.toComponent(config.lobby().title());
        sidebar = new SharedSidebar("cc_sidebar", initialTitle,
                warning -> plugin.getLogger().warning(Utils.formatModuleLog("Sidebar", "发包", warning)));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        scheduleRefresh();
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    public void unload() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        HandlerList.unregisterAll(this);
        rendered.clear();
        if (sidebar != null) sidebar.hideAll();
        sidebar = null;
    }

    /** Atomically replaces the active snapshot; malformed edits leave the last good configuration live. */
    public boolean reload() {
        File file = new File(plugin.getDataFolder(), "scoreboards.yml");
        try {
            SidebarConfiguration loaded = SidebarConfiguration.load(file);
            configuration = loaded;
            rendered.clear();
            if (sidebar != null && !loaded.enabled()) sidebar.hideAll();
            if (sidebar != null) scheduleRefresh();
            plugin.getLogger().info(Utils.formatModuleLog("Sidebar", "配置", "已加载 scoreboards.yml"));
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Sidebar", "配置",
                    "scoreboards.yml 无效，保留上一份有效配置 | " + exception.getMessage()), exception);
            return false;
        }
    }

    public Map<String, String> bingoWorkerPresentation() {
        SidebarConfiguration config = configuration;
        return config == null ? Map.of() : config.bingoWorkerFields();
    }

    /** Explicit invalidation hook used by game and prepare lifecycle transitions. */
    public void invalidate(@NotNull Player player) {
        rendered.remove(player.getUniqueId());
        if (player.isOnline()) refresh(player);
    }

    public void invalidateAll() {
        rendered.clear();
        if (sidebar != null && plugin.isEnabled()) Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    private void scheduleRefresh() {
        if (refreshTask != null) refreshTask.cancel();
        SidebarConfiguration config = configuration;
        if (config == null || !config.enabled() || !plugin.isEnabled()) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 1L, config.updateIntervalTicks());
    }

    private void refresh(Player player) {
        SharedSidebar target = sidebar;
        SidebarConfiguration config = configuration;
        if (target == null || config == null || !config.enabled() || !player.isOnline()) {
            hide(player);
            return;
        }
        RenderedSidebar next = render(player, config);
        RenderedSidebar previous = rendered.get(player.getUniqueId());
        if (next.equals(previous) && target.isShown(player)) return;
        if (!target.isShown(player)) target.show(player);
        target.render(player, next.title(), next.lines());
        rendered.put(player.getUniqueId(), next);
    }

    private RenderedSidebar render(Player player, SidebarConfiguration config) {
        BaseGameInstance instance = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        boolean spectator = false;
        if (instance == null) {
            instance = plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId());
            spectator = instance != null;
        }
        if (instance instanceof ParkourWarriorTeamArea parkourWarrior
                && parkourWarrior.isFinishedPlayer(player.getUniqueId())) {
            spectator = true;
        }
        if (instance != null) return renderGame(player, instance, spectator, config);

        PrepareSession session = plugin.getPrepareSessionManager().getSession(player);
        if (session != null) return renderEdit(player, session, config);

        List<MapDescriptor> maps = mapsIn(player.getWorld());
        if (player.hasPermission("cc.admin") && !maps.isEmpty()) {
            return renderMapStatus(player, maps, config);
        }
        return renderTemplate(player, config.lobby(), Map.of());
    }

    private RenderedSidebar renderGame(Player player, BaseGameInstance instance, boolean spectator,
                                        SidebarConfiguration config) {
        SidebarConfiguration.GameTemplate game = config.game(instance.getGameTypeEnum());
        SidebarConfiguration.Template template = game.templateFor(instance.getGameConfig().getConfigName());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("game.name", instance.getGameTypeEnum().toString());
        values.put("game.map", Objects.toString(instance.getGameConfig().getConfigName(), "-"));
        values.put("game.world", Objects.toString(instance.getWorldName(), "-"));
        values.put("game.status", instance.getGameStageEnum().toString());
        values.put("viewer.role", spectator ? "旁观" : "参赛");
        ChampionshipTeam viewerTeam = plugin.getTeamManager().getTeamByPlayer(player);
        values.put("viewer.team", viewerTeam == null ? "&7旁观者" : viewerTeam.getColoredName());
        if (instance instanceof BattleBoxArea area) {
            BattleBoxMatch match = area.currentMatch();
            putMatchup(values, match == null ? null : match.getRight(), match == null ? null : match.getLeft());
        } else if (instance instanceof ParkourTagArea area) {
            ParkourTagMatch match = area.currentMatch();
            putMatchup(values, match == null ? null : match.getRight(), match == null ? null : match.getLeft());
        }

        List<String> lines = new ArrayList<>();
        for (String raw : template.lines()) {
            if ("{ranking}".equals(raw) && instance instanceof BingoArea bingo) {
                lines.addAll(renderLocalBingoRanking(player, bingo, game));
                continue;
            }
            if ("{ranking}".equals(raw) && instance instanceof SnowballShowdownTeamArea snowball) {
                lines.addAll(renderLocalSnowballRanking(player, snowball, game));
                continue;
            }
            String line = raw;
            if (instance instanceof BingoArea bingo) {
                BingoRound round = bingo.getRound();
                int tasks = viewerTeam == null || round == null ? 0 : round.completedCount(viewerTeam);
                line = line.replace("{viewer.tasks}", Integer.toString(tasks));
            }
            lines.add(line);
        }
        return renderRaw(player, template.title(), lines, values, config.papiFallback());
    }

    private static void putMatchup(Map<String, String> values, ChampionshipTeam right, ChampionshipTeam left) {
        values.put("match.right", right == null ? "&7待定" : right.getColoredName());
        values.put("match.left", left == null ? "&7待定" : left.getColoredName());
    }

    private List<String> renderLocalBingoRanking(Player player, BingoArea bingo,
                                                 SidebarConfiguration.GameTemplate template) {
        BingoRound round = bingo.getRound();
        if (round == null) return List.of("&7暂无排行");
        List<ChampionshipTeam> ranked = round.rankedTeams();
        ChampionshipTeam viewerTeam = plugin.getTeamManager().getTeamByPlayer(player);
        List<ChampionshipTeam> selected = selectRankingRows(ranked, viewerTeam);
        List<String> result = new ArrayList<>();
        for (ChampionshipTeam team : selected) {
            int position = ranked.indexOf(team) + 1;
            String raw = team.equals(viewerTeam) ? template.ownRankingLine() : template.rankingLine();
            String line = raw.replace("{rank.team-color}", team.getColorCode())
                    .replace("{rank.position}", Integer.toString(position))
                    .replace("{rank.team}", team.getName())
                    .replace("{rank.score}", Integer.toString(round.score(team)))
                    .replace("{rank.tasks}", Integer.toString(round.completedCount(team)));
            result.add(line);
        }
        return result;
    }

    private List<String> renderLocalSnowballRanking(Player player, SnowballShowdownTeamArea snowball,
                                                    SidebarConfiguration.GameTemplate template) {
        List<ChampionshipTeam> ranked = snowball.getRankedTeams();
        if (ranked.isEmpty()) return List.of("&7暂无排行");
        ChampionshipTeam viewerTeam = plugin.getTeamManager().getTeamByPlayer(player);
        List<String> result = new ArrayList<>();
        for (ChampionshipTeam team : selectRankingRows(ranked, viewerTeam)) {
            int position = ranked.indexOf(team) + 1;
            String raw = team.equals(viewerTeam) ? template.ownRankingLine() : template.rankingLine();
            result.add(raw.replace("{rank.team-color}", team.getColorCode())
                    .replace("{rank.position}", Integer.toString(position))
                    .replace("{rank.team}", team.getName())
                    .replace("{rank.score}", Integer.toString(snowball.getTeamScore(team))));
        }
        return result;
    }

    static <T> List<T> selectRankingRows(List<T> ranked, T viewerEntry) {
        List<T> selected = new ArrayList<>(ranked.subList(0, Math.min(8, ranked.size())));
        if (viewerEntry != null && ranked.contains(viewerEntry) && !selected.contains(viewerEntry)) {
            selected.add(viewerEntry);
        }
        return selected;
    }

    private RenderedSidebar renderEdit(Player player, PrepareSession session, SidebarConfiguration config) {
        List<String> errors = session.getFlow().validate(session);
        BaseGameConfig map = session.getTarget().config();
        boolean correctWorld = session.getFlow().isInCorrectWorld(player, session.getTarget());
        boolean complete = errors.isEmpty();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("edit.game", session.getGameType().toString());
        values.put("edit.map", session.getAreaName());
        values.put("edit.world", session.getTarget().worldName().isBlank() ? "&c尚未绑定" : session.getTarget().worldName());
        values.put("edit.world-status", correctWorld ? "&a正确" : "&c错误");
        values.put("edit.done", Integer.toString(session.configDone()));
        values.put("edit.total", Integer.toString(session.configTotal()));
        values.put("edit.errors", Integer.toString(errors.size()));
        values.put("edit.progress-color", complete ? "&a" : "&e");
        values.put("edit.error-color", complete ? "&a" : "&c");
        values.put("edit.revision", Integer.toString(Objects.requireNonNullElse(map.getPrepareRevision(), 0)));
        values.put("edit.publish-status", publishStatus(map));
        values.put("edit.warning", editWarning(map, complete, correctWorld));
        return renderTemplate(player, config.mapEdit(), values);
    }

    private RenderedSidebar renderMapStatus(Player player, List<MapDescriptor> maps,
                                             SidebarConfiguration config) {
        MapDescriptor primary = maps.getFirst();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("world.name", player.getWorld().getName());
        values.put("map.count", Integer.toString(maps.size()));
        values.put("map.publish-status", maps.stream().allMatch(entry -> entry.config().isPrepareReady())
                ? "&a全部已发布" : "&e存在草稿或未保存修改");
        values.put("map.runtime-status", maps.stream().map(entry -> entry.instance().getGameStageEnum().toString())
                .distinct().reduce((left, right) -> left + "&7 / " + right).orElse("&7未知"));
        values.put("map.revision", Integer.toString(Objects.requireNonNullElse(primary.config().getPrepareRevision(), 0)));

        List<Component> lines = new ArrayList<>();
        for (String raw : config.mapStatus().lines()) {
            if ("{map.entries}".equals(raw)) {
                for (MapDescriptor descriptor : maps.stream().limit(4).toList()) {
                    lines.add(Utils.toComponent("  " + gameColor(descriptor.game()) + descriptor.game()
                            + " &8/ " + publishGlyph(descriptor.config()) + "&f" + descriptor.mapName()));
                }
                continue;
            }
            lines.add(resolve(player, raw, values, false));
        }
        return normalize(resolve(player, config.mapStatus().title(), values, false), lines);
    }

    private List<MapDescriptor> mapsIn(World world) {
        Map<String, MapDescriptor> unique = new LinkedHashMap<>();
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            if (!plugin.getGameManager().isGameManagerLoaded(gameType)) continue;
            BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameType);
            if (manager == null) continue;
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (!world.getName().equals(instance.getWorldName())) continue;
                String mapName = Objects.toString(instance.getGameConfig().getConfigName(), instance.getGameConfig().getAreaName());
                unique.putIfAbsent(gameType.name() + '\u0000' + mapName.toLowerCase(java.util.Locale.ROOT),
                        new MapDescriptor(gameType, mapName, instance.getGameConfig(), instance));
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing((MapDescriptor value) -> value.game().ordinal())
                        .thenComparing(MapDescriptor::mapName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private RenderedSidebar renderTemplate(Player player, SidebarConfiguration.Template template,
                                           Map<String, String> values) {
        SidebarConfiguration config = configuration;
        boolean papi = config != null && config.papiFallback();
        return renderRaw(player, template.title(), template.lines(), values, papi);
    }

    /** Runs the PAPI compatibility pass once per viewer/snapshot rather than once per line. */
    private RenderedSidebar renderRaw(Player player, String title, List<String> rawLines,
                                      Map<String, String> values, boolean papi) {
        Map<String, String> effectiveValues = withViewerValues(player, values);
        StringBuilder batch = new StringBuilder(replaceValues(title, effectiveValues));
        for (String line : rawLines) batch.append('\u0000').append(replaceValues(line, effectiveValues));
        String renderedBatch = batch.toString();
        if (papi && renderedBatch.indexOf('%') >= 0) {
            renderedBatch = PlaceholderAPI.setPlaceholders(player, renderedBatch);
        }
        String[] parts = renderedBatch.split(Character.toString(0), -1);
        Component renderedTitle = Utils.toComponent(parts[0]);
        List<Component> lines = new ArrayList<>(rawLines.size());
        for (int index = 1; index < parts.length; index++) lines.add(Utils.toComponent(parts[index]));
        return normalize(renderedTitle, lines);
    }

    private static String replaceValues(String raw, Map<String, String> values) {
        String value = raw;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return value;
    }

    private Component resolve(Player player, String raw, Map<String, String> values, boolean papi) {
        String value = replaceValues(raw, withViewerValues(player, values));
        if (papi && value.indexOf('%') >= 0) value = PlaceholderAPI.setPlaceholders(player, value);
        return Utils.toComponent(value);
    }

    private Map<String, String> withViewerValues(Player player, Map<String, String> values) {
        ChampionshipTeam viewerTeam = plugin.getTeamManager().getTeamByPlayer(player);
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("viewer.team", viewerTeam == null ? "&7旁观者" : viewerTeam.getColoredName());
        effective.putAll(values);
        return effective;
    }

    private static RenderedSidebar normalize(Component title, List<Component> requested) {
        if (requested.size() <= SidebarConfiguration.MAX_LINES) return new RenderedSidebar(title, List.copyOf(requested));
        return new RenderedSidebar(title, List.copyOf(requested.subList(0, SidebarConfiguration.MAX_LINES)));
    }

    private void hide(Player player) {
        rendered.remove(player.getUniqueId());
        if (sidebar != null) sidebar.hide(player);
    }

    private static String publishStatus(BaseGameConfig config) {
        if (config.isPrepareReady()) return "&a已发布";
        if (config.isPreparePublished()) return "&e有未发布修改";
        return "&c尚未发布";
    }

    private static String editWarning(BaseGameConfig config, boolean complete, boolean correctWorld) {
        if (!correctWorld) return "&c&l✗ 当前不在绑定世界，无法安全捕获配置";
        if (!complete) return "&c&l✗ 地图配置尚未完成";
        if (config.isPrepareDirty()) return "#ff6b26&l⚠ 修改尚未发布，不能用于比赛";
        return "&a&l✓ 配置完整且已发布";
    }

    private static String publishGlyph(BaseGameConfig config) {
        return config.isPrepareReady() ? "&a✓ " : config.isPreparePublished() ? "&e⚠ " : "&c✗ ";
    }

    private static String gameColor(GameTypeEnum game) {
        return switch (game.ordinal() % 6) {
            case 0 -> "#ff7373";
            case 1 -> "#64b5f6";
            case 2 -> "#81c784";
            case 3 -> "#ba68c8";
            case 4 -> "#ffb74d";
            default -> "#4dd0e1";
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) invalidate(event.getPlayer());
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        invalidate(event.getPlayer());
    }

    private record RenderedSidebar(Component title, List<Component> lines) {
    }

    private record MapDescriptor(GameTypeEnum game, String mapName, BaseGameConfig config,
                                 BaseGameInstance instance) {
    }
}
