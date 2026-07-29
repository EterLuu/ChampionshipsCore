package ink.ziip.championshipscore.util.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Modified under <a href="https://github.com/lukasvdgaag/SkyWarsReloaded/">SkyWarsReloaded</a>
 *
 * @author lukasvdgaag
 */
public class WorldManager extends BaseManager {
    public static final String BINGO_OVERWORLD = "bingo";
    public static final String BINGO_NETHER = "bingo_nether";
    public static final String BINGO_END = "bingo_the_end";
    private static final Set<String> BINGO_WORLD_NAMES =
            Set.of(BINGO_OVERWORLD, BINGO_NETHER, BINGO_END);

    public WorldManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        World lobby = CCConfig.LOBBY_LOCATION == null ? null : CCConfig.LOBBY_LOCATION.getWorld();
        if (lobby == null && !Bukkit.getWorlds().isEmpty())
            lobby = Bukkit.getWorlds().get(0);
        if (lobby == null) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("WorldManager", "大厅", "大厅世界未加载"));
            return;
        }

        // The lobby is Paper's normal level-name world. CC configures it but never creates/unloads it.
        // WorldGuard remains responsible for its protected regions and the dedicated PvP region.
        lobby.setDifficulty(Difficulty.PEACEFUL);
        lobby.setSpawnFlags(false, true);
        lobby.setAutoSave(true);
        lobby.setGameRule(GameRules.SPAWN_MOBS, true);
        lobby.setGameRule(GameRules.PVP, true);
        lobby.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
        lobby.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("WorldManager", "大厅",
                "世界=" + lobby.getName() + " 类型=服务端管理 naturalMonsters=" + lobby.getAllowMonsters()
                        + " naturalAnimals=" + lobby.getAllowAnimals()
                        + " spawnMobs=" + lobby.getGameRuleValue(GameRules.SPAWN_MOBS)));
    }

    @Override
    public void unload() {
        // The main lobby is server-owned and must stay loaded.
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
        if (Bukkit.getWorld(name) == null)
            loadWorld(name, environment, false);
    }

    /**
     * Loads an arena world with the standard minigame profile. Existing chunks are preserved; new
     * chunks are void. Natural mob/animal spawning is disabled, while entities deliberately spawned
     * by game code or stored in the map remain available.
     *
     * @return true when the world was loaded successfully
     */
    public boolean loadWorld(String worldName, World.Environment environment, boolean readOnly) {
        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(environment);
        worldCreator.generateStructures(false);
        worldCreator.generator(new VoidChunkGenerator());

        World world = worldCreator.createWorld();

        if (world == null) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("WorldManager", "加载",
                    "小游戏世界=" + worldName + " 加载失败"));
            return false;
        }

        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        world.setSpawnFlags(false, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setAutoSave(!readOnly);

        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("WorldManager", "加载",
                "小游戏世界=" + worldName + " 生成器=虚空 naturalMonsters=" + world.getAllowMonsters()
                        + " naturalAnimals=" + world.getAllowAnimals()
                        + " spawnMobs=" + world.getGameRuleValue(GameRules.SPAWN_MOBS)));
        return true;
    }

    /**
     * Loads one Bingo dimension with vanilla terrain and survival spawning. Bingo cards explicitly
     * include animal and hostile-mob objectives, so it intentionally does not use the arena profile.
     */
    public boolean loadBingoWorld(String worldName, World.Environment environment) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            world = creator.createWorld();
        }
        if (world == null) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "世界",
                    "世界=" + worldName + " 加载失败"));
            return false;
        }

        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnFlags(true, true);
        world.setAutoSave(true);
        world.setGameRule(GameRules.SPAWN_MOBS, true);
        world.setGameRule(GameRules.MOB_GRIEFING, true);
        world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.PVP, true);
        world.setGameRule(GameRules.ADVANCE_TIME, true);
        plugin.getLogger().log(Level.INFO, Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "世界",
                "世界=" + worldName + " 环境=" + environment
                        + " naturalMonsters=" + world.getAllowMonsters()
                        + " naturalAnimals=" + world.getAllowAnimals()
                        + " spawnMobs=" + world.getGameRuleValue(GameRules.SPAWN_MOBS)));
        return true;
    }

    public static boolean isBingoWorldName(String name) {
        return name != null && BINGO_WORLD_NAMES.contains(name);
    }

    public static boolean isBingoWorld(World world) {
        return world != null && isBingoWorldName(world.getName());
    }

    public static World.Environment getBingoEnvironment(String worldName) {
        if (BINGO_OVERWORLD.equals(worldName))
            return World.Environment.NORMAL;
        if (BINGO_NETHER.equals(worldName))
            return World.Environment.NETHER;
        if (BINGO_END.equals(worldName))
            return World.Environment.THE_END;
        return null;
    }

    /** Resolves the server-owned main world without relying on a hard-coded world name. */
    public World getMainWorld() {
        World lobby = CCConfig.LOBBY_LOCATION == null ? null : CCConfig.LOBBY_LOCATION.getWorld();
        if (lobby != null)
            return lobby;
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public boolean isMainWorld(World world) {
        World mainWorld = getMainWorld();
        return world != null && mainWorld != null && world.getUID().equals(mainWorld.getUID());
    }

    public List<String> getLoadedWorldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds())
            names.add(world.getName());
        Collections.sort(names);
        return names;
    }

    /**
     * Returns worlds known either to Bukkit or to the MC 26.1 custom-dimension directory. Built-in
     * dimension folders belong to the server's main level and are not separate Bukkit worlds.
     */
    public List<String> getStoredWorldNames() {
        Set<String> names = new LinkedHashSet<>(getLoadedWorldNames());
        File[] folders = getDimensionsContainer().listFiles(File::isDirectory);
        if (folders != null) {
            Set<String> builtInDimensions = Set.of("overworld", "the_nether", "the_end");
            for (File folder : folders) {
                if (!builtInDimensions.contains(folder.getName()))
                    names.add(folder.getName());
            }
        }
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted;
    }

    public static boolean isValidWorldName(String worldName) {
        return worldName != null && worldName.matches("[A-Za-z0-9_-]+");
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
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("WorldManager", "复制",
                    "源文件不存在=" + source.getPath() + " 目标=" + target.getPath()), e);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("WorldManager", "复制",
                    "世界文件复制失败，源=" + source.getPath() + " 目标=" + target.getPath()), e);
        }
    }

    public void deleteWorld(String name, boolean removeFile) {
        unloadWorld(name, false);

        if (removeFile) {
            File target = getWorldFolder(name);
            deleteWorldFiles(target);
        }
    }

    public boolean unloadWorld(String worldName, boolean save) {
        World world = plugin.getServer().getWorld(worldName);

        if (world == null || isMainWorld(world))
            return false;

        for (Player player : new ArrayList<>(world.getPlayers()))
            player.teleport(CCConfig.LOBBY_LOCATION);
        return plugin.getServer().unloadWorld(world, save);
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
