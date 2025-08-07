package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class PlayerScoreRequest {
    private String player;
    private String team;
    private int score;

    public PlayerScoreRequest() {
    }
}