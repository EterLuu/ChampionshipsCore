package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.MapColorMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.map.MapCanvas;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BingoCardMapRendererTest {
    @Test
    void completionRefreshesToFullOwnBorderEvenAfterFourOtherTeams() {
        for (int tiers : new int[]{0, 2, 4, 6}) {
            BingoCard card = card();
            GameTask task = card.getTasks().getFirst();
            BingoCardMapRenderer renderer = new BingoCardMapRenderer(card, "own", NamedTextColor.GREEN, tiers);
            Pixels pixels = new Pixels();
            renderer.render(null, pixels.canvas, null);

            complete(task, "red", NamedTextColor.RED);
            complete(task, "blue", NamedTextColor.BLUE);
            renderer.render(null, pixels.canvas, null);
            assertTrue(pixels.firstBorder().contains(palette(NamedTextColor.RED)));
            assertTrue(pixels.firstBorder().contains(palette(NamedTextColor.BLUE)));

            complete(task, "yellow", NamedTextColor.YELLOW);
            complete(task, "white", NamedTextColor.WHITE);
            renderer.render(null, pixels.canvas, null);
            complete(task, "own", NamedTextColor.GREEN);
            renderer.render(null, pixels.canvas, null);
            assertEquals(Set.of(palette(NamedTextColor.GREEN)), pixels.firstBorder());

            complete(task, "late", NamedTextColor.AQUA);
            renderer.render(null, pixels.canvas, null);
            assertEquals(Set.of(palette(NamedTextColor.GREEN)), pixels.firstBorder());
        }
    }

    @Test
    void spectatorKeepsAllCompletionColors() {
        BingoCard card = card();
        complete(card.getTasks().getFirst(), "red", NamedTextColor.RED);
        complete(card.getTasks().getFirst(), "blue", NamedTextColor.BLUE);
        Pixels pixels = new Pixels();
        new BingoCardMapRenderer(card, null, null).render(null, pixels.canvas, null);
        assertEquals(Set.of(palette(NamedTextColor.RED), palette(NamedTextColor.BLUE)), pixels.firstBorder());
    }

    @Test
    void playerAndSpectatorBordersShowAtMostSixTeams() {
        for (String viewer : new String[]{"own", null}) {
            BingoCard card = card();
            GameTask task = card.getTasks().getFirst();
            BingoCardMapRenderer renderer = new BingoCardMapRenderer(card, viewer,
                    viewer == null ? null : NamedTextColor.GREEN);
            Pixels pixels = new Pixels();
            Set<Color> expected = new HashSet<>();
            TextColor[] colors = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.YELLOW,
                    NamedTextColor.WHITE, NamedTextColor.AQUA, NamedTextColor.GOLD};
            for (int index = 0; index < colors.length; index++) {
                complete(task, "other-" + index, colors[index]);
                expected.add(palette(colors[index]));
                renderer.render(null, pixels.canvas, null);
                assertEquals(expected, pixels.firstBorder());
            }

            complete(task, "seventh", NamedTextColor.DARK_PURPLE);
            renderer.render(null, pixels.canvas, null);
            assertEquals(expected, pixels.firstBorder());
            complete(task, "own", NamedTextColor.GREEN);
            renderer.render(null, pixels.canvas, null);
            assertEquals(viewer == null ? expected : Set.of(palette(NamedTextColor.GREEN)), pixels.firstBorder());
        }
    }

    @Test
    void liveRowsColumnsAndBothDiagonalsAppearOnlyWhenOwnLineIsComplete() {
        int[][] lines = {{0, 1, 2}, {0, 3, 6}, {0, 4, 8}, {2, 4, 6}};
        int[][] gaps = {{51, 40}, {40, 51}, {52, 52}, {76, 52}};
        for (int index = 0; index < lines.length; index++) {
            BingoCard card = card();
            BingoCardMapRenderer renderer = new BingoCardMapRenderer(card, "own", NamedTextColor.GREEN);
            Pixels pixels = new Pixels();
            for (int cell : lines[index]) complete(card.getTasks().get(cell), "other", NamedTextColor.RED);
            complete(card.getTasks().get(lines[index][0]), "own", NamedTextColor.GREEN);
            complete(card.getTasks().get(lines[index][1]), "own", NamedTextColor.GREEN);
            renderer.render(null, pixels.canvas, null);
            int x = gaps[index][0], y = gaps[index][1];
            assertNotEquals(palette(NamedTextColor.GREEN), pixels.colors[y][x]);

            complete(card.getTasks().get(lines[index][2]), "own", NamedTextColor.GREEN);
            renderer.render(null, pixels.canvas, null);
            assertEquals(palette(NamedTextColor.GREEN), pixels.colors[y][x]);
        }
    }

    private static BingoCard card() {
        ArrayList<GameTask> tasks = new ArrayList<>();
        for (int cell = 0; cell < 9; cell++) tasks.add(new GameTask(new ItemTask(Material.STONE)));
        return new BingoCard(CardSize.X3, tasks);
    }

    private static void complete(GameTask task, String team, TextColor color) {
        assertTrue(task.complete(new GameTask.Completion(null, Component.empty(), color, team, 1L), false));
    }

    private static Color palette(TextColor color) {
        return MapColorMatcher.color(MapColorMatcher.matchColor(color.red(), color.green(), color.blue()));
    }

    private static final class Pixels {
        final Color[][] colors = new Color[128][128];
        final MapCanvas canvas = (MapCanvas) Proxy.newProxyInstance(MapCanvas.class.getClassLoader(),
                new Class<?>[]{MapCanvas.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setPixelColor")) {
                        colors[(int) args[1]][(int) args[0]] = (Color) args[2];
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        Set<Color> firstBorder() {
            Set<Color> result = new HashSet<>();
            for (int y = 1; y < 23; y++) {
                for (int x = 1; x < 23; x++) {
                    if (x < 3 || x >= 21 || y < 3 || y >= 21) result.add(colors[28 + y][28 + x]);
                }
            }
            return result;
        }
    }
}
