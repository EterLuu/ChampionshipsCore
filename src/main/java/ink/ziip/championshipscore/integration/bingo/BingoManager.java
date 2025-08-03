package ink.ziip.championshipscore.integration.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.bingo.BingoTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.github.steaf23.bingoreloaded.BingoReloaded;
import io.github.steaf23.bingoreloaded.gameloop.BingoSession;
import io.github.steaf23.bingoreloaded.gameloop.SingularGameManager;
import io.github.steaf23.bingoreloaded.player.BingoParticipant;
import io.github.steaf23.bingoreloaded.player.BingoPlayer;
import io.github.steaf23.bingoreloaded.player.team.TeamManager;
import io.github.steaf23.bingoreloaded.tasks.GameTask;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BingoManager extends BaseManager {
    private final BingoReloaded bingoReloaded;
    private final Map<Material, List<ChampionshipTeam>> bingoTaskCompleteLists = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Integer> teamPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPoints = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private boolean started = false;
    private BingoHandler bingoHandler;
    private BingoSession session;
    private int timer;
    private BukkitTask pvpTask;
    @Getter
    private boolean allowPvP = false;

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
        if (session != null) {
            session.endGame();
        }
        bingoHandler.unRegister();
    }

    public void startGame() {
        try {
            Field gameManagerField = bingoReloaded.getClass().getDeclaredField("gameManager");
            gameManagerField.setAccessible(true);

            SingularGameManager gameManager = (SingularGameManager) gameManagerField.get(bingoReloaded);

            World world = Bukkit.getWorld("bingo");
            if (world == null)
                return;

            BingoSession session = gameManager.getSessionFromWorld(world);

            if (session == null)
                return;

            TeamManager teamManager = session.teamManager;

            plugin.getScheduleManager().addRound(GameTypeEnum.Bingo);

            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                championshipTeam.teleportAllPlayers(CCConfig.BINGO_SPAWN_LOCATION);
                championshipTeam.setGameModeForAllPlayers(GameMode.SPECTATOR);
            }

            allowPvP = false;

            timer = 20;
            plugin.getServer().getScheduler().runTaskTimer(plugin, (task) -> {
                if (timer == 15) {
                    for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                        for (Player player : championshipTeam.getOnlinePlayers()) {
                            BingoParticipant bingoParticipant = teamManager.getPlayerAsParticipant(player);
                            if (bingoParticipant == null) {
                                bingoParticipant = new BingoPlayer(player, session);
                            }
                            teamManager.addMemberToTeam(bingoParticipant, championshipTeam.getColorName().toLowerCase());
                        }
                    }

                    Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BINGO));
                }

                if (timer == 10) {
                    Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.BINGO_POINTS));
                }

                Utils.changeLevelForAllPlayers(timer);

                if (timer < 5 && timer > 1) {
                    Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
                }
                if (timer == 1) {
                    Utils.playSoundToAllPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
                }

                if (timer == 0) {
                    Utils.changeLevelForAllPlayers(0);
                    for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                        championshipTeam.setGameModeForAllPlayers(GameMode.SURVIVAL);

                        for (UUID uuid : championshipTeam.getMembers()) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null) {
                                PotionEffect potionEffectGlowing = new PotionEffect(PotionEffectType.GLOWING, 606 * 20, 0, false, false);
                                player.addPotionEffect(potionEffectGlowing);
                            }
                        }
                    }
                    session.startGame();

                    pvpTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        allowPvP = true;
                        Utils.sendMessageToAllPlayers(MessageConfig.BINGO_PVP_START);
                    }, 3720L);
                    task.cancel();
                }

                timer--;
            }, 0, 20L);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected void endGame() {
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (Player player : championshipTeam.getOnlinePlayers()) {
                for (PotionEffect potionEffect : player.getActivePotionEffects())
                    player.removePotionEffect(potionEffect.getType());
            }
        }
        if (pvpTask != null)
            pvpTask.cancel();

        allowPvP = false;

        Utils.sendMessageToAllPlayers(MessageConfig.BINGO_GAME_END);

        ArrayList<Map.Entry<ChampionshipTeam, Integer>> list = new ArrayList<>(teamPoints.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(MessageConfig.BINGO_RANK_BOARD_BAR).append("\n");

        int i = 1;
        for (Map.Entry<ChampionshipTeam, Integer> entry : list) {
            String row = MessageConfig.BINGO_RANK_BOARD_INFO
                    .replace("%team_rank%", String.valueOf(i))
                    .replace("%team%", entry.getKey().getColoredName())
                    .replace("%team_point%", String.valueOf(entry.getValue()));
            stringBuilder.append(row).append("\n");

            for (UUID uuid : entry.getKey().getMembers()) {
                int points = playerPoints.getOrDefault(uuid, 0) + entry.getValue();
                plugin.getRankManager().addPlayerPoints(uuid, null, GameTypeEnum.Bingo, "bingo", points);
            }

            i++;
        }

        Utils.sendMessageToAllPlayers(stringBuilder.toString());

        Bukkit.getLogger().log(Level.INFO, stringBuilder.toString());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                championshipTeam.teleportAllPlayers(CCConfig.LOBBY_LOCATION);
            }
        }, 50L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                championshipTeam.setGameModeForAllPlayers(GameMode.ADVENTURE);
                championshipTeam.cleanInventoryForAllPlayers();
            }

            Utils.sendMessageToAllPlayers(Utils.getMessage(ScheduleMessageConfig.ROUND_END));
        }, 70L);

        teamPoints.clear();
        playerPoints.clear();
        bingoTaskCompleteLists.clear();
        started = false;

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(new BingoTeamArea(plugin, null, null), plugin.getTeamManager().getTeamList()));
    }

    private void addPointsToTeam(ChampionshipTeam championshipTeam, int points) {
        teamPoints.putIfAbsent(championshipTeam, 0);
        teamPoints.put(championshipTeam, teamPoints.get(championshipTeam) + points);
    }

    private int getPoints(int num) {
        if (num == 1) {
            return 60;
        }
        if (num == 2) {
            return 50;
        }
        if (num == 3) {
            return 40;
        }
        if (num == 4) {
            return 30;
        }
        if (num == 5) {
            return 20;
        }

        return 10;
    }

    public void handleTeamCompleteTask(GameTask gameTask, ChampionshipTeam championshipTeam, Player player) {
        List<ChampionshipTeam> completeChampionshipTeams = getCompleteTeams(gameTask);
        int num = completeChampionshipTeams.size();
        if (championshipTeam != null) {
            if (!completeChampionshipTeams.contains(championshipTeam)) {
                int points = getPoints(num + 1);
                addCompleteTeams(gameTask, championshipTeam);
                addPointsToTeam(championshipTeam, points);

                if (player != null)
                    playerPoints.put(player.getUniqueId(), playerPoints.getOrDefault(player.getUniqueId(), 0) + 20);

                String[] messages = MessageConfig.BINGO_TASK_COMPLETE.split("%team%");
                messages[1] = messages[1]
                        .replace("%points%", String.valueOf(points));

                String[] finalMessages = messages[1].split("%task%");
                TextComponent textComponent = new TextComponent(messages[0]);
                TextComponent teamComponent = new TextComponent(championshipTeam.getName());
                textComponent.setColor(Utils.toBungeeChatColor(championshipTeam.getColorName()));
                textComponent.addExtra(teamComponent);
                textComponent.addExtra(new TextComponent(finalMessages[0]));
                textComponent.addExtra(new TranslatableComponent(gameTask.material().getItemTranslationKey()));
                textComponent.addExtra(finalMessages[1]);

                Utils.sendMessageToAllSpigotPlayers(textComponent);
            }
            if (num == 4) {
                String[] messages = MessageConfig.BINGO_TASK_EXPIRED.split("%task%");
                TextComponent textComponent = new TextComponent(messages[0]);
                textComponent.addExtra(new TranslatableComponent(gameTask.material().getItemTranslationKey()));
                textComponent.addExtra(messages[1]);
                gameTask.setVoided(true);

                Utils.sendMessageToAllSpigotPlayers(textComponent);
            }
        }
    }

    public int getMaterialPoints(Material material) {
        return getPoints(bingoTaskCompleteLists.getOrDefault(material, Collections.emptyList()).size());
    }

    private void addCompleteTeams(GameTask gameTask, ChampionshipTeam championshipTeam) {
        List<ChampionshipTeam> championshipTeams = getCompleteTeams(gameTask);
        if (!championshipTeams.contains(championshipTeam))
            championshipTeams.add(championshipTeam);
    }

    private List<ChampionshipTeam> getCompleteTeams(GameTask gameTask) {
        if (!bingoTaskCompleteLists.containsKey(gameTask.material())) {
            bingoTaskCompleteLists.put(gameTask.material(), new ArrayList<>());
        }
        return bingoTaskCompleteLists.get(gameTask.material());
    }
}
