package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import java.util.List;

public record SliderPuzzle(int row, List<Notch> notches) {
    public record Notch(int column, SliderShape shape) {}

    public SliderPuzzle {
        notches = List.copyOf(notches);
    }

    public Notch target() {
        return notches.getFirst();
    }
}
