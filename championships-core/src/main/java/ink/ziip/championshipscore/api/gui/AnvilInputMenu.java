package ink.ziip.championshipscore.api.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns native input windows and their callbacks; domain owners supply current-context admission.
 */
public final class AnvilInputMenu implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Session> pending = new HashMap<>();
    // Retired views remain read-only without retaining their owner or callbacks.
    private final Map<Inventory, Boolean> views = new WeakHashMap<>();
    private boolean closed;

    public AnvilInputMenu(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public record Prompt(String title, String initialValue, ItemStack input) {
        public Prompt {
            input = input.clone();
            input.editMeta(meta -> meta.displayName(LegacyText.plainComponent(initialValue)));
        }

        @Override
        public ItemStack input() {
            return input.clone();
        }

        public static Prompt configured(String path, String title, String value, Object hint) {
            var spec = GuiConfig.item(path, Map.of("prompt", title, "value", value, "hint", hint));
            return new Prompt(
                    title,
                    value,
                    GuiMenu.item(spec.material(), spec.title(), spec.lore(), spec.glint()));
        }
    }

    public record Callbacks(
            BooleanSupplier admitted,
            Function<String, String> validate,
            Consumer<String> submit,
            Consumer<String> error,
            Runnable cancel,
            Runnable expired) {}

    private record Session(Object owner, Player player, Inventory inventory, Callbacks callbacks) {}

    public void open(Object owner, Player player, Prompt prompt, Callbacks callbacks) {
        if (!connected(player) || !callbacks.admitted().getAsBoolean()) return;
        AnvilView view = MenuType.ANVIL.create(player, LegacyText.plainComponent(prompt.title()));
        Inventory inventory = view.getTopInventory();
        inventory.setItem(0, prompt.input());
        Session next = new Session(owner, player, inventory, callbacks);
        Session previous = sessions.put(player.getUniqueId(), next);
        pending.remove(player.getUniqueId());
        views.put(inventory, Boolean.TRUE);
        if (previous != null) previous.inventory().clear();
        player.openInventory(view);
        if (sessions.get(player.getUniqueId()) != next || !current(next)) {
            retire(next, true);
            return;
        }
        view.setMaximumRepairCost(0);
        view.setRepairCost(0);
    }

    public boolean owns(Inventory inventory) {
        return views.containsKey(inventory);
    }

    private boolean connected(Player player) {
        return !closed
                && plugin.isEnabled()
                && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private boolean admitted(Session session) {
        return connected(session.player()) && session.callbacks().admitted().getAsBoolean();
    }

    private boolean current(Session session) {
        return admitted(session)
                && sessions.get(session.player().getUniqueId()) == session
                && session.player().getOpenInventory().getTopInventory() == session.inventory();
    }

    private static boolean defaultView(Player player) {
        InventoryType type = player.getOpenInventory().getType();
        return type == InventoryType.CRAFTING || type == InventoryType.CREATIVE;
    }

    private void retire(Session session, boolean closeView) {
        sessions.remove(session.player().getUniqueId(), session);
        pending.remove(session.player().getUniqueId(), session);
        session.inventory().clear();
        if (closeView
                && session.player().isOnline()
                && Bukkit.getPlayer(session.player().getUniqueId()) == session.player()
                && session.player().getOpenInventory().getTopInventory() == session.inventory()) {
            session.player().closeInventory();
        }
    }

    public void close(Object owner, Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.owner() == owner && session.player() == player)
            retire(session, true);
        Session queued = pending.get(player.getUniqueId());
        if (queued != null && queued.owner() == owner && queued.player() == player)
            pending.remove(player.getUniqueId(), queued);
    }

    public void closeOwner(Object owner) {
        for (Session session : List.copyOf(sessions.values())) {
            if (session.owner() == owner) retire(session, true);
        }
        pending.values().removeIf(session -> session.owner() == owner);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pending.clear();
        for (Session session : List.copyOf(sessions.values())) retire(session, true);
        HandlerList.unregisterAll(this);
        views.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!views.containsKey(inventory)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player() != player || session.inventory() != inventory)
            return;
        if (!current(session)) {
            boolean notify = connected(player);
            retire(session, true);
            if (notify && session.callbacks().expired() != null)
                session.callbacks().expired().run();
            return;
        }
        if (event.getRawSlot() != 2) return;
        String value = ((AnvilView) event.getView()).getRenameText();
        String text = value == null ? "" : value.trim();
        String error = session.callbacks().validate().apply(text);
        if (!current(session)) return;
        if (error != null) {
            session.callbacks().error().accept(error);
            return;
        }
        retire(session, false);
        pending.put(player.getUniqueId(), session);
        player.closeInventory();
        // Closing can synchronously revoke permission, replace the context or open another view.
        if (pending.remove(player.getUniqueId(), session)
                && admitted(session)
                && defaultView(player)) {
            session.callbacks().submit().accept(text);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (views.containsKey(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!views.containsKey(event.getInventory())) return;
        event.getView().setMaximumRepairCost(0);
        event.getView().setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session queued = pending.get(player.getUniqueId());
        if (queued != null && queued.player() == player)
            pending.remove(player.getUniqueId(), queued);
        Session session = sessions.get(player.getUniqueId());
        if (session != null
                && session.player() == player
                && session.inventory() != event.getInventory()) retire(session, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null
                || session.player() != player
                || session.inventory() != event.getInventory()) return;
        retire(session, false);
        if (!admitted(session) || session.callbacks().cancel() == null) return;
        pending.put(player.getUniqueId(), session);
        var task =
                player.getScheduler()
                        .runDelayed(
                                plugin,
                                ignored -> {
                                    if (!pending.remove(player.getUniqueId(), session)
                                            || !admitted(session)
                                            || sessions.containsKey(player.getUniqueId())
                                            || !defaultView(player)) return;
                                    session.callbacks().cancel().run();
                                },
                                () -> pending.remove(player.getUniqueId(), session),
                                1L);
        if (task == null) pending.remove(player.getUniqueId(), session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.player() == player) retire(session, false);
        Session queued = pending.get(player.getUniqueId());
        if (queued != null && queued.player() == player)
            pending.remove(player.getUniqueId(), queued);
    }
}
