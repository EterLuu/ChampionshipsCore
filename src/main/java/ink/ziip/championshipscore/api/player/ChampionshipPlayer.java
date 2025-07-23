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
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ChampionshipPlayer {
    @NotNull
    private final UUID playerUUID;
    @Nullable
    @Getter
    private Player player;
    @Nullable
    private OfflinePlayer offlinePlayer;

    protected ChampionshipPlayer(@NotNull UUID uuid) {
        this.playerUUID = uuid;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            this.player = player;

        this.offlinePlayer = Bukkit.getOfflinePlayer(uuid);
    }

    public void updatePlayer() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null)
            this.player = player;

        this.offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
    }

    public void sendActionBar(String content) {
        if (player == null)
            return;
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromLegacyText(setPlaceholders(content));
        PacketContainer packetContainer = protocolManager.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
        StructureModifier<Integer> integers = packetContainer.getIntegers();
        if (integers.size() == 1) {
            integers.write(0, (int) EnumWrappers.ChatType.GAME_INFO.getId());
        } else {
            packetContainer.getBooleans().write(0, true);
        }
        packetContainer.getChatComponents().write(0, wrappedChatComponent);
        protocolManager.sendServerPacket(player, packetContainer);
    }

    public void setRedScreen() {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE);

        if (player == null)
            return;

        World world = player.getWorld();

        WorldBorder worldBorder = world.getWorldBorder();

        packet.getModifier().writeDefaults();
        packet.getIntegers().write(0, (int) worldBorder.getSize());
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    public void removeRedScreen() {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE);

        packet.getModifier().writeDefaults();
        packet.getIntegers().write(0, 0);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    public void sendMessage(String content) {
        if (player == null)
            return;
        player.sendMessage(Utils.translateColorCodes(setPlaceholders(content)));
    }

    public void setLevel(int level) {
        if (player == null)
            return;
        player.setLevel(level);
    }

    public void playSound(Sound sound, float volume, float pitch) {
        if (player == null)
            return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void sendTitle(String title, String subTitle) {
        if (player == null)
            return;
        player.sendTitle(Utils.translateColorCodes(setPlaceholders(title)), Utils.translateColorCodes(setPlaceholders(subTitle)), 1, 20, 1);
    }

    private String setPlaceholders(String content) {
        if (offlinePlayer == null)
            return "";

        // Using offlinePlayer to avoid issues
        return Utils.translateColorCodes(PlaceholderAPI.setPlaceholders(offlinePlayer, content));
    }

    @Nullable
    public ChampionshipTeam getChampionshipTeam() {
        if (player == null)
            return null;
        return ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
    }
}
