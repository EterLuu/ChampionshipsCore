package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import java.util.*;

public record SliderDeck(long seed) {
    static long mix(long n) {
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    long stationSeed(SliderHall s) {
        return mix(seed + 0x9e3779b97f4a7c15L * (s.ordinal() + 1));
    }

    public long imageSeed(SliderHall s, int round) {
        return mix(stationSeed(s) + round);
    }

    private int firstColumnOffset(SliderHall station, int round) {
        return new Random(imageSeed(station, round)).nextInt(19);
    }

    public SliderPuzzle puzzle(SliderHall s, int round, int attempt) {
        var seedForRound = imageSeed(s, round);
        var base = new Random(seedForRound);
        // 19 is prime: stepping by 1..18 guarantees a new target after every failure.
        int offset = base.nextInt(19);
        int step = 1 + base.nextInt(18);
        if (round == 3) {
            int first = firstColumnOffset(s, 1), second = firstColumnOffset(s, 2);
            while (offset == first || offset == second) offset = (offset + step) % 19;
        }
        int target = 4 + (int) Math.floorMod(offset + (long) attempt * step, 19L);
        var random = new Random(mix(~seedForRound + attempt));
        var shapes = new ArrayList<>(SliderShape.STYLES);
        Collections.shuffle(shapes, random);
        var notches = new ArrayList<SliderPuzzle.Notch>();
        notches.add(new SliderPuzzle.Notch(target, shapes.getFirst()));
        if (round >= 2) {
            var pairs = new ArrayList<int[]>();
            for (int a = 4; a <= 22; a++)
                for (int b = a + 5; b <= 22; b++)
                    if (Math.abs(a - target) >= 5 && Math.abs(b - target) >= 5)
                        pairs.add(new int[] {a, b});
            int[] pair = pairs.get(random.nextInt(pairs.size()));
            for (int i = 0; i < 2; i++)
                notches.add(new SliderPuzzle.Notch(pair[i], shapes.get(i + 1)));
        }
        return new SliderPuzzle(1 + random.nextInt(7), notches);
    }
}
