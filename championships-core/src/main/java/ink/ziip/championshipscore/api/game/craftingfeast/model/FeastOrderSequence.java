package ink.ziip.championshipscore.api.game.craftingfeast.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The game's shared guest sequence. Every restaurant draws the same n-th guest, so teams face
 * identical orders, VIPs and rare special guests while the menu is reshuffled each round.
 */
public final class FeastOrderSequence {
    private final List<FeastRequest> menu;
    private final Random random;
    private final int vipEvery;
    private final List<FeastGuestProfile> specials;
    private final int specialChancePercent;
    private final List<FeastGuestProfile> vips;
    private final Deck deck;
    private final Deck deliveries;
    private final List<FeastGuest> guests = new ArrayList<>();

    public FeastOrderSequence(List<FeastRequest> menu, long seed, int vipEvery) {
        this(menu, seed, vipEvery, List.of(), 0, List.of());
    }

    public FeastOrderSequence(
            List<FeastRequest> menu,
            long seed,
            int vipEvery,
            List<FeastGuestProfile> specials,
            int specialChancePercent,
            List<FeastGuestProfile> vips) {
        if (menu == null || menu.isEmpty()) throw new IllegalArgumentException("Empty menu");
        if (vipEvery < 0) throw new IllegalArgumentException("Negative VIP interval");
        if (specialChancePercent < 0 || specialChancePercent > 100)
            throw new IllegalArgumentException("Invalid special guest chance");
        this.menu = List.copyOf(menu);
        this.random = new Random(seed);
        this.vipEvery = vipEvery;
        this.specials = List.copyOf(specials);
        this.vips = List.copyOf(vips);
        this.specialChancePercent = specials.isEmpty() ? 0 : specialChancePercent;
        deck = new Deck(random);
        // Delivery orders draw from their own shuffled deck so they never shift the guests.
        deliveries = new Deck(new Random(seed ^ 0x6A09E667F3BCC909L));
    }

    public FeastGuest guest(int index) {
        if (index < 0) throw new IllegalArgumentException("Negative guest index");
        while (guests.size() <= index) guests.add(next(guests.size()));
        return guests.get(index);
    }

    private FeastGuest next(int index) {
        if (specialChancePercent > 0 && random.nextInt(100) < specialChancePercent)
            return namedGuest(index, specials, false);
        boolean vip = vipEvery > 0 && index % vipEvery == vipEvery - 1;
        if (vip && !vips.isEmpty()) return namedGuest(index, vips, true);
        return new FeastGuest(index + 1, deck.next(), vip, null);
    }

    private FeastGuest namedGuest(int index, List<FeastGuestProfile> profiles, boolean vip) {
        FeastGuestProfile profile = profiles.get(random.nextInt(profiles.size()));
        FeastRequest request = profile.requests().get(random.nextInt(profile.requests().size()));
        return new FeastGuest(index + 1, request, vip, profile);
    }

    /** The n-th delivery order; every restaurant receives the same delivery sequence. */
    public FeastRequest delivery(int index) {
        if (index < 0) throw new IllegalArgumentException("Negative delivery index");
        return deliveries.get(index);
    }

    /** The menu reshuffled round after round. */
    private final class Deck {
        private final Random random;
        private final List<FeastRequest> cards = new ArrayList<>();
        private int dealt;

        Deck(Random random) {
            this.random = random;
        }

        FeastRequest next() {
            return get(dealt++);
        }

        FeastRequest get(int index) {
            while (cards.size() <= index) {
                List<FeastRequest> round = new ArrayList<>(menu);
                java.util.Collections.shuffle(round, random);
                // Never serve the same dish twice in a row across a reshuffle boundary.
                if (round.size() > 1
                        && !cards.isEmpty()
                        && cards.getLast().equals(round.getFirst())) round.add(round.removeFirst());
                cards.addAll(round);
            }
            return cards.get(index);
        }
    }
}
