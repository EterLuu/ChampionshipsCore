package ink.ziip.championshipscore.api.game.sulfursoccer.config;

import static ink.ziip.championshipscore.api.game.sulfursoccer.support.SulfurSoccerTestFixtures.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

class SulfurSoccerConfigTest {

    @Test
    void validatesFourSpawnsPerHalfWithoutLoadingBukkitWorld() throws Exception {
        assertDoesNotThrow(config()::validate);
    }

    @Test
    void rejectsWrongCountsDuplicatesWrongWorldAndCrossHalfSpawns() throws Exception {
        var config = config();
        for (List<String> values :
                List.of(
                        List.<String>of(),
                        List.of("soccer:4:1:0:0:0"),
                        List.of(
                                "soccer:4:1:0:0:0",
                                "soccer:4:1:0:90:0",
                                "soccer:6:1:0:0:0",
                                "soccer:8:1:0:0:0"),
                        List.of(
                                "other:4:1:0:0:0",
                                "soccer:6:1:0:0:0",
                                "soccer:8:1:0:0:0",
                                "soccer:10:1:0:0:0"),
                        List.of(
                                "soccer:-4:1:0:0:0",
                                "soccer:6:1:0:0:0",
                                "soccer:8:1:0:0:0",
                                "soccer:10:1:0:0:0"))) {
            config.setRightSpawnPoints(values);
            assertThrows(IllegalArgumentException.class, config::validate);
        }
    }

    @Test
    void rejectsOverlappingHalvesWrongGoalsAndUnsafeKickoff() throws Exception {
        var config = config();
        config.setLeftAreaPos2(new Vector(0, 8, 10));
        assertThrows(IllegalArgumentException.class, config::validate);
        config = config();
        config.setRightGoalPos1(new Vector(-10, 0, -2));
        assertThrows(IllegalArgumentException.class, config::validate);
        config = config();
        config.setBallSpawnPoint(new Location(null, 19, 1, 0));
        assertThrows(IllegalArgumentException.class, config::validate);
        config = config();
        config.setSpectatorSpawnPoint(new Location(null, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void rejectsMalformedCoordinatesAndInvalidMatchSettings() throws Exception {
        var config = config();
        for (String raw :
                List.of(
                        "soccer:NaN:1:0:0:0",
                        "soccer:4:1:0:Infinity:0",
                        "soccer:4:1:0:0",
                        "other:4:1:0:0:0"))
            assertThrows(IllegalArgumentException.class, () -> config.parseSpawn(raw));
        config.setGoalsToWin(0);
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setGoalsToWin(5);
        config.setKickoffCountdown(61);
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void publicationRejectsGoalsThatCannotSupportThreePenaltyDirections() throws Exception {
        var config = config();
        config.setRightGoalPos1(new Vector(18, 0, -1));
        config.setRightGoalPos2(new Vector(20, 3, 0));
        assertThrows(IllegalArgumentException.class, config::validate);
        config = config();
        config.setLeftGoalPos2(new Vector(-18, 1, 2));
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void validatesIndependentAccentCoordinatesAndProtectsPitchAndFloor() throws Exception {
        var config = config();
        config.setRightTeamColorBlocks(List.of("24:0:-8"));
        config.setLeftTeamColorBlocks(List.of("-24:0:8"));
        assertDoesNotThrow(config::validate);
        config.setLeftTeamColorBlocks(List.of("24:0:-8"));
        assertThrows(IllegalArgumentException.class, config::validate);
        config.setLeftTeamColorBlocks(List.of());
        for (List<String> blocks :
                List.of(List.of("24:0:-8", "24:0:-8"), List.of("0:0:0"), List.of("0:-1:0"))) {
            config.setRightTeamColorBlocks(blocks);
            assertThrows(IllegalArgumentException.class, config::validate);
        }
    }

    @Test
    void rejectsMalformedAndOutOfWorldAccentCoordinates() {
        for (String raw :
                new String[] {null, "", "1:2", "1.5:2:3", "0:NaN:0", "0:320:0", "30000000:88:0"})
            assertThrows(
                    IllegalArgumentException.class, () -> SulfurSoccerConfig.parseColorBlock(raw));
    }
}
