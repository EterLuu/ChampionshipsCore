package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

class LaserShowdownKillFeedbackTest {
    @Test
    void decisiveKillMelodyCanFinishDuringSettlement() throws Exception {
        var area = area();
        UUID id = UUID.randomUUID();
        set(area, "gamePlayers", List.of(id));
        set(area, "gameStageEnum", GameStageEnum.PROGRESS);
        assertTrue(area.isKillFeedbackCurrent(7, id));
        set(area, "gameStageEnum", GameStageEnum.END);
        assertTrue(area.isKillFeedbackCurrent(7, id), "a 40th kill must finish all four notes");
    }

    @Test
    void resetNewRunDisposeAndLeavingInvalidateOldNotes() throws Exception {
        var area = area();
        UUID id = UUID.randomUUID();
        set(area, "gamePlayers", List.of(id));
        for (GameStageEnum stage :
                List.of(
                        GameStageEnum.WAITING,
                        GameStageEnum.PREPARATION,
                        GameStageEnum.COUNTDOWN)) {
            set(area, "gameStageEnum", stage);
            assertFalse(area.isKillFeedbackCurrent(7, id));
        }
        set(area, "gameStageEnum", GameStageEnum.PROGRESS);
        set(area, "startGeneration", 8L);
        assertFalse(area.isKillFeedbackCurrent(7, id));
        set(area, "startGeneration", 7L);
        set(area, "disposed", true);
        assertFalse(area.isKillFeedbackCurrent(7, id));
        set(area, "disposed", false);
        set(area, "gamePlayers", List.of());
        assertFalse(area.isKillFeedbackCurrent(7, id));
    }

    private static LaserShowdownTeamArea area() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var area =
                (LaserShowdownTeamArea)
                        ((sun.misc.Unsafe) field.get(null))
                                .allocateInstance(LaserShowdownTeamArea.class);
        set(area, "startGeneration", 7L);
        return area;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
