package ink.ziip.championshipscore.api.rank.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TeamPointEntry {
    private int id;
    private int teamId;
    private int rivalId;
    private String team;
    private String rival;
    private GameTypeEnum game;
    private String area;
    private String round;
    private int points;
    private String time;
}
