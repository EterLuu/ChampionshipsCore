package ink.ziip.championshipscore.api.game.laser.presentation;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponSelection;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@ExtendWith(ConfigurationStateExtension.class)
class LaserGunMenuTest {
    private final Map<UUID, Player> connections = new HashMap<>();
    private Server originalServer;

    @BeforeEach
    void prepareServer() throws Exception {
        originalServer = Bukkit.getServer();
        field(Bukkit.class, "server")
                .set(
                        null,
                        proxy(
                                Server.class,
                                (p, m, a) -> {
                                    if (m.getName().equals("getPlayer"))
                                        return connections.get(a[0]);
                                    throw new AssertionError(m.getName());
                                }));
        field(GuiConfig.class, "active").set(null, new YamlConfiguration());
    }

    @AfterEach
    void restoreServer() throws Exception {
        field(Bukkit.class, "server").set(null, originalServer);
    }

    @Test
    void everyButtonSelectsExactlyOnceAndCannotMoveItems() throws Exception {
        LaserGun[] guns = LaserGun.values();
        for (int i = 0; i < guns.length; i++) {
            var f = new Fixture();
            var click = f.click(10 + i);
            assertTrue(LaserGunMenu.handleClick(f.owner, click));
            assertTrue(click.isCancelled());
            LaserGunMenu.handleClick(f.owner, click);
            assertEquals(List.of(guns[i]), f.chosen);
            assertEquals(1, f.closes);
        }
    }

    @Test
    void bottomInventoryEmptySlotsAndOutsideClicksNeverSelect() throws Exception {
        var f = new Fixture();
        for (int slot : List.of(-999, -1, 0, 9, 15, 26, 27, 37, 63)) {
            var click = f.click(slot);
            assertTrue(LaserGunMenu.handleClick(f.owner, click));
            assertTrue(click.isCancelled());
        }
        assertTrue(f.chosen.isEmpty());
        assertEquals(0, f.closes);
    }

    @Test
    void obsoleteViewsAndOtherViewersCannotSelectOrCloseTheCurrentMenu() throws Exception {
        for (String reason : List.of("invalid", "other-view", "other-player", "closed")) {
            var f = new Fixture();
            switch (reason) {
                case "invalid" -> f.current = false;
                case "other-view" -> f.openHolder = null;
                case "other-player" -> f.eventPlayer = proxy(Player.class, (p, m, a) -> null);
                case "closed" -> LaserGunMenu.handleClose(f.owner, new InventoryCloseEvent(f.view));
            }
            var click = f.click(10);
            assertTrue(LaserGunMenu.handleClick(f.owner, click));
            assertTrue(click.isCancelled());
            assertTrue(f.chosen.isEmpty(), reason);
            if (reason.equals("other-player")) {
                LaserGunMenu.handleClose(f.owner, new InventoryCloseEvent(f.view));
                assertEquals(0, f.closes);
            }
        }
        var f = new Fixture();
        assertFalse(LaserGunMenu.handleClick(new Object(), f.click(10)));
        LaserGunMenu.handleClose(new Object(), new InventoryCloseEvent(f.view));
        assertTrue(LaserGunMenu.isOpen(f.owner, f.player));
        assertEquals(0, f.closes);
    }

    @Test
    void closingExpiredMenuReleasesGraceOnceAndOldClickCannotReopenIt() throws Exception {
        var f = new Fixture();
        assertTrue(f.life.openMenu(159));
        f.tick = 500;
        assertTrue(LaserGunMenu.isOpen(f.owner, f.player));
        var close = new InventoryCloseEvent(f.view);
        LaserGunMenu.handleClose(f.owner, close);
        LaserGunMenu.handleClose(f.owner, close);
        assertEquals(1, f.closes);
        assertTrue(f.life.locked());
        assertFalse(LaserGunMenu.isOpen(f.owner, f.player));
        LaserGunMenu.handleClick(f.owner, f.click(10));
        assertTrue(f.chosen.isEmpty());
    }

    @Test
    void selectionCallbackCannotCloseReplacementViewAndReleasesGraceOnce() throws Exception {
        var f = new Fixture();
        var replacement = new Object();
        f.selection =
                gun -> {
                    f.chosen.add(gun);
                    f.openHolder = replacement;
                };
        LaserGunMenu.handleClick(f.owner, f.click(10));
        assertEquals(List.of(LaserGun.SNIPER), f.chosen);
        assertSame(replacement, f.openHolder);
        assertEquals(0, f.inventoryCloses);
        assertEquals(1, f.closes);
        LaserGunMenu.handleClose(f.owner, new InventoryCloseEvent(f.view));
        assertEquals(1, f.closes);
    }

