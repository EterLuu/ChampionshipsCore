package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.protocol.*;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.*;

/**
 * Owns the map views and bound card items of one match; role/score decisions stay in the session.
 */
final class WorkerCards {
    static final int SPECTATOR_CARD_TEAM = Integer.MIN_VALUE;
    private final NamespacedKey cardKey;
    private final MatchManifest manifest;
    private final Map<Integer, TeamSnapshot> teams;
    private final int spectatorCardSlot;
    private final BiFunction<Integer, UUID, MapRenderer> renderers;
    private final Function<World, MapView> maps;
    private final Map<Integer, MapView> cardViews = new HashMap<>();
    private final Map<UUID, MapView> playerCardViews = new HashMap<>();
    private MapView spectatorCardView;

    WorkerCards(
            NamespacedKey cardKey,
            MatchManifest manifest,
            int spectatorCardSlot,
            BiFunction<Integer, UUID, MapRenderer> renderers,
            Function<World, MapView> maps) {
        this.cardKey = cardKey;
        this.manifest = manifest;
        this.teams = manifest.teamsById();
        this.spectatorCardSlot = spectatorCardSlot;
        this.renderers = renderers;
        this.maps = maps;
    }

    boolean isSpectatorCard(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || item.getItemMeta() == null)
            return false;
        String value =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(cardKey, PersistentDataType.STRING);
        return (manifest.matchId() + ":spectator").equals(value);
    }

    Integer boundCardTeam(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) return null;
        String value =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(cardKey, PersistentDataType.STRING);
        if (value == null) return null;
        String prefix = manifest.matchId() + ":";
        if (!value.startsWith(prefix)) return null;
        if (value.equals(prefix + "spectator")) return SPECTATOR_CARD_TEAM;
        try {
            int teamId = Integer.parseInt(value.substring(prefix.length()));
            return teams.containsKey(teamId) ? teamId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    synchronized void createViews(World world) {
        if (spectatorCardView != null) return;
        TaskImageAtlas.ensureLoaded();
        var stagedTeams = new HashMap<Integer, MapView>();
        var stagedPlayers = new HashMap<UUID, MapView>();
        var stagedRenderers = new LinkedHashMap<MapView, MapRenderer>();
        try {
            for (TeamSnapshot team : manifest.teams())
                stagedTeams.put(team.id(), createView(world, team.id(), null, stagedRenderers));
            MapView spectator = createView(world, null, null, stagedRenderers);
            if (manifest.scoring().variant().remix() == BingoRemix.DIFFERENTIAL) {
                for (PlayerSnapshot participant : manifest.participants()) {
                    if (participant.role() == ParticipantRole.PLAYER)
                        stagedPlayers.put(
                                participant.uuid(),
                                createView(
                                        world,
                                        participant.teamId(),
                                        participant.uuid(),
                                        stagedRenderers));
                }
            }
            cardViews.putAll(stagedTeams);
            playerCardViews.putAll(stagedPlayers);
            spectatorCardView = spectator;
        } catch (RuntimeException | Error failure) {
            stagedRenderers.forEach(
                    (view, renderer) -> {
                        try {
                            view.removeRenderer(renderer);
                        } catch (RuntimeException | Error cleanup) {
                            failure.addSuppressed(cleanup);
                        }
                    });
            throw failure;
        }
    }

    private MapView createView(
            World world, Integer team, UUID player, Map<MapView, MapRenderer> staged) {
        MapView view = maps.apply(world);
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers()))
            view.removeRenderer(renderer);
        MapRenderer renderer = renderers.apply(team, player);
        staged.put(view, renderer);
        view.addRenderer(renderer);
        return view;
    }

    void closeViews() {
        var views = new java.util.HashSet<MapView>(cardViews.values());
        views.addAll(playerCardViews.values());
        if (spectatorCardView != null) views.add(spectatorCardView);
        for (MapView view : views)
            for (MapRenderer renderer : new ArrayList<>(view.getRenderers()))
                view.removeRenderer(renderer);
        cardViews.clear();
        playerCardViews.clear();
        spectatorCardView = null;
    }

    void ensureCard(Player player, int teamId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (Integer.valueOf(teamId).equals(boundCardTeam(item))) return;
        }
        ItemStack card = cardItem(teamId, player.getUniqueId());
        if (card == null) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir())
            player.getInventory().setItemInOffHand(card);
        else if (!player.getInventory().addItem(card).isEmpty())
            player.getWorld().dropItemNaturally(player.getLocation(), card);
    }

    Component cardTitle(int teamId) {
        TeamSnapshot team = teams.get(teamId);
        return message("card.map_name", "{0}", team == null ? "" : team.name());
    }

    private ItemStack cardItem(int teamId, UUID playerId) {
        MapView view = playerCardViews.getOrDefault(playerId, cardViews.get(teamId));
        if (view == null) return null;
        return item(view, cardTitle(teamId), manifest.matchId() + ":" + teamId);
    }

    private ItemStack spectatorCardItem() {
        if (spectatorCardView == null) return null;
        return item(
                spectatorCardView,
                message("card.spectator_map_name"),
                manifest.matchId() + ":spectator");
    }

    private ItemStack item(MapView view, Component title, String binding) {
        ItemStack card = new ItemStack(Material.FILLED_MAP);
        card.editMeta(
                MapMeta.class,
                meta -> {
                    meta.setMapView(view);
                    meta.displayName(
                            title.decoration(
                                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(
                            List.of(
                                    message("card.map_hint")
                                            .decoration(
                                                    net.kyori.adventure.text.format.TextDecoration
                                                            .ITALIC,
                                                    false)));
                    meta.getPersistentDataContainer()
                            .set(cardKey, PersistentDataType.STRING, binding);
                });
        return card;
    }

    void ensureSpectatorCards(Player player) {
        removeBoundCards(player);
        ItemStack card = spectatorCardItem();
        // Keep the read-only card in its configured dedicated slot. addItem() may choose the
        // spectator
        // controls slot (or any other hotbar slot), so repeated presentation refreshes could
        // overwrite controls and appear to duplicate cards.
        if (card != null) player.getInventory().setItem(spectatorCardSlot, card);
        player.updateInventory();
    }

    private void removeBoundCards(Player player) {
        if (boundCardTeam(player.getInventory().getItemInOffHand()) != null) {
            player.getInventory().setItemInOffHand(null);
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (boundCardTeam(player.getInventory().getItem(slot)) != null) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private Component message(String key, String... replacements) {
        return WorkerPresentationService.message(
                manifest.runtimeRules().presentation(), key, replacements);
    }
}
