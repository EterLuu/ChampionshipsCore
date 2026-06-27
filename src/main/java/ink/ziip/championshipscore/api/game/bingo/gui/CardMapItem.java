package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.util.BingoTeamAdapter;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds the filled-map item that is a team's primary bingo card. Bound to a live
 * {@link BingoCardMapRenderer}, and tagged in PDC (value = team id) so right-clicking it opens the
 * detail chest GUI.
 */
public final class CardMapItem {
    /** PDC key marking a card map; value is the team id. */
    public static final NamespacedKey CARD_KEY = NamespacedKey.fromString("championshipscore:bingo_card");

    private CardMapItem() {
    }

    /** True if the stack is a bingo card map (tagged in PDC), regardless of which team it belongs to. */
    public static boolean isCard(ItemStack item) {
        if (CARD_KEY == null || item == null || item.getType() != Material.FILLED_MAP) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CARD_KEY, PersistentDataType.STRING);
    }

    /**
     * Builds the card map onto a (possibly reused) {@link MapView}. Reusing the same view across rounds
     * keeps the server's map-id counter from climbing without bound; the view's renderer is replaced
     * each call to point at the new card.
     */
    public static ItemStack create(MapView view, World world, BingoCard card, ChampionshipTeam team, int tierSegments) {
        view.setWorld(world);
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        for (MapRenderer existing : new java.util.ArrayList<>(view.getRenderers())) {
            view.removeRenderer(existing);
        }
        view.addRenderer(new BingoCardMapRenderer(card, BingoTeamAdapter.id(team), BingoTeamAdapter.color(team), tierSegments));

        var msg = MessageService.global();
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        item.editMeta(MapMeta.class, meta -> {
            meta.setMapView(view);
            meta.displayName(msg.component("card.map_name", team.getName()));
            meta.lore(java.util.List.of(msg.component("card.map_hint")));
            if (CARD_KEY != null) {
                meta.getPersistentDataContainer().set(CARD_KEY, PersistentDataType.STRING, BingoTeamAdapter.id(team));
            }
        });
        return item;
    }
}
