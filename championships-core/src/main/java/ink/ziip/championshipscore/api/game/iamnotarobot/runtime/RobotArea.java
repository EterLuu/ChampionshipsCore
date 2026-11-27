package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.iamnotarobot.config.RobotConfig;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.EntryTrack;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse.Room;
import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointDeck;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointRigState.Phase;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderAssignment;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.SliderDeck;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.VerificationRun;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.WarehouseCode;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class RobotArea extends BaseMultiTeamGameInstance {
    private static final String MESSAGE_PREFIX = "robot.";

    private record Lane(
            Vector origin,
            Vector entryOrigin,
            ChampionshipTeam team,
            List<UUID> members,
            RobotProps props,
            Warehouse warehouse,
            long generation,
            SliderAudience audience,
            List<SliderStation> sliders,
            SliderAssignment assignment,
            VerificationRun run,
            JointRig joint) {}

    private record DigAttempt(JointRig rig, long epoch, int x, int y, int z) {}

    private final Map<UUID, DigAttempt> digs = new HashMap<>();
    private final List<Lane> lanes = new ArrayList<>();
    private final Map<UUID, Room> announcedRooms = new HashMap<>();
    private final List<Integer> sliderTimes = new ArrayList<>();
    private final List<Long> jointTimes = new ArrayList<>();
    private final SliderMaps maps;
    private SliderMaps.Edition edition;
    private World editionWorld;
    private BukkitTask clock;
    private int startTick, remaining;

    public RobotArea(ChampionshipsCore plugin, RobotConfig config, SliderMaps maps) {
        super(plugin, GameTypeEnum.IAmNotARobot, new RobotHandler(plugin), config);
        this.maps = maps;
        ((RobotHandler) gameHandler).area = this;
        gameHandler.register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public RobotConfig getGameConfig() {
        return (RobotConfig) gameConfig;
    }

    @Override
    public BaseListener getGameHandler() {
        return gameHandler;
    }

    @Override
    public String getWorldName() {
        return getGameConfig().getWorldName();
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getGameConfig().getSpectatorSpawnPoint();
    }

    @Override
    public int getTimer() {
        return remaining;
    }

    private List<Integer> indices() {
        return getSelectedArenaIndices(getGameConfig().getLanes());
    }

    private boolean canAssign(List<ChampionshipTeam> teams) {
        if (!getGameConfig().isPrepareReady()
                || teams == null
                || teams.isEmpty()
                || teams.size() > indices().size()) return false;
        Set<UUID> seen = new HashSet<>();
        for (var team : teams) {
            if (team == null || team.getMembers().isEmpty()) return false;
            for (UUID id : team.getMembers()) if (id == null || !seen.add(id)) return false;
        }
        return true;
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams) {
        return canAssign(teams)
                && teams.stream().allMatch(t -> t.getMembers().size() == EntryTrack.COUNT)
                && super.tryStartGame(teams);
    }

    @Override
    public boolean tryStartGame(List<ChampionshipTeam> teams, List<UUID> players) {
        return canAssign(teams)
                && players != null
                && teams.stream()
                        .allMatch(
                                t ->
                                        t.getMembers().stream().filter(players::contains).count()
                                                == EntryTrack.COUNT)
                && super.tryStartGame(teams, players);
    }

    @Override
    protected Collection<Location> getStartPreloadLocations() {
        List<Location> points = new ArrayList<>();
        World world = Bukkit.getWorld(getWorldName());
        var selected = indices();
        for (int team = 0; team < gameTeams.size(); team++) {
            int index = selected.get(team);
            // Preload the standalone Stage 0 city block (lanes + flanking streets/buildings) and
            // the warehouse and downstream puzzle rooms.
            Vector entryOrigin = EntryTrack.origin(index);
            for (int x = EntryTrack.MIN_X; x < EntryTrack.MIN_X + EntryTrack.WIDTH; x += 16) {
                for (int z = EntryTrack.MIN_Z; z < 0; z += 16)
                    points.add(entryOrigin.clone().add(new Vector(x, 1, z)).toLocation(world));
                points.add(entryOrigin.clone().add(new Vector(x, 1, -5)).toLocation(world));
            }
            points.add(
                    entryOrigin
                            .clone()
                            .add(new Vector(EntryTrack.MIN_X + EntryTrack.WIDTH - 1, 1, -60))
                            .toLocation(world));
            Vector origin = VerificationCourse.GRID.origin(index);
            for (Vector offset : JointPlaza.preloadPoints())
                points.add(origin.clone().add(offset).toLocation(world));
            for (int cx = (SliderHall.MIN_X >> 4);
                    cx <= ((SliderHall.MIN_X + SliderHall.WIDTH - 1) >> 4);
                    cx++)
                for (int cz = SliderHall.MIN_Z >> 4; cz <= SliderHall.END_Z >> 4; cz++)
                    points.add(
                            origin.clone().add(new Vector(cx * 16, 1, cz * 16)).toLocation(world));
            for (int x = WarehouseRoom.STRUCT_MIN_X;
                    x < WarehouseRoom.STRUCT_MIN_X + WarehouseRoom.STRUCT_WIDTH;
                    x += 16)
                for (int z : List.of(WarehouseRoom.MIN_Z, WarehouseRoom.END_Z))
                    points.add(origin.clone().add(new Vector(x, 1, z)).toLocation(world));
            points.add(
                    origin.clone()
                            .add(
                                    new Vector(
                                            WarehouseRoom.STRUCT_MIN_X
                                                    + WarehouseRoom.STRUCT_WIDTH
                                                    - 1,
                                            1,
                                            WarehouseRoom.END_Z))
                            .toLocation(world));
            for (int x :
                    List.of(
                            WarehouseRoom.MIN_X,
                            1,
                            VerificationCourse.WIDTH - 2,
                            WarehouseRoom.MAX_X))
                for (int z = WarehouseRoom.MIN_Z; z < VerificationCourse.LENGTH + 15; z += 16)
                    points.add(
                            origin.clone()
                                    .add(
                                            new Vector(
                                                    x,
                                                    1,
                                                    Math.min(z, VerificationCourse.LENGTH - 1)))
                                    .toLocation(world));
        }
        return points;
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        editionWorld = Objects.requireNonNull(Bukkit.getWorld(getWorldName()));
        edition = maps.acquire(editionWorld);
        var deck = new SliderDeck(edition.seed);
        sliderTimes.clear();
        jointTimes.clear();
        var joints = new JointDeck(new Random().nextLong());
        var challenge = WarehouseCode.draw(new Random());
        long generation = captureGameGeneration();
        var setup = new ArrayDeque<Runnable>();
        var pick = new ItemStack(Material.IRON_PICKAXE);
        setup.add(() -> Warehouse.configurePick(pick, challenge));
        var selected = indices();
        for (int i = 0; i < gameTeams.size(); i++) {
            var team = gameTeams.get(i);
            Vector origin = VerificationCourse.GRID.origin(selected.get(i));
            World world = Objects.requireNonNull(Bukkit.getWorld(getWorldName()));
            var sweep = new SliderAudience(world, origin, edition);
            var props = new RobotProps();
            var stations =
                    Arrays.stream(SliderHall.values())
                            .map(
                                    h ->
                                            new SliderStation(
                                                    plugin, world, origin, h, deck, maps, edition,
                                                    sweep, props))
                            .toList();
            var lane =
                    new Lane(
                            VerificationCourse.GRID.origin(selected.get(i)),
                            EntryTrack.origin(selected.get(i)),
                            team,
                            team.getMembers().stream().filter(gamePlayers::contains).toList(),
                            props,
                            new Warehouse(
                                    Objects.requireNonNull(Bukkit.getWorld(getWorldName())),
                                    VerificationCourse.GRID.origin(selected.get(i)),
                                    challenge,
                                    props),
                            captureGameGeneration(),
                            sweep,
                            stations,
                            new SliderAssignment(),
                            new VerificationRun(),
                            new JointRig(
                                    world, origin, joints, maps.jointWork, props, this::elapsed));
            lanes.add(lane);
            lane.joint.bind(
                    () -> liveJoint(lane),
                    () -> lane.members.stream().anyMatch(this::activeInWorld),
                    p -> jointPlayer(lane, p),
                    () -> ejectJoint(lane),
                    message -> {
                        teamMessage(lane, message);
                        for (UUID id : lane.members) {
                            Player p = Bukkit.getPlayer(id);
                            if (p != null && jointPlayer(lane, p))
                                p.sendActionBar(LegacyText.component(message));
                        }
                    },
                    pass -> jointPassed(lane, pass),
                    () -> {
                        if (liveJoint(lane) && lane.run.completeJoint(elapsed())) {
                            removeJointTools(lane);
                            opened(lane, Room.JOINT);
                        }
                    });
            lane.joint.prepare(() -> preparingJoint(lane));
            setup.add(() -> resetGates(lane));
            lane.warehouse.prepare(setup, pick);
        }
        remaining = getGameConfig().getTimer();
        queuePreparation(generation, setup, () -> true);
        startGameIntroduction(
                () -> {
                    var positions = new ArrayDeque<Runnable>();
                    for (UUID id : gamePlayers) {
                        positions.add(
                                () -> {
                                    var player = Bukkit.getPlayer(id);
                                    if (player != null) restoreLane(player);
                                });
                    }
                    positions.add(
                            () ->
                                    startFinalCountdown(
                                            text("name"), text("start"), "", this::begin));
                    // Manual starts can skip the introduction entirely.
                    queuePreparation(
                            generation,
                            positions,
                            () ->
                                    setup.isEmpty()
                                            && lanes.stream().allMatch(l -> l.joint.prepared()));
                });
    }

    private void queuePreparation(long generation, Deque<Runnable> steps, BooleanSupplier ready) {
        BooleanSupplier current =
                () ->
                        generation == captureGameGeneration()
                                && !isDisposed()
                                && getGameStageEnum() == GameStageEnum.PREPARATION;
        maps.work.add(
                current,
                () -> {
                    if (!current.getAsBoolean()) return true;
                    if (!ready.getAsBoolean()) return false;
                    steps.removeFirst().run();
                    return steps.isEmpty();
                },
                16,
                false);
    }

    private void begin() {
        for (var lane : lanes) startGate(lane, true);
        startTick = Bukkit.getCurrentTick();
        long generation = captureGameGeneration();
        clock =
                scheduler.runTaskTimer(
                        plugin,
                        () -> {
                            if (!running() || generation != captureGameGeneration()) return;
                            int tick = elapsed();
                            if (expired(tick)) {
                                endGame();
                                return;
                            }
                            maps.publishIfReady(edition);
                            int seconds = getGameConfig().getTimer() - tick / 20;
                            if (seconds != remaining || tick < 3) {
                                remaining = seconds;
                                updateGameTimerBossBar(
                                        text("timer", remaining),
                                        remaining,
                                        getGameConfig().getTimer());
                            }
                            for (var lane : lanes) sample(lane, tick);
                            if (lanes.stream().allMatch(l -> l.run.finished())) endGame();
                        },
                        1L,
                        2L);
    }

    private int elapsed() {
        return Bukkit.getCurrentTick() - startTick;
    }

    private boolean expired(int tick) {
        return tick >= getGameConfig().getTimer() * 20;
    }

    private boolean running() {
        return !isDisposed() && getGameStageEnum() == GameStageEnum.PROGRESS;
    }

    private Lane lane(UUID id) {
        for (var lane : lanes) if (lane.members.contains(id)) return lane;
        return null;
    }

    private boolean admitted(Player p) {
        return isCurrentParticipantConnection(captureGameGeneration(), p)
                && isGameplayParticipant(p)
                && p.isOnline()
                && !p.isDead();
    }

    private boolean inWorld(Player p) {
        return p.getWorld().getName().equals(getWorldName());
    }

    private List<UUID> requiredMembers(Lane lane) {
        return lane.members.stream()
                .filter(gamePlayers::contains)
                .filter(id -> lane.run.room() != Room.SLIDER || activeInWorld(id))
                .filter(id -> !plugin.getGameManager().getSpectatorManager().isSpectatorLike(id))
                .toList();
    }

    private int required(Lane lane) {
        return requiredMembers(lane).size();
    }

    /** Every present member met the quota and no holder is still finishing a round. */
    private boolean sliderCleared(Lane lane) {
        if (lane.sliders.stream().allMatch(st -> st.state.complete())) return true;
        var needed = requiredMembers(lane);
        return !needed.isEmpty()
                && needed.stream()
                        .map(lane.assignment::track)
                        .allMatch(
                                hall ->
                                        hall != null
                                                && lane.sliders
                                                        .get(hall.ordinal())
                                                        .state
                                                        .complete())
                && lane.sliders.stream().allMatch(st -> st.state.holder() == null);
    }

    private void sample(Lane lane, int tick) {
        var run = lane.run;
        if (!liveLane(lane) || run.finished()) return;
        if (run.room() == Room.ENTRY) {
            sampleEntry(lane, tick);
            return;
        }
        if (!run.begun()) {
            sampleEntrance(lane, tick);
            if (!run.begun() || !liveLane(lane)) return;
        }
        if (run.room() == Room.JOINT && liveJoint(lane)) {
            boolean present = lane.members.stream().anyMatch(this::activeInWorld);
            if (!present && lane.joint.state.phase() != Phase.DORMANT && !lane.joint.suspended()) {
                lane.joint.suspend();
                removeJointTools(lane);
            } else if (present) {
                if (lane.joint.suspended()) lane.joint.open();
                lane.joint.tick(tick);
            }
        }
        if (liveSlider(lane)) {
            for (var station : lane.sliders) {
                station.maintain(tick);
                UUID id = station.state.holder();
                Player holder = id == null ? null : Bukkit.getPlayer(id);
                if (id != null
                        && (holder == null
                                || !admitted(holder)
                                || !inWorld(holder)
                                || station.column(holder) < 0)) {
                    station.leave(id, holder);
                    holder = null;
                }
                station.tick(tick, holder, id != null && run.sliderDone(id));
            }
            if (sliderCleared(lane) && run.completeSlider(tick)) {
                opened(lane, Room.SLIDER);
                return;
            }
        }
        int atFinish = 0;
        for (int slot = 0; slot < lane.members.size(); slot++) {
            UUID id = lane.members.get(slot);
            var p = Bukkit.getPlayer(id);
            if (p == null || !admitted(p) || !inWorld(p)) {
                if (p != null) {
                    lane.joint.tools.remove(p);
                    lane.props.recover(p, RobotProps.Kind.WAREHOUSE_PICK);
                    leaveStations(lane, p);
                }
                continue;
            }
            Vector position = p.getLocation().toVector().subtract(lane.origin);
            Room room = run.room();
            int z = position.getBlockZ();
            if (room != Room.JOINT || z < JointPlaza.MIN_Z) lane.joint.tools.remove(p);
            if (room != Room.WAREHOUSE || z < WarehouseRoom.MIN_Z || z > WarehouseRoom.END_Z)
                lane.props.recover(p, RobotProps.Kind.WAREHOUSE_PICK);
            if (!room.allows(slot, position, true)) {
                restoreLane(p);
                continue;
            }
            switch (room) {
                case WAREHOUSE -> {
                    if (run.onEntryTrack(slot) || tick % 20 < 2) {
                        run.enterPuzzleRooms(slot);
                        equipMiner(lane, p, slot);
                    }
                }
                case SLIDER -> sampleSlider(lane, p, position, tick);
                case JOINT -> {
                    if (jointPlayer(lane, p)) {
                        lane.joint.tools.give(p);
                        ejectJointPlayer(lane, p);
                    }
                }
                case SPRINT -> {
                    if (position.getBlockZ() >= VerificationCourse.FINISH) atFinish++;
                }
            }
        }
        int needed = required(lane);
        if (run.room() == Room.WAREHOUSE
                && lane.warehouse.sample()
                && run.completeWarehouse(tick)) {
            lane.warehouse.finish(lane.members);
            opened(lane, Room.WAREHOUSE);
        } else if (run.crossFinish(needed > 0 && atFinish == needed, tick)) {
            sendMessageToAllGamePlayers(text("finish", lane.team.getColoredName(), seconds(tick)));
        }
    }

    private List<Player> presentMembers(Lane lane) {
        return lane.members.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(this::admitted)
                .filter(this::inWorld)
                .toList();
    }

    private void sampleEntrance(Lane lane, int tick) {
        Room room = lane.run.room();
        if (room == Room.SLIDER) for (var station : lane.sliders) station.maintain(tick);
        var present = presentMembers(lane);
        if (present.isEmpty()) return;
        var missing = new ArrayList<Player>();
        for (Player p : present) {
            int slot = lane.members.indexOf(p.getUniqueId());
            Vector position = p.getLocation().toVector().subtract(lane.origin);
            if (room.entered(slot, position, p.getBoundingBox().getMinZ() - lane.origin.getZ())) {
                lane.run.enterPuzzleRooms(slot);
                continue;
            }
            missing.add(p);
            if (!room.allows(slot, position, false)) restoreLane(p);
        }
        if (!missing.isEmpty()) {
            if (tick % 20 < 2) {
                String names =
                        String.join(
                                text("wait-separator"),
                                missing.stream().map(Player::getName).toList());
                for (Player p : present) {
                    String message =
                            missing.contains(p)
                                    ? text("approach-" + key(room))
                                    : text("wait-members", names);
                    p.sendActionBar(LegacyText.component(message));
                }
            }
            return;
        }
        Room previous = room.previous();
        for (Player p : present) {
            if (VerificationCourse.gateOccupied(
                    previous,
                    p.getBoundingBox()
                            .shift(-lane.origin.getX(), -lane.origin.getY(), -lane.origin.getZ())))
                return;
        }
        if (previous == Room.SLIDER) for (var station : lane.sliders) station.finishRetirement();
        gate(lane, previous, false);
        if (!lane.run.begin()) return;
        switch (room) {
            case WAREHOUSE -> {
                lane.warehouse.open();
                for (Player p : present) equipMiner(lane, p, lane.members.indexOf(p.getUniqueId()));
            }
            case SLIDER -> {
                assignSliders(lane, present);
                for (var station : lane.sliders)
                    station.open(
                            () -> liveSlider(lane),
                            p ->
                                    admitted(p)
                                            && inWorld(p)
                                            && lane.assignment.track(p.getUniqueId())
                                                    == station.hall
                                            && station.column(p) >= 0);
            }
            case JOINT -> lane.joint.open();
        }
    }

    private void equipMiner(Lane lane, Player p, int slot) {
        if (WarehouseRoom.miner(slot) && warehousePlayer(lane, p)) lane.warehouse.equip(p);
    }

    private void assignSliders(Lane lane, List<Player> present) {
        // Claim in roster order before announcing, so both halves of a pair see each other.
        for (Player p : present) {
            if (lane.assignment.track(p.getUniqueId()) == null) {
                var assigned = lane.assignment.claim(p.getUniqueId());
                lane.sliders.get(assigned.ordinal()).assign(p.getName());
                var partner = SliderAssignment.partner(assigned);
                UUID partnerId = lane.assignment.owner(partner);
                if (partnerId != null && announcedRooms.get(partnerId) == Room.SLIDER) {
                    Player teammate = Bukkit.getPlayer(partnerId);
                    if (teammate != null && admitted(teammate) && inWorld(teammate))
                        tell(
                                teammate,
                                "slider-partner",
                                partner.name(),
                                p.getName(),
                                assigned.name());
                }
            }
        }
        for (Player p : present) {
            if (announcedRooms.put(p.getUniqueId(), Room.SLIDER) == Room.SLIDER) continue;
            var hall = lane.assignment.track(p.getUniqueId());
            var station = lane.sliders.get(hall.ordinal());
            p.showTitle(
                    Title.title(
                            LegacyText.component(text("slider")),
                            LegacyText.component(
                                    text(
                                            "slider-assigned",
                                            station.color(),
                                            hall.name(),
                                            text("slider-place-" + key(hall))))));
            var partner = SliderAssignment.partner(hall);
            String name = lane.sliders.get(partner.ordinal()).ownerName();
            if (name == null) tell(p, "slider-no-partner", hall.name(), partner.name());
            else tell(p, "slider-partner", hall.name(), name, partner.name());
        }
    }

    private void sampleSlider(Lane lane, Player p, Vector position, int tick) {
        if (!liveSlider(lane)) return;
        var id = p.getUniqueId();
        if (announcedRooms.get(id) != Room.SLIDER) assignSliders(lane, presentMembers(lane));
        var assigned = lane.assignment.track(id);
        if (assigned == null) return;
        int x = position.getBlockX(), y = position.getBlockY(), z = position.getBlockZ();
        boolean done = lane.run.sliderDone(id);
        if (!done && tick % 20 < 2 && assigned.holdingColumn(x, y, z) < 0)
            p.sendActionBar(
                    LegacyText.component(
                            text(
                                    "slider-guide",
                                    assigned.name(),
                                    text("slider-place-" + key(assigned)),
                                    text("slider-line-" + key(assigned)))));
        var hall = SliderHall.station(x, y, z);
        if (hall != null)
            lane.sliders.get(hall.ordinal()).sample(p, tick, required(lane) == 1, done, assigned);
    }

    private void sampleEntry(Lane lane, int tick) {
        for (int slot = 0; slot < lane.members.size(); slot++) {
            Player player = Bukkit.getPlayer(lane.members.get(slot));
            if (player == null || !admitted(player) || !inWorld(player)) continue;
            Vector position = player.getLocation().toVector().subtract(lane.entryOrigin);
            int x = position.getBlockX(), y = position.getBlockY(), z = position.getBlockZ();
            EntryTrack track = EntryTrack.forSlot(slot);
            if (!track.contains(x, y, z)) {
                resetToCheckpoint(player, lane, slot);
            } else if (!lane.run.arrived(slot)) {
                int reached = track.checkpointAt(x, y, z);
                if (reached > 0 && lane.run.reachCheckpoint(slot, reached)) {
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_CHIME,
                            SoundCategory.PLAYERS,
                            0.8f,
                            1.0f + 0.15f * reached);
                    tell(player, "checkpoint");
                }
                if (track.atFinish(x, y, z) && lane.run.arrive(slot, tick)) {
                    player.playSound(
                            player.getLocation(),
                            Sound.ENTITY_PLAYER_LEVELUP,
                            SoundCategory.PLAYERS,
                            0.6f,
                            1.25f);
                    teamMessage(lane, text("arrival", player.getName(), lane.run.arrivalCount()));
                    if (lane.run.room() == Room.WAREHOUSE) {
                        opened(lane, Room.ENTRY);
                        return;
                    }
                }
            }
        }
    }

    private boolean warehousePlayer(Lane lane, Player p) {
        return liveLane(lane)
                && lane.run.room() == Room.WAREHOUSE
                && lane.run.begun()
                && admitted(p)
                && inWorld(p);
    }

    private Lane containerLane(Inventory inventory) {
        Location loc = inventory.getLocation();
        for (var lane : lanes) if (lane.warehouse.container(loc)) return lane;
        return null;
    }

    private boolean canUse(Lane lane, Player p, Location loc) {
        int slot = lane.members.indexOf(p.getUniqueId());
        return slot >= 0 && warehousePlayer(lane, p) && lane.warehouse.canOpen(p, slot, loc);
    }

    boolean openWarehouse(Player p, Block b) {
        Lane lane = lane(p.getUniqueId());
        return b != null
                && lane != null
                && lane.warehouse.container(b.getLocation())
                && canUse(lane, p, b.getLocation());
    }

    void inventoryOpen(InventoryOpenEvent e) {
        Lane lane = containerLane(e.getInventory());
        if (lane != null
                && e.getPlayer() instanceof Player p
                && !canUse(lane, p, e.getInventory().getLocation())) e.setCancelled(true);
    }

    void inventoryInteract(InventoryInteractEvent e) {
        Inventory inv = e.getInventory();
        Lane lane = containerLane(inv);
        if (lane == null || !(e.getWhoClicked() instanceof Player p)) return;
        if (inv.getHolder() instanceof Chest || !canUse(lane, p, inv.getLocation()))
            e.setCancelled(true);
        else lane.warehouse.inventoryChanged();
    }

    boolean canMine(Player p, Block b) {
        Lane lane = lane(p.getUniqueId());
        if (b == null
                || !warehousePlayer(lane, p)
                || b.getWorld() != p.getWorld()
                || p.getLocation().distanceSquared(b.getLocation().add(.5, .5, .5)) > 36)
            return false;
        int slot = lane.members.indexOf(p.getUniqueId());
        return WarehouseRoom.miner(slot)
                && lane.warehouse.inRole(p, slot)
                && lane.warehouse.mineable(b) != null;
    }

    void breakBlock(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (notAreaPlayer(p)) return;
        Block b = e.getBlock();
        DigAttempt attempt = digs.remove(p.getUniqueId());
        Lane lane = lane(p.getUniqueId());
        if (attempt != null
                && lane != null
                && attempt.rig() == lane.joint
                && attempt.epoch() == lane.joint.state.epoch()
                && attempt.x() == b.getX()
                && attempt.y() == b.getY()
                && attempt.z() == b.getZ()
                && canDigJoint(p, b)) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            return;
        }
        e.setCancelled(true);
        if (canMine(p, b)) lane(p.getUniqueId()).warehouse.mine(p, b.getType());
    }

    private boolean preparingJoint(Lane lane) {
        return lane.generation == captureGameGeneration()
                && lanes.contains(lane)
                && !isDisposed()
                && lane.run.room().ordinal() < Room.JOINT.ordinal()
                && getGameStageEnum() != GameStageEnum.WAITING
                && getGameStageEnum() != GameStageEnum.END;
    }

    private boolean liveLane(Lane lane) {
        return lane != null
                && running()
                && !expired(elapsed())
                && lane.generation == captureGameGeneration()
                && lanes.contains(lane);
    }

    private boolean liveJoint(Lane lane) {
        return liveLane(lane) && lane.run.room() == Room.JOINT && lane.run.begun();
    }

    private boolean jointPlayer(Lane lane, Player p) {
        return liveJoint(lane)
                && admitted(p)
                && inWorld(p)
                && lane.members.contains(p.getUniqueId());
    }

    boolean beginDigJoint(Player player, Block block) {
        digs.remove(player.getUniqueId());
        if (!canDigJoint(player, block)) return false;
        Lane lane = lane(player.getUniqueId());
        digs.put(
                player.getUniqueId(),
                new DigAttempt(
                        lane.joint,
                        lane.joint.state.epoch(),
                        block.getX(),
                        block.getY(),
                        block.getZ()));
        return true;
    }

    boolean canDigJoint(Player p, Block block) {
        Lane lane = lane(p.getUniqueId());
        return lane != null && jointPlayer(lane, p) && lane.joint.canDig(p, block);
    }

    boolean interactJoint(Player p, Block block, BlockFace face) {
        Lane lane = lane(p.getUniqueId());
        return lane != null
                && jointPlayer(lane, p)
                && (lane.joint.press(p, block, elapsed()) || lane.joint.fill(p, block, face));
    }

    private void ejectJoint(Lane lane) {
        if (!liveJoint(lane)) return;
        for (UUID id : lane.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && jointPlayer(lane, p)) ejectJointPlayer(lane, p);
        }
    }

    private void ejectJointPlayer(Lane lane, Player p) {
        Phase phase = lane.joint.state.phase();
        if (!jointPlayer(lane, p) || (phase != Phase.CARVING && phase != Phase.TESTING)) return;
        var box = p.getBoundingBox();
        Vector o = lane.origin;
        if (JointPlaza.intersectsSweep(
                box.getMinX() - o.getX(),
                box.getMinY() - o.getY(),
                box.getMinZ() - o.getZ(),
                box.getMaxX() - o.getX(),
                box.getMaxY() - o.getY(),
                box.getMaxZ() - o.getZ())) {
            teleportRelative(p, o, JointPlaza.ejectTarget(p.getX() - o.getX()));
            p.sendActionBar(LegacyText.component(text("joint-eject")));
        }
    }

    private void removeJointTools(Lane lane) {
        for (UUID id : lane.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) lane.joint.tools.remove(p);
        }
    }

    private void stripLane(Lane lane) {
        for (UUID id : lane.members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) RobotProps.strip(player);
        }
    }

    void worldChanged(Player player) {
        digs.remove(player.getUniqueId());
        Lane lane = lane(player.getUniqueId());
        if (inWorld(player)) {
            if (lane == null) RobotProps.strip(player);
            else lane.props.stripForeign(player);
        } else if (lane != null) {
            lane.joint.operatorLeft(player.getUniqueId());
            leaveStations(lane, player);
            lane.joint.tools.remove(player);
            lane.warehouse.clearPlayer(player);
        }
    }

    private void jointPassed(Lane lane, JointRig.Pass pass) {
        int total = jointTimes.size();
        String rank =
                total == 0
                        ? text("joint-first")
                        : text(
                                "joint-percent",
                                100L
                                        * jointTimes.stream()
                                                .filter(t -> t > pass.duration())
                                                .count()
                                        / total);
        jointTimes.add(pass.duration());
        teamMessage(
                lane,
                text(
                        "joint-result",
                        pass.player(),
                        pass.round(),
                        String.format(Locale.ROOT, "%.1f", pass.duration() / 20.0),
                        rank));
    }

    private boolean activeInWorld(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && admitted(p) && inWorld(p);
    }

    private void opened(Lane lane, Room cleared) {
        gate(lane, cleared, true);
        if (cleared == Room.SLIDER) retireSliderHall(lane);
        if (cleared == Room.JOINT) lane.joint.retire();
        if (cleared == Room.WAREHOUSE) {
            // Frames queue at the warehouse exit so the walls are ready when the team arrives.
            // Waiting for hall entry left them blank for 1.4-2.1 s in the 8-lane iso test.
            for (var station : lane.sliders)
                station.prebuild(() -> liveLane(lane) && lane.run.room() == Room.SLIDER);
        }
        for (UUID id : lane.members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && admitted(player) && inWorld(player))
                announceRoom(player, lane.run.room());
        }
    }

    private void announceRoom(Player player, Room room) {
        if (room != Room.ENTRY
                && room != Room.SLIDER
                && announcedRooms.put(player.getUniqueId(), room) != room)
            player.showTitle(Title.title(LegacyText.component(text(key(room))), Component.empty()));
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0);
    }

    private void teamMessage(Lane lane, String message) {
        for (UUID id : lane.members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && gamePlayers.contains(id)) p.sendMessage(LegacyText.component(message));
        }
    }

    void jointChunkUnloading(Chunk chunk) {
        if (!chunk.getWorld().getName().equals(getWorldName())) return;
        for (Lane lane : lanes) {
            if (!liveJoint(lane)) continue;
            int x = chunk.getX() - (lane.origin.getBlockX() >> 4);
            int z = chunk.getZ() - (lane.origin.getBlockZ() >> 4);
            if (JointPlaza.chunkInBounds(x, z)) lane.joint.suspend();
        }
    }

    void forgetMaps(Player viewer) {
        maps.forget(viewer);
        Lane lane = lane(viewer.getUniqueId());
        if (lane != null && !inWorld(viewer)) lane.joint.tools.remove(viewer);
    }

    /**
     * A cleared hall is never used again by this team: drop its map frames, pieces and labels now
     * instead of tracking and healing them until the match ends.
     */
    private void retireSliderHall(Lane lane) {
        lane.audience.retire();
        for (var station : lane.sliders) station.reset();
    }

    private void resetLaneBlocks(Lane lane) {
        retireSliderHall(lane);
        resetGates(lane);
    }

    private void resetGates(Lane lane) {
        startGate(lane, false);
        for (Room room : List.of(Room.ENTRY, Room.WAREHOUSE, Room.SLIDER, Room.JOINT))
            gate(lane, room, false);
    }

    private void startGate(Lane lane, boolean open) {
        World world = Bukkit.getWorld(getWorldName());
        if (world != null) EntryTrack.setStartGate(world, lane.entryOrigin, open);
    }

    private boolean liveSlider(Lane lane) {
        return liveLane(lane) && lane.run.room() == Room.SLIDER && lane.run.begun();
    }

    void releaseHandle(Player p) {
        var lane = lane(p.getUniqueId());
        if (!liveSlider(lane) || !admitted(p) || !inWorld(p)) return;
        for (var station : lane.sliders) {
            if (!p.getUniqueId().equals(station.state.holder())) continue;
            if (station.column(p) < 0) {
                station.leave(p.getUniqueId(), p);
                return;
            }
            int tick = elapsed();
            if (!station.submit(p, tick)) return;
            var opposite = SliderAssignment.partner(station.hall);
            var partner =
                    lane.assignment.owner(opposite) == null
                            ? null
                            : lane.sliders.get(opposite.ordinal());
            if (partner != null) confirmSlider(lane, partner, station, tick);
            confirmSlider(lane, station, partner, tick);
            return;
        }
    }

    private void confirmSlider(Lane lane, SliderStation station, SliderStation partner, int tick) {
        if (!station.confirm(partner, tick)) return;
        int duration = station.state.solveDuration();
        int total = sliderTimes.size();
        String rank =
                total == 0
                        ? text("slider-first")
                        : text(
                                "slider-percent",
                                100L
                                        * sliderTimes.stream().filter(t -> t > duration).count()
                                        / total);
        sliderTimes.add(duration);
        var owner = lane.assignment.owner(station.hall);
        int personal = lane.run.passSlider(owner);
        if (lane.run.sliderDone(owner)) station.updateNameplate(true);
        teamMessage(
                lane,
                text(
                        "slider-result",
                        station.ownerName(),
                        station.hall.name(),
                        station.state.round(),
                        String.format(Locale.ROOT, "%.1f", duration / 20.0),
                        rank,
                        personal));
    }

    private void leaveStations(Lane lane, Player p) {
        for (var station : lane.sliders) station.leave(p.getUniqueId(), p);
    }

    private void gate(Lane lane, Room room, boolean open) {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return;
        VerificationCourse.setGate(world, lane.origin, room, open);
    }

    public String roomName(UUID id) {
        var lane = lane(id);
        return lane == null ? "—" : text(lane.run.finished() ? "done" : key(lane.run.room()));
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static String text(String key, Object... args) {
        return MessageConfig.codeText(MESSAGE_PREFIX + key, args);
    }

    private static void tell(Player p, String key, Object... args) {
        p.sendMessage(LegacyText.component(text(key, args)));
    }

    private void restoreEntryPosition(Player player, Lane lane, int slot) {
        EntryTrack track = EntryTrack.forSlot(slot);
        teleportRelative(
                player,
                lane.entryOrigin,
                lane.run.arrived(slot)
                        ? track.finishSpawn()
                        : track.checkpointSpawn(lane.run.checkpoint(slot)));
    }

    private void resetToCheckpoint(Player player, Lane lane, int slot) {
        restoreEntryPosition(player, lane, slot);
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS,
                SoundCategory.PLAYERS,
                0.55f,
                0.9f);
    }

    private void restoreLane(Player p) {
        var lane = lane(p.getUniqueId());
        if (lane == null || !admitted(p)) return;
        leaveStations(lane, p);
        lane.joint.tools.remove(p);
        p.setGameMode(GameMode.ADVENTURE);
        int slot = lane.members.indexOf(p.getUniqueId());
        Room room = lane.run.room();
        if (room == Room.ENTRY) {
            restoreEntryPosition(p, lane, slot);
        } else {
            if (room != Room.WAREHOUSE) lane.warehouse.clearPlayer(p);
            Vector spawn =
                    lane.run.finished()
                            ? new Vector(7.5 + slot, 1, VerificationCourse.FINISH + 3.5)
                            : room.restoreSpawn(slot);
            teleportRelative(p, lane.origin, spawn);
            if (room == Room.WAREHOUSE) {
                lane.run.enterPuzzleRooms(slot);
                equipMiner(lane, p, slot);
            } else if (jointPlayer(lane, p)) lane.joint.tools.give(p);
        }
        if (lane.run.finished()) tell(p, "done");
        else if (room == Room.ENTRY && lane.run.arrived(slot)) tell(p, "wait-entry");
        else announceRoom(p, room);
    }

    private void teleportRelative(Player player, Vector origin, Vector offset) {
        player.teleport(origin.clone().add(offset).toLocation(Bukkit.getWorld(getWorldName())));
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
    }

    @Override
    public void handlePlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Lane lane = lane(p.getUniqueId());
        if (lane == null) RobotProps.strip(p);
        else lane.props.stripForeign(p);
        if (restorePostGameParticipant(p)) return;
        if (isManagedSpectator(p)) {
            handleSpectatorJoin(event);
            return;
        }
        if (restoreSharedPreGameParticipant(p)) return;
        scheduleGameplayAction(p, () -> restoreLane(p), 1L);
    }

    @Override
    public void handlePlayerQuit(PlayerQuitEvent event) {
        var p = event.getPlayer();
        var lane = lane(p.getUniqueId());
        var current = Bukkit.getPlayer(p.getUniqueId());
        if (current != null && current != p) return;
        announcedRooms.remove(p.getUniqueId(), Room.SLIDER);
        digs.remove(p.getUniqueId());
        if (lane != null) {
            leaveStations(lane, p);
            lane.joint.tools.remove(p);
            lane.warehouse.clearPlayer(p);
            lane.joint.operatorLeft(p.getUniqueId());
            if (lane.run.room() == Room.JOINT
                    && lane.members.stream()
                            .filter(id -> !id.equals(p.getUniqueId()))
                            .noneMatch(this::activeInWorld)) {
                lane.joint.suspend();
                removeJointTools(lane);
            }
        }
        removePlayerFromBossBars(p);
    }

    @Override
    public void handlePlayerDeath(PlayerDeathEvent event) {
        var p = event.getEntity();
        event.getDrops().removeIf(RobotProps::prop);
        if (!isGameplayParticipant(p)) return;
        var lane = lane(p.getUniqueId());
        if (Bukkit.getPlayer(p.getUniqueId()) != p) return;
        digs.remove(p.getUniqueId());
        if (lane != null) {
            lane.joint.operatorLeft(p.getUniqueId());
            lane.props.died(p, event.getDrops(), event.getKeepInventory());
            leaveStations(lane, p);
        }
        scheduleParticipantRespawn(
                p,
                () -> {
                    if (isGameplayParticipant(p) && !restoreSharedPreGameParticipant(p))
                        restoreLane(p);
                });
    }

    @Override
    public void endGame() {
        digs.clear();
        if (getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END)
            return;
        stopClock();
        maps.release(edition, editionWorld);
        editionWorld = null;
        edition = null;
        setGameStageEnum(GameStageEnum.END);
        for (var lane : lanes) {
            retireSliderHall(lane);
            lane.joint.interrupt();
            removeJointTools(lane);
            lane.warehouse.finish(lane.members);
            stripLane(lane);
        }
        if (isSettlementAllowed()) {
            var field = lanes.stream().map(Lane::run).toList();
            for (var lane : lanes) {
                int points = lane.run.points(field);
                if (points > 0)
                    for (var id : lane.members)
                        addPlayerPoints(id, (double) points / lane.members.size());
                teamMessage(lane, text("result", points));
            }
            addPlayerPointsToDatabase();
        }
        announceGameEnd(text("end"), text("end-hint"));
        sendMessageToAllGamePlayers(getTeamPointsRank());
        beginPostGameSettlement();
        publishGameEndEvent(new SingleGameEndEvent(this, gameTeams));
        finishPostGameAfterEndEvent();
    }

    private void stopClock() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
    }

    @Override
    public void resetArea() {
        digs.clear();
        stopClock();
        maps.release(edition, editionWorld);
        editionWorld = null;
        edition = null;
        for (var lane : lanes) {
            lane.joint.interrupt();
            removeJointTools(lane);
            lane.warehouse.reset(lane.members);
            stripLane(lane);
            resetLaneBlocks(lane);
        }
        lanes.clear();
        sliderTimes.clear();
        jointTimes.clear();
        announcedRooms.clear();
        remaining = 0;
    }

    @Override
    public void dispose() {
        resetArea();
        super.dispose();
    }
}
