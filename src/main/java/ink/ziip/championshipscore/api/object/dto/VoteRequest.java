package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
public class VoteRequest {
    private List<VoteEventRequest> votes;
    private int time;

    public VoteRequest() {
    }
}