package ink.ziip.championshipscore.api.rank;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankRefreshContractTest {
    @Test
    void periodicRefreshUsesOnePointSnapshotInsteadOfPerTeamAndPerPlayerQueries() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ink/ziip/championshipscore/api/rank/RankManager.java"));
        assertTrue(source.contains("rankDao.getAllValidPlayerPoints()"));
        assertTrue(source.contains("validPointSnapshot = pointSnapshot"));
        assertFalse(source.contains("private final TeamDaoImpl teamDao"));
        assertFalse(source.contains("private double calculatePlayerPoints(UUID uuid)"));
        assertFalse(source.contains("rankDao.getTeamPlayerPoints(championshipTeam.getId())"));
    }
}
