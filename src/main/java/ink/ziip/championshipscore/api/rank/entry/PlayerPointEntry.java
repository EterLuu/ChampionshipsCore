package ink.ziip.championshipscore.api.rank.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PlayerPointEntry {
    private int id;
    private UUID uuid;
    private String username;
    private int teamId;
    private String team;
    private int rivalId;
    private String rival;
    private GameTypeEnum game;
    private String area;
    private String round;
    private double points;
    private String time;
    private int valid;
}
