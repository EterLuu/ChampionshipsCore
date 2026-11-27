package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Claims last for the lifetime of the hall, including across disconnects. */
public final class SliderAssignment {
    private static final List<SliderHall> ORDER =
            List.of(SliderHall.A, SliderHall.C, SliderHall.B, SliderHall.D);
    private final EnumMap<SliderHall, UUID> owners = new EnumMap<>(SliderHall.class);

    public SliderHall track(UUID member) {
        for (var entry : owners.entrySet())
            if (entry.getValue().equals(member)) return entry.getKey();
        return null;
    }

    public UUID owner(SliderHall hall) {
        return owners.get(hall);
    }

    public SliderHall claim(UUID member) {
        Objects.requireNonNull(member);
        var existing = track(member);
        if (existing != null) return existing;
        for (var hall : ORDER) if (owners.putIfAbsent(hall, member) == null) return hall;
        return null;
    }

    public static SliderHall partner(SliderHall hall) {
        return switch (hall) {
            case A -> SliderHall.C;
            case C -> SliderHall.A;
            case B -> SliderHall.D;
            case D -> SliderHall.B;
        };
    }
}
