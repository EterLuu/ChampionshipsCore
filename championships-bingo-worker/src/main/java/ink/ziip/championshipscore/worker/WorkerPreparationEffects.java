package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoWorldRules;
import ink.ziip.championshipscore.platform.bukkit.player.PlayerStateService;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** Presentation and player operations for the session-owned preparation flow. */
final class WorkerPreparationEffects implements WorkerPreparation.Host {
    private final WorkerMatchSession session;
    private final Plugin plugin;
    private final WorkerConfig config;
    private final MatchManifest manifest;
    private final PlatformScheduler scheduler;
    private final WorkerTeamScatterService scatter;

    WorkerPreparationEffects(
            WorkerMatchSession session,
            Plugin plugin,
            WorkerConfig config,
            MatchManifest manifest,
            PlatformScheduler scheduler,
            WorkerTeamScatterService scatter) {
        this.session = session;
        this.plugin = plugin;
        this.config = config;
        this.manifest = manifest;
        this.scheduler = scheduler;
        this.scatter = scatter;
    }

    @Override
    public boolean valid() {
        return plugin.isEnabled() && session.state() == MatchState.COUNTDOWN;
    }

    @Override
    public Runnable later(Runnable action, long ticks) {
        ScheduledTask task = scheduler.runGlobalLater(action, ticks);
        return () -> task.cancel();
    }

    @Override
    public void execute(Runnable action) {
        scheduler.runGlobal(action);
    }

    @Override
    public void introduction(java.util.function.BooleanSupplier current) {
        forEachPreparationPlayer(
                current,
                (player, snapshot) -> {
                    if (snapshot.role() == ParticipantRole.PLAYER)
                        session.applyIntroductionMode(player);
                    else session.applySpectatorState(player);
                    Location destination = session.introductionLocation();
                    if (destination != null) player.teleportAsync(destination);
                    session.showTitle(
                            player,
                            message("game.introduction-title", "%game%", session.gameName()),
                            Component.empty(),
                            1,
                            100,
                            1);
                });
    }

    @Override
    public void introductionTick(int elapsedSeconds, java.util.function.BooleanSupplier current) {
        int duration = manifest.runtimeRules().introductionSeconds();
        int section =
                WorkerPresentationService.sectionAt(
                        elapsedSeconds,
                        duration,
                        manifest.runtimeRules().introductionRules().size());
        if (section >= 0) {
            List<String> lines = manifest.runtimeRules().introductionRules().get(section);
            forEachPreparationPlayer(
                    current,
                    (player, snapshot) -> {
                        WorkerPresentationService.sendSection(player, lines);
                        player.playSound(
                                player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F);
                    });
        }
        if (elapsedSeconds >= duration) return;
        int remaining = duration - elapsedSeconds;
        Component preparation =
                message(
                        "game.preparation-countdown",
                        "%game%",
                        session.gameName(),
                        "%time%",
                        WorkerPresentationService.bossBarTime(
                                manifest.runtimeRules().presentation(), remaining));
        session.updateTimerBossBar(preparation, remaining, duration);
    }

