package ink.ziip.championshipscore.api.game.riptiderush;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Paper's vanilla moving-piston entity owns all collision, displacement and slime momentum. */
final class RiptideNativePiston {
    private static Access access;

    private RiptideNativePiston() { }

    static void prepare() {
        if (access != null) return;
        try { access = new Access(); }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("当前服务端不支持激流侧墙的原生活塞", failure);
        }
    }

    static BlockFace facing(int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1) throw new IllegalArgumentException("piston must move one block horizontally");
        return dx == 1 ? BlockFace.EAST : dx == -1 ? BlockFace.WEST : dz == 1 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    static void move(Block target, BlockData carried, BlockFace face) {
        prepare();
        try {
            Object level = access.worldHandle.invoke(target.getWorld());
            Object pos = access.position.newInstance(target.getX(), target.getY(), target.getZ());
            var moving = (Directional) Material.MOVING_PISTON.createBlockData();
            moving.setFacing(face);
            Object state = access.blockState.invoke(moving);
            Object direction = access.direction.getField(face.name()).get(null);
            Object entity = access.piston.newInstance(pos, state, access.blockState.invoke(carried), direction, true, false);
            target.setBlockData(moving, false);
            access.setBlockEntity.invoke(level, entity);

            // A block update alone cannot create a client moving-piston entity. A vanilla extension
            // first creates its head; the entity tag then replaces that head with the carried block.
            var source = target.getRelative(face.getOppositeFace());
            var piston = (Directional) Material.PISTON.createBlockData();
            piston.setFacing(face);
            Object sourcePos = access.position.newInstance(source.getX(), source.getY(), source.getZ());
            Object event = access.event.newInstance(sourcePos, access.pistonBlock, 0, access.directionId.invoke(direction));
            Object update = access.entityPacket.invoke(null, entity);
            // Process the temporary source and its restoration in one client tick.
            var animation = PacketContainer.fromPacket(access.bundle.newInstance(List.of(
                    access.blockPacket.newInstance(pos, access.blockState.invoke(Material.AIR.createBlockData())),
                    access.blockPacket.newInstance(sourcePos, access.blockState.invoke(piston)),
                    event,
                    access.blockPacket.newInstance(sourcePos, access.blockState.invoke(source.getBlockData())),
                    update)));
            for (var player : target.getWorld().getPlayersSeeingChunk(target.getX() >> 4, target.getZ() >> 4)) {
                if (!player.isChunkSent(org.bukkit.Chunk.getChunkKey(source.getLocation()))) continue;
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, animation);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("激流原生活塞移动失败", failure);
        }
    }

    static void remove(Block block) {
        prepare();
        try {
            access.removeBlockEntity.invoke(access.worldHandle.invoke(block.getWorld()),
                    access.position.newInstance(block.getX(), block.getY(), block.getZ()));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("清理激流原生活塞失败", failure);
        }
    }

    static void settle(Block block) {
        try {
            Object level = access.worldHandle.invoke(block.getWorld());
            Object pos = access.position.newInstance(block.getX(), block.getY(), block.getZ());
            Object entity = access.getBlockEntity.invoke(level, pos);
            if (!access.piston.getDeclaringClass().isInstance(entity)
                    || (float) access.progress.invoke(entity, 1F) < 1F) return;
            // Vanilla has completed both motion ticks. Preserve authored fence/stair connections
            // instead of letting the next tick recompute shapes and close intentional openings.
            var carried = (BlockData) access.createData.invoke(null, access.movedState.invoke(entity));
            access.removeBlockEntity.invoke(level, pos);
            block.setBlockData(carried, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("结束激流原生活塞移动失败", failure);
        }
    }

    private static final class Access {
        final Class<?> direction;
        final Constructor<?> position, piston, event, blockPacket, bundle;
        final Method worldHandle, blockState, setBlockEntity, removeBlockEntity, entityPacket, directionId;
        final Method getBlockEntity, progress, movedState, createData;
        final Object pistonBlock;

        Access() throws ReflectiveOperationException {
            Class<?> pos = Class.forName("net.minecraft.core.BlockPos");
            Class<?> state = Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> entity = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            direction = Class.forName("net.minecraft.core.Direction");
            directionId = direction.getMethod("get3DDataValue");
            position = pos.getConstructor(int.class, int.class, int.class);
            piston = Class.forName("net.minecraft.world.level.block.piston.PistonMovingBlockEntity")
                    .getConstructor(pos, state, state, direction, boolean.class, boolean.class);
            progress = piston.getDeclaringClass().getMethod("getProgress", float.class);
            movedState = piston.getDeclaringClass().getMethod("getMovedState");
            worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
            blockState = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData").getMethod("getState");
            createData = blockState.getDeclaringClass().getMethod("createData", state);
            setBlockEntity = level.getMethod("setBlockEntity", entity);
            getBlockEntity = level.getMethod("getBlockEntity", pos);
            removeBlockEntity = level.getMethod("removeBlockEntity", pos);
            event = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockEventPacket")
                    .getConstructor(pos, Class.forName("net.minecraft.world.level.block.Block"), int.class, int.class);
            entityPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket")
                    .getMethod("create", entity);
            blockPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket")
                    .getConstructor(pos, state);
            bundle = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket")
                    .getConstructor(Iterable.class);
            pistonBlock = Class.forName("net.minecraft.world.level.block.Blocks").getField("PISTON").get(null);
        }
    }
}
