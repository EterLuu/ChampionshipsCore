package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.spatial.ReplicatedSpatialLayout;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.format.TextDecoration;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * One independently runnable Parkour Tag instance, permanently bound to one stamped map copy. Chaser
 * rotation remains map/game-wide state in {@link ParkourTagManager}; this object owns one two-team run.
 */
public class ParkourTagArea extends BasePairedGameInstance {
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;

    @Getter
    private final int copyIndex;
    private ParkourTagMatch match;
    // Teams that have spent their once-per-round wind charge this round; cleared on each preparation.
    private final Set<ChampionshipTeam> windChargeUsedTeams = ConcurrentHashMap.newKeySet();
    /** Chasers currently revealed by an Ender Eye; each entry owns its expiry task. */
    private final Map<UUID, BukkitTask> temporaryChaserGlowTasks = new HashMap<>();
    private boolean parkourTagGlowsActive;

    public ParkourTagArea(ChampionshipsCore plugin, ParkourTagConfig parkourTagConfig) {
        this(plugin, parkourTagConfig, 0, true);
    }

    ParkourTagArea(ChampionshipsCore plugin, ParkourTagConfig parkourTagConfig, int copyIndex,
                   boolean initializeConfig) {
        super(plugin, GameTypeEnum.ParkourTag, new ParkourTagHandler(plugin), parkourTagConfig);

        if (initializeConfig) {
            getGameConfig().initializeConfiguration(plugin.getFolder());
        }
        this.copyIndex = copyIndex;
        getGameHandler().setParkourTagArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public boolean tryStartGame(ChampionshipTeam right, ChampionshipTeam left) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        ParkourTagGeometry geometry;
        try {
            geometry = configuredGeometry();
        } catch (RuntimeException exception) {
            logGame(Level.WARNING, "启动", "地图配置尚未完成，无法创建实例几何");
            return false;
        }
        if (getGameConfig().getTimer() <= 0 || copyIndex >= getGameConfig().getCopyCount()
                || geometry.getRightPrepareSpot() == null || geometry.getLeftPrepareSpot() == null
                || geometry.getSpectatorSpawn() == null
                || geometry.getLeftZone().getChaserSpawn() == null
                || geometry.getRightZone().getChaserSpawn() == null
                || geometry.getRightChaserButton() == null
                || geometry.getLeftChaserButton() == null
                || geometry.getLeftZone().getEscapeeSpawns().isEmpty()
                || geometry.getRightZone().getEscapeeSpawns().isEmpty()) {
            logGame(Level.WARNING, "启动", "地图配置尚未完成，无法开始游戏");
            return false;
        }
        match = new ParkourTagMatch(copyIndex, right, left, geometry);
        return super.tryStartGame(right, left);
    }

    private ParkourTagGeometry configuredGeometry() {
        return configuredGeometry(copyIndex);
    }

    private ParkourTagGeometry configuredGeometry(int index) {
        return new ReplicatedSpatialLayout<>(ParkourTagGeometry.from(getGameConfig()),
                getGameConfig().getCopyGrid(), getGameConfig().getCopyCount()).geometry(index);
    }

    @Override
    protected Vector[] getCountdownBlockDisappearanceBounds() {
        Vector[] bounds = super.getCountdownBlockDisappearanceBounds();
        if (bounds == null) return null;
        Vector delta = getGameConfig().getCopyGrid().delta(copyIndex);
        return new Vector[]{bounds[0].add(delta), bounds[1].add(delta)};
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        if (match == null) return List.of();
        List<Location> locations = new ArrayList<>();
        locations.add(match.getRightPrepareSpot());
        locations.add(match.getLeftPrepareSpot());
        locations.add(match.getRightChaserButton());
        locations.add(match.getLeftChaserButton());
        locations.add(match.getSpectatorSpawn());
        locations.add(match.getLeftAreaChaserSpawn());
        locations.add(match.getRightAreaChaserSpawn());
        locations.addAll(match.getLeftAreaEscapeeSpawns());
        locations.addAll(match.getRightAreaEscapeeSpawns());
        return locations;
    }

