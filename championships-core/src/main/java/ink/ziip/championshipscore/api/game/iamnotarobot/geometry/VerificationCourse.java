package ink.ziip.championshipscore.api.game.iamnotarobot.geometry;

import ink.ziip.championshipscore.api.game.arena.RowArenaGrid;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Block coordinates relative to the minimum corner of the puzzle rooms, facing south (+Z). */
public final class VerificationCourse {
    public static final int WIDTH = 19, HEIGHT = 10, LENGTH = 107;
    public static final int FINISH = 100;
    public static final Vector STEP = new Vector(256, 0, 0);
    public static final RowArenaGrid GRID = new RowArenaGrid(new Vector(0, 80, 0), STEP);
    public static final int VENUE_MIN_X = Math.min(EntryTrack.MIN_X, JointPlaza.MIN_X);
    public static final int VENUE_MIN_Y = Math.min(EntryTrack.MIN_Y, JointPlaza.MIN_Y);
    public static final int VENUE_WIDTH =
            Math.max(EntryTrack.MIN_X + EntryTrack.WIDTH, JointPlaza.MIN_X + JointPlaza.WIDTH)
                    - VENUE_MIN_X;
    public static final int VENUE_HEIGHT =
            Math.max(EntryTrack.MIN_Y + EntryTrack.HEIGHT, JointPlaza.MIN_Y + JointPlaza.HEIGHT)
                    - VENUE_MIN_Y;
    public static final int VENUE_LENGTH = LENGTH - EntryTrack.MIN_Z;
    public static final Vector VENUE_SIZE = new Vector(VENUE_WIDTH, VENUE_HEIGHT, VENUE_LENGTH);
    public static final RowArenaGrid VENUE_GRID =
            new RowArenaGrid(
                    GRID.origin(0).add(new Vector(VENUE_MIN_X, VENUE_MIN_Y, EntryTrack.MIN_Z)),
                    STEP);
    public static final String STRUCTURE = "iamnotarobot/verification.nbt";
    public static final int MAX_LANES = 16;

    public enum Room {
        ENTRY(0),
        WAREHOUSE(WarehouseRoom.END_Z),
        SLIDER(38),
        JOINT(JointPlaza.EXIT_Z),
        SPRINT(106);
        public final int end;

        Room(int end) {
            this.end = end;
        }

        public Room previous() {
            return switch (this) {
                case ENTRY -> throw new IllegalStateException("No room before entry");
                case WAREHOUSE -> ENTRY;
                case SLIDER -> WAREHOUSE;
                case JOINT -> SLIDER;
                case SPRINT -> JOINT;
            };
        }

        private int insideZ() {
            return this == SLIDER ? SliderHall.MIN_Z + 1 : start();
        }

        private boolean contains(int slot, Vector p, boolean cleared) {
            int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
            return switch (this) {
                case ENTRY -> EntryTrack.forSlot(slot).contains(x, y, z);
                case WAREHOUSE -> WarehouseRoom.contains(slot, x, y, z, cleared);
                case SLIDER -> SliderHall.contains(x, y, z, cleared);
                case JOINT -> JointPlaza.contains(p.getX(), p.getY(), p.getZ(), cleared);
                case SPRINT ->
                        x > 0 && x < WIDTH - 1 && y >= 1 && y < 9 && z >= start() && z < LENGTH - 1;
            };
        }

        public boolean entered(int slot, Vector position, double minZ) {
            return minZ >= insideZ() && contains(slot, position, false);
        }

        public boolean allows(int slot, Vector position, boolean begun) {
            if (contains(slot, position, false) && (!begun || position.getZ() >= insideZ()))
                return true;
            if (begun || this == ENTRY) return false;
            Room previous = previous();
            return position.getZ() >= previous.insideZ() && previous.contains(slot, position, true);
        }

        public Vector restoreSpawn(int slot) {
            return switch (this) {
                case WAREHOUSE -> WarehouseRoom.spawn(slot);
                case JOINT -> JointPlaza.restoreSpawn(slot);
                case SLIDER, SPRINT -> new Vector(7.5 + slot, 1, start() + 1.5);
                case ENTRY -> throw new IllegalStateException("Use the personal entry checkpoint");
            };
        }

        public int start() {
            return switch (this) {
                case ENTRY -> EntryTrack.MIN_Z + 1;
                case WAREHOUSE -> WarehouseRoom.MIN_Z;
                case SLIDER -> WAREHOUSE.end + 1;
                case JOINT -> SLIDER.end + 1;
                case SPRINT -> JOINT.end + 1;
            };
        }
    }

    private static BoundingBox centralGate(Room cleared) {
        int z = cleared == Room.WAREHOUSE ? SliderHall.MIN_Z : cleared.end;
        return new BoundingBox(7, 1, z, 12, 5, z + 1);
    }

    public static boolean gateOccupied(Room cleared, BoundingBox laneRelativeBox) {
        return switch (cleared) {
            case ENTRY -> WarehouseRoom.gateOccupied(laneRelativeBox, true);
            case WAREHOUSE ->
                    WarehouseRoom.gateOccupied(laneRelativeBox, false)
                            || centralGate(cleared).overlaps(laneRelativeBox);
            default -> centralGate(cleared).overlaps(laneRelativeBox);
        };
    }

    public static void setGate(World world, Vector origin, Room cleared, boolean open) {
        if (cleared == Room.ENTRY || cleared == Room.WAREHOUSE)
            WarehouseRoom.setGates(world, origin, cleared == Room.ENTRY, open);
        if (cleared != Room.ENTRY) setGateBlocks(world, origin, centralGate(cleared), open);
    }

    public static void closeSliderEntrance(World world, Vector origin) {
        setGateBlocks(world, origin, centralGate(Room.WAREHOUSE), false);
    }

    static void setGateBlocks(World world, Vector origin, BoundingBox gate, boolean open) {
        Material material = open ? Material.AIR : Material.RED_STAINED_GLASS;
        for (int x = (int) gate.getMinX(); x < gate.getMaxX(); x++)
            for (int y = (int) gate.getMinY(); y < gate.getMaxY(); y++)
                for (int z = (int) gate.getMinZ(); z < gate.getMaxZ(); z++)
                    world.getBlockAt(
                                    origin.getBlockX() + x,
                                    origin.getBlockY() + y,
                                    origin.getBlockZ() + z)
                            .setType(material, false);
    }

    private VerificationCourse() {}
}
