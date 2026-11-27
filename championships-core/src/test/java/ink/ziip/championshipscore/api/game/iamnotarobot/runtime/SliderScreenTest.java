package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderPuzzle;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderShape;

import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

class SliderScreenTest {
    private static final double EPS = 1e-9;

    private static SliderScreen screen(SliderHall hall) {
        return new SliderScreen(null, null, new Vector(), hall, "", null, null, null);
    }

    private static List<SliderScreen.Panel> panels(SliderShape shape, int column) {
        return SliderScreen.panels(
                new SliderPuzzle(4, List.of(new SliderPuzzle.Notch(column, shape))));
    }

    @Test
    void pieceStartsByCentralAxisAndFollowsTheHolderIncludingRebound() {
        var body =
                panels(SliderShape.STYLES.getFirst(), 12).stream()
                        .filter(p -> p.moves() && p.material() == Material.WHITE_CONCRETE)
                        .findFirst()
                        .orElseThrow();
        for (var hall : SliderHall.values()) {
            var screen = screen(hall);
            int run = hall == SliderHall.A || hall == SliderHall.C ? -1 : 1;
            double start = screen.panelBase(body, 0).getX() + body.w() / 2;
            double nearEdge = run < 0 ? 8 : 11;
            assertEquals(nearEdge + run * (SliderHall.MARGIN + body.w() / 2), start, EPS);
            for (int col = 0; col <= SliderHall.LAST; col++) {
                var nudged = screen.panelBase(body, SliderStation.reboundColumn(col));
                double direction = col == SliderHall.LAST ? -1 : 1;
                assertEquals(
                        direction * .35 * (SliderHall.coreLeft(1) - SliderHall.coreLeft(0)),
                        run * (nudged.getX() - screen.panelBase(body, col).getX()),
                        EPS);
            }
            // The far-end nudge keeps the handle inside the 16-block bar.
            assertTrue(
                    SliderStation.reboundColumn(SliderHall.LAST)
                                            * (SliderHall.MAP_COLUMNS - .9)
                                            / SliderHall.LAST
                                    + .9
                            <= SliderHall.MAP_COLUMNS);
            for (double c : new double[] {1, 10, 10.35, 21.65, 22, 0}) {
                double center = screen.panelBase(body, c).getX() + body.w() / 2;
                assertEquals(
                        start + run * c * (SliderHall.coreLeft(1) - SliderHall.coreLeft(0)),
                        center,
                        EPS);
            }
        }
    }

    @Test
    void allShapeFillsAlignWithTheirNotchAndCastAViewerRightDownShadow() {
        for (var hall : SliderHall.values()) {
            var screen = screen(hall);
            for (var shape : SliderShape.STYLES) {
                for (int c = 0; c <= SliderHall.LAST; c++) {
                    var layers = panels(shape, c);
                    var notch =
                            layers.stream()
                                    .filter(
                                            p ->
                                                    !p.moves()
                                                            && p.material()
                                                                    == Material.BLACK_STAINED_GLASS)
                                    .toList();
                    var piece =
                            layers.stream()
                                    .filter(
                                            p ->
                                                    p.moves()
                                                            && p.material()
                                                                    == Material.WHITE_CONCRETE)
                                    .toList();
                    var shadow =
                            layers.stream()
                                    .filter(
                                            p ->
                                                    p.moves()
                                                            && p.material()
                                                                    == Material.BLACK_STAINED_GLASS)
                                    .toList();
                    assertEquals(shape.fill.size(), piece.size());
                    assertEquals(piece.size(), notch.size());
                    assertEquals(piece.size(), shadow.size());
                    for (int i = 0; i < piece.size(); i++) {
                        var n = screen.panelBase(notch.get(i), 0);
                        var p = screen.panelBase(piece.get(i), c);
                        var s = screen.panelBase(shadow.get(i), c);
                        assertEquals(n.getX(), p.getX());
                        assertEquals(n.getY(), p.getY());
                        assertEquals(notch.get(i).w(), piece.get(i).w());
                        assertEquals(notch.get(i).h(), piece.get(i).h());
                        // Depth deliberately separates the overlaid planes to prevent z-fighting.
                        assertEquals(.25 - .17, (p.getZ() - n.getZ()) * hall.step, EPS);
                        assertEquals(.088, (s.getX() - p.getX()) * hall.step, EPS);
                        assertEquals(-.104, s.getY() - p.getY(), EPS);
                    }
                }
            }
        }
    }

    @Test
    void handleAndProgressShareTheNearEdgeAndFollowThePieceToCompletion() {
        for (var hall : SliderHall.values()) {
            var screen = screen(hall);
            int run = hall == SliderHall.A || hall == SliderHall.C ? -1 : 1;
            double edge = run < 0 ? 8 : 11;
            double fillAnchor = screen.fillBase(.01).getX();
            for (double c : new double[] {0, .35, 9, 21.65, SliderHall.LAST}) {
                double centre = screen.handleBase(c).getX() + .45;
                assertEquals(edge + run * (.45 + c * (16 - .9) / 22), centre, EPS);
                var transform = screen.fillTransform(SliderScreen.fillLength(c));
                double left = fillAnchor + transform.getTranslation().x;
                double right = left + transform.getScale().x;
                assertEquals(edge, run < 0 ? right : left, 1e-6);
                assertEquals(centre, run < 0 ? left : right, 1e-6);
            }
            double fullLeft = screen.fillBase(SliderHall.MAP_COLUMNS).getX();
            assertEquals(Math.min(edge, edge + run * 16), fullLeft, EPS);
            assertEquals(Math.max(edge, edge + run * 16), fullLeft + 16, EPS);
        }
    }
}
