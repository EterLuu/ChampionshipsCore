package ink.ziip.championshipscore.api.team.entry;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class TeamMemberEntry {
    private int id;
    private UUID uuid;
    private String username;
    private int teamId;
    private boolean substitute;

    public TeamMemberEntry(int id, UUID uuid, String username, int teamId) {
        this(id, uuid, username, teamId, false);
    }

    public TeamMemberEntry(int id, UUID uuid, String username, int teamId, boolean substitute) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.teamId = teamId;
        this.substitute = substitute;
    }
}
