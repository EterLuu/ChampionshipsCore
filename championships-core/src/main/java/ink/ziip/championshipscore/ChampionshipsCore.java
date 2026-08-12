package ink.ziip.championshipscore;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.daily.DailyManager;
import ink.ziip.championshipscore.api.daily.DailyStatsManager;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.vote.VoteManager;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;
import ink.ziip.championshipscore.integration.papi.PlaceholderManager;
import ink.ziip.championshipscore.util.glow.GlowingEntities;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.world.WorldManager;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;
import ink.ziip.championshipscore.listener.ListenerManager;
import ink.ziip.championshipscore.logging.CCLogManager;
import ink.ziip.championshipscore.presentation.sidebar.CoreSidebarManager;
import ink.ziip.championshipscore.command.CommandManager;
import ink.ziip.championshipscore.configuration.manager.ConfigurationManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.database.DatabaseManager;
import ink.ziip.championshipscore.redis.RedisManager;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Level;

@Getter
public final class ChampionshipsCore extends JavaPlugin {
    @Getter
    private static ChampionshipsCore instance;
    private boolean loaded;
    private TeamManager teamManager;
    private PlayerManager playerManager;
    private ListenerManager listenerManager;
    private ConfigurationManager configurationManager;
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private CommandManager commandManager;
    private WorldEditManager worldEditManager;
    private GameManager gameManager;
    private PlayerVisibilityManager visibilityManager;
    private RemoteBingoManager remoteBingoManager;
    private RankManager rankManager;
    private WorldManager worldManager;
    private GlowingEntities glowingEntities;
    private PlaceholderManager placeholderManager;
    private VoteManager voteManager;
    private ScheduleManager scheduleManager;
    private PrepareSessionManager prepareSessionManager;
    private CoreSidebarManager sidebarManager;
    private DailyManager dailyManager;
    private DailyStatsManager dailyStatsManager;
    private CCLogManager logManager;
    @Getter(AccessLevel.NONE)
    private final Set<BaseManager> startedManagers = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void onEnable() {
        instance = this;
        loaded = true;
        logManager = CCLogManager.install(this);

        java.util.List<String> missingDependencies = java.util.stream.Stream.of(
                        "PlaceholderAPI", "ProtocolLib", "FastAsyncWorldEdit")
                .filter(name -> Bukkit.getPluginManager().getPlugin(name) == null)
                .toList();
        if (!missingDependencies.isEmpty()) {
            loaded = false;
            String message = Utils.formatModuleLog("Bootstrap", "依赖",
                    "缺少必要插件=" + String.join(", ", missingDependencies) + "，ChampionshipsCore 已关闭");
            if (logManager != null) logManager.important(message);
            else getLogger().severe(message);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        configurationManager = new ConfigurationManager(this);
        // GUI definitions can be referenced by static menu descriptors while the remaining managers
        // are constructed, so language resources must be ready before those classes initialize.
        loadManager(configurationManager);
        databaseManager = new DatabaseManager(this);
        redisManager = new RedisManager(this);
        playerManager = new PlayerManager(this);
        listenerManager = new ListenerManager(this);
        commandManager = new CommandManager(this);
        teamManager = new TeamManager(this);
        worldEditManager = new WorldEditManager(this);
        gameManager = new GameManager(this);
        visibilityManager = new PlayerVisibilityManager(this);
        remoteBingoManager = new RemoteBingoManager(this);
        prepareSessionManager = new PrepareSessionManager(this);
        rankManager = new RankManager(this);
        worldManager = new WorldManager(this);
        glowingEntities = new GlowingEntities(this);
        placeholderManager = new PlaceholderManager(this);
        voteManager = new VoteManager(this);
        scheduleManager = new ScheduleManager(this);
        sidebarManager = new CoreSidebarManager(this);
        dailyStatsManager = new DailyStatsManager(this);
        dailyManager = new DailyManager(this, dailyStatsManager);

        // Plugin startup logic
        loadManager(databaseManager);
        loadManager(listenerManager);
        loadManager(worldManager);

        loadManager(playerManager);
        loadManager(teamManager);
        loadManager(rankManager);
        loadManager(redisManager);

        loadManager(worldEditManager);

        loadManager(gameManager);
        loadManager(visibilityManager);
        loadManager(remoteBingoManager);
        loadManager(dailyStatsManager);
        loadManager(dailyManager);

        loadManager(prepareSessionManager);

        loadManager(commandManager);
        loadManager(placeholderManager);
        loadManager(voteManager);
        loadManager(scheduleManager);
        loadManager(sidebarManager);

        String readyMessage = Utils.formatModuleLog("Bootstrap", "启动", "加载完成 | 模式=" + CCConfig.MODE);
        if (logManager != null) logManager.important(readyMessage);
        else getLogger().log(Level.INFO, readyMessage);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        unloadManager(sidebarManager);
        unloadManager(dailyManager);
        unloadManager(dailyStatsManager);
        unloadManager(remoteBingoManager);
        unloadManager(redisManager);
        unloadManager(gameManager);
        unloadManager(visibilityManager);
        unloadManager(prepareSessionManager);
        unloadManager(rankManager);

        unloadManager(listenerManager);
        unloadManager(playerManager);
        unloadManager(teamManager);
        unloadManager(commandManager);

        unloadManager(worldEditManager);
        unloadManager(worldManager);

        loaded = false;

        unloadManager(configurationManager);
        unloadManager(databaseManager);
        unloadManager(placeholderManager);
        unloadManager(voteManager);
        unloadManager(scheduleManager);
        if (glowingEntities != null) glowingEntities.disable();

        if (logManager != null) {
            logManager.important(Utils.formatModuleLog("Bootstrap", "停止", "插件已安全卸载"));
            logManager.close();
            logManager = null;
        }
    }

    private void loadManager(@NotNull BaseManager manager) {
        startedManagers.add(manager);
        manager.load();
    }

    private void unloadManager(BaseManager manager) {
        if (manager != null && startedManagers.remove(manager)) {
            manager.unload();
        }
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
