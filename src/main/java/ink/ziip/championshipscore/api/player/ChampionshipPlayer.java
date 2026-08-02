package ink.ziip.championshipscore.api.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

public class ChampionshipPlayer {
    @NotNull
    private final UUID playerUUID;
    @Nullable
    @Getter
    private volatile Player player;

    protected ChampionshipPlayer(@NotNull UUID uuid) {
        this.playerUUID = uuid;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            this.player = player;

    }

    public void updatePlayer() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null)
            this.player = player;

    }

    public void sendActionBar(String content) {
        Player current = player;
        if (current == null)
            return;
        scheduler().runEntity(current, () -> {
            String message = setPlaceholders(current, content);
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromLegacyText(message);
            PacketContainer packetContainer = protocolManager.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
            StructureModifier<Integer> integers = packetContainer.getIntegers();
            if (integers.size() == 1) {
                integers.write(0, (int) EnumWrappers.ChatType.GAME_INFO.getId());
            } else {
                packetContainer.getBooleans().write(0, true);
            }
            packetContainer.getChatComponents().write(0, wrappedChatComponent);
            protocolManager.sendServerPacket(current, packetContainer);
        });
    }

    public void setRedScreen() {
        if (player == null)
            return;

        Player current = player;
        scheduler().runEntity(current, () -> {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE);
            World world = current.getWorld();
            WorldBorder worldBorder = world.getWorldBorder();
            packet.getModifier().writeDefaults();
            packet.getIntegers().write(0, (int) worldBorder.getSize());
            ProtocolLibrary.getProtocolManager().sendServerPacket(current, packet);
        });
    }

    public void removeRedScreen() {
        if (player == null)
            return;

        Player current = player;
        scheduler().runEntity(current, () -> {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE);
            packet.getModifier().writeDefaults();
            packet.getIntegers().write(0, 0);
            ProtocolLibrary.getProtocolManager().sendServerPacket(current, packet);
        });
    }

    public void sendMessage(String content) {
        if (player == null)
            return;
        Player current = player;
        scheduler().runEntity(current, () -> current.sendMessage(
                Utils.translateColorCodes(setPlaceholders(current, content))));
    }

    public void setLevel(int level) {
        if (player == null)
            return;
        Player current = player;
        scheduler().runEntity(current, () -> current.setLevel(level));
    }

    public void playSound(Sound sound, float volume, float pitch) {
        if (player == null)
            return;
        Player current = player;
        scheduler().runEntity(current, () -> current.playSound(current.getLocation(), sound, volume, pitch));
    }

    public void sendTitle(String title, String subTitle) {
        if (player == null)
            return;
        Player current = player;
        scheduler().runEntity(current, () -> {
            // legacySection() decodes the §-prefixed codes (incl. §x hex) that translateColorCodes emits.
            Component titleComponent = LegacyComponentSerializer.legacySection()
                    .deserialize(Utils.translateColorCodes(setPlaceholders(current, title)));
            Component subTitleComponent = LegacyComponentSerializer.legacySection()
                    .deserialize(Utils.translateColorCodes(setPlaceholders(current, subTitle)));
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofMillis(50));
            current.showTitle(Title.title(titleComponent, subTitleComponent, times));
        });
    }

    private String setPlaceholders(OfflinePlayer target, String content) {
        if (target == null)
            return "";

        return Utils.translateColorCodes(PlaceholderAPI.setPlaceholders(target, content));
    }

    @Nullable
    public ChampionshipTeam getChampionshipTeam() {
        if (player == null)
            return null;
        return ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
    }

    private FoliaScheduler scheduler() {
        return FoliaScheduler.global(ChampionshipsCore.getInstance());
    }
}
