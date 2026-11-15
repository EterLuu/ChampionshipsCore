package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spatial.TeleportPositions;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Owns an instance's map restoration, draft publication and pending map operations. */
final class GameMapLifecycle {
    private final BaseGameInstance instance;
    private final ChampionshipsCore plugin;
    private final BukkitScheduler scheduler;
    private final Set<CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Boolean> activeMapOperation = CompletableFuture.completedFuture(true);
    private long mapLoadGeneration;
    private volatile boolean closed;

    GameMapLifecycle(BaseGameInstance instance) {
        this.instance = instance;
        this.plugin = instance.plugin;
        this.scheduler = instance.scheduler;
    }

    CompletableFuture<Boolean> activeOperation() {
        return activeMapOperation;
    }

    void dispose() {
        closed = true;
        mapLoadGeneration++;
        // Completion may run dependent callbacks inline, so close the lifecycle first.
        for (CompletableFuture<Boolean> request : pending) request.complete(false);
        pending.clear();
    }

    private boolean isAvailable() {
        return !closed && !instance.isDisposed() && plugin.isLoaded() && plugin.isEnabled();
    }

    private CompletableFuture<Boolean> pendingResult() {
        var result = new CompletableFuture<Boolean>();
        pending.add(result);
        result.whenComplete((success, error) -> pending.remove(result));
        if (!isAvailable()) result.complete(false);
        return result;
    }

    CompletableFuture<Boolean> loadMap(World.Environment environment) {
        return startOperation(() -> restoreMap(environment), false);
    }

    CompletableFuture<Boolean> loadPublishedMapOrDraft(World.Environment environment) {
        return startOperation(() -> restorePublishedMapOrDraft(environment), false);
    }

    CompletableFuture<Boolean> saveMap(World.Environment environment) {
        return startOperation(() -> publishMap(environment), true);
    }

    private CompletableFuture<Boolean> startOperation(
            Supplier<CompletableFuture<Boolean>> operation, boolean publication) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        if (!Bukkit.isPrimaryThread())
            return runOnMain(() -> startOperation(operation, publication));
        if (!activeMapOperation.isDone())
            return publication ? CompletableFuture.completedFuture(false) : activeMapOperation;
        CompletableFuture<Boolean> result = pendingResult();
        activeMapOperation = result;
        try {
            operation
                    .get()
                    .whenComplete(
                            (success, error) -> {
                                if (error != null) result.completeExceptionally(error);
                                else result.complete(success);
                            });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private CompletableFuture<Boolean> restoreMap(World.Environment environment) {
        CompletableFuture<Boolean> result = pendingResult();
        long generation = ++mapLoadGeneration;
        long startedAt = System.nanoTime();

        instance.clearBossBars();
        instance.teleportAllSpectators(instance.getLobbyLocation());

        instance.setGameStageEnum(GameStageEnum.END);
        instance.getGameHandler().unRegister();
        instance.logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.load-map-1"),
                MessageConfig.codeText("base-game-instance.load-map-2", instance.getWorldName()));

        // Repair the published template and preserve the current world before reload deletes either
        // copy.
        if (!plugin.getWorldManager().prepareWorldForFirstLoad(instance.getWorldName())) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.load-map-3", instance.getWorldName()));
            result.complete(false);
            return result;
        }

