package ink.ziip.championshipscore.api.game.hotycodydusky;

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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HotyCodyDuskyTeamArea extends BaseMultiTeamGameInstance {
    @Getter
    private final List<UUID> deathPlayer = new ArrayList<>();
    private final Map<UUID, Long> playerDeadTimes = new HashMap<>();
    private final Map<UUID, Long> playerCodyChangeTimes = new HashMap<>();
    private final Map<ChampionshipTeam, Integer> teamDeathPlayers = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    @Getter
    private UUID codyHolder;

    public HotyCodyDuskyTeamArea(ChampionshipsCore plugin, HotyCodyDuskyConfig hotyCodyDuskyConfig) {
        super(plugin, GameTypeEnum.HotyCodyDusky, new HotyCodyDuskyHandler(plugin), hotyCodyDuskyConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());

        getGameHandler().setHotyCodyDuskyArea(this);
        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        calculatePoints();

        setGameStageEnum(GameStageEnum.END);

        announceGameEnd(MessageConfig.HOTY_CODY_DUSKY_GAME_END_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_GAME_END_SUBTITLE);

        teleportAllPlayers(CCConfig.LOBBY_LOCATION);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));
        resetGame();
    }

    protected void calculatePoints() {
        List<Map.Entry<UUID, Long>> sortedEntries = new ArrayList<>(playerDeadTimes.entrySet());
        Long time = System.currentTimeMillis();
        List<UUID> alivePlayers = new ArrayList<>(gamePlayers);
        alivePlayers.removeAll(deathPlayer);
        for (UUID uuid : alivePlayers) {
            playerDeadTimes.put(uuid, time);
        }
        sortedEntries.sort((entry1, entry2) -> Long.compare(entry2.getValue(), entry1.getValue()));
        int rank = 0;
        long curTime = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : sortedEntries) {
            if (entry.getValue() < curTime) {
                rank++;
            }

            if (rank == 1) {
                addPlayerPoints(entry.getKey(), 25);
            } else if (rank == 2) {
                addPlayerPoints(entry.getKey(), 20);
            } else if (rank == 3) {
                addPlayerPoints(entry.getKey(), 15);
                break;
            }
        }

        sendMessageToAllGamePlayers(getTeamPointsRank());
        addPlayerPointsToDatabase();
    }

    @Override
    public void resetArea() {
        deathPlayer.clear();
        teamDeathPlayers.clear();
        codyHolder = null;

        startGamePreparationTask = null;
        startGameProgressTask = null;
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
        return gameConfig.getAreaName();
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

        teleportAllPlayers(getGameConfig().getPlayerSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        createBossBar("chaser", MessageConfig.HOTY_CODY_DUSKY_BOSS_BAR_CHASER, BarColor.RED, BarStyle.SOLID);
        createBossBar("escaper", MessageConfig.HOTY_CODY_DUSKY_BOSS_BAR_ESCAPER, BarColor.WHITE, BarStyle.SOLID);

        announceGamePreparation(MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION,
                MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_START_PREPARATION_SUBTITLE);

        timer = 10;
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
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                deathPlayer.add(uuid);
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null) {
                    addTeamDeathPlayer(championshipTeam);
                    logGame(Level.INFO, "玩家", "玩家=" + playerManager.getPlayerName(uuid) + " uuid=" + uuid
                            + " 状态=离线，计入淘汰");
                }
            } else {
                addBossBarPlayer("escaper", player);
                player.getInventory().setBoots(player.getInventory().getBoots());
            }
        }

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        startFinalCountdown(MessageConfig.HOTY_CODY_DUSKY_GAME_START_SOON_TITLE,
                MessageConfig.HOTY_CODY_DUSKY_GAME_START_TITLE, MessageConfig.HOTY_CODY_DUSKY_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        timer = getGameConfig().getTimer();
        selectCodyHolder(true);
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            changeLevelForAllGamePlayers(timer);
            updateSpectatorTimerBossBar(MessageConfig.HOTY_CODY_DUSKY_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer))
                    .replace("%holder%", codyHolder == null ? "待定" : Utils.formatPlayerName(codyHolder)),
                    timer, getGameConfig().getTimer());

            int elapsed = getGameConfig().getTimer() - timer;
            if (timer > 0 && elapsed > 0 && elapsed % 3 == 0 && codyHolder != null) {
                Player player = Bukkit.getPlayer(codyHolder);
                if (player != null) {
                    double health = player.getHealth() - 2;
                    if (health < 0)
                        health = 0;
                    player.setHealth(health);
                    player.playSound(player, Sound.ENTITY_PLAYER_HURT, 1, 1);
                }
            }

        }, this::endGame);
    }

    public int getSurvivedPlayerNums() {
        return gamePlayers.size() - deathPlayer.size();
    }

    public int getSurvivedTeamNums() {
        int i = 0;
        for (ChampionshipTeam championshipTeam : teamDeathPlayers.keySet()) {
            if (teamDeathPlayers.get(championshipTeam) == championshipTeam.getMembers().size())
                i++;
        }
        return gameTeams.size() - i;
    }

    protected boolean changeCodyHolder(int type, UUID holder) {
        synchronized (this) {
            if (type == 1) {
                codyHolder = null;
                selectCodyHolder(false);
            }
            if (type == 2) {
                return changeCodyHolder(holder, false);
            }

            return false;
        }
    }

    private void selectCodyHolder(boolean first) {
        if (gameStageEnum != GameStageEnum.PROGRESS) {
            return;
        }

        if (gamePlayers.isEmpty()) {
            return;
        }

        List<UUID> alivePlayers = new ArrayList<>(gamePlayers);
        alivePlayers.removeAll(deathPlayer);

        if (alivePlayers.isEmpty()) {
            codyHolder = null;
            return;
        }

        UUID holder = alivePlayers.get(new Random().nextInt(alivePlayers.size()));
        while (!changeCodyHolder(holder, first)) {
            holder = alivePlayers.get(new Random().nextInt(alivePlayers.size()));
        }
        addPlayerPoints(holder, 10);
    }

    private boolean changeCodyHolder(UUID to, boolean first) {
        long lastChangeTime = playerCodyChangeTimes.getOrDefault(to, 0L);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChangeTime < 1500) {
            return false;
        }
        if (codyHolder != null) {
            if (codyHolder.equals(to)) {
                return false;
            }
            playerCodyChangeTimes.put(codyHolder, System.currentTimeMillis());
        }
        if (codyHolder == null)
            sendActionBarToAllGamePlayers(MessageConfig.HOTY_CODY_DUSKY_PLAYER_RECEIVED_CODY
                    .replace("%player%", Utils.formatPlayerName(to)));
        else
            sendActionBarToAllGamePlayers(MessageConfig.HOTY_CODY_DUSKY_GIVE_CODY_TO_PLAYER
                    .replace("%to%", Utils.formatPlayerName(to))
                    .replace("%from%", Utils.formatPlayerName(codyHolder)));
        setCodyPlayer(to, first);
        return true;
    }

    private void setCodyPlayer(UUID uuid, boolean first) {
        if (codyHolder != null) {
            Player codyHolderPlayer = Bukkit.getPlayer(codyHolder);
            if (codyHolderPlayer != null) {
                codyHolderPlayer.getInventory().clear();
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                if (championshipTeam != null)
                    codyHolderPlayer.getInventory().setBoots(championshipTeam.getBoots());
                codyHolderPlayer.playSound(codyHolderPlayer, Sound.ENTITY_ENDER_PEARL_THROW, 1, 0);
                for (PotionEffect potionEffect : codyHolderPlayer.getActivePotionEffects())
                    codyHolderPlayer.removePotionEffect(potionEffect.getType());

                removeBossBarPlayer("chaser", codyHolderPlayer);
                addBossBarPlayer("escaper", codyHolderPlayer);
            }
        }
        codyHolder = uuid;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            ItemStack cody = new ItemStack(Material.COD);
            PlayerInventory inventory = player.getInventory();
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
            if (championshipTeam != null)
                inventory.setBoots(championshipTeam.getBoots());
            inventory.setLeggings(cody.clone());
            inventory.setChestplate(cody.clone());
            inventory.setHelmet(cody.clone());
            inventory.setItemInMainHand(cody.clone());
            inventory.setItemInOffHand(cody.clone());

            PotionEffect potionEffectBlindness = new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false);
            PotionEffect potionEffectGlowing = new PotionEffect(PotionEffectType.GLOWING, getTimer() * 20, 0, false, false);
            PotionEffect potionEffectSpeed = new PotionEffect(PotionEffectType.SPEED, getTimer() * 20, 0, false, false);
            PotionEffect potionEffectHaste = new PotionEffect(PotionEffectType.HASTE, getTimer() * 20, 0, false, false);
            if (!first)
                player.addPotionEffect(potionEffectBlindness);
            player.addPotionEffect(potionEffectGlowing);
            player.addPotionEffect(potionEffectSpeed);
            player.addPotionEffect(potionEffectHaste);
            player.playSound(player, Sound.ENTITY_ENDERMAN_HURT, 1, 1);
            removeBossBarPlayer("escaper", player);
            addBossBarPlayer("chaser", player);
        }

    }

    private void addDeathPlayer(Player player) {
        removeBossBarPlayer("chaser", player);
        removeBossBarPlayer("escaper", player);

        UUID uuid = player.getUniqueId();
        addDeathPlayer(uuid);
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
        if (championshipTeam != null) {
            addTeamDeathPlayer(championshipTeam);
        }
    }

    private void addDeathPlayer(UUID uuid) {
        if (deathPlayer.contains(uuid))
            return;

        deathPlayer.add(uuid);
        playerDeadTimes.put(uuid, System.currentTimeMillis());

        if (uuid.equals(codyHolder)) {
            changeCodyHolder(1, null);
        }
        addPointsToAllSurvivePlayers();
    }

    private void addPointsToAllSurvivePlayers() {
        for (UUID uuid : gamePlayers) {
            if (!deathPlayer.contains(uuid)) {
                addPlayerPoints(uuid, 15);
            }
        }
    }

    private void addTeamDeathPlayer(ChampionshipTeam championshipTeam) {
        teamDeathPlayers.put(championshipTeam, teamDeathPlayers.getOrDefault(championshipTeam, 0) + 1);
        Integer deathPlayer = teamDeathPlayers.get(championshipTeam);
        logGame(Level.INFO, "淘汰", "队伍=" + championshipTeam.getName() + " 已淘汰人数=" + deathPlayer);
        if (deathPlayer != null) {
            if (deathPlayer == championshipTeam.getMembers().size()) {
                sendMessageToAllGamePlayers(MessageConfig.HOTY_CODY_DUSKY_WHOLE_TEAM_WAS_KILLED.replace("%team%", championshipTeam.getColoredName()));
                addPointsToAllSurvivePlayers();
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getSpectatorSpawnLocation());
            });
            player.teleport(getPreparationTeleportLocation(getGameConfig().getPlayerSpawnPoint()));
            return;
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        addDeathPlayer(player);

        String message = MessageConfig.HOTY_CODY_DUSKY_PLAYER_DEATH;

        message = message.replace("%player%", Utils.formatPlayerName(player));
        sendMessageToAllGamePlayers(message);
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

        if (deathPlayer.contains(player.getUniqueId()))
            return;

        sendMessageToAllGamePlayers(MessageConfig.HOTY_CODY_DUSKY_PLAYER_LEAVE
                .replace("%player%", Utils.formatPlayerName(player)));
        addDeathPlayer(player);
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            player.teleport(getPreparationTeleportLocation(getGameConfig().getPlayerSpawnPoint()));
            return;
        }

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }
}
