package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team-teleport compass (part of the bingo starter kit). Left-clicking a compass while a bingo round
 * runs opens a chest menu of online teammates - each an ender pearl labelled with the teammate's name
 * in team colour. Ender pearls (not player heads) are used deliberately: this server runs in offline
 * mode, so player-head skin textures are unreliable, and the pearl + name reads clearly regardless.
 * Clicking a pearl teleports the holder to that teammate. The compass can't be dropped mid-round.
 *
 * <p>Restricted to the live {@link GameStageEnum#PROGRESS} stage and to actual participants; targets
 * are online teammates currently in the bingo area (spectators are excluded - teleporting to a
 * spectator would yank the player out of play).
 */
public final class BingoCompassListener extends BaseListener {
    private static final String MENU_PATH = MenuId.BINGO_TEAMMATE_TELEPORT.path();

    /** PDC key on each ender-pearl button; value is the target player's UUID as a string. */
    public static final NamespacedKey TARGET_KEY =
            NamespacedKey.fromString("championshipscore:bingo_compass_target");

    public BingoCompassListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    /** The bingo round the player is currently in, or null. */
    private @Nullable BingoArea bingoAreaOf(Player player) {
        BaseGameInstance area = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        return area instanceof BingoArea bingoArea ? bingoArea : null;
    }

    // ── open menu on either click (supports both legacy rules text and current item hint) ──────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK
                && a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        BingoArea area = bingoAreaOf(player);
        if (area == null || area.getGameStageEnum() != GameStageEnum.PROGRESS) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        if (a == Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        openTeleportMenu(player, area);
    }

    private void openTeleportMenu(Player player, BingoArea area) {
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;

        // Online teammates currently in the bingo area (excludes self, offline members, spectators).
        List<Player> teammates = new ArrayList<>();
        for (UUID uuid : team.getMembers()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || area.notAreaPlayer(target)) continue;
            teammates.add(target);
        }
        if (teammates.isEmpty()) {
            player.sendMessage(Component.text(MessageConfig.MAP_EDITOR_BINGO_NO_TEAMMATES).color(NamedTextColor.YELLOW));
            return;
        }
        teammates.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int rows = Math.max(1, Math.min(6, (teammates.size() + 8) / 9));
        CompassHolder holder = new CompassHolder();
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, rows * 9, "",
                java.util.stream.IntStream.range(0, rows * 9).boxed().toList());
        Inventory inv = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.setInventory(inv);

        for (int i = 0; i < teammates.size() && i < menu.contentSlots().size(); i++) {
            inv.setItem(menu.contentSlots().get(i), button(teammates.get(i), team));
        }
        player.openInventory(inv);
    }

    /** An ender pearl labelled with the standard player/team identity, tagged with their UUID. */
    private static ItemStack button(Player target, ChampionshipTeam team) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        item.editMeta(meta -> {
            meta.displayName(Utils.toComponent(Utils.formatPlayerName(target))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(GuiConfig.line(MENU_PATH + ".items.teammate.lore", 0)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            if (TARGET_KEY != null) {
                meta.getPersistentDataContainer().set(TARGET_KEY, PersistentDataType.STRING,
                        target.getUniqueId().toString());
            }
        });
        return ConfiguredGui.item(MENU_PATH + ".items.teammate", null,
                Map.of("player", target.getName()), item);
    }

    // ── teleport on click ──────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CompassHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID targetId = targetOf(event.getCurrentItem());
        if (targetId == null) return;

        BingoArea area = bingoAreaOf(player);
        if (area == null || area.getGameStageEnum() != GameStageEnum.PROGRESS) {
            player.closeInventory();
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || area.notAreaPlayer(target)) {
            player.sendMessage(Component.text(MessageConfig.MAP_EDITOR_BINGO_UNREACHABLE).color(NamedTextColor.YELLOW));
            player.closeInventory();
            return;
        }
        player.closeInventory();
        player.teleportAsync(target.getLocation());
        player.sendMessage(Component.text(MessageConfig.MAP_EDITOR_BINGO_SENT_TO, NamedTextColor.AQUA)
                .append(Utils.toComponent(Utils.formatPlayerName(target))));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CompassHolder) {
            event.setCancelled(true);
        }
    }

    // ── don't let the compass be dropped mid-round ─────────────────────────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        BingoArea area = bingoAreaOf(player);
        if (area == null || area.getGameStageEnum() != GameStageEnum.PROGRESS) return;
        if (event.getItemDrop().getItemStack().getType() == Material.COMPASS) {
            event.setCancelled(true);
        }
    }

    /** Reads the target UUID from a clicked pearl's PDC, or null if the item isn't a button. */
    private static @Nullable UUID targetOf(ItemStack item) {
        if (TARGET_KEY == null || item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(TARGET_KEY, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Marker holder for the team-teleport menu. */
    private static final class CompassHolder implements InventoryHolder {
        private Inventory inventory;

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
