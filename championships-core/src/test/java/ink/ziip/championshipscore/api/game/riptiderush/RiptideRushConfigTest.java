package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiptideRushConfigTest {
    @Test void repeatedReadsReuseImmutableTemplatesAndCallerEditsCannotChangeThePool() throws Exception {
        var config = RiptideTestFixtures.config();
        var template = RiptideHitwCatalog.templates().getFirst();
        var row = new HashMap<>(template.serialize());
        var building = new HashMap<>(template.blueprint().serialize());
        row.put("building", building);
        var input = new ArrayList<>(List.of(row));
        config.setPool(new ArrayList<>(input));
        var resolved = config.resolvePool();
        row.put("enabled", false); building.put("schematic", "invalid"); input.clear();
        assertSame(resolved, config.resolvePool());
        assertEquals(List.of(template), resolved);
        assertThrows(UnsupportedOperationException.class, () -> resolved.clear());
        assertThrows(UnsupportedOperationException.class, () -> config.getPool().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.getPool().getFirst().put("enabled", false));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<?, ?>) config.getPool().getFirst().get("building")).clear());
    }

    @Test void editsReloadsAndReflectiveRollbackInvalidateParsedPool() throws Exception {
        var config = RiptideTestFixtures.config();
        var before = config.resolvePool();
        var savedRows = config.getPool();
        var changed = RiptideLevelTemplate.create("replacement", RiptideLevelType.MATH);
        config.setTemplates(List.of(changed));
        assertEquals(List.of(changed), config.resolvePool());
        assertNotSame(before, config.resolvePool());
        var field = RiptideRushConfig.class.getDeclaredField("pool"); field.setAccessible(true);
        field.set(config, savedRows);
        assertEquals(before, config.resolvePool());
        var yaml = RiptideTestFixtures.defaults();
        yaml.set("course.pool", List.of(changed.serialize()));
        config.loadFromConfiguration(yaml);
        assertEquals(List.of(changed), config.resolvePool());
        assertSame(config.resolvePool(), config.resolvePool());
    }

    @Test void invalidReplacementDoesNotPoisonTheLastValidPool() throws Exception {
        var config = RiptideTestFixtures.config();
        var before = config.resolvePool();
        assertThrows(IllegalArgumentException.class,
                () -> config.setTemplates(List.of(before.getFirst(), before.getFirst())));
        assertSame(before, config.resolvePool());
        assertThrows(IllegalArgumentException.class, () -> config.setPool(List.of(Map.of("id", "invalid"))));
        assertSame(before, config.resolvePool());
    }
}
