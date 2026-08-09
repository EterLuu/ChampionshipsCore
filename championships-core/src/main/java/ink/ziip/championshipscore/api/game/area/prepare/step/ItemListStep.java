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
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        List<ListEntry> entries = new ArrayList<>();
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        if (kits == null) return entries;
        for (int i = 0; i < kits.size(); i++) {
            ItemStack item = kits.get(i);
            entries.add(new ListEntry("Kit " + (i + 1), List.of(
                    item.getType().getKey().toString() + " × " + item.getAmount())));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) return Utils.formatAdminError("请先把 Kit 物品拿在主手。 ");
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        if (kits == null || index < 0 || index >= kits.size()) return null;
        kits.set(index, held.clone());
        cfg(session.getTarget()).setKits(kits);
        session.markDirty();
        return Utils.formatAdminSuccess("已更新第 " + (index + 1) + " 套 Kit。");
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        if (kits == null || index < 0 || index >= kits.size() || newOrder < 1 || newOrder > kits.size())
            return Utils.formatAdminError("序号必须在 1 到 " + (kits == null ? 0 : kits.size()) + " 之间。");
        ItemStack item = kits.remove(index);
        kits.add(newOrder - 1, item);
        cfg(session.getTarget()).setKits(kits);
        session.markDirty();
        return Utils.formatAdminSuccess("已将 Kit 调整为第 " + newOrder + " 项。");
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        List<ItemStack> kits = cfg(session.getTarget()).getKits();
        if (kits == null || index < 0 || index >= kits.size()) return null;
        kits.remove(index);
        cfg(session.getTarget()).setKits(kits);
        session.markDirty();
        return Utils.formatAdminSuccess("已删除第 " + (index + 1) + " 套 Kit。");
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
