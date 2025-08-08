package ink.ziip.championshipscore.api.object.schedule;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TwoVTwoVector {
    private ChampionshipTeam teamOne;
    private ChampionshipTeam teamTwo;

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TwoVTwoVector))
            return false;

        if (((TwoVTwoVector) o).teamOne.equals(teamOne) && ((TwoVTwoVector) o).teamTwo.equals(teamTwo))
            return true;
        return ((TwoVTwoVector) o).teamTwo.equals(teamOne) && ((TwoVTwoVector) o).teamOne.equals(teamTwo);
    }
}
