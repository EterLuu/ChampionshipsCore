package ink.ziip.championshipscore.api.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VoidBiomeProvider extends BiomeProvider {
    @NotNull
    @Override
    public Biome getBiome(@NotNull WorldInfo worldInfo, int i, int i1, int i2) {
        return Biome.THE_VOID;
    }

    @NotNull
    @Override
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return List.of(Biome.THE_VOID);
    }
}
