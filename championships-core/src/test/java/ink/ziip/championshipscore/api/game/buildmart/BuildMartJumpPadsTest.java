package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BuildMartJumpPadsTest {
    @Test void facesHorizontallyAlongYawWithOnlyASmallForwardImpulse() {
        for (float yaw : new float[]{0, 90, 180, -90}) {
            Vector velocity = BuildMartJumpPads.launchVelocity(yaw, 4.5);
            assertEquals(.3, Math.hypot(velocity.getX(), velocity.getZ()), 1e-8);
            assertEquals(4.5, velocity.getY());
        }
        assertEquals(.3, BuildMartJumpPads.launchVelocity(0, 4.5).getZ(), 1e-8);
        assertEquals(-.3, BuildMartJumpPads.launchVelocity(90, 4.5).getX(), 1e-8);
    }

    @Test void singleImpulseBallisticApexTargetsY180AcrossDifferentPadHeights() {
        for (double source : new double[]{-20, 101, 102, 150, 179}) {
            double y = source, velocity = BuildMartJumpPads.verticalVelocity(source);
            while (velocity > 0) { y += velocity; velocity = (velocity - .08) * .98; }
            assertEquals(180, y, 1e-7);
        }
        assertEquals(0, BuildMartJumpPads.verticalVelocity(180));
        assertEquals(0, BuildMartJumpPads.verticalVelocity(200));
    }

    @Test void firstContactArmsAndFollowingTickAppliesOneFixedImpulse() {
        Fixture fixture = new Fixture();
        fixture.velocity = new Vector(2, -.2, -3);
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(0, fixture.launches);
        assertTrue(fixture.pads.sample(fixture.player));
        assertEquals(1, fixture.launches);
        assertEquals(.3, fixture.velocity.getZ(), 1e-8);
        assertEquals(0, fixture.velocity.getX(), 1e-8);
        assertEquals(BuildMartJumpPads.verticalVelocity(102), fixture.velocity.getY());
        assertEquals(0, fixture.fallDistance);
    }

    @Test void pitchCannotMultiplyOrCancelTheUpwardLaunch() {
        for (float pitch : new float[]{-90, 0, 90}) {
            Fixture fixture = new Fixture();
            fixture.location.setPitch(pitch);
            fixture.pads.sample(fixture.player);
            assertTrue(fixture.pads.sample(fixture.player));
            assertEquals(BuildMartJumpPads.verticalVelocity(102), fixture.velocity.getY());
            assertEquals(.3, fixture.velocity.getZ(), 1e-8);
        }
    }

    @Test void pendingContactMustRemainGroundedOnTheSamePad() {
        Fixture fixture = new Fixture();
        fixture.pads.sample(fixture.player);
        fixture.grounded = false;
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.grounded = true;
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.location.setX(190);
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(0, fixture.launches);
    }

    @Test void jumpingBeforeTheConfirmationCannotTriggerAnAirborneBoost() {
        Fixture fixture = new Fixture();
        fixture.pads.sample(fixture.player);
        fixture.velocity.setY(.42);
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.location.setY(120);
        fixture.velocity.setY(0);
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(0, fixture.launches);
    }

    @Test void repeatGroundPacketsAndWalkingBetweenBlocksOfOnePadNeverRelaunch() {
        Fixture fixture = new Fixture();
        fixture.pads.sample(fixture.player);
        fixture.pads.sample(fixture.player);
        for (int tick = 0; tick < 10; tick++) assertFalse(fixture.pads.sample(fixture.player));
        fixture.velocity.setY(0);
        fixture.location.setX(184);
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(1, fixture.launches);
    }

    @Test void leavingAndLandingAgainAllowsAFreshLaunch() {
        Fixture fixture = new Fixture();
        fixture.pads.sample(fixture.player);
        fixture.pads.sample(fixture.player);
        fixture.grounded = false;
        fixture.location.setY(150);
        fixture.pads.sample(fixture.player);
        fixture.grounded = true;
        fixture.location.setY(102);
        fixture.velocity.setY(0);
        assertFalse(fixture.pads.sample(fixture.player));
        assertTrue(fixture.pads.sample(fixture.player));
        assertEquals(2, fixture.launches);
    }

    @Test void glidingFlyingRidingSpectatorDeadAndOfflinePlayersNeverArm() {
        Fixture fixture = new Fixture();
        for (String flag : List.of("gliding", "flying", "riding", "dead")) {
            try {
                var field = Fixture.class.getDeclaredField(flag);
                field.setBoolean(fixture, true);
                assertFalse(fixture.pads.sample(fixture.player));
                assertFalse(fixture.pads.sample(fixture.player));
                field.setBoolean(fixture, false);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        fixture.mode = GameMode.SPECTATOR;
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.mode = GameMode.SURVIVAL;
        fixture.online = false;
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(0, fixture.launches);
    }

    @Test void groundFlagWithoutPhysicalSupportAndOtherWorldCannotArm() {
        Fixture fixture = new Fixture();
        fixture.passable = true;
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.passable = false;
        fixture.location.setWorld(null);
        assertFalse(fixture.pads.sample(fixture.player));
        assertEquals(0, fixture.launches);
    }

    @Test void clearingAndForgettingCancelArmedContacts() {
        Fixture fixture = new Fixture();
        fixture.pads.sample(fixture.player);
        fixture.pads.clear();
        assertFalse(fixture.pads.sample(fixture.player));
        fixture.pads.forget(fixture.uuid);
        assertFalse(fixture.pads.sample(fixture.player));
        assertTrue(fixture.pads.sample(fixture.player));
    }

    @Test void eachPlayerHasIndependentContactConfirmation() {
        Fixture first = new Fixture(), second = new Fixture();
        second.location.setWorld(first.world);
        assertFalse(first.pads.sample(first.player));
        assertFalse(first.pads.sample(second.player));
        assertTrue(first.pads.sample(first.player));
        assertTrue(first.pads.sample(second.player));
        assertEquals(1, first.launches);
        assertEquals(1, second.launches);
    }

    @Test void currentJumpPadCoordinatesLoadAndSaveUnderTheConfiguredName() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world-name", "buildmart_area");
        yaml.set("jump-pads", List.of(Map.of("pos1", Map.of("x", 182, "y", 101, "z", 192),
                "pos2", Map.of("x", 185, "y", 101, "z", 195))));
        yaml.set("custom", "preserved");
        BuildMartConfig config = config(yaml);
        config.loadCustomFileOptions();
        assertEquals(1, config.getJumpPads().size());
        assertEquals(new Vector(182, 101, 192), config.getJumpPads().getFirst().pos1());
        assertEquals(new Vector(185, 101, 195), config.getJumpPads().getFirst().pos2());
        config.saveCustomOptions();
        assertEquals(1, yaml.getMapList("jump-pads").size());
        assertEquals("preserved", yaml.getString("custom"));
        config.loadCustomFileOptions();
        Fixture fixture = new Fixture();
        assertTrue(config.isInPlayableArea(new Location(fixture.world, 189, 170, 193)));
        assertTrue(config.isInPlayableArea(new Location(fixture.world, 183, 180, 193)));
    }

    @Test void explicitlyEmptyNewJumpPadListDoesNotRestoreOldSelections() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jump-pads", List.of());
        BuildMartConfig config = config(yaml);
        config.loadCustomFileOptions();
        assertTrue(config.getJumpPads().isEmpty());
    }

    private static BuildMartConfig config(YamlConfiguration yaml) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        BuildMartConfig config = (BuildMartConfig) ((sun.misc.Unsafe) field.get(null)).allocateInstance(BuildMartConfig.class);
        var configuration = BaseConfigurationFile.class.getDeclaredField("configuration");
        configuration.setAccessible(true);
        configuration.set(config, yaml);
        return config;
    }

    private static final class Fixture {
        final UUID uuid = UUID.randomUUID();
        boolean passable, gliding, flying, riding, dead;
        boolean grounded = true, online = true;
        GameMode mode = GameMode.SURVIVAL;
        int launches;
        float fallDistance = 12;
        Vector velocity = new Vector();
        final Block support = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isPassable")) return passable;
                    throw new UnsupportedOperationException(method.getName());
                });
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockAt" -> support;
                    case "getName" -> "buildmart_area";
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final BuildMartJumpPads pads = new BuildMartJumpPads(world, List.of(new BuildMartConfig.JumpPadZone(
                new Vector(182, 101, 192), new Vector(185, 101, 195))));
        final Location location = new Location(world, 183, 102, 193);
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> location.clone();
                    case "getVelocity" -> velocity.clone();
                    case "setVelocity" -> { velocity = (Vector) args[0]; launches++; yield null; }
                    case "setFallDistance" -> { fallDistance = (float) args[0]; yield null; }
                    case "isOnline" -> online;
                    case "isDead" -> dead;
                    case "getGameMode" -> mode;
                    case "isGliding" -> gliding;
                    case "isFlying" -> flying;
                    case "isInsideVehicle" -> riding;
                    case "isOnGround" -> grounded;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
