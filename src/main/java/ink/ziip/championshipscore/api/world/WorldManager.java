package ink.ziip.championshipscore.api.world;

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
import java.util.logging.Level;

/*
 * Modified under https://github.com/lukasvdgaag/SkyWarsReloaded/
 * Author lukasvdgaag
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

    public World createEmptyWorld(String name, World.Environment environment) {
        if (org.bukkit.Bukkit.getWorld(name) == null) {
            loadWorld(name, environment, false);
            return org.bukkit.Bukkit.getWorld(name);
        }
        return null;
    }

    public boolean loadWorld(String worldName, World.Environment environment, boolean readOnly) {

        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);
        worldCreator.generator(new VoidChunkGenerator());

        World world = worldCreator.createWorld();

        if (world == null)
            return false;

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

        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        boolean loaded = false;
        for (World w : plugin.getServer().getWorlds()) {
            if (w.getName().equals(world.getName())) {
                loaded = true;
                break;
            }
        }
        return loaded;
    }

    public void unloadWorld(String w, boolean save) {
        World world = plugin.getServer().getWorld(w);

        if (world != null) {
            for (Player player : world.getPlayers()) {
                player.teleport(CCConfig.LOBBY_LOCATION);
            }
            plugin.getServer().unloadWorld(world, save);
        }
    }

    public void copyWorld(File source, File target) {
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
                                copyWorld(srcFile, destFile);
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
        File target = new File(plugin.getServer().getWorldContainer().getAbsolutePath(), name);
        deleteWorld(target);
    }

    public void deleteWorld(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorld(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        path.delete();
    }
}
