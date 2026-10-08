package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.laserbox.mechanics.LaserBoxKillFeedback;
import ink.ziip.championshipscore.api.game.laserbox.model.LaserBoxGun;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import io.papermc.paper.datacomponent.item.UseEffects;

import lombok.Getter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LaserShowdownTeamArea extends BaseMultiTeamGameInstance {
    private final List<List<Location>> areaLocations = new ArrayList<>();
    private final Map<UUID, List<Location>> playerRespawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerSpawnLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRespawnTick = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerLaserHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerAmmo = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerNextShot = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerLastShot = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerBurst = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerReloadUntil = new ConcurrentHashMap<>();
    private final Map<UUID, LaserBoxGun> playerReloadGun = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerProtectionUntil = new ConcurrentHashMap<>();
    private final Map<UUID, LaserBoxGun> selectedGuns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> aimingUntil = new ConcurrentHashMap<>();
    private final Set<UUID> laserMovementSpeed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> laserMovementSlow = ConcurrentHashMap.newKeySet();
    private final Map<List<Location>, Iterator<Location>> locationIterators =
            new IdentityHashMap<>();
    private final Map<ChampionshipTeam, Integer> teamShootTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerIndividualKills = new ConcurrentHashMap<>();
    private List<String> teamRank = new ArrayList<>();
    @Getter private int timer;
    private BukkitTask startGameProgressTask;
    private BukkitTask laserTickTask;
    private int laserTick;
    private boolean firstRoundGunSelectionWindow;
    private final NamespacedKey laserItemKey;
    private static final int GUN_SELECTOR_SLOT = 8;
    private static final int AMMO_SLOT = 4;
    private static final String GUN_SELECTOR_TYPE = "gun-selector";
    private static final Particle.DustOptions LASER_PARTICLE =
            new Particle.DustOptions(Color.fromRGB(0xE8FFFF), 0.35F);

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

        ConfigurationSection configurationSection = getGameConfig().getPlayerSpawnPoints();
        for (String areaName :
                configurationSection.getKeys(false).stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()) {
            List<Location> locations = new ArrayList<>();
            for (String stringLocation : configurationSection.getStringList(areaName)) {
                locations.add(LocationConfig.readLocation(stringLocation));
            }

            areaLocations.add(locations);
        }
    }

    public int getArenaCount() {
        return areaLocations.size();
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

    private List<Player> players() {
        return gamePlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    @Override
    public void resetArea() {
        if (startGameProgressTask != null) startGameProgressTask.cancel();
        if (laserTickTask != null) laserTickTask.cancel();
        for (Player player : players()) clearLaserEffects(player);
        startGameProgressTask = null;
        laserTickTask = null;
        cleanDroppedItems();

        playerRespawnLocations.clear();
        playerSpawnLocation.clear();
        playerRespawnTick.clear();
        playerLaserHealth.clear();
        playerAmmo.clear();
        playerNextShot.clear();
        playerLastShot.clear();
        playerBurst.clear();
        playerReloadUntil.clear();
        playerReloadGun.clear();
        playerProtectionUntil.clear();
        aimingUntil.clear();
        laserMovementSpeed.clear();
        laserMovementSlow.clear();
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
        setGameStageEnum(GameStageEnum.PREPARATION);

        // Rule-introduction phase (if configured): gather players at the introduction spawn point
        // and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

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

        teleportPlayersToSpawnLocation();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION,
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION_TITLE,
                MessageConfig.LASER_SHOWDOWN_START_PREPARATION_SUBTITLE);

        firstRoundGunSelectionWindow = selectedGuns.isEmpty();
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                givePlayerItem(player);
                if (firstRoundGunSelectionWindow) openGunMenu(player);
            }
        }

        startGameProgress();
    }

    public void startGameProgress() {
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayersAndTeleport();

        startFinalCountdown(
                MessageConfig.LASER_SHOWDOWN_GAME_START_SOON_TITLE,
                MessageConfig.LASER_SHOWDOWN_GAME_START_TITLE,
                MessageConfig.LASER_SHOWDOWN_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        firstRoundGunSelectionWindow = false;
        for (Player player : players()) player.closeInventory();
        laserTick = 0;
        for (UUID uuid : gamePlayers) {
            playerLaserHealth.put(uuid, 100D);
            playerProtectionUntil.put(uuid, laserTick + 80);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
        }

        startGameProgressTask =
                startRemainingTimer(
                        getGameConfig().getTimer(),
                        seconds -> {
                            timer = seconds;
                            updateGameTimerBossBar(
                                    MessageConfig.LASER_SHOWDOWN_ACTION_BAR_COUNT_DOWN.replace(
                                            "%time%", String.valueOf(timer)),
                                    timer,
                                    getGameConfig().getTimer());
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
        list = new ArrayList<>(teamShootTimes.entrySet());
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

        int times = teamShootTimes.get(championshipTeam);
        if (times == 100) {
            endGame();
        }
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
        playerRespawnTick.remove(player.getUniqueId());
        playerLaserHealth.put(player.getUniqueId(), 100D);
        playerProtectionUntil.put(player.getUniqueId(), laserTick + 80);
        teleportPlayerToSpawnLocation(player);
        player.setHealth(20);
        givePlayerItem(player);
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
        LaserBoxGun gun =
                selectedGuns.computeIfAbsent(player.getUniqueId(), ignored -> LaserBoxGun.RIFLE);
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
            if (playerReloadGun.containsKey(id)
                    && playerReloadUntil.getOrDefault(id, Integer.MAX_VALUE) <= laserTick)
                completeReload(player);
            if (aimingUntil.getOrDefault(id, 0) <= laserTick) aimingUntil.remove(id);
            LaserBoxGun gun = gun(player.getInventory().getItemInMainHand());
            if (gun != null
                    && gun != LaserBoxGun.SNIPER
                    && player.isHandRaised()
                    && player.getActiveItemHand() == EquipmentSlot.HAND
                    && !playerReloadGun.containsKey(id)) fireGun(player, gun);
            if (gun != null) syncAmmoItem(player, gun);
            syncMovementEffects(player, gun);
            player.setLevel((int) Math.ceil(playerLaserHealth.getOrDefault(id, 100D)));
        }
    }

    private void clearLaserEffects(Player player) {
        playerReloadGun.remove(player.getUniqueId());
        playerReloadUntil.remove(player.getUniqueId());
        aimingUntil.remove(player.getUniqueId());
        if (laserMovementSpeed.remove(player.getUniqueId()))
            player.removePotionEffect(PotionEffectType.SPEED);
        if (laserMovementSlow.remove(player.getUniqueId()))
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        for (LaserBoxGun gun : LaserBoxGun.values()) player.setCooldown(gun.material(), 0);
    }

    private void syncMovementEffects(Player player, LaserBoxGun gun) {
        UUID id = player.getUniqueId();
        int speed =
                gun == LaserBoxGun.SMG ? 1 : gun == null ? 1 : gun == LaserBoxGun.MACHINE ? -1 : 0;
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
        int slow =
                gun == LaserBoxGun.SNIPER && aimingUntil.getOrDefault(id, 0) > laserTick
                        ? 1
                        : gun == LaserBoxGun.SNIPER ? 0 : -1;
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
                                        MessageConfig.codeText("laser-box-area.item-14"),
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

    private LaserBoxGun gun(String type) {
        if (type == null) return null;
        return LaserBoxGun.fromId(type.startsWith("gun:") ? type : type);
    }

    private LaserBoxGun gun(ItemStack stack) {
        String type = type(stack);
        if (type != null) {
            LaserBoxGun parsed = gun(type);
            if (parsed != null) return parsed;
        }
        return stack == null ? null : LaserBoxGun.fromMaterial(stack.getType());
    }

    private String gunName(LaserBoxGun gun) {
        return switch (gun) {
            case SNIPER -> configured(MessageConfig.LASER_BOX_GUN_SNIPER_NAME, "Sniper Rifle");
            case RIFLE -> configured(MessageConfig.LASER_BOX_GUN_RIFLE_NAME, "Assault Rifle");
            case SMG -> configured(MessageConfig.LASER_BOX_GUN_SMG_NAME, "SMG");
            case MACHINE -> configured(MessageConfig.LASER_BOX_GUN_MACHINE_NAME, "LMG");
            case SHOTGUN -> configured(MessageConfig.LASER_BOX_GUN_SHOTGUN_NAME, "Shotgun");
        };
    }

    private String gunDescription(LaserBoxGun gun) {
        return switch (gun) {
            case SNIPER ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SNIPER_DESCRIPTION,
                            "Accurate long-range fire");
            case RIFLE ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_RIFLE_DESCRIPTION,
                            "A reliable all-round weapon");
            case SMG ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SMG_DESCRIPTION, "Fast close-range fire");
            case MACHINE ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_MACHINE_DESCRIPTION, "Sustained firepower");
            case SHOTGUN ->
                    configured(
                            MessageConfig.LASER_BOX_GUN_SHOTGUN_DESCRIPTION, "Close-range burst");
        };
    }

    private ItemStack gunItem(LaserBoxGun gun, LaserBoxGun selected) {
        ItemStack result = new ItemStack(gun.material());
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        meta.displayName(LegacyText.component(gunName(gun)));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyText.component(gunDescription(gun)));
        String control =
                gun == LaserBoxGun.SNIPER
                        ? "laser-box-area.item-15"
                        : gun == LaserBoxGun.SMG || gun == LaserBoxGun.MACHINE
                                ? "laser-box-area.item-16"
                                : "laser-box-area.item-8";
        lore.add(LegacyText.component(MessageConfig.codeText(control)));
        lore.add(
                LegacyText.component(
                        configured(MessageConfig.LASER_BOX_GUN_MAGAZINE, "&#bababaLimited ammo")
                                .replace("%magazine%", Integer.toString(gun.magazine()))
                                .replace(
                                        "%seconds%",
                                        String.format(
                                                Locale.ROOT, "%.1f", gun.reloadTicks() / 20.0D))));
        if (selected == gun)
            lore.add(
                    LegacyText.component(
                            configured(
                                    MessageConfig.LASER_BOX_GUN_SELECTED, "&#a8ff78✓ Equipped")));
        meta.lore(lore);
        meta.getPersistentDataContainer()
                .set(laserItemKey, PersistentDataType.STRING, "gun:" + gun.id());
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        result.setItemMeta(meta);
        if (gun == LaserBoxGun.SMG || gun == LaserBoxGun.MACHINE) {
            result.setData(
                    DataComponentTypes.KINETIC_WEAPON,
                    KineticWeapon.kineticWeapon()
                            .delayTicks(Integer.MAX_VALUE)
                            .forwardMovement(0.0F)
                            .damageMultiplier(0.0F));
            result.setData(
                    DataComponentTypes.USE_EFFECTS,
                    UseEffects.useEffects().canSprint(true).speedMultiplier(1.0F));
        }
        return result;
    }

    private static String configured(String value, String fallback) {
        return value == null ? fallback : value;
    }

    void openGunMenu(Player player) {
        if (!gamePlayers.contains(player.getUniqueId())
                || getGameStageEnum() == GameStageEnum.PROGRESS) return;
        LaserShowdownGunMenuHolder holder = new LaserShowdownGunMenuHolder(this);
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        LegacyText.component(
                                configured(
                                        MessageConfig.LASER_BOX_GUN_SELECTION_TITLE,
                                        "&#fff566Choose equipment")));
        holder.inventory(inventory);
        LaserBoxGun selected = selectedGuns.get(player.getUniqueId());
        LaserBoxGun[] guns = LaserBoxGun.values();
        for (int i = 0; i < guns.length; i++) inventory.setItem(10 + i, gunItem(guns[i], selected));
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
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getView().getTopInventory().getItem(event.getRawSlot());
        LaserBoxGun selected = gun(clicked);
        if (selected == null) return;
        selectedGuns.put(player.getUniqueId(), selected);
        equipGun(player, selected);
        player.closeInventory();
        player.sendMessage(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_GUN_SELECTION_CHOSEN,
                                        "&#a8ff78Equipped %gun%")
                                .replace("%gun%", gunName(selected))));
    }

    boolean holdsGun(Player player) {
        return gun(player.getInventory().getItemInMainHand()) != null;
    }

    boolean holdsSniper(Player player) {
        return gun(player.getInventory().getItemInMainHand()) == LaserBoxGun.SNIPER;
    }

    boolean holdsRightClickAutomaticGun(Player player) {
        LaserBoxGun gun = gun(player.getInventory().getItemInMainHand());
        return gun == LaserBoxGun.SMG || gun == LaserBoxGun.MACHINE;
    }

    boolean holdsGunSelector(Player player) {
        return GUN_SELECTOR_TYPE.equals(type(player.getInventory().getItemInMainHand()));
    }

    void beginAiming(Player player) {
        if (!active(player) || !holdsSniper(player)) return;
        aimingUntil.put(player.getUniqueId(), laserTick + 7);
    }

    void fireFromLeftClick(Player player) {
        if (!active(player)) return;
        LaserBoxGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == LaserBoxGun.SNIPER) fireGun(player, gun);
    }

    void fireFromRightClick(Player player) {
        if (!active(player)) return;
        LaserBoxGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun != null && gun != LaserBoxGun.SNIPER) fireGun(player, gun);
    }

    void reloadFromSwap(Player player) {
        if (!active(player)) return;
        LaserBoxGun gun = gun(player.getInventory().getItemInMainHand());
        if (gun == null) return;
        UUID id = player.getUniqueId();
        if (!playerReloadUntil.containsKey(id)
                && playerAmmo.getOrDefault(id, gun.magazine()) < gun.magazine()) {
            playerReloadGun.put(id, gun);
            playerReloadUntil.put(id, laserTick + gun.reloadTicks());
            player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.85F, 0.9F);
            player.sendActionBar(
                    LegacyText.component(
                            configured(
                                            MessageConfig.LASER_BOX_RELOAD,
                                            "&#fff566%gun% &#bababa• Reloading")
                                    .replace("%gun%", gunName(gun))
                                    .replace(
                                            "%seconds%",
                                            String.format(
                                                    Locale.ROOT,
                                                    "%.1f",
                                                    gun.reloadTicks() / 20.0D))));
        }
    }

    void heldItemChanged(Player player, int newSlot) {
        UUID id = player.getUniqueId();
        LaserBoxGun reload = playerReloadGun.get(id);
        if (reload == null) return;
        if (reload != gun(player.getInventory().getItem(newSlot))) {
            playerReloadGun.remove(id);
            playerReloadUntil.remove(id);
        }
    }

    private boolean active(Player player) {
        return getGameStageEnum() == GameStageEnum.PROGRESS
                && gamePlayers.contains(player.getUniqueId())
                && player.getGameMode() == GameMode.ADVENTURE
                && !notInArea(player.getLocation())
                && !isManagedSpectator(player);
    }

    private void equipGun(Player player, LaserBoxGun gun) {
        selectedGuns.put(player.getUniqueId(), gun);
        player.getInventory().setItem(0, gunItem(gun, null));
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        playerAmmo.put(player.getUniqueId(), gun.magazine());
        playerReloadUntil.remove(player.getUniqueId());
        playerReloadGun.remove(player.getUniqueId());
        syncAmmoItem(player, gun);
    }

    private void syncAmmoItem(Player player, LaserBoxGun gun) {
        int ammo = playerAmmo.getOrDefault(player.getUniqueId(), gun.magazine());
        if (ammo <= 0) {
            ItemStack current = player.getInventory().getItem(AMMO_SLOT);
            if (current != null && "ammo".equals(type(current)))
                player.getInventory().setItem(AMMO_SLOT, null);
            return;
        }
        ItemStack current = player.getInventory().getItem(AMMO_SLOT);
        if (current == null
                || current.getType() != Material.SPECTRAL_ARROW
                || !"ammo".equals(type(current))) {
            ItemStack stack = new ItemStack(Material.SPECTRAL_ARROW, ammo);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(
                        LegacyText.component(
                                configured(MessageConfig.LASER_BOX_AMMO_ITEM, "&#fff566Ammo")));
                meta.getPersistentDataContainer()
                        .set(laserItemKey, PersistentDataType.STRING, "ammo");
                stack.setItemMeta(meta);
            }
            player.getInventory().setItem(AMMO_SLOT, stack);
        } else current.setAmount(ammo);
    }

    private void fireGun(Player shooter, LaserBoxGun weapon) {
        UUID id = shooter.getUniqueId();
        LaserBoxGun reload = playerReloadGun.get(id);
        if (reload != null) {
            if (playerReloadUntil.getOrDefault(id, Integer.MAX_VALUE) <= laserTick)
                completeReload(shooter);
            else return;
        }
        int ammo = playerAmmo.getOrDefault(id, weapon.magazine());
        int now = laserTick * 4;
        if (playerNextShot.getOrDefault(id, 0) > now) return;
        if (ammo <= 0) {
            playerReloadGun.put(id, weapon);
            playerReloadUntil.put(id, laserTick + weapon.reloadTicks());
            shooter.sendActionBar(
                    LegacyText.component(
                            configured(
                                            MessageConfig.LASER_BOX_RELOAD,
                                            "&#fff566%gun% &#bababa• Reloading")
                                    .replace("%gun%", gunName(weapon))
                                    .replace(
                                            "%seconds%",
                                            String.format(
                                                    Locale.ROOT,
                                                    "%.1f",
                                                    weapon.reloadTicks() / 20.0D))));
            return;
        }
        int previous = playerLastShot.getOrDefault(id, Integer.MIN_VALUE);
        int burst = now - previous < 32 ? playerBurst.getOrDefault(id, 0) + 1 : 0;
        playerLastShot.put(id, now);
        playerBurst.put(id, burst);
        playerNextShot.put(id, now + weapon.fireIntervalQuarterTicks());
        playerAmmo.put(id, ammo - 1);
        syncAmmoItem(shooter, weapon);
        if (ammo == 1) {
            playerReloadGun.put(id, weapon);
            playerReloadUntil.put(id, laserTick + weapon.reloadTicks());
        }
        shooter.sendActionBar(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_AMMO,
                                        "&#fff566%gun% &#bababa• &#ff6b26%ammo%&#ededed/%magazine%")
                                .replace("%gun%", gunName(weapon))
                                .replace("%ammo%", Integer.toString(ammo - 1))
                                .replace("%magazine%", Integer.toString(weapon.magazine()))));
        Location eye = shooter.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return;
        Vector direction = eye.getDirection().normalize();
        int pellets = weapon.pellets();
        for (int pellet = 0; pellet < pellets; pellet++) {
            Vector shot = recoil(spread(direction, combatSpread(shooter, weapon)), weapon, burst);
            RayTraceResult block =
                    world.rayTraceBlocks(eye, shot, weapon.range(), FluidCollisionMode.NEVER, true);
            double distance =
                    block == null
                            ? weapon.range()
                            : block.getHitPosition().distance(eye.toVector());
            RayTraceResult hit =
                    world.rayTraceEntities(
                            eye,
                            shot,
                            distance,
                            0.15,
                            entity ->
                                    entity instanceof Player p
                                            && active(p)
                                            && !p.getUniqueId().equals(id));
            if (hit != null) distance = hit.getHitPosition().distance(eye.toVector());
            spawnLaser(world, eye, shot, distance);
            if (hit != null && hit.getHitEntity() instanceof Player victim) {
                LaserBoxGun.HitRegion region = hitRegion(victim, hit.getHitPosition());
                applyLaserHit(shooter, victim, weapon.damage(region, distance), weapon, region);
            }
        }
        world.playSound(
                shooter.getLocation(), gunSound(weapon), gunVolume(weapon), gunPitch(weapon));
    }

    private Sound gunSound(LaserBoxGun gun) {
        return switch (gun) {
            case SMG -> Sound.ENTITY_ARROW_SHOOT;
            case RIFLE -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
            case MACHINE -> Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR;
            case SNIPER -> Sound.ENTITY_GENERIC_EXPLODE;
            case SHOTGUN -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
        };
    }

    private float gunVolume(LaserBoxGun gun) {
        return gun == LaserBoxGun.SNIPER || gun == LaserBoxGun.SHOTGUN ? 1.25F : 1.05F;
    }

    private float gunPitch(LaserBoxGun gun) {
        return switch (gun) {
            case SMG -> 1.65F;
            case RIFLE -> 0.82F;
            case MACHINE -> 0.65F;
            case SNIPER -> 0.5F;
            case SHOTGUN -> 0.42F;
        };
    }

    private double combatSpread(Player player, LaserBoxGun gun) {
        double spread = gun.spreadDegrees(playerBurst.getOrDefault(player.getUniqueId(), 0));
        Vector velocity = player.getVelocity();
        boolean moving =
                velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.0025D;
        if (!player.isOnGround())
            return spread + gun.spreadDegrees() * (gun == LaserBoxGun.SMG ? 2.25D : 4D);
        if (player.isSneaking()) return gun.crouchedSpread(spread, moving);
        double result =
                moving
                        ? spread + gun.spreadDegrees() * (gun == LaserBoxGun.SMG ? 1D : 1.6D)
                        : spread;
        if (gun == LaserBoxGun.SNIPER
                && aimingUntil.getOrDefault(player.getUniqueId(), 0) > laserTick)
            result *= moving ? 0.65D : 0.25D;
        return result;
    }

    private Vector spread(Vector direction, double degrees) {
        if (degrees <= 0) return direction.clone();
        double radians = Math.toRadians(degrees);
        return perturb(
                direction,
                Math.tan(ThreadLocalRandom.current().nextDouble(-radians, radians)),
                Math.tan(ThreadLocalRandom.current().nextDouble(-radians, radians)));
    }

    private Vector recoil(Vector direction, LaserBoxGun gun, int burstShot) {
        return perturb(
                direction,
                Math.tan(Math.toRadians(gun.recoilYawDegrees(burstShot))),
                Math.tan(Math.toRadians(gun.recoilPitchDegrees(burstShot))));
    }

    private Vector perturb(Vector direction, double horizontal, double vertical) {
        Vector forward = direction.clone().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.0001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return forward.add(right.multiply(horizontal)).add(up.multiply(vertical)).normalize();
    }

    private void spawnLaser(World world, Location eye, Vector direction, double distance) {
        Set<Player> viewers = new LinkedHashSet<>(players());
        for (UUID uuid : getSpectatorUniqueIds()) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) viewers.add(spectator);
        }
        List<Player> viewerList = new ArrayList<>(viewers);
        for (double d = 0; d <= distance; d += 1D)
            world.spawnParticle(
                    Particle.DUST,
                    viewerList,
                    null,
                    eye.getX() + direction.getX() * d,
                    eye.getY() + direction.getY() * d,
                    eye.getZ() + direction.getZ() * d,
                    1,
                    0,
                    0,
                    0,
                    0,
                    LASER_PARTICLE,
                    false);
    }

    private LaserBoxGun.HitRegion hitRegion(Player victim, Vector point) {
        double min = victim.getBoundingBox().getMinY();
        double height = Math.max(0.1D, victim.getBoundingBox().getMaxY() - min);
        double relative = (point.getY() - min) / height;
        if (relative >= 0.72D) return LaserBoxGun.HitRegion.HEAD;
        if (relative <= 0.35D) return LaserBoxGun.HitRegion.LEGS;
        return LaserBoxGun.HitRegion.BODY;
    }

    private void applyLaserHit(
            Player attacker,
            Player victim,
            double damage,
            LaserBoxGun weapon,
            LaserBoxGun.HitRegion region) {
        if (getGameStageEnum() != GameStageEnum.PROGRESS
                || !active(attacker)
                || !gamePlayers.contains(victim.getUniqueId())
                || victim.getGameMode() != GameMode.ADVENTURE) return;
        ChampionshipTeam attackerTeam = plugin.getTeamManager().getTeamByPlayer(attacker);
        ChampionshipTeam victimTeam = plugin.getTeamManager().getTeamByPlayer(victim);
        if (attackerTeam == null || victimTeam == null || attackerTeam.equals(victimTeam)) return;
        UUID victimId = victim.getUniqueId();
        if (playerProtectionUntil.getOrDefault(victimId, 0) > laserTick) return;
        double health = playerLaserHealth.getOrDefault(victimId, 100D) - damage;
        if (health > 0) {
            playerLaserHealth.put(victimId, health);
            victim.setLevel((int) Math.ceil(health));
            victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5F, 1.8F);
            return;
        }
        playerLaserHealth.remove(victimId);
        playerRespawnTick.put(victimId, laserTick + 80);
        victim.getInventory().clear();
        victim.setLevel(0);
        victim.setGameMode(GameMode.SPECTATOR);
        ChampionshipTeam killerTeam = attackerTeam;
        if (killerTeam != null)
            LaserBoxKillFeedback.burst(
                    victim,
                    TeamColors.fromHex(killerTeam.getColorCode()),
                    laserItemKey,
                    region == LaserBoxGun.HitRegion.HEAD);
        int kills = playerIndividualKills.getOrDefault(attacker.getUniqueId(), 0) + 1;
        playerIndividualKills.put(attacker.getUniqueId(), kills);
        if (killerTeam != null) addTeamShootCount(killerTeam);
        addPlayerPoints(attacker.getUniqueId(), 4);
        LaserBoxKillFeedback.play(
                attacker,
                kills,
                scheduler,
                plugin,
                () ->
                        getGameStageEnum() == GameStageEnum.PROGRESS
                                && gamePlayers.contains(attacker.getUniqueId()));
        String template =
                region == LaserBoxGun.HitRegion.HEAD
                        ? configured(
                                MessageConfig.LASER_BOX_HEADSHOT,
                                "&#ff6b26%attacker% &#bababaheadshot &#ededed%victim%")
                        : configured(
                                MessageConfig.LASER_BOX_HIT,
                                "&#ff6b26%attacker% &#bababaeliminated &#ededed%victim%");
        sendMessageToAllGamePlayers(
                template.replace("%attacker%", CoreMessages.formatPlayerName(attacker))
                        .replace("%victim%", CoreMessages.formatPlayerName(victim))
                        .replace("%gun%", gunName(weapon)));
    }

    private void completeReload(Player player) {
        UUID id = player.getUniqueId();
        LaserBoxGun gun = playerReloadGun.get(id);
        if (gun == null || playerReloadUntil.getOrDefault(id, Integer.MAX_VALUE) > laserTick)
            return;
        playerReloadGun.remove(id);
        playerReloadUntil.remove(id);
        playerAmmo.put(id, gun.magazine());
        syncAmmoItem(player, gun);
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.9F, 1.05F);
        player.sendActionBar(
                LegacyText.component(
                        configured(
                                        MessageConfig.LASER_BOX_RELOAD_COMPLETE,
                                        "&#a8ff78%gun% reloaded")
                                .replace("%gun%", gunName(gun))));
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
