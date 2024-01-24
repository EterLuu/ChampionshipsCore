package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.command.member.MemberMainCommand;
import ink.ziip.championshipscore.command.team.TeamMainCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.util.logging.Level;

public class CommandManager extends BaseManager {
    private final PluginCommand corePluginCommand;
    private MainCommand mainCommand;
    private TeamMainCommand teamMainCommand;
    private MemberMainCommand memberMainCommand;

    public CommandManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        this.corePluginCommand = Bukkit.getPluginCommand("cc");
    }

    @Override
    public void load() {
        this.mainCommand = new MainCommand();

        this.teamMainCommand = new TeamMainCommand();
        this.memberMainCommand = new MemberMainCommand();

        this.mainCommand.addSubCommand(teamMainCommand);
        this.mainCommand.addSubCommand(memberMainCommand);

        if (this.corePluginCommand != null) {
            this.corePluginCommand.setExecutor(this.mainCommand);
            this.corePluginCommand.setTabCompleter(this.mainCommand);
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
