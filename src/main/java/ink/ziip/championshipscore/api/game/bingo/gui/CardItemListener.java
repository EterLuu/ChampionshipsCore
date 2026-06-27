package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.bingo.BingoArea;
import ink.ziip.championshipscore.api.game.bingo.game.BingoRound;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Right-clicking a card map opens the read-only detail chest GUI for the holder's team card. */
public final class CardItemListener extends BaseListener {

    public CardItemListener(ChampionshipsCore plugin) {
        super(plugin);
    }

    /** The bingo round the player is currently in, or null. */
    private BingoArea bingoAreaOf(Player player) {
        BaseArea area = plugin.getGameManager().getBasePlayerArea(player.getUniqueId());
        return area instanceof BingoArea bingoArea ? bingoArea : null;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (CardMapItem.CARD_KEY == null) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FILLED_MAP) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String teamId = meta.getPersistentDataContainer().get(CardMapItem.CARD_KEY, PersistentDataType.STRING);
        if (teamId == null) return;

        if (a == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType().isInteractable()) return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && isOffhandWeapon(event.getPlayer().getInventory().getItemInOffHand().getType())) return;

        Player player = event.getPlayer();
        BingoArea bingoArea = bingoAreaOf(player);
        if (bingoArea == null) return;
        BingoRound round = bingoArea.getRound();
        if (round == null) return;

        event.setCancelled(true);

        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        var msg = MessageService.global();
        round.cardFor(team).ifPresent(card ->
                CardView.open(player, card, round.displayInfo(),
                        msg.component("card.map_name", team.getName()), BingoTeamAdapter.id(team)));
    }

    private static boolean isOffhandWeapon(Material m) {
        return switch (m) {
            case SHIELD, TRIDENT, BOW, CROSSBOW -> true;
            default -> false;
        };
    }

    /** The card map is a bound tool, not loot — players cannot drop it. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (CardMapItem.isCard(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** A card that reaches the ground may only be reclaimed by a member of its own team. */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (CardMapItem.CARD_KEY == null || !CardMapItem.isCard(stack)) return;
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        String teamId = meta == null ? null
                : meta.getPersistentDataContainer().get(CardMapItem.CARD_KEY, PersistentDataType.STRING);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (teamId == null || team == null || !teamId.equals(BingoTeamAdapter.id(team))) {
            event.setCancelled(true);
        }
    }
}
