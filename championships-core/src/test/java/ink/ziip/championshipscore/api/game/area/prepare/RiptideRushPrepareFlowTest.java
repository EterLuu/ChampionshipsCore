package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.riptiderush.*;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiptideRushPrepareFlowTest {
    private Object originalBlockType;

    @BeforeEach void installHeadlessStoneType() throws Exception {
        // Paper 26.2 delegates Material predicates to the live registry. Provide only the stone
        // used by this fixture and restore its supplier after every test.
        var block = (BlockType) Proxy.newProxyInstance(BlockType.class.getClassLoader(), new Class<?>[]{BlockType.class},
                (p, m, a) -> {
                    if (m.getName().equals("isAir")) return false;
                    throw new UnsupportedOperationException(m.getName());
                });
        var field = Material.class.getDeclaredField("blockType"); field.setAccessible(true);
        originalBlockType = field.get(Material.STONE);
        field.set(Material.STONE, (java.util.function.Supplier<BlockType>) () -> block);
    }

    @AfterEach void restoreStoneType() throws Exception {
        var field = Material.class.getDeclaredField("blockType"); field.setAccessible(true);
        field.set(Material.STONE, originalBlockType);
    }

    @Test void displayChecksDoNotSearchButExplicitValidationStillRejectsImpossibleCourses() throws Exception {
        var session = session();
        var config = (RiptideRushConfig) session.getTarget().config();
        // All inputs are valid, but a 500-block course cannot finish in one second.
        config.setTimer(1);
        for (int i = 0; i < 20; i++) assertTrue(session.getFlow().validateForDisplay(session).isEmpty());
        assertTrue(session.getFlow().validate(session).stream().anyMatch(s -> s.contains("无法生成安全赛道")));
    }

    @Test void displayChecksObserveEditsAndWorldConfirmationImmediately() throws Exception {
        var session = session();
        var config = (RiptideRushConfig) session.getTarget().config();
        var pool = config.resolvePool();
        assertTrue(session.getFlow().validateForDisplay(session).isEmpty());
        config.setTemplates(List.of());
        assertFalse(session.getFlow().validateForDisplay(session).isEmpty());
        config.setTemplates(pool);
        assertTrue(session.getFlow().validateForDisplay(session).isEmpty());
        session.setWorldConfirmed(false);
        assertFalse(session.getFlow().validateForDisplay(session).isEmpty());
        session.setWorldConfirmed(true);
        config.setFixedSeed("invalid");
        assertFalse(session.getFlow().validateForDisplay(session).isEmpty());
        config.setFixedSeed("");
        config.setFinalSpeed(Double.NaN);
        assertFalse(session.getFlow().validateForDisplay(session).isEmpty());
    }

    private static PrepareSession session() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var plugin = (ChampionshipsCore) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ChampionshipsCore.class);
        var config = new RiptideRushConfig(plugin, "test");
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(RiptideRushPrepareFlowTest.class.getResourceAsStream(
                "/riptiderush/area.yml"), StandardCharsets.UTF_8)) { yaml.load(reader); }
        config.loadFromConfiguration(yaml);
        config.setRaftMaterial("STONE"); config.setObstacleMaterial("STONE"); config.setTrailMaterial("STONE");
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getName" -> "test";
                    case "equals" -> p == a[0];
                    case "hashCode" -> 1;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        config.setStartPoint(new Location(world, .5, 80, -110.5));
        config.setFinishPoint(new Location(world, .5, 80, 389.5));
        config.setIntroductionSpawnPoint(config.getStartPoint());
        config.setPassCount(2); config.setMathCount(0); config.setStoppedCount(0); config.setRhythmCount(0);
        config.setTemplates(List.of(
                new RiptideLevelTemplate("gap", "gap", RiptideLevelType.PASS, "GAP", true, 10, 1, 2),
                new RiptideLevelTemplate("jump", "jump", RiptideLevelType.PASS, "JUMP", true, 10, 1, 2)));
        var target = (SetupTarget) Proxy.newProxyInstance(SetupTarget.class.getClassLoader(), new Class<?>[]{SetupTarget.class},
                (p, m, a) -> switch (m.getName()) {
                    case "config" -> config;
                    case "worldName", "name" -> "test";
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        var session = new PrepareSession(plugin, GameTypeEnum.RiptideRush, "test", target, new RiptideRushPrepareFlow());
        session.setWorldConfirmed(true);
        return session;
    }
}
