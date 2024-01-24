package ink.ziip.championshipscore.api.team.entry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TeamEntry {
    private int id;
    private String name;
    private String colorName;
    private String colorCode;
}
