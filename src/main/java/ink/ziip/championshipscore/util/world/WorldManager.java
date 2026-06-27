package ink.ziip.championshipscore.util.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Modified under <a href="https://github.com/lukasvdgaag/SkyWarsReloaded/">SkyWarsReloaded</a>
 *
 * @author lukasvdgaag
 */
public class WorldManager extends BaseManager {

    public WorldManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {

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
        return Bukkit.getWorlds().get(0).getWorldFolder().getParentFile();
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
        if (org.bukkit.Bukkit.getWorld(name) == null) {
            loadWorld(name, environment, false);
            Bukkit.getWorld(name);
        }
    }

    public void loadWorld(String worldName, World.Environment environment, boolean readOnly) {
        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);
        worldCreator.generator(new VoidChunkGenerator());

        World world = worldCreator.createWorld();

        if (world == null)
            return;

        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        world.setSpawnFlags(true, true);
        world.setPVP(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setKeepSpawnInMemory(false);
        world.setTicksPerSpawns(SpawnCategory.ANIMAL, 1);
        world.setTicksPerSpawns(SpawnCategory.MONSTER, 1);
        world.setAutoSave(!readOnly);

        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    }

    public void copyWorldFiles(File source, File target) {
        try {
            List<String> ignore = List.of("uid.dat", "session.dat", "session.lock");
            if (!ignore.contains(source.getName())) {
                if (source.isDirectory()) {
                    if ((!target.exists()) &&
                            (target.mkdirs())) {
                        String[] files = source.list();
                        if (files != null) {
                            for (String file : files) {
                                File srcFile = new File(source, file);
                                File destFile = new File(target, file);
                                copyWorldFiles(srcFile, destFile);
                            }
                        }
                    }
                } else {
                    java.io.InputStream in = new java.io.FileInputStream(source);
                    OutputStream out = new java.io.FileOutputStream(target);
                    byte[] buffer = new byte['Ѐ'];
                    int length;
                    while ((length = in.read(buffer)) > 0)
                        out.write(buffer, 0, length);
                    in.close();
                    out.close();
                }
            }
        } catch (FileNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy world as required! - file not found.", e);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to copy world as required!", e);
        }
    }

    public void deleteWorld(String name, boolean removeFile) {
        unloadWorld(name, false);

        if (removeFile) {
            File target = getWorldFolder(name);
            deleteWorldFiles(target);
        }
    }

    public void unloadWorld(String worldName, boolean save) {
        World world = plugin.getServer().getWorld(worldName);

        if (world != null) {
            for (Player player : world.getPlayers()) {
                player.teleport(CCConfig.LOBBY_LOCATION);
            }
            plugin.getServer().unloadWorld(world, save);
        }
    }

    public void deleteWorldFiles(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFiles(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        path.delete();
    }
}
