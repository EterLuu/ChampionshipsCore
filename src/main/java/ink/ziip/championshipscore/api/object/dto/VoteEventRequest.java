package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class VoteEventRequest {
    private String game;
    private int ticket;

    public VoteEventRequest() {
    }
}