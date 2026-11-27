package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.support.TestObjects;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class SliderStationTest {
    @Test
    void foreignAndUnclaimedRailsEjectWithoutGrantingAHandle() {
        UUID visitor = UUID.randomUUID(), owner = UUID.randomUUID();
        var origin = new Vector(256, 80, 0);
        var hall = SliderHall.B;
        var station =
                new SliderStation(
                        null, null, origin, hall, null, null, null, null, new RobotProps());
        Location[] position = new Location[1];
        Component[] message = new Component[1];
        Player player =
                TestObjects.proxy(
                        Player.class,
                        (self, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> visitor;
                                    case "getLocation" -> position[0].clone();
                                    case "teleport" -> {
                                        position[0] = (Location) args[0];
                                        yield true;
                                    }
                                    case "setFallDistance" -> null;
                                    case "sendActionBar" -> {
                                        message[0] = (Component) args[0];
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        var outside = hall.outside().add(origin);
        position[0] = hall.start().add(origin).toLocation(null);
        station.sample(player, 20, false, false, SliderHall.A);
        assertEquals(outside, position[0].toVector());
        assertNull(station.state.holder());
        assertEquals(
                LegacyText.component(RobotArea.text("slider-unassigned", "B", "A")), message[0]);

        station.assign("Sam");
        position[0] = hall.start().add(origin).add(new Vector(8, 0, 0)).toLocation(null);
        station.sample(player, 40, false, false, SliderHall.A);
        assertEquals(outside, position[0].toVector());
        assertNull(station.state.holder());
        var wrong = RobotArea.text("slider-other", "Sam", "B", "A");
        assertEquals(LegacyText.component(wrong), message[0]);

        assertTrue(station.state.grip(owner, 42));
        position[0] = hall.start().add(origin).toLocation(null);
        station.sample(player, 44, false, false, SliderHall.A);
        assertEquals(outside, position[0].toVector());
        assertEquals(owner, station.state.holder());
        assertEquals(
                LegacyText.component(RobotArea.text("slider-occupied", "Sam") + " · " + wrong),
                message[0]);
    }
}
