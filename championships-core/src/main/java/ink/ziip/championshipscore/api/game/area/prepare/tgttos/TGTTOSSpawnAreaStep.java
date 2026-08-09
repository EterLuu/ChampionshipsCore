package ink.ziip.championshipscore.api.game.area.prepare.tgttos;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Captures a one-block-high WorldEdit plane used to randomly spawn chickens or players above it. */
public final class TGTTOSSpawnAreaStep extends PrepareStep {
    public enum SpawnType {
        CHICKEN,
        PLAYER
    }

    private final SpawnType spawnType;

    public TGTTOSSpawnAreaStep(@NotNull String key, @NotNull Component displayName,
                               @NotNull Component description, @NotNull Material icon,
                               @NotNull SpawnType spawnType) {
        super(key, displayName, description, icon, StepCaptureType.WE_SELECTION);
        this.spawnType = spawnType;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        TGTTOSConfig config = (TGTTOSConfig) session.getTarget().config();
        Vector pos1 = spawnType == SpawnType.CHICKEN
                ? config.getChickenSpawnAreaPos1() : config.getPlayerSpawnAreaPos1();
        Vector pos2 = spawnType == SpawnType.CHICKEN
                ? config.getChickenSpawnAreaPos2() : config.getPlayerSpawnAreaPos2();
        if (pos1 == null || pos2 == null || pos1.getBlockY() != pos2.getBlockY()) return false;
        return spawnType != SpawnType.PLAYER
                || (config.getPlayerSpawnYaw() != null && config.getPlayerSpawnPitch() != null);
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError("请先用 WorldEdit 选取两个端点。");
        }
        if (selection[0].getBlockY() != selection[1].getBlockY()) {
            return Utils.formatAdminError("生成区域必须是恰好一格高的 WorldEdit 选区。");
        }

        TGTTOSConfig config = (TGTTOSConfig) session.getTarget().config();
        if (spawnType == SpawnType.CHICKEN) {
            config.setChickenSpawnAreaPos1(selection[0]);
            config.setChickenSpawnAreaPos2(selection[1]);
        } else {
            config.setPlayerSpawnAreaPos1(selection[0]);
            config.setPlayerSpawnAreaPos2(selection[1]);
            config.setPlayerSpawnYaw(player.getLocation().getYaw());
            config.setPlayerSpawnPitch(player.getLocation().getPitch());
        }
        session.markDirty();
        return Utils.formatAdminSuccess(spawnType == SpawnType.CHICKEN
                ? "已设置鸡生成区域；鸡会随机生成在选区上方一格。"
                : "已设置玩家生成区域与统一朝向；玩家会随机生成在选区上方一格。");
    }
}
