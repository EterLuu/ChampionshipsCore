package ink.ziip.championshipscore.api.player;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import me.clip.placeholderapi.PlaceholderAPI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;

public class ChampionshipPlayer {
    @NotNull private final UUID playerUUID;

    protected ChampionshipPlayer(@NotNull UUID uuid) {
        this.playerUUID = uuid;
    }

    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    public void sendActionBar(String content) {
        Player player = getPlayer();
        if (player == null) return;
        player.sendActionBar(LegacyText.component(setPlaceholders(content)));
    }

    public void setRedScreen() {
        Player player = getPlayer();
        if (player != null)
            sendWarningDistance(player, (int) player.getWorld().getWorldBorder().getSize());
    }

    public void removeRedScreen() {
        Player player = getPlayer();
        if (player != null) sendWarningDistance(player, 0);
    }

    private void sendWarningDistance(Player player, int distance) {
        com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getPlayerManager()
                .sendPacket(
                        player,
                        new com.github.retrooper.packetevents.wrapper.play.server
                                .WrapperPlayServerWorldBorderWarningReach(distance));
    }

    public void sendMessage(String content) {
        Player player = getPlayer();
        if (player == null) return;
        player.sendMessage(LegacyText.translateColorCodes(setPlaceholders(content)));
    }

    public void setLevel(int level) {
        Player player = getPlayer();
        if (player == null) return;
        player.setLevel(level);
    }

    public void playSound(Sound sound, float volume, float pitch) {
        Player player = getPlayer();
        if (player == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void sendTitle(String title, String subTitle) {
        sendTitle(title, subTitle, 20);
    }

    public void sendTitle(String title, String subTitle, int stayTicks) {
        Player player = getPlayer();
        if (player == null) return;
        // Use the shared serializer so six-digit colours are handled consistently with chat and
        // boss bars.
        Component titleComponent = LegacyText.component(setPlaceholders(title));
        Component subTitleComponent = LegacyText.component(setPlaceholders(subTitle));
        // fade-in 1 tick, caller-defined stay, fade-out 1 tick
        Title.Times times =
                Title.Times.times(
                        Duration.ofMillis(50),
                        Duration.ofMillis(Math.max(0, stayTicks) * 50L),
                        Duration.ofMillis(50));
        player.showTitle(Title.title(titleComponent, subTitleComponent, times));
    }

    private String setPlaceholders(String content) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        return LegacyText.translateColorCodes(
                PlaceholderAPI.setPlaceholders(offlinePlayer, content));
    }

    @Nullable
    public ChampionshipTeam getChampionshipTeam() {
        Player player = getPlayer();
        if (player == null) return null;
        return ChampionshipsCore.getInstance().getTeamManager().getTeamByPlayer(player);
    }
}