    @Nullable
    public ParkourTagMatch matchOf(@NotNull Player player) {
        return match != null && match.contains(player) ? match : null;
    }

    /** The match currently hosted by this arena, including for spectator-facing presentation. */
    @Nullable
    public ParkourTagMatch currentMatch() {
        return match;
    }

    @Nullable
    public ParkourTagMatch matchAt(@NotNull Location location) {
        return match != null && match.isInArea(location) ? match : null;
    }

    private int elapsed() {
        return getGameConfig().getTimer() - timer;
    }

    @Override
    public void resetArea() {
        clearParkourTagGlows();
        cleanDroppedItems();
        match = null;
        windChargeUsedTeams.clear();
        startGamePreparationTask = null;
        startGameProgressTask = null;
    }

    @Override
    public void dispose() {
        clearParkourTagGlows();
        super.dispose();
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        windChargeUsedTeams.clear();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        if (match != null) {
            match.getRight().teleportAllPlayers(match.getRightPrepareSpot());
            match.getLeft().teleportAllPlayers(match.getLeftPrepareSpot());
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.PARKOUR_TAG_START_PREPARATION,
                MessageConfig.PARKOUR_TAG_START_PREPARATION_TITLE, MessageConfig.PARKOUR_TAG_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            showPreparationCountdown(timer);

            if (timer == 0) {
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
                startGameProgress();
                return;
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        if (match != null) {
            assignChasers(match);
            spawnMatch(match);
        }

        resetPlayerHealthFoodEffectLevelInventory();

        if (match != null) {
            giveItemToMatch(match);
        }

        startFinalCountdown(MessageConfig.PARKOUR_TAG_GAME_START_SOON_TITLE,
                MessageConfig.PARKOUR_TAG_GAME_START_TITLE, MessageConfig.PARKOUR_TAG_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        if (match != null) updateAndAnnounce(match);
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            String chasers = match == null ? "-"
                    : formatChaserName(match.getRightAreaChaser()) + " / "
                    + formatChaserName(match.getLeftAreaChaser());
            updateGameTimerBossBar(MessageConfig.PARKOUR_TAG_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer))
                    + " &#bababa• &#ededed追击者 &#fff566" + chasers,
                    timer, getGameConfig().getTimer());
        }, () -> {
            if (match != null) updateAndAnnounce(match);
            endGame();
        });
    }

    private String formatChaserName(UUID uuid) {
        return uuid == null ? "待定" : Utils.formatPlayerNameOnly(playerManager.getPlayerName(uuid));
    }

    /** Assigns each team's chaser for a match (honouring a prep-time choice), rotating via the manager. */
    private void assignChasers(ParkourTagMatch match) {
        ParkourTagManager manager = plugin.getGameManager().getParkourTagManager();
        String message = MessageConfig.PARKOUR_TAG_BECOME_CHASER;

        if (match.getRightAreaChaser() == null) {
            match.setRightAreaChaser(manager.getTeamChaser(match.getRight()));
            match.getRight().sendMessageToAll(message
                    .replace("%player%", Utils.formatPlayerName(match.getRightAreaChaser()))
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(match.getRightAreaChaser()) - 1)));
        }
        if (match.getLeftAreaChaser() == null) {
            match.setLeftAreaChaser(manager.getTeamChaser(match.getLeft()));
            match.getLeft().sendMessageToAll(message
                    .replace("%player%", Utils.formatPlayerName(match.getLeftAreaChaser()))
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(match.getLeftAreaChaser()) - 1)));
        }

        manager.addChaserTimes(match.getRightAreaChaser());
        manager.addChaserTimes(match.getLeftAreaChaser());

        setSurviveTimeToZero(match, match.getRight(), match.getRightAreaChaser());
        setSurviveTimeToZero(match, match.getLeft(), match.getLeftAreaChaser());
    }

    /** Teleports a match's chasers and escapees into their cages. */
    private void spawnMatch(ParkourTagMatch match) {
        Player rightChaser = match.getRightAreaChaserPlayer();
        if (rightChaser != null) rightChaser.teleport(match.getRightAreaChaserSpawn());
        teleportEscapees(match.getRightAreaEscapees(), match.getRightAreaEscapeeSpawns());

        Player leftChaser = match.getLeftAreaChaserPlayer();
        if (leftChaser != null) leftChaser.teleport(match.getLeftAreaChaserSpawn());
        teleportEscapees(match.getLeftAreaEscapees(), match.getLeftAreaEscapeeSpawns());
    }

    private void teleportEscapees(List<Player> escapees, List<Location> spawns) {
        if (spawns.isEmpty()) return;
        int i = 0;
        for (Player escapee : escapees) {
            escapee.teleport(spawns.get(i % spawns.size()));
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
        if (match != null) {
            return match.getSpectatorSpawn();
        }
        Location configured = ThreadLocalRandom.current().nextInt(2) == 0
                ? getGameConfig().getLeftAreaChaserSpawnPoint()
                : getGameConfig().getRightAreaChaserSpawnPoint();
        return getGameConfig().getCopyGrid().transform(copyIndex).apply(configured);
    }

    @Override
    public Location getAdminTeleportLocation() {
        Location configured = getGameConfig().getGameSpawnPoint();
        return configured != null ? configured : configuredGeometry(0).getSpectatorSpawn();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        // A forced stop can happen during preparation or the final countdown, before
        // chasers have been assigned.  That is not a played match and must not score.
        if (match != null && getGameStageEnum() == GameStageEnum.PROGRESS
                && match.getRightAreaChaser() != null && match.getLeftAreaChaser() != null)
            calculatePoints(match);
        addPlayerPointsToDatabase();

        clearParkourTagGlows();
        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.PARKOUR_TAG_GAME_END_TITLE, MessageConfig.PARKOUR_TAG_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        ChampionshipTeam right = getRightChampionshipTeam();
        ChampionshipTeam left = getLeftChampionshipTeam();
        if (right != null && left != null) {
            Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(right, left, this));
        }

        finishPostGameAfterEndEvent();
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
        sendMessageToAllSpectators(message);
    }

    private void addPlayerPointsToTeamEscapees(ParkourTagMatch match, ChampionshipTeam team, int points) {
        for (UUID uuid : team.getMembers()) {
            if (!uuid.equals(match.getRightAreaChaser()) && !uuid.equals(match.getLeftAreaChaser()))
                addPlayerPoints(uuid, points);
        }
    }

    private void giveItemToMatch(ParkourTagMatch match) {
        ItemStack feather = new ItemStack(Material.FEATHER);
        ItemMeta featherMeta = feather.getItemMeta();
        if (featherMeta != null) {
            featherMeta.displayName(Utils.toComponent(MessageConfig.PARKOUR_TAG_KITS_FEATHER)
                    .decoration(TextDecoration.ITALIC, false));
            feather.setItemMeta(featherMeta);
        }

        Player rightChaser = match.getRightAreaChaserPlayer();
        if (rightChaser != null) rightChaser.getInventory().setItem(0, feather.clone());
        Player leftChaser = match.getLeftAreaChaserPlayer();
        if (leftChaser != null) leftChaser.getInventory().setItem(0, feather.clone());

        ItemStack enderEye = new ItemStack(Material.ENDER_EYE);
        ItemMeta enderEyeMeta = enderEye.getItemMeta();
        if (enderEyeMeta != null) {
            enderEyeMeta.displayName(Utils.toComponent(MessageConfig.PARKOUR_TAG_KITS_ENDER_EYE)
                    .decoration(TextDecoration.ITALIC, false));
            enderEye.setItemMeta(enderEyeMeta);
        }

        ItemStack windCharge = new ItemStack(Material.WIND_CHARGE);
        ItemMeta windChargeMeta = windCharge.getItemMeta();
        if (windChargeMeta != null) {
            windChargeMeta.displayName(Utils.toComponent(MessageConfig.PARKOUR_TAG_KITS_WIND_CHARGE)
                    .decoration(TextDecoration.ITALIC, false));
            windCharge.setItemMeta(windChargeMeta);
        }

        for (Player player : match.getRightAreaEscapees()) {
            player.getInventory().setItem(0, enderEye.clone());
            player.getInventory().setItem(1, windCharge.clone());
        }
        for (Player player : match.getLeftAreaEscapees()) {
            player.getInventory().setItem(0, enderEye.clone());
            player.getInventory().setItem(1, windCharge.clone());
        }
        parkourTagGlowsActive = true;
        syncAllParkourTagGlows();
    }

    /**
     * Rebuilds every PKT glow relationship for this match. The two simultaneous chase zones are kept
     * deliberately separate: a zone's three escapees (and its temporarily revealed chaser) are visible
     * only to that zone's four players plus spectators attached to this copied match instance.
     */
    private void syncAllParkourTagGlows() {
        ParkourTagMatch current = match;
        if (current == null || !parkourTagGlowsActive) return;

        Set<Player> allViewers = allGlowViewers(current);
        Set<Player> rightViewers = rightAreaGlowViewers(current);
        Set<Player> leftViewers = leftAreaGlowViewers(current);

        for (Player target : current.getRightAreaEscapees()) {
            syncGlowTarget(target, allViewers, rightViewers);
        }
        for (Player target : current.getLeftAreaEscapees()) {
            syncGlowTarget(target, allViewers, leftViewers);
        }

        Player rightChaser = current.getRightAreaChaserPlayer();
        if (rightChaser != null) {
            syncGlowTarget(rightChaser, allViewers,
                    temporaryChaserGlowTasks.containsKey(rightChaser.getUniqueId())
                            ? rightViewers : Set.of());
        }
        Player leftChaser = current.getLeftAreaChaserPlayer();
        if (leftChaser != null) {
            syncGlowTarget(leftChaser, allViewers,
                    temporaryChaserGlowTasks.containsKey(leftChaser.getUniqueId())
                            ? leftViewers : Set.of());
        }
    }

    private void syncGlowForViewer(@NotNull Player viewer) {
        ParkourTagMatch current = match;
        if (current == null || !parkourTagGlowsActive) return;

        boolean spectator = isSpectator(viewer);
        boolean rightViewer = spectator || isRightAreaPlayer(current, viewer);
        boolean leftViewer = spectator || isLeftAreaPlayer(current, viewer);

        for (Player target : current.getRightAreaEscapees()) {
            setViewerGlow(target, viewer, rightViewer);
        }
        for (Player target : current.getLeftAreaEscapees()) {
            setViewerGlow(target, viewer, leftViewer);
        }
        Player rightChaser = current.getRightAreaChaserPlayer();
        if (rightChaser != null) {
            setViewerGlow(rightChaser, viewer, rightViewer
                    && temporaryChaserGlowTasks.containsKey(rightChaser.getUniqueId()));
        }
        Player leftChaser = current.getLeftAreaChaserPlayer();
        if (leftChaser != null) {
            setViewerGlow(leftChaser, viewer, leftViewer
                    && temporaryChaserGlowTasks.containsKey(leftChaser.getUniqueId()));
        }
    }

    private void syncGlowTarget(@NotNull Player target, @NotNull Set<Player> allViewers,
                                @NotNull Set<Player> allowedViewers) {
        for (Player viewer : allViewers) {
            setViewerGlow(target, viewer, allowedViewers.contains(viewer));
        }
    }

    private void setViewerGlow(@NotNull Player target, @NotNull Player viewer, boolean glowing) {
        if (glowing) plugin.getGlowingEntities().setGlowing(target, viewer);
        else plugin.getGlowingEntities().unsetGlowing(target, viewer);
    }

    private Set<Player> rightAreaGlowViewers(@NotNull ParkourTagMatch current) {
        Set<Player> viewers = new LinkedHashSet<>(getOnlineSpectators());
        Player chaser = current.getRightAreaChaserPlayer();
        if (chaser != null) viewers.add(chaser);
        viewers.addAll(current.getRightAreaEscapees());
        return viewers;
    }

    private Set<Player> leftAreaGlowViewers(@NotNull ParkourTagMatch current) {
        Set<Player> viewers = new LinkedHashSet<>(getOnlineSpectators());
        Player chaser = current.getLeftAreaChaserPlayer();
        if (chaser != null) viewers.add(chaser);
        viewers.addAll(current.getLeftAreaEscapees());
        return viewers;
    }

    private Set<Player> allGlowViewers(@NotNull ParkourTagMatch current) {
        Set<Player> viewers = rightAreaGlowViewers(current);
        viewers.addAll(leftAreaGlowViewers(current));
        return viewers;
    }

    private boolean isRightAreaPlayer(@NotNull ParkourTagMatch current, @NotNull Player player) {
        return player.getUniqueId().equals(current.getRightAreaChaser())
                || current.getRightAreaEscapees().contains(player);
    }

    private boolean isLeftAreaPlayer(@NotNull ParkourTagMatch current, @NotNull Player player) {
        return player.getUniqueId().equals(current.getLeftAreaChaser())
                || current.getLeftAreaEscapees().contains(player);
    }

    private void revealChaser(@NotNull Player chaser) {
        UUID uuid = chaser.getUniqueId();
        BukkitTask previous = temporaryChaserGlowTasks.remove(uuid);
        if (previous != null) previous.cancel();

        BukkitTask task = scheduler.runTaskLater(plugin, () -> {
            temporaryChaserGlowTasks.remove(uuid);
            syncAllParkourTagGlows();
        }, 60L);
        temporaryChaserGlowTasks.put(uuid, task);
        syncAllParkourTagGlows();
    }

    private void clearParkourTagGlows() {
        parkourTagGlowsActive = false;
        for (BukkitTask task : temporaryChaserGlowTasks.values()) task.cancel();
        temporaryChaserGlowTasks.clear();

        ParkourTagMatch current = match;
        if (current == null) return;
        Set<Player> viewers = allGlowViewers(current);
        Set<Player> targets = new LinkedHashSet<>();
        targets.addAll(current.getRightAreaEscapees());
        targets.addAll(current.getLeftAreaEscapees());
        Player rightChaser = current.getRightAreaChaserPlayer();
        Player leftChaser = current.getLeftAreaChaserPlayer();
        if (rightChaser != null) targets.add(rightChaser);
        if (leftChaser != null) targets.add(leftChaser);
        for (Player target : targets) {
            clearGlowTarget(target, viewers);
        }
    }

    private void clearGlowTarget(@NotNull Player target, @NotNull Collection<Player> viewers) {
        for (Player viewer : viewers) {
            plugin.getGlowingEntities().unsetGlowing(target, viewer);
        }
    }

    // ── handler-facing logic (routed to the player's match) ──────────────────────────────────────

    /** Whether this is the configured selection button for the player's own team in this arena copy. */
    public boolean isChaserButton(@NotNull Player player, @NotNull Block block) {
        ParkourTagMatch match = matchOf(player);
        if (match == null) return false;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return false;
        Location expected = team.equals(match.getRight())
                ? match.getRightChaserButton()
                : team.equals(match.getLeft()) ? match.getLeftChaserButton() : null;
        if (expected == null || expected.getWorld() == null
                || !expected.getWorld().equals(block.getWorld())) return false;
        return expected.getBlockX() == block.getX()
                && expected.getBlockY() == block.getY()
                && expected.getBlockZ() == block.getZ();
    }

    /** Prep-time chaser pick from the configured wall button; validates quota and records the choice. */
    public void chooseChaser(@NotNull Player player) {
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
                .replace("%player%", Utils.formatPlayerName(player))
                .replace("%times%", String.valueOf(Math.max(0,
                        CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(uuid) - 1)));
        if (team.equals(match.getRight())) {
            match.setRightAreaChaser(uuid);
            match.getRight().sendMessageToAll(message);
        } else if (team.equals(match.getLeft())) {
            match.setLeftAreaChaser(uuid);
            match.getLeft().sendMessageToAll(message);
        }
    }

    /** Ender eye use: glows the opposing chaser and cooldowns the user's team's escapee ender eyes. */
    public void useEnderEye(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        if (match == null) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        if (!plugin.getGameManager().getParkourTagManager().canUseEnderEye(team)) {
            player.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_ENDER_EYE_FAILED);
            return;
        }

        Player chaser = null;
        if (team.equals(match.getRight())) {
            chaser = match.getLeftAreaChaserPlayer();
            for (Player escapee : match.getLeftAreaEscapees()) {
                escapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_ENDER_EYE);
                escapee.setCooldown(Material.ENDER_EYE, 200);
            }
        } else if (team.equals(match.getLeft())) {
            chaser = match.getRightAreaChaserPlayer();
            for (Player escapee : match.getRightAreaEscapees()) {
                escapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_ENDER_EYE);
                escapee.setCooldown(Material.ENDER_EYE, 200);
            }
        }
        if (chaser != null) revealChaser(chaser);
        plugin.getGameManager().getParkourTagManager().setEnderEyeUsedTimes(team);
    }

    /** Wind charge use: levitates the opposing chaser for 1.5s. Once per team per round. */
    public void useWindCharge(@NotNull Player player) {
        ParkourTagMatch match = matchOf(player);
        if (match == null) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team == null) return;
        if (windChargeUsedTeams.contains(team)) {
            player.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_WIND_CHARGE_FAILED);
            return;
        }

        Player chaser = null;
        List<Player> teamEscapees = null;
        if (team.equals(match.getRight())) {
            chaser = match.getLeftAreaChaserPlayer();
            teamEscapees = match.getLeftAreaEscapees();
        } else if (team.equals(match.getLeft())) {
            chaser = match.getRightAreaChaserPlayer();
            teamEscapees = match.getRightAreaEscapees();
        }

        if (chaser != null) chaser.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0));
        if (teamEscapees != null) {
            for (Player escapee : teamEscapees) {
                escapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_WIND_CHARGE);
                ItemStack slot1 = escapee.getInventory().getItem(1);
                if (slot1 != null && slot1.getType() == Material.WIND_CHARGE) {
                    slot1.setAmount(0);
                }
            }
        }
        windChargeUsedTeams.add(team);
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
            scheduler.runTask(plugin, () -> victim.setGameMode(GameMode.SPECTATOR));
            assailant.playSound(assailant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);
            addPlayerPoints(assailant.getUniqueId(), 6);

            String message = MessageConfig.PARKOUR_TAG_CATCH_PLAYER
                    .replace("%player%", Utils.formatPlayerName(victim))
                    .replace("%chaser%", Utils.formatPlayerName(assailant));
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
        for (Player player : match.getRightAreaEscapees()) player.sendMessage(message);
        Player chaser = match.getRightAreaChaserPlayer();
        if (chaser != null) chaser.sendMessage(message);
        sendMessageToAllSpectators(message);
    }

    private void sendMessageToLeftArea(ParkourTagMatch match, String message) {
        for (Player player : match.getLeftAreaEscapees()) player.sendMessage(message);
        Player chaser = match.getLeftAreaChaserPlayer();
        if (chaser != null) chaser.sendMessage(message);
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
        if (match == null) return;
        World world = match.getSpectatorSpawn() != null ? match.getSpectatorSpawn().getWorld() : null;
        if (world == null) return;
        world.getNearbyEntities(match.getLeftAreaBox()).forEach(e -> {
            if (e instanceof Item) e.remove();
        });
        world.getNearbyEntities(match.getRightAreaBox()).forEach(e -> {
            if (e instanceof Item) e.remove();
        });
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName()))
            return true;
        ParkourTagGeometry geometry = match == null ? configuredGeometry() : match.getGeometry();
        return !geometry.contains(location);
    }

    @Override
    public boolean isSpectatorLocationAllowed(@NotNull Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(getWorldName()))
            return false;
        for (int index = 0; index < getGameConfig().getCopyCount(); index++) {
            if (configuredGeometry(index).contains(location))
                return true;
        }
        return false;
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player))
            return;

        // Remove the departing entity id immediately; Bukkit may reuse it before this round is reset.
        if (match != null) clearGlowTarget(player, allGlowViewers(match));

        if (getGameStageEnum() != GameStageEnum.PROGRESS)
            return;
        if (!isChaser(player)) {
            sendMessageToAllGamePlayers(MessageConfig.PARKOUR_TAG_PLAYER_LEAVE
                    .replace("%player%", Utils.formatPlayerName(player)));
            handleEscapeeQuit(player);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player))
            return;

        // Recreate target/viewer entity-id relationships after the reconnecting client has spawned.
        scheduler.runTaskLater(plugin, this::syncAllParkourTagGlows, 2L);

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPrepareSpotLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN && match != null) {
            if (match.isChaser(player)) {
                player.teleport(player.getUniqueId().equals(match.getRightAreaChaser())
                        ? match.getRightAreaChaserSpawn() : match.getLeftAreaChaserSpawn());
            } else if (match.getRight().getMembers().contains(player.getUniqueId())) {
                List<Location> spawns = match.getLeftAreaEscapeeSpawns();
                if (!spawns.isEmpty()) player.teleport(spawns.get(0));
            } else {
                List<Location> spawns = match.getRightAreaEscapeeSpawns();
                if (!spawns.isEmpty()) player.teleport(spawns.get(0));
            }
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        if (getGameStageEnum() == GameStageEnum.PROGRESS && isChaser(player)) {
            player.teleport(player.getUniqueId().equals(match.getRightAreaChaser())
                    ? match.getRightAreaChaserSpawn() : match.getLeftAreaChaserSpawn());
            scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
            return;
        }

        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(
                getGameStageEnum() == GameStageEnum.END ? GameMode.ADVENTURE : GameMode.SPECTATOR));
    }

    @Override
    protected void applySpectatorGameMode(@NotNull Player player) {
        super.applySpectatorGameMode(player);
        scheduler.runTask(plugin, () -> syncGlowForViewer(player));
    }

    @Override
    protected void clearSpectatorGameMode(@NotNull Player player) {
        ParkourTagMatch current = match;
        if (current != null) {
            Set<Player> targets = new LinkedHashSet<>();
            targets.addAll(current.getRightAreaEscapees());
            targets.addAll(current.getLeftAreaEscapees());
            Player rightChaser = current.getRightAreaChaserPlayer();
            Player leftChaser = current.getLeftAreaChaserPlayer();
            if (rightChaser != null) targets.add(rightChaser);
            if (leftChaser != null) targets.add(leftChaser);
            for (Player target : targets) {
                plugin.getGlowingEntities().unsetGlowing(target, player);
            }
        }
        super.clearSpectatorGameMode(player);
    }

    private void teleportPlayerToPrepareSpotLocation(Player player) {
        // During the rule-introduction phase everyone roams from the introduction spawn point.
        if (isIntroductionPhase()) {
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            return;
        }
        ParkourTagMatch match = matchOf(player);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (match != null && team != null) {
            Location target = team.equals(match.getRight()) ? match.getRightPrepareSpot()
                    : team.equals(match.getLeft()) ? match.getLeftPrepareSpot() : null;
            if (target != null) {
                player.teleport(target);
                scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
                return;
            }
        }
        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
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
        return getGameConfig().getConfiguredWorld();
    }
}
