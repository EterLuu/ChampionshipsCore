package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Stamps {@code count} copies of a schematic into the target map's world and persists the result
 * via {@code target.saveMap(NORMAL)} (which round-trips the world through the {@code maps/<world>/}
 * template, teleporting everyone in the world to the lobby and reloading it). After the reload the player
 * is teleported back to copy 0 (grid origin) so point-setting can continue. The selected copy count is
 * persisted as part of the map layout.
 */
public class StampStep extends PrepareStep {

    private final Function<ChampionshipsCore, File> fileResolver;
    private final ArenaGrid grid;
    private final BiConsumer<SetupTarget, Integer> copyCountWriter;

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid,
                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter) {
        super("stamp",
                Component.text("盖章生成多份地图"),
                Component.text("输入份数后粘贴 N 份场地并固化为模板"),
                Material.DISPENSER,
                StepCaptureType.STAMP);
        this.fileResolver = fileResolver;
        this.grid = grid;
        this.copyCountWriter = copyCountWriter;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override
    public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) {
            return Utils.formatAdminError("场地份数必须大于 0。");
        }
        File file = fileResolver.apply(session.getPlugin());
        if (!file.isFile()) {
            return Utils.formatAdminError("缺少场地模板，请先完成“保存场地模板”。");
        }
        String worldName = session.getTarget().worldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Utils.formatAdminError("世界 &#fff566" + worldName + " &#ededed尚未加载。");
        }
        if (!session.getTarget().canSaveMap()) {
            return Utils.formatAdminError("同一地图仍有游戏实例运行，无法重新生成或保存。");
        }
        try {
            ArenaPreparer.stampCopies(session.getPlugin(), world, file, grid, count);
        } catch (Exception e) {
            return Utils.formatAdminError("生成场地失败：&#fff566" + e.getMessage());
        }

        // Persist the stamped world into the static template. saveMap teleports everyone in the world to
        // the lobby, unloads, copies, and reloads - so re-fetch the world afterwards.
        if (!session.getTarget().saveMap(World.Environment.NORMAL)) {
            return Utils.formatAdminError("地图保存失败，请查看控制台日志；复制数量未写入配置。");
        }
        copyCountWriter.accept(session.getTarget(), count);
        session.getTarget().config().saveOptions();

        World reloaded = Bukkit.getWorld(worldName);
        if (reloaded != null) {
            Location dest = grid.origin(0).toLocation(reloaded);
            Bukkit.getScheduler().runTask(session.getPlugin(), () -> player.teleport(dest));
        }
        session.setWorldConfirmed(true);
        session.setStamped(true);
        return Utils.formatAdminSuccess("已生成并保存 &#fff566" + count + " &#ededed份场地，已返回 0 号场地。");
    }
}
