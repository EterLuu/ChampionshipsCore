package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RiptideAutomaticLayoutTest {
    @Test void rawYamlLoadedBeforeDraftWorldBindsWhenWorldBecomesAvailable() throws Exception {
        var c = RiptideTestFixtures.config();
        var loadedWorld = new java.util.concurrent.atomic.AtomicReference<org.bukkit.World>();
        var server = java.lang.reflect.Proxy.newProxyInstance(org.bukkit.Server.class.getClassLoader(),
                new Class<?>[]{org.bukkit.Server.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getWorld")) return loadedWorld.get();
                    throw new UnsupportedOperationException(method.getName());
                });
        var pluginField = ink.ziip.championshipscore.configuration.config.BaseConfigurationFile.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        var serverField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(pluginField.get(c), server);
        var world = c.getStartPoint().getWorld();
        var yaml = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/riptiderush/area.yml"),
                java.nio.charset.StandardCharsets.UTF_8)) { yaml.load(reader); }
        yaml.set("world-name", world.getName());
        yaml.set("start-point.y", 80D);
        yaml.set("finish-point.y", 79.628D);
        c.loadFromConfiguration(yaml);
        assertEquals(80D, c.getStartPoint().getY());
        assertEquals(80D, c.getFinishPoint().getY());
        assertNull(c.getStartPoint().getWorld());
        assertNull(c.getFinishPoint().getWorld());
        assertTrue(assertThrows(IllegalArgumentException.class, c::resolveGeometry).getMessage().contains("地图世界尚未加载"));

        loadedWorld.set(world);
        var geometry = c.resolveGeometry();
        assertSame(world, c.getStartPoint().getWorld());
        assertSame(world, c.getFinishPoint().getWorld());
        assertEquals(500, geometry.totalSteps());
        assertEquals(80D, geometry.centerAt(0).getY());
        assertEquals(80D, geometry.centerAt(500).getY());

        loadedWorld.set(null);
        assertThrows(IllegalArgumentException.class, c::resolveGeometry);
    }

    @Test void migrationRepairsHeightAndFixesRouteAndTimeWithoutChangingQuotasOrMetadata() {
        var yaml=new YamlConfiguration();yaml.set("world-name","raft");
        yaml.set("start-point.y",79);yaml.set("finish-point.y",81);yaml.set("timer",99);
        yaml.set("prepare.revision",3);yaml.set("custom.keep","hello");yaml.set("course.counts.pass",20);
        yaml.set("course.pool",List.of(RiptideLevelTemplate.create("pass",RiptideLevelType.PASS).serialize(),
                RiptideLevelTemplate.create("math",RiptideLevelType.MATH).serialize(),
                RiptideLevelTemplate.create("floor",RiptideLevelType.COLOR_FLOOR).serialize()));
        RiptideRushConfig.migrateBuildings(yaml);
        assertEquals(80,yaml.getDouble("start-point.y"));assertEquals(80,yaml.getDouble("finish-point.y"));
        assertEquals(500,yaml.getDouble("finish-point.z")-yaml.getDouble("start-point.z"));assertEquals(300,yaml.getInt("timer"));
        assertEquals("raft",yaml.getString("start-point.world"));assertEquals("raft",yaml.getString("finish-point.world"));
        assertEquals(20,yaml.getInt("course.counts.pass"));assertEquals(3,yaml.getInt("prepare.revision"));assertEquals("hello",yaml.getString("custom.keep"));
        assertEquals(19,yaml.getMapList("course.pool").size());
        assertTrue(yaml.getMapList("course.pool").stream().noneMatch(row -> row.get("variant").equals("AUTO")));
        var once=yaml.saveToString();RiptideRushConfig.migrateBuildings(yaml);assertEquals(once,yaml.saveToString());
    }
    @Test void workshopMigrationExpandsStockBoundsAndPreservesBuildingsAndPublication() {
        var yaml = new YamlConfiguration();
        yaml.set("generation.obstacle-margin",2); yaml.set("generation.clear-height",6);
        yaml.set("course.pool",List.of(new RiptideLevelTemplate("old","旧建筑",RiptideLevelType.PASS,"JUMP",true,10,64,1,
                new RiptideBlueprint("",3,9,6,List.of())).serialize()));
        yaml.set("prepare.published",true); yaml.set("prepare.dirty",false); yaml.set("prepare.revision",4);
        var pool=yaml.getMapList("course.pool");
        RiptideRushConfig.migrateWorkshop(yaml);
        assertEquals(5,yaml.getInt("generation.obstacle-margin")); assertEquals(12,yaml.getInt("generation.clear-height"));
        assertEquals(pool,yaml.getMapList("course.pool")); assertTrue(yaml.getBoolean("prepare.published"));
        assertFalse(yaml.getBoolean("prepare.dirty")); assertEquals(4,yaml.getInt("prepare.revision"));
        var once=yaml.saveToString(); RiptideRushConfig.migrateWorkshop(yaml); assertEquals(once,yaml.saveToString());
        yaml.set("generation.obstacle-margin",7); yaml.set("generation.clear-height",15);
        RiptideRushConfig.migrateWorkshop(yaml);
        assertEquals(7,yaml.getInt("generation.obstacle-margin")); assertEquals(15,yaml.getInt("generation.clear-height"));
    }

    @Test void inMemoryGeometryAlsoRepairsMismatchedY() throws Exception {
        var c=RiptideTestFixtures.config();c.getFinishPoint().setY(81);
        assertEquals(500,c.resolveGeometry().totalSteps());assertEquals(c.getStartPoint().getY(),c.getFinishPoint().getY());
    }
    @Test void clearingWorldBindingDoesNotReloadAndEraseNewMapNameOrEdits() throws Exception {
        var c=RiptideTestFixtures.config();
        var field=ink.ziip.championshipscore.configuration.config.BaseConfigurationFile.class.getDeclaredField("configuration");
        field.setAccessible(true);field.set(c,new YamlConfiguration());
        c.setAreaName("my-new-map");c.setMathCount(4);var pool=c.resolvePool();
        c.bindConfiguredWorld("");
        assertEquals("my-new-map",c.getAreaName());assertEquals(4,c.getMathCount());assertEquals(pool,c.resolvePool());
        assertNull(c.getStartPoint());assertNull(c.getFinishPoint());assertEquals(300,c.getTimer());
    }
    @Test void explicitVariantsAndCustomSettingsSurviveMigration() {
        var yaml=new YamlConfiguration();var custom=new RiptideLevelTemplate("jump","我的跳跃",RiptideLevelType.PASS,"JUMP",false,41,2,3);
        yaml.set("course.pool",List.of(custom.serialize()));RiptideRushConfig.migrateBuildings(yaml);
        assertEquals(custom,RiptideLevelTemplate.parse(yaml.getMapList("course.pool").getFirst()));
    }
}
