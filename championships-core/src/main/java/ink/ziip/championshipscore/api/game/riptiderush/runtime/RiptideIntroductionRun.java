package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;
import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideDifficulty;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorInventory;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorPlatform;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeEntities;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideRaftBlocks;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** No-score guided lesson, reusing real raft movement, fall, color and dodge implementations. */
final class RiptideIntroductionRun implements AutoCloseable {
    private final ChampionshipsCore plugin;
    private final RiptideRushArea area;
    private final RiptideIntroductionArena arena;
    private final RiptideCourseGeometry geometry;
    private final Set<UUID> shields = new HashSet<>();
    private final Map<UUID, Integer> connections = new HashMap<>();
    private final Map<UUID, Integer> rescueUntil = new HashMap<>();
    private final Map<UUID, RiptideFallCheck> falls = new HashMap<>();
    private final Map<UUID, RiptideColorFloorInventory> floorInventories = new HashMap<>();
    private final Map<UUID, Integer> shieldNoticeUntil = new HashMap<>();
    private int speedShieldNoticeUntil;
    private final RiptideDeparture departure = new RiptideDeparture();
    private RiptideIntroductionLesson.Stage stage;
    private BukkitTask task;
    private int tick;
    private int step;
    private boolean closed;
    private RiptideColorFloorRun colors;
    private RiptideColorFloorPlatform platform;
    private TextDisplay targetDisplay;
    private RiptideDodgeRun dodge;
    private RiptideDodgeEntities mobs;

    RiptideIntroductionRun(
            ChampionshipsCore plugin,
            RiptideRushArea area,
            RiptideIntroductionArena arena,
            RiptideCourseGeometry geometry) {
        this.plugin = plugin;
        this.area = area;
        this.arena = arena;
        this.geometry = geometry;
        shields.addAll(area.getParticipantUniqueIds());
    }

