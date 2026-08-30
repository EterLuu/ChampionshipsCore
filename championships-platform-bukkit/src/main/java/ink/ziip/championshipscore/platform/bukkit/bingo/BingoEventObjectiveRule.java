package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.Material;

import java.util.Set;

/** Fully resolved pollable event objective shared by local and remote Bingo runtimes. */
public record BingoEventObjectiveRule(String trigger, String param, int count,
                                      Set<Material> members, Set<String> biomeKeys) {
    public BingoEventObjectiveRule {
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("trigger must not be blank");
        param = param == null ? "" : param;
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        members = members == null ? Set.of() : Set.copyOf(members);
        biomeKeys = biomeKeys == null ? Set.of() : Set.copyOf(biomeKeys);
    }
}
