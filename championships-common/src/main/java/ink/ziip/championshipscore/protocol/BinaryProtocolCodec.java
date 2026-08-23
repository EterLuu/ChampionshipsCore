package ink.ziip.championshipscore.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Dependency-free, length-prefixed wire codec used inside Redis stream payload fields. */
public final class BinaryProtocolCodec {
    private static final int MAGIC = 0x43434231; // CCB1
    private static final int MANIFEST = 1;
    private static final int COMMAND = 2;
    private static final int EVENT = 3;
    private static final int MAX_COLLECTION_SIZE = 16_384;
    private static final int MAX_STRING_BYTES = 4 * 1024 * 1024;

    public byte[] encodeManifest(MatchManifest manifest) {
        ProtocolSupport.required(manifest, "manifest");
        return encode(MANIFEST, out -> {
            out.writeInt(manifest.protocolVersion());
            writeUuid(out, manifest.matchId());
            out.writeLong(manifest.epoch());
            out.writeLong(manifest.createdAtEpochMilli());
            writeString(out, manifest.workerId());
            writeString(out, manifest.runMode().name());
            out.writeInt(manifest.durationSeconds());
            out.writeLong(manifest.cardSeed());
            writeString(out, manifest.configHash());
            writeScoring(out, manifest.scoring());
            writeRuntimeRules(out, manifest.runtimeRules());

            out.writeInt(manifest.tasks().size());
            for (BingoTaskSpec task : manifest.tasks()) {
                out.writeInt(task.cellIndex());
                writeString(out, task.taskId());
                writeString(out, task.taskType());
                writeAttributes(out, task.attributes());
            }

            out.writeInt(manifest.teams().size());
            for (TeamSnapshot team : manifest.teams()) {
                out.writeInt(team.id());
                writeString(out, team.name());
                writeString(out, team.colorName());
                writeString(out, team.colorCode());
                out.writeInt(team.members().size());
                for (UUID member : team.members()) writeUuid(out, member);
                out.writeDouble(team.points());
            }

            out.writeInt(manifest.participants().size());
            for (PlayerSnapshot participant : manifest.participants()) {
                writeUuid(out, participant.uuid());
                writeString(out, participant.username());
                writeString(out, participant.role().name());
                out.writeBoolean(participant.teamId() != null);
                if (participant.teamId() != null) out.writeInt(participant.teamId());
                out.writeBoolean(participant.requiredAtStart());
                out.writeDouble(participant.points());
            }
        });
    }

    public MatchManifest decodeManifest(byte[] bytes) {
        return decode(bytes, MANIFEST, in -> {
            int protocolVersion = in.readInt();
            UUID matchId = readUuid(in);
            long epoch = in.readLong();
            long createdAt = in.readLong();
            String workerId = readString(in);
            MatchRunMode runMode = readEnum(in, MatchRunMode.class);
            int duration = in.readInt();
            long seed = in.readLong();
            String configHash = readString(in);
            BingoScoringRules scoring = readScoring(in);
            BingoRuntimeRules runtimeRules = readRuntimeRules(in);

            List<BingoTaskSpec> tasks = new ArrayList<>();
            for (int remaining = readSize(in, "tasks"); remaining > 0; remaining--) {
                tasks.add(new BingoTaskSpec(in.readInt(), readString(in), readString(in), readAttributes(in)));
            }

            List<TeamSnapshot> teams = new ArrayList<>();
            for (int remaining = readSize(in, "teams"); remaining > 0; remaining--) {
                int teamId = in.readInt();
                String name = readString(in);
                String colorName = readString(in);
                String colorCode = readString(in);
                List<UUID> members = new ArrayList<>();
                for (int memberCount = readSize(in, "members"); memberCount > 0; memberCount--) {
                    members.add(readUuid(in));
                }
                teams.add(new TeamSnapshot(teamId, name, colorName, colorCode, members, in.readDouble()));
            }

            List<PlayerSnapshot> participants = new ArrayList<>();
            for (int remaining = readSize(in, "participants"); remaining > 0; remaining--) {
                UUID uuid = readUuid(in);
                String username = readString(in);
                ParticipantRole role = readEnum(in, ParticipantRole.class);
                Integer teamId = in.readBoolean() ? in.readInt() : null;
                participants.add(new PlayerSnapshot(uuid, username, role, teamId,
                        in.readBoolean(), in.readDouble()));
            }
            return new MatchManifest(protocolVersion, matchId, epoch, createdAt, workerId, runMode,
                    duration, seed, configHash, scoring, runtimeRules, tasks, teams, participants);
        });
    }

    public byte[] encodeCommand(MatchCommand command) {
        ProtocolSupport.required(command, "command");
        return encode(COMMAND, out -> {
            out.writeInt(command.protocolVersion());
            writeUuid(out, command.messageId());
            writeUuid(out, command.matchId());
            out.writeLong(command.epoch());
            out.writeLong(command.createdAtEpochMilli());
            writeString(out, command.type().name());
            writeAttributes(out, command.attributes());
        });
    }

    public MatchCommand decodeCommand(byte[] bytes) {
        return decode(bytes, COMMAND, in -> new MatchCommand(
                in.readInt(), readUuid(in), readUuid(in), in.readLong(), in.readLong(),
                readEnum(in, MatchCommandType.class), readAttributes(in)));
    }

