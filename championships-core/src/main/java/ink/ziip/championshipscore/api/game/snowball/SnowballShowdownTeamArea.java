package ink.ziip.championshipscore.api.game.snowball;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SnowballShowdownTeamArea extends BaseMultiTeamGameInstance {
    private final List<List<Location>> areaLocations = new ArrayList<>();
    private final Map<UUID, List<Location>> playerRespawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerSpawnLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerRespawnTime = new ConcurrentHashMap<>();
    private final Map<List<Location>, Iterator<Location>> locationIterators = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Integer> teamShootTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerIndividualKills = new ConcurrentHashMap<>();
    private List<String> teamRank = new ArrayList<>();
    @Getter
    private int timer;
    private BukkitTask startGameProgressTask;

    public SnowballShowdownTeamArea(ChampionshipsCore plugin, SnowballShowdownConfig snowballShowdownConfig) {
        super(plugin, GameTypeEnum.SnowballShowdown, new SnowballShowdownHandler(plugin), snowballShowdownConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setSnowballShowdownTeamArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);

        ConfigurationSection configurationSection = getGameConfig().getPlayerSpawnPoints();
        for (String areaName : configurationSection.getKeys(false)) {
            List<Location> locations = new ArrayList<>();
            for (String stringLocation : configurationSection.getStringList(areaName)) {
                locations.add(Utils.getLocation(stringLocation));
            }

            areaLocations.add(locations);
        }
    }

    @Override
    public void resetArea() {
        cleanDroppedItems();

        playerRespawnLocations.clear();
        playerSpawnLocation.clear();
        playerRespawnTime.clear();
        teamShootTimes.clear();
        playerIndividualKills.clear();
        teamRank.clear();
        locationIterators.clear();

        startGameProgressTask = null;

        World world = getSpectatorSpawnLocation().getWorld();
        Vector pos1 = getGameConfig().getAreaPos1();
        Vector pos2 = getGameConfig().getAreaPos2();
        BoundingBox boundingBox = new BoundingBox(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
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

        // Rule-introduction phase (if configured): gather players at the introduction spawn point and
        // broadcast the rule sections in chat over 45s, then run the normal preparation below.
        startGameIntroduction(this::startFormalPreparation);
    }

    /** Normal preparation: spawn assignment + countdown, runs after the rule-introduction phase. */
    private void startFormalPreparation() {

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        for (List<Location> locations : areaLocations) {
            Collections.shuffle(locations, ThreadLocalRandom.current());
            locationIterators.put(locations, locations.iterator());
        }

        for (ChampionshipTeam championshipTeam : gameTeams) {
            Collections.shuffle(areaLocations);

            Iterator<List<Location>> locationI = areaLocations.iterator();

            for (UUID uuid : championshipTeam.getMembers()) {
                if (!locationI.hasNext())
                    locationI = areaLocations.iterator();

                playerRespawnLocations.put(uuid, locationI.next());
            }
        }

        for (List<Location> locations : areaLocations) {
            Iterator<Location> locationIterator = locations.iterator();

            for (Map.Entry<UUID, List<Location>> playerLocationList : playerRespawnLocations.entrySet()) {
                if (locations.equals(playerLocationList.getValue())) {
                    if (!locationIterator.hasNext())
                        locationIterator = locations.iterator();

                    playerSpawnLocation.put(playerLocationList.getKey(), locationIterator.next());
                }
            }
        }

        teleportPlayersToSpawnLocation();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.SNOWBALL_START_PREPARATION,
                MessageConfig.SNOWBALL_START_PREPARATION_TITLE, MessageConfig.SNOWBALL_START_PREPARATION_SUBTITLE);

        startGameProgress();
    }

    public void startGameProgress() {
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayersAndTeleport();

        startFinalCountdown(MessageConfig.SNOWBALL_GAME_START_SOON_TITLE,
                MessageConfig.SNOWBALL_GAME_START_TITLE, MessageConfig.SNOWBALL_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        for (UUID uuid : gamePlayers) {
            playerRespawnTime.put(uuid, System.currentTimeMillis());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
        }

        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            updateGameTimerBossBar(MessageConfig.SNOWBALL_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
            calculateCurrentRank();
        }, this::endGame);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        try {
            List<Location> locations = areaLocations.get(ThreadLocalRandom.current().nextInt(areaLocations.size()));
            return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        } catch (Exception ignored) {
            return gameConfig.getSpectatorSpawnPoint();
        }
    }

    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        if (isSettlementAllowed()) calculatePoints();

        announceGameEnd(MessageConfig.SNOWBALL_GAME_END_TITLE, MessageConfig.SNOWBALL_GAME_END_SUBTITLE);

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
                if (additionalPoints <= 10)
                    additionalPoints = 10;
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

            Player killer = player.getKiller();
            if (killer != null) {
                ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam killerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(killer);
                if (playerChampionshipTeam != null && killerChampionshipTeam != null) {
                    event.deathMessage(null);

                    killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

                    addShoot(killer, player);
                }
            } else {
                event.deathMessage(null);
                ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (playerChampionshipTeam != null) {
                    String message = MessageConfig.SNOWBALL_PLAYER_DEATH
                            .replace("%player%", Utils.formatPlayerName(player));

                    sendMessageToAllGamePlayers(message);
                }
            }
        }

        scheduler.runTask(plugin, () -> {
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
            String message = MessageConfig.SNOWBALL_PLAYER_LEAVE
                    .replace("%player%", Utils.formatPlayerName(player));

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
            teleportPlayerToSpawnLocation(player);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.COUNTDOWN || getGameStageEnum() == GameStageEnum.PROGRESS) {
            respawnPlayer(player);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        player.teleport(CCConfig.LOBBY_LOCATION);
        player.setGameMode(GameMode.ADVENTURE);
    }

    public void addShoot(Player assailant, Player player) {
        ChampionshipTeam assailantChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(assailant);
        ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        respawnPlayer(player);

        if (assailantChampionshipTeam == null || playerChampionshipTeam == null)
            return;

        if (assailantChampionshipTeam.equals(playerChampionshipTeam))
            return;

        assailant.playSound(assailant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);
        player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

        addTeamShootCount(assailantChampionshipTeam);
        addPlayerPoints(assailant.getUniqueId(), 4);
        addPlayerIndividualKills(assailant);
        String message = MessageConfig.SNOWBALL_KILL_PLAYER
                .replace("%player%", Utils.formatPlayerName(player))
                .replace("%killer%", Utils.formatPlayerName(assailant));
        sendMessageToAllGamePlayers(message);

        ItemStack snowball = new ItemStack(Material.SNOWBALL);
        snowball.setAmount(6);
        assailant.getInventory().addItem(snowball.clone());
    }

    private void addPlayerIndividualKills(Player player) {
        playerIndividualKills.put(player.getUniqueId(), playerIndividualKills.getOrDefault(player.getUniqueId(), 0) + 1);
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
        ranked.sort(Comparator.comparingInt(this::getTeamScore).reversed()
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

    public boolean canBeDamaged(Player player) {
        Long time = playerRespawnTime.get(player.getUniqueId());
        if (time == null)
            return false;

        return (System.currentTimeMillis() - time) > 3000;
    }

    public void respawnPlayer(Player player) {
        playerRespawnTime.put(player.getUniqueId(), System.currentTimeMillis());
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

        scheduler.runTaskLater(plugin, () -> {
            for (UUID uuid : gamePlayers) {
                Player gamePlayer = Bukkit.getPlayer(uuid);
                if (gamePlayer != null) {
                    ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                    if (championshipTeam != null) {
                        plugin.getGlowingEntities().unsetGlowing(gamePlayer, player);
                    }
                }
            }
        }, 60L);
    }

    public synchronized void teleportPlayerToSpawnLocation(Player player) {
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
                player.teleport(locations.get(ThreadLocalRandom.current().nextInt(locations.size())));
            }
        } else {
            List<Location> randomLocations = areaLocations.get(ThreadLocalRandom.current().nextInt(areaLocations.size()));
            player.teleport(randomLocations.get(ThreadLocalRandom.current().nextInt(randomLocations.size())));
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
        ItemStack snowball = new ItemStack(Material.SNOWBALL);
        snowball.setAmount(64);

        ItemStack sword = new ItemStack(Material.IRON_SWORD);

        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();

        playerInventory.addItem(snowball);
        playerInventory.addItem(sword);

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            playerInventory.setHelmet(championshipTeam.getHelmet());
            playerInventory.setChestplate(championshipTeam.getChestPlate());
            playerInventory.setLeggings(championshipTeam.getLeggings());
            playerInventory.setBoots(championshipTeam.getBoots());
        }

        PotionEffect jumpPotionEffect = new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 0);
        PotionEffect speedPotionEffect = new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0);
        player.addPotionEffect(jumpPotionEffect);
        player.addPotionEffect(speedPotionEffect);
    }

    @Override
    public SnowballShowdownConfig getGameConfig() {
        return (SnowballShowdownConfig) gameConfig;
    }

    @Override
    public SnowballShowdownHandler getGameHandler() {
        return (SnowballShowdownHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return gameConfig.getConfiguredWorld();
    }
}
