package ink.ziip.championshipscore;

import fr.skytasul.glowingentities.GlowingEntities;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.rank.RankManager;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.vote.VoteManager;
import ink.ziip.championshipscore.integration.papi.PlaceholderManager;
import ink.ziip.championshipscore.util.world.WorldManager;
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
    private BingoManager bingoManager;
    private WorldManager worldManager;
    private GlowingEntities glowingEntities;
    private PlaceholderManager placeholderManager;
    private VoteManager voteManager;
    private ScheduleManager scheduleManager;

    @Override
    public void onEnable() {
        instance = this;
        loaded = true;

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
        if (Bukkit.getPluginManager().getPlugin("BingoReloaded") != null) {
            bingoManager = new BingoManager(this);
            bingoManager.load();
        }

        commandManager.load();
        placeholderManager.load();
        voteManager.load();
        scheduleManager.load();

        getLogger().log(Level.INFO, CCConfig.MODE);
    }

    @Override
    public void onDisable() {
        loaded = false;

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
        placeholderManager.unload();
        voteManager.unload();
        scheduleManager.unload();
        glowingEntities.disable();
    }

    public @NotNull Path getFolder() {
        return Paths.get(super.getDataFolder().getAbsolutePath());
    }
}
