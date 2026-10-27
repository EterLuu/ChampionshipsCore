package ink.ziip.championshipscore.worker.seed;

import org.bukkit.Server;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongFunction;

/** Samples the runtime's Overworld climate without creating worlds or loading chunks. */
public final class OverworldBiomeSampler {
    @FunctionalInterface
    interface BiomeQuery {
        Object biome(int quartX, int quartY, int quartZ);
    }

    private final LongFunction<BiomeQuery> queries;

    /** Captures frozen registries during plugin initialization, before candidate evaluation. */
    public OverworldBiomeSampler(Server server) throws ReflectiveOperationException {
        ClassLoader loader = server.getClass().getClassLoader();
        Class<?> registries =
                Class.forName("net.minecraft.core.registries.Registries", true, loader);
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
        Class<?> registry = Class.forName("net.minecraft.core.Registry", true, loader);
        Class<?> key = Class.forName("net.minecraft.resources.ResourceKey", true, loader);
        Class<?> getter = Class.forName("net.minecraft.core.HolderGetter", true, loader);
        Class<?> settingsType =
                Class.forName(
                        "net.minecraft.world.level.levelgen.NoiseGeneratorSettings", true, loader);
        Class<?> randomState =
                Class.forName("net.minecraft.world.level.levelgen.RandomState", true, loader);
        Class<?> context =
                Class.forName(
                        "net.minecraft.world.level.levelgen.densityfunction.SamplerContext",
                        true,
                        loader);
        Class<?> samplerType =
                Class.forName("net.minecraft.world.level.biome.Climate$Sampler", true, loader);
        Class<?> target =
                Class.forName("net.minecraft.world.level.biome.Climate$TargetPoint", true, loader);
        Class<?> list =
                Class.forName(
                        "net.minecraft.world.level.biome.Climate$ParameterList", true, loader);
        Class<?> parameterType =
                Class.forName(
                        "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList",
                        true,
                        loader);
        Class<?> presets =
                Class.forName(
                        "net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists",
                        true,
                        loader);

        Object minecraft = server.getClass().getMethod("getServer").invoke(server);
        Object access =
                Class.forName("net.minecraft.server.MinecraftServer", true, loader)
                        .getMethod("registryAccess")
                        .invoke(minecraft);
        var lookup = registryAccess.getMethod("lookupOrThrow", key);
        var value = registry.getMethod("getValueOrThrow", key);
        Object noises = lookup.invoke(access, registries.getField("NOISE").get(null));
        Object settings =
                value.invoke(
                        lookup.invoke(access, registries.getField("NOISE_SETTINGS").get(null)),
                        settingsType.getField("OVERWORLD").get(null));
        Object parameters =
                parameterType
                        .getMethod("parameters")
                        .invoke(
                                value.invoke(
                                        lookup.invoke(
                                                access,
                                                registries
                                                        .getField(
                                                                "MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST")
                                                        .get(null)),
                                        presets.getField("OVERWORLD").get(null)));
        MethodHandles.Lookup handles = MethodHandles.publicLookup();
        MethodHandle create =
                handles.unreflect(
                        randomState.getMethod("create", getter, long.class, settingsType));
        MethodHandle climate =
                handles.unreflect(randomState.getMethod("createClimateSampler", context));
        Object uncached = context.getField("EMPTY_UNCACHED").get(null);
        MethodHandle sample =
                handles.unreflect(samplerType.getMethod("sample", int.class, int.class, int.class));
        MethodHandle find =
                handles.unreflect(list.getMethod("findValue", target)).bindTo(parameters);
        queries =
                seed -> {
                    try {
                        Object state = create.invoke(noises, seed, settings);
                        Object sampler = climate.invoke(state, uncached);
                        MethodHandle query =
                                MethodHandles.filterReturnValue(sample.bindTo(sampler), find)
                                        .asType(
                                                MethodType.methodType(
                                                        Object.class,
                                                        int.class,
                                                        int.class,
                                                        int.class));
                        return (x, y, z) -> {
                            try {
                                return query.invokeExact(x, y, z);
                            } catch (Throwable failure) {
                                throw samplingFailure(failure);
                            }
                        };
                    } catch (Throwable failure) {
                        throw samplingFailure(failure);
                    }
                };
    }

    OverworldBiomeSampler(LongFunction<BiomeQuery> queries) {
        this.queries = queries;
    }

    /** Counts distinct biome holders in a circular window at Y=64 within a shared deadline. */
    public int score(long seed, int radiusBlocks, int sampleStepBlocks, long deadlineNanos) {
        if (radiusBlocks < 4 || radiusBlocks % 4 != 0)
            throw new IllegalArgumentException("radiusBlocks must be divisible by 4");
        if (sampleStepBlocks < 4 || sampleStepBlocks % 4 != 0)
            throw new IllegalArgumentException("sampleStepBlocks must be divisible by 4");
        checkDeadline(deadlineNanos);
        BiomeQuery query = queries.apply(seed);
        int radius = radiusBlocks / 4, step = sampleStepBlocks / 4;
        Set<Object> biomes = new HashSet<>();
        for (int z = -radius; z <= radius; z += step) {
            for (int x = -radius; x <= radius; x += step) {
                checkDeadline(deadlineNanos);
                if ((long) x * x + (long) z * z <= (long) radius * radius)
                    biomes.add(query.biome(x, 16, z));
            }
        }
        checkDeadline(deadlineNanos);
        return biomes.size();
    }

    private static void checkDeadline(long deadline) {
        if (System.nanoTime() - deadline >= 0) throw new SeedFilterTimeoutException();
    }

    private static IllegalStateException samplingFailure(Throwable failure) {
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Unable to sample native Overworld climate", failure);
    }

    public static final class SeedFilterTimeoutException extends RuntimeException {
        public SeedFilterTimeoutException() {
            super(null, null, false, false);
        }
    }
}
