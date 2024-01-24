package ink.ziip.championshipscore;

import ink.ziip.championshipscore.api.player.CCPlayerManager;
import ink.ziip.championshipscore.api.team.TeamManager;
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

public final class ChampionshipsCore extends JavaPlugin {
    @Getter
    private static ChampionshipsCore instance;

    @Getter
    private TeamManager teamManager;
    @Getter
    private CCPlayerManager ccPlayerManager;
    @Getter
    private ListenerManager listenerManager;
    @Getter
    private ConfigurationManager configurationManager;
    @Getter
    private DatabaseManager databaseManager;
    @Getter
    private CommandManager commandManager;
    @Getter
    private WorldEditManager worldEditManager;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            /*
             * We inform about the fact that PlaceholderAPI isn't installed and then
             * disable this plugin to prevent issues.
             */
            getLogger().warning("Could not find PlaceholderAPI!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            /*
             * We inform about the fact that ProtocolLib isn't installed and then
             * disable this plugin to prevent issues.
             */
            getLogger().warning("Could not find ProtocolLib!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().warning("Could not find FastAsyncWorldEdit!");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        configurationManager = new ConfigurationManager(this);
        databaseManager = new DatabaseManager(this);
        ccPlayerManager = new CCPlayerManager(this);
        listenerManager = new ListenerManager(this);
        commandManager = new CommandManager(this);
        teamManager = new TeamManager(this);
        worldEditManager = new WorldEditManager(this);

        // Plugin startup logic
        configurationManager.load();
        databaseManager.load();
        listenerManager.load();
        ccPlayerManager.load();
        worldEditManager.load();

        teamManager.load();
        commandManager.load();

        getLogger().log(Level.INFO, CCConfig.MODE);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        listenerManager.unload();
        commandManager.unload();
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
