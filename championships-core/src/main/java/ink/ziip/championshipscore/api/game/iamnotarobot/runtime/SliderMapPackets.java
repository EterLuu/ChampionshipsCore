package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;

import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Maps are immutable within this edition. Paper may also send the same map on a cursor tick. */
final class SliderMapPackets extends PacketListenerAbstract {
    private final ConcurrentHashMap<User, Set<Integer>> sent = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, byte[]> pixels = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.LongAdder ordinaryBytes =
            new java.util.concurrent.atomic.LongAdder();

    private volatile boolean retired;

    void retire() {
        retired = true;
        sent.clear();
    }

    long drainBytes() {
        return ordinaryBytes.sumThenReset();
    }

    void publish(int id, byte[] tile) {
        pixels.put(id, tile);
    }

    SliderMapPackets() {
        super(PacketListenerPriority.HIGHEST);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    boolean push(Player viewer, int id, byte scale, byte[] pixels) {
        var manager = PacketEvents.getAPI().getPlayerManager();
        var user = manager.getUser(viewer);
        if (user == null) return false;
        var known = sent.computeIfAbsent(user, ignored -> ConcurrentHashMap.newKeySet());
        if (known.add(id)) {
            var packet =
                    new WrapperPlayServerMapData(
                            id, scale, false, true, List.of(), 128, 128, 0, 0, pixels);
            try {
                manager.sendPacketSilently(viewer, packet);
            } catch (RuntimeException failure) {
                known.remove(id);
                throw failure;
            }
        }
        return true;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        if (retired) {
            if (event.getPacketType() == PacketType.Play.Server.MAP_DATA
                    && pixels.containsKey(new WrapperPlayServerMapData(event).getMapId()))
                event.setCancelled(true);
            return;
        }
        // PacketEvents exposes the encoded backend packet before Minecraft compression. Silent
        // postcard sends bypass this hook and are reserved explicitly by SliderDelivery.
        int bytes =
                com.github.retrooper.packetevents.netty.buffer.ByteBufHelper.writerIndex(
                                event.getByteBuf())
                        + 3;
        if (event.getPacketType() != PacketType.Play.Server.MAP_DATA) {
            ordinaryBytes.add(bytes);
            return;
        }
        var packet = new WrapperPlayServerMapData(event);
        int id = packet.getMapId();
        var tile = pixels.get(id);
        if (tile == null) {
            ordinaryBytes.add(bytes);
            return;
        }
        var known = sent.computeIfAbsent(event.getUser(), ignored -> ConcurrentHashMap.newKeySet());
        // A native cursor tick can beat the budgeted push when a viewer arrives. Only a
        // complete patch of this edition can satisfy that delivery. Partial updates cannot.
        if (known.contains(id)
                || (packet.getColumns() == 128
                        && packet.getRows() == 128
                        && packet.getX() == 0
                        && packet.getZ() == 0
                        && Arrays.equals(tile, packet.getData())
                        && !known.add(id))) {
            event.setCancelled(true);
        } else ordinaryBytes.add(bytes);
    }

    void forget(Player viewer) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user != null) sent.remove(user);
    }

    void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        sent.clear();
        pixels.clear();
    }
}
