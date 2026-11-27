package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastDelivery;
import ink.ziip.championshipscore.api.game.craftingfeast.model.FeastRequest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

class FeastDeliveryDeskTest {
    @Test
    void boardHidesContentsAndOnlyTimesAnActiveRider() throws Exception {
        var desk = allocate(FeastDeliveryDesk.class);
        var delivery = new FeastDelivery(new FeastDelivery.Timing(1, 10, 1, 2, 5, 1), 2);
        set(desk, "delivery", delivery);
        set(desk, "inventories", new HashMap<>());
        String idle =
                FeastKitchen.text("delivery-header") + "\n" + FeastKitchen.text("delivery-none");
        assertEquals(idle, desk.boardText());
        var request = new FeastRequest("secret", "秘密菜品", Material.BREAD, 7, 100);
        delivery.tick(i -> request);
        assertTrue(desk.boardText().contains(FeastKitchen.text("delivery-eta", 10)));
        assertTrue(desk.boardText().contains("W1"));
        assertFalse(desk.boardText().contains("秘密菜品"));
        assertFalse(desk.boardText().contains("×7"));
        for (int i = 0; i < 10; i++) delivery.tick(n -> request);
        assertTrue(desk.boardText().contains(FeastKitchen.text("delivery-waiting", 5)));
        delivery.pickUp();
        assertEquals(idle, desk.boardText());
        delivery.tick(i -> request);
        assertTrue(desk.boardText().contains("W3"));
        for (int i = 0; i < 15; i++) delivery.tick(n -> request);
        assertEquals(idle, desk.boardText());
    }
}