    @Test
    void disconnectedAndReplacedConnectionsCannotSelectButExpiredCurrentViewCanClose()
            throws Exception {
        var old = new Fixture();
        connections.put(old.id, proxy(Player.class, (p, m, a) -> null));
        LaserGunMenu.handleClick(old.owner, old.click(10));
        LaserGunMenu.closeOpen(old.owner, old.player);
        assertTrue(old.chosen.isEmpty());
        assertEquals(0, old.inventoryCloses);
        var expired = new Fixture();
        expired.current = false;
        LaserGunMenu.closeOpen(expired.owner, expired.player);
        assertEquals(1, expired.closes);
    }

    @Test
    void clicksResolveRenderedTargetsAfterSlotConfigurationReload() throws Exception {
        var doc = new YamlConfiguration();
        doc.set("laser.menus.gun-selection.layout.content", List.of(16, 10));
        field(GuiConfig.class, "active").set(null, doc);
        var f = new Fixture();
        doc.set("laser.menus.gun-selection.layout.content", List.of(11));
        LaserGunMenu.handleClick(f.owner, f.click(11));
        assertTrue(f.chosen.isEmpty());
        LaserGunMenu.handleClick(f.owner, f.click(10));
        assertEquals(List.of(LaserGun.RIFLE), f.chosen);
    }

    private final class Fixture {
        final Object owner = new Object();
        final UUID id = UUID.randomUUID();
        final LaserWeaponSelection life = new LaserWeaponSelection(0);
        final List<LaserGun> chosen = new ArrayList<>();
        Consumer<LaserGun> selection = chosen::add;
        final LaserGunMenu menu;
        Inventory inventory;
        final Player player;
        InventoryView view;
        Object openHolder;
        Player eventPlayer;
        boolean current = true;
        int closes, tick, inventoryCloses;

        Fixture() throws Exception {
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getOpenInventory" -> openView();
                                        case "isOnline" -> true;
                                        case "getUniqueId" -> id;
                                        case "closeInventory" -> {
                                            inventoryCloses++;
                                            LaserGunMenu.handleClose(
                                                    owner, new InventoryCloseEvent(view));
                                            yield null;
                                        }
                                        case "sendMessage" -> null;
                                        default -> throw new AssertionError(m.getName());
                                    });
            connections.put(id, player);
            eventPlayer = player;
            var constructor =
                    LaserGunMenu.class.getDeclaredConstructor(
                            Object.class,
                            Player.class,
                            BooleanSupplier.class,
                            Consumer.class,
                            Runnable.class);
            constructor.setAccessible(true);
            menu =
                    constructor.newInstance(
                            owner,
                            player,
                            (BooleanSupplier) () -> current && life.canSelect(tick),
                            (Consumer<LaserGun>) gun -> selection.accept(gun),
                            (Runnable)
                                    () -> {
                                        closes++;
                                        life.closeMenu(tick);
                                    });
            openHolder = menu;
            inventory =
                    proxy(
                            Inventory.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getHolder" -> menu;
                                        case "getSize" -> 27;
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(menu, "inventory", inventory);
            var layout = (MenuLayout) field(LaserGunMenu.class, "layout").get(menu);
            @SuppressWarnings("unchecked")
            var targets =
                    (Map<Integer, LaserGun>) field(LaserGunMenu.class, "gunsBySlot").get(menu);
            var slots = layout.pageSlots(0, LaserGun.values().length);
            for (int i = 0; i < slots.size(); i++) targets.put(slots.get(i), LaserGun.values()[i]);
            view =
                    proxy(
                            InventoryView.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getTopInventory" -> inventory;
                                        case "getPlayer" -> eventPlayer;
                                        case "convertSlot" -> a[0];
                                        default -> throw new AssertionError(m.getName());
                                    });
        }

        private InventoryView openView() {
            Inventory top =
                    openHolder == menu
                            ? inventory
                            : proxy(Inventory.class, (p, m, a) -> openHolder);
            return proxy(InventoryView.class, (p, m, a) -> top);
        }

        InventoryClickEvent click(int slot) {
            return new InventoryClickEvent(
                    view,
                    InventoryType.SlotType.CONTAINER,
                    slot,
                    ClickType.LEFT,
                    InventoryAction.PICKUP_ALL);
        }
    }
}
