package ink.ziip.championshipscore.api.team.entry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class TeamMemberEntry {
    private int id;
    private UUID uuid;
    private String username;
    private int teamId;
}
