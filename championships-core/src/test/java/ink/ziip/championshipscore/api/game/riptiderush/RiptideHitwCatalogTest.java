package ink.ziip.championshipscore.api.game.riptiderush;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiptideHitwCatalogTest {
    @Test void passageLookupsReuseImmutableForwardAndMirroredMetadata() {
        for (var template : RiptideHitwCatalog.templates()) {
            var forward = RiptideHitwCatalog.passages(template, false);
            var mirrored = RiptideHitwCatalog.passages(template, true);
            assertSame(forward, RiptideHitwCatalog.passages(template, false));
            assertSame(mirrored, RiptideHitwCatalog.passages(template, true));
            assertEquals(forward.stream().map(p -> new RiptideHitwCatalog.Passage(
                    -p.lateral(), p.sill(), p.crouch())).toList(), mirrored);
            assertThrows(UnsupportedOperationException.class, forward::clear);
            assertThrows(UnsupportedOperationException.class, mirrored::clear);
        }
    }

    @Test void allThirteenTabsAndAllDifficultyGroupsAreBundledAndEditable() throws Exception {
        var templates = RiptideHitwCatalog.templates();
        assertEquals(354, templates.size());
        assertEquals(354, templates.stream().map(RiptideLevelTemplate::id).distinct().count());
        for (String map : List.of("beach", "classic", "highrise", "medieval", "fishbowl", "ice_palace",
                "labwallatory", "dojo", "rink", "beach_halloween", "medieval_halloween", "beach_winter", "iced_palace")) {
            int normal = map.equals("labwallatory") ? 14 : map.equals("dojo") ? 9 : 7;
            int hard = map.equals("labwallatory") ? 12 : map.equals("dojo") ? 9 : 7;
            int easy = switch (map) {
                case "beach", "fishbowl", "beach_halloween", "beach_winter" -> 12;
                case "highrise" -> 11; case "labwallatory" -> 28; case "dojo" -> 21; default -> 8;
            };
            for (String group : List.of("e", "d", "x")) for (int n=1; n <= (group.equals("e") ? easy : group.equals("d") ? normal : hard); n++) {
                String id = "hitw_" + map + "_" + group + n;
                var t = templates.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
                assertEquals("CUSTOM", t.variant()); assertTrue(t.enabled());
                assertTrue(t.difficulty() >= 1 && t.difficulty() <= 3);
                assertEquals(t, RiptideLevelTemplate.parse(t.serialize()));
            }
        }
        var c = RiptideTestFixtures.config(); c.setTemplates(templates);
        var copy = RiptideEditorModel.duplicate(c,RiptideLevelType.PASS,templates.getLast().id());
        assertEquals(templates.getLast().blueprint(),copy.blueprint());
        assertNotEquals(templates.getLast().id(),copy.id());
        assertEquals(9,RiptideEditorModel.clampPage(99,templates.size(),36));
    }

    @Test void realSpongePayloadsKeepWoodDetailsAndOrdinaryMovementPassages() throws Exception {
        var seen=new HashSet<String>();
        for (var t : RiptideHitwCatalog.templates()) {
            var b=t.blueprint();
            try (var in = new NBTInputStream(new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(b.schematic()))))) {
                var root=(CompoundTag)in.readNamedTag().getTag();
                var schem=(CompoundTag)root.getValue().get("Schematic");
                assertEquals(3,schem.getInt("Version"));
                assertEquals(15,schem.getShort("Width")); assertEquals(15,schem.getShort("Length"));
                assertEquals(b.height(),schem.getShort("Height")); assertArrayEquals(new int[]{-7,1,-7},schem.getIntArray("Offset"));
                assertEquals(0,b.extent());
                var blocks=(CompoundTag)schem.getValue().get("Blocks");
                var palette=(CompoundTag)blocks.getValue().get("Palette");
                var names=new HashMap<Integer,String>();
                for(var name:palette.getValue().keySet()) {names.put(palette.getInt(name),name);seen.add(name);}
                byte[] data=blocks.getByteArray("Data"); assertEquals(225*b.height(),data.length);
                if(t.id().equals("hitw_beach_x2")) {
                    int disconnected=0;
                    for(int y=0;y<b.height();y++)for(int x=0;x<14;x++) {
                        String left=names.get(Byte.toUnsignedInt(data[x+105+225*y]));
                        String right=names.get(Byte.toUnsignedInt(data[x+106+225*y]));
                        if(left.contains("spruce_fence[east=false") && left.contains("west=true")
                                && right.contains("spruce_fence[east=true") && right.contains("west=false"))disconnected++;
                    }
                    assertEquals(2,disconnected,"Adjacent fences deliberately leave a .75-block passage, like bench13");
                }
                var collision=new ArrayList<org.bukkit.util.BoundingBox>();
                int min=15,max=-1;
                for(int y=0;y<b.height();y++)for(int z=0;z<15;z++)for(int x=0;x<15;x++) {
                    String state=names.get(Byte.toUnsignedInt(data[x+15*z+225*y]));assertNotNull(state);
                    if(state.equals("minecraft:air"))continue;
                    assertEquals(7,z,t.id());min=Math.min(min,x);max=Math.max(max,x);
                    assertTrue(state.contains("spruce"),state);
                    addCollision(collision,state,x-7.5,y);
                }
                assertTrue(max-min+1<=12,t.id());assertTrue(max>=min,t.id());
                var passages=RiptideHitwCatalog.passages(t,false);assertFalse(passages.isEmpty(),t.id());
                for(var passage:passages) {
                    assertTrue(Math.abs(passage.lateral())<=3);assertTrue(passage.sill()<=1);
                    var body=new org.bukkit.util.BoundingBox(passage.lateral()-.3,passage.sill(),-.3,
                            passage.lateral()+.3,passage.sill()+(passage.crouch()?1.5:1.8),.3);
                    assertTrue(collision.stream().noneMatch(body::overlaps),t.id()+" "+passage);
                }
            }
        }
        for(String detail:List.of("axis=x","axis=y","spruce_fence","spruce_trapdoor","half=top","half=bottom","type=top","type=bottom"))
            assertTrue(seen.stream().anyMatch(s->s.contains(detail)),detail);
    }

    private static void addCollision(List<org.bukkit.util.BoundingBox> out,String state,double x,int y) {
        if(state.contains("trapdoor") && state.contains("open=false")) {
            boolean top=state.contains("half=top");
            out.add(new org.bukkit.util.BoundingBox(x,y+(top?13/16D:0),-.5,x+1,y+(top?1:3/16D),.5));
        } else if(state.contains("trapdoor")) {
            boolean west=state.contains("facing=east");
            out.add(new org.bukkit.util.BoundingBox(x+(west?0:13/16D),y,-.5,x+(west?3/16D:1),y+1,.5));
        } else if(state.contains("fence")) {
            out.add(new org.bukkit.util.BoundingBox(x+.375,y,-.5,x+.625,y+1.5,.5));
            if(state.contains("west=true"))out.add(new org.bukkit.util.BoundingBox(x,y,-.5,x+.5,y+1.5,.5));
            if(state.contains("east=true"))out.add(new org.bukkit.util.BoundingBox(x+.5,y,-.5,x+1,y+1.5,.5));
        } else if(state.contains("stairs")) {
            boolean top=state.contains("half=top"),east=state.contains("facing=east");
            out.add(new org.bukkit.util.BoundingBox(x,y+(top?.5:0),-.5,x+1,y+(top?1:.5),.5));
            out.add(new org.bukkit.util.BoundingBox(x+(east?.5:0),y+(top?0:.5),-.5,x+(east?1:.5),y+(top?.5:1),.5));
        } else {
            boolean slab=state.contains("slab"),top=state.contains("type=top");
            out.add(new org.bukkit.util.BoundingBox(x,y+(slab&&top?.5:0),-.5,x+1,y+(slab&&!top?.5:1),.5));
        }
    }






    @Test void fullDefaultPoolPlansReproduciblyAndRespectsDifficultyAndTransitions() throws Exception {
        var defaults=new YamlConfiguration();
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/riptiderush/area.yml")),StandardCharsets.UTF_8)) {defaults.load(reader);}
        assertEquals(RiptideTestFixtures.config().getLatestVersion(),defaults.getInt("dont-edit-this.version"));assertEquals(4,defaults.getInt("course.hitw-catalog-version"));
        var rows=defaults.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList();
        assertEquals(new HashSet<>(RiptideRushConfig.defaultPool()),new HashSet<>(rows.stream().map(RiptideLevelTemplate::serialize).toList()));
        var c=RiptideTestFixtures.config();c.setTemplates(rows);
        for(long seed=0;seed<8;seed++) {
            var plan=RiptideCoursePlanner.plan(c,seed);
            if (seed == 0) assertEquals(plan,RiptideCoursePlanner.plan(c,seed));
            assertEquals(32,plan.levels().stream().map(RiptideCoursePlan.Level::number).distinct().count());assertEquals(RiptideCoursePlanner.estimateTicks(c,c.resolveGeometry(),plan.levels()),plan.estimatedTicks());
            for(var level:plan.levels()) {
                if(level.type()==RiptideLevelType.PASS && !level.isSideSweep())
                    assertTrue(RiptideDifficulty.allowsPass(level.template().difficulty(),level.step(),500));
            }
            for(int i=1;i<plan.levels().size();i++)assertTrue(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),plan.levels().get(i-1),plan.levels().get(i)));
        }
    }

    @Test void copiedSnapshotsShareOneUseAndLegacyWallCapsCannotEnableRepeats() {
        var original = RiptideHitwCatalog.templates().getFirst();
        var copy = new RiptideLevelTemplate("copy", "copy", original.type(), original.variant(), true,
                10, 64, original.difficulty(), original.blueprint());
        assertEquals(1, copy.maxUses());
        assertFalse(copy.serialize().containsKey("max-uses"));
        assertEquals(original.designKey("CUSTOM"), copy.designKey("CUSTOM"));
        assertEquals(RiptideHitwCatalog.passages(original, true), RiptideHitwCatalog.passages(copy, true));
        var first = new RiptideCoursePlan.Level(1, 100, original, "CUSTOM", 0, false, 1);
        var second = new RiptideCoursePlan.Level(2, 200, copy, "CUSTOM", 0, true, 2);
        assertFalse(RiptideWallGroups.uniqueWalls(List.of(first, second)));
        var side = new RiptideCoursePlan.Level(2, 200, copy, "CUSTOM", 0, false, 2,
                0, 0, "SIDE", 1, List.of(new RiptideCoursePlan.SideWall(copy, "CUSTOM", 0, 1, 7)));
        assertFalse(RiptideWallGroups.uniqueWalls(List.of(first, side)));
        assertEquals(64, RiptideLevelTemplate.create("math", RiptideLevelType.MATH).maxUses());
    }

    @Test void commonLateralPassageIsRejectedEvenWithDifferentBuildingsAndMoreSpacing() throws Exception {
        var c = RiptideTestFixtures.config();
        var g = c.resolveGeometry();
        var a = RiptideHitwCatalog.templates().getFirst();
        var first = new RiptideCoursePlan.Level(1, 210, a, "CUSTOM", 0, false, 1);
        var b = RiptideHitwCatalog.templates().stream().filter(t -> !t.designKey("CUSTOM").equals(a.designKey("CUSTOM")))
                .filter(t -> RiptideHitwCatalog.passages(t, false).stream().anyMatch(p ->
                        RiptideHitwCatalog.passages(a, false).stream().anyMatch(q -> Math.abs(p.lateral() - q.lateral()) < .75)))
                .findFirst().orElseThrow();
        var second = new RiptideCoursePlan.Level(2, 300, b, "CUSTOM", 0, false, 2);
        assertFalse(RiptideCoursePlanner.safeTransition(c, g, first, second));
    }

    @Test void sideWallsNeverRelaxUniquenessWhenPoolIsTooSmall() throws Exception {
        var c = RiptideTestFixtures.config();
        var wall = RiptideHitwCatalog.templates().stream().filter(t -> t.difficulty() == 2).findFirst().orElseThrow();
        c.setTemplates(List.of(wall));
        assertTrue(RiptideWallGroups.selectSideWalls(c, c.resolveGeometry(), 350,
                new java.util.HashMap<>(Map.of()), new java.util.Random(1)).isEmpty());
    }
}
