package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import java.util.ArrayList;
import java.util.List;

/** A top and a bottom profile share one solid core, in viewer coordinates (right and down). */
public final class SliderShape {
    private static final double SIZE = SliderHall.CORE_SIZE, TAB = SliderHall.TAB_SIZE;
    public static final List<SliderShape> STYLES = styles();

    public record Rect(double x, double y, double w, double h) {}

    private record Edge(double center, int direction) {
        double left() {
            return center - TAB / 2;
        }

        double right() {
            return center + TAB / 2;
        }

        void fill(List<Rect> pieces, double y) {
            if (direction > 0) pieces.add(new Rect(left(), y, TAB, TAB));
            if (direction < 0) {
                pieces.add(new Rect(0, y, left(), TAB));
                pieces.add(new Rect(right(), y, SIZE - right(), TAB));
            }
        }

        void outline(List<Rect> edges, double baseline, double tip, double stroke) {
            if (direction == 0) {
                line(edges, 0, baseline, SIZE, baseline, stroke);
                return;
            }
            line(edges, 0, baseline, left(), baseline, stroke);
            line(edges, left(), baseline, left(), tip, stroke);
            line(edges, left(), tip, right(), tip, stroke);
            line(edges, right(), tip, right(), baseline, stroke);
            line(edges, right(), baseline, SIZE, baseline, stroke);
        }
    }

    public final List<Rect> fill, outline, pieceOutline;

    private SliderShape(Edge top, Edge bottom) {
        double y = top.direction < 0 ? TAB : 0;
        double end = bottom.direction < 0 ? SIZE - TAB : SIZE;
        var pieces = new ArrayList<Rect>();
        pieces.add(new Rect(0, y, SIZE, end - y));
        top.fill(pieces, top.direction < 0 ? 0 : -TAB);
        bottom.fill(pieces, end);
        fill = List.copyOf(pieces);
        outline = contour(top, bottom, SliderHall.OUTLINE);
        pieceOutline = contour(top, bottom, SliderHall.PIECE_OUTLINE);
    }

    private static List<SliderShape> styles() {
        var edges = new ArrayList<Edge>();
        edges.add(new Edge(0, 0));
        for (int slot = 1; slot <= 3; slot++) {
            double center = SIZE * slot / 4;
            edges.add(new Edge(center, 1));
            edges.add(new Edge(center, -1));
        }
        var shapes = new ArrayList<SliderShape>();
        for (var top : edges)
            for (var bottom : edges)
                if (top.direction != 0 || bottom.direction != 0)
                    shapes.add(new SliderShape(top, bottom));
        return List.copyOf(shapes);
    }

    private static List<Rect> contour(Edge top, Edge bottom, double stroke) {
        var edges = new ArrayList<Rect>();
        top.outline(edges, 0, -top.direction * TAB, stroke);
        bottom.outline(edges, SIZE, SIZE + bottom.direction * TAB, stroke);
        line(edges, 0, 0, 0, SIZE, stroke);
        line(edges, SIZE, 0, SIZE, SIZE, stroke);
        return List.copyOf(edges);
    }

    private static void line(
            List<Rect> edges, double x1, double y1, double x2, double y2, double stroke) {
        edges.add(
                new Rect(
                        Math.min(x1, x2) - stroke / 2,
                        Math.min(y1, y2) - stroke / 2,
                        Math.abs(x2 - x1) + stroke,
                        Math.abs(y2 - y1) + stroke));
    }
}