    @Override
    public void preparation(java.util.function.BooleanSupplier current) {
        Component preparationMessage = message("bingo.start-preparation");
        Component preparationTitle = message("bingo.start-preparation-title");
        Component preparationSubtitle = message("bingo.start-preparation-subtitle");
        Location destination = session.spectatorLocation();
        forEachPreparationPlayer(
                current,
                (player, snapshot) -> {
                    if (snapshot.role() == ParticipantRole.SPECTATOR) {
                        session.applySpectatorState(player);
                    } else {
                        player.getInventory().clear();
                        PlayerStateService.clearEffects(player);
                        player.setGameMode(GameMode.ADVENTURE);
                        player.setInvulnerable(false);
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                    PlayerStateService.resetVitals(player);
                    PlayerStateService.resetExperience(player);
                    if (destination != null) player.teleportAsync(destination);
                    player.sendMessage(preparationMessage);
                    session.showTitle(player, preparationTitle, preparationSubtitle, 1, 20, 1);
                });
    }

    @Override
    public void preparationTick(int remaining, java.util.function.BooleanSupplier current) {
        Component preparation =
                message(
                        "game.preparation-countdown",
                        "%game%",
                        session.gameName(),
                        "%time%",
                        WorkerPresentationService.bossBarTime(
                                manifest.runtimeRules().presentation(), remaining));
        session.updateTimerBossBar(
                preparation, remaining, manifest.runtimeRules().preparationSeconds());
    }

    @Override
    public CompletionStage<Void> prepare(java.util.function.BooleanSupplier current) {
        String scatterWorld =
                manifest.scoring().variant().remix() == BingoRemix.NETHER
                        ? config.nether()
                        : config.overworld();
        World world = plugin.getServer().getWorld(scatterWorld);
        if (world == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("overworld-unavailable-before-scatter"));
        }
        BingoWorldRules.setStartTime(world);
        List<Integer> playingTeams =
                session.preparationParticipants().stream()
                        .filter(snapshot -> snapshot.role() == ParticipantRole.PLAYER)
                        .map(PlayerSnapshot::teamId)
                        .distinct()
                        .toList();
        CompletableFuture<Void> locations =
                scatter.prepareAsync(
                        world, playingTeams, manifest.runtimeRules().scatterMaxTries());
        session.markRoundPrepared();
        List<CompletableFuture<Void>> preparations = new ArrayList<>();
        preparations.add(locations);
        for (PlayerSnapshot snapshot : session.preparationParticipants()) {
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player == null) continue;
            if (snapshot.role() == ParticipantRole.SPECTATOR) {
                preparations.add(
                        scheduler.runEntityFuture(
                                player,
                                () -> {
                                    if (session.currentPreparationPlayer(current, player, snapshot))
                                        session.applySpectatorState(player);
                                }));
            } else {
                preparations.add(
                        scheduler.runEntityFuture(
                                player,
                                () -> {
                                    if (session.currentPreparationPlayer(
                                            current, player, snapshot)) {
                                        session.prepareParticipantOnce(player, snapshot);
                                    }
                                }));
            }
        }
        return CompletableFuture.allOf(preparations.toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletionStage<Void> scatter(java.util.function.BooleanSupplier current) {
        List<CompletableFuture<Void>> teleports = new ArrayList<>();
        for (PlayerSnapshot snapshot : session.preparationParticipants()) {
            if (snapshot.role() != ParticipantRole.PLAYER) continue;
            Player player = plugin.getServer().getPlayer(snapshot.uuid());
            if (player != null)
                teleports.add(
                        scatter.teleportAsync(
                                player,
                                snapshot.teamId(),
                                () -> session.currentPreparationPlayer(current, player, snapshot)));
        }
        return CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new));
    }

    @Override
    public void countdown(int remaining, java.util.function.BooleanSupplier current) {
        Component title =
                message("game.start-countdown-title", "%time%", Integer.toString(remaining));
        Component subtitle = message("game.start-countdown-subtitle", "%game%", session.gameName());
        forEachPreparationPlayer(
                current,
                (player, snapshot) -> {
                    session.showTitle(player, title, subtitle, 1, 20, 1);
                    player.playNote(
                            player.getLocation(), Instrument.BIT, Note.natural(0, Note.Tone.C));
                });
    }

    @Override
    public void hideTimer() {
        session.hideTimerBossBar();
    }

    @Override
    public void start() {
        session.beginRunning();
    }

    @Override
    public void failed(String reason, Throwable failure) {
        plugin.getLogger()
                .log(Level.SEVERE, "Unable to complete Bingo preparation: " + reason, failure);
        session.abort(reason);
    }

    private void forEachPreparationPlayer(
            java.util.function.BooleanSupplier current,
            java.util.function.BiConsumer<Player, PlayerSnapshot> action) {
        session.forEachOnlineParticipant(
                (player, snapshot) -> {
                    if (session.currentPreparationPlayer(current, player, snapshot))
                        action.accept(player, snapshot);
                });
    }

    private Component message(String key, String... replacements) {
        return WorkerPresentationService.message(
                manifest.runtimeRules().presentation(), key, replacements);
    }
}
