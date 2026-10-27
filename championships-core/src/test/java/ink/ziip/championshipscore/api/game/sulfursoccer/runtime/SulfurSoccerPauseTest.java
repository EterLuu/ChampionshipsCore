package ink.ziip.championshipscore.api.game.sulfursoccer.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.sulfursoccer.config.SulfurSoccerConfig;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;

@ExtendWith(ConfigurationStateExtension.class)
class SulfurSoccerPauseTest {
    @Test
    void refereePauseSurvivesTicksAndReconnectUntilExplicitResume() throws Exception {
        MessageConfig.SULFUR_SOCCER_PAUSED = "paused";
        MessageConfig.SULFUR_SOCCER_RESUMED = "resumed";
        MessageConfig.SULFUR_SOCCER_SCORE = "score";
        var area = allocate(SulfurSoccerArea.class);
        set(area, "gameConfig", allocate(SulfurSoccerConfig.class));
        set(area, "gameStageEnum", GameStageEnum.PROGRESS);
        set(area, "spectators", new HashSet<>());
        set(area, "pearls", new HashMap<>());
        Team right = new Team(1), left = new Team(2);
        set(area, "rightChampionshipTeam", right);
        set(area, "leftChampionshipTeam", left);
        set(area, "match", new SulfurSoccerMatch(3));
        var tick = SulfurSoccerArea.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        assertTrue(area.pauseMatch());
        for (int i = 0; i < 10; i++) tick.invoke(area);
        assertTrue(area.isPaused());
        right.online = false;
        tick.invoke(area);
        assertTrue(area.resumeMatch());
        assertTrue(area.isPaused(), "Offline roster still requires a pause");
        assertTrue(area.pauseMatch(), "Referee may retain a pause while players reconnect");
        right.online = true;
        tick.invoke(area);
        assertTrue(area.isPaused());
        assertTrue(area.resumeMatch());
        assertFalse(area.isPaused());
        right.online = false;
        tick.invoke(area);
        assertTrue(area.isPaused());
        right.online = true;
        tick.invoke(area);
        assertFalse(area.isPaused());
    }

    static final class Team extends ChampionshipTeam {
        boolean online = true;
        final Player player = proxy(Player.class, (p, m, a) -> null);

        Team(int id) {
            super(id, "team", "red", "", Set.of(UUID.randomUUID()), null);
        }

        @Override
        public List<Player> getOnlinePlayers() {
            return online ? List.of(player) : List.of();
        }

        @Override
        public String getColoredName() {
            return "team";
        }

        @Override
        public void sendMessageToAll(String ignored) {}

        @Override
        public void sendActionBarToAll(String ignored) {}
    }
}