    void start() {
        task =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!current()) {
                                        close();
                                        return;
                                    }
                                    try {
                                        advance();
                                    } catch (RuntimeException error) {
                                        plugin.getLogger()
                                                .log(
                                                        Level.SEVERE,
                                                        MessageConfig.codeText(
                                                                "riptide-introduction-arena.run-failed"),
                                                        error);
                                        area.abortAndReset();
                                    }
                                },
                                1L,
                                1L);
    }

    private boolean current() {
        return !closed
                && plugin.isLoaded()
                && arena.ownedBy(area)
                && area.isIntroductionPhase()
                && area.getGameStageEnum() == GameStageEnum.PREPARATION;
    }

    private boolean admitted(Player player) {
        return current()
                && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && area.isGameplayParticipant(player)
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == area;
    }

    private List<Player> participants() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : area.getParticipantUniqueIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !admitted(player)) {
                connections.remove(uuid);
                falls.remove(uuid);
                continue;
            }
            if (!Integer.valueOf(player.getEntityId()).equals(connections.get(uuid))) {
                connections.put(uuid, player.getEntityId());
                player.setGameMode(GameMode.ADVENTURE);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.setFireTicks(0);
                player.setFoodLevel(20);
                player.getInventory().clear();
                for (var effect : player.getActivePotionEffects())
                    player.removePotionEffect(effect.getType());
                area.prepareIntroductionParticipant(player);
                teleport(player, respawn());
            }
            players.add(player);
        }
        return players;
    }

    private void advance() {
        int nextStep = RiptideIntroductionLesson.stepAt(tick);
        if (nextStep != step) {
            RiptideRaftBlocks.move(geometry, step, nextStep, Material.AIR);
            step = nextStep;
        }
        var nextStage = RiptideIntroductionLesson.stageAt(tick);
        if (nextStage != stage) {
            stopColors();
            stopDodge();
            stage = nextStage;
            area.sendMessageToAllGamePlayers(text(stage.name().toLowerCase(java.util.Locale.ROOT)));
            if (stage == RiptideIntroductionLesson.Stage.COLOR) startColors();
            if (stage == RiptideIntroductionLesson.Stage.DODGE) {
                dodge =
                        new RiptideDodgeRun(
                                "ZOMBIE",
                                20261006L,
                                geometry.raftWidth(),
                                geometry.raftLength(),
                                0);
                mobs = new RiptideDodgeEntities(geometry, step);
                area.sendTitleToAllGamePlayers(RiptidePresentation.dodgeTitle(dodge), "");
            }
            if (stage == RiptideIntroductionLesson.Stage.SPEED) {
                shields.clear();
                speedShieldNoticeUntil = tick + 40;
                area.sendTitleToAllGamePlayers(
                        MessageConfig.RIPTIDE_RUSH_SPEED_BOOST_TITLE,
                        MessageConfig.RIPTIDE_RUSH_SPEED_BOOST_SUBTITLE);
                area.sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK);
                area.playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1.4F);
            }
        }
        var players = participants();
        if (stage == RiptideIntroductionLesson.Stage.SPEED && speedShieldNoticeUntil == tick + 40)
            for (Player player : players)
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
        for (Player player : players) {
            UUID uuid = player.getUniqueId();
            Location feet = player.getLocation();
            if (falls.computeIfAbsent(uuid, unused -> new RiptideFallCheck())
                    .sample(
                            geometry,
                            feet,
                            step,
                            .65,
                            6,
                            player.isInWater(),
                            RiptideFallCheck.deckPresent(geometry, feet, step),
                            tick)) rescue(player, "fall");
        }
        tickColors(players);
        tickDodge(players);
        refreshPresentation(players);
        if (departure.active()) departure.tick();
        tick++;
    }

    private void startColors() {
        colors =
                new RiptideColorFloorRun(
                        geometry.raftWidth(),
                        geometry.raftLength(),
                        RiptideDifficulty.floorRoundTicks(
                                geometry.totalSteps(), geometry.totalSteps()),
                        new Random(20261006L),
                        RiptideColorFloorRun.Theme.TERRACOTTA);
        platform = new RiptideColorFloorPlatform(geometry, step);
        platform.paint(colors.floor());
        targetDisplay =
                geometry.centerAt(step)
                        .getWorld()
                        .spawn(
                                geometry.centerAt(step).add(0, 1.45, 6),
                                TextDisplay.class,
                                display -> {
                                    display.setBillboard(Display.Billboard.CENTER);
                                    display.setPersistent(false);
                                });
        showColor();
    }

    private void showColor() {
        for (Player player : participants()) {
            floorInventories.computeIfAbsent(
                    player.getUniqueId(),
                    ignored -> RiptideColorFloorInventory.capture(player.getInventory()));
            RiptideColorFloorInventory.show(
                    player.getInventory(), new ItemStack(colors.target(), 64));
        }
        targetDisplay.text(
                LegacyText.component(text("color-prompt"))
                        .append(
                                Component.translatable(colors.target().translationKey())
                                        .color(
                                                net.kyori.adventure.text.format.NamedTextColor
                                                        .YELLOW)));
        area.playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, .8F, 1.4F);
    }

    private void tickColors(List<Player> players) {
        if (colors == null) return;
        if (!colors.tick()) {
            if (!colors.preparing()
                    && colors.remainingTicks() <= 60
                    && colors.remainingTicks() % 20 == 0)
                area.playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_HAT, .7F, 1.2F);
            return;
        }
        for (Player player : players) {
            if (!admitted(player)) continue;
            if (!colors.matches(player.getLocation(), geometry, step))
                rescue(player, "wrong-color");
        }
        if (colors.advance()) {
            platform.paint(colors.floor());
            showColor();
        } else {
            stopColors();
            departure.begin();
            area.playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_PLING, .8F, 1.4F);
        }
    }

    private void tickDodge(List<Player> players) {
        if (dodge == null) return;
        List<BoundingBox> hits = mobs.tick(dodge);
        for (Player player : players)
            if (admitted(player)
                    && hits.stream().anyMatch(box -> box.overlaps(player.getBoundingBox())))
                rescue(player, "hit");
        if (dodge.complete()) {
            stopDodge();
            departure.begin();
        }
    }

    private void rescue(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        if (!admitted(player) || rescueUntil.getOrDefault(uuid, 0) > tick) return;
        rescueUntil.put(uuid, tick + 40);
        falls.remove(uuid);
        boolean shield = shields.remove(uuid);
        player.sendMessage(LegacyText.component(text(reason)));
        player.sendMessage(LegacyText.component(text(shield ? "shield-used" : "retry")));
        if (shield) {
            shieldNoticeUntil.put(uuid, tick + 40);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
            player.sendActionBar(LegacyText.component(MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN));
        }
        teleport(player, respawn());
    }

    Location respawn() {
        Location center = geometry.centerAt(step);
        // Rescue onto a real deck cell, avoiding wall blocks and finding the current target floor.
        Location best = null;
        double distance = Double.MAX_VALUE;
        for (int f = -geometry.halfLength(); f <= geometry.halfLength(); f++) {
            for (int x = -geometry.halfWidth(); x <= geometry.halfWidth(); x++) {
                Location at = geometry.centerAt(step + f).add(x, 0, 0);
                if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable())
                    continue;
                if ((colors == null || colors.matches(at, geometry, step))
                        && at.distanceSquared(center) < distance) {
                    best = at;
                    distance = at.distanceSquared(center);
                }
            }
        }
        return best == null ? center : best;
    }

    private void teleport(Player player, Location location) {
        if (!admitted(player)) return;
        player.setVelocity(new Vector());
        player.setFallDistance(0F);
        player.teleport(location);
    }

    void disconnected(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) restoreFloorInventory(player);
        connections.remove(uuid);
        falls.remove(uuid);
        rescueUntil.remove(uuid);
    }

    private void stopColors() {
        if (targetDisplay != null && targetDisplay.isValid()) targetDisplay.remove();
        targetDisplay = null;
        if (platform != null) platform.restore();
        platform = null;
        colors = null;
        for (UUID uuid : List.copyOf(floorInventories.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) restoreFloorInventory(player);
        }
        floorInventories.clear();
    }

    boolean isColorFloorParticipant(Player player) {
        return colors != null && admitted(player);
    }

    private void restoreFloorInventory(Player player) {
        RiptideColorFloorInventory saved = floorInventories.remove(player.getUniqueId());
        if (saved != null
                && area.isGameplayParticipant(player)
                && Integer.valueOf(player.getEntityId())
                        .equals(connections.get(player.getUniqueId()))
                && plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) == area) {
            player.closeInventory();
            saved.restore(player.getInventory());
            player.resetTitle();
            player.sendActionBar(Component.empty());
        }
    }

    private void refreshPresentation(List<Player> players) {
        if (colors != null && (colors.preparing() || colors.remainingTicks() % 5 == 0)) {
            RiptidePresentation.Floor display = RiptidePresentation.floor(colors);
            for (Player player : players)
                if (admitted(player)) {
                    player.showTitle(display.title());
                    player.sendActionBar(
                            RiptidePresentation.floorActionbar(
                                    display.actionbar(),
                                    speedShieldNoticeUntil > tick,
                                    shieldNoticeUntil.getOrDefault(player.getUniqueId(), 0)
                                            > tick));
                }
            for (Player player : area.getOnlineSpectators()) {
                player.showTitle(display.title());
                player.sendActionBar(display.actionbar());
            }
        } else if (colors == null) {
            if (speedShieldNoticeUntil > tick)
                area.sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK);
            else
                for (Player player : players)
                    if (shieldNoticeUntil.getOrDefault(player.getUniqueId(), 0) > tick)
                        player.sendActionBar(
                                LegacyText.component(MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN));
        }
        if (dodge != null)
            area.sendActionBarToAllGamePlayers(
                    RiptidePresentation.dodgeActionbar(
                            RiptideDodgeRun.DURATION_TICKS - dodge.tickNumber()));
        if (departure.active())
            area.sendActionBarToAllGamePlayers(MessageConfig.RIPTIDE_RUSH_DEPARTURE_ACTIONBAR);
    }

    private void stopDodge() {
        if (mobs != null) mobs.clear();
        mobs = null;
        dodge = null;
    }

    static String text(String key) {
        return switch (key) {
            case "welcome" -> MessageConfig.codeText("riptide-introduction-run.welcome");
            case "follow" -> MessageConfig.codeText("riptide-introduction-run.follow");
            case "pass" -> MessageConfig.codeText("riptide-introduction-run.pass");
            case "color" -> MessageConfig.codeText("riptide-introduction-run.color");
            case "dodge" -> MessageConfig.codeText("riptide-introduction-run.dodge");
            case "speed" -> MessageConfig.codeText("riptide-introduction-run.speed");
            case "finish" -> MessageConfig.codeText("riptide-introduction-run.finish");
            case "color-prompt" -> MessageConfig.codeText("riptide-introduction-run.color-prompt");
            case "fall" -> MessageConfig.codeText("riptide-introduction-run.fall");
            case "wrong-color" -> MessageConfig.codeText("riptide-introduction-run.wrong-color");
            case "hit" -> MessageConfig.codeText("riptide-introduction-run.hit");
            case "shield-used" -> MessageConfig.codeText("riptide-introduction-run.shield-used");
            case "retry" -> MessageConfig.codeText("riptide-introduction-run.retry");
            default -> throw new IllegalArgumentException("Unknown lesson message: " + key);
        };
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (task != null) task.cancel();
        task = null;
        stopColors();
        stopDodge();
        if (arena.ownedBy(area)) {
            RiptideRaftBlocks.move(geometry, step, 0, Material.AIR);
            arena.release(area);
        }
        for (UUID uuid : connections.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null
                    && area.isGameplayParticipant(player)
                    && plugin.getGameManager().getBasePlayerArea(uuid) == area) {
                player.resetTitle();
                player.sendActionBar(Component.empty());
            }
        }
        connections.clear();
        falls.clear();
        rescueUntil.clear();
        shields.clear();
    }
}
