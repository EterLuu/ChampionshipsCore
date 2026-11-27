package ink.ziip.championshipscore.platform.bukkit.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native packet delivery and authoritative entity flags on the owning entity thread. */
public final class NativeEntityPackets {
    private static final Method GET_HANDLE, SEND, ENTITY_DATA, DATA_GET;
    private static final Field CONNECTION;
    private static final Object FLAGS;

    static {
        try {
            GET_HANDLE =
                    Class.forName("org.bukkit.craftbukkit.entity.CraftEntity")
                            .getMethod("getHandle");
            CONNECTION =
                    Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            SEND =
                    CONNECTION
                            .getType()
                            .getMethod(
                                    "send", Class.forName("net.minecraft.network.protocol.Packet"));
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            var field = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            FLAGS = field.get(null);
            ENTITY_DATA = entity.getMethod("getEntityData");
            DATA_GET =
                    ENTITY_DATA
                            .getReturnType()
                            .getMethod(
                                    "get",
                                    Class.forName(
                                            "net.minecraft.network.syncher.EntityDataAccessor"));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private NativeEntityPackets() {}

    static Object handle(Entity entity) throws ReflectiveOperationException {
        return GET_HANDLE.invoke(entity);
    }

    public static byte sharedFlags(Entity entity) {
        try {
            return (Byte) DATA_GET.invoke(ENTITY_DATA.invoke(handle(entity)), FLAGS);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read entity flags", failure);
        }
    }

    public static void send(Player viewer, Object packet) {
        try {
            SEND.invoke(CONNECTION.get(handle(viewer)), packet);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot send native packet", failure);
        }
    }
}
