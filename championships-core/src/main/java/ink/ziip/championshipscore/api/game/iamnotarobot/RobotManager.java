package ink.ziip.championshipscore.api.game.iamnotarobot;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.RobotPrepareFlow;
import ink.ziip.championshipscore.api.game.iamnotarobot.config.RobotConfig;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.EntryTrack;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom;
import ink.ziip.championshipscore.api.game.iamnotarobot.runtime.RobotArea;
import ink.ziip.championshipscore.api.game.iamnotarobot.runtime.SliderMaps;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class RobotManager extends BaseGameInstanceManager<RobotArea> {
    private static final String PRESET = "verification";
    private static final String PRESET_WORLD = "robot_verification";

    private final SliderMaps sliderMaps;

    public RobotManager(ChampionshipsCore plugin) {
        super(plugin);
        sliderMaps = new SliderMaps(plugin);
    }

    @Override
    public synchronized void clearAreas() {
        super.clearAreas();
        sliderMaps.unload();
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    File folder = new File(plugin.getDataFolder(), "iamnotarobot/areas");
                    for (String resource :
                            List.of(
                                    VerificationCourse.STRUCTURE,
                                    EntryTrack.STRUCTURE,
                                    WarehouseRoom.STRUCTURE,
                                    SliderHall.STRUCTURE,
                                    JointPlaza.STRUCTURE))
                        if (!new File(plugin.getDataFolder(), resource).isFile())
                            plugin.saveResource(resource, false);
                    boolean install =
                            !folder.exists()
                                    && Bukkit.getWorld(PRESET_WORLD) == null
                                    && !plugin.getWorldManager()
                                            .getWorldFolder(PRESET_WORLD)
                                            .exists();
                    loadMapDefinitions(folder, this::loadDefinition);
                    if (install && addArea(PRESET, PRESET_WORLD)) {
                        var config = getArea(PRESET).getGameConfig();
                        var world = Bukkit.getWorld(PRESET_WORLD);
                        try {
                            RobotPrepareFlow.buildMasterVenue(plugin, world);
                            RobotPrepareFlow.replicateMasterVenue(
                                    plugin, world, PRESET, 1, config.getLanes());
                            world.save();
                            config.markPrepareWorldBuilt();
                            config.markPreparePublished();
                            plugin.getLogger()
                                    .info(
                                            "Published I am not a ROBOT map verification ("
                                                    + config.getLanes()
                                                    + " lanes)");
                        } catch (IOException failure) {
                            throw new IllegalStateException(
                                    "Could not install verification course; map remains a draft",
                                    failure);
                        }
                    }
                    var verification = Bukkit.getWorld(PRESET_WORLD);
                    if (verification != null) checkMapInterval(verification);
                });
    }

    private void checkMapInterval(World world) {
        String setting =
                "maps.item-frame-cursor-update-interval in "
                        + world.getWorldPath().resolve("paper-world.yml");
        try {
            // Bukkit does not expose the effective, merged per-dimension Paper configuration.
            var level = world.getClass().getMethod("getHandle").invoke(world);
            var config = level.getClass().getMethod("paperConfig").invoke(level);
            var maps = config.getClass().getField("maps").get(config);
            int interval = maps.getClass().getField("itemFrameCursorUpdateInterval").getInt(maps);
            logMapInterval(plugin.getLogger(), setting, interval);
        } catch (ReflectiveOperationException failure) {
            plugin.getLogger()
                    .warning(
                            "Cannot verify the effective slider map interval; check "
                                    + setting
                                    + " (recommended: 0)");
        }
    }

    static void logMapInterval(java.util.logging.Logger logger, String setting, int interval) {
        String message = "Slider map interval " + interval + " ticks (" + setting + ")";
        if (interval > 0)
            logger.warning(message + "; recommended: 0 (disable the item-frame map poll)");
        else logger.info(message + "; item-frame map poll disabled");
    }

    enum MapUpgrade {
        NONE,
        LEGACY,
        SLIDERS_AND_JOINT,
        JOINT,
        FORKLIFT_ACCESS;

        static MapUpgrade fromVersion(int version) {
            if (version >= 1 && version <= 14) return LEGACY;
            if (version == 15) return SLIDERS_AND_JOINT;
            if (version == 16 || version == 17) return JOINT;
            return version == 18 || version == 19 ? FORKLIFT_ACCESS : NONE;
        }
    }

    private void loadDefinition(String name, File file) {
        var definition = YamlConfiguration.loadConfiguration(file);
        String world = definition.getString("world-name", "");
        if (definition.getBoolean("prepare.published")
                && !world.isBlank()
                && Bukkit.getWorld(world) == null
                && !plugin.getWorldManager().getWorldFolder(world).isDirectory())
            throw new IllegalStateException("Published verification world is missing: " + world);
        if (!world.isBlank() && !loadArenaWorld(world)) return;
        int version = definition.getInt("dont-edit-this.version");
        MapUpgrade upgrade = MapUpgrade.fromVersion(version);
        if (upgrade != MapUpgrade.NONE) {
            try {
                // From v6 onward each changed region is stamped once at the existing lane pitch.
                if (version < 17) plugin.saveResource(VerificationCourse.STRUCTURE, true);
                plugin.saveResource(JointPlaza.STRUCTURE, true);
                if (upgrade == MapUpgrade.LEGACY || upgrade == MapUpgrade.SLIDERS_AND_JOINT)
                    plugin.saveResource(SliderHall.STRUCTURE, true);
                if (upgrade == MapUpgrade.LEGACY) {
                    if (version < 6) plugin.saveResource(EntryTrack.STRUCTURE, true);
                    plugin.saveResource(WarehouseRoom.STRUCTURE, true);
                }
                if (definition.getBoolean("prepare.world-built")) {
                    World target = Bukkit.getWorld(world);
                    int count = definition.getInt("lanes");
                    if (target == null || count < 1 || count > VerificationCourse.MAX_LANES)
                        throw new IllegalStateException("Invalid verification map: " + name);
                    if (upgrade == MapUpgrade.FORKLIFT_ACCESS) {
                        RobotPrepareFlow.buildForkliftAccess(plugin, target, count, version);
                    } else if (upgrade == MapUpgrade.JOINT) {
                        RobotPrepareFlow.buildJoints(plugin, target, count);
                    } else if (upgrade == MapUpgrade.SLIDERS_AND_JOINT) {
                        RobotPrepareFlow.buildSliders(plugin, target, count);
                        RobotPrepareFlow.buildJoints(plugin, target, count);
                    } else if (version >= 6) {
                        if (version < 10) RobotPrepareFlow.buildWarehouses(plugin, target, count);
                        else RobotPrepareFlow.buildWarehouseCorridors(plugin, target, count);
                        RobotPrepareFlow.buildSliders(plugin, target, count);
                        RobotPrepareFlow.buildJoints(plugin, target, count);
                    } else {
                        // Clear all legacy packed copies (old 24-block rooms/lobbies and old
                        // 40/48-wide
                        // Stage 0 decks) before building Copy 0 and replicating out along the 256
                        // grid.
                        Vector base = VerificationCourse.GRID.origin(0);
                        int legacyEntryWidth = Math.max(48 * count, 24 * count);
                        plugin.getWorldEditManager()
                                .clearCuboid(
                                        target,
                                        base.clone().add(new Vector(0, -6, -124)),
                                        new Vector(legacyEntryWidth, 36, 120));
                        plugin.getWorldEditManager()
                                .clearCuboid(
                                        target,
                                        base.clone().add(new Vector(0, 0, WarehouseRoom.MIN_Z)),
                                        new Vector(
                                                24 * count,
                                                VerificationCourse.HEIGHT,
                                                VerificationCourse.LENGTH - WarehouseRoom.MIN_Z));
                        RobotPrepareFlow.buildMasterVenue(plugin, target);
                        RobotPrepareFlow.replicateMasterVenue(plugin, target, name, 1, count);
                    }
                    target.save();
                }
                int timer = definition.getInt("timer");
                if (version == 1 && (timer == 360 || timer > RobotConfig.MAX_SECONDS))
                    definition.set("timer", RobotConfig.MAX_SECONDS);
                definition.set("dont-edit-this.version", RobotConfig.MAP_VERSION);
                definition.save(file);
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "Could not upgrade verification map " + name, failure);
            }
        }
        RobotConfig config = new RobotConfig(plugin, name);
        if (!config.initializeConfigurationChecked(plugin.getFolder())) return;
        registerMapInstances(name, List.of(new RobotArea(plugin, config, sliderMaps)));
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String world) {
        if (world == null) world = "";
        if (areas.containsKey(name) || (!world.isBlank() && !loadArenaWorld(world))) return false;
        RobotConfig config = new RobotConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld(world);
        config.beginPrepareDraft();
        registerMapInstances(name, List.of(new RobotArea(plugin, config, sliderMaps)));
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(String name, String world) {
        if (areas.containsKey(name)) return false;
        loadDefinition(
                name, new File(plugin.getDataFolder(), "iamnotarobot/areas/" + name + ".yml"));
        return areas.containsKey(name);
    }
}
