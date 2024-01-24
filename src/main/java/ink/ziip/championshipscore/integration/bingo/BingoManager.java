package ink.ziip.championshipscore.integration.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.Team;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.github.steaf23.bingoreloaded.BingoReloaded;
import io.github.steaf23.bingoreloaded.gameloop.BingoSession;
import io.github.steaf23.bingoreloaded.gameloop.singular.SingularGameManager;
import io.github.steaf23.bingoreloaded.player.BingoParticipant;
import io.github.steaf23.bingoreloaded.player.BingoPlayer;
import io.github.steaf23.bingoreloaded.player.team.TeamManager;
import io.github.steaf23.bingoreloaded.tasks.BingoTask;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BingoManager extends BaseManager {
    private final BingoReloaded bingoReloaded;
    private final Map<Material, List<Team>> bingoTaskCompleteLists = new ConcurrentHashMap<>();
    private final Map<Team, Integer> teamPoints = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private boolean started = false;
    private BingoHandler bingoHandler;

    public BingoManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        this.bingoReloaded = BingoReloaded.getInstance();
    }

    @Override
    public void load() {
        bingoHandler = new BingoHandler(plugin, this);
        bingoHandler.register();
    }

    @Override
    public void unload() {
        bingoHandler.unRegister();
    }

    public void startGame() {
        try {
            Field gameManagerField = bingoReloaded.getClass().getDeclaredField("gameManager");
            gameManagerField.setAccessible(true);

            SingularGameManager singularGameManager = (SingularGameManager) gameManagerField.get(bingoReloaded);

            BingoSession bingoSession = singularGameManager.getSession();
            TeamManager teamManager = singularGameManager.getSession().teamManager;
            for (Team team : plugin.getTeamManager().getTeamList()) {
                team.teleportAllPlayers(CCConfig.BINGO_SPAWN_LOCATION);
                for (Player player : team.getOnlinePlayers()) {
                    BingoParticipant bingoParticipant = teamManager.getPlayerAsParticipant(player);
                    if (bingoParticipant == null) {
                        bingoParticipant = new BingoPlayer(player, teamManager.getSession());
                    }
                    teamManager.addMemberToTeam(bingoParticipant, team.getName().toLowerCase());
                }
            }

            bingoSession.startGame();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected void endGame() {
        Utils.sendMessageToAllPlayers(MessageConfig.BINGO_GAME_END);

        ArrayList<Map.Entry<Team, Integer>> list = new ArrayList<>(teamPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(MessageConfig.BINGO_RANK_BOARD_BAR).append("\n");

        int i = 1;
        for (Map.Entry<Team, Integer> entry : list) {
            String row = MessageConfig.BINGO_RANK_BOARD_INFO
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", String.valueOf(entry.getValue()));
            stringBuilder.append(row).append("\n");

            plugin.getRankManager().addTeamPoints(entry.getKey(), entry.getKey(), GameTypeEnum.Bingo, "bingo", entry.getValue());
            i++;
        }

        Utils.sendMessageToAllPlayers(stringBuilder.toString());

        Bukkit.getLogger().log(Level.INFO, stringBuilder.toString());

        teamPoints.clear();
        bingoTaskCompleteLists.clear();
        started = false;
    }

    private void addPointsToTeam(Team team, int points) {
        teamPoints.putIfAbsent(team, 0);
        teamPoints.put(team, teamPoints.get(team) + points);
    }

    private int getPoints(int num) {
        if (num == 1) {
            return 55;
        }
        if (num == 2) {
            return 45;
        }
        if (num == 3) {
            return 35;
        }
        if (num == 4) {
            return 25;
        }
        if (num == 5) {
            return 15;
        }

        return 10;
    }

    public void handleTeamCompleteTask(BingoTask bingoTask, Team team) {
        List<Team> completeTeams = getCompleteTeams(bingoTask);
        int num = completeTeams.size();
        if (team != null) {
            if (!completeTeams.contains(team)) {
                int points = getPoints(num + 1);
                addCompleteTeams(bingoTask, team);
                addPointsToTeam(team, points);

                String[] messages = MessageConfig.BINGO_TASK_COMPLETE.split("%team%");
                messages[1] = messages[1]
                        .replace("%points%", String.valueOf(points));

                String[] finalMessages = messages[1].split("%task%");
                TextComponent textComponent = new TextComponent(messages[0]);
                TextComponent teamComponent = new TextComponent(team.getName());
                textComponent.setColor(ChatColor.of(team.getName()));
                textComponent.addExtra(teamComponent);
                textComponent.addExtra(new TextComponent(finalMessages[0]));
                textComponent.addExtra(bingoTask.data.getItemDisplayName().asComponent());
                textComponent.addExtra(finalMessages[1]);

                Utils.sendMessageToAllSpigotPlayers(textComponent);
            }
            if (num == 4) {
                String[] messages = MessageConfig.BINGO_TASK_EXPIRED.split("%task%");
                TextComponent textComponent = new TextComponent(messages[0]);
                textComponent.addExtra(bingoTask.data.getItemDisplayName().asComponent());
                textComponent.addExtra(messages[1]);

                Utils.sendMessageToAllSpigotPlayers(textComponent);
            }
        }
    }

    private void addCompleteTeams(BingoTask bingoTask, Team team) {
        List<Team> teams = getCompleteTeams(bingoTask);
        if (!teams.contains(team))
            teams.add(team);
    }

    private List<Team> getCompleteTeams(BingoTask bingoTask) {
        if (!bingoTaskCompleteLists.containsKey(bingoTask.material)) {
            bingoTaskCompleteLists.put(bingoTask.material, new ArrayList<>());
        }
        return bingoTaskCompleteLists.get(bingoTask.material);
    }
}
