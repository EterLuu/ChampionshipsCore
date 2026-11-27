package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.BasinLoot;
import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.CodyWave;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.HotyCodyDuskyMessages;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import lombok.Getter;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HotyCodyDuskyTeamArea extends BaseMultiTeamGameInstance {
    private static final int PASSER_ESCAPE_SPEED_TICKS = 40;
    private static final int RECEIVER_SPEED_DELAY_TICKS = 15;
    private static final int HUNTER_PULSE_DURATION_TICKS = 35;
    private static final int SMALL_ARENA_PULSE_THRESHOLD = 4;

    @Getter private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<UUID, Long> playerDeadTimes = new HashMap<>();
    private long eliminationBatch;
    private CodyWave wave = new CodyWave();
    private BasinItemRun items;
    private BasinAppearance appearance;
    private PurpleArenaRun terrain;
    private int runTicks;
    private List<org.bukkit.util.BoundingBox> matchBounds = List.of();
    private final Map<UUID, Integer> outsideSamples = new HashMap<>();
    private final Map<UUID, Integer> shieldHintTicks = new HashMap<>();
    private final Map<UUID, Integer> pendingHolderSpeedTicks = new HashMap<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    private BukkitTask startGameProgressTask;
    private final Map<UUID, Integer> playerArenas = new HashMap<>();
    private long holderGeneration;

    public HotyCodyDuskyTeamArea(
            ChampionshipsCore plugin, HotyCodyDuskyConfig hotyCodyDuskyConfig) {
        super(
                plugin,
                GameTypeEnum.HotyCodyDusky,
                new HotyCodyDuskyHandler(plugin),
                hotyCodyDuskyConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setHotyCodyDuskyArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    private void stopWaveClock() {
        for (UUID id : wave.holders()) {
            Player holder = Bukkit.getPlayer(id);
            if (holder != null && !notAreaPlayer(holder)) removeFish(holder);
        }
        if (appearance != null) {
            for (UUID id : getParticipantUniqueIds()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) appearance.clearKit(p);
            }
            appearance.close();
        }
        appearance = null;
        if (terrain != null) terrain.close();
        terrain = null;
        runTicks = 0;
        matchBounds = List.of();
        if (items != null) items.close();
        items = null;
        outsideSamples.clear();
        shieldHintTicks.clear();
        pendingHolderSpeedTicks.clear();
        holderGeneration++;
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        startGameProgressTask = null;
        wave = new CodyWave();
    }

    @Override
    public void dispose() {
        stopWaveClock();
        playerArenas.clear();
        super.dispose();
    }

    public int arenaOf(UUID player) {
        return playerArenas.getOrDefault(player, -1);
    }

    /** External spectators use their current physical field; participants retain allocation. */
    public int locatorArenaOf(UUID id) {
        int assigned = arenaOf(id);
        if (assigned >= 0) return assigned;
        Player player = Bukkit.getPlayer(id);
        if (player == null) return -1;
        Location at = player.getLocation();
        for (int arena : getSelectedArenaIndices(getGameConfig().getCopies())) {
            if (withinArena(arena, at)) return arena;
        }
        return -1;
    }

    @Override
    public int getTimer() {
        return wave.remaining();
    }

    BasinItemRun basinItems() {
        return items;
    }

    PurpleArenaRun terrain() {
        return terrain;
    }

    boolean holdsFish(Player p) {
        return wave.holds(p.getUniqueId());
    }

    boolean shedFish(Player p) {
        if (!canUseBasinItem(p) || !holdsFish(p)) return false;
        wave.remove(p.getUniqueId());
        removeFish(p);
        invalidateSidebar();
        return true;
    }

    boolean insideBasin(int arena, Location location) {
        if (!withinArena(arena, location)) return false;
        Integer ceiling = getGameConfig().getCeilingOffset();
        if (ceiling != null
                && location.getY() >= getGameConfig().getCopyGrid().origin(arena).getY() + ceiling)
            return false;
        Integer offset = getGameConfig().getSafeFloorOffset();
        return offset == null
                || location.getWorld()
                                .getBlockAt(
                                        location.getBlockX(),
                                        getGameConfig().getCopyGrid().origin(arena).getBlockY()
                                                + offset,
                                        location.getBlockZ())
                                .getType()
                        == Material.GREEN_TERRACOTTA;
    }

    boolean canUseBasinItem(Player player) {
        UUID id = player.getUniqueId();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || deathPlayer.contains(id)
                || !isGameplayParticipant(player)
                || !isInsideAssignedArena(player)) return false;
        return contested(id);
    }

    private boolean contested(UUID id) {
        return playerArenas.entrySet().stream()
                .anyMatch(
                        e ->
                                e.getValue() == arenaOf(id)
                                        && !e.getKey().equals(id)
                                        && !deathPlayer.contains(e.getKey())
                                        && isGameplayParticipant(e.getKey()));
    }

    public List<UUID> getCodyHolders(UUID viewer) {
        int arena = viewer == null ? -1 : arenaOf(viewer);
        return wave.holders().stream().filter(id -> arena < 0 || arenaOf(id) == arena).toList();
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> points = new ArrayList<>();
        for (int arena : getSelectedArenaIndices(getGameConfig().getCopies())) {
            Location spawn = getGameConfig().spawn(arena);
            if (spawn != null) {
                points.add(spawn);
                var base = getGameConfig().getCopyGrid().origin(arena);
                for (var item : getGameConfig().getItemSpawns())
                    points.add(base.clone().add(item).toLocation(spawn.getWorld()));
                if (getGameConfig().getElevatorBase() != null)
                    points.add(
                            base.clone()
                                    .add(getGameConfig().getElevatorBase())
                                    .toLocation(spawn.getWorld()));
            }
        }
        return points;
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName())) return true;
        var boxes = getGameConfig().getCopyBoxes();
        if (boxes.isEmpty()) return true;
        return getSelectedArenaIndices(boxes.size()).stream()
                .noneMatch(index -> boxes.get(index).contains(location.toVector()));
    }

    public boolean isInsideAssignedArena(Player player) {
        return withinArena(arenaOf(player.getUniqueId()), player.getLocation());
    }

    boolean withinArena(int arena, Location at) {
        var boxes = matchBounds.isEmpty() ? getGameConfig().getCopyBoxes() : matchBounds;
        return arena >= 0
                && arena < boxes.size()
                && at.getWorld() != null
                && at.getWorld().getName().equals(getWorldName())
                && boxes.get(arena).contains(at.toVector());
    }

    private Location playerSpawn(UUID id) {
        int arena = arenaOf(id);
        return getGameConfig()
                .spawn(
                        arena < 0
                                ? getSelectedArenaIndices(getGameConfig().getCopies()).getFirst()
                                : arena);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        stopWaveClock();

        if (isSettlementAllowed()) calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GAME_END_TITLE,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GAME_END_SUBTITLE);

        beginPostGameSettlement();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    protected void calculatePoints() {
        List<UUID> alivePlayers = new ArrayList<>(gamePlayers);
        alivePlayers.removeAll(deathPlayer);
        ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.HotyCodyDuskyScoring
                .rankingBonuses(playerDeadTimes, alivePlayers, eliminationBatch + 1)
                .forEach(this::addPlayerPoints);

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
    }

    @Override
    public void resetArea() {
        stopWaveClock();
        deathPlayer.clear();
        playerDeadTimes.clear();
        teamDeathPlayers.clear();
        playerArenas.clear();
        shieldHintTicks.clear();
        pendingHolderSpeedTicks.clear();
        eliminationBatch = 0;
    }

    @Override
    public HotyCodyDuskyConfig getGameConfig() {
        return (HotyCodyDuskyConfig) gameConfig;
    }

    @Override
    public HotyCodyDuskyHandler getGameHandler() {
        return (HotyCodyDuskyHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }

    @Override
    public boolean freezeMovementDuringCountdown() {
        return false;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the configured rule sections, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        playerArenas.clear();
        var roster =
                gameTeams.stream()
                        .map(
                                team ->
                                        team.getMembers().stream()
                                                .filter(gamePlayers::contains)
                                                .sorted()
                                                .toList())
                        .toList();
        playerArenas.putAll(
                CodyWave.allocate(roster, getSelectedArenaIndices(getGameConfig().getCopies())));
        for (UUID id : gamePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.teleport(playerSpawn(id));
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        for (UUID id : gamePlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && isGameplayParticipant(p)) prepareKit(p);
        }
        announceGamePreparation(
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_START_PREPARATION,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_START_PREPARATION_TITLE,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    protected void startGameProgress() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                excludeBeforeStart(uuid);
                if (plugin.getTeamManager().getTeamByPlayer(uuid) != null) {
                    logGame(
                            Level.INFO,
                            MessageConfig.codeText(
                                    "hoty-cody-dusky-team-area.start-game-progress-1"),
                            MessageConfig.codeText(
                                    "hoty-cody-dusky-team-area.start-game-progress-2",
                                    playerManager.getPlayerName(uuid),
                                    uuid));
                }
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        startFinalCountdown(
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GAME_START_SOON_TITLE,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GAME_START_TITLE,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) excludeBeforeStart(uuid);
        }
        long generation = holderGeneration;
        matchBounds = getGameConfig().getCopyBoxes();
        terrain = new PurpleArenaRun(this, plugin);
        items = new BasinItemRun(this, plugin.getGlowingEntities());
        if (!startScatter()) return;
        startGameProgressTask =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            if (generation == holderGeneration
                                    && getGameStageEnum() == GameStageEnum.PROGRESS) {
                                wave.nextServerTick();
                                runTicks++;
                                tickPendingHolderSpeed();
                                if (items != null) items.tickEffects(runTicks);
                                terrain.tick(runTicks);
                                if (runTicks % 2 == 0) tickBoundaries();
                                if (generation == holderGeneration && runTicks % 20 == 0)
                                    tickWave();
                            }
                        },
                        1L,
                        1L);
    }

    private boolean startScatter() {
        var groups = survivorsByArena();
        if (groups.values().stream().noneMatch(group -> group.size() > 1)) {
            endGame();
            return false;
        }
        wave.beginScatter();
        for (var group : groups.values()) {
            for (UUID id : group) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && isGameplayParticipant(player)) {
                    player.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.SPEED,
                                    (CodyWave.SCATTER + 1) * 20,
                                    0,
                                    false,
                                    false));
                }
            }
        }
        displayClock();
        invalidateSidebar();
        return true;
    }

    private void clearScatterSpeed() {
        for (UUID id : gamePlayers) {
            if (deathPlayer.contains(id)) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !isGameplayParticipant(player)) continue;
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.getAmplifier() == 0 && !effect.isInfinite()) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    private Map<Integer, List<UUID>> survivorsByArena() {
        Map<Integer, List<UUID>> groups = new LinkedHashMap<>();
        for (var entry : playerArenas.entrySet()) {
            UUID id = entry.getKey();
            if (!deathPlayer.contains(id) && isGameplayParticipant(id)) {
                groups.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(id);
            }
        }
        return groups;
    }

    private boolean startWave() {
        var groups = survivorsByArena();
        if (groups.values().stream().noneMatch(group -> group.size() > 1)) {
            endGame();
            return false;
        }
        pendingHolderSpeedTicks.clear();
        wave.start(groups.values());
        if (terrain != null) terrain.startWave();
        for (UUID id : wave.holders()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            equipFish(player, false);
            addPlayerPoints(id, 10);
        }
        displayClock();
        invalidateSidebar();
        return true;
    }

    private void tickPendingHolderSpeed() {
        if (pendingHolderSpeedTicks.isEmpty()) return;
        for (UUID id : List.copyOf(pendingHolderSpeedTicks.keySet())) {
            if (runTicks < pendingHolderSpeedTicks.get(id)) continue;
            pendingHolderSpeedTicks.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null
                    && activeWave()
                    && !deathPlayer.contains(id)
                    && isGameplayParticipant(player)
                    && holdsFish(player)) {
                applyHolderSpeed(player);
            }
        }
    }

    private void applyHolderSpeed(Player player) {
        int amplifier = wave.isFrenzy() ? 2 : 1;
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && !current.isInfinite() && current.getAmplifier() > amplifier) return;
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.SPEED,
                        PotionEffect.INFINITE_DURATION,
                        amplifier,
                        false,
                        false));
    }

    private void tickBoundaries() {
        var cfg = getGameConfig();
        var grid = cfg.getCopyGrid();
        Integer floor = cfg.getSafeFloorOffset(), ceiling = cfg.getCeilingOffset();
        for (var entry : playerArenas.entrySet()) {
            UUID id = entry.getKey();
            Player player = Bukkit.getPlayer(id);
            if (player == null || deathPlayer.contains(id) || !isGameplayParticipant(player))
                continue;
            if (!isInsideAssignedArena(player)) {
                player.teleport(playerSpawn(id));
                outsideSamples.remove(id);
                continue;
            }
            if (holdsFish(player) || !activeWave()) {
                outsideSamples.remove(id);
                continue;
            }
            Location at = player.getLocation();
            int baseY = grid.origin(entry.getValue()).getBlockY();
            var world = at.getWorld();
            int x = at.getBlockX(), z = at.getBlockZ();
            boolean outside =
                    floor != null
                                    && world.getBlockAt(x, baseY + floor, z).getType()
                                            != Material.GREEN_TERRACOTTA
                            || ceiling != null
                                    && player.getBoundingBox().getMaxY() > baseY + ceiling;
            if (!outside) {
                if (outsideSamples.remove(id) != null)
                    CoreMessages.sendActionBar(
                            player, MessageConfig.codeText("hoty-items.boundary-safe"));
                continue;
            }
            int layer = floor != null ? baseY + floor : at.getBlockY();
            if (world.getBlockAt(x, layer - 1, z).getType() == Material.TINTED_GLASS) {
                outsideSamples.remove(id);
                if (runTicks % 10 == 0) showBoundary(player, at, layer);
                continue;
            }
            int samples = outsideSamples.merge(id, 1, Integer::sum);
            if (samples >= 60) {
                infect(player, false);
                outsideSamples.remove(id);
            } else if (samples % 10 == 1) {
                CoreMessages.sendActionBar(
                        player,
                        MessageConfig.codeText("hoty-items.infection-boundary", 6 - samples / 10));
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 2);
            }
        }
    }

    private void showBoundary(Player viewer, Location at, int layer) {
        int sent = 0;
        for (var face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            for (int along : List.of(0, -2, 2)) {
                int dx = 3 * face.getModX() + along * face.getModZ();
                int dz = 3 * face.getModZ() + along * face.getModX();
                if (at.getWorld()
                                .getBlockAt(at.getBlockX() + dx, layer, at.getBlockZ() + dz)
                                .getType()
                        == Material.GREEN_TERRACOTTA) continue;
                viewer.spawnParticle(
                        Particle.DUST,
                        at.clone().add(dx, 1.5, dz),
                        4,
                        0.3,
                        1.4,
                        0.3,
                        0,
                        BasinParticles.SPRAY);
                if (++sent == 4) return;
                break;
            }
        }
    }

    private void tickWave() {
        var groups = survivorsByArena();
        if (items != null) items.tick(groups);
        if (groups.values().stream().noneMatch(group -> group.size() > 1)) {
            endGame();
            return;
        }
        for (UUID id : wave.holders()) {
            if (groups.getOrDefault(arenaOf(id), List.of()).size() <= 1
                    || !isGameplayParticipant(id)) {
                wave.remove(id);
                Player player = Bukkit.getPlayer(id);
                if (player != null && isGameplayParticipant(player)) removeFish(player);
            }
        }
        boolean wasImmune = wave.isImmune();
        boolean wasFrenzy = wave.isFrenzy();
        boolean wasFrozen = wave.frozenRemaining() > 0;
        if (wave.tick()) {
            if (wave.scattering()) {
                clearScatterSpeed();
                sendActionBarToActiveGamePlayers(
                        MessageConfig.codeText("hoty-items.scatter-ended"));
                startWave();
                return;
            }
            if (wave.intermission()) {
                startWave();
                return;
            }
            var victims = wave.holders();
            capture(victims, false);
            if (survivorsByArena().values().stream().noneMatch(group -> group.size() > 1)) {
                endGame();
                return;
            }
            pendingHolderSpeedTicks.clear();
            wave.beginIntermission();
            invalidateSidebar();
        } else {
            if (wasImmune && !wave.isImmune()) {
                sendActionBarToActiveGamePlayers(
                        MessageConfig.codeText("hoty-items.immunity-ended"));
                for (UUID id : gamePlayers) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null
                            && !deathPlayer.contains(id)
                            && isGameplayParticipant(player)) {
                        player.playSound(
                                player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
                    }
                }
                invalidateSidebar();
            }
            if (!wasFrenzy && wave.isFrenzy()) {
                onEnterFrenzy();
            } else if (wasFrozen && wave.frozenRemaining() == 0) {
                invalidateSidebar();
            }
        }
        if (wave.active() && !wave.isImmune()) {
            tickHunterSense(groups);
        }
        displayClock();
    }

    private void onEnterFrenzy() {
        sendActionBarToActiveGamePlayers(MessageConfig.codeText("hoty-items.frenzy-start"));
        for (UUID id : gamePlayers) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || deathPlayer.contains(id) || !isGameplayParticipant(player))
                continue;
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.25f);
            if (holdsFish(player) && !pendingHolderSpeedTicks.containsKey(id)) {
                applyHolderSpeed(player);
            }
        }
        invalidateSidebar();
    }

    private void tickHunterSense(Map<Integer, List<UUID>> groups) {
        if (items == null) return;
        int sec = runTicks / 20;
        boolean frenzyPulse = wave.isFrenzy() && sec % 4 == 0;
        boolean smallArenaPulse = sec % 6 == 0;
        if (!frenzyPulse && !smallArenaPulse) return;
        for (var group : groups.values()) {
            if (group.size() < 2) continue;
            if (!frenzyPulse && group.size() > SMALL_ARENA_PULSE_THRESHOLD) continue;
            List<Player> arenaHolders = new ArrayList<>();
            List<Player> arenaRunners = new ArrayList<>();
            for (UUID id : group) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || deathPlayer.contains(id) || !isGameplayParticipant(p)) continue;
                if (holdsFish(p)) arenaHolders.add(p);
                else if (!p.hasPotionEffect(PotionEffectType.INVISIBILITY)) arenaRunners.add(p);
            }
            if (arenaHolders.isEmpty() || arenaRunners.isEmpty()) continue;
            for (Player holder : arenaHolders) {
                for (Player runner : arenaRunners) {
                    items.tools.revealForViewer(holder, runner, HUNTER_PULSE_DURATION_TICKS);
                }
            }
        }
    }

    boolean freezeClock(Player player) {
        if (!canUseBasinItem(player) || !wave.freeze()) return false;
        sendActionBarToAllGamePlayers(MessageConfig.codeText("hoty-items.frozen"));
        displayClock();
        invalidateSidebar();
        return true;
    }

    boolean activeWave() {
        return wave.active();
    }

    void infect(Player player, boolean blockable) {
        UUID id = player.getUniqueId();
        if (!activeWave()
                || !isGameplayParticipant(player)
                || deathPlayer.contains(id)
                || holdsFish(player)
                || !contested(id)
                || !isInsideAssignedArena(player)
                || (blockable && breakVest(player))) return;
        if (wave.infect(id)) {
            equipFish(player, false);
            invalidateSidebar();
        }
    }

    void capture(Collection<UUID> candidates, boolean quicksand) {
        List<UUID> victims = new ArrayList<>();
        for (UUID id : candidates) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !isGameplayParticipant(player) || deathPlayer.contains(id))
                continue;
            if (items != null && items.tools.spendProtection(id)) {
                player.playEffect(EntityEffect.TOTEM_RESURRECT);
                if (holdsFish(player)) {
                    wave.remove(id);
                    removeFish(player);
                }
                if (quicksand) player.teleport(playerSpawn(id));
                invalidateSidebar();
            } else {
                if (terrain != null && isInsideAssignedArena(player))
                    terrain.splashOnCapture(player);
                victims.add(id);
            }
        }
        if (victims.isEmpty()) return;
        eliminate(victims);
        for (UUID id : victims) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !isGameplayParticipant(player)) continue;
            player.getWorld()
                    .spawnParticle(
                            Particle.SPLASH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4);
            player.getWorld()
                    .playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1, 0.85f);
            plugin.getGameManager()
                    .getSpectatorManager()
                    .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
            sendMessageToAllGamePlayers(
                    HotyCodyDuskyMessages.HOTY_CODY_DUSKY_PLAYER_DEATH.replace(
                            "%player%", CoreMessages.formatPlayerName(player)));
        }
        if (quicksand
                && survivorsByArena().values().stream().noneMatch(group -> group.size() > 1)) {
            endGame();
        }
    }

    boolean isWornVest(ItemStack item) {
        return appearance != null && appearance.hotbar.isWornVest(item);
    }

    void wearVest(Player player) {
        if (appearance != null) {
            appearance.hotbar.wearVest(player);
            invalidatePlayerSidebar(player);
        }
    }

    private boolean breakVest(Player player) {
        boolean broken = appearance != null && appearance.hotbar.breakVest(player);
        if (broken) invalidatePlayerSidebar(player);
        return broken;
    }

    public int getWaveNumber() {
        return wave.number();
    }

    public boolean hasActiveProtection(UUID id) {
        if (items != null && items.tools.hasArmedTotem(id)) return true;
        if (appearance == null) return false;
        Player player = Bukkit.getPlayer(id);
        return player != null && appearance.hotbar.hasWornVest(player);
    }

    public String getSidebarPhaseKey() {
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.END || stage == GameStageEnum.STOPPING) return "ended";
        if (stage != GameStageEnum.PROGRESS) return "waiting";
        if (wave.scattering()) return "scatter";
        if (wave.intermission()) return "intermission";
        if (wave.isImmune()) return "immunity";
        if (wave.frozenRemaining() > 0) return "frozen";
        if (wave.isFrenzy()) return "frenzy";
        return "chase";
    }

    public String getSidebarStateKey(UUID viewer, boolean spectator) {
        if (spectator) return "spectator";
        GameStageEnum stage = getGameStageEnum();
        if (stage == GameStageEnum.END || stage == GameStageEnum.STOPPING) return "ended";
        if (stage != GameStageEnum.PROGRESS) return "waiting";
        if (deathPlayer.contains(viewer)) return "eliminated";
        if (wave.scattering()) return "scatter";
        if (wave.intermission()) return "intermission";
        if (wave.holds(viewer)) return wave.isFrenzy() ? "holder-frenzy" : "holder";
        return hasActiveProtection(viewer) ? "runner-protected" : "runner";
    }

    public String getSidebarSkillDisplay(UUID viewer, boolean spectator) {
        if (spectator
                || getGameStageEnum() != GameStageEnum.PROGRESS
                || deathPlayer.contains(viewer)
                || terrain == null) {
            return "&7-";
        }
        PurpleArenaRun.Ability ability = terrain.selected(viewer);
        return MessageConfig.codeText(
                terrain.isSkillReady(viewer)
                        ? "hoty-items.skill-ready"
                        : "hoty-items.skill-cooldown",
                ability.title());
    }

    void invalidateSidebar() {
        if (plugin != null && plugin.getSidebarManager() != null) {
            plugin.getSidebarManager().invalidateAll();
        }
    }

    void invalidatePlayerSidebar(Player player) {
        if (player != null && plugin != null && plugin.getSidebarManager() != null) {
            plugin.getSidebarManager().invalidate(player);
        }
    }

    private void displayClock() {
        String template;
        BarColor color;
        int duration;
        if (wave.scattering()) {
            template =
                    HotyCodyDuskyMessages.SCATTER != null
                            ? HotyCodyDuskyMessages.SCATTER
                            : HotyCodyDuskyMessages.INTERMISSION;
            color = BarColor.GREEN;
            duration = CodyWave.SCATTER;
        } else if (wave.intermission()) {
            template = HotyCodyDuskyMessages.INTERMISSION;
            color = BarColor.BLUE;
            duration = CodyWave.INTERMISSION;
        } else if (wave.isImmune()) {
            template =
                    HotyCodyDuskyMessages.IMMUNITY != null
                            ? HotyCodyDuskyMessages.IMMUNITY
                            : HotyCodyDuskyMessages.HOTY_CODY_DUSKY_ACTION_BAR_COUNT_DOWN;
            color = BarColor.PURPLE;
            duration = CodyWave.duration(wave.number());
        } else {
            template = HotyCodyDuskyMessages.HOTY_CODY_DUSKY_ACTION_BAR_COUNT_DOWN;
            duration = CodyWave.duration(wave.number());
            if (wave.frozenRemaining() > 0) color = BarColor.BLUE;
            else if (wave.isFrenzy()) color = BarColor.RED;
            else color = BarColor.YELLOW;
        }
        String title =
                template.replace("%wave%", String.valueOf(wave.number()))
                        .replace("%time%", CoreMessages.bossBarTime(wave.remaining()))
                        .replace("%immunity%", CoreMessages.bossBarTime(wave.immunityRemaining()));
        if (wave.active() && wave.frozenRemaining() > 0) {
            title +=
                    MessageConfig.codeText(
                            "hoty-items.bossbar-frozen",
                            CoreMessages.bossBarTime(wave.frozenRemaining()));
        } else if (wave.isFrenzy()) {
            title += MessageConfig.codeText("hoty-items.bossbar-frenzy");
        }
        if (title.contains("%holder%")) {
            title =
                    title.replace(
                            "%holder%",
                            wave.holders().stream()
                                    .map(CoreMessages::formatPlayerName)
                                    .collect(java.util.stream.Collectors.joining("、")));
        }
        updateGameTimerBossBar(title, wave.remaining(), duration, color);
        if (wave.scattering() && wave.remaining() <= 3 && wave.remaining() > 0) {
            float pitch =
                    switch (wave.remaining()) {
                        case 3 -> 1.2f;
                        case 2 -> 1.4f;
                        default -> 1.6f;
                    };
            for (UUID id : gamePlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.playSound(player, Sound.UI_BUTTON_CLICK, 0.8f, pitch);
            }
        } else if (wave.active() && wave.frozenRemaining() == 0 && wave.remaining() <= 3) {
            float pitch =
                    switch (wave.remaining()) {
                        case 3 -> 2.0f;
                        case 2 -> 1.75f;
                        default -> 1.5f;
                    };
            for (UUID id : gamePlayers) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.playSound(player, Sound.UI_BUTTON_CLICK, 1, pitch);
            }
        }
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public int getSurvivedTeamNums() {
        return (int)
                gameTeams.stream()
                        .filter(
                                team ->
                                        team.getMembers().stream()
                                                .anyMatch(
                                                        id ->
                                                                gamePlayers.contains(id)
                                                                        && !deathPlayer.contains(
                                                                                id)))
                        .count();
    }

    static float hurtAnimationYaw(
            double sourceX, double sourceZ, double targetX, double targetZ, float targetYaw) {
        double dx = sourceX - targetX;
        double dz = sourceZ - targetZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - targetYaw - 90.0);
    }

    void passFish(Player from, Player to) {
        passFish(from, to, from.getLocation());
    }

    void passFish(Player from, Player to, Location impactOrigin) {
        UUID source = from.getUniqueId(), target = to.getUniqueId();
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !isGameplayParticipant(from)
                || !isGameplayParticipant(to)
                || deathPlayer.contains(source)
                || deathPlayer.contains(target)
                || arenaOf(source) != arenaOf(target)
                || !isInsideAssignedArena(from)
                || !isInsideAssignedArena(to)) return;
        if (wave.isImmune() && wave.holds(source) && !wave.holds(target)) {
            if (runTicks - shieldHintTicks.getOrDefault(source, -20) >= 8) {
                shieldHintTicks.put(source, runTicks);
                from.playSound(from.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.1f);
                to.playSound(to.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.1f);
                CoreMessages.sendActionBar(
                        from,
                        MessageConfig.codeText("hoty-items.immune", wave.immunityRemaining()));
            }
            return;
        }
        boolean duel =
                playerArenas.entrySet().stream()
                        .noneMatch(
                                e ->
                                        e.getValue() == arenaOf(source)
                                                && !e.getKey().equals(source)
                                                && !e.getKey().equals(target)
                                                && !deathPlayer.contains(e.getKey())
                                                && isGameplayParticipant(e.getKey()));
        if (wave.active() && wave.holds(source) && !wave.holds(target)) {
            int tagBackTicks = wave.tagBackRemainingTicks(source, target);
            if (tagBackTicks > 0) {
                if (runTicks - shieldHintTicks.getOrDefault(source, -20) >= 8) {
                    shieldHintTicks.put(source, runTicks);
                    from.playSound(from.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75f, 1.35f);
                    String seconds = String.format(Locale.ROOT, "%.1f", tagBackTicks / 20.0);
                    CoreMessages.sendActionBar(
                            from,
                            MessageConfig.codeText(
                                    duel
                                            ? "hoty-items.tagback-blocked-duel"
                                            : "hoty-items.tagback-blocked",
                                    seconds));
                }
                return;
            }
        }
        if (!wave.canPass(source, target, duel)) return;
        Location origin = impactOrigin != null ? impactOrigin : from.getLocation();
        if (breakVest(to)) {
            applyKnockback(to, origin, 0.25);
            return;
        }
        if (!wave.pass(source, target, duel)) return;
        if (terrain != null) terrain.dismount(target);
        if (to.isInsideVehicle()) to.leaveVehicle();
        boolean melee = from.getLocation().distanceSquared(origin) < 0.01;
        applyPassImpact(from, to, origin, melee && from.isSprinting() ? 0.55 : 0.4);
        removeFish(from);
        from.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.SPEED, PASSER_ESCAPE_SPEED_TICKS, 1, false, false));
        sendActionBarToAllGamePlayers(
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_GIVE_CODY_TO_PLAYER
                        .replace("%from%", CoreMessages.formatPlayerName(from))
                        .replace("%to%", CoreMessages.formatPlayerName(to)));
        equipFish(to, true);
        invalidateSidebar();
    }

    private void applyPassImpact(Player from, Player to, Location origin, double knockback) {
        Location targetLoc = to.getLocation();
        to.playHurtAnimation(
                hurtAnimationYaw(
                        origin.getX(),
                        origin.getZ(),
                        targetLoc.getX(),
                        targetLoc.getZ(),
                        targetLoc.getYaw()));
        applyKnockback(to, origin, knockback);
        to.getWorld().playSound(targetLoc, Sound.ENTITY_PLAYER_HURT, 0.9f, 1.0f);
        from.playSound(from.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.15f);
        to.getWorld()
                .spawnParticle(
                        Particle.CRIT, targetLoc.clone().add(0, 1.1, 0), 6, 0.3, 0.35, 0.3, 0.15);
    }

    private void applyKnockback(Player target, Location origin, double strength) {
        if (items != null && items.tools.isJailed(target)) return;
        Location targetLoc = target.getLocation();
        target.knockback(
                strength, origin.getX() - targetLoc.getX(), origin.getZ() - targetLoc.getZ());
    }

    private void equipTeam(Player player) {
        if (appearance == null) appearance = new BasinAppearance();
        appearance.teamKit(player, plugin.getTeamManager().getTeamByPlayer(player.getUniqueId()));
    }

    private void prepareKit(Player player) {
        equipTeam(player);
        appearance.hotbar.prepare(player);
    }

    boolean hasHotbar(Player player) {
        return appearance != null && appearance.hotbar.installed(player);
    }

    boolean giveLoot(Player player, BasinLoot loot) {
        return appearance != null && appearance.hotbar.give(player, loot);
    }

    void showSkill(Player player, PurpleArenaRun.Ability skill) {
        if (appearance != null) appearance.hotbar.show(player, skill);
    }

    void copyAppearance(Player player, org.bukkit.entity.Mannequin model) {
        BasinAppearance.copy(
                player,
                model,
                plugin.getTeamManager().getTeamByPlayer(player.getUniqueId()),
                holdsFish(player));
    }

    private void equipFish(Player player, boolean fromPass) {
        if (appearance == null) appearance = new BasinAppearance();
        UUID id = player.getUniqueId();
        terrain.clearExposure(id);
        PurpleArenaRun.Ability skill = terrain.selected(player);
        showSkill(player, skill);
        appearance.mark(player, plugin.getTeamManager().getTeamByPlayer(id));
        player.getWorld()
                .spawnParticle(
                        Particle.DUST,
                        player.getLocation().add(0, 1.8, 0),
                        10,
                        0.25,
                        0.2,
                        0.25,
                        0,
                        BasinParticles.COD);
        if (fromPass) {
            pendingHolderSpeedTicks.put(id, runTicks + RECEIVER_SPEED_DELAY_TICKS);
        } else {
            pendingHolderSpeedTicks.remove(id);
            applyHolderSpeed(player);
        }
        player.playSound(player, Sound.ENTITY_COD_FLOP, 1, 1);
        CoreMessages.sendActionBar(
                player,
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_PLAYER_RECEIVED_CODY.replace(
                                "%player%", CoreMessages.formatPlayerName(player))
                        + " "
                        + MessageConfig.codeText("hoty-items.ability-controls", skill.title()));
    }

    private void removeFish(Player player) {
        UUID id = player.getUniqueId();
        pendingHolderSpeedTicks.remove(id);
        showSkill(player, null);
        if (terrain != null) terrain.releaseSource(player);
        if (items != null) items.tools.clearRevealsForViewer(id);
        if (appearance != null) appearance.release(id);
        equipTeam(player);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    /**
     * An absent countdown participant never enters the survival ranking or earns survivor points.
     */
    private void excludeBeforeStart(UUID uuid) {
        if (deathPlayer.contains(uuid)) return;
        deathPlayer.add(uuid);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (team != null) teamDeathPlayers.merge(team, 1, Integer::sum);
    }

    private void eliminate(Collection<UUID> victims) {
        long batch = ++eliminationBatch;
        int awards = 0;
        for (UUID id : victims) {
            if (deathPlayer.contains(id)) continue;
            Player previousHolder = Bukkit.getPlayer(id);
            if (wave.holds(id) && previousHolder != null) removeFish(previousHolder);
            pendingHolderSpeedTicks.remove(id);
            if (appearance != null && previousHolder != null) appearance.clearKit(previousHolder);
            if (terrain != null) terrain.forget(id);
            if (previousHolder != null && previousHolder.isInsideVehicle())
                previousHolder.leaveVehicle();
            if (items != null) items.forget(id);
            deathPlayer.add(id);
            playerDeadTimes.put(id, batch);
            wave.remove(id);
            awards++;
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(id);
            if (team != null
                    && teamDeathPlayers.merge(team, 1, Integer::sum)
                            == team.getMembers().stream().filter(gamePlayers::contains).count()) {
                awards++;
                sendMessageToAllGamePlayers(
                        HotyCodyDuskyMessages.HOTY_CODY_DUSKY_WHOLE_TEAM_WAS_KILLED.replace(
                                "%team%", team.getColoredName()));
            }
        }
        for (UUID id : gamePlayers) {
            if (awards > 0 && !deathPlayer.contains(id)) addPlayerPoints(id, 15 * awards);
        }
        invalidateSidebar();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        long generation = holderGeneration;
        UUID id = player.getUniqueId();
        boolean preparation =
                getGameStageEnum() == GameStageEnum.PREPARATION
                        || getGameStageEnum() == GameStageEnum.COUNTDOWN;
        scheduler.runTask(
                plugin,
                () -> {
                    if (generation != holderGeneration
                            || Bukkit.getPlayer(id) != player
                            || !gamePlayers.contains(id)
                            || !plugin.isLoaded()
                            || getGameStageEnum() == GameStageEnum.WAITING) return;
                    player.spigot().respawn();
                    if (preparation
                            && !deathPlayer.contains(id)
                            && isGameplayParticipant(player)
                            && (getGameStageEnum() == GameStageEnum.PREPARATION
                                    || getGameStageEnum() == GameStageEnum.COUNTDOWN
                                    || getGameStageEnum() == GameStageEnum.PROGRESS)) {
                        prepareKit(player);
                        if (terrain != null && holdsFish(player)) equipFish(player, false);
                        player.teleport(getPreparationTeleportLocation(playerSpawn(id)));
                    } else if (deathPlayer.contains(id)) {
                        plugin.getGameManager()
                                .getSpectatorManager()
                                .enterParticipantSpectator(
                                        player, this, getSpectatorSpawnLocation());
                    }
                });
        if (preparation) return;

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId())) return;

        eliminate(List.of(player.getUniqueId()));

        String message = HotyCodyDuskyMessages.HOTY_CODY_DUSKY_PLAYER_DEATH;

        message = message.replace("%player%", CoreMessages.formatPlayerName(player));
        sendMessageToAllGamePlayers(message);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            if (appearance != null) appearance.clearKit(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            excludeBeforeStart(player.getUniqueId());
            if (appearance != null) appearance.clearKit(player);
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId())) return;

        sendMessageToAllGamePlayers(
                HotyCodyDuskyMessages.HOTY_CODY_DUSKY_PLAYER_LEAVE.replace(
                        "%player%", CoreMessages.formatPlayerName(player)));
        eliminate(List.of(player.getUniqueId()));
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(playerSpawn(player.getUniqueId())));
            player.setGameMode(GameMode.ADVENTURE);
            prepareKit(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN) {
            if (deathPlayer.contains(player.getUniqueId())) {
                plugin.getGameManager()
                        .getSpectatorManager()
                        .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
                return;
            }
            player.teleport(playerSpawn(player.getUniqueId()));
            player.setGameMode(GameMode.ADVENTURE);
            prepareKit(player);
            return;
        }
        if (getGameStageEnum() == GameStageEnum.END) {
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        plugin.getGameManager()
                .getSpectatorManager()
                .enterParticipantSpectator(player, this, getSpectatorSpawnLocation());
    }
}
