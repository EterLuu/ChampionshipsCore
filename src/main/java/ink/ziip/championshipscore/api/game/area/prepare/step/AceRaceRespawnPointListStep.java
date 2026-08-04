package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Edits the independent proximity markers used as Ace Race respawn destinations. */
public final class AceRaceRespawnPointListStep extends ListStep {
    public AceRaceRespawnPointListStep() {
        super("respawn_points", Component.text("竞速重生点"),
                Component.text("站在重生位置并朝向赛道方向；玩家经过 3 格内时保存"),
                Material.RECOVERY_COMPASS,
                target -> cfg(target).ensureRespawnPoints(),
                (target, values) -> cfg(target).setRespawnPoints(values),
                target -> cfg(target).ensureRespawnPoints().isEmpty(),
                (target, value) -> cfg(target).addRespawnPoint(value),
                target -> cfg(target).clearRespawnPoints(),
                target -> cfg(target).ensureRespawnPoints().size());
    }

    private static AceRaceConfig cfg(SetupTarget target) {
        return (AceRaceConfig) target.config();
    }

    private static void reload(PrepareSession session) {
        AceRaceArea area = session.getPlugin().getGameManager().getAceRaceManager().getArea(session.getAreaName());
        if (area != null) area.loadCoursePoints();
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        String result = super.listAdd(session, player);
        reload(session);
        return result;
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        String result = super.listClear(session, player);
        reload(session);
        return result;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        String result = super.listEdit(session, player, index);
        reload(session);
        return result;
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        String result = super.listSetOrder(session, player, index, newOrder);
        reload(session);
        return result;
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        String result = super.listRemove(session, player, index);
        reload(session);
        return result;
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text("添加重生点");
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text("站在重生位置并朝向赛道方向后点击");
    }
}
