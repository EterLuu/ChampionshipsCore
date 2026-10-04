package ink.ziip.championshipscore.platform.bukkit.player;

import static org.junit.jupiter.api.Assertions.*;

import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class PlayerPacketPresentationTest {
    @Test
    void onlySpectatorModeIsRewrittenForClients() {
        for (GameMode mode : GameMode.values())
            assertEquals(
                    mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : mode,
                    PlayerPacketPresentation.clientMode(mode));
        assertNull(PlayerPacketPresentation.clientMode(null));
    }

    @Test
    void onlineHiddenSpectatorKeepsItsCompleteTabIdentityAndStatus() {
        var original =
                new PlayerInfo(
                        new UserProfile(UUID.randomUUID(), "Spectator"),
                        false,
                        137,
                        GameMode.SPECTATOR,
                        Component.text("custom display"),
                        null,
                        23,
                        false);
        var result = PlayerPacketPresentation.presentedInfo(original, true);
        assertTrue(result.isListed());
        assertEquals(GameMode.ADVENTURE, result.getGameMode());
        assertEquals(original.getGameProfile(), result.getGameProfile());
        assertEquals(original.getLatency(), result.getLatency());
        assertEquals(original.getDisplayName(), result.getDisplayName());
        assertEquals(original.getChatSession(), result.getChatSession());
        assertEquals(23, result.getListOrder());
        assertFalse(result.isShowHat());
        assertFalse(original.isListed());
        assertEquals(GameMode.SPECTATOR, original.getGameMode());
    }

    @Test
    void spectatorsSeeInvisiblePlayersAndOtherFlagsRemainUnchanged() {
        for (int value = 0; value < 256; value++) {
            byte flags = (byte) value;
            assertEquals(flags, PlayerPacketPresentation.clientFlags(flags, false, false));
            assertEquals(
                    (byte) (value & ~0x20),
                    PlayerPacketPresentation.clientFlags(flags, false, true));
            assertEquals(
                    (byte) (value & ~0x20),
                    PlayerPacketPresentation.clientFlags(flags, true, false));
        }
    }

    @Test
    void normalPlayerModesAndOfflineSyntheticTabEntriesRemainIntact() {
        var original =
                new PlayerInfo(
                        new UserProfile(UUID.randomUUID(), "Player"),
                        false,
                        4,
                        GameMode.SURVIVAL,
                        null,
                        null);
        assertEquals(
                GameMode.SURVIVAL,
                PlayerPacketPresentation.presentedInfo(original, true).getGameMode());
        assertTrue(PlayerPacketPresentation.presentedInfo(original, true).isListed());
        assertFalse(PlayerPacketPresentation.presentedInfo(original, false).isListed());
    }
}
