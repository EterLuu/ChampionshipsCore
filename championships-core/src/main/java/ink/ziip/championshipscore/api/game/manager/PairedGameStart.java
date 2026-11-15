package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.laserbox.runtime.LaserBoxArea;
import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.start.ArenaSelection;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Batch start orchestration; registrations continue to belong to GameManager. */
final class PairedGameStart {
    interface Slot {
        void arm(CompletableFuture<Void> gate);

        boolean start();

        void claim();

        void rollback();

        CompletableFuture<Void> ready();
    }

    private final ChampionshipsCore plugin;
    private final GameManager manager;

    PairedGameStart(ChampionshipsCore plugin, GameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** All acquired slots are released even if an earlier rollback itself fails. */
    static boolean execute(List<Slot> slots, java.util.function.Consumer<Runnable> dispatch) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        List<Slot> armed = new ArrayList<>();
        List<Slot> attempted = new ArrayList<>();
        try {
            for (Slot slot : slots) {
                armed.add(slot);
                slot.arm(gate);
            }
            for (Slot slot : slots) {
                attempted.add(slot);
                if (!slot.start()) {
                    RuntimeException cleanup = rollback(attempted, armed, gate, null);
                    if (cleanup != null) throw cleanup;
                    return false;
                }
                slot.claim();
            }
            CompletableFuture.allOf(
                            slots.stream().map(Slot::ready).toArray(CompletableFuture[]::new))
                    .whenComplete(
                            (unused, failure) -> {
                                try {
                                    dispatch.accept(() -> gate.complete(null));
                                } catch (RuntimeException rejected) {
                                    gate.completeExceptionally(rejected);
                                }
                            });
            return true;
        } catch (RuntimeException failure) {
            throw rollback(attempted, armed, gate, failure);
        }
    }

    private static RuntimeException rollback(
            List<Slot> attempted,
            List<Slot> armed,
            CompletableFuture<Void> gate,
            RuntimeException failure) {
        for (Slot slot : List.copyOf(attempted)) {
            try {
                slot.rollback();
            } catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else if (cleanup != failure) failure.addSuppressed(cleanup);
            }
        }
        attempted.clear();
        for (Slot slot : List.copyOf(armed)) {
            try {
                slot.arm(null);
            } catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else if (cleanup != failure) failure.addSuppressed(cleanup);
            }
        }
        armed.clear();
        gate.complete(null);
        return failure;
    }

    /** Starts copy-based pairings through one validation, ownership and rollback contract. */
    @Nullable
    List<BasePairedGameInstance> start(
            @NotNull GameTypeEnum game,
            @NotNull String map,
            @NotNull List<TwoVTwoVector> pairs,
            @NotNull ArenaSelection arenaSelection,
            boolean introduction,
            @NotNull GameRunMode mode) {
        if (mode == GameRunMode.EVENT && plugin.getScheduleManager() != null) {
            arenaSelection = plugin.getScheduleManager().selectedArenas(game);
        }
        if (!manager.isGameEnabled(game)
                || !plugin.getPrepareSessionManager().canStart(game, map)
                || pairs.isEmpty()) return null;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (var pair : pairs) {
            if (pair == null
                    || pair.getTeamOne() == null
                    || pair.getTeamTwo() == null
                    || !teams.add(pair.getTeamOne())
                    || !teams.add(pair.getTeamTwo())) return null;
        }
        if (manager.validateStartTeams(game, List.copyOf(teams), introduction, mode) != null)
            return null;
        BaseGameInstanceManager<?> areas = manager.getAreaManager(game);
        if (areas == null) return null;
        List<BasePairedGameInstance> pool =
                areas.getMapInstances(map).stream()
                        .filter(BasePairedGameInstance.class::isInstance)
                        .map(BasePairedGameInstance.class::cast)
                        .sorted(Comparator.comparingInt(BaseGameInstance::getCopyIndex))
                        .toList();
        if (pool.isEmpty()) return null;
        List<BasePairedGameInstance> selected;
        try {
            selected =
                    ink.ziip.championshipscore.api.game.start.StartTargets.copies(
                            pool, arenaSelection, pairs.size());
            for (var instance : selected)
                if (instance instanceof LaserBoxArea laser) laser.getGameConfig().validate();
        } catch (IllegalArgumentException failure) {
            return null;
        }
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            BasePairedGameInstance instance = selected.get(i);
            TwoVTwoVector pair = pairs.get(i);
            slots.add(
                    new Slot() {
                        @Override
                        public void arm(CompletableFuture<Void> gate) {
                            instance.coordinateStartWith(gate);
                        }

                        @Override
                        public boolean start() {
                            for (var team : List.of(pair.getTeamOne(), pair.getTeamTwo()))
                                for (UUID id : team.getMembers()) manager.removeSpectator(id);
                            instance.prepareRunMode(mode);
                            instance.setIntroductionEnabledForNextStart(introduction);
                            return instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo());
                        }

                        @Override
                        public void claim() {
                            manager.claimPairedStart(instance, pair);
                        }

                        @Override
                        public void rollback() {
                            manager.releaseStartRegistrations(instance);
                            try {
                                instance.abortAndReset();
                            } finally {
                                if (instance.getGameStageEnum()
                                        == ink.ziip.championshipscore.api.game.model.GameStageEnum
                                                .WAITING) {
                                    instance.prepareRunMode(GameRunMode.GAME);
                                    instance.setIntroductionEnabledForNextStart(false);
                                }
                            }
                        }

                        @Override
                        public CompletableFuture<Void> ready() {
                            return instance.getStartPreloadFuture();
                        }
                    });
        }
        if (!execute(slots, action -> plugin.getServer().getScheduler().runTask(plugin, action)))
            return null;
        manager.focusSpectatorsOn(selected.getFirst());
        return selected;
    }
}
