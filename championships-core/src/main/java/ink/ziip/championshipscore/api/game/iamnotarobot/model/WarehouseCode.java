package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public record WarehouseCode(List<Material> stock, List<Digit> digits, int materialReader) {
    public record Digit(Material material, int amount) {}

    public WarehouseCode {
        stock = List.copyOf(stock);
        digits = List.copyOf(digits);
    }

    public static WarehouseCode draw(Random random) {
        var pool = new ArrayList<>(WarehouseRoom.STOCK_POOL);
        Collections.shuffle(pool, random);
        var stock = List.copyOf(pool.subList(0, WarehouseRoom.SLOT_COUNT));
        var targets = new ArrayList<>(stock);
        Collections.shuffle(targets, random);
        var digits = new ArrayList<Digit>();
        for (int i = 0; i < 4; i++) digits.add(new Digit(targets.get(i), 1 + random.nextInt(9)));
        return new WarehouseCode(stock, digits, random.nextBoolean() ? 0 : 3);
    }
}
