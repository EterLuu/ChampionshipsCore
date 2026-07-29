package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.game.battlebox.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle Box arena hosting several independent team-vs-team matches in parallel, one per stamped arena copy
 * (see {@link BattleBoxLayout}). It extends {@link BaseSingleTeamArea} (a team-list area): the area owns all
 * participating teams for the round and a {@link BattleBoxMatch} list provides the per-copy geometry, win
 * detection and freezing.
 * The championship's Swiss scheduler feeds one round's pairings in via {@link #tryStartMatches(List)}.
 */
public class BattleBoxArea extends BaseSingleTeamArea {
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private BukkitTask woolCheckerTask;

    private final List<BattleBoxMatch> matches = new ArrayList<>();
    private final Map<UUID, BattleBoxMatch> matchByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BBWeaponKitEnum> playerWeaponKit = new ConcurrentHashMap<>();

    public BattleBoxArea(ChampionshipsCore plugin, BattleBoxConfig battleBoxConfig) {
        super(plugin, GameTypeEnum.BattleBox, new BattleBoxHandler(plugin), battleBoxConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setBattleBoxArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    /**
     * Starts a round of parallel matches from {@code pairs} (each a team-vs-team pairing). Pair {@code i}
     * runs in arena copy {@code i}, so {@code prepare} must have stamped at least {@code pairs.size()} copies.
     */
    public boolean tryStartMatches(@NotNull List<TwoVTwoVector> pairs) {
        if (getGameStageEnum() != GameStageEnum.WAITING)
            return false;
        setGameStageEnum(GameStageEnum.LOADING);

        matches.clear();
        matchByPlayer.clear();
        int copyIndex = 0;
        for (TwoVTwoVector pair : pairs) {
            BattleBoxMatch match = new BattleBoxMatch(copyIndex, pair.getTeamOne(), pair.getTeamTwo(), getGameConfig());
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

    /** The match a player belongs to this round, or {@code null} for non-participants. */
    @Nullable
    public BattleBoxMatch matchOf(@NotNull Player player) {
        return matchByPlayer.get(player.getUniqueId());
    }

    @Override
    public void resetArea() {
        for (BattleBoxMatch match : matches) {
            match.resetWool(Material.WHITE_WOOL);
        }
        cleanDroppedItems();

        matches.clear();
        matchByPlayer.clear();
        playerWeaponKit.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        woolCheckerTask = null;
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
        for (BattleBoxMatch match : matches) {
            match.getRight().teleportAllPlayers(match.getRightPreSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftPreSpawn());
            match.resetCenterWool();
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        announceGamePreparation(MessageConfig.BATTLE_BOX_START_PREPARATION,
                MessageConfig.BATTLE_BOX_START_PREPARATION_TITLE, MessageConfig.BATTLE_BOX_START_PREPARATION_SUBTITLE);

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
        summonPotions();

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        for (BattleBoxMatch match : matches) {
            match.getRight().teleportAllPlayers(match.getRightSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftSpawn());
        }
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        startFinalCountdown(MessageConfig.BATTLE_BOX_GAME_START_SOON_TITLE,
                MessageConfig.BATTLE_BOX_GAME_START_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SUBTITLE,
                this::beginGameProgress);
    }

    private void beginGameProgress() {
        startGameProgressTask = startRemainingTimer(getGameConfig().getTimer(), seconds -> {
            timer = seconds;
            changeLevelForAllGamePlayers(timer);
            updateSpectatorTimerBossBar(MessageConfig.BATTLE_BOX_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
        }, this::endGame);

        woolCheckerTask = scheduler.runTaskTimer(plugin, () -> {
            if (getGameStageEnum() != GameStageEnum.PROGRESS)
                return;

            boolean allFinished = true;
            for (BattleBoxMatch match : matches) {
                if (match.isFinished())
                    continue;
                HashMap<Material, Integer> blockCount = match.countWool();
                int rightWool = blockCount.getOrDefault(match.getRight().getWool().getType(), 0);
                int leftWool = blockCount.getOrDefault(match.getLeft().getWool().getType(), 0);
                if (rightWool == 9 || leftWool == 9) {
                    finishMatch(match);
                } else {
                    allFinished = false;
                }
            }

            if (allFinished && !matches.isEmpty()) {
                changeLevelForAllGamePlayers(0);
                endGame();
                if (woolCheckerTask != null)
                    woolCheckerTask.cancel();
            }
        }, 0, 1L);
    }

    /** Freezes a settled match: its players go spectator at their copy's viewpoint; scoring waits for end. */
    private void finishMatch(BattleBoxMatch match) {
        match.setFinished(true);
        for (ChampionshipTeam team : List.of(match.getRight(), match.getLeft())) {
            for (Player player : team.getOnlinePlayers()) {
                player.setGameMode(GameMode.SPECTATOR);
                if (match.getSpectatorSpawn() != null)
                    player.teleport(match.getSpectatorSpawn());
            }
            team.sendMessageToAll(MessageConfig.BATTLE_BOX_GAME_END);
        }
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (woolCheckerTask != null)
            woolCheckerTask.cancel();

        for (BattleBoxMatch match : matches) {
            calculatePoints(match);
        }
        addPlayerPointsToDatabase();

        announceGameEnd(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(getLobbyLocation());
        resetPlayerHealthFoodEffectLevelInventory();
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new SingleGameEndEvent(this, gameTeams));

        resetGame();
    }

    /** Settles one match: compares its wool floor, awards win/draw points and announces to its two teams. */
    private void calculatePoints(BattleBoxMatch match) {
        HashMap<Material, Integer> blockCount = match.countWool();
        ChampionshipTeam right = match.getRight();
        ChampionshipTeam left = match.getLeft();
        int rightWool = blockCount.getOrDefault(right.getWool().getType(), 0);
        int leftWool = blockCount.getOrDefault(left.getWool().getType(), 0);

        String message;
        if (rightWool > leftWool) {
            for (UUID uuid : right.getMembers()) addPlayerPoints(uuid, 40);
            message = MessageConfig.BATTLE_BOX_WIN.replace("%team%", right.getColoredName());
        } else if (leftWool > rightWool) {
            for (UUID uuid : left.getMembers()) addPlayerPoints(uuid, 40);
            message = MessageConfig.BATTLE_BOX_WIN.replace("%team%", left.getColoredName());
        } else {
            for (UUID uuid : right.getMembers()) addPlayerPoints(uuid, 15);
            for (UUID uuid : left.getMembers()) addPlayerPoints(uuid, 15);
            message = MessageConfig.BATTLE_BOX_DRAW;
        }

        messageMatch(match, message);
        right.sendTitleToAll(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);
        left.sendTitleToAll(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);

        String points = MessageConfig.BATTLE_BOX_SHOW_POINTS
                .replace("%team%", right.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(right)))
                .replace("%rival%", left.getColoredName())
                .replace("%rival_points%", String.valueOf(getTeamPoints(left)));
        messageMatch(match, points);
    }

    private void messageMatch(BattleBoxMatch match, String message) {
        match.getRight().sendMessageToAll(message);
        match.getLeft().sendMessageToAll(message);
        sendMessageToAllSpectators(message);
    }

    @Override
    public void addPlayerPointsToDatabase() {
        for (Map.Entry<UUID, Double> entry : playerPoints.entrySet()) {
            if (entry.getValue() == 0)
                continue;
            BattleBoxMatch match = matchByPlayer.get(entry.getKey());
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(entry.getKey());
            ChampionshipTeam rival = match == null ? null : match.rivalOf(team);
            plugin.getRankManager().addPlayerPoints(entry.getKey(), rival, gameTypeEnum, gameConfig.getAreaName(), entry.getValue());
        }
        plugin.getRankManager().refreshAfterPendingPointWrites();
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player))
            return;

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            Player killer = player.getKiller();
            if (killer != null) {
                ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam killerTeam = plugin.getTeamManager().getTeamByPlayer(killer);
                if (playerTeam != null && killerTeam != null) {
                    addPlayerPoints(killer.getUniqueId(), 15);
                    event.deathMessage(null);
                    killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);
                    sendMessageToAllGamePlayers(MessageConfig.BATTLE_BOX_KILL_PLAYER
                            .replace("%player%", playerTeam.getColoredColor() + player.getName())
                            .replace("%killer%", killerTeam.getColoredColor() + killer.getName()));
                }
            }
        }

        BattleBoxMatch match = matchOf(player);
        Location respawn = match != null && match.getSpectatorSpawn() != null
                ? match.getSpectatorSpawn() : getSpectatorSpawnLocation();
        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(respawn);
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player) || getGameStageEnum() != GameStageEnum.PROGRESS)
            return;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team != null) {
            sendMessageToAllGamePlayers(MessageConfig.BATTLE_BOX_PLAYER_LEAVE
                    .replace("%player%", team.getColoredColor() + player.getName()));
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

        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        // During the rule-introduction phase everyone roams from the introduction spawn point.
        Location introductionSpawnPoint = getGameConfig().getIntroductionSpawnPoint();
        if (isIntroductionPhase() && introductionSpawnPoint != null) {
            player.teleport(introductionSpawnPoint);
            return;
        }
        BattleBoxMatch match = matchOf(player);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (match != null && team != null) {
            Location target = team.equals(match.getRight()) ? match.getRightPreSpawn()
                    : team.equals(match.getLeft()) ? match.getLeftPreSpawn() : null;
            if (target != null) {
                player.teleport(target);
                scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
                return;
            }
        }
        player.teleport(getSpectatorSpawnLocation());
        scheduler.runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    private void giveItemToAllGamePlayers() {
        for (UUID uuid : gamePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                setWeaponKit(player);
        }
    }

    public boolean setPlayerWeaponKit(@NotNull Player player, @NotNull BBWeaponKitEnum type) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null)
            return false;
        for (UUID uuid : championshipTeam.getMembers()) {
            if (playerWeaponKit.get(uuid) == type) {
                return uuid.equals(player.getUniqueId());
            }
        }
        playerWeaponKit.put(player.getUniqueId(), type);
        return true;
    }

    public BBWeaponKitEnum getPlayerCurrentWeaponKit(@NotNull Player player) {
        return playerWeaponKit.get(player.getUniqueId());
    }

    public BBWeaponKitEnum getPlayerWeaponKit(@NotNull Player player) {
        BBWeaponKitEnum bbWeaponKitEnum = playerWeaponKit.get(player.getUniqueId());
        if (bbWeaponKitEnum != null)
            return bbWeaponKitEnum;

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        List<BBWeaponKitEnum> kits = new ArrayList<>(List.of(BBWeaponKitEnum.values()));
        if (championshipTeam != null) {
            for (UUID uuid : championshipTeam.getMembers()) {
                BBWeaponKitEnum selected = playerWeaponKit.get(uuid);
                if (selected != null)
                    kits.remove(selected);
            }
            BBWeaponKitEnum selected = kits.iterator().next();
            if (selected != null) {
                playerWeaponKit.put(player.getUniqueId(), selected);
                return selected;
            }
            return BBWeaponKitEnum.getRandomEnum();
        }
        return null;
    }

    public void setWeaponKit(@NotNull Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null)
            return;

        player.getInventory().clear();
        PlayerInventory inventory = player.getInventory();
        ItemStack arrows = new ItemStack(Material.ARROW);
        arrows.setAmount(8);
        inventory.addItem(new ItemStack(Material.STONE_SWORD));
        inventory.addItem(new ItemStack(Material.BOW));
        inventory.addItem(arrows);

        BBWeaponKitEnum type = getPlayerWeaponKit(player);
        if (type == BBWeaponKitEnum.ARMOR) {
            inventory.addItem(new ItemStack(Material.GOLDEN_LEGGINGS));
        }
        if (type == BBWeaponKitEnum.SPEED) {
            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1), true);
                potion.setItemMeta(potionMeta);
            }
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.HEAL) {
            ItemStack potion = new ItemStack(Material.SPLASH_POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 600, 2), true);
                potion.setItemMeta(potionMeta);
            }
            potion.setAmount(2);
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.PULL) {
            ItemStack moreArrows = new ItemStack(Material.ARROW);
            moreArrows.setAmount(8);
            inventory.addItem(moreArrows);
        }

        inventory.addItem(new ItemStack(Material.SHEARS));
        inventory.addItem(championshipTeam.getWool());
        inventory.setBoots(championshipTeam.getBoots());
        inventory.setHelmet(championshipTeam.getHelmet());
    }

    private void summonPotions() {
        for (BattleBoxMatch match : matches) {
            for (Location location : match.getPotionLocations()) {
                World world = location.getWorld();
                if (world == null)
                    continue;
                ItemStack item = new ItemStack(Material.SPLASH_POTION);
                PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
                if (potionMeta != null) {
                    potionMeta.setBasePotionType(PotionType.STRONG_HARMING);
                    item.setItemMeta(potionMeta);
                    Item dropped = world.dropItem(location, item);
                    dropped.setGlowing(true);
                }
            }
        }
    }

    @Override
    public void cleanDroppedItems() {
        for (BattleBoxMatch match : matches) {
            World world = match.getSpectatorSpawn() != null ? match.getSpectatorSpawn().getWorld() : null;
            if (world == null)
                continue;
            world.getNearbyEntities(match.getAreaBox()).forEach(entity -> {
                if (entity instanceof Item)
                    entity.remove();
            });
        }
    }

    @Override
    public boolean notInArea(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(getWorldName()))
            return true;
        Vector point = location.toVector();
        for (BattleBoxMatch match : matches) {
            if (match.isInArea(point))
                return false;
        }
        return true;
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public BattleBoxConfig getGameConfig() {
        return (BattleBoxConfig) gameConfig;
    }

    @Override
    public BattleBoxHandler getGameHandler() {
        return (BattleBoxHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return "battlebox";
    }
}
