package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import java.util.*;
import java.util.function.*;

/** Main-thread frame work, charged in microseconds. Pixel sends have their own wire budget. */
final class SliderSweep {
    static final int MICROS = 12_000, SPAWN = 64, EQUIP = 7;

    record Card(
            BooleanSupplier valid,
            BooleanSupplier urgent,
            IntSupplier cost,
            BooleanSupplier next) {}

    private final ArrayDeque<Card> cards = new ArrayDeque<>();

    void add(Card card) {
        cards.addLast(card);
    }

    boolean isEmpty() {
        return cards.isEmpty();
    }

    void clear() {
        cards.clear();
    }

    void slice(LongSupplier clock, long deadline) {
        int remaining = MICROS;
        for (boolean urgent : new boolean[] {true, false}) {
            int skipped = 0;
            while (skipped < cards.size() && clock.getAsLong() < deadline) {
                var card = cards.removeFirst();
                if (!card.valid.getAsBoolean()) continue;
                int cost = card.cost.getAsInt();
                if (card.urgent.getAsBoolean() != urgent || cost == 0) {
                    cards.addLast(card);
                    skipped++;
                    continue;
                }
                if (cost > remaining) {
                    cards.addFirst(card);
                    return;
                }
                remaining -= cost;
                if (!card.next.getAsBoolean()) cards.addLast(card);
                skipped = 0;
            }
        }
    }
}
