package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class GlobalEventRequest {
    private String status;
    private GameInfo game;

    public GlobalEventRequest() {
    }

    @Setter
    @Getter
    public static class GameInfo {
        private String name;
        private int round;

        public GameInfo() {
        }

        public GameInfo(String name, int round) {
            this.name = name;
            this.round = round;
        }
    }
}