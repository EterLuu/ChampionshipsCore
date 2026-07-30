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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Item;
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
import org.jetbrains.annotations.Nullable;

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
                || geometry.getRightPreSpawn() == null || geometry.getLeftPreSpawn() == null
                || geometry.getSpectatorSpawn() == null
                || geometry.getLeftZone().getChaserSpawn() == null
                || geometry.getRightZone().getChaserSpawn() == null
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
                ParkourTagLayout.GRID, getGameConfig().getCopyCount()).geometry(index);
    }

    @Nullable
    public ParkourTagMatch matchOf(@NotNull Player player) {
        return match != null && match.contains(player) ? match : null;
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
        cleanDroppedItems();
        match = null;
        windChargeUsedTeams.clear();
        startGamePreparationTask = null;
        startGameProgressTask = null;
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
            match.getRight().teleportAllPlayers(match.getRightPreSpawn());
            match.getLeft().teleportAllPlayers(match.getLeftPreSpawn());
        }
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        createBossBar("chaser", MessageConfig.PARKOUR_TAG_BOSS_BAR_CHASER, BarColor.RED, BarStyle.SOLID);
        createBossBar("escaper", MessageConfig.PARKOUR_TAG_BOSS_BAR_ESCAPER, BarColor.WHITE, BarStyle.SOLID);

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
            changeLevelForAllGamePlayers(timer);
            updateSpectatorTimerBossBar(MessageConfig.PARKOUR_TAG_ACTION_BAR_COUNT_DOWN
                    .replace("%time%", String.valueOf(timer)), timer, getGameConfig().getTimer());
        }, () -> {
            if (match != null) updateAndAnnounce(match);
            endGame();
        });
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
                    .replace("%player%", Utils.formatPlayerName(match.getRightAreaChaser()))
                    .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(match.getRightAreaChaser()) - 1)));
        }
        if (match.getLeftAreaChaser() == null) {
            match.setLeftAreaChaser(manager.getTeamChaser(match.getLeft()));
            Player player = Bukkit.getPlayer(match.getLeftAreaChaser());
            if (player != null) addBossBarPlayer("chaser", player);
            match.getLeft().sendMessageToAll(message
                    .replace("%player%", Utils.formatPlayerName(match.getLeftAreaChaser()))
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
            addBossBarPlayer("escaper", escapee);
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
        return ParkourTagLayout.GRID.transform(copyIndex).apply(configured);
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        if (match != null) calculatePoints(match);
        addPlayerPointsToDatabase();

        cleanInventoryForAllGamePlayers();

        announceGameEnd(MessageConfig.PARKOUR_TAG_GAME_END_TITLE, MessageConfig.PARKOUR_TAG_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(getLobbyLocation());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        resetPlayerHealthFoodEffectLevelInventory();

        ChampionshipTeam right = getRightChampionshipTeam();
        ChampionshipTeam left = getLeftChampionshipTeam();
        if (right != null && left != null) {
            Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(right, left, this));
        }

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

        int glowTicks = getGameConfig().getTimer() * 20 + 100;
        for (Player player : match.getRightAreaEscapees()) {
            player.getInventory().setItem(0, enderEye.clone());
            player.getInventory().setItem(1, windCharge.clone());
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
        }
        for (Player player : match.getLeftAreaEscapees()) {
            player.getInventory().setItem(0, enderEye.clone());
            player.getInventory().setItem(1, windCharge.clone());
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
        }
    }

    // ── handler-facing logic (routed to the player's match) ──────────────────────────────────────

    /** Prep-time chaser pick from a sign click; validates rotation and records it on the player's match. */
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
                .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - manager.getChaserTimes(uuid)));
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
        if (chaser != null) chaser.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
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
        if (notAreaPlayer(player) || getGameStageEnum() != GameStageEnum.PROGRESS)
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

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPreSpawnLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS && isChaser(player)) {
            scheduler.runTask(plugin, () -> player.setGameMode(GameMode.ADVENTURE));
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
        ParkourTagMatch match = matchOf(player);
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
        return getGameConfig().resolveWorldName();
    }
}
