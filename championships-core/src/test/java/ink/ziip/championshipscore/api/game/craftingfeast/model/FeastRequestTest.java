package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

class FeastRequestTest {
    @Test
    void clueAcceptsEitherAnswerIncludingDecoratedItemsAndRejectsMixedWrongContents() {
        var request =
                new FeastRequest(
                        "clue_017",
                        "用8块木板围边合成的音乐方块",
                        java.util.Set.of(Material.NOTE_BLOCK, Material.JUKEBOX),
                        true,
                        1,
                        100,
                        null,
                        null);
        var first = new Stack(Material.NOTE_BLOCK, 4, true);
        var other = new Stack(Material.JUKEBOX, 1, false);
        var wrong = new Stack(Material.BREAD, 1, false);
        assertTrue(request.accepts(first));
        assertTrue(request.accepts(other));
        assertFalse(request.accepts(wrong));
        assertTrue(request.fills(List.of(first, other)));
        assertFalse(request.fills(List.of(first, wrong)));
        assertFalse(request.fills(List.of()));
    }

    @Test
    void requiresExactMaterialQuantityAndPlainVanillaItems() {
        var request = new FeastRequest("cookie", "曲奇", Material.COOKIE, 4, 120);
        assertFalse(request.accepts(null));
        assertFalse(request.accepts(new Stack(Material.COOKIE, 3, false)));
        assertFalse(request.accepts(new Stack(Material.BREAD, 4, false)));
        assertFalse(request.accepts(new Stack(Material.COOKIE, 4, true)));
        assertTrue(request.accepts(new Stack(Material.COOKIE, 4, false)));
        assertTrue(request.accepts(new Stack(Material.COOKIE, 8, false)));
    }

    @Test
    void deliverySlotsNeedOnlyTheRequestedPlainItemsInFullAmount() {
        var request = new FeastRequest("cookie", "曲奇", Material.COOKIE, 4, 120);
        assertFalse(request.fills(java.util.Arrays.asList(null, null)), "Empty slot");
        assertFalse(request.fills(List.of(new Stack(Material.COOKIE, 3, false))));
        assertTrue(
                request.fills(
                        java.util.Arrays.asList(
                                new Stack(Material.COOKIE, 2, false),
                                null,
                                new Stack(Material.COOKIE, 2, false))),
                "Split stacks count together");
        assertFalse(
                request.fills(
                        List.of(
                                new Stack(Material.COOKIE, 4, false),
                                new Stack(Material.BREAD, 1, false))),
                "A wrong item spoils the order");
        assertFalse(request.fills(List.of(new Stack(Material.COOKIE, 4, true))));
        assertEquals(150, request.deliveryPoints(25));
        assertEquals(120, request.deliveryPoints(0));
    }

    @Test
    void badgeTierFollowsBasePoints() {
        int[][] cases = {
            {1, 1},
            {100, 1},
            {101, 2},
            {130, 2},
            {131, 3},
            {160, 3},
            {161, 4},
            {190, 4},
            {191, 5},
            {10000, 5}
        };
        for (int[] c : cases)
            assertEquals(
                    c[1],
                    new FeastRequest("x", "x", Material.BREAD, 1, c[0]).tier(),
                    "points " + c[0]);
    }

    @Test
    void markingsAreOptionalButNeverBlank() {
        var plain = new FeastRequest("bread", "面包", Material.BREAD, 1, 100);
        assertNull(plain.profession());
        assertNull(plain.biome());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastRequest("bread", "面包", Material.BREAD, 1, 100, " ", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastRequest("bread", "面包", Material.BREAD, 1, 100, null, ""));
    }

    private static final class Stack extends ItemStack {
        private final Material type;
        private final int amount;
        private final boolean meta;

        Stack(Material type, int amount, boolean meta) {
            this.type = type;
            this.amount = amount;
            this.meta = meta;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public boolean hasItemMeta() {
            return meta;
        }
    }
}
