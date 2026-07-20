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
        if (player == null)
            return;

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
        // legacySection() decodes the §-prefixed codes (incl. §x hex) that translateColorCodes emits.
        Component titleComponent = LegacyComponentSerializer.legacySection()
                .deserialize(Utils.translateColorCodes(setPlaceholders(title)));
        Component subTitleComponent = LegacyComponentSerializer.legacySection()
                .deserialize(Utils.translateColorCodes(setPlaceholders(subTitle)));
        // fade-in 1 tick, stay 20 ticks, fade-out 1 tick
        Title.Times times = Title.Times.times(Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofMillis(50));
        player.showTitle(Title.title(titleComponent, subTitleComponent, times));
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
