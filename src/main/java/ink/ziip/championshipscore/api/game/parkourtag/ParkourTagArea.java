package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.format.TextDecoration;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Parkour Tag arena hosting several independent team-vs-team matches in parallel, one per stamped arena copy
 * (see {@link ParkourTagLayout}). Like Battle Box it now extends {@link BaseSingleTeamArea} (a team-list
 * area) instead of the two-team {@code BaseTeamArea}, and a {@link ParkourTagMatch} list carries the per-copy
 * geometry and live state (chasers, survive times). The championship's Swiss scheduler feeds one round's
 * pairings in via {@link #tryStartMatches(List)}. Chaser-rotation state stays in {@link ParkourTagManager}
 * (keyed per team/player, not per area), so it is untouched.
 */
public class ParkourTagArea extends BaseSingleTeamArea {
    @Getter
    private volatile int timer;
    private volatile ScheduledTask startGamePreparationTask;
    private volatile ScheduledTask startGameProgressTask;

    private final List<ParkourTagMatch> matches = new CopyOnWriteArrayList<>();
    private final Map<UUID, ParkourTagMatch> matchByPlayer = new ConcurrentHashMap<>();

    public ParkourTagArea(ChampionshipsCore plugin, ParkourTagConfig parkourTagConfig) {
        super(plugin, GameTypeEnum.ParkourTag, new ParkourTagHandler(plugin), parkourTagConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setParkourTagArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public synchronized boolean tryStartMatches(@NotNull List<TwoVTwoVector> pairs) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        matches.clear();
        matchByPlayer.clear();
        int copyIndex = 0;
        for (TwoVTwoVector pair : pairs) {
            ParkourTagMatch match = new ParkourTagMatch(copyIndex, pair.getTeamOne(), pair.getTeamTwo(), getGameConfig());
            matches.add(match);
            gameTeams.add(pair.getTeamOne());
            gameTeams.add(pair.getTeamTwo());
            gamePlayers.addAll(pair.getTeamOne().getMembers());
            gamePlayers.addAll(pair.getTeamTwo().getMembers());
            for (UUID uuid : pair.getTeamOne().getMembers()) matchByPlayer.put(uuid, match);
            for (UUID uuid : pair.getTeamTwo().getMembers()) matchByPlayer.put(uuid, match);
            copyIndex++;
        }

        startGamePreparation();
        return true;
    }

    @Nullable
    public ParkourTagMatch matchOf(@NotNull Player player) {
        return matchByPlayer.get(player.getUniqueId());
    }

    @Nullable
    public ParkourTagMatch matchAt(@NotNull Location location) {
        for (ParkourTagMatch match : matches) {
            if (match.isInArea(location)) return match;
        }
        return null;
    }

    private int elapsed() {
        return getGameConfig().getTimer() - timer;
    }

    @Override
    public void resetArea() {
        cleanDroppedItems();
        matches.clear();
        matchByPlayer.clear();
        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        for (ParkourTagMatch match : matches) {
            match.getRight().teleportAllPlayers(match.getRightPreSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftPreSpawn());
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        createBossBar("chaser", MessageConfig.PARKOUR_TAG_BOSS_BAR_CHASER, BarColor.RED, BarStyle.SOLID);
        createBossBar("escaper", MessageConfig.PARKOUR_TAG_BOSS_BAR_ESCAPER, BarColor.WHITE, BarStyle.SOLID);

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_START_PREPARATION_TITLE, MessageConfig.PARKOUR_TAG_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(() -> {
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
        for (ParkourTagMatch match : matches) {
            assignChasers(match);
        }

        timer = getGameConfig().getTimer() + 5;

        for (ParkourTagMatch match : matches) {
            spawnMatch(match);
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_SOON_TITLE, MessageConfig.PARKOUR_TAG_GAME_START_SOON_SUBTITLE);

        resetPlayerHealthFoodEffectLevelInventory();

        for (ParkourTagMatch match : matches) {
            giveItemToMatch(match);
        }

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(() -> {
            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.PARKOUR_TAG_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                for (ParkourTagMatch match : matches) updateAndAnnounce(match);
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_START_TITLE, MessageConfig.PARKOUR_TAG_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.PARKOUR_TAG_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                changeLevelForAllGamePlayers(timer);
                for (ParkourTagMatch match : matches) updateAndAnnounce(match);
                endGame();
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    /** Assigns each team's chaser for a match (honouring a prep-time choice), rotating via the manager. */
    private void assignChasers(ParkourTagMatch match) {
        ParkourTagManager manager = plugin.getGameManager().getParkourTagManager();
        String message = MessageConfig.PARKOUR_TAG_BECOME_CHASER;

        if (match.getRightAreaChaser() == null) {
            match.setRightAreaChaser(manager.getTeamChaser(match.getRight()));
            Player player = Bukkit.getPlayer(match.getRightAreaChaser());
            if (player != null) addBossBarPlayer("chaser", player);
            match.getRight().sendMessageToAll(message
                    .replace("%player%", match.getRight().getColoredColor() + playerManager.getPlayerName(match.getRightAreaChaser()))
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(match.getRightAreaChaser()) - 1)));
        }
        if (match.getLeftAreaChaser() == null) {
            match.setLeftAreaChaser(manager.getTeamChaser(match.getLeft()));
            Player player = Bukkit.getPlayer(match.getLeftAreaChaser());
            if (player != null) addBossBarPlayer("chaser", player);
            match.getLeft().sendMessageToAll(message
                    .replace("%player%", match.getLeft().getColoredColor() + playerManager.getPlayerName(match.getLeftAreaChaser()))
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(match.getLeftAreaChaser()) - 1)));
        }

        manager.addChaserTimes(match.getRightAreaChaser());
        manager.addChaserTimes(match.getLeftAreaChaser());

        setSurviveTimeToZero(match, match.getRight(), match.getRightAreaChaser());
        setSurviveTimeToZero(match, match.getLeft(), match.getLeftAreaChaser());
    }

    /** Teleports a match's chasers/escapees into their cages and shows the escaper boss bar. */
    private void spawnMatch(ParkourTagMatch match) {
        Player rightChaser = match.getRightAreaChaserPlayer();
        if (rightChaser != null) rightChaser.teleportAsync(match.getRightAreaChaserSpawn());
        teleportEscapees(match.getRightAreaEscapees(), match.getRightAreaEscapeeSpawns());

        Player leftChaser = match.getLeftAreaChaserPlayer();
        if (leftChaser != null) leftChaser.teleportAsync(match.getLeftAreaChaserSpawn());
        teleportEscapees(match.getLeftAreaEscapees(), match.getLeftAreaEscapeeSpawns());
    }

    private void teleportEscapees(List<Player> escapees, List<Location> spawns) {
        if (spawns.isEmpty()) return;
        int i = 0;
        for (Player escapee : escapees) {
            addBossBarPlayer("escaper", escapee);
            escapee.teleportAsync(spawns.get(i % spawns.size()));
            i++;
        }
    }

    private void setSurviveTimeToZero(ParkourTagMatch match, ChampionshipTeam team, UUID chaser) {
        for (UUID uuid : team.getOfflineMembers()) {
            if (!uuid.equals(chaser)) {
                match.getPlayerSurviveTimes().put(uuid, 0);
            }
        }
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        if (ThreadLocalRandom.current().nextInt(2) == 0)
            return getGameConfig().getLeftAreaChaserSpawnPoint();
        return getGameConfig().getRightAreaChaserSpawnPoint();
    }

    @Override
    public synchronized void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        setGameStageEnum(GameStageEnum.END);

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        for (ParkourTagMatch match : matches) {
            calculatePoints(match);
        }
        addPlayerPointsToDatabase();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.PARKOUR_TAG_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.PARKOUR_TAG_GAME_END_TITLE, MessageConfig.PARKOUR_TAG_GAME_END_SUBTITLE);

        teleportAllPlayers(getLobbyLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        resetGame();
    }

    /** Settles one match's points (survivors, chaser clears, longest-survival bonus) and announces them. */
    private void calculatePoints(ParkourTagMatch match) {
        ChampionshipTeam right = match.getRight();
        ChampionshipTeam left = match.getLeft();

        int rightTeamSurvivor = right.getMembers().size() - 1;
        int leftTeamSurvivor = left.getMembers().size() - 1;
        for (UUID uuid : match.getPlayerSurviveTimes().keySet()) {
            if (match.getRightTeamEscapees().contains(uuid)) rightTeamSurvivor -= 1;
            if (match.getLeftTeamEscapees().contains(uuid)) leftTeamSurvivor -= 1;
        }

        if (match.getRightTeamSurviveTime() == -1) match.setRightTeamSurviveTime(getGameConfig().getTimer());
        if (match.getLeftTeamSurviveTime() == -1) match.setLeftTeamSurviveTime(getGameConfig().getTimer());

        if (rightTeamSurvivor > 0) {
            addPlayerPointsToTeamEscapees(match, right, 20);
        } else {
            int points = 7 * (getGameConfig().getTimer() - match.getRightTeamSurviveTime()) / 10;
            addPlayerPoints(match.getLeftAreaChaser(), points);
        }
        if (leftTeamSurvivor > 0) {
            addPlayerPointsToTeamEscapees(match, left, 20);
        } else {
            int points = 7 * (getGameConfig().getTimer() - match.getLeftTeamSurviveTime()) / 10;
            addPlayerPoints(match.getRightAreaChaser(), points);
        }

        for (UUID uuid : match.getRightTeamEscapees()) match.getPlayerSurviveTimes().putIfAbsent(uuid, getGameConfig().getTimer());
        for (UUID uuid : match.getLeftTeamEscapees()) match.getPlayerSurviveTimes().putIfAbsent(uuid, getGameConfig().getTimer());

        for (Map.Entry<UUID, Integer> entry : match.getPlayerSurviveTimes().entrySet()) {
            addPlayerPoints(entry.getKey(), entry.getValue() / 10 * 2);
        }

        if (match.getRightTeamSurviveTime() > match.getLeftTeamSurviveTime()) {
            addPlayerPointsToAllTeamMembers(right, 30);
        } else if (match.getRightTeamSurviveTime() < match.getLeftTeamSurviveTime()) {
            addPlayerPointsToAllTeamMembers(left, 30);
        }

        String message = MessageConfig.PARKOUR_TAG_SHOW_POINTS
                .replace("%team%", right.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(right)))
                .replace("%rival%", left.getColoredName())
                .replace("%rival_points%", String.valueOf(getTeamPoints(left)));
        right.sendMessageToAll(message);
        left.sendMessageToAll(message);
    }

    private void addPlayerPointsToTeamEscapees(ParkourTagMatch match, ChampionshipTeam team, int points) {
        for (UUID uuid : team.getMembers()) {
            if (!uuid.equals(match.getRightAreaChaser()) && !uuid.equals(match.getLeftAreaChaser()))
                addPlayerPoints(uuid, points);
        }
    }

    @Override
    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> entry : playerPoints.entrySet()) {
            if (entry.getValue() == 0)
                continue;
            ParkourTagMatch match = matchByPlayer.get(entry.getKey());
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(entry.getKey());
            ChampionshipTeam rival = null;
            if (match != null && team != null) {
                rival = team.equals(match.getRight()) ? match.getLeft()
                        : team.equals(match.getLeft()) ? match.getRight() : null;
            }
            plugin.getRankManager().addPlayerPoints(entry.getKey(), rival, gameTypeEnum, gameConfig.getAreaName(), entry.getValue());
        }
    }

    private void giveItemToMatch(ParkourTagMatch match) {
        ItemStack feather = new ItemStack(Material.POTION);
        PotionMeta featherMeta = (PotionMeta) feather.getItemMeta();
        if (featherMeta != null) {
            featherMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2), true);
            featherMeta.displayName(Utils.toComponent(MessageConfig.PARKOUR_TAG_KITS_FEATHER)
                    .decoration(TextDecoration.ITALIC, false));
            feather.setItemMeta(featherMeta);
        }

        Player rightChaser = match.getRightAreaChaserPlayer();
        if (rightChaser != null) {
            scheduler.runEntity(rightChaser, () -> rightChaser.getInventory().setItem(0, feather.clone()));
        }
        Player leftChaser = match.getLeftAreaChaserPlayer();
        if (leftChaser != null) {
            scheduler.runEntity(leftChaser, () -> leftChaser.getInventory().setItem(0, feather.clone()));
        }

        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clock.getItemMeta();
        if (clockMeta != null) {
            clockMeta.displayName(Utils.toComponent(MessageConfig.PARKOUR_TAG_KITS_CLOCK)
                    .decoration(TextDecoration.ITALIC, false));
            clock.setItemMeta(clockMeta);
        }

        int glowTicks = getGameConfig().getTimer() * 20 + 100;
        for (Player player : match.getRightAreaEscapees()) {
            scheduler.runEntity(player, () -> {
                player.getInventory().setItem(0, clock.clone());
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
            });
        }
        for (Player player : match.getLeftAreaEscapees()) {
            scheduler.runEntity(player, () -> {
                player.getInventory().setItem(0, clock.clone());
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
            });
        }
    }

    // ── handler-facing logic (routed to the player's match) ──────────────────────────────────────

    /** Prep-time chaser pick from a sign click; validates rotation and records it on the player's match. */
    public synchronized void chooseChaser(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        ParkourTagMatch match = matchOf(player);
        if (match == null) return;
        ParkourTagManager manager = plugin.getGameManager().getParkourTagManager();
        if (!manager.canBeChaser(uuid)) {
            player.sendMessage(MessageConfig.PARKOUR_TAG_BECOME_CHASER_FAILED);
            return;
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (team == null) return;
        String message = MessageConfig.PARKOUR_TAG_BECOME_CHASER
                .replace("%player%", player.getName())
                .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(uuid)));
        if (team.equals(match.getRight())) {
            match.setRightAreaChaser(uuid);
            match.getRight().sendMessageToAll(message);
        } else if (team.equals(match.getLeft())) {
            match.setLeftAreaChaser(uuid);
            match.getLeft().sendMessageToAll(message);
        }
    }

    /** Clock use: glows the opposing chaser and cooldowns the user's team's escapee clocks. */
    public void useClock(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        if (match == null) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        if (!plugin.getGameManager().getParkourTagManager().canUseClock(team)) {
            player.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK_FAILED);
            return;
        }

        Player chaser = null;
        if (team.equals(match.getRight())) {
            chaser = match.getLeftAreaChaserPlayer();
            for (Player escapee : match.getLeftAreaEscapees()) {
                scheduler.runEntity(escapee, () -> {
                    escapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK);
                    escapee.setCooldown(Material.CLOCK, 200);
                });
            }
        } else if (team.equals(match.getLeft())) {
            chaser = match.getRightAreaChaserPlayer();
            for (Player escapee : match.getRightAreaEscapees()) {
                scheduler.runEntity(escapee, () -> {
                    escapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK);
                    escapee.setCooldown(Material.CLOCK, 200);
                });
            }
        }
        if (chaser != null) {
            Player target = chaser;
            scheduler.runEntity(target,
                    () -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0)));
        }
        plugin.getGameManager().getParkourTagManager().setClockUsedTimes(team);
    }

    /** Resolves player-vs-player damage. Returns true if the event should be cancelled. */
    public boolean handleChaserDamage(@NotNull Player victim, @NotNull Player assailant) {
        ParkourTagMatch match = matchOf(victim);
        if (match == null) return false;
        if (match.getRightAreaEscapees().contains(assailant) || match.getLeftAreaEscapees().contains(assailant))
            return true;

        ChampionshipTeam victimTeam = plugin.getTeamManager().getTeamByPlayer(victim);
        ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
        if (victimTeam == null || assailantTeam == null) return false;
        if (victimTeam.equals(assailantTeam)) return false;

        if (assailant.getUniqueId().equals(match.getLeftAreaChaser()) || assailant.getUniqueId().equals(match.getRightAreaChaser())) {
            scheduler.runEntity(victim, () -> victim.setGameMode(GameMode.SPECTATOR));
            assailant.playSound(assailant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);
            addPlayerPoints(assailant.getUniqueId(), 6);

            String message = MessageConfig.PARKOUR_TAG_CATCH_PLAYER
                    .replace("%player%", victimTeam.getColoredColor() + victim.getName())
                    .replace("%chaser%", assailantTeam.getColoredColor() + assailant.getName());
            sendMessageToChaserArea(match, assailant.getUniqueId(), message);

            match.getPlayerSurviveTimes().put(victim.getUniqueId(), elapsed());
            updateAndAnnounce(match);
            return false;
        }
        return true;
    }

    /** A non-chaser leaving counts as caught; record their survive time and re-check the team. */
    public void handleEscapeeQuit(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        if (match == null || match.isChaser(player)) return;
        match.getPlayerSurviveTimes().put(player.getUniqueId(), elapsed());
        updateAndAnnounce(match);
    }

    /** Recomputes a match's team survival and announces any team that was just fully caught. */
    private void updateAndAnnounce(ParkourTagMatch match) {
        for (ChampionshipTeam caught : match.updateTeamSurviveTimes(elapsed())) {
            String message = MessageConfig.PARKOUR_TAG_WHOLE_TEAM_WAS_KILLED.replace("%team%", caught.getColoredName());
            // A team's escapees play in the OPPOSITE cage, so announce there.
            if (caught.equals(match.getRight())) sendMessageToLeftArea(match, message);
            else sendMessageToRightArea(match, message);
        }
    }

    private void sendMessageToChaserArea(ParkourTagMatch match, UUID chaser, String message) {
        if (chaser.equals(match.getRightAreaChaser())) sendMessageToRightArea(match, message);
        if (chaser.equals(match.getLeftAreaChaser())) sendMessageToLeftArea(match, message);
    }

    private void sendMessageToRightArea(ParkourTagMatch match, String message) {
        for (Player player : match.getRightAreaEscapees()) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        }
        Player chaser = match.getRightAreaChaserPlayer();
        if (chaser != null) scheduler.runEntity(chaser, () -> chaser.sendMessage(message));
        sendMessageToAllSpectators(message);
    }

    private void sendMessageToLeftArea(ParkourTagMatch match, String message) {
        for (Player player : match.getLeftAreaEscapees()) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        }
        Player chaser = match.getLeftAreaChaserPlayer();
        if (chaser != null) scheduler.runEntity(chaser, () -> chaser.sendMessage(message));
        sendMessageToAllSpectators(message);
    }

    /** True when {@code player} is a chaser in their match (for placeholders / role display). */
    public boolean isChaser(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        return match != null && match.isChaser(player);
    }

    /** True when {@code player} is an escapee in their match (handler friendly-fire check). */
    public boolean isEscapee(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        return match != null && match.isEscapee(player);
    }

    public int getAreaEscapeesNums(@NotNull Location location) {
        ParkourTagMatch match = matchAt(location);
        return match == null ? 0 : match.getAreaEscapeesNums(location);
    }

    public int getAreaSurvivedEscapeesNums(@NotNull Location location) {
        ParkourTagMatch match = matchAt(location);
        return match == null ? 0 : match.getAreaSurvivedEscapeesNums(location);
    }

    @Override
    public void cleanDroppedItems() {
        for (ParkourTagMatch match : matches) {
            World world = match.getSpectatorSpawn() != null ? match.getSpectatorSpawn().getWorld() : null;
            if (world == null) continue;
            cleanEntities(world, match.getLeftAreaBox(), Item.class);
            cleanEntities(world, match.getRightAreaBox(), Item.class);
        }
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName()))
            return true;
        return matchAt(location) == null;
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player) || getGameStageEnum() != GameStageEnum.PROGRESS)
            return;
        if (!isChaser(player)) {
            sendMessageToAllGamePlayers(MessageConfig.PARKOUR_TAG_PLAYER_LEAVE.replace("%player%", player.getName()));
            handleEscapeeQuit(player);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player))
            return;

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPreSpawnLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS && isChaser(player)) {
            scheduler.runEntity(player, () -> player.setGameMode(GameMode.ADVENTURE));
            return;
        }

        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        ParkourTagMatch match = matchOf(player);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (match != null && team != null) {
            Location target = team.equals(match.getRight()) ? match.getRightPreSpawn()
                    : team.equals(match.getLeft()) ? match.getLeftPreSpawn() : null;
            if (target != null) {
                player.teleportAsync(target);
                scheduler.runEntity(player, () -> player.setGameMode(GameMode.ADVENTURE));
                return;
            }
        }
        player.teleportAsync(getSpectatorSpawnLocation());
        scheduler.runEntity(player, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    @Override
    public ParkourTagConfig getGameConfig() {
        return (ParkourTagConfig) gameConfig;
    }

    @Override
    public ParkourTagHandler getGameHandler() {
        return (ParkourTagHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return "parkourtag";
    }
}
