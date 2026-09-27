package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorModel;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RiptideBlueprintTest {
    @TempDir Path directory;
    // The snapshot is opaque to configuration editing; WorldEdit owns the Sponge binary format.
    private static RiptideBlueprint blueprint(String payload, List<Material> floor) {
        return new RiptideBlueprint(Base64.getEncoder().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                1, 9, 6, floor.stream().map(Enum::name).toList());
    }
    private static List<Material> floor() {
        var cells = new ArrayList<Material>();
        for (int i = 0; i < 63; i++) cells.add(i % 2 == 0 ? Material.IRON_ORE : Material.COAL_ORE);
        return cells;
    }

    @Test void copyingAndEditingPreservesIndependentBuildingPayloadsAndRoundTripStorage() throws Exception {
        var c = RiptideTestFixtures.config();
        var original = c.resolvePool().stream().filter(t -> t.type() == RiptideLevelType.PASS).findFirst().orElseThrow();
        var first = original.withBlueprint(blueprint("original block state and block entity bytes", List.of()));
        c.setTemplates(List.of(first));
        var copy = RiptideEditorModel.duplicate(c, first.type(), first.id());
        assertEquals(first.blueprint(), copy.blueprint()); assertNotEquals(first.id(), copy.id());
        var edited = copy.withBlueprint(blueprint("edited stairs/facing and sign NBT bytes", List.of()));
        c.setTemplates(List.of(first, edited));
        var yaml = new YamlConfiguration(); yaml.set("course.pool", c.getPool());
        Path file = directory.resolve("map.yml"); yaml.save(file.toFile());
        var restored = YamlConfiguration.loadConfiguration(file.toFile()).getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList();
        assertEquals(List.of(first, edited), restored);
        assertNotEquals(restored.get(0).blueprint(), restored.get(1).blueprint());
        var cells = new ArrayList<>(List.of("IRON_ORE"));
        var snapshot = new RiptideBlueprint("", 0, 9, 6, cells); cells.clear();
        assertEquals(List.of("IRON_ORE"), snapshot.floor());
    }

    @Test void checkedBuildingSavePersistsAndFailedSaveRollsBack() throws Exception {
        var c = RiptideTestFixtures.config(); var original = c.resolvePool().getFirst();
        var yaml = new YamlConfiguration(); yaml.set("course.pool", c.getPool()); yaml.set("custom.keep", "untouched");
        var configField = BaseConfigurationFile.class.getDeclaredField("configuration"); configField.setAccessible(true); configField.set(c, yaml);
        var pathField = BaseConfigurationFile.class.getDeclaredField("configurationPath"); pathField.setAccessible(true);
        Path file = directory.resolve("map.yml"); pathField.set(c,file);
        var edited = original.withBlueprint(blueprint("saved", List.of())); c.saveBuilding(edited);
        var onDisk = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(edited, RiptideLevelTemplate.parse(onDisk.getMapList("course.pool").getFirst()));
        assertEquals("untouched", onDisk.getString("custom.keep")); assertTrue(onDisk.getBoolean("prepare.dirty"));
        pathField.set(c,directory); // Writing a YAML file to a directory must fail without mutating the saved model.
        assertThrows(java.io.IOException.class, () -> c.saveBuilding(original.withBlueprint(blueprint("failed", List.of()))));
        assertEquals(edited, c.resolvePool().getFirst());
        assertEquals(edited, RiptideLevelTemplate.parse(yaml.getMapList("course.pool").getFirst()));
    }

    @Test void savedFloorIsUsedWheneverTheRoundSelectsPresetModeAndTargetsAlwaysExistAndChange() {
        var cells = floor(); var snapshot = blueprint("floor", cells);
        var run = new RiptideColorFloorRun(7,9,RiptideDifficulty.floorRoundTicks(0,500),new Random(91),RiptideColorFloorRun.Theme.ORE,snapshot.floorMaterials());
        Material previous = null; int rounds=0; boolean sawPreset=false; boolean sawRandom=false;
        do {
            if (run.presetPattern()) { assertEquals(cells, run.floor()); sawPreset = true; }
            else sawRandom = true;
            assertTrue(run.floor().contains(run.target())); assertNotEquals(previous, run.target());
            previous=run.target(); rounds++; while(!run.tick()) { }
        } while(run.advance());
        assertEquals(7, rounds); assertTrue(sawPreset); assertTrue(sawRandom);
    }

    @Test void floorRequiresCompleteDistributedMultipleMaterials() {
        assertDoesNotThrow(() -> RiptideBlueprint.validateFloor(floor(),7,9));
        var one = new ArrayList<>(Collections.nCopies(63,Material.IRON_ORE));
        assertThrows(IllegalArgumentException.class, () -> RiptideBlueprint.validateFloor(one,7,9));
        one.set(0,Material.COAL_ORE);
        assertThrows(IllegalArgumentException.class, () -> RiptideBlueprint.validateFloor(one,7,9));
        var hole=floor();hole.set(0,Material.AIR);
        assertThrows(IllegalArgumentException.class, () -> RiptideBlueprint.validateFloor(hole,7,9));
    }


    @Test void mathAndFloorPreserveMechanicClearance() {
        var gate=new boolean[7][7][6];
        for(int y=0;y<3;y++)gate[3][3][y]=true;
        assertDoesNotThrow(() -> validateClearance(gate,RiptideLevelType.MATH));
        gate[3][1][2]=true;
        assertThrows(IllegalArgumentException.class, () -> validateClearance(gate,RiptideLevelType.MATH));
        var deck=new boolean[7][7][6];
        assertDoesNotThrow(() -> validateClearance(deck,RiptideLevelType.COLOR_FLOOR));
        deck[0][0][0]=true;
        assertThrows(IllegalArgumentException.class, () -> validateClearance(deck,RiptideLevelType.COLOR_FLOOR));
    }

    @Test void clearanceOnlyChecksTheMathPlaneAndFloorHeadroom() {
        var shape = new boolean[15][7][12];
        for (int x=0;x<7;x++) for (int y=0;y<12;y++) shape[13][x][y]=true;
        assertDoesNotThrow(() -> validateClearance(shape,RiptideLevelType.PASS));
        shape[13][1][0]=false; shape[13][1][1]=false;
        assertDoesNotThrow(() -> validateClearance(shape,RiptideLevelType.PASS));
        var gate = new boolean[15][7][12];
        for (int y=0;y<3;y++) gate[7][3][y]=true;
        assertDoesNotThrow(() -> validateClearance(gate,RiptideLevelType.MATH));
        // Sealing an approach slice is allowed; only the math judging plane needs clearance.
        for (int x=0;x<7;x++) for (int y=0;y<12;y++) gate[2][x][y]=true;
        assertDoesNotThrow(() -> validateClearance(gate,RiptideLevelType.MATH));
        gate[7][1][2]=true;
        assertThrows(IllegalArgumentException.class,() -> validateClearance(gate,RiptideLevelType.MATH));
        var floor = new boolean[15][7][12]; floor[14][2][2]=true;
        assertThrows(IllegalArgumentException.class,() -> validateClearance(floor,RiptideLevelType.COLOR_FLOOR));
    }

    @Test void expandedAndLegacyBuildingsRoundTripAndRespectActualSpacing() throws Exception {
        var c = RiptideTestFixtures.config(); c.setMathCount(0); c.setStoppedCount(0); c.setPassCount(6);
        var wide = new RiptideBlueprint("",7,15,12,List.of());
        assertEquals(wide,RiptideBlueprint.parse(wide.serialize()));
        var first = new RiptideLevelTemplate("wide","长建筑",RiptideLevelType.PASS,"JUMP",true,10,64,1,wide);
        var legacy = new RiptideLevelTemplate("old","旧建筑",RiptideLevelType.PASS,"JUMP",true,10,64,1,blueprint("old",List.of()));
        assertEquals(7, RiptideCoursePlanner.templatePreview(c,first,42).levels().getFirst().extent());
        assertEquals(legacy.blueprint(), RiptideCoursePlanner.templatePreview(c,legacy,42).levels().getFirst().template().blueprint());
        var a = new RiptideCoursePlan.Level(1,100,first,"JUMP",1,false,1);
        var math = RiptideLevelTemplate.create("math", RiptideLevelType.MATH);
        var nearby = new RiptideCoursePlan.Level(2,113,math,"ADD",1,false,1);
        assertFalse(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),a,nearby));
        var spaced = new RiptideCoursePlan.Level(2,150,math,"ADD",1,false,1);
        assertTrue(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),a,spaced));
        c.setObstacleMargin(2);
        c.setTemplates(List.of(first,legacy));
        assertThrows(IllegalArgumentException.class,() -> RiptideCoursePlanner.plan(c,42));
    }

    @Test void previewsUseSavedExtentAndUnknownRoutesCannotBecomeConsecutiveWalls() throws Exception {
        var c=RiptideTestFixtures.config(); c.setMathCount(0);c.setStoppedCount(0);c.setPassCount(6);
        var first = new RiptideLevelTemplate("jump_a","矮栏",RiptideLevelType.PASS,"JUMP",true,10,64,1,
                blueprint("one",List.of()));
        var second = new RiptideLevelTemplate("jump_b","阶梯",RiptideLevelType.PASS,"JUMP",true,10,64,1,
                blueprint("two",List.of()));
        var preview=RiptideCoursePlanner.templatePreview(c,first,42);
        assertEquals(1,preview.levels().getFirst().extent());
        assertEquals(1,RiptideCoursePlanner.templatePreview(c,second,42).levels().getFirst().extent());
        assertNotEquals(first.designKey("CUSTOM"),second.designKey("CUSTOM"));
        var a=new RiptideCoursePlan.Level(1,100,first,"CUSTOM",0,false,1);
        var b=new RiptideCoursePlan.Level(2,200,second,"CUSTOM",0,false,2);
        assertTrue(RiptideWallGroups.uniqueWalls(List.of(a,b)));
        assertFalse(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),a,b));
        assertEquals(preview,RiptideCoursePlanner.templatePreview(c,first,42));
    }

    /** Keep the exact plane used by per-player math judging open on both sides. */
    private static void validateClearance(boolean[][][] solid, RiptideLevelType type) {
        var halves = new boolean[solid.length][solid[0].length][solid[0][0].length * 2];
        for (int f = 0; f < solid.length; f++) for (int x = 0; x < solid[f].length; x++)
            for (int y = 0; y < solid[f][x].length; y++)
                halves[f][x][2 * y] = halves[f][x][2 * y + 1] = solid[f][x][y];
        RiptideBlueprint.validateMechanicClearance(halves, type);
    }





    @Test void oldAuthoredWeaveBecomesIndependentAndCanPreviewAtHighSpeed() throws Exception {
        var old = new RiptideLevelTemplate("weave","我的建筑",RiptideLevelType.PASS,"WEAVE",true,10,64,1).serialize();
        old.put("building", new RiptideBlueprint("",1,15,12,List.of()).serialize());
        var restored = RiptideLevelTemplate.parse(old);
        assertEquals("CUSTOM", restored.variant());
        assertEquals("我的建筑", restored.name());
        var c=RiptideTestFixtures.config();
        assertDoesNotThrow(() -> RiptideCoursePlanner.templatePreview(c,restored,42));
        c.setPassCount(6);c.setMathCount(0);c.setStoppedCount(0);
        var second = new RiptideLevelTemplate("other","第二个建筑",RiptideLevelType.PASS,"CUSTOM",true,10,64,1,restored.blueprint());
        c.setTemplates(List.of(restored,second));
        assertEquals(restored.usageKey(),second.usageKey());
        assertThrows(IllegalArgumentException.class,() -> RiptideCoursePlanner.plan(c,42));
    }

    @Test void passDoesNotRequireCollisionSamplingAndMechanicClearanceStillUsesFractionalShapes() {
        assertDoesNotThrow(() -> RiptideBlueprint.validateMechanicClearance(null,RiptideLevelType.PASS));
        var solid=new boolean[15][7][24];
        for(var slice:solid) for(var column:slice) Arrays.fill(column,true);
        assertDoesNotThrow(() -> RiptideBlueprint.validateMechanicClearance(solid,RiptideLevelType.PASS));
        var deck=new boolean[15][7][24];
        RiptideBlueprint.markCollision(deck[7][1],2,
                List.of(new org.bukkit.util.BoundingBox(0,.8125,0,1,1,1)));
        assertThrows(IllegalArgumentException.class,()->RiptideBlueprint.validateMechanicClearance(deck,RiptideLevelType.COLOR_FLOOR));
        assertThrows(IllegalArgumentException.class,()->RiptideBlueprint.validateMechanicClearance(deck,RiptideLevelType.MATH));
    }

}
