package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareModeInventory;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartBlueprintGui;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartCopperPolicy;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartMaterialManifest;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprintAuditor;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprintReview;
import ink.ziip.championshipscore.api.game.buildmart.reference.ReferenceBuilder;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** A temporary 7x7x7 workshop owned by one prepare session; restoring it never changes map geometry. */
public final class BuildMartBlueprintWorkshop {
    private static final Object WRITE_LOCK = new Object();
    private final PrepareSessionManager manager;
    private final PrepareSession session;
    private final Player editor;
    private final UUID owner;
    private final String id;
    private final BuildMartBlueprint previous;
    private final Path file;
    private final Location floor;
    private final Location button;
    private final Location hub;
    private final List<BlockState> original = new ArrayList<>(343);
    private byte[] baseline;
    private volatile boolean closed;
    private boolean busy = true;

    private BuildMartBlueprintWorkshop(PrepareSessionManager manager, PrepareSession session, Player player,
                                      String id, BuildMartBlueprint previous, Location floor, Location button, Location hub) {
        this.manager = manager;
        this.session = session;
        this.editor = player;
        this.owner = player.getUniqueId();
        this.id = id;
        this.previous = previous;
        this.floor = floor.clone();
        this.button = button.clone();
        this.hub = hub.clone();
        this.file = session.getPlugin().getDataFolder().toPath().resolve("buildmart/blueprints").resolve(id + ".yml");
    }

