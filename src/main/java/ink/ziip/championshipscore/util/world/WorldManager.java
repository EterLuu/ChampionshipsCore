package ink.ziip.championshipscore.util.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Modified under <a href="https://github.com/lukasvdgaag/SkyWarsReloaded/">SkyWarsReloaded</a>
 *
 * @author lukasvdgaag
 */
public class WorldManager extends BaseManager {
    private File dimensionsContainer;

    public WorldManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        dimensionsContainer = Bukkit.getWorlds().getFirst().getWorldFolder().getParentFile();
    }

    @Override
    public void unload() {

    }

    /**
     * The on-disk parent directory that holds every world folder. Since MC 26.1 custom worlds are
     * stored as dimensions under {@code <level>/dimensions/minecraft/} instead of as top-level folders
     * in the server container, so {@link org.bukkit.Server#getWorldContainer()} no longer points at
     * them. The main world is always loaded and always first, so its folder's parent is the common
     * parent of every world dimension. Must be called on the main thread.
     */
    public File getDimensionsContainer() {
        if (dimensionsContainer == null) {
            throw new IllegalStateException("WorldManager has not been loaded");
        }
        return dimensionsContainer;
    }

    /**
     * Resolves the on-disk folder for {@code worldName} under the MC 26.1 dimensions layout, working
     * even when that world is not loaded. Folder names are lower-cased with spaces replaced by
     * underscores, matching how the server names dimension folders.
     */
    public File getWorldFolder(String worldName) {
        String folder = worldName.toLowerCase(Locale.ENGLISH).replace(' ', '_');
        return new File(getDimensionsContainer(), folder);
    }

    public void createEmptyWorld(String name, World.Environment environment) {
        FoliaScheduler.global(plugin).runGlobalFuture(() -> {
            if (Bukkit.getWorld(name) == null) {
                loadWorldNow(name, environment, false);
            }
        });
    }

    /** Schedules world creation on the global region. */
    public void loadWorld(String worldName, World.Environment environment, boolean readOnly) {
        loadWorldAsync(worldName, environment, readOnly);
    }

    private void loadWorldNow(String worldName, World.Environment environment, boolean readOnly) {
        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);
        worldCreator.generator(new VoidChunkGenerator());

        World world = worldCreator.createWorld();

        if (world == null)
            throw new IllegalStateException("Could not create or load world " + worldName);

        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        world.setSpawnFlags(true, true);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setTicksPerSpawns(SpawnCategory.ANIMAL, 1);
        world.setTicksPerSpawns(SpawnCategory.MONSTER, 1);
        world.setAutoSave(!readOnly);

        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
    }

    public void copyWorldFiles(File source, File target) {
        List<String> ignore = List.of("uid.dat", "session.dat", "session.lock");
        if (ignore.contains(source.getName())) {
            return;
        }
        if (!source.exists()) {
            plugin.getLogger().warning("World template does not exist; loading an empty world: " + source);
            return;
        }

        try {
            if (source.isDirectory()) {
                if (target.exists() && !target.isDirectory()) {
                    throw new IOException("Copy target is not a directory: " + target);
                }
                if (!target.exists() && !target.mkdirs()) {
                    throw new IOException("Could not create directory: " + target);
                }
                String[] files = source.list();
                if (files == null) {
                    throw new IOException("Could not list directory: " + source);
                }
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(target, file);
                    copyWorldFiles(srcFile, destFile);
                }
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create directory: " + parent);
                }
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy world as required!", e);
            throw new IllegalStateException("Failed to copy world from " + source + " to " + target, e);
        }
    }

    public void deleteWorld(String name, boolean removeFile) {
        deleteWorldAsync(name, removeFile);
    }

    /** Unloads a world only after all of its players have completed an async teleport. */
    public CompletableFuture<Void> unloadWorldAsync(String worldName, boolean save) {
        FoliaScheduler scheduler = FoliaScheduler.global(plugin);
        return scheduler.supplyGlobal(() -> {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                return List.<CompletableFuture<Boolean>>of();
            }
            List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                teleports.add(player.teleportAsync(CCConfig.LOBBY_LOCATION));
            }
            return teleports;
        }).thenCompose(teleports ->
            CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new))
                    .thenCompose(done -> scheduler.runGlobalFuture(() -> {
                        World loaded = plugin.getServer().getWorld(worldName);
                        if (loaded != null && !plugin.getServer().unloadWorld(loaded, save)) {
                            throw new IllegalStateException("Could not unload world " + worldName);
                        }
                    })));
    }

    public CompletableFuture<Void> deleteWorldAsync(String worldName, boolean removeFiles) {
        CompletableFuture<Void> unloaded = unloadWorldAsync(worldName, false);
        if (!removeFiles) {
            return unloaded;
        }
        return unloaded.thenCompose(ignored -> FoliaScheduler.global(plugin).runAsyncFuture(
                () -> deleteWorldFiles(getWorldFolder(worldName))));
    }

    /** Creates/configures a world on the global region, as required by Folia. */
    public CompletableFuture<Void> loadWorldAsync(String worldName, World.Environment environment, boolean readOnly) {
        return FoliaScheduler.global(plugin).runGlobalFuture(() -> loadWorldNow(worldName, environment, readOnly));
    }

    public void unloadWorld(String worldName, boolean save) {
        unloadWorldAsync(worldName, save);
    }

    public void deleteWorldFiles(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFiles(file);
                    } else if (!file.delete()) {
                        throw new IllegalStateException("Could not delete world file " + file);
                    }
                }
            }
        }
        if (path.exists() && !path.delete()) {
            throw new IllegalStateException("Could not delete world path " + path);
        }
    }
}
