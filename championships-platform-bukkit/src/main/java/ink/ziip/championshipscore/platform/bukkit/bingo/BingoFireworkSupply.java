package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

/** Match-only flight supplies shared by local Core and the dedicated worker. */
public final class BingoFireworkSupply {
    public static final int INTERVAL_SECONDS = 20;
    private int lastWindow;

    /** Each elapsed window grants one refill, even when a timer repeats or skips a second. */
    public boolean shouldRefill(int elapsedSeconds) {
        int window = Math.max(0, elapsedSeconds) / INTERVAL_SECONDS;
        if (window <= lastWindow) return false;
        lastWindow = window;
        return true;
    }

    public static void give(Player player) {
        if (player == null || !player.isOnline() || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR) return;
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET, 1);
        FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
        meta.setPower(2);
        rocket.setItemMeta(meta);
        for (ItemStack overflow : player.getInventory().addItem(rocket).values()) {
            player.getWorld().dropItem(player.getLocation(), overflow);
        }
    }
}