    public String name() { return previous == null ? id : previous.getDisplayName(); }
    public boolean isBusy() { return busy; }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 64 && name.equals(name.strip())
                && !name.startsWith(".") && name.chars().noneMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\');
    }

    public static void start(PrepareSessionManager manager, Player player, PrepareSession session,
                             String id, BuildMartBlueprint previous) {
        if (!player.hasPermission(ChampionshipPermissions.ADMIN) || manager.getSession(player) != session
                || !(session.getTarget().config() instanceof BuildMartConfig config)) return;
        if (!validName(id)) { Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_INVALID_NAME); return; }
        if (session.getBlueprintWorkshop() != null) { Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_FINISH_FIRST); return; }
        if (!session.getTarget().canSaveMap()) { Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_BUILD_INSTANCE_RUNNING); return; }
        World world = Bukkit.getWorld(session.getTarget().worldName());
        var base = config.getBaseTemplate();
        if (world == null || base == null || !config.hasBaseLocation("normal-plot-1")
                || base.getNormalBuildAnchors().isEmpty() || base.getNormalSubmitAnchors().getFirst() == null
                || config.getHubPortalPoint() == null) {
            Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_SETUP_MISSING); return;
        }
        Location floor = bind(base.getNormalBuildAnchors().getFirst(), world);
        Location button = bind(base.getNormalSubmitAnchors().getFirst(), world);
        Location hub = bind(config.getHubPortalPoint(), world);
        if (!world.equals(floor.getWorld()) || !world.equals(button.getWorld()) || !world.equals(hub.getWorld())) {
            Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_SETUP_MISSING); return;
        }
        var workshop = new BuildMartBlueprintWorkshop(manager, session, player, id, previous, floor, button, hub);
        session.setBlueprintWorkshop(workshop);
        player.closeInventory();
        workshop.async(() -> {
            try {
                workshop.baseline = Files.exists(workshop.file) ? Files.readAllBytes(workshop.file) : null;
                if ((previous == null) != (workshop.baseline == null)) throw new IOException(MessageConfig.BUILD_MART_EDITOR_CONFLICT);
                workshop.main(() -> workshop.place(player));
            } catch (Exception error) {
                workshop.main(() -> { if (!workshop.active()) return; workshop.close(); Utils.sendAdminError(player,
                        MessageConfig.BUILD_MART_BLUEPRINT_SAVE_FAILED.replace("%detail%", detail(error))); });
            }
        });
    }

    private static Location bind(Location value, World world) {
        Location result = value.clone(); if (result.getWorld() == null) result.setWorld(world); return result;
    }

    private boolean active() {
        return !closed && editor.isOnline() && manager.getSession(editor) == session && session.getBlueprintWorkshop() == this;
    }

    private void place(Player player) {
        if (!active()) return;
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++)
            original.add(block(x, y, z).getState());
        try {
            if (previous == null) ReferenceBuilder.clearBuildArea(floor);
            else ReferenceBuilder.paste(previous, floor);
            if (!player.teleport(floor.clone().add(3.5, 8, 3.5))) throw new IllegalStateException(MessageConfig.MAP_EDITOR_SESSION_TELEPORT_FAILED);
            player.setFlying(true);
            busy = false;
            PrepareModeInventory.refresh(player, session);
            Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_STARTED.replace("%name%", name()));
        } catch (Exception error) {
            close();
            Utils.sendAdminError(player, MessageConfig.BUILD_MART_BLUEPRINT_SAVE_FAILED.replace("%detail%", detail(error)));
        }
    }

    private Block block(int x, int y, int z) {
        return floor.getWorld().getBlockAt(floor.getBlockX() + x, floor.getBlockY() + 1 + y, floor.getBlockZ() + z);
    }

    public boolean contains(Location location) { return ReferenceBuilder.isBuildAreaBlock(floor,
            location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    public boolean isButton(Location location) {
        return button.getWorld().equals(location.getWorld()) && button.getBlockX() == location.getBlockX()
                && button.getBlockY() == location.getBlockY() && button.getBlockZ() == location.getBlockZ();
    }

    public void handleAction(Player player, String action) {
        if (!active() || !owner.equals(player.getUniqueId()) || !player.hasPermission(ChampionshipPermissions.ADMIN)) return;
        if (busy) { Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_BUSY); return; }
        switch (action) {
            case "save-draft" -> submit(player);
            case "exit" -> cancel(player);
            case "teleport" -> player.teleport(floor.clone().add(3.5, 8, 3.5));
            case "steps" -> BuildMartBlueprintGui.open(manager, player, session, 0);
            default -> Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_FINISH_FIRST);
        }
    }

    public void submit(Player player) {
        if (!active() || !owner.equals(player.getUniqueId()) || !player.hasPermission(ChampionshipPermissions.ADMIN)) return;
        if (busy) { Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_BUSY); return; }
        if (!session.getTarget().canSaveMap()) { Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_BUILD_INSTANCE_RUNNING); return; }
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int x = 0; x < 7; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            Block block = block(x, y, z);
            if (block.getType().isAir()) continue;
            if (!block.getBlockData().isSupported(block.getLocation())) {
                Utils.sendAdminError(player, MessageConfig.BUILD_MART_BLUEPRINT_SAVE_FAILED.replace("%detail%",
                        MessageConfig.BUILD_MART_EDITOR_UNSUPPORTED.replace("%position%", "(" + x + "," + y + "," + z + ")")));
                return;
            }
            blocks.add(new BlueprintBlock(x, y, z, BuildMartCopperPolicy.normalizeBlueprint(block.getBlockData()).clone()));
        }
        if (blocks.isEmpty()) { Utils.sendAdminError(player, MessageConfig.BUILD_MART_BLUEPRINT_EMPTY_SELECTION); return; }
        Map<BuildMartBlueprintReview.Position, BlockData> floorStates = new HashMap<>();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++)
            floorStates.put(new BuildMartBlueprintReview.Position(x, -1, z), block(x, -1, z).getBlockData());
        List<BuildMartBlueprintReview.Position> invisible = BuildMartBlueprintReview.invisible(blocks, floorStates::get);
        BuildMartBlueprint candidate = new BuildMartBlueprint(id, name(), previous == null ? 1 : previous.getStars(), blocks);
        busy = true;
        Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_REVIEWING);
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        async(() -> {
            try {
                var inventory = BuildMartMaterialManifest.readSubmissionInventory(config);
                main(() -> review(player, candidate, inventory, invisible));
            } catch (Exception error) { main(() -> reject(player, detail(error))); }
        });
    }

    private void review(Player player, BuildMartBlueprint candidate, BuildMartMaterialManifest.AuditInventory inventory,
                        List<BuildMartBlueprintReview.Position> invisible) {
        if (!active()) return;
        if (!inventory.available() || inventory.materials().isEmpty()) {
            busy = false; Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_MANIFEST_MISSING); return;
        }
        var audit = BuildMartBlueprintAuditor.audit(candidate, inventory);
        if (!audit.fullyCovered()) {
            busy = false;
            Utils.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_MATERIALS_REJECTED.replace("%materials%",
                    audit.uncoveredMaterials().stream().map(m -> m.getKey().toString()).sorted().collect(Collectors.joining(", "))));
            return;
        }
        BuildMartBlueprint saved = previous == null
                ? new BuildMartBlueprint(id, id, audit.suggestedStars(), candidate.getBlocks()) : candidate;
        List<String> serialized = saved.getBlocks().stream().map(BlueprintBlock::serialize).toList();
        async(() -> {
            try {
                save(saved.getDisplayName(), saved.getStars(), serialized);
                main(() -> {
                    session.getPlugin().getGameManager().getBuildMartManager().updateBlueprint(saved);
                    if (!active()) return;
                    if (!invisible.isEmpty()) Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_INVISIBLE_WARNING
                            .replace("%count%", String.valueOf(invisible.size())).replace("%positions%",
                                    invisible.stream().limit(8).map(Object::toString).collect(Collectors.joining(", "))));
                    close();
                    returnToHub(player);
                    Utils.sendAdminSuccess(player, MessageConfig.BUILD_MART_EDITOR_SAVED.replace("%name%", saved.getDisplayName())
                            .replace("%blocks%", String.valueOf(saved.blockCount())).replace("%stars%", String.valueOf(saved.getStars())));
                });
            } catch (Exception error) { main(() -> reject(player, detail(error))); }
        });
    }

    private void save(String name, int stars, List<String> blocks) throws Exception {
        save(file, baseline, name, stars, blocks, () -> closed);
    }

    static void save(Path file, byte[] baseline, String name, int stars, List<String> blocks,
                     BooleanSupplier cancelled) throws Exception {
        synchronized (WRITE_LOCK) {
            if (cancelled.getAsBoolean()) throw new IOException(MessageConfig.BUILD_MART_EDITOR_CANCELLED);
            byte[] current = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (!Arrays.equals(current, baseline)) throw new IOException(MessageConfig.BUILD_MART_EDITOR_CONFLICT);
            YamlConfiguration yaml = new YamlConfiguration();
            if (baseline != null) yaml.loadFromString(new String(baseline, StandardCharsets.UTF_8));
            yaml.set("name", name); yaml.set("stars", stars); yaml.set("blocks", blocks);
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "blueprint-", ".tmp");
            try {
                Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
                if (cancelled.getAsBoolean()) throw new IOException(MessageConfig.BUILD_MART_EDITOR_CANCELLED);
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    private void reject(Player player, String reason) {
        if (!active()) return;
        busy = false;
        Utils.sendAdminError(player, MessageConfig.BUILD_MART_BLUEPRINT_SAVE_FAILED.replace("%detail%", reason));
    }

    public void cancel(Player player) {
        if (!active() || !owner.equals(player.getUniqueId()) || !player.hasPermission(ChampionshipPermissions.ADMIN)) return;
        if (busy) { Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_BUSY); return; }
        close(); returnToHub(player); Utils.sendAdminInfo(player, MessageConfig.BUILD_MART_EDITOR_CANCELLED);
    }

    /** Called on submit, cancel, prepare exit, quit, and plugin disable; restores even tile snapshots. */
    public void close() {
        if (closed) return;
        closed = true;
        if (session.getBlueprintWorkshop() == this) session.setBlueprintWorkshop(null);
        for (BlockState state : original) state.getBlock().setBlockData(state.getBlockData(), false);
        for (BlockState state : original) state.update(true, false);
        original.clear();
    }

    private void returnToHub(Player player) {
        if (!player.teleport(hub)) Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_SESSION_TELEPORT_FAILED);
        PrepareModeInventory.refresh(player, session);
    }

    private void async(Runnable action) { Bukkit.getScheduler().runTaskAsynchronously(session.getPlugin(), action); }
    private void main(Runnable action) {
        if (session.getPlugin().isEnabled()) Bukkit.getScheduler().runTask(session.getPlugin(), action);
    }
    private static String detail(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
