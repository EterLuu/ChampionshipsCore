package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.laser.mechanics.LaserGunMechanics;
import ink.ziip.championshipscore.api.game.laser.mechanics.LaserShotEngine;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserBeam;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserGunItems;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserKillFeedback;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserNameTags;
import ink.ziip.championshipscore.api.game.laser.presentation.LaserZoom;
import ink.ziip.championshipscore.api.game.laser.runtime.LaserWeaponState;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.lasershowdown.model.LaserShowdownLife;
import ink.ziip.championshipscore.api.game.lasershowdown.model.LaserShowdownStats;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.DurationText;

import lombok.Getter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LaserShowdownTeamArea extends BaseMultiTeamGameInstance {
    static final int DAILY_DURATION_SECONDS = 900;
    static final int DAILY_KILL_TARGET = 40;
    private final Map<UUID, LaserShowdownLife> playerLives = new ConcurrentHashMap<>();
    private final Map<UUID, EnumMap<LaserGun, Long>> playerWeaponLives = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerEnemyHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerHeadHits = new ConcurrentHashMap<>();
    private final List<List<Location>> areaLocations = new ArrayList<>();
    private final Map<UUID, List<Location>> playerRespawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerSpawnLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRespawnTick = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerLaserHealth = new ConcurrentHashMap<>();
    private LaserWeaponState weapons = new LaserWeaponState();
    private final Map<UUID, Integer> playerProtectionUntil = new ConcurrentHashMap<>();
    private final Map<UUID, LaserGun> selectedGuns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> aimingUntil = new ConcurrentHashMap<>();
    private final Set<UUID> laserMovementSpeed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> laserMovementSlow = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> bulletSlowUntil = new ConcurrentHashMap<>();
    private final Map<List<Location>, Iterator<Location>> locationIterators =
            new IdentityHashMap<>();
    private final Map<ChampionshipTeam, Integer> teamShootTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerIndividualKills = new ConcurrentHashMap<>();
    private List<String> teamRank = new ArrayList<>();
    @Getter private int timer;
    private BukkitTask startGameProgressTask;
    private BukkitTask laserTickTask;
    private int laserTick;
    private LaserZoom zoom;
    private LaserNameTags nameTags;
    private boolean firstRoundGunSelectionWindow;
    private final NamespacedKey laserItemKey;
    private static final int GUN_SELECTOR_SLOT = 8;
    private static final int AMMO_SLOT = 4;
    private static final String GUN_SELECTOR_TYPE = "gun-selector";

    public LaserShowdownTeamArea(
            ChampionshipsCore plugin, LaserShowdownConfig laserShowdownConfig) {
        super(
                plugin,
                GameTypeEnum.LaserShowdown,
                new LaserShowdownHandler(plugin),
                laserShowdownConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setLaserShowdownTeamArea(this);

        getGameHandler().register();

        laserItemKey = new NamespacedKey(plugin, "lasershowdown-laser-item");

        setGameStageEnum(GameStageEnum.WAITING);

        refreshArenaLocations();
    }

    private void refreshArenaLocations() {
        if (!(gameConfig instanceof LaserShowdownConfig config)) return;
        areaLocations.clear();
        for (List<Location> locations : config.arenaSpawns())
            areaLocations.add(new ArrayList<>(locations));
    }

    public int getArenaCount() {
        refreshArenaLocations();
        return areaLocations.size();
    }

    /** Empty sub-arenas cannot supply initial or respawn locations. */
    public List<Integer> getPlayableArenaIndices() {
        refreshArenaLocations();
        return java.util.stream.IntStream.range(0, areaLocations.size())
                .filter(index -> !areaLocations.get(index).isEmpty())
                .boxed()
                .toList();
    }

    @Override
    public void prepareRunMode(@NotNull GameRunMode mode) {
        GameRunMode previous = getRunMode();
        List<Integer> playable = mode == GameRunMode.DAILY ? getPlayableArenaIndices() : List.of();
        if (mode == GameRunMode.DAILY && playable.isEmpty())
            throw new IllegalStateException("Laser Showdown has no playable sub-arena");
        super.prepareRunMode(mode);
        if (mode == GameRunMode.DAILY) {
            int arena = playable.get(ThreadLocalRandom.current().nextInt(playable.size()));
            prepareArenaSelection(new ArenaSelection(List.of(arena)));
        } else if (mode == GameRunMode.GAME && previous == GameRunMode.DAILY) {
            prepareArenaSelection(ArenaSelection.all());
        }
    }

    private List<List<Location>> selectedArenaLocations() {
        return getSelectedArenaIndices(areaLocations.size()).stream()
                .map(areaLocations::get)
                .toList();
    }

    private List<Location> randomArenaLocations() {
        var selected = selectedArenaLocations();
        return selected.get(ThreadLocalRandom.current().nextInt(selected.size()));
    }

    public boolean notInPlayerArena(Player player, Location location) {
        LaserShowdownConfig config = getGameConfig();
        if (!config.hasCopyLayout() || !isGameplayParticipant(player)) return notInArea(location);
        List<Location> assigned = playerRespawnLocations.get(player.getUniqueId());
        if (assigned != null && !assigned.isEmpty()) {
            for (int arena = 0; arena < areaLocations.size(); arena++) {
                if (config.contains(assigned.getFirst(), arena))
                    return !config.contains(location, arena);
            }
        }
        return getSelectedArenaIndices(areaLocations.size()).stream()
                .noneMatch(arena -> config.contains(location, arena));
    }

    private List<Player> players() {
        return gamePlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    @Override
    public boolean freezeMovementDuringCountdown() {
        // Spawn points can be above the floor. Let gravity finish the landing during selection.
        return false;
    }

    @Override
    public void resetArea() {
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (laserTickTask != null) laserTickTask.cancel();
        for (Player player : players()) clearLaserEffects(player);
        bulletSlowUntil.clear();
        if (zoom != null) zoom.close();
        zoom = null;
        restoreNameTags();
        startGameProgressTask = null;
        laserTickTask = null;
        cleanDroppedItems();

        playerLives.clear();
        firstRoundGunSelectionWindow = false;
        playerWeaponLives.clear();
        playerEnemyHits.clear();
        playerHeadHits.clear();
        locationIterators.clear();
        if (getRunMode() == GameRunMode.DAILY) selectedGuns.clear();
        playerRespawnLocations.clear();
        playerSpawnLocation.clear();
        playerRespawnTick.clear();
        playerLaserHealth.clear();
        weapons.close();
        weapons = new LaserWeaponState();
        playerProtectionUntil.clear();
        aimingUntil.clear();
        laserMovementSpeed.clear();
        laserMovementSlow.clear();
        bulletSlowUntil.clear();
        teamShootTimes.clear();
        playerIndividualKills.clear();
        teamRank.clear();
        World world = getSpectatorSpawnLocation().getWorld();
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        BoundingBox boundingBox =
                new BoundingBox(
                        pos1.getX(),
                        pos1.getY(),
                        pos1.getZ(),
                        pos2.getX(),
                        pos2.getY(),
                        pos2.getZ());
        if (world != null) {
            for (Entity entity : world.getNearbyEntities(boundingBox)) {
                if (entity instanceof Snowball) {
                    entity.remove();
                }
            }
        }
    }

    @Override
    public void startGamePreparation() {
        refreshArenaLocations();
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {
        assignSpawnLocations();

        announceGamePreparation(
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION,
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION_TITLE,
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION_SUBTITLE);

        firstRoundGunSelectionWindow = selectedGuns.isEmpty();
        startGameProgress();
    }

    /** Assigns the complete roster before any player is teleported. */
    private void assignSpawnLocations() {
        List<List<Location>> selectedAreas = selectedArenaLocations();
        for (List<Location> locations : selectedAreas) {
            Collections.shuffle(locations, ThreadLocalRandom.current());
            locationIterators.put(locations, locations.iterator());
        }

        for (ChampionshipTeam championshipTeam : gameTeams) {
            List<List<Location>> teamAreas = new ArrayList<>(selectedAreas);
            Collections.shuffle(teamAreas);

            Iterator<List<Location>> locationI = teamAreas.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!locationI.hasNext()) locationI = teamAreas.iterator();

                playerRespawnLocations.put(uuid, locationI.next());
            }
        }

        for (List<Location> locations : selectedAreas) {
            Iterator<Location> locationIterator = locations.iterator();

            for (Map.Entry<UUID, List<Location>> playerLocationList :
                    playerRespawnLocations.entrySet()) {
                if (locations.equals(playerLocationList.getValue())) {
                    if (!locationIterator.hasNext()) locationIterator = locations.iterator();

                    playerSpawnLocation.put(playerLocationList.getKey(), locationIterator.next());
                }
            }
        }
    }

    public void startGameProgress() {
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayersAndTeleport();
        if (firstRoundGunSelectionWindow) {
            for (Player player : players()) openGunMenu(player);
        }

        startFinalCountdown(
                firstRoundGunSelectionWindow ? 15 : 5,
                MessageConfig.LASER_SHOWDOWN_GAME_START_SOON_TITLE,
                MessageConfig.LASER_SHOWDOWN_GAME_START_TITLE,
                MessageConfig.LASER_SHOWDOWN_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        firstRoundGunSelectionWindow = false;
        for (Player player : players()) player.closeInventory();
        laserTick = 0;
        hideNameTags();
        for (UUID uuid : gamePlayers) {
            playerLives.put(uuid, new LaserShowdownLife(laserTick));
            playerLaserHealth.put(uuid, 100D);
            playerProtectionUntil.put(uuid, laserTick + 80);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
        }

        int duration = matchDurationSeconds();
        startGameProgressTask =
                startRemainingTimer(
                        duration,
                        seconds -> {
                            timer = seconds;
                            updateGameTimerBossBar(
                                    MessageConfig.LASER_SHOWDOWN_ACTION_BAR_COUNT_DOWN.replace(
                                            "%time%", DurationText.bossBarSeconds(timer)),
                                    timer,
                                    duration);
                            calculateCurrentRank();
                        },
                        this::endGame);
        laserTickTask = scheduler.runTaskTimer(plugin, this::tickLaserCombat, 1L, 1L);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        try {
            List<Location> locations = randomArenaLocations();
            return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        } catch (Exception ignored) {
            return gameConfig.getSpectatorSpawnPoint();
        }
    }

    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;

        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (laserTickTask != null) laserTickTask.cancel();
        for (Player player : players()) clearLaserEffects(player);
        if (zoom != null) zoom.close();
        zoom = null;
        restoreNameTags();
        weapons.close();

        for (UUID id : gamePlayers) finishLife(id);
        if (isSettlementAllowed()) calculatePoints();

        announceGameEnd(
                MessageConfig.LASER_SHOWDOWN_GAME_END_TITLE,
                MessageConfig.LASER_SHOWDOWN_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        beginPostGameSettlement();

        resetPlayerHealthFoodEffectLevelInventory();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));

        finishPostGameAfterEndEvent();
    }

    protected void calculatePoints() {
        ArrayList<Map.Entry<ChampionshipTeam, Integer>> list;
        list =
                getRunMode() == GameRunMode.DAILY
                        ? new ArrayList<>(
                                gameTeams.stream()
                                        .map(team -> Map.entry(team, getTeamScore(team)))
                                        .toList())
                        : new ArrayList<>(teamShootTimes.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        int shootTimes = Integer.MIN_VALUE;
        int additionalPoints = 65;
        for (Map.Entry<ChampionshipTeam, Integer> entry : list) {
            if (shootTimes != entry.getValue()) {
                additionalPoints = additionalPoints - 5;
                if (additionalPoints <= 10) additionalPoints = 10;
                shootTimes = entry.getValue();
            }
            for (UUID uuid : entry.getKey().getMembers()) {
                addPlayerPoints(uuid, additionalPoints);
            }
        }

        sendMessageToAllGamePlayers(getTeamPointsRank());

        addPlayerPointsToDatabase();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {

            event.deathMessage(null);
            ChampionshipTeam playerChampionshipTeam =
                    plugin.getTeamManager().getTeamByPlayer(player);
            if (playerChampionshipTeam != null) {
                String message =
                        MessageConfig.LASER_SHOWDOWN_PLAYER_DEATH.replace(
                                "%player%", CoreMessages.formatPlayerName(player));
                sendMessageToAllGamePlayers(message);
            }
        }

        clearLaserEffects(player);
        finishLife(player.getUniqueId());
        playerRespawnTick.put(player.getUniqueId(), laserTick);
        long generation = captureGameGeneration();
        scheduler.runTask(
                plugin,
                () -> {
                    if (!isCurrentParticipantConnection(generation, player)
                            || !gamePlayers.contains(player.getUniqueId())) return;
                    event.getEntity().spigot().respawn();
                    respawnPlayer(player);
                });

        event.getDrops().clear();
        event.setDroppedExp(0);
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

        clearLaserEffects(player);
        if (nameTags != null) nameTags.remove(player.getUniqueId());
        LaserShowdownLife life = playerLives.get(player.getUniqueId());
        if (life != null) {
            life.closeMenu(laserTick);
            recordLifeWeapon(player.getUniqueId());
        }

        ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (playerChampionshipTeam != null) {
            String message =
                    MessageConfig.LASER_SHOWDOWN_PLAYER_LEAVE.replace(
                            "%player%", CoreMessages.formatPlayerName(player));

            sendMessageToAllGamePlayers(message);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.setGameMode(GameMode.ADVENTURE);
            teleportPlayerToSpawnLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN
                || getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.setGameMode(GameMode.ADVENTURE);
            respawnPlayer(player);
            return;
        }
        teleportToLobby(player);
        player.setGameMode(GameMode.ADVENTURE);
    }

    public int getPlayerIndividualKills(Player player) {
        return playerIndividualKills.getOrDefault(player.getUniqueId(), 0);
    }

    public List<String> getCurrentRank() {
        return teamRank;
    }

    /** Authoritative live team score used by the Core-owned sidebar. */
    public int getTeamScore(ChampionshipTeam team) {
        return teamShootTimes.getOrDefault(team, 0);
    }

    /** Includes zero-score teams and uses a stable team-id tiebreaker. */
    public List<ChampionshipTeam> getRankedTeams() {
        List<ChampionshipTeam> ranked = new ArrayList<>(getGameTeams());
        ranked.sort(
                Comparator.comparingInt(this::getTeamScore)
                        .reversed()
                        .thenComparingInt(ChampionshipTeam::getId));
        return List.copyOf(ranked);
    }

    private void calculateCurrentRank() {
        List<String> rank = new ArrayList<>();

        ArrayList<Map.Entry<ChampionshipTeam, Integer>> list;
        list = new ArrayList<>(teamShootTimes.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Collections.reverse(list);

        for (Map.Entry<ChampionshipTeam, Integer> entry : list) {
            String content = entry.getKey().getName() + ": " + entry.getValue();

            rank.add(content);
        }

        teamRank = rank;
    }

    private synchronized void addTeamShootCount(ChampionshipTeam championshipTeam) {
        teamShootTimes.put(championshipTeam, teamShootTimes.getOrDefault(championshipTeam, 0) + 1);
    }

    public void respawnPlayer(Player player) {
        long generation = captureGameGeneration();
        if (!isCurrentParticipantConnection(generation, player)
                || !gamePlayers.contains(player.getUniqueId())
                || player.getGameMode() == GameMode.SPECTATOR
                        && (playerRespawnTick == null
                                || !playerRespawnTick.containsKey(player.getUniqueId()))) return;
        player.setGameMode(GameMode.ADVENTURE);
        plugin.getGameManager().getSpectatorManager().resumeParticipant(player, this);
        UUID id = player.getUniqueId();
        boolean newLife = playerRespawnTick.remove(id) != null || !playerLives.containsKey(id);
        int previousAmmo = weapons.ammo(id, selectedGuns.getOrDefault(id, LaserGun.RIFLE));
        if (newLife) {
            weapons.forget(id);
            finishLife(id);
            playerLives.put(id, new LaserShowdownLife(laserTick));
        }
        playerLaserHealth.put(player.getUniqueId(), 100D);
        playerProtectionUntil.put(player.getUniqueId(), laserTick + 80);
        teleportPlayerToSpawnLocation(player);
        player.setHealth(20);
        givePlayerItem(player);
        if (!newLife) weapons.setAmmo(id, previousAmmo);
        syncGunSelection(player);
        for (UUID uuid : gamePlayers) {
            Player gamePlayer = Bukkit.getPlayer(uuid);
            if (gamePlayer != null) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
                    plugin.getGlowingEntities().setGlowing(gamePlayer, player);
                }
            }
        }

        scheduler.runTaskLater(
                plugin,
                () -> {
                    if (!isCurrentParticipantConnection(generation, player)
                            || !isGameplayParticipant(player)) return;
                    for (UUID uuid : gamePlayers) {
                        Player gamePlayer = Bukkit.getPlayer(uuid);
                        if (gamePlayer != null) {
                            ChampionshipTeam championshipTeam =
                                    plugin.getTeamManager().getTeamByPlayer(uuid);
                            if (championshipTeam != null) {
                                plugin.getGlowingEntities().unsetGlowing(gamePlayer, player);
                            }
                        }
                    }
                },
                60L);
    }

    public synchronized void teleportPlayerToSpawnLocation(Player player) {
        if (!isGameplayParticipant(player)) return;
        // During the rule-introduction phase everyone roams from the introduction spawn point.
        if (isIntroductionPhase()) {
            player.teleport(getPreparationTeleportLocation(getSpectatorSpawnLocation()));
            return;
        }
        List<Location> locations = playerRespawnLocations.get(player.getUniqueId());
        if (locations != null) {
            Iterator<Location> locationIterator = locationIterators.get(locations);
            if (locationIterator != null) {
                if (!locationIterator.hasNext()) {
                    locationIterator = locations.iterator();
                    locationIterators.put(locations, locationIterator);
                }
                player.teleport(locationIterator.next());
            } else {
                player.teleport(
                        locations.get(ThreadLocalRandom.current().nextInt(locations.size())));
            }
        } else {
            List<Location> randomLocations = randomArenaLocations();
            player.teleport(
                    randomLocations.get(
                            ThreadLocalRandom.current().nextInt(randomLocations.size())));
        }
    }

    private void teleportPlayersToSpawnLocation() {
        for (UUID uuid : playerSpawnLocation.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(playerSpawnLocation.get(uuid));
            }
        }
    }

    private void giveItemToAllGamePlayersAndTeleport() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                givePlayerItem(player);
            }
        }
        teleportPlayersToSpawnLocation();
    }

    private void givePlayerItem(Player player) {
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        player.clearActiveItem();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0);
        LaserGun gun =
                selectedGuns.computeIfAbsent(player.getUniqueId(), ignored -> LaserGun.RIFLE);
        equipGun(player, gun);
        playerInventory.setItem(GUN_SELECTOR_SLOT, item("gun-selector"));

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            playerInventory.setHelmet(championshipTeam.getHelmet().clone());
            playerInventory.setChestplate(null);
            playerInventory.setLeggings(null);
            playerInventory.setBoots(null);
        }
        player.setLevel(
                (int) Math.ceil(playerLaserHealth.getOrDefault(player.getUniqueId(), 100D)));
        syncAmmoItem(player, gun);
    }

    private void tickLaserCombat() {
        if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
        laserTick++;
        if (laserTick % 5 == 0 && nameTags != null) nameTags.refresh(gamePlayers);
        for (Player player : players()) {
            UUID id = player.getUniqueId();
            if (playerRespawnTick.containsKey(id)) {
                int remaining = playerRespawnTick.get(id) - laserTick;
                if (remaining <= 0) respawnPlayer(player);
                else if (laserTick % 20 == 0)
                    player.sendTitle(
                            LegacyText.translateColorCodes(
                                    configured(
                                            MessageConfig.LASER_BOX_RESPAWN_TITLE,
                                            "&#fff566Respawning")),
                            LegacyText.translateColorCodes(
                                    configured(
                                                    MessageConfig.LASER_BOX_RESPAWN_SUBTITLE,
                                                    "%seconds%")
                                            .replace(
                                                    "%seconds%",
                                                    Integer.toString((remaining + 19) / 20))),
                            0,
                            25,
                            0);
                continue;
            }
            if (!active(player)) {
                clearLaserEffects(player);
                continue;
            }
            syncGunSelection(player);
            cancelReloadIfNotHolding(player);
            if (!holdsSniper(player) || weapons.reloading(id) || !aiming(player)) endAiming(player);
            LaserGun gun = gun(player.getInventory().getItemInMainHand());
            if (LaserGunMechanics.automatic(gun)
                    && player.isHandRaised()
                    && player.getActiveItemHand() == EquipmentSlot.HAND
                    && !weapons.reloading(id)) fireGun(player, gun);
            if (getGameStageEnum() != GameStageEnum.PROGRESS) return;
            if (weapons.reloading(id) && weapons.reloadRemaining(id, laserTick) == 0)
                completeReload(player);
            if (gun != null) {
                syncAmmoItem(player, gun);
                if (weapons.reloading(id) && laserTick % 10 == 0) showReload(player, gun);
            }
            syncMovementEffects(player, gun);
            player.setLevel((int) Math.ceil(playerLaserHealth.getOrDefault(id, 100D)));
        }
    }

    private void clearLaserEffects(Player player) {
        weapons.cancelReload(player.getUniqueId());
        endAiming(player);
        if (laserMovementSpeed.remove(player.getUniqueId()))
            player.removePotionEffect(PotionEffectType.SPEED);
        if (laserMovementSlow.remove(player.getUniqueId()))
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        bulletSlowUntil.remove(player.getUniqueId());
        for (LaserGun gun : LaserGun.values()) player.setCooldown(gun.material(), 0);
    }

    private void syncMovementEffects(Player player, LaserGun gun) {
        UUID id = player.getUniqueId();
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean empty =
                ("ammo".equals(type(held)) || held == null || held.getType().isAir())
                        && (offHand == null || offHand.getType().isAir());
        int speed = LaserGunMechanics.speedAmplifier(gun, empty);
        boolean bulletSlow = bulletSlowUntil.getOrDefault(id, 0) > laserTick;
        if (!bulletSlow) bulletSlowUntil.remove(id);
        if (bulletSlow) speed = LaserGunMechanics.hitSpeedAmplifier(speed);
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed < 0) {
            if (current != null) player.removePotionEffect(PotionEffectType.SPEED);
        } else if (current == null
                || current.getAmplifier() != speed
                || current.getDuration() < 20) {
            if (current != null && current.getAmplifier() != speed)
                player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, 40, speed, true, false, false));
        }
        laserMovementSpeed.add(id);
        int slow = LaserGunMechanics.slownessAmplifier(gun, aiming(player));
        if (bulletSlow) slow = LaserGunMechanics.hitSlownessAmplifier(slow);
        PotionEffect currentSlow = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow >= 0) {
            if (currentSlow == null
                    || currentSlow.getAmplifier() != slow
                    || currentSlow.getDuration() < 20) {
                if (currentSlow != null && currentSlow.getAmplifier() != slow)
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, 40, slow, true, false, false));
            }
            laserMovementSlow.add(id);
        } else if (laserMovementSlow.remove(id))
            player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private ItemStack item(String type) {
        Material material =
                type.equals(GUN_SELECTOR_TYPE) ? Material.COMPASS : Material.PRISMARINE_SHARD;
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        String name =
                type.equals(GUN_SELECTOR_TYPE)
                        ? MessageConfig.codeText("laser-box-area.item-13")
                        : MessageConfig.codeText("laser-box-area.item-5");
        meta.displayName(
                Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(
                List.of(
                        Component.text(
                                        MessageConfig.codeText(
                                                "laser-showdown-team-area.gun-selector-lore"),
                                        NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(laserItemKey, PersistentDataType.STRING, type);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        result.setItemMeta(meta);
        return result;
    }

    private String type(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta()
                .getPersistentDataContainer()
                .get(laserItemKey, PersistentDataType.STRING);
    }

    private LaserGun gun(String type) {
        if (type == null) return null;
        return LaserGun.fromId(type.startsWith("gun:") ? type : type);
    }

    private LaserGun gun(ItemStack stack) {
        String type = type(stack);
        if (type != null) {
            LaserGun parsed = gun(type);
            if (parsed != null) return parsed;
        }
        return stack == null ? null : LaserGun.fromMaterial(stack.getType());
    }

    private static String configured(String value, String fallback) {
        return value == null ? fallback : value;
    }

    void openGunMenu(Player player) {
        if (!isGameplayParticipant(player)) return;
        GameStageEnum stage = getGameStageEnum();
        LaserShowdownLife life = playerLives.get(player.getUniqueId());
        if (player.getOpenInventory().getTopInventory().getHolder()
                        instanceof LaserShowdownGunMenuHolder current
                && current.area() == this
                && current.life() == life) return;
        if (stage == GameStageEnum.PROGRESS) {
            if (life == null || !life.openMenu(laserTick)) {
                syncGunSelection(player);
                return;
            }
        } else if (stage != GameStageEnum.PREPARATION && stage != GameStageEnum.COUNTDOWN) return;
        LaserShowdownGunMenuHolder holder = new LaserShowdownGunMenuHolder(this, life);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        LegacyText.component(
                                configured(
                                        MessageConfig.LASER_BOX_GUN_SELECTION_TITLE,
                                        "&#fff566Choose equipment")));
        holder.inventory(inventory);
        LaserGun selected = selectedGuns.get(player.getUniqueId());
        LaserGun[] guns = LaserGun.values();
        for (int i = 0; i < guns.length; i++)
            inventory.setItem(
                    10 + i, LaserGunItems.gunItem(plugin, laserItemKey, guns[i], selected));
        player.openInventory(inventory);
        player.sendActionBar(
                LegacyText.component(
                        configured(
                                MessageConfig.LASER_BOX_GUN_SELECTION_SUBTITLE,
                                "&#edededChoose one weapon")));
    }

    void selectGunFromMenu(Player player, InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                        instanceof LaserShowdownGunMenuHolder holder)
                || holder.area() != this
                || !isGameplayParticipant(player)
                || (getGameStageEnum() == GameStageEnum.PROGRESS
                        && (holder.life() == null
                                || holder.life() != playerLives.get(player.getUniqueId())
                                || !holder.life().canSelect(laserTick)))
                || (getGameStageEnum() != GameStageEnum.PROGRESS
                        && getGameStageEnum() != GameStageEnum.PREPARATION
                        && getGameStageEnum() != GameStageEnum.COUNTDOWN)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getView().getTopInventory().getItem(event.getRawSlot());
        LaserGun selected = gun(clicked);
        if (selected == null) return;
        selectedGuns.put(player.getUniqueId(), selected);
        equipGun(player, selected);
        player.closeInventory();
        player.sendMessage(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_GUN_SELECTION_CHOSEN,
                                        "&#a8ff78Equipped %gun%")
                                .replace("%gun%", LaserGunItems.gunName(selected))));
    }

    void closeGunMenu(Player player, LaserShowdownGunMenuHolder holder) {
        LaserShowdownLife life = playerLives.get(player.getUniqueId());
        if (holder.area() != this || holder.life() != life || life == null) return;
        life.closeMenu(laserTick);
        if (isGameplayParticipant(player)) syncGunSelection(player);
    }

    private void syncGunSelection(Player player) {
        LaserShowdownLife life = playerLives.get(player.getUniqueId());
        if (life == null) return;
        life.expire(laserTick);
        recordLifeWeapon(player.getUniqueId());
        if (life.locked()
                && GUN_SELECTOR_TYPE.equals(type(player.getInventory().getItem(GUN_SELECTOR_SLOT))))
            player.getInventory().setItem(GUN_SELECTOR_SLOT, null);
    }

    private void lockGunSelection(Player player) {
        LaserShowdownLife life = playerLives.get(player.getUniqueId());
        if (life == null) return;
        life.lock();
        syncGunSelection(player);
        if (player.getOpenInventory().getTopInventory().getHolder()
                        instanceof LaserShowdownGunMenuHolder holder
                && holder.area() == this
                && holder.life() == life) player.closeInventory();
    }

    private void finishLife(UUID player) {
        LaserShowdownLife life = playerLives.get(player);
        if (life == null) return;
        life.lock();
        recordLifeWeapon(player);
    }

    private void recordLifeWeapon(UUID player) {
        LaserShowdownLife life = playerLives.get(player);
        if (life == null || !life.takeUsage()) return;
        LaserGun gun = selectedGuns.getOrDefault(player, LaserGun.RIFLE);
        playerWeaponLives
                .computeIfAbsent(player, ignored -> new EnumMap<>(LaserGun.class))
                .merge(gun, 1L, Long::sum);
    }

    public LaserShowdownStats getPlayerCombatStats(UUID player) {
        Map<LaserGun, Long> lives =
                playerWeaponLives.getOrDefault(player, new EnumMap<>(LaserGun.class));
        return new LaserShowdownStats(
                1,
                playerIndividualKills.getOrDefault(player, 0),
                playerEnemyHits.getOrDefault(player, 0L),
                playerHeadHits.getOrDefault(player, 0L),
                lives.getOrDefault(LaserGun.SNIPER, 0L),
                lives.getOrDefault(LaserGun.RIFLE, 0L),
                lives.getOrDefault(LaserGun.SMG, 0L),
                lives.getOrDefault(LaserGun.MACHINE, 0L),
                lives.getOrDefault(LaserGun.SHOTGUN, 0L));
    }

    boolean holdsGun(Player player) {
        return gun(player.getInventory().getItemInMainHand()) != null;
    }

    boolean holdsSniper(Player player) {
        return gun(player.getInventory().getItemInMainHand()) == LaserGun.SNIPER;
    }

    boolean holdsRightClickAutomaticGun(Player player) {
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        return gun == LaserGun.SMG || gun == LaserGun.MACHINE;
    }

    boolean holdsGunSelector(Player player) {
        return GUN_SELECTOR_TYPE.equals(type(player.getInventory().getItemInMainHand()));
    }

    void beginAiming(Player player) {
        if (!active(player) || !holdsSniper(player) || weapons.reloading(player.getUniqueId())) {
            endAiming(player);
            return;
        }
        aimingUntil.put(
                player.getUniqueId(),
                Bukkit.getCurrentTick() + LaserGunMechanics.AIM_INPUT_GRACE_TICKS);
        if (zoom == null) zoom = new LaserZoom(plugin);
        zoom.setAiming(player, true);
    }

    private boolean aiming(Player player) {
        return aimingUntil.getOrDefault(player.getUniqueId(), 0) > Bukkit.getCurrentTick();
    }

    private void endAiming(Player player) {
        if (aimingUntil.remove(player.getUniqueId()) != null && zoom != null)
            zoom.setAiming(player, false);
    }

    void fireFromLeftClick(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == LaserGun.SNIPER) fireGun(player, gun);
    }

    void fireFromRightClick(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun != null && gun != LaserGun.SNIPER) fireGun(player, gun);
    }

    void reloadFromSwap(Player player) {
        if (!active(player)) return;
        LaserGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == null) return;
        cancelReloadIfNotHolding(player);
        UUID id = player.getUniqueId();
        if (weapons.reloading(id)) showReload(player, gun);
        else if (weapons.requestReload(id, gun, laserTick, active(player)))
            showReloadStarted(player, gun);
    }

    private void showReloadStarted(Player player, LaserGun gun) {
        endAiming(player);
        World world = player.getWorld();
        if (world != null)
            world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.85F, 0.9F);
        showReload(player, gun);
    }

    private void showReload(Player player, LaserGun gun) {
        endAiming(player);
        int remaining = weapons.reloadRemaining(player.getUniqueId(), laserTick);
        player.setCooldown(gun.material(), remaining);
        player.sendActionBar(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_RELOAD,
                                        "&#fff566%gun% &#bababa• Reloading")
                                .replace("%gun%", LaserGunItems.gunName(gun))
                                .replace(
                                        "%seconds%",
                                        String.format(Locale.ROOT, "%.1f", remaining / 20.0D))));
    }

    private void cancelReload(Player player) {
        LaserGun gun = weapons.cancelReload(player.getUniqueId());
        if (gun != null) player.setCooldown(gun.material(), 0);
    }

    private void cancelReloadIfNotHolding(Player player) {
        LaserGun gun = weapons.reloadGun(player.getUniqueId());
        if (gun != null && gun != gun(player.getInventory().getItemInMainHand()))
            cancelReload(player);
    }

    void heldItemChanged(Player player, int newSlot) {
        LaserGun next = gun(player.getInventory().getItem(newSlot));
        LaserGun reload = weapons.reloadGun(player.getUniqueId());
        if (reload != null && reload != next) cancelReload(player);
        if (next != LaserGun.SNIPER) endAiming(player);
    }

    private boolean active(Player player) {
        return getGameStageEnum() == GameStageEnum.PROGRESS
                && isGameplayParticipant(player)
                && !playerRespawnTick.containsKey(player.getUniqueId())
                && player.getGameMode() == GameMode.ADVENTURE
                && !notInArea(player.getLocation())
                && !isManagedSpectator(player);
    }

    private void equipGun(Player player, LaserGun gun) {
        player.clearActiveItem();
        endAiming(player);
        cancelReload(player);
        player.setCooldown(gun.material(), 0);
        selectedGuns.put(player.getUniqueId(), gun);
        player.getInventory().setItem(0, LaserGunItems.gunItem(plugin, laserItemKey, gun, null));
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        weapons.equip(player.getUniqueId(), gun);
        syncAmmoItem(player, gun);
    }

    private void syncAmmoItem(Player player, LaserGun gun) {
        LaserGunItems.syncAmmo(
                player, laserItemKey, AMMO_SLOT, weapons.ammo(player.getUniqueId(), gun));
    }

    private void fireGun(Player shooter, LaserGun weapon) {
        if (weapon == null || !active(shooter)) return;
        UUID id = shooter.getUniqueId();
        cancelReloadIfNotHolding(shooter);
        if (weapons.reloading(id) && weapons.reloadRemaining(id, laserTick) == 0)
            completeReload(shooter);
        boolean scopedShot = aiming(shooter);
        boolean wasReloading = weapons.reloading(id);
        LaserWeaponState.FireResult result = weapons.shoot(id, weapon, laserTick, active(shooter));
        if (result == LaserWeaponState.FireResult.RELOADING) {
            if (!wasReloading) showReloadStarted(shooter, weapon);
            else showReload(shooter, weapon);
            return;
        }
        if (result != LaserWeaponState.FireResult.FIRED) return;
        lockGunSelection(shooter);
        syncAmmoItem(shooter, weapon);
        if (!wasReloading && weapons.reloading(id)) showReloadStarted(shooter, weapon);
        else {
            shooter.sendActionBar(
                    LegacyText.component(
                            configured(
                                            MessageConfig.LASER_BOX_AMMO,
                                            "&#fff566%gun% &#bababa•"
                                                    + " &#ff6b26%ammo%&#ededed/%magazine%")
                                    .replace("%gun%", LaserGunItems.gunName(weapon))
                                    .replace("%ammo%", Integer.toString(weapons.ammo(id, weapon)))
                                    .replace("%magazine%", Integer.toString(weapon.magazine()))));
        }
        LaserWeaponState shootingWeapons = weapons;
        long generation = captureGameGeneration();
        LaserShotEngine.fire(
                shooter,
                weapon,
                weapons.burstShot(id),
                scopedShot,
                new LaserShotEngine.Context() {
                    @Override
                    public boolean current() {
                        return weapons == shootingWeapons
                                && captureGameGeneration() == generation
                                && active(shooter);
                    }

                    @Override
                    public boolean collidable(Entity entity) {
                        return entity instanceof Player player && active(player);
                    }

                    @Override
                    public boolean enemy(Player player) {
                        return LaserShowdownTeamArea.this.enemy(shooter, player);
                    }

                    @Override
                    public void beam(World world, Location eye, Vector direction, double distance) {
                        Set<Player> viewers = new LinkedHashSet<>(players());
                        for (UUID uuid : getSpectatorUniqueIds()) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null) viewers.add(player);
                        }
                        LaserBeam.draw(world, eye, direction, distance, viewers);
                    }

                    @Override
                    public void pelletHit(Player player, LaserGun.HitRegion region) {
                        recordEnemyHit(id, region);
                    }

                    @Override
                    public void damage(Player player, double amount, LaserGun.HitRegion region) {
                        applyLaserHit(shooter, player, amount, weapon, region);
                    }
                });
    }

    private boolean enemy(Player attacker, Player victim) {
        if (!active(attacker) || !active(victim)) return false;
        ChampionshipTeam attackerTeam = plugin.getTeamManager().getTeamByPlayer(attacker);
        ChampionshipTeam victimTeam = plugin.getTeamManager().getTeamByPlayer(victim);
        return attackerTeam != null && victimTeam != null && !attackerTeam.equals(victimTeam);
    }

    private void recordEnemyHit(UUID attacker, LaserGun.HitRegion region) {
        playerEnemyHits.merge(attacker, 1L, Long::sum);
        if (region == LaserGun.HitRegion.HEAD) playerHeadHits.merge(attacker, 1L, Long::sum);
    }

    private void applyLaserHit(
            Player attacker,
            Player victim,
            double damage,
            LaserGun weapon,
            LaserGun.HitRegion region) {
        if (!enemy(attacker, victim)) return;
        ChampionshipTeam attackerTeam = plugin.getTeamManager().getTeamByPlayer(attacker);
        UUID victimId = victim.getUniqueId();
        if (playerProtectionUntil.getOrDefault(victimId, 0) > laserTick) return;
        bulletSlowUntil.put(victimId, laserTick + 20);
        syncMovementEffects(victim, weapon);
        lockGunSelection(victim);
        double health = playerLaserHealth.getOrDefault(victimId, 100D) - damage;
        if (health > 0) {
            playerLaserHealth.put(victimId, health);
            victim.setLevel((int) Math.ceil(health));
            victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.8F);
            return;
        }
        clearLaserEffects(victim);
        playerLaserHealth.remove(victimId);
        playerRespawnTick.put(victimId, laserTick + 80);
        victim.getInventory().clear();
        victim.setLevel(0);
        plugin.getGameManager().getSpectatorManager().enterParticipantSpectator(victim, this);
        ChampionshipTeam killerTeam = attackerTeam;
        if (killerTeam != null)
            LaserKillFeedback.burst(
                    victim,
                    TeamColors.fromHex(killerTeam.getColorCode()),
                    laserItemKey,
                    region == LaserGun.HitRegion.HEAD);
        int kills = playerIndividualKills.getOrDefault(attacker.getUniqueId(), 0) + 1;
        playerIndividualKills.put(attacker.getUniqueId(), kills);
        if (killerTeam != null) addTeamShootCount(killerTeam);
        addPlayerPoints(attacker.getUniqueId(), 4);
        long feedbackGeneration = captureGameGeneration();
        UUID attackerId = attacker.getUniqueId();
        LaserKillFeedback.play(
                attacker,
                kills,
                plugin,
                () -> isKillFeedbackCurrent(feedbackGeneration, attackerId));
        String template =
                region == LaserGun.HitRegion.HEAD
                        ? configured(
                                MessageConfig.LASER_BOX_HEADSHOT,
                                "&#ff6b26%attacker% &#bababaheadshot &#ededed%victim%")
                        : configured(
                                MessageConfig.LASER_BOX_HIT,
                                "&#ff6b26%attacker% &#bababaeliminated &#ededed%victim%");
        sendMessageToAllGamePlayers(
                template.replace("%attacker%", CoreMessages.formatPlayerName(attacker))
                        .replace("%victim%", CoreMessages.formatPlayerName(victim))
                        .replace("%gun%", LaserGunItems.gunName(weapon)));
        if (reachedKillTarget(kills, getTeamScore(attackerTeam))) endGame();
    }

    /** The decisive kill's four notes may finish in settlement, but never in a later run. */
    boolean isKillFeedbackCurrent(long generation, UUID player) {
        GameStageEnum stage = getGameStageEnum();
        return captureGameGeneration() == generation
                && !isDisposed()
                && (stage == GameStageEnum.PROGRESS || stage == GameStageEnum.END)
                && gamePlayers.contains(player);
    }

    int matchDurationSeconds() {
        return getRunMode() == GameRunMode.DAILY
                ? DAILY_DURATION_SECONDS
                : getGameConfig().getTimer();
    }

    boolean reachedKillTarget(int individualKills, int teamKills) {
        return getRunMode() == GameRunMode.DAILY
                ? individualKills >= DAILY_KILL_TARGET
                : teamKills >= 100;
    }

    private void completeReload(Player player) {
        UUID id = player.getUniqueId();
        LaserGun gun = weapons.reloadGun(id);
        if (!active(player)
                || gun == null
                || gun != gun(player.getInventory().getItemInMainHand())
                || !weapons.completeReload(id, gun, laserTick)) return;
        player.setCooldown(gun.material(), 0);
        syncAmmoItem(player, gun);
        World world = player.getWorld();
        if (world != null)
            world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.9F, 1.05F);
        player.sendActionBar(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_RELOAD_COMPLETE,
                                        "&#a8ff78%gun% reloaded")
                                .replace("%gun%", LaserGunItems.gunName(gun))));
    }

    private void hideNameTags() {
        restoreNameTags();
        nameTags = new LaserNameTags(plugin);
        nameTags.refresh(gamePlayers);
    }

    private void restoreNameTags() {
        if (nameTags != null) nameTags.close();
        nameTags = null;
    }

    @Override
    public LaserShowdownConfig getGameConfig() {
        return (LaserShowdownConfig) gameConfig;
    }

    @Override
    public void dispose() {
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (laserTickTask != null) laserTickTask.cancel();
        for (Player player : players()) clearLaserEffects(player);
        if (zoom != null) zoom.close();
        zoom = null;
        restoreNameTags();
        weapons.close();
        playerLives.clear();
        firstRoundGunSelectionWindow = false;
        playerWeaponLives.clear();
        playerEnemyHits.clear();
        playerHeadHits.clear();
        selectedGuns.clear();
        locationIterators.clear();
        super.dispose();
    }

    @Override
    public LaserShowdownHandler getGameHandler() {
        return (LaserShowdownHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
