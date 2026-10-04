package ink.ziip.championshipscore.api.game.skywars.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SkyWarsShrink {
    private int startTime;
    private int endTime;
    private int toRadius;
    private int toHeight;
}
