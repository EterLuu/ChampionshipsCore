package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprintAuditor;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
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
            Utils.sendAdminError(sender, "找不到 Build Mart 地图 &#fff566" + areaName);
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
            Utils.sendAdminError(sender, "找不到蓝图 &#fff566" + args[0]);
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
            Utils.sendAdminError(sender, "页码超出范围，当前共 &#fff566" + pages + " &#ededed页");
            return;
        }
        long covered = audits.stream().filter(BuildMartBlueprintAuditor.Audit::fullyCovered).count();
        long mismatched = audits.stream().filter(a -> a.configuredStars() != a.suggestedStars()).count();
        String map = config == null ? "无材料清单" : config.getAreaName();
        Utils.sendAdminInfo(sender, "蓝图全量审查 &#696969• &#ededed地图 &#fff566" + map
                + " &#696969• &#ededed第 &#fff566" + page + "/" + pages + " &#ededed页");
        Utils.sendAdminInfo(sender, "总数 &#fff566" + audits.size() + " &#696969• &#ededed材料完整 &#fff566"
                + covered + " &#696969• &#ededed建议调整星级 &#fff566" + mismatched);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(audits.size(), from + PAGE_SIZE);
        for (BuildMartBlueprintAuditor.Audit audit : audits.subList(from, to)) {
            String coverage = !audit.coverageChecked() ? "?" : audit.fullyCovered() ? "✓" : "缺"
                    + audit.uncoveredMaterials().size();
            String stars = audit.configuredStars() == audit.suggestedStars()
                    ? audit.configuredStars() + "★" : audit.configuredStars() + "→" + audit.suggestedStars() + "★";
            Utils.sendAdminInfo(sender, "&#fff566" + audit.name() + " &#696969• &#ededed" + stars
                    + " &#696969• &#ededed分数 " + audit.score() + " &#696969• &#ededed" + audit.blocks()
                    + " 块 &#696969• &#ededed材料 " + coverage);
        }
    }

    static void showAudit(CommandSender sender, BuildMartBlueprintAuditor.Audit audit, BuildMartConfig config) {
        Utils.sendAdminInfo(sender, "蓝图 &#fff566" + audit.name() + " &#696969• &#ededed当前 "
                + audit.configuredStars() + "★ &#696969• &#ededed建议 &#fff566" + audit.suggestedStars()
                + "★ &#696969• &#ededed难度分 &#fff566" + audit.score());
        Utils.sendAdminInfo(sender, "结构 &#696969• &#ededed" + audit.blocks() + " 块 &#696969• &#ededed"
                + audit.uniqueMaterials() + " 种材料 &#696969• &#ededed尺寸 " + audit.dimensions()
                + " &#696969• &#ededed连通区域 " + audit.components());
        Utils.sendAdminInfo(sender, "状态 &#696969• &#ededed带状态 " + audit.statefulBlocks()
                + " &#696969• &#ededed方向 " + audit.directionalBlocks() + " &#696969• &#ededed复杂 "
                + audit.complexStateBlocks() + " &#696969• &#ededed严格连接 " + audit.strictConnectableBlocks());
        if (!audit.coverageChecked()) {
            Utils.sendAdminError(sender, "材料清单不存在，材料覆盖未检查。"
                    + (config == null ? "请先加载 Build Mart 地图。" : "请先保存该地图的材料区。"));
        } else {
            String islands = audit.materialIslands().isEmpty() ? "-" : audit.materialIslands().stream()
                    .map(island -> island.displayName()).collect(Collectors.joining("、"));
            Utils.sendAdminInfo(sender, "材料 &#696969• &#ededed直接 " + audit.directMaterials()
                    + " &#696969• &#ededed可加工 " + audit.craftableMaterials() + " &#696969• &#ededed区域 " + islands);
            if (audit.uncoveredMaterials().isEmpty()) {
                Utils.sendAdminSuccess(sender, "当前材料表可完整覆盖该蓝图。");
            } else {
                Utils.sendAdminError(sender, "未覆盖材料：&#fff566" + audit.uncoveredMaterials().stream()
                        .map(Material::getKey).map(key -> key.getKey()).collect(Collectors.joining("、")));
            }
        }
        for (String warning : audit.warnings()) Utils.sendAdminError(sender, "审查提醒：" + warning);
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
