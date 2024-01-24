package ink.ziip.championshipscore.api.rank.entry;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class GameStatusEntry {
    private int id;
    private String time;
    private GameTypeEnum game;
    private int order;
}