    public byte[] encodeEvent(MatchEvent event) {
        ProtocolSupport.required(event, "event");
        return encode(EVENT, out -> {
            out.writeInt(event.protocolVersion());
            writeUuid(out, event.messageId());
            writeUuid(out, event.matchId());
            out.writeLong(event.epoch());
            out.writeLong(event.seq());
            out.writeLong(event.createdAtEpochMilli());
            writeString(out, event.type().name());
            writeAttributes(out, event.attributes());
        });
    }

    public MatchEvent decodeEvent(byte[] bytes) {
        return decode(bytes, EVENT, in -> new MatchEvent(
                in.readInt(), readUuid(in), readUuid(in), in.readLong(), in.readLong(), in.readLong(),
                readEnum(in, MatchEventType.class), readAttributes(in)));
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

    private static BingoScoringRules readScoring(DataInputStream in) throws IOException {
        int width = in.readInt();
        List<Integer> points = new ArrayList<>();
        for (int remaining = readSize(in, "claimPoints"); remaining > 0; remaining--) {
            points.add(in.readInt());
        }
        int lineBonus = in.readInt();
        int majorLines = in.readInt();
        int minorBonus = in.readInt();
        BingoMode mode = readEnum(in, BingoMode.class);
        BingoDifficulty difficulty = readEnum(in, BingoDifficulty.class);
        int winLines = in.readInt();
        BingoRemix remix = readEnum(in, BingoRemix.class);
        List<String> genesisItems = new ArrayList<>();
        for (int remaining = readSize(in, "genesisItems"); remaining > 0; remaining--)
            genesisItems.add(readString(in));
        BingoVariantRules variant = new BingoVariantRules(mode, difficulty, winLines, remix, genesisItems);
        return new BingoScoringRules(width, points, lineBonus, majorLines, minorBonus, variant);
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
        writeAttributes(out, rules.presentation().messages());
    }

    private static BingoRuntimeRules readRuntimeRules(DataInputStream in) throws IOException {
        int preparationSeconds = in.readInt();
        int finalCountdownSeconds = in.readInt();
        int scatterRadius = in.readInt();
        int scatterJitter = in.readInt();
        int scatterMaxTries = in.readInt();
        int pvpGrace = in.readInt();
        List<String> effects = new ArrayList<>();
        for (int remaining = readSize(in, "permanentEffects"); remaining > 0; remaining--) {
            effects.add(readString(in));
        }
        boolean showIntroduction = in.readBoolean();
        int introductionSeconds = in.readInt();
        List<List<String>> rules = new ArrayList<>();
        for (int remaining = readSize(in, "introductionRules"); remaining > 0; remaining--) {
            List<String> section = new ArrayList<>();
            for (int lines = readSize(in, "introductionRuleLines"); lines > 0; lines--) {
                section.add(readString(in));
            }
            rules.add(section);
        }
        BingoIntroductionMode introductionMode = readEnum(in, BingoIntroductionMode.class);
        BingoLocationSnapshot introductionSpawn = readLocation(in);
        BingoLocationSnapshot spectatorSpawn = readLocation(in);
        return new BingoRuntimeRules(preparationSeconds, finalCountdownSeconds, scatterRadius, scatterJitter,
                scatterMaxTries, pvpGrace, effects, showIntroduction, introductionSeconds, rules,
                introductionMode, introductionSpawn, spectatorSpawn,
                new BingoPresentation(readAttributes(in)));
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

    private static BingoLocationSnapshot readLocation(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return null;
        return new BingoLocationSnapshot(readEnum(in, BingoDimension.class), in.readDouble(),
                in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
    }

    private static void writeAttributes(DataOutputStream out, Map<String, String> attributes) throws IOException {
        Map<String, String> sorted = new TreeMap<>(attributes);
        out.writeInt(sorted.size());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            writeString(out, entry.getKey());
            writeString(out, entry.getValue());
        }
    }

    private static Map<String, String> readAttributes(DataInputStream in) throws IOException {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int remaining = readSize(in, "attributes"); remaining > 0; remaining--) {
            attributes.put(readString(in), readString(in));
        }
        return Map.copyOf(attributes);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IllegalArgumentException("String is too large");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Invalid string length " + length);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated string payload");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readSize(DataInputStream in, String name) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_COLLECTION_SIZE) throw new IOException("Invalid " + name + " size " + size);
        return size;
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, Class<E> type) throws IOException {
        String name = readString(in);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Unknown " + type.getSimpleName() + " value " + name, invalid);
        }
    }

    private static byte[] encode(int kind, Writer writer) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(kind);
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode in-memory protocol message", impossible);
        }
    }

    private static <T> T decode(byte[] bytes, int expectedKind, Reader<T> reader) {
        ProtocolSupport.required(bytes, "bytes");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid protocol magic");
            int kind = in.readUnsignedByte();
            if (kind != expectedKind) throw new IOException("Unexpected message kind " + kind);
            T value = reader.read(in);
            if (in.available() != 0) throw new IOException("Trailing bytes after protocol message");
            return value;
        } catch (EOFException truncated) {
            throw new IllegalArgumentException("Truncated protocol message", truncated);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid protocol message", invalid);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }
}
