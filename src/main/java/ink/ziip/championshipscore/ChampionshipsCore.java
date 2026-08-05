package ink.ziip.championshipscore;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.vote.VoteManager;
import ink.ziip.championshipscore.integration.papi.PlaceholderManager;
import ink.ziip.championshipscore.util.glow.GlowingEntities;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.world.WorldManager;
import ink.ziip.championshipscore.integration.worldedit.WorldEditManager;
import ink.ziip.championshipscore.listener.ListenerManager;
import ink.ziip.championshipscore.logging.CCLogManager;
import ink.ziip.championshipscore.command.CommandManager;
import ink.ziip.championshipscore.configuration.manager.ConfigurationManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.database.DatabaseManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
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
    private CommandManager commandManager;
    private WorldEditManager worldEditManager;
    private GameManager gameManager;
    private RankManager rankManager;
    private WorldManager worldManager;
    private GlowingEntities glowingEntities;
    private PlaceholderManager placeholderManager;
    private VoteManager voteManager;
    private ScheduleManager scheduleManager;
    private PrepareSessionManager prepareSessionManager;
    private CCLogManager logManager;

    @Override
    public void onEnable() {
        instance = this;
        loaded = true;
        logManager = CCLogManager.install(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning(Utils.formatModuleLog("Bootstrap", "依赖", "缺少 PlaceholderAPI，插件已停用"));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().warning(Utils.formatModuleLog("Bootstrap", "依赖", "缺少 ProtocolLib，插件已停用"));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().warning(Utils.formatModuleLog("Bootstrap", "依赖", "缺少 FastAsyncWorldEdit，插件已停用"));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        configurationManager = new ConfigurationManager(this);
        databaseManager = new DatabaseManager(this);
        playerManager = new PlayerManager(this);
        listenerManager = new ListenerManager(this);
        commandManager = new CommandManager(this);
        teamManager = new TeamManager(this);
        worldEditManager = new WorldEditManager(this);
        gameManager = new GameManager(this);
        prepareSessionManager = new PrepareSessionManager(this);
        rankManager = new RankManager(this);
        worldManager = new WorldManager(this);
        glowingEntities = new GlowingEntities(this);
        placeholderManager = new PlaceholderManager(this);
        voteManager = new VoteManager(this);
        scheduleManager = new ScheduleManager(this);

        // Plugin startup logic
        configurationManager.load();
        databaseManager.load();
        listenerManager.load();
        worldManager.load();

        playerManager.load();
        teamManager.load();
        rankManager.load();

        worldEditManager.load();

        gameManager.load();

        prepareSessionManager.load();

        commandManager.load();
        placeholderManager.load();
        voteManager.load();
        scheduleManager.load();

        String readyMessage = Utils.formatModuleLog("Bootstrap", "启动", "加载完成 | 模式=" + CCConfig.MODE);
        if (logManager != null) logManager.important(readyMessage);
        else getLogger().log(Level.INFO, readyMessage);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        gameManager.unload();
        prepareSessionManager.unload();
        rankManager.unload();

        listenerManager.unload();
        playerManager.unload();
        teamManager.unload();
        commandManager.unload();

        worldEditManager.unload();
        worldManager.unload();

        loaded = false;

        configurationManager.unload();
        databaseManager.unload();
        placeholderManager.unload();
        voteManager.unload();
        scheduleManager.unload();
        glowingEntities.disable();

        if (logManager != null) {
            logManager.important(Utils.formatModuleLog("Bootstrap", "停止", "插件已安全卸载"));
            logManager.close();
            logManager = null;
        }
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
