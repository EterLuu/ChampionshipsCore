package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ParkourTagArea extends BaseTeamArea {
    @Getter
    private final Map<UUID, Integer> playerSurviveTimes = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    @Setter
    @Getter
    private UUID rightAreaChaser;
    @Setter
    @Getter
    private UUID leftAreaChaser;
    private int rightTeamSurviveTime;
    private int leftTeamSurviveTime;

    @Override
    public void resetArea() {
        cleanDroppedItems();

        rightTeamSurviveTime = -1;
        leftTeamSurviveTime = -1;

        rightAreaChaser = null;
        leftAreaChaser = null;

        playerSurviveTimes.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    public ParkourTagArea(ChampionshipsCore plugin, ParkourTagConfig parkourTagConfig) {
        super(plugin, GameTypeEnum.ParkourTag, new ParkourTagHandler(plugin), parkourTagConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setParkourTagArea(this);
        getGameHandler().register();

        rightTeamSurviveTime = -1;
        leftTeamSurviveTime = -1;

        rightAreaChaser = null;
        leftAreaChaser = null;

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public boolean tryStartGame(ChampionshipTeam rightChampionshipTeam, ChampionshipTeam leftChampionshipTeam) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        this.rightChampionshipTeam = rightChampionshipTeam;
        this.leftChampionshipTeam = leftChampionshipTeam;
        startGamePreparation();
        return true;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        rightChampionshipTeam.teleportAllPlayers(getGameConfig().getRightPreSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(getGameConfig().getLeftPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        cleanInventoryForAllGamePlayers();
        setHealthForAllGamePlayers(20);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_START_PREPARATION_TITLE, MessageConfig.PARKOUR_TAG_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        String message = MessageConfig.PARKOUR_TAG_BECOME_CHASER;

        if (rightAreaChaser == null) {
            rightAreaChaser = plugin.getGameManager().getParkourTagManager().getTeamChaser(rightChampionshipTeam);
            rightChampionshipTeam.sendMessageToAll(message
                    .replace("%player%", rightChampionshipTeam.getColoredColor() + Bukkit.getOfflinePlayer(rightAreaChaser).getName())
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - plugin.getGameManager().getParkourTagManager().getChaserTimes(rightAreaChaser) - 1))
            );
        }
        if (leftAreaChaser == null) {
            leftAreaChaser = plugin.getGameManager().getParkourTagManager().getTeamChaser(leftChampionshipTeam);
            leftChampionshipTeam.sendMessageToAll(message
                    .replace("%player%", leftChampionshipTeam.getColoredColor() + Bukkit.getOfflinePlayer(leftAreaChaser).getName())
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - plugin.getGameManager().getParkourTagManager().getChaserTimes(leftAreaChaser) - 1))
            );
        }

        plugin.getGameManager().getParkourTagManager().addChaserTimes(rightAreaChaser);
        plugin.getGameManager().getParkourTagManager().addChaserTimes(leftAreaChaser);

        // Set survive time for those player not online
        setSurviveTimeToZero(rightChampionshipTeam, rightAreaChaser);
        setSurviveTimeToZero(leftChampionshipTeam, leftAreaChaser);

        timer = getGameConfig().getTimer() + 5;

        // Right area
        {
            Player player = Bukkit.getPlayer(rightAreaChaser);
            if (player != null) {
                player.teleport(getGameConfig().getRightAreaChaserSpawnPoint());
            }

            Iterator<String> escapeeSpawnPointsI = getGameConfig().getRightAreaEscapeeSpawnPoints().iterator();
            for (Player rightAreaEscapee : getRightAreaEscapees()) {
                if (escapeeSpawnPointsI.hasNext())
                    rightAreaEscapee.teleport(Utils.getLocation(escapeeSpawnPointsI.next()));
                else {
                    escapeeSpawnPointsI = getGameConfig().getRightAreaEscapeeSpawnPoints().iterator();
                    rightAreaEscapee.teleport(Utils.getLocation(escapeeSpawnPointsI.next()));
                }
            }
        }

        // Left area
        {
            Player player = Bukkit.getPlayer(leftAreaChaser);
            if (player != null) {
                player.teleport(getGameConfig().getLeftAreaChaserSpawnPoint());
            }

            Iterator<String> escapeeSpawnPointsI = getGameConfig().getLeftAreaEscapeeSpawnPoints().iterator();
            for (Player leftAreaEscapee : getLeftAreaEscapees()) {
                if (escapeeSpawnPointsI.hasNext())
                    leftAreaEscapee.teleport(Utils.getLocation(escapeeSpawnPointsI.next()));
                else {
                    escapeeSpawnPointsI = getGameConfig().getLeftAreaEscapeeSpawnPoints().iterator();
                    leftAreaEscapee.teleport(Utils.getLocation(escapeeSpawnPointsI.next()));
                }
            }
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_SOON_TITLE, MessageConfig.PARKOUR_TAG_GAME_START_SOON_SUBTITLE);

        giveItemToAllGamePlayers();
        setHealthForAllGamePlayers(20);

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.PARKOUR_TAG_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == getGameConfig().getTimer()) {

                updateTeamSurviveTimes();

                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_TITLE, MessageConfig.PARKOUR_TAG_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.PARKOUR_TAG_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                changeLevelForAllGamePlayers(timer);
                updateTeamSurviveTimes();
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    private void setSurviveTimeToZero(ChampionshipTeam championshipTeam, UUID areaChaser) {
        for (UUID uuid : championshipTeam.getOfflineMembers()) {
            if (!uuid.equals(areaChaser)) {
                playerSurviveTimes.put(uuid, 0);
                plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Player " + Bukkit.getOfflinePlayer(uuid).getName() + " (" + uuid + ") not online, set survive time 0.");
            }
        }
    }

    protected void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        calculatePoints();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_END_TITLE, MessageConfig.PARKOUR_TAG_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        rightChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        leftChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        clearEffectsForAllGamePlayers();

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {

        // More than zero player survived, escapees gain 15 points
        // Caught all escapees, chaser gain 7 points per 10s
        int rightTeamSurvivor = rightChampionshipTeam.getMembers().size() - 1;
        int leftTeamSurvivor = leftChampionshipTeam.getMembers().size() - 1;

        for (UUID uuid : playerSurviveTimes.keySet()) {
            if (getRightTeamEscapees().contains(uuid))
                rightTeamSurvivor -= 1;
            if (getLeftTeamEscapees().contains(uuid))
                leftTeamSurvivor -= 1;
        }

        if (rightTeamSurviveTime == -1) {
            rightTeamSurviveTime = getGameConfig().getTimer();
        }
        if (leftTeamSurviveTime == -1) {
            leftTeamSurviveTime = getGameConfig().getTimer();
        }

        if (rightTeamSurvivor > 0) {
            addPlayerPointsToAllTeamMembers(rightChampionshipTeam, 15);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + rightChampionshipTeam.getName() + " has survivor(s), added 15 points");
        } else {
            int points = 7 * (getGameConfig().getTimer() - rightTeamSurviveTime) / 10;
            addPlayerPoints(leftAreaChaser, points);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + rightChampionshipTeam.getName() + " has no survivor, added " + points + " to " + (getLeftAreaChaserPlayer() == null ? "none" : getLeftAreaChaserPlayer().getName()));
        }
        if (leftTeamSurvivor > 0) {
            addPlayerPointsToAllTeamMembers(leftChampionshipTeam, 15);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + leftChampionshipTeam.getName() + " has survivor(s), added 15 points");
        } else {
            int points = 7 * (getGameConfig().getTimer() - leftTeamSurviveTime) / 10;
            addPlayerPoints(rightAreaChaser, points);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + leftChampionshipTeam.getName() + " has no survivor, added " + points + " to " + (getRightAreaChaserPlayer() == null ? "none" : getRightAreaChaserPlayer().getName()));
        }

        // Escapees gain 2 per 10s
        for (UUID uuid : getRightTeamEscapees()) {
            playerSurviveTimes.putIfAbsent(uuid, getGameConfig().getTimer());
        }
        for (UUID uuid : getLeftTeamEscapees()) {
            playerSurviveTimes.putIfAbsent(uuid, getGameConfig().getTimer());
        }

        for (Map.Entry<UUID, Integer> surviveEntry : playerSurviveTimes.entrySet()) {
            int points = surviveEntry.getValue() / 10 * 2;
            addPlayerPoints(surviveEntry.getKey(), points);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Player " + Bukkit.getOfflinePlayer(surviveEntry.getKey()).getName() + " survived " + surviveEntry.getValue() + "s, get points " + points);
        }

        // The team that successfully kills all the survivors faster earns 30 points each
        if (rightTeamSurviveTime > leftTeamSurviveTime) {
            addPlayerPointsToAllTeamMembers(rightChampionshipTeam, 30);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + rightChampionshipTeam.getName() + " survived longer, get points 30");
        } else if (rightTeamSurviveTime < leftTeamSurviveTime) {
            addPlayerPointsToAllTeamMembers(leftChampionshipTeam, 30);
            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + leftChampionshipTeam.getName() + " survived longer, get points 30");
        }

        addPlayerPointsToDatabase();

        String message = MessageConfig.PARKOUR_TAG_SHOW_POINTS
                .replace("%team%", rightChampionshipTeam.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(rightChampionshipTeam)))
                .replace("%rival%", leftChampionshipTeam.getColoredName())
                .replace("%rival_points%", String.valueOf(getTeamPoints(leftChampionshipTeam)));

        sendMessageToAllGamePlayersInActionbarAndMessage(message);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {

    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (!player.getUniqueId().equals(getLeftAreaChaser()) && !player.getUniqueId().equals(getRightAreaChaser())) {

            String message = MessageConfig.PARKOUR_TAG_PLAYER_LEAVE
                    .replace("%player%", player.getName());

            sendMessageToPlayerAreaPlayers(player, message);
            getPlayerSurviveTimes().put(player.getUniqueId(), getGameConfig().getTimer() - timer);
            updateTeamSurviveTimes();
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPreSpawnLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (player.getUniqueId().equals(leftAreaChaser) || player.getUniqueId().equals(rightAreaChaser)) {
                player.setGameMode(GameMode.ADVENTURE);
                return;
            }
        }

        player.teleport(getGameConfig().getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            if (championshipTeam.equals(rightChampionshipTeam)) {
                player.teleport(getGameConfig().getRightPreSpawnPoint());
                player.setGameMode(GameMode.ADVENTURE);
                return;
            }
            if (championshipTeam.equals(leftChampionshipTeam)) {
                player.teleport(getGameConfig().getLeftPreSpawnPoint());
                player.setGameMode(GameMode.ADVENTURE);
                return;
            }
        }

        player.teleport(getGameConfig().getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void updateTeamSurviveTimes() {
        int rightTeamSurvivor = rightChampionshipTeam.getMembers().size() - 1;
        int leftTeamSurvivor = leftChampionshipTeam.getMembers().size() - 1;

        for (UUID uuid : playerSurviveTimes.keySet()) {
            if (getRightTeamEscapees().contains(uuid))
                rightTeamSurvivor -= 1;
            if (getLeftTeamEscapees().contains(uuid))
                leftTeamSurvivor -= 1;
        }

        String message = MessageConfig.PARKOUR_TAG_WHOLE_TEAM_WAS_KILLED;

        if (rightTeamSurvivor == 0 && rightTeamSurviveTime == -1) {
            rightTeamSurviveTime = getGameConfig().getTimer() - timer;

            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + rightChampionshipTeam.getName() + " survived time: " + rightTeamSurviveTime);
            sendMessageToLeftArea(message.replace("%team%", rightChampionshipTeam.getColoredName()));
        }
        if (leftTeamSurvivor == 0 && leftTeamSurviveTime == -1) {
            leftTeamSurviveTime = getGameConfig().getTimer() - timer;

            plugin.getLogger().log(Level.INFO, GameTypeEnum.ParkourTag + ", " + getGameConfig().getAreaName() + ", Team " + leftChampionshipTeam.getName() + " survived time: " + leftTeamSurviveTime);
            sendMessageToRightArea(message.replace("%team%", leftChampionshipTeam.getColoredName()));
        }
    }

    public void giveItemToAllGamePlayers() {
//        ItemStack chaserItem = new ItemStack(Material.FEATHER);
//        ItemMeta chaserItemMeta = chaserItem.getItemMeta();
//        if (chaserItemMeta != null) {
//            chaserItemMeta.setDisplayName(MessageConfig.PARKOUR_TAG_KITS_FEATHER);
//        }
//        chaserItem.setItemMeta(chaserItemMeta);
//
//        Player left = Bukkit.getPlayer(leftAreaChaser);
//        Player right = Bukkit.getPlayer(rightAreaChaser);
//        if (left != null)
//            left.getInventory().setItem(0, chaserItem.clone());
//        if (right != null)
//            right.getInventory().setItem(0, chaserItem.clone());

        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta clockItemMeta = clock.getItemMeta();
        if (clockItemMeta != null) {
            clockItemMeta.setDisplayName(MessageConfig.PARKOUR_TAG_KITS_CLOCK);
        }
        clock.setItemMeta(clockItemMeta);

        for (Player player : getRightAreaEscapees()) {
            player.getInventory().setItem(0, clock.clone());
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, getGameConfig().getTimer() * 20 + 100, 0));
        }
        for (Player player : getLeftAreaEscapees()) {
            player.getInventory().setItem(0, clock.clone());
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, getGameConfig().getTimer() * 20 + 100, 0));
        }
    }

    public Player getRightAreaChaserPlayer() {
        return Bukkit.getPlayer(rightAreaChaser);
    }

    public Player getLeftAreaChaserPlayer() {
        return Bukkit.getPlayer(leftAreaChaser);
    }

    public void setRightAreaEscapeesClockCoolDown() {
        for (Player player : getRightAreaEscapees()) {
            player.setCooldown(Material.CLOCK, 200);
        }
    }

    public void setLeftAreaEscapeesClockCoolDown() {
        for (Player player : getLeftAreaEscapees()) {
            player.setCooldown(Material.CLOCK, 200);
        }
    }

    public List<UUID> getRightTeamEscapees() {
        List<UUID> escapees = new ArrayList<>();
        for (UUID uuid : rightChampionshipTeam.getMembers()) {
            if (!uuid.equals(rightAreaChaser))
                escapees.add(uuid);
        }

        return escapees;
    }

    public List<UUID> getLeftTeamEscapees() {
        List<UUID> escapees = new ArrayList<>();
        for (UUID uuid : leftChampionshipTeam.getMembers()) {
            if (!uuid.equals(leftAreaChaser))
                escapees.add(uuid);
        }

        return escapees;
    }

    public List<Player> getRightAreaEscapees() {
        List<Player> escapees = new ArrayList<>();
        for (Player leftPlayer : leftChampionshipTeam.getOnlinePlayers()) {
            if (!leftPlayer.getUniqueId().equals(leftAreaChaser))
                escapees.add(leftPlayer);
        }

        return escapees;
    }

    public List<Player> getLeftAreaEscapees() {
        List<Player> escapees = new ArrayList<>();
        for (Player rightPlayer : rightChampionshipTeam.getOnlinePlayers()) {
            if (!rightPlayer.getUniqueId().equals(rightAreaChaser))
                escapees.add(rightPlayer);
        }

        return escapees;
    }

    public void sendMessageToPlayerAreaPlayers(@NotNull Player player, @NotNull String message) {
        if (player.getUniqueId().equals(rightAreaChaser)) {
            sendMessageToRightArea(message);
        }
        if (player.getUniqueId().equals(leftAreaChaser)) {
            sendMessageToLeftArea(message);
        }
    }

    public void sendMessageToRightArea(@NotNull String message) {
        for (Player player : getRightAreaEscapees()) {
            player.sendMessage(message);
        }
        Player chaser = getRightAreaChaserPlayer();
        if (chaser != null)
            chaser.sendMessage(message);

        sendMessageToAllSpectators(message);
    }

    public void sendMessageToLeftArea(@NotNull String message) {
        for (Player player : getLeftAreaEscapees()) {
            player.sendMessage(message);
        }
        Player chaser = getLeftAreaChaserPlayer();
        if (chaser != null)
            chaser.sendMessage(message);

        sendMessageToAllSpectators(message);
    }

    @Override
    public ParkourTagConfig getGameConfig() {
        return (ParkourTagConfig) gameConfig;
    }

    @Override
    public ParkourTagHandler getGameHandler() {
        return (ParkourTagHandler) gameHandler;
    }
}
