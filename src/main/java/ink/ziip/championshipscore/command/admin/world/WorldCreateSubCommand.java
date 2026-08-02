package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import ink.ziip.championshipscore.util.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class WorldCreateSubCommand extends BaseSubCommand {
    private static final List<String> ENVIRONMENTS = List.of("normal", "nether", "the_end");

    public WorldCreateSubCommand() {
        super("create", "创建或加载小游戏世界", "/cc admin world create <世界> [normal|nether|the_end]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || args.length > 2) {
            sendUsage(sender);
            return true;
        }

        String worldName = args[0];
        if (!WorldManager.isValidWorldName(worldName)) {
            Utils.sendAdminError(sender, "世界名仅支持字母、数字、下划线和连字符");
            return true;
        }
        World.Environment environment = args.length == 2 ? parseEnvironment(args[1]) : World.Environment.NORMAL;
        if (environment == null) {
            sendUsage(sender);
            return true;
        }

        WorldManager worldManager = plugin.getWorldManager();
        World.Environment bingoEnvironment = WorldManager.getBingoEnvironment(worldName);
        if (bingoEnvironment != null && args.length == 2 && environment != bingoEnvironment) {
            Utils.sendAdminError(sender, "Bingo 维度 &#fff566" + worldName + " &#ededed必须使用 &#fff566"
                    + bingoEnvironment.name().toLowerCase());
            return true;
        }

        boolean existed = worldManager.getWorldFolder(worldName).isDirectory();
        FoliaScheduler.global(plugin).runTask(() -> {
            if (Bukkit.getWorld(worldName) != null) {
                reply(sender, () -> Utils.sendAdminInfo(sender, "世界 &#fff566" + worldName + " &#bababa已加载"));
                return;
            }
            java.util.concurrent.CompletableFuture<Boolean> loaded = bingoEnvironment == null
                    ? worldManager.loadWorldAsync(worldName, environment, false)
                    : worldManager.loadBingoWorldAsync(worldName, bingoEnvironment);
            loaded.whenComplete((success, error) -> reply(sender, () -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    Utils.sendAdminError(sender, "世界 &#fff566" + worldName
                            + " &#ededed加载失败 &#696969• 请检查控制台");
                } else {
                    Utils.sendAdminSuccess(sender, "已" + (existed ? "加载" : "创建") + "世界 &#fff566"
                            + worldName + " &#696969• "
                            + (bingoEnvironment == null ? environment : bingoEnvironment).name().toLowerCase());
                }
            }));
        });
        return true;
    }

    private void reply(CommandSender sender, Runnable message) {
        if (sender instanceof Player player) {
            FoliaScheduler.global(plugin).runEntity(player, message);
        } else {
            FoliaScheduler.global(plugin).runTask(message);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> worlds = plugin.getWorldManager().getStoredWorldNames();
            worlds.removeIf(name -> Bukkit.getWorld(name) != null);
            return filterStartsWith(worlds, args[0]);
        }
        if (args.length == 2)
            return filterStartsWith(ENVIRONMENTS, args[1]);
        return Collections.emptyList();
    }

    private World.Environment parseEnvironment(String value) {
        return switch (value.toLowerCase()) {
            case "normal" -> World.Environment.NORMAL;
            case "nether" -> World.Environment.NETHER;
            case "the_end" -> World.Environment.THE_END;
            default -> null;
        };
    }
}
