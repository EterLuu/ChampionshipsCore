package ink.ziip.championshipscore;

import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.world.WorldManager;
import ink.ziip.championshipscore.integration.bingo.BingoManager;
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
    private TeamManager teamManager;
    private PlayerManager playerManager;
    private ListenerManager listenerManager;
    private ConfigurationManager configurationManager;
    private DatabaseManager databaseManager;
    private CommandManager commandManager;
    private WorldEditManager worldEditManager;
    private GameManager gameManager;
    private RankManager rankManager;
    private BingoManager bingoManager;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("Could not find PlaceholderAPI!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().warning("Could not find ProtocolLib!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().warning("Could not find FastAsyncWorldEdit!");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        configurationManager = new ConfigurationManager(this);
        databaseManager = new DatabaseManager(this);
        playerManager = new PlayerManager(this);
        listenerManager = new ListenerManager(this);
        commandManager = new CommandManager(this);
        teamManager = new TeamManager(this);
        worldEditManager = new WorldEditManager(this);
        gameManager = new GameManager(this);
        rankManager = new RankManager(this);
        worldManager = new WorldManager(this);

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
        if (Bukkit.getPluginManager().getPlugin("BingoReloaded") != null) {
            bingoManager = new BingoManager(this);
            bingoManager.load();
        }

        commandManager.load();

        getLogger().log(Level.INFO, CCConfig.MODE);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        gameManager.unload();
        rankManager.unload();

        listenerManager.unload();
        playerManager.unload();
        teamManager.unload();
        commandManager.unload();

        worldEditManager.unload();
        worldManager.unload();

        if (Bukkit.getPluginManager().getPlugin("BingoReloaded") != null) {
            bingoManager.unload();
        }

        configurationManager.unload();
        databaseManager.unload();
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
