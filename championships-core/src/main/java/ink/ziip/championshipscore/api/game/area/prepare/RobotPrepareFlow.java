package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.iamnotarobot.config.RobotConfig;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.EntryTrack;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Prepare flow for I am not a ROBOT. Copy 0 is kept as the single editable master venue (Stage 0
 * modern city block, warehouse, and subsequent rooms); copies {@code 1..count-1} are automatically
 * cloned from Copy 0 along the 256-block venue grid.
 */
public final class RobotPrepareFlow extends PrepareFlowDefinition {
    public String worldName(SetupTarget target) {
        return target.worldName();
    }

    public boolean isInCorrectWorld(Player player, SetupTarget target) {
        return player.getWorld().getName().equals(target.worldName());
    }

    public Location copyZeroLocation(SetupTarget target) {
        return target.config().getSpectatorSpawnPoint();
    }

    public List<PrepareStep> buildSteps(SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(p -> isInCorrectWorld(p, target), target.worldName()),
                new StampStep(
                        RobotPrepareFlow::roomTemplate,
                        EntryTrack.GRID,
                        (t, n) -> ((RobotConfig) t.config()).setLanes(n),
                        VerificationCourse.MAX_LANES) {
                    @Override
                    protected void stampCopies(
                            ChampionshipsCore plugin,
                            World world,
                            File file,
                            ArenaGrid grid,
                            int count)
                            throws IOException {
                        RobotConfig config = (RobotConfig) target.config();
                        int previousLanes = Math.max(1, config.getLanes());
                        if (!hasMasterVenue(world)) buildMasterVenue(plugin, world);
                        replicateMasterVenue(plugin, world, target.name(), previousLanes, count);
                    }
                });
    }

    public static File roomTemplate(ChampionshipsCore plugin) {
        File custom = new File(plugin.getDataFolder(), "iamnotarobot/verification.schem");
        return custom.isFile()
                ? custom
                : new File(plugin.getDataFolder(), VerificationCourse.STRUCTURE);
    }

    public static File venueSchematic(ChampionshipsCore plugin, String mapName) {
        return new File(
                plugin.getDataFolder(), "iamnotarobot/schematics/" + mapName + "/venue.schem");
    }

    /** Seeds only Copy 0 (the master venue) from the bundled templates and closes its gates. */
    public static void buildMasterVenue(ChampionshipsCore plugin, World world) throws IOException {
        ArenaPreparer.stampCopies(plugin, world, roomTemplate(plugin), VerificationCourse.GRID, 1);
        ArenaPreparer.stampCopies(
                plugin,
                world,
                new File(plugin.getDataFolder(), EntryTrack.STRUCTURE),
                EntryTrack.GRID,
                1);
        buildWarehouses(plugin, world, 1);
        buildSliders(plugin, world, 1);
        buildJoints(plugin, world, 1);
        EntryTrack.setStartGate(world, EntryTrack.origin(0), false);
    }

    public static void buildJoints(ChampionshipsCore plugin, World world, int count)
            throws IOException {
        ArenaPreparer.stampCopies(
                plugin,
                world,
                new File(plugin.getDataFolder(), JointPlaza.STRUCTURE),
                JointPlaza.GRID,
                count);
        buildSprints(plugin, world, count);
        for (int i = 0; i < count; i++)
            VerificationCourse.setGate(
                    world, VerificationCourse.GRID.origin(i), VerificationCourse.Room.JOINT, false);
    }

    public static void buildForkliftAccess(
            ChampionshipsCore plugin, World world, int count, int version) throws IOException {
        var palette =
                Bukkit.getStructureManager()
                        .loadStructure(new File(plugin.getDataFolder(), JointPlaza.STRUCTURE))
                        .getPalettes()
                        .getFirst();
        var cells =
                palette.getBlocks().stream()
                        .filter(
                                cell ->
                                        JointPlaza.forkliftAccessUpgrade(
                                                cell.getX() + JointPlaza.MIN_X,
                                                cell.getY() + JointPlaza.MIN_Y,
                                                cell.getZ() + JointPlaza.MIN_Z,
                                                cell.getType(),
                                                version))
                        .toList();
        for (int lane = 0; lane < count; lane++) {
            Vector origin = JointPlaza.GRID.origin(lane);
            for (var cell : cells) {
                world.getBlockAt(
                                origin.getBlockX() + cell.getX(),
                                origin.getBlockY() + cell.getY(),
                                origin.getBlockZ() + cell.getZ())
                        .setBlockData(cell.getBlockData(), false);
            }
        }
    }

    /** Stamps the shifted sprint finish hall from the master course template after the plaza. */
    public static void buildSprints(ChampionshipsCore plugin, World world, int count)
            throws IOException {
        var palette =
                Bukkit.getStructureManager()
                        .loadStructure(roomTemplate(plugin))
                        .getPalettes()
                        .getFirst();
        int sprintStart = VerificationCourse.Room.SPRINT.start();
        var cells = palette.getBlocks().stream().filter(b -> b.getZ() >= sprintStart).toList();
        for (int lane = 0; lane < count; lane++) {
            Vector origin = VerificationCourse.GRID.origin(lane);
            for (var cell : cells) {
                var target =
                        world.getBlockAt(
                                origin.getBlockX() + cell.getX(),
                                origin.getBlockY() + cell.getY(),
                                origin.getBlockZ() + cell.getZ());
                var data = cell.getBlockData();
                if (cell instanceof TileState) cell.copy(target.getLocation()).update(true, false);
                else if (!target.getBlockData().equals(data)) target.setBlockData(data, false);
            }
        }
    }

    public static void buildSliders(ChampionshipsCore plugin, World world, int count)
            throws IOException {
        ArenaPreparer.stampCopies(
                plugin,
                world,
                new File(plugin.getDataFolder(), SliderHall.STRUCTURE),
                SliderHall.GRID,
                count);
        for (int i = 0; i < count; i++) {
            Vector origin = VerificationCourse.GRID.origin(i);
            VerificationCourse.closeSliderEntrance(world, origin);
            VerificationCourse.setGate(world, origin, VerificationCourse.Room.SLIDER, false);
        }
    }

    /** Only the transition between the warehouse exits and the slider entrance changed in v15. */
    public static void buildWarehouseCorridors(ChampionshipsCore plugin, World world, int count)
            throws IOException {
        var file = new File(plugin.getDataFolder(), WarehouseRoom.STRUCTURE);
        var palette = Bukkit.getStructureManager().loadStructure(file).getPalettes().getFirst();
        var cells =
                palette.getBlocks().stream()
                        .filter(
                                b ->
                                        b.getZ() + WarehouseRoom.MIN_Z > WarehouseRoom.EXIT_Z
                                                && b.getZ() + WarehouseRoom.MIN_Z
                                                        <= WarehouseRoom.END_Z)
                        .toList();
        for (int lane = 0; lane < count; lane++) {
            Vector origin = WarehouseRoom.GRID.origin(lane);
            for (var cell : cells) {
                var target =
                        world.getBlockAt(
                                origin.getBlockX() + cell.getX(),
                                origin.getBlockY() + cell.getY(),
                                origin.getBlockZ() + cell.getZ());
                var data = cell.getBlockData();
                if (cell instanceof TileState) cell.copy(target.getLocation()).update(true, false);
                else if (!target.getBlockData().equals(data)) target.setBlockData(data, false);
            }
        }
    }

    public static void buildWarehouses(ChampionshipsCore plugin, World world, int count)
            throws IOException {
        ArenaPreparer.stampCopies(
                plugin,
                world,
                new File(plugin.getDataFolder(), WarehouseRoom.STRUCTURE),
                WarehouseRoom.GRID,
                count);
        for (int i = 0; i < count; i++)
            WarehouseRoom.setGates(world, VerificationCourse.GRID.origin(i), true, false);
    }

    /**
     * Preserves Copy 0 untouched, clears previous copies {@code 1..previousLanes-1}, snapshots live
     * Copy 0 into {@link #venueSchematic}, and stamps copies {@code 1..lanes-1} at 256-block pitch.
     */
    public static void replicateMasterVenue(
            ChampionshipsCore plugin, World world, String mapName, int previousLanes, int lanes)
            throws IOException {
        if (previousLanes > 1) {
            ArenaPreparer.clearAdditionalCopies(
                    plugin,
                    world,
                    VerificationCourse.VENUE_GRID,
                    previousLanes,
                    VerificationCourse.VENUE_SIZE);
        }
        if (lanes <= 1) return;
        File schematic = venueSchematic(plugin, mapName);
        plugin.getWorldEditManager()
                .saveCuboidAsSchematic(
                        world,
                        VerificationCourse.VENUE_GRID.origin(0),
                        VerificationCourse.VENUE_SIZE,
                        schematic);
        ArenaPreparer.stampAdditionalCopies(
                plugin, world, schematic, VerificationCourse.VENUE_GRID, lanes);
    }

    private static boolean hasMasterVenue(World world) {
        Vector roomOrigin = VerificationCourse.GRID.origin(0);
        Vector spawn = EntryTrack.origin(0).add(EntryTrack.forSlot(0).spawn());
        Material roomFloor =
                world.getBlockAt(
                                roomOrigin.getBlockX() + 9,
                                roomOrigin.getBlockY(),
                                roomOrigin.getBlockZ() + VerificationCourse.FINISH)
                        .getType();
        Material entryFloor =
                world.getBlockAt(spawn.getBlockX(), spawn.getBlockY() - 1, spawn.getBlockZ())
                        .getType();
        return !roomFloor.isAir() && !entryFloor.isAir();
    }

    @Override
    public List<String> validate(PrepareSession session) {
        var errors = super.validate(session);
        if (!((RobotConfig) session.getTarget().config()).validSettings())
            errors.add(MessageConfig.codeText("robot.settings"));
        return errors;
    }
}
