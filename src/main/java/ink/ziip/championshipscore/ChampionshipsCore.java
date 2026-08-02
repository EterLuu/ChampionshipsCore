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
    private volatile boolean loaded;
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

    @Override
    public void onEnable() {
        instance = this;
        loaded = true;

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
        configurationManager = new ConfigurationManager(this);
        databaseManager = new DatabaseManager(this);
        playerManager = new PlayerManager(this);
        listenerManager = new ListenerManager(this);
        commandManager = new CommandManager(this);
        teamManager = new TeamManager(this);
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            worldEditManager = new WorldEditManager(this);
        } else {
            getLogger().warning(Utils.formatModuleLog("Bootstrap", "可选依赖",
                    "FastAsyncWorldEdit 不可用；游戏运行保持启用，需要 WorldEdit 的地图编辑功能已关闭"));
        }
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

        if (worldEditManager != null) worldEditManager.load();

        gameManager.load();

        prepareSessionManager.load();

        commandManager.load();
        placeholderManager.load();
        voteManager.load();
        scheduleManager.load();

        getLogger().log(Level.INFO, Utils.formatModuleLog("Bootstrap", "启动", "模式=" + CCConfig.MODE));
    }

    @Override
    public void onDisable() {
        loaded = false;
        // Stop producers first so Folia region tasks cannot race managers or the database while
        // their backing state is being torn down.
        if (scheduleManager != null) scheduleManager.unload();
        if (voteManager != null) voteManager.unload();
        if (listenerManager != null) listenerManager.unload();
        if (prepareSessionManager != null) prepareSessionManager.unload();
        if (gameManager != null) gameManager.unload();
        if (rankManager != null) rankManager.unload();

        if (placeholderManager != null) placeholderManager.unload();
        if (glowingEntities != null) glowingEntities.disable();

        if (commandManager != null) commandManager.unload();
        if (playerManager != null) playerManager.unload();
        if (teamManager != null) teamManager.unload();

        if (worldEditManager != null) worldEditManager.unload();
        if (worldManager != null) worldManager.unload();

        if (databaseManager != null) databaseManager.unload();
        if (configurationManager != null) configurationManager.unload();
    }

    public boolean hasWorldEdit() {
        return worldEditManager != null;
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
