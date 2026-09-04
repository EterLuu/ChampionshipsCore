package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartCopperPolicy;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprintAuditor;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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

/** Exports a WorldEdit selection into a Build Mart blueprint and refreshes the shared order pool. */
public final class BuildMartBlueprintCreateSubCommand extends BaseSubCommand {
    private static final int MAX_BLOCKS = 20000;
    private static final int MAX_SIZE = 7;

    public BuildMartBlueprintCreateSubCommand() {
        super("create", "从WE选区导出并自动审查蓝图", "/cc map blueprint create <名称> [覆盖星级]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if ((args.length != 1 && args.length != 2) || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        String name = args[0].toLowerCase(java.util.Locale.ROOT);
        Integer overriddenStars = null;
        if (args.length == 2) {
            try {
                overriddenStars = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sendUsage(sender);
                return true;
            }
            if (overriddenStars < 1 || overriddenStars > 5) {
                Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_STARS_RANGE);
                return true;
            }
        }

        Vector[] selection;
        try {
            selection = plugin.getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_SELECTION_READ_FAILED);
            return true;
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        World world = player.getWorld();

        int sizeX = max.getBlockX() - min.getBlockX() + 1;
        int sizeY = max.getBlockY() - min.getBlockY() + 1;
        int sizeZ = max.getBlockZ() - min.getBlockZ() + 1;
        if (sizeY > MAX_SIZE) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_HEIGHT_LIMIT
                    .replace("%limit%", String.valueOf(MAX_SIZE)).replace("%current%", String.valueOf(sizeY)));
            return true;
        }
        if (sizeX > MAX_SIZE || sizeZ > MAX_SIZE) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_FOOTPRINT_LIMIT
                    .replace("%limit%", String.valueOf(MAX_SIZE))
                    .replace("%x%", String.valueOf(sizeX))
                    .replace("%z%", String.valueOf(sizeZ)));
            return true;
        }

        List<String> blocks = new ArrayList<>();
        List<BlueprintBlock> blueprintBlocks = new ArrayList<>();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    int ox = x - min.getBlockX();
                    int oy = y - min.getBlockY();
                    int oz = z - min.getBlockZ();
                    org.bukkit.block.data.BlockData normalized = BuildMartCopperPolicy
                            .normalizeBlueprint(block.getBlockData());
                    blocks.add(ox + "," + oy + "," + oz + "=" + normalized.getAsString());
                    blueprintBlocks.add(new BlueprintBlock(ox, oy, oz, normalized.clone()));
                    if (blocks.size() > MAX_BLOCKS) {
                        Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_BLOCK_LIMIT
                            .replace("%limit%", String.valueOf(MAX_BLOCKS)));
                        return true;
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_EMPTY_SELECTION);
            return true;
        }

        BuildMartManager manager = plugin.getGameManager().getBuildMartManager();
        BuildMartConfig config = BuildMartBlueprintAuditSubCommand.resolveConfig(manager, null);
        BuildMartMaterialManifest.AuditInventory inventory = config == null
                ? new BuildMartMaterialManifest.AuditInventory(false, java.util.Map.of(), java.util.Map.of())
                : BuildMartMaterialManifest.readAuditInventory(config);
        BuildMartBlueprint preliminary = new BuildMartBlueprint(name, name, 1, blueprintBlocks);
        int suggestedStars = BuildMartBlueprintAuditor.audit(preliminary, inventory).suggestedStars();
        int stars = overriddenStars == null ? suggestedStars : overriddenStars;

        File dir = new File(new File(plugin.getDataFolder(), "buildmart"), "blueprints");
        dir.mkdirs();
        File file = new File(dir, name + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("stars", stars);
        yaml.set("blocks", blocks);
        try {
            yaml.save(file);
        } catch (Exception exception) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_SAVE_FAILED
                    .replace("%detail%", String.valueOf(exception.getMessage())));
            return true;
        }

        manager.reloadOrderPool();
        Utils.sendAdminSuccess(sender, (overriddenStars == null
                ? MessageConfig.BUILD_MART_BLUEPRINT_EXPORTED_AUTO
                : MessageConfig.BUILD_MART_BLUEPRINT_EXPORTED_MANUAL)
                .replace("%name%", name)
                .replace("%stars%", String.valueOf(stars))
                .replace("%blocks%", String.valueOf(blocks.size())));
        BuildMartBlueprint saved = manager.getOrderPool().byId(name);
        if (saved != null) {
            BuildMartBlueprintAuditor.Audit audit = BuildMartBlueprintAuditor.audit(saved, inventory);
            BuildMartBlueprintAuditSubCommand.showAudit(sender, audit, config);
            if (overriddenStars != null && overriddenStars != suggestedStars) {
                Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_OVERRIDE_NOT_SUGGESTED);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) return complete(List.of("1", "2", "3", "4", "5"), args[1]);
        return Collections.emptyList();
    }
}
