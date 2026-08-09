package ink.ziip.championshipscore.protocol;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Team data frozen when SCC creates a match. */
public record TeamSnapshot(
        int id,
        String name,
        String colorName,
        String colorCode,
        List<UUID> members,
        double points
) {
    public TeamSnapshot(int id, String name, String colorName, String colorCode, List<UUID> members) {
        this(id, name, colorName, colorCode, members, 0D);
    }

    public TeamSnapshot {
        if (id < 0) throw new IllegalArgumentException("id must be non-negative");
        name = ProtocolSupport.nonBlank(name, "name");
        colorName = ProtocolSupport.nonBlank(colorName, "colorName");
        colorCode = ProtocolSupport.nonBlank(colorCode, "colorCode");
        members = ProtocolSupport.immutableList(members, "members");
        if (Set.copyOf(members).size() != members.size()) {
            throw new IllegalArgumentException("members must not contain duplicate UUIDs");
        }
        if (!Double.isFinite(points)) throw new IllegalArgumentException("points must be finite");
    }
}
