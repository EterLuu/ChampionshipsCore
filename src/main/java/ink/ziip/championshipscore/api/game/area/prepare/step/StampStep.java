package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Function;

/**
 * Stamps {@code count} copies of a schematic into the area's world along a grid and persists the result
 * via {@code area.saveMap(NORMAL)} (which round-trips the world through the {@code maps/<world>/}
 * template, teleporting everyone in the world to the lobby and reloading it). After the reload the player
 * is teleported back to copy 0 (grid origin) so point-setting can continue. State is session-only (the
 * copy count is not persisted, matching the legacy {@code prepare} commands).
 */
public class StampStep extends PrepareStep {

    private final Function<ChampionshipsCore, File> fileResolver;
    private final ArenaGrid grid;

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid) {
        super("stamp",
                Component.text("盖章生成多份地图"),
                Component.text("输入份数后粘贴 N 份场地并固化为模板"),
                Material.DISPENSER,
                StepCaptureType.STAMP);
        this.fileResolver = fileResolver;
        this.grid = grid;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override
    public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        File file = fileResolver.apply(session.getPlugin());
        if (!file.isFile()) {
            return Utils.formatAdminError("缺少场地模板，请先完成“保存场地模板”。");
        }
        String worldName = session.getArea().getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Utils.formatAdminError("世界 #fff566" + worldName + " #ededed尚未加载。");
        }
        try {
            ArenaPreparer.stampCopies(session.getPlugin(), world, file, grid, count);
        } catch (Exception e) {
            return Utils.formatAdminError("生成场地失败：#fff566" + e.getMessage());
        }

        // Persist the stamped world into the static template. saveMap teleports everyone in the world to
        // the lobby, unloads, copies, and reloads - so re-fetch the world afterwards.
        session.getArea().saveMap(World.Environment.NORMAL);

        World reloaded = Bukkit.getWorld(worldName);
        if (reloaded != null) {
            Location dest = grid.origin(0).toLocation(reloaded);
            Bukkit.getScheduler().runTask(session.getPlugin(), () -> player.teleport(dest));
        }
        session.setWorldConfirmed(true);
        session.setStamped(true);
        return Utils.formatAdminSuccess("已生成并保存 #fff566" + count + " #ededed份场地，已返回 0 号场地。");
    }
}
