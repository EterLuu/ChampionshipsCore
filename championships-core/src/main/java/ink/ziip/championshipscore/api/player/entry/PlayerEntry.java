package ink.ziip.championshipscore.api.player.entry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PlayerEntry {
    private int id;
    private String name;
    private UUID uuid;
}
