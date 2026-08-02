package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** A list editor for Dragon Egg Carnival kits, captured from the player's main hand. */
public final class ItemListStep extends PrepareStep {
    public ItemListStep() {
        super("kits", Component.text("比赛 Kit"), Component.text("手持一个 Kit 物品后添加；至少需要一套"),
                Material.CHEST, StepCaptureType.LIST);
    }

    private static DragonEggCarnivalConfig cfg(SetupTarget target) {
        return (DragonEggCarnivalConfig) target.config();
    }

    @Override
    public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        return kits != null && !kits.isEmpty();
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return Utils.formatAdminError("请先把 Kit 物品拿在主手。 ");
        DragonEggCarnivalConfig config = cfg(session.getTarget());
        List<ItemStack> kits = config.getKits();
        if (kits == null) kits = new ArrayList<>();
        kits.add(item.clone());
        config.setKits(kits);
        session.markDirty();
        return Utils.formatAdminSuccess("已添加 Kit &#696969• 当前 &#fff566" + kits.size() + " &#ededed套");
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        cfg(session.getTarget()).setKits(new ArrayList<>());
        session.markDirty();
        return Utils.formatAdminSuccess("已清空 Kit 列表。");
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        return kits == null ? 0 : kits.size();
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text("添加主手 Kit");
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text("将要使用的物品拿在主手后点击");
    }
}
