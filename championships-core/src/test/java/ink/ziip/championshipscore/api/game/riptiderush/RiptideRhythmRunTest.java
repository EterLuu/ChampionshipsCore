package ink.ziip.championshipscore.api.game.riptiderush;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiptideRhythmRunTest {
    @Test void fixedGateAnimatesWhileRaftAdvancesAndRestoresOnExitOrCancel() {
        for (String variant : RiptideLevelTemplate.variants(RiptideLevelType.RHYTHM).stream()
                .filter(v -> !v.equals("AUTO")).toList()) {
            var f = new Fixture(variant);
            f.run.tick(10,5.2,List.of(f.player)); assertTrue(f.blocks.isEmpty());
            f.run.tick(15,5.2,List.of(f.player)); assertEquals(0,f.snapshots);
            f.run.tick(16,5.2,List.of(f.player)); assertEquals(28,f.snapshots);
            var originalFrame = Map.copyOf(f.blocks);
            boolean changed=false;
            for(int tick=0;tick<42;tick++) {
                f.run.tick(15+tick/5,5.2,List.of(f.player));
                changed |= !originalFrame.equals(f.blocks);
            }
            assertTrue(changed);
            assertTrue(f.restored.isEmpty());
            f.run.tick(36,5.2,List.of(f.player));
            assertEquals(28,f.restored.size()); assertTrue(f.blocks.values().stream().allMatch(m->m==Material.AIR));
            f.run.tick(37,5.2,List.of(f.player)); assertEquals(28,f.snapshots);
            f.run.close();
            var cancelled = new Fixture(variant);
            var decoration = List.of(cancelled.g.blockX(30,0),cancelled.g.floorY()+4,cancelled.g.blockZ(30,0));
            cancelled.blocks.put(decoration,Material.OAK_PLANKS);
            cancelled.run.tick(20,5.2,List.of(cancelled.player)); cancelled.run.close();
            assertEquals(28,cancelled.restored.size());
            assertEquals(Material.OAK_PLANKS,cancelled.blocks.get(decoration));
        }
    }

    @Test void raisedWindowAllowsJumpingPlayersAndPushesStandingPlayersOnEveryAxis() {
        for (int[] axis : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            var f = new Fixture("VERTICAL_WINDOW", axis);
            for (int tick = 0; tick < 32; tick++) f.run.tick(30, 5.2, List.of(f.player));
            f.position = f.g.centerAt(30).add(0, 1, 0);
            f.run.tick(30, 5.2, List.of(f.player));
            assertEquals(0, f.teleports, "A jumping body fits the raised two-block aperture");
            assertEquals(2, f.sounds, "Height change sounds even though the open columns did not change");
            for (int y = 1; y <= 4; y++) {
                Material expected = y == 1 ? Material.CYAN_CONCRETE : y == 4 ? Material.LIME_CONCRETE : Material.AIR;
                assertEquals(expected, f.blocks.getOrDefault(List.of(f.g.blockX(30,0),f.g.floorY()+y,f.g.blockZ(30,0)), Material.AIR));
            }
            f.position = f.g.centerAt(30);
            f.run.tick(30, 5.2, List.of(f.player));
            assertEquals(1, f.teleports);
            assertEquals(-.81, f.g.forwardOffset(f.position,30), 1e-8);
            f.run.close();
            assertEquals(28, f.restored.size());
        }
    }

    @Test void fixedWindowHasSolidFrameAndClosesAcrossAnOccupiedOpening() {
        var f = new Fixture("WINDOW_SHUTTER");
        f.position = f.g.centerAt(30);
        f.run.tick(30, 5.2, List.of(f.player));
        assertEquals(0, f.teleports);
        assertEquals(Material.CYAN_CONCRETE, f.blocks.get(List.of(f.g.blockX(30,0),f.g.floorY()+3,f.g.blockZ(30,0))));
        assertEquals(Material.CYAN_CONCRETE, f.blocks.get(List.of(f.g.blockX(30,2),f.g.floorY()+1,f.g.blockZ(30,2))));
        for (int tick = 1; tick <= 42; tick++) f.run.tick(30, 5.2, List.of(f.player));
        assertEquals(1, f.teleports);
        assertEquals(0, f.blocks.values().stream().filter(m -> m == Material.AIR).count());
        f.run.close();
        assertTrue(f.blocks.values().stream().allMatch(m -> m == Material.AIR));
    }

    @Test void occupiedCellClosesAndPushesPlayerOutsideTheWall() {
        var f = new Fixture("SHUTTER");
        f.position = f.g.centerAt(30);
        for(int tick=0;tick<=45;tick++) f.run.tick(30,5.2,List.of(f.player));
        var occupied = List.of(f.g.blockX(30,0),f.g.floorY()+1,f.g.blockZ(30,0));
        assertEquals(Material.IRON_BLOCK,f.blocks.get(occupied));
        assertEquals(-.81, f.g.forwardOffset(f.position,30), 1e-8);
        assertEquals(Material.IRON_BLOCK,f.blocks.get(List.of(f.g.blockX(30,2),f.g.floorY()+1,f.g.blockZ(30,2))));
        f.position = f.g.centerAt(32);
        f.run.tick(30,5.2,List.of(f.player));
        assertEquals(Material.IRON_BLOCK,f.blocks.get(occupied));
        f.run.close();
    }

    @Test void closurePushesToTheNearestFaceOnEveryAxisWithoutMovingClearPlayers() {
        for (int[] axis : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            for (double offset : new double[]{-.4, 0, .4, 1.2}) {
                var f = new Fixture("SHUTTER", axis);
                f.position = f.g.centerAt(30).add(axis[0] * offset, 0, axis[1] * offset);
                for (int tick=0;tick<=45;tick++) f.run.tick(30,5.2,List.of(f.player));
                assertEquals(Math.abs(offset) > .8 ? offset : offset > 0 ? .81 : -.81,
                        f.g.forwardOffset(f.position,30), 1e-8);
                assertEquals(0, f.g.lateralOffset(f.position,30), 1e-8);
                assertEquals(80, f.position.getY(), 1e-8);
                assertEquals(Math.abs(offset) > .8 ? 0 : 1, f.teleports);
                f.run.close();
            }
        }
    }

    private static final class Fixture {
        final Map<List<Integer>,Material> blocks = new HashMap<>();
        final Set<List<Integer>> restored = new HashSet<>();
        int snapshots;
        int teleports;
        int sounds;
        Location position;
        final World world = proxy(World.class,(p,m,a)->switch(m) {
            case "getName" -> "rhythm"; case "equals" -> p==a[0]; case "hashCode" -> 1;
            case "getBlockAt" -> {
                var key = List.of((Integer)a[0],(Integer)a[1],(Integer)a[2]);
                yield proxy(Block.class,(b,n,v)->switch(n) {
                    case "getX" -> key.get(0); case "getY" -> key.get(1); case "getZ" -> key.get(2);
                    case "getType" -> blocks.getOrDefault(key,Material.AIR);
                    case "setType" -> { blocks.put(key,(Material)v[0]); yield null; }
                    case "getState" -> {
                        snapshots++;
                        var original = blocks.getOrDefault(key,Material.AIR);
                        yield proxy(BlockState.class,(s,k,x)-> {
                            if(k.equals("update")) { blocks.put(key,original);restored.add(key);return true; } return null;
                        });
                    }
                    default -> null;
                });
            }
            default -> null;
        });
        final RiptideCourseGeometry g;
        final Player player = proxy(Player.class,(p,m,a)->switch(m) {
            case "getLocation" -> position.clone();
            case "teleport" -> { teleports++; position=((Location)a[0]).clone(); yield true; }
            case "playSound" -> { sounds++; yield null; }
            case "getBoundingBox" -> new BoundingBox(position.getX()-.3,position.getY(),position.getZ()-.3,
                    position.getX()+.3,position.getY()+1.8,position.getZ()+.3);
            default -> null;
        });
        final RiptideRhythmRun run;
        Fixture(String variant) {
            this(variant, new int[]{0,1});
        }
        Fixture(String variant, int[] axis) {
            g=RiptideCourseGeometry.resolve(new Location(world,.5,80,.5),
                    new Location(world,.5+axis[0]*500,80,.5+axis[1]*500),7,9);
            position=g.centerAt(10);
            var t=RiptideLevelTemplate.create("gate",RiptideLevelType.RHYTHM);
            run=new RiptideRhythmRun(g,List.of(new RiptideCoursePlan.Level(1,30,t,variant,0,false,1,0,0,"",0)));
        }
    }
    @FunctionalInterface interface Call { Object invoke(Object proxy,String method,Object[] args); }
    private static <T> T proxy(Class<T> type,Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.invoke(p,m.getName(),a)));
    }
}
