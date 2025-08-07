package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class GameEventRequest {
    private String player;
    private String team;
    private String event;
    private String lore;

    public GameEventRequest() {}

}