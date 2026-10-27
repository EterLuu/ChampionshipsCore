package ink.ziip.championshipscore.api.game.battlebox.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.battlebox.config.BattleBoxConfig;
import ink.ziip.championshipscore.api.game.battlebox.geometry.BattleBoxGeometry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.*;

class BattleBoxWoolTest {
    @Test
    void countsOnlyTeamWoolAndReadsExternalChangesOnTheNextScan() throws Exception {
        var f = new Fixture();
        f.blocks[0] = Material.RED_WOOL;
        f.blocks[1] = Material.BLUE_WOOL;
        f.blocks[2] = Material.RED_WOOL;
        assertEquals(new BattleBoxMatch.WoolCount(2, 1), f.match.countWool());
        Arrays.fill(f.blocks, Material.BLUE_WOOL);
        assertEquals(new BattleBoxMatch.WoolCount(0, 9), f.match.countWool());
        assertTrue(f.match.countWool().complete());
        assertEquals(27, f.reads);
    }

    @Test
    void resetUsesOnePhysicsEnabledWritePerBlockAndPreservesNeutralColour() throws Exception {
        var f = new Fixture();
        f.match.resetCenterWool();
        assertEquals(9, f.writes);
        for (Material m : f.blocks) assertEquals(Material.WHITE_WOOL, m);
        assertFalse(f.match.countWool().complete());
    }

    @Test
    void partialFloorTieAndChangedTeamColourAreCountedWithoutCachedScoring() throws Exception {
        var f = new Fixture();
        f.blocks[0] = Material.RED_WOOL;
        f.blocks[1] = Material.BLUE_WOOL;
        assertEquals(new BattleBoxMatch.WoolCount(1, 1), f.match.countWool());
        set(f.right, "colorName", "GREEN");
        f.blocks[2] = Material.GREEN_WOOL;
        assertEquals(new BattleBoxMatch.WoolCount(1, 1), f.match.countWool());
        assertFalse(f.match.countWool().complete());
    }

    private static final class Fixture {
        final Material[] blocks = new Material[9];
        final ChampionshipTeam right = allocate(ChampionshipTeam.class);
        final BattleBoxMatch match;
        int writes, reads;

        Fixture() throws Exception {
            Arrays.fill(blocks, Material.WHITE_WOOL);
            var left = allocate(ChampionshipTeam.class);
            set(right, "colorName", "RED");
            set(left, "colorName", "BLUE");
            World world =
                    proxy(
                            World.class,
                            (p, m, a) -> {
                                if (!m.getName().equals("getBlockAt"))
                                    throw new AssertionError(m.getName());
                                int index = (int) a[0] * 3 + (int) a[2];
                                return proxy(
                                        Block.class,
                                        (b, method, args) ->
                                                switch (method.getName()) {
                                                    case "getType" -> {
                                                        reads++;
                                                        yield blocks[index];
                                                    }
                                                    case "setType" -> {
                                                        assertEquals(
                                                                1,
                                                                args.length,
                                                                "default physics is preserved");
                                                        writes++;
                                                        blocks[index] = (Material) args[0];
                                                        yield null;
                                                    }
                                                    default ->
                                                            throw new AssertionError(
                                                                    method.getName());
                                                });
                            });
            var config = allocate(BattleBoxConfig.class);
            set(config, "rightSpawnPoint", new Location(world, 0, 70, 0));
            set(config, "woolPos1", new Vector(0, 70, 0));
            set(config, "woolPos2", new Vector(2, 70, 2));
            set(config, "areaPos1", new Vector(-10, 60, -10));
            set(config, "areaPos2", new Vector(10, 80, 10));
            match = new BattleBoxMatch(0, right, left, BattleBoxGeometry.from(config));
        }
    }
}
