package ink.ziip.championshipscore.protocol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryProtocolCodecTest {
    @Test
    void manifestRoundTripPreservesPresentationAndOptionalArrival() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<String> mutableSection = new ArrayList<>(List.of("&#ff6b26第一段", "&f第二行"));
        BingoRuntimeRules runtimeRules = new BingoRuntimeRules(5, 3, 128, 17, 32, 180,
                List.of("night_vision:0"), true, 45, List.of(mutableSection),
                BingoIntroductionMode.SPECTATOR,
                new BingoLocationSnapshot(BingoDimension.OVERWORLD, 1.5, 80, -3.5, 90, 12),
                new BingoLocationSnapshot(BingoDimension.NETHER, 4, 70, 8, 0, 0),
                new BingoPresentation(Map.of("bingo.timer", "&#fff566剩余 %time%s")));
        List<BingoTaskSpec> tasks = List.of(new BingoTaskSpec(0, "minecraft:stone", "item",
                Map.of("material", "minecraft:stone", "amount", "1")));
        BingoScoringRules scoring = new BingoScoringRules(1, List.of(40, 30), 50, 3, 10,
                new BingoVariantRules(BingoMode.SPEEDRUN, BingoDifficulty.HARD, 3,
                        BingoRemix.GENESIS, List.of("DIAMOND", "BLAZE_ROD")));
        String configHash = BingoManifestHasher.hash(900, 42L, scoring, runtimeRules, tasks);
        MatchManifest manifest = new MatchManifest(ProtocolVersion.CURRENT,
                UUID.fromString("10000000-0000-0000-0000-000000000001"), 3, 1_800_000_000_000L,
                "bingo-1", MatchRunMode.GAME, 900, 42L, configHash, scoring, runtimeRules, tasks,
                List.of(new TeamSnapshot(7, "red", "RED", "#ff0000", List.of(playerId), 1234.5D)),
                List.of(new PlayerSnapshot(playerId, "Player", ParticipantRole.PLAYER, 7,
                        false, 321.5D)));

        mutableSection.add("不得进入快照");
        MatchManifest decoded = new BinaryProtocolCodec().decodeManifest(
                new BinaryProtocolCodec().encodeManifest(manifest));

        assertEquals(manifest, decoded);
        assertEquals(List.of(List.of("&#ff6b26第一段", "&f第二行")),
                decoded.runtimeRules().introductionRules());
        assertEquals("&#fff566剩余 %time%s",
                decoded.runtimeRules().presentation().message("bingo.timer"));
        assertEquals(BingoIntroductionMode.SPECTATOR, decoded.runtimeRules().introductionMode());
        assertEquals(BingoDimension.OVERWORLD,
                decoded.runtimeRules().introductionSpawn().dimension());
        assertEquals(3, decoded.runtimeRules().finalCountdownSeconds());
        assertEquals(17, decoded.runtimeRules().scatterJitter());
        assertFalse(decoded.participants().getFirst().requiredAtStart());
        assertEquals(1234.5D, decoded.teams().getFirst().points());
        assertEquals(321.5D, decoded.participants().getFirst().points());
        assertEquals(List.of("DIAMOND", "BLAZE_ROD"), decoded.scoring().variant().genesisItems());
        assertEquals(decoded.configHash(), BingoManifestHasher.hash(decoded));
        assertThrows(UnsupportedOperationException.class,
                () -> decoded.runtimeRules().introductionRules().getFirst().add("不可修改"));
    }

    @Test
    void manifestHashIsIndependentOfTaskAttributeInsertionOrder() {
        Map<String, String> first = new java.util.LinkedHashMap<>();
        first.put("material", "minecraft:stone");
        first.put("amount", "2");
        Map<String, String> second = new java.util.LinkedHashMap<>();
        second.put("amount", "2");
        second.put("material", "minecraft:stone");
        BingoScoringRules scoring = new BingoScoringRules(1, List.of(40), 0, 0, 0);
        BingoRuntimeRules runtime = new BingoRuntimeRules(5, 64, 16, 0, List.of());

        assertEquals(
                BingoManifestHasher.hash(300, 9L, scoring, runtime,
                        List.of(new BingoTaskSpec(0, "stone", "item", first))),
                BingoManifestHasher.hash(300, 9L, scoring, runtime,
                        List.of(new BingoTaskSpec(0, "stone", "item", second))));
    }
}
