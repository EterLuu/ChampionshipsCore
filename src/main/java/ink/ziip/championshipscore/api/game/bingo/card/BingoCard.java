package ink.ziip.championshipscore.api.game.bingo.card;

import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A square grid of {@link GameTask}s for one team, with line/row/column/diagonal win detection.
 */
public final class BingoCard {
    public final CardSize size;
    private final List<GameTask> tasks;

    public BingoCard(CardSize size, List<GameTask> tasks) {
        this.size = size;
        this.tasks = new ArrayList<>(tasks);
    }

    public List<GameTask> getTasks() {
        return tasks;
    }

    public int getCompleteCount(@NotNull String teamId) {
        int count = 0;
        for (GameTask task : tasks) {
            if (task.isCompletedByTeam(teamId)) count++;
        }
        return count;
    }

    /**
     * @return grid indices of every fully-completed row/column/diagonal for the team. Each element is
     *         an array of {@link CardSize#size} cell indices forming one line; the returned list is
     *         empty when no line is complete. Order: rows top-to-bottom, columns left-to-right,
     *         main diagonal, anti-diagonal.
     */
    public List<int[]> completedLines(@NotNull String teamId) {
        List<int[]> lines = new ArrayList<>();
        int n = size.size;

        for (int y = 0; y < n; y++) {
            int[] row = new int[n];
            boolean full = true;
            for (int x = 0; x < n; x++) {
                int idx = n * y + x;
                row[x] = idx;
                if (!tasks.get(idx).isCompletedByTeam(teamId)) full = false;
            }
            if (full) lines.add(row);
        }
        for (int x = 0; x < n; x++) {
            int[] col = new int[n];
            boolean full = true;
            for (int y = 0; y < n; y++) {
                int idx = n * y + x;
                col[y] = idx;
                if (!tasks.get(idx).isCompletedByTeam(teamId)) full = false;
            }
            if (full) lines.add(col);
        }

        int[] diag1 = new int[n];
        boolean d1 = true;
        for (int i = 0; i < n; i++) {
            int idx = i * (n + 1);
            diag1[i] = idx;
            if (!tasks.get(idx).isCompletedByTeam(teamId)) d1 = false;
        }
        if (d1) lines.add(diag1);

        int[] diag2 = new int[n];
        boolean d2 = true;
        for (int i = 0; i < n; i++) {
            int idx = (i + 1) * (n - 1);
            diag2[i] = idx;
            if (!tasks.get(idx).isCompletedByTeam(teamId)) d2 = false;
        }
        if (d2) lines.add(diag2);

        return lines;
    }

    /**
     * Geometry of every line (rows, columns, both diagonals) as arrays of cell indices, independent of
     * any team's progress. Used by stalemate detection to test which lines a team could still complete.
     */
    public List<int[]> allLines() {
        List<int[]> lines = new ArrayList<>();
        int n = size.size;
        for (int y = 0; y < n; y++) {
            int[] row = new int[n];
            for (int x = 0; x < n; x++) row[x] = n * y + x;
            lines.add(row);
        }
        for (int x = 0; x < n; x++) {
            int[] col = new int[n];
            for (int y = 0; y < n; y++) col[y] = n * y + x;
            lines.add(col);
        }
        int[] diag1 = new int[n];
        for (int i = 0; i < n; i++) diag1[i] = i * (n + 1);
        lines.add(diag1);
        int[] diag2 = new int[n];
        for (int i = 0; i < n; i++) diag2[i] = (i + 1) * (n - 1);
        lines.add(diag2);
        return lines;
    }

    /** Grid indices of cells the team has completed. Used for non-line win highlighting. */
    public int[] completedIndices(@NotNull String teamId) {
        int[] tmp = new int[tasks.size()];
        int n = 0;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).isCompletedByTeam(teamId)) tmp[n++] = i;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    /** @return number of fully-completed rows + columns + diagonals for the team. */
    public int countCompletedLines(@NotNull String teamId) {
        int lines = 0;

        for (int y = 0; y < size.size; y++) {
            boolean row = true;
            boolean col = true;
            for (int x = 0; x < size.size; x++) {
                if (!tasks.get(size.size * y + x).isCompletedByTeam(teamId)) row = false;
                if (!tasks.get(size.size * x + y).isCompletedByTeam(teamId)) col = false;
            }
            if (row) lines++;
            if (col) lines++;
        }

        boolean diag1 = true;
        for (int idx = 0; idx < size.fullCardSize; idx += size.size + 1) {
            if (!tasks.get(idx).isCompletedByTeam(teamId)) {
                diag1 = false;
                break;
            }
        }
        if (diag1) lines++;

        boolean diag2 = true;
        for (int idx = size.size - 1; idx <= size.fullCardSize - size.size; idx += size.size - 1) {
            if (!tasks.get(idx).isCompletedByTeam(teamId)) {
                diag2 = false;
                break;
            }
        }
        if (diag2) lines++;

        return lines;
    }
}
