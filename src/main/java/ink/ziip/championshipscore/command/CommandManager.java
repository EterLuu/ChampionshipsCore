package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.command.admin.AdminMainCommand;
import ink.ziip.championshipscore.command.game.GameMainCommand;
import ink.ziip.championshipscore.command.member.MemberMainCommand;
import ink.ziip.championshipscore.command.rank.RankMainCommand;
import ink.ziip.championshipscore.command.spectate.SpectateSubCommand;
import ink.ziip.championshipscore.command.team.TeamMainCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.util.logging.Level;

public class CommandManager extends BaseManager {
    private final PluginCommand corePluginCommand;

    public CommandManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        this.corePluginCommand = Bukkit.getPluginCommand("cc");
    }

    @Override
    public void load() {
        MainCommand mainCommand = new MainCommand();

        mainCommand.addSubCommand(new TeamMainCommand());
        mainCommand.addSubCommand(new MemberMainCommand());
        mainCommand.addSubCommand(new GameMainCommand());
        mainCommand.addSubCommand(new SpectateSubCommand());
        mainCommand.addSubCommand(new RankMainCommand());
        mainCommand.addSubCommand(new AdminMainCommand());
        mainCommand.addSubCommand(new VoteSubCommand());
        mainCommand.addSubCommand(new SpawnSubCommand());

        if (this.corePluginCommand != null) {
            this.corePluginCommand.setExecutor(mainCommand);
            this.corePluginCommand.setTabCompleter(mainCommand);
        }
    }

    @Override
    public void unload() {
        Field commandMapField;
        try {
            commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            corePluginCommand.unregister(commandMap);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Unregister commands failed.");
        }
    }
}
