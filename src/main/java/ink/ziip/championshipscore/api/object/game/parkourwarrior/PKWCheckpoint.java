package ink.ziip.championshipscore.api.object.game.parkourwarrior;

import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PKWCheckpoint {
    private ConfigurationSection pkwCheckpoint;
    private String name;
    private PKWCheckPointTypeEnum type;
    private List<CCSelection> subCheckpoints;
    private CCSelection enter;
    private Location spawn;
    private PKWFinalCheckPointTypeEnum finalCheckPointType;

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof PKWCheckpoint))
            return false;
        return this.name.equals(((PKWCheckpoint) o).name);
    }

    public double getPointMultiplier(ParkourWarriorConfig parkourWarriorConfig) {
        if (finalCheckPointType != null) {
            if (finalCheckPointType == PKWFinalCheckPointTypeEnum.easy)
                return parkourWarriorConfig.getFinalPointsEasy();
            else if (finalCheckPointType == PKWFinalCheckPointTypeEnum.normal)
                return parkourWarriorConfig.getFinalPointsNormal();
            else if (finalCheckPointType == PKWFinalCheckPointTypeEnum.hard)
                return parkourWarriorConfig.getFinalPointsHard();
        }
        return 0d;
    }
}