        File target = plugin.getWorldManager().getWorldFolder(instance.getWorldName());
        World loadedWorld = plugin.getServer().getWorld(instance.getWorldName());
        if (loadedWorld != null
                && !plugin.getWorldManager().unloadWorld(instance.getWorldName(), false)) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.load-map-4", instance.getWorldName()));
            result.complete(false);
            return result;
        }
        long unloadedAt = System.nanoTime();

        File maps = new File(plugin.getDataFolder(), "maps");
        File source = new File(maps, instance.getWorldName());
        runAsyncFileOperation(() -> replaceWorldFiles(source, target))
                .thenCompose(
                        filesReady ->
                                runOnMain(
                                        () ->
                                                CompletableFuture.completedFuture(
                                                        finishMapLoad(
                                                                environment,
                                                                generation,
                                                                startedAt,
                                                                unloadedAt,
                                                                filesReady))))
                .whenComplete(
                        (success, error) -> {
                            if (error != null)
                                instance.logGame(
                                        Level.SEVERE,
                                        MessageConfig.codeText("base-game-instance.load-map-1"),
                                        MessageConfig.codeText(
                                                "base-game-instance.load-map-5",
                                                instance.getWorldName(),
                                                error.getMessage()));
                            result.complete(error == null && Boolean.TRUE.equals(success));
                        });
        return result;
    }

    private boolean finishMapLoad(
            World.Environment environment,
            long generation,
            long startedAt,
            long unloadedAt,
            boolean filesReady) {
        if (!isAvailable() || generation != mapLoadGeneration) return false;
        if (!filesReady) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.finish-map-load-1", instance.getWorldName()));
            return false;
        }
        long filesReadyAt = System.nanoTime();
        if (!plugin.getWorldManager().loadWorld(instance.getWorldName(), environment, false)) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.finish-map-load-2", instance.getWorldName()));
            return false;
        }

        if (!instance.getGameConfig().reloadConfigurationChecked(plugin.getFolder())) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.finish-map-load-3"),
                    MessageConfig.codeText(
                            "base-game-instance.finish-map-load-4", instance.getWorldName()));
            return false;
        }
        instance.getGameHandler().register();
        instance.setGameStageEnum(GameStageEnum.WAITING);
        long finishedAt = System.nanoTime();
        instance.logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.load-map-1"),
                MessageConfig.codeText(
                        "base-game-instance.finish-map-load-5",
                        instance.getWorldName(),
                        elapsedMillis(startedAt, unloadedAt),
                        elapsedMillis(unloadedAt, filesReadyAt),
                        elapsedMillis(filesReadyAt, finishedAt)));
        instance.teleportAllSpectators(instance.getSpectatorSpawnLocation());
        return true;
    }

    private boolean replaceWorldFiles(File source, File target) {
        if (target.exists() && !plugin.getWorldManager().deleteWorldFiles(target)) return false;
        if (plugin.getWorldManager().copyWorldFiles(source, target)) return true;
        plugin.getWorldManager().deleteWorldFiles(target);
        return false;
    }

    private long elapsedMillis(long startedAt, long finishedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt);
    }

    /**
     * Restores a published map template when one exists. New maps deliberately have no template
     * until prepare publishes their first revision, so reopening the server must keep their
     * editable draft world instead of deleting it and then failing a template copy.
     */
    private CompletableFuture<Boolean> restorePublishedMapOrDraft(World.Environment environment) {
        if (!instance.getGameConfig().reloadConfigurationChecked(plugin.getFolder()))
            return CompletableFuture.completedFuture(false);
        if (instance.getGameConfig().isWorldBindingPending()) {
            instance.getGameHandler().register();
            instance.setGameStageEnum(GameStageEnum.WAITING);
            instance.logGame(
                    Level.INFO,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText("base-game-instance.load-published-map-or-draft-1"));
            return CompletableFuture.completedFuture(true);
        }
        File template = new File(new File(plugin.getDataFolder(), "maps"), instance.getWorldName());
        if (instance.getGameConfig().isPrepareReady() && template.isDirectory())
            return restoreMap(environment);

        if (instance.getGameConfig().isPrepareReady()) {
            instance.getGameConfig().beginPrepareDraft();
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.load-published-map-or-draft-2",
                            template.getPath()));
        }
        return CompletableFuture.completedFuture(loadDraftWorld(environment));
    }

    private boolean loadDraftWorld(World.Environment environment) {
        instance.clearBossBars();
        instance.teleportAllSpectators(instance.getLobbyLocation());
        instance.setGameStageEnum(GameStageEnum.END);
        instance.getGameHandler().unRegister();
        instance.logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.load-map-1"),
                MessageConfig.codeText(
                        "base-game-instance.load-draft-world-1", instance.getWorldName()));

        if (!plugin.getWorldManager().loadWorld(instance.getWorldName(), environment, false)) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.load-draft-world-2", instance.getWorldName()));
            return false;
        }

        instance.getGameHandler().register();
        instance.setGameStageEnum(GameStageEnum.WAITING);
        instance.logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.load-map-1"),
                MessageConfig.codeText("base-game-instance.load-draft-world-3"));
        return true;
    }

    private CompletableFuture<Boolean> publishMap(World.Environment environment) {
        if (!instance.canSaveMap()) {
            instance.logGame(
                    Level.WARNING,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText("base-game-instance.save-map-1"));
            return CompletableFuture.completedFuture(false);
        }

        instance.setGameStageEnum(GameStageEnum.END);
        instance.logGame(
                Level.INFO,
                MessageConfig.codeText("base-game-instance.load-map-1"),
                MessageConfig.codeText("base-game-instance.save-map-2", instance.getWorldName()));
        instance.teleportAllSpectators(instance.getLobbyLocation());

        World editWorld = plugin.getServer().getWorld(instance.getWorldName());
        if (editWorld == null) {
            instance.setGameStageEnum(GameStageEnum.WAITING);
            instance.logGame(
                    Level.WARNING,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.save-map-3", instance.getWorldName()));
            return CompletableFuture.completedFuture(false);
        }
        for (Player player : editWorld.getPlayers()) {
            player.teleport(
                    TeleportPositions.getScatteredLobbyLocation(CCConfig.LOBBY_LOCATION, player));
        }

        // Unload world but not remove files
        if (!plugin.getWorldManager().unloadWorld(instance.getWorldName(), true)) {
            instance.setGameStageEnum(GameStageEnum.WAITING);
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.save-map-4", instance.getWorldName()));
            return CompletableFuture.completedFuture(false);
        }

        File dataDirectory = new File(plugin.getDataFolder(), "maps");
        File target = new File(dataDirectory, instance.getWorldName());
        File source = plugin.getWorldManager().getWorldFolder(instance.getWorldName());
        String transaction = ".prepare-" + instance.getWorldName() + "-" + UUID.randomUUID();
        File staging = new File(dataDirectory, transaction + "-staging");
        File backup = new File(dataDirectory, transaction + "-previous");

        return runAsyncFileOperation(() -> stagePublishedTemplate(source, target, staging, backup))
                .thenCompose(
                        staged ->
                                runOnMain(
                                        () ->
                                                finishPublication(
                                                        environment,
                                                        source,
                                                        target,
                                                        backup,
                                                        staged)));
    }

    private CompletableFuture<Boolean> finishPublication(
            World.Environment environment, File source, File target, File backup, boolean staged) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        if (!staged) {
            if (!loadDraftWorld(environment))
                instance.logGame(
                        Level.SEVERE,
                        MessageConfig.codeText("base-game-instance.load-map-1"),
                        MessageConfig.codeText(
                                "base-game-instance.save-map-5", instance.getWorldName()));
            return CompletableFuture.completedFuture(false);
        }
        return restoreMap(environment)
                .thenCompose(
                        loaded -> {
                            if (!isAvailable()) return CompletableFuture.completedFuture(false);
                            return loaded
                                    ? discardPreviousTemplate(backup)
                                    : rollbackPublishedTemplate(
                                            environment, source, target, backup);
                        });
    }

    private CompletableFuture<Boolean> discardPreviousTemplate(File backup) {
        return runAsyncFileOperation(
                () -> {
                    if (backup.exists() && !plugin.getWorldManager().deleteWorldFiles(backup))
                        instance.logGame(
                                Level.WARNING,
                                MessageConfig.codeText("base-game-instance.load-map-1"),
                                MessageConfig.codeText(
                                        "base-game-instance.save-map-6", backup.getPath()));
                    return true;
                });
    }

    private boolean stagePublishedTemplate(File source, File target, File staging, File backup) {
        if (!plugin.getWorldManager().copyWorldFiles(source, staging)) {
            plugin.getWorldManager().deleteWorldFiles(staging);
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.stage-published-template-1",
                            instance.getWorldName()));
            return false;
        }
        try {
            if (target.exists()) java.nio.file.Files.move(target.toPath(), backup.toPath());
            java.nio.file.Files.move(staging.toPath(), target.toPath());
        } catch (Exception exception) {
            plugin.getWorldManager().deleteWorldFiles(staging);
            try {
                if (!target.exists() && backup.exists())
                    java.nio.file.Files.move(backup.toPath(), target.toPath());
            } catch (Exception rollback) {
                instance.logGame(
                        Level.SEVERE,
                        MessageConfig.codeText("base-game-instance.load-map-1"),
                        MessageConfig.codeText(
                                "base-game-instance.stage-published-template-2",
                                rollback.getMessage()));
            }
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.stage-published-template-3",
                            exception.getMessage()));
            return false;
        }
        if (!plugin.getWorldManager().deleteWorldFiles(source))
            instance.logGame(
                    Level.WARNING,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.stage-published-template-4", source.getPath()));
        return true;
    }

    private CompletableFuture<Boolean> rollbackPublishedTemplate(
            World.Environment environment, File source, File target, File backup) {
        if (!isAvailable() || !backup.exists()) return CompletableFuture.completedFuture(false);
        World loadedWorld = plugin.getServer().getWorld(instance.getWorldName());
        if (loadedWorld != null
                && !plugin.getWorldManager().unloadWorld(instance.getWorldName(), false)) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText("base-game-instance.rollback-published-template-1"));
            return CompletableFuture.completedFuture(false);
        }
        return runAsyncFileOperation(
                        () -> {
                            if (!plugin.getWorldManager().deleteWorldFiles(source)
                                    || !plugin.getWorldManager().deleteWorldFiles(target))
                                return false;
                            try {
                                java.nio.file.Files.move(backup.toPath(), target.toPath());
                                return true;
                            } catch (Exception exception) {
                                instance.logGame(
                                        Level.SEVERE,
                                        MessageConfig.codeText("base-game-instance.load-map-1"),
                                        MessageConfig.codeText(
                                                "base-game-instance.rollback-published-template-3",
                                                exception.getMessage()));
                                return false;
                            }
                        })
                .thenCompose(
                        restored ->
                                runOnMain(
                                        () -> {
                                            if (!restored)
                                                return CompletableFuture.completedFuture(false);
                                            return restoreMap(environment)
                                                    .thenApply(
                                                            ignored -> {
                                                                instance.logGame(
                                                                        Level.SEVERE,
                                                                        MessageConfig.codeText(
                                                                                "base-game-instance.load-map-1"),
                                                                        MessageConfig.codeText(
                                                                                "base-game-instance.rollback-published-template-2"));
                                                                return false;
                                                            });
                                        }));
    }

    private CompletableFuture<Boolean> runAsyncFileOperation(BooleanSupplier operation) {
        CompletableFuture<Boolean> result = pendingResult();
        if (!isAvailable()) {
            result.complete(false);
            return result;
        }
        try {
            scheduler.runTaskAsynchronously(
                    plugin,
                    () -> {
                        if (!isAvailable()) {
                            result.complete(false);
                            return;
                        }
                        try {
                            result.complete(operation.getAsBoolean());
                        } catch (Throwable throwable) {
                            instance.logGame(
                                    Level.SEVERE,
                                    MessageConfig.codeText("base-game-instance.load-map-1"),
                                    MessageConfig.codeText(
                                            "base-game-instance.run-async-file-operation-2",
                                            throwable.getMessage()));
                            result.complete(false);
                        }
                    });
        } catch (RuntimeException exception) {
            instance.logGame(
                    Level.SEVERE,
                    MessageConfig.codeText("base-game-instance.load-map-1"),
                    MessageConfig.codeText(
                            "base-game-instance.run-async-file-operation-1",
                            exception.getMessage()));
            result.complete(false);
        }
        return result;
    }

    CompletableFuture<Boolean> runOnMain(Supplier<CompletableFuture<Boolean>> operation) {
        if (!isAvailable()) return CompletableFuture.completedFuture(false);
        if (Bukkit.isPrimaryThread()) return operation.get();
        CompletableFuture<Boolean> result = pendingResult();
        if (!isAvailable()) {
            result.complete(false);
            return result;
        }
        try {
            scheduler.runTask(
                    plugin,
                    () -> {
                        if (!isAvailable()) {
                            result.complete(false);
                            return;
                        }
                        try {
                            operation
                                    .get()
                                    .whenComplete(
                                            (success, error) -> {
                                                if (error != null)
                                                    result.completeExceptionally(error);
                                                else result.complete(success);
                                            });
                        } catch (Throwable throwable) {
                            result.completeExceptionally(throwable);
                        }
                    });
        } catch (RuntimeException exception) {
            result.complete(false);
        }
        return result;
    }
}
