package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.*;

class SliderArtTest {
    @Test
    void allEightMatchCardsAreDifferentAndRepeatable() {
        var coverage = EnumSet.noneOf(SliderArt.Scene.class);
        assertEquals(22, SliderArt.Scene.values().length);
        for (long seed = -25; seed < 25; seed++) {
            Set<SliderArt.Scene> dealt = EnumSet.noneOf(SliderArt.Scene.class);
            for (var station : SliderHall.values())
                for (int round = 1; round <= 2; round++) {
                    var card = SliderArt.postcard(seed, station, round);
                    assertEquals(card, SliderArt.postcard(seed, station, round));
                    assertTrue(dealt.add(card.scene()));
                }
            assertEquals(8, dealt.size());
            coverage.addAll(dealt);
        }
        assertEquals(EnumSet.allOf(SliderArt.Scene.class), coverage);
    }

    @Test
    void artworkScalesAndIsDeterministicAtTheChosenMapResolution() {
        var card = SliderArt.postcard(234, SliderHall.C, 2);
        var original = card.draw();
        assertEquals(2048, original.getWidth());
        assertEquals(1024, original.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, original.getType());
        assertArrayEquals(
                ((DataBufferInt) original.getRaster().getDataBuffer()).getData(),
                ((DataBufferInt) card.draw().getRaster().getDataBuffer()).getData());
        var small = card.draw(1792, 896);
        assertEquals(896, small.getHeight());
        assertThrows(IllegalArgumentException.class, () -> card.draw(640, 640));
        int agreements = 0;
        for (int x = 16; x < 2048; x += 32)
            for (int y = 16; y < 1024; y += 32)
                if (original.getRGB(x, y) == small.getRGB(x * 7 / 8, y * 7 / 8)) agreements++;
        assertTrue(agreements > 1900, "The artwork should occupy the same normalized coordinates.");
    }

    @Test
    void eachSceneHasDistinctLightingAndSeedVariationsAfterMapQuantization() {
        for (var scene : SliderArt.Scene.values()) {
            Set<Integer> fingerprints = new HashSet<>();
            for (var time : SliderArt.Time.values())
                for (long seed : new long[] {37, 902}) {
                    var img = new SliderArt.Postcard(scene, time, seed).draw(480, 240);
                    var bytes = SliderArt.mapPixels(img);
                    assertEquals(480 * 240, bytes.length);
                    int[] counts = new int[256];
                    for (byte b : bytes) counts[Byte.toUnsignedInt(b)]++;
                    assertEquals(0, counts[0]);
                    assertTrue(
                            Arrays.stream(counts).filter(n -> n >= 100).count() >= 8,
                            scene + " " + time);
                    assertTrue(fingerprints.add(Arrays.hashCode(bytes)), scene + " " + time);
                    for (int y = 0; y < 240; y += 29)
                        for (int x = 0; x < 480; x += 23) {
                            int rgb = img.getRGB(x, y);
                            assertEquals(255, rgb >>> 24);
                            assertEquals(
                                    MapColorMatcher.matchColor(
                                            (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255),
                                    bytes[y * 480 + x]);
                        }
                }
        }
    }
}
