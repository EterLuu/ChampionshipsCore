package ink.ziip.championshipscore.api.game.riptiderush;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class RiptideHitwCatalogTest {
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

    @Test void v1UpgradeReplacesOnlyUntouchedSnapshotsAndDoesNotResurrectDeletedRows() throws Exception {
        var current=RiptideHitwCatalog.templates().getFirst();
        var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/riptiderush/hitw-walls.json"))).getAsJsonObject();
        String legacy=json.getAsJsonArray("pool").get(0).getAsJsonObject().get("previous-schematic").getAsString();
        var row=current.serialize();row.put("name","我的命名");row.put("enabled",false);row.put("weight",23);
        row.put("building",new RiptideBlueprint(legacy,0,15,5,List.of()).serialize());
        var edited=RiptideHitwCatalog.templates().get(1).serialize();edited.put("building",current.blueprint().serialize());
        var yaml=new YamlConfiguration();yaml.set("course.hitw-catalog-version",1);yaml.set("course.pool",List.of(row,edited));
        RiptideHitwCatalog.migrate(yaml);
        var rows=yaml.getMapList("course.pool");assertEquals(158,rows.size());
        assertEquals(current.blueprint(),RiptideLevelTemplate.parse(rows.getFirst()).blueprint());
        assertEquals("我的命名",rows.getFirst().get("name"));assertEquals(false,rows.getFirst().get("enabled"));assertEquals(23,rows.getFirst().get("weight"));
        assertEquals(edited,rows.get(1));assertEquals(4,yaml.getInt("course.hitw-catalog-version"));
        assertFalse(RiptideHitwCatalog.isOriginal(RiptideLevelTemplate.parse(edited)));
    }

    @Test void v2UpgradeRefreshesAreaRatingsEvenWhenTheBuildingDidNotChange() throws Exception {
        var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/riptiderush/hitw-walls.json"))).getAsJsonObject();
        var rows=new ArrayList<Map<String,Object>>();
        for (var element:json.getAsJsonArray("pool")) {
            var source=element.getAsJsonObject();
            if (!source.has("previous-v2-schematic")) continue;
            var current=RiptideHitwCatalog.templates().stream().filter(t -> t.id().equals(source.get("id").getAsString())).findFirst().orElseThrow();
            double area=source.get("passable-area").getAsDouble();
            assertEquals(area>=6?1:area>=3?2:3,current.difficulty());
            var row=current.serialize();row.put("difficulty",1);row.put("weight",17);row.put("enabled",false);
            row.put("building",new RiptideBlueprint(source.get("previous-v2-schematic").getAsString(),0,15,current.blueprint().height(),List.of()).serialize());
            rows.add(row);
        }
        var yaml=new YamlConfiguration();yaml.set("course.hitw-catalog-version",2);yaml.set("course.pool",rows);
        RiptideHitwCatalog.migrate(yaml);
        var upgraded=yaml.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList();
        assertEquals(Set.of(1,2,3),upgraded.stream().map(RiptideLevelTemplate::difficulty).collect(java.util.stream.Collectors.toSet()));
        assertEquals(354,upgraded.size());
        for(int i=0;i<rows.size();i++) {
            assertEquals(RiptideHitwCatalog.templates().get(i).blueprint(),upgraded.get(i).blueprint());
            assertEquals(RiptideHitwCatalog.templates().get(i).difficulty(),upgraded.get(i).difficulty());
            assertEquals(17,upgraded.get(i).weight());assertFalse(upgraded.get(i).enabled());
        }
    }

    @Test void migrationPreservesUserEntriesPublicationAndDeletedImportedWalls() throws Exception {
        var yaml=new YamlConfiguration();var custom=RiptideHitwCatalog.templates().getFirst().serialize();
        custom.put("name","保留我的编辑");custom.put("enabled",false);
        yaml.set("course.pool",List.of(custom));yaml.set("prepare.revision",7);yaml.set("prepare.published",true);
        yaml.set("prepare.dirty",false);yaml.set("custom.keep","yes");
        RiptideHitwCatalog.migrate(yaml);
        assertEquals(354,yaml.getMapList("course.pool").size());assertEquals(custom,yaml.getMapList("course.pool").getFirst());
        assertEquals(7,yaml.getInt("prepare.revision"));assertTrue(yaml.getBoolean("prepare.published"));
        assertFalse(yaml.getBoolean("prepare.dirty"));assertEquals("yes",yaml.getString("custom.keep"));
        var smaller=new ArrayList<>(yaml.getMapList("course.pool"));smaller.removeLast();yaml.set("course.pool",smaller);
        var once=yaml.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList();
        RiptideHitwCatalog.migrate(yaml);
        assertEquals(once,yaml.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList());
    }

    @Test void v3AddsOnlyEasyWallsOnceAndPreservesRatingsEditsAndPublication() throws Exception {
        var yaml=new YamlConfiguration();
        var old=RiptideHitwCatalog.templates().getFirst().serialize();
        old.put("difficulty",3);old.put("name","保留手动评级");old.put("enabled",false);old.put("weight",19);
        var easy=RiptideHitwCatalog.templates().stream().filter(t -> t.id().equals("hitw_beach_e1")).findFirst().orElseThrow().serialize();
        easy.put("name","已有同ID建筑");easy.put("building",old.get("building"));
        yaml.set("course.pool",List.of(old,easy));yaml.set("course.hitw-catalog-version",3);
        yaml.set("prepare.revision",9);yaml.set("prepare.published",true);yaml.set("prepare.dirty",false);
        RiptideHitwCatalog.migrate(yaml);
        var rows=yaml.getMapList("course.pool");
        assertEquals(157,rows.size());assertEquals(old,rows.getFirst());assertEquals(easy,rows.get(1));
        assertEquals(156,rows.stream().filter(r -> String.valueOf(r.get("id")).matches(".*_e[0-9]+" )).count());
        assertEquals(9,yaml.getInt("prepare.revision"));assertTrue(yaml.getBoolean("prepare.published"));assertFalse(yaml.getBoolean("prepare.dirty"));
        var smaller=new ArrayList<>(rows);smaller.removeLast();yaml.set("course.pool",smaller);
        RiptideHitwCatalog.migrate(yaml);assertEquals(smaller,yaml.getMapList("course.pool"));
    }

    @Test void actualUpgradeDoesNotMistakeBundledImportMarkerForOldMapState() throws Exception {
        var c=RiptideTestFixtures.config();
        c.setTemplates(RiptideRushConfig.defaultPool().stream().map(RiptideLevelTemplate::parse)
                .filter(t -> !RiptideHitwCatalog.isOriginal(t)).toList());
        var old=new YamlConfiguration();old.set("course.pool",c.getPool());old.set("dont-edit-this.version",5);
        var migrated=new YamlConfiguration();migrated.set("course.pool",c.getPool());
        migrated.set("course.hitw-catalog-version",1);
        c.customizeMigratedConfiguration(old,migrated);
        assertEquals(RiptideRushConfig.defaultPool().size(),migrated.getMapList("course.pool").size());
        var ids=migrated.getMapList("course.pool").stream().map(row -> row.get("id")).toList();
        assertTrue(ids.containsAll(RiptideHitwCatalog.templates().stream().map(RiptideLevelTemplate::id).toList()));
        assertEquals(4,migrated.getInt("course.hitw-catalog-version"));
    }

    @Test void fullDefaultPoolPlansReproduciblyAndRespectsDifficultyAndTransitions() throws Exception {
        var defaults=new YamlConfiguration();
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/riptiderush/area.yml")),StandardCharsets.UTF_8)) {defaults.load(reader);}
        assertEquals(RiptideTestFixtures.config().getLatestVersion(),defaults.getInt("dont-edit-this.version"));assertEquals(4,defaults.getInt("course.hitw-catalog-version"));
        var rows=defaults.getMapList("course.pool").stream().map(RiptideLevelTemplate::parse).toList();
        assertEquals(new HashSet<>(RiptideRushConfig.defaultPool()),new HashSet<>(rows.stream().map(RiptideLevelTemplate::serialize).toList()));
        var c=RiptideTestFixtures.config();c.setTemplates(rows);var seen=new HashSet<String>();
        for(long seed=0;seed<200;seed++) {
            var plan=RiptideCoursePlanner.plan(c,seed);
            if (seed == 0) assertEquals(plan,RiptideCoursePlanner.plan(c,seed));
            assertEquals(32,plan.levels().stream().map(RiptideCoursePlan.Level::number).distinct().count());assertEquals(RiptideCoursePlanner.estimateTicks(c,c.resolveGeometry(),plan.levels()),plan.estimatedTicks());
            for(var level:plan.levels()) {
                seen.add(level.template().id());
                level.sideWalls().forEach(wall -> seen.add(wall.template().id()));
                if(level.type()==RiptideLevelType.PASS && !level.isSideSweep())
                    assertTrue(RiptideDifficulty.allowsPass(level.template().difficulty(),level.step(),500));
            }
            for(int i=1;i<plan.levels().size();i++)assertTrue(RiptideCoursePlanner.safeTransition(c,c.resolveGeometry(),plan.levels().get(i-1),plan.levels().get(i)));
        }
        var missing = RiptideHitwCatalog.templates().stream().map(RiptideLevelTemplate::id).filter(id -> !seen.contains(id)).toList();
        // A finite uniform sample need not draw every ID, especially identical snapshot copies.
        // Promote missed entries and verify they remain selectable in a complete valid course.
        for (String id : missing) {
            c.setTemplates(rows.stream().map(t -> new RiptideLevelTemplate(t.id(), t.name(), t.type(),
                    t.variant(), t.enabled(), t.id().equals(id) ? 100 : 1, t.maxUses(), t.difficulty(), t.blueprint())).toList());
            for (long seed = 0; seed < 32 && !seen.contains(id); seed++) {
                var plan = RiptideCoursePlanner.plan(c, seed);
                for (var level : plan.levels()) {
                    if (!level.isSideSweep()) seen.add(level.template().id());
                    level.sideWalls().forEach(wall -> seen.add(wall.template().id()));
                }
            }
            assertTrue(seen.contains(id), "Catalog entry cannot be selected: " + id);
        }
    }
}
