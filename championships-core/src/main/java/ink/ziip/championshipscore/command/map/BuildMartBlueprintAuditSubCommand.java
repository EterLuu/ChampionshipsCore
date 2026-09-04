package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprintAuditor;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** In-game single/all blueprint audit; "preview" is intentionally an alias of the same read-only report. */
public final class BuildMartBlueprintAuditSubCommand extends BaseSubCommand {
    private static final int PAGE_SIZE = 10;

    public BuildMartBlueprintAuditSubCommand(String commandName) {
        super(commandName, "审查蓝图结构、难度和材料覆盖",
                "/cc map blueprint " + commandName + " <名称|all> [地图] [页码]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 1 || args.length > 3) {
            sendUsage(sender);
            return true;
        }
        BuildMartManager manager = plugin.getGameManager().getBuildMartManager();
        String areaName = args.length >= 2 && !isInteger(args[1]) ? args[1] : null;
        BuildMartConfig config = resolveConfig(manager, areaName);
        if (areaName != null && config == null) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_MAP_MISSING
                    .replace("%map%", areaName));
            return true;
        }
        BuildMartMaterialManifest.AuditInventory inventory = config == null
                ? new BuildMartMaterialManifest.AuditInventory(false, java.util.Map.of(), java.util.Map.of())
                : BuildMartMaterialManifest.readAuditInventory(config);

        if (args[0].equalsIgnoreCase("all")) {
            int pageArgument = args.length == 3 ? 2 : args.length == 2 && isInteger(args[1]) ? 1 : -1;
            int page = pageArgument < 0 ? 1 : parsePage(args[pageArgument]);
            if (page < 1) {
                sendUsage(sender);
                return true;
            }
            showAll(sender, manager, inventory, config, page);
            return true;
        }

        if (args.length == 3) {
            sendUsage(sender);
            return true;
        }
        BuildMartBlueprint blueprint = manager.getOrderPool().byId(args[0].toLowerCase(Locale.ROOT));
        if (blueprint == null) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_MISSING
                    .replace("%blueprint%", args[0]));
            return true;
        }
        showAudit(sender, BuildMartBlueprintAuditor.audit(blueprint, inventory), config);
        return true;
    }

    private void showAll(CommandSender sender, BuildMartManager manager,
                         BuildMartMaterialManifest.AuditInventory inventory, BuildMartConfig config, int page) {
        List<BuildMartBlueprintAuditor.Audit> audits = manager.getOrderPool().getAll().stream()
                .map(blueprint -> BuildMartBlueprintAuditor.audit(blueprint, inventory))
                .sorted(Comparator.comparingInt(BuildMartBlueprintAuditor.Audit::configuredStars)
                        .thenComparingDouble(BuildMartBlueprintAuditor.Audit::score)
                        .thenComparing(BuildMartBlueprintAuditor.Audit::name))
                .toList();
        int pages = Math.max(1, (audits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_PAGE_OUT_OF_RANGE
                    .replace("%pages%", String.valueOf(pages)));
            return;
        }
        long covered = audits.stream().filter(BuildMartBlueprintAuditor.Audit::fullyCovered).count();
        long mismatched = audits.stream().filter(a -> a.configuredStars() != a.suggestedStars()).count();
        String map = config == null ? "无材料清单" : config.getAreaName();
        Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_HEADER
                .replace("%map%", map)
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages)));
        Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_SUMMARY
                .replace("%total%", String.valueOf(audits.size()))
                .replace("%covered%", String.valueOf(covered))
                .replace("%mismatched%", String.valueOf(mismatched)));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(audits.size(), from + PAGE_SIZE);
        for (BuildMartBlueprintAuditor.Audit audit : audits.subList(from, to)) {
            String coverage = !audit.coverageChecked() ? "?" : audit.fullyCovered() ? "✓" : "缺"
                    + audit.uncoveredMaterials().size();
            String stars = audit.configuredStars() == audit.suggestedStars()
                    ? MessageConfig.BUILD_MART_BLUEPRINT_STARS_SAME.replace("%stars%", String.valueOf(audit.configuredStars()))
                    : MessageConfig.BUILD_MART_BLUEPRINT_STARS_CHANGED
                            .replace("%configured%", String.valueOf(audit.configuredStars()))
                            .replace("%suggested%", String.valueOf(audit.suggestedStars()));
            Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_ROW
                    .replace("%name%", audit.name())
                    .replace("%stars%", stars)
                    .replace("%score%", String.valueOf(audit.score()))
                    .replace("%blocks%", String.valueOf(audit.blocks()))
                    .replace("%coverage%", coverage));
        }
    }

    static void showAudit(CommandSender sender, BuildMartBlueprintAuditor.Audit audit, BuildMartConfig config) {
        Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_TITLE
                .replace("%name%", audit.name())
                .replace("%configured%", String.valueOf(audit.configuredStars()))
                .replace("%suggested%", String.valueOf(audit.suggestedStars()))
                .replace("%score%", String.valueOf(audit.score())));
        Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_STRUCTURE
                .replace("%blocks%", String.valueOf(audit.blocks()))
                .replace("%materials%", String.valueOf(audit.uniqueMaterials()))
                .replace("%dimensions%", audit.dimensions())
                .replace("%components%", String.valueOf(audit.components())));
        Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_STATE
                .replace("%stateful%", String.valueOf(audit.statefulBlocks()))
                .replace("%directional%", String.valueOf(audit.directionalBlocks()))
                .replace("%complex%", String.valueOf(audit.complexStateBlocks()))
                .replace("%strict%", String.valueOf(audit.strictConnectableBlocks())));
        if (!audit.coverageChecked()) {
            Utils.sendAdminError(sender, config == null
                    ? MessageConfig.BUILD_MART_BLUEPRINT_COVERAGE_NO_MAP
                    : MessageConfig.BUILD_MART_BLUEPRINT_COVERAGE_NO_ZONES);
        } else {
            String islands = audit.materialIslands().isEmpty() ? "-" : audit.materialIslands().stream()
                    .map(island -> island.displayName()).collect(Collectors.joining("、"));
            Utils.sendAdminInfo(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_MATERIALS
                    .replace("%direct%", String.valueOf(audit.directMaterials()))
                    .replace("%craftable%", String.valueOf(audit.craftableMaterials()))
                    .replace("%islands%", islands));
            if (audit.uncoveredMaterials().isEmpty()) {
                Utils.sendAdminSuccess(sender, MessageConfig.BUILD_MART_BLUEPRINT_COVERAGE_FULL);
            } else {
                Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_COVERAGE_UNCOVERED
                        .replace("%materials%", audit.uncoveredMaterials().stream()
                                .map(Material::getKey).map(key -> key.getKey()).collect(Collectors.joining("、"))));
            }
        }
        for (String warning : audit.warnings())
                Utils.sendAdminError(sender, MessageConfig.BUILD_MART_BLUEPRINT_AUDIT_WARNING
                        .replace("%warning%", warning));
    }

    static @Nullable BuildMartConfig resolveConfig(BuildMartManager manager, @Nullable String requested) {
        if (requested != null) {
            BuildMartArea area = manager.getArea(requested);
            return area == null ? null : area.getGameConfig();
        }
        return manager.getAreaNameList().stream().sorted(String.CASE_INSENSITIVE_ORDER).findFirst()
                .map(manager::getArea).map(BuildMartArea::getGameConfig).orElse(null);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!plugin.getGameManager().isGameEnabled(GameTypeEnum.BuildMart)) return List.of();
        BuildMartManager manager = plugin.getGameManager().getBuildMartManager();
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("all");
            manager.getOrderPool().getAll().stream().map(BuildMartBlueprint::getId).forEach(values::add);
            return complete(values, args[0]);
        }
        if (args.length == 2) {
            List<String> values = new ArrayList<>(enabledAreaNames(GameTypeEnum.BuildMart));
            if (args[0].equalsIgnoreCase("all")) values.add("1");
            return complete(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("all")) return complete(List.of("1"), args[2]);
        return List.of();
    }
}
