package ink.ziip.championshipscore.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic digest of every frozen input that can alter Worker-side Bingo execution. */
public final class BingoManifestHasher {
    private BingoManifestHasher() {
    }

    public static String hash(int durationSeconds, long cardSeed, BingoScoringRules scoring,
                              BingoRuntimeRules runtimeRules, List<BingoTaskSpec> tasks) {
        ProtocolSupport.required(scoring, "scoring");
        ProtocolSupport.required(runtimeRules, "runtimeRules");
        ProtocolSupport.required(tasks, "tasks");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(ProtocolVersion.CURRENT);
            out.writeInt(durationSeconds);
            out.writeLong(cardSeed);
            writeScoring(out, scoring);
            writeRuntimeRules(out, runtimeRules);
            List<BingoTaskSpec> ordered = tasks.stream()
                    .sorted(Comparator.comparingInt(BingoTaskSpec::cellIndex)).toList();
            out.writeInt(ordered.size());
            for (BingoTaskSpec task : ordered) {
                out.writeInt(task.cellIndex());
                writeString(out, task.taskId());
                writeString(out, task.taskType());
                TreeMap<String, String> attributes = new TreeMap<>(task.attributes());
                out.writeInt(attributes.size());
                for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                    writeString(out, attribute.getKey());
                    writeString(out, attribute.getValue());
                }
            }
            out.flush();
            return HexFormat.of().formatHex(sha256(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to hash in-memory Bingo manifest", impossible);
        }
    }

    public static String hash(MatchManifest manifest) {
        ProtocolSupport.required(manifest, "manifest");
        return hash(manifest.durationSeconds(), manifest.cardSeed(), manifest.scoring(),
                manifest.runtimeRules(), manifest.tasks());
    }

    private static void writeScoring(DataOutputStream out, BingoScoringRules scoring) throws IOException {
        out.writeInt(scoring.cardWidth());
        out.writeInt(scoring.claimPoints().size());
        for (int points : scoring.claimPoints()) out.writeInt(points);
        out.writeInt(scoring.lineBonus());
        out.writeInt(scoring.lineBonusMajorCount());
        out.writeInt(scoring.lineBonusMinor());
        writeString(out, scoring.variant().mode().name());
        writeString(out, scoring.variant().difficulty().name());
        out.writeInt(scoring.variant().winLines());
        writeString(out, scoring.variant().remix().name());
        out.writeInt(scoring.variant().genesisItems().size());
        for (String item : scoring.variant().genesisItems()) writeString(out, item);
    }

    private static void writeRuntimeRules(DataOutputStream out, BingoRuntimeRules rules) throws IOException {
        out.writeInt(rules.preparationSeconds());
        out.writeInt(rules.finalCountdownSeconds());
        out.writeInt(rules.scatterRadius());
        out.writeInt(rules.scatterJitter());
        out.writeInt(rules.scatterMaxTries());
        out.writeInt(rules.pvpGraceSeconds());
        out.writeInt(rules.permanentEffects().size());
        for (String effect : rules.permanentEffects()) writeString(out, effect);
        out.writeBoolean(rules.showIntroduction());
        out.writeInt(rules.introductionSeconds());
        out.writeInt(rules.introductionRules().size());
        for (List<String> section : rules.introductionRules()) {
            out.writeInt(section.size());
            for (String line : section) writeString(out, line);
        }
        writeString(out, rules.introductionMode().name());
        writeLocation(out, rules.introductionSpawn());
        writeLocation(out, rules.spectatorSpawn());
        TreeMap<String, String> presentation = new TreeMap<>(rules.presentation().messages());
        out.writeInt(presentation.size());
        for (Map.Entry<String, String> entry : presentation.entrySet()) {
            writeString(out, entry.getKey());
            writeString(out, entry.getValue());
        }
    }

    private static void writeLocation(DataOutputStream out, BingoLocationSnapshot location) throws IOException {
        out.writeBoolean(location != null);
        if (location == null) return;
        writeString(out, location.dimension().name());
        out.writeDouble(location.x());
        out.writeDouble(location.y());
        out.writeDouble(location.z());
        out.writeFloat(location.yaw());
        out.writeFloat(location.pitch());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
