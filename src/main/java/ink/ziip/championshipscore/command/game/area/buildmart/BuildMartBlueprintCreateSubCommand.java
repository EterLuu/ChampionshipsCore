package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exports the player's WorldEdit selection into a Build Mart blueprint yml. Block offsets are stored
 * relative to the selection's minimum corner so the build anchor maps to that corner in-game. Air is
 * skipped. After writing, the shared blueprint pool is reloaded so the order is immediately drawable.
 *
 * <p>Usage: {@code /cc game area buildmart blueprint create <name> <stars>}.
 */
public class BuildMartBlueprintCreateSubCommand extends BaseSubCommand {
    /** Safety cap on exported block count to avoid accidentally serializing a giant region. */
    private static final int MAX_BLOCKS = 20000;
    /** Also cap air-heavy selections, which otherwise could schedule an unbounded cross-region scan. */
    private static final long MAX_SELECTION_VOLUME = 2_000_000L;

    public BuildMartBlueprintCreateSubCommand() {
        super("create", "从WE选区导出蓝图", "/cc game area buildmart blueprint create <名称> <星级>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        String name = args[0].toLowerCase();
        if (!name.matches("[a-z0-9_-]+")) {
            sender.sendMessage("§c蓝图名称只能包含小写字母、数字、下划线和连字符。");
            return true;
        }
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendUsage(sender);
            return true;
        }

        Vector[] selection;
        try {
            selection = plugin.getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception e) {
            sender.sendMessage("§c无法读取 WorldEdit 选区，请先用 //pos1 //pos2 选择区域。");
            return true;
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        World world = player.getWorld();

        long volume = (long) (max.getBlockX() - min.getBlockX() + 1)
                * (max.getBlockY() - min.getBlockY() + 1)
                * (max.getBlockZ() - min.getBlockZ() + 1);
        if (volume > MAX_SELECTION_VOLUME) {
            sender.sendMessage("§c选区体积超过上限 " + MAX_SELECTION_VOLUME + "，已取消。");
            return true;
        }

        sender.sendMessage("§7正在按区块扫描选区并导出蓝图……");
        scanSelection(world, min, max).thenAccept(blocks -> {
            if (blocks.size() > MAX_BLOCKS) {
                send(player, "§c选区方块数超过上限 " + MAX_BLOCKS + "，已取消。");
                return;
            }
            if (blocks.isEmpty()) {
                send(player, "§c选区内没有任何非空气方块。");
                return;
            }

            FoliaScheduler.global(plugin).runAsyncFuture(() -> saveBlueprint(name, stars, blocks))
                    .thenRun(() -> send(player, "§a已导出蓝图 §e" + name + " §7(" + stars + "★, "
                            + blocks.size() + " 个方块)，蓝图库已刷新。"))
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save Build Mart blueprint", throwable);
                        send(player, "§c保存蓝图失败：" + throwable.getMessage());
                        return null;
                    });
        }).exceptionally(throwable -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to scan Build Mart blueprint", throwable);
            send(player, "§c扫描蓝图选区失败：" + throwable.getMessage());
            return null;
        });

        return true;
    }

    private CompletableFuture<List<String>> scanSelection(World world, Vector min, Vector max) {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        AtomicInteger blockCount = new AtomicInteger();
        List<CompletableFuture<List<String>>> scans = new ArrayList<>();
        int minChunkX = min.getBlockX() >> 4;
        int maxChunkX = max.getBlockX() >> 4;
        int minChunkZ = min.getBlockZ() >> 4;
        int maxChunkZ = max.getBlockZ() >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int currentChunkX = chunkX;
                int currentChunkZ = chunkZ;
                int fromX = Math.max(min.getBlockX(), chunkX << 4);
                int toX = Math.min(max.getBlockX(), (chunkX << 4) + 15);
                int fromZ = Math.max(min.getBlockZ(), chunkZ << 4);
                int toZ = Math.min(max.getBlockZ(), (chunkZ << 4) + 15);
                Location owner = new Location(world, fromX, min.getBlockY(), fromZ);
                scans.add(world.getChunkAtAsync(currentChunkX, currentChunkZ).thenCompose(ignored ->
                        scheduler.supplyAtLocation(owner, () -> scanChunk(
                                world, min, max, fromX, toX, fromZ, toZ, blockCount))));
            }
        }

        return CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<String> blocks = new ArrayList<>();
            for (CompletableFuture<List<String>> scan : scans) {
                blocks.addAll(scan.join());
            }
            return blocks;
        });
    }

    private List<String> scanChunk(World world, Vector min, Vector max,
                                   int fromX, int toX, int fromZ, int toZ,
                                   AtomicInteger blockCount) {
        List<String> blocks = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    if (blockCount.get() > MAX_BLOCKS) return blocks;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    blockCount.incrementAndGet();
                    blocks.add((x - min.getBlockX()) + "," + (y - min.getBlockY()) + ","
                            + (z - min.getBlockZ()) + "=" + block.getBlockData().getAsString());
                }
            }
        }
        return blocks;
    }

    private void saveBlueprint(String name, int stars, List<String> blocks) {
        File dir = new File(new File(plugin.getDataFolder(), "buildmart"), "blueprints");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建蓝图目录");
        }
        File file = new File(dir, name + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("stars", stars);
        yaml.set("blocks", blocks);
        try {
            yaml.save(file);
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入 " + file.getName(), exception);
        }
        plugin.getGameManager().getBuildMartManager().reloadOrderPool();
    }

    private void send(Player player, String message) {
        FoliaScheduler.global(plugin).runEntity(player, () -> player.sendMessage(message));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
