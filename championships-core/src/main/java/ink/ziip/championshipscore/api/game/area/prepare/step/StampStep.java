package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Stamps {@code count} copies into the editable world. This deliberately does not snapshot/reload the
 * world: publish performs that expensive operation once, after every geometry step validates.
 */
public class StampStep extends PrepareStep {

    private final Function<ChampionshipsCore, File> fileResolver;
    private final BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver;
    private final BiConsumer<SetupTarget, Integer> copyCountWriter;
    private final BiConsumer<SetupTarget, Vector> sizeWriter;
    private final BiConsumer<PrepareSession, World> preStampCleaner;
    private final int maxCount;
    private final boolean keepSourceCopy;

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid,
                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter) {
        this(fileResolver, (target, size) -> grid, copyCountWriter, null, null, Integer.MAX_VALUE, true);
    }

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid,
                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter, int maxCount) {
        this(fileResolver, (target, size) -> grid, copyCountWriter, null, null, maxCount, true);
    }

    private StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver,
                      @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                      @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                      BiConsumer<SetupTarget, Vector> sizeWriter,
                      BiConsumer<PrepareSession, World> preStampCleaner,
                      int maxCount, boolean ignored) {
        this(fileResolver, gridResolver, copyCountWriter, sizeWriter, preStampCleaner, maxCount, false, ignored);
    }

    private StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver,
                      @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                      @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                      BiConsumer<SetupTarget, Vector> sizeWriter,
                      BiConsumer<PrepareSession, World> preStampCleaner,
                      int maxCount, boolean keepSourceCopy, boolean ignored) {
        super("stamp",
                Component.text(GuiConfig.text("map-editor.steps.arena-generation.stamp-and-generate-multiple-maps")),
                Component.text(GuiConfig.text("map-editor.steps.arena-generation.after-entering-the-number-of-copies-paste-n-copies-of-the-site-and-solidify-them-as-templates")),
                Material.DISPENSER,
                StepCaptureType.STAMP);
        this.fileResolver = fileResolver;
        this.gridResolver = gridResolver;
        this.copyCountWriter = copyCountWriter;
        this.sizeWriter = sizeWriter;
        this.preStampCleaner = preStampCleaner;
        this.maxCount = maxCount;
        this.keepSourceCopy = keepSourceCopy;
    }

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid,
                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                     @NotNull BiConsumer<SetupTarget, Vector> sizeWriter) {
        this(fileResolver, (target, size) -> grid, copyCountWriter, sizeWriter, null, Integer.MAX_VALUE, true);
    }

    public StampStep(@NotNull Function<ChampionshipsCore, File> fileResolver, @NotNull ArenaGrid grid,
                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                     @NotNull BiConsumer<SetupTarget, Vector> sizeWriter, int maxCount) {
        this(fileResolver, (target, size) -> grid, copyCountWriter, sizeWriter, null, maxCount, true);
    }

    public static StampStep adaptive(@NotNull Function<ChampionshipsCore, File> fileResolver,
                                     @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter) {
        return new StampStep(fileResolver, gridResolver, copyCountWriter, null, null,
                Integer.MAX_VALUE, true);
    }

    public static StampStep adaptiveKeepingSource(@NotNull Function<ChampionshipsCore, File> fileResolver,
                                                   @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                                                   @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter) {
        return new StampStep(fileResolver, gridResolver, copyCountWriter, null, null,
                Integer.MAX_VALUE, true, true);
    }

    public static StampStep adaptiveKeepingSource(@NotNull Function<ChampionshipsCore, File> fileResolver,
                                                   @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                                                   @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                                                   @NotNull BiConsumer<PrepareSession, World> preStampCleaner) {
        return new StampStep(fileResolver, gridResolver, copyCountWriter, null, preStampCleaner,
                Integer.MAX_VALUE, true, true);
    }

    public static StampStep adaptive(@NotNull Function<ChampionshipsCore, File> fileResolver,
                                     @NotNull BiFunction<SetupTarget, Vector, ArenaGrid> gridResolver,
                                     @NotNull BiConsumer<SetupTarget, Integer> copyCountWriter,
                                     @NotNull BiConsumer<PrepareSession, World> preStampCleaner) {
        return new StampStep(fileResolver, gridResolver, copyCountWriter, null, preStampCleaner,
                Integer.MAX_VALUE, true);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override
    public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.the-number-of-venue-shares-must-be-greater-than-0"));
        }
        if (count > maxCount) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.the-map-can-only-generate-at-most") + maxCount + GuiConfig.text("map-editor.steps.arena-generation.venue"));
        }
        File file = fileResolver.apply(session.getPlugin());
        if (!file.isFile()) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.missing-venue-template-please-complete-first-save-venue-template"));
        }
        String worldName = session.getTarget().worldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.world") + worldName + GuiConfig.text("map-editor.steps.arena-generation.not-loaded-yet"));
        }
        if (!session.getTarget().canSaveMap()) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.there-is-still-a-game-instance-running-on-the-same-map-cannot-be-regenerated-or-saved"));
        }
        ArenaGrid resolvedGrid;
        try {
            Vector size = session.getPlugin().getWorldEditManager().getSchematicDimensions(file);
            if (preStampCleaner != null) preStampCleaner.accept(session, world);
            resolvedGrid = gridResolver.apply(session.getTarget(), size);
            if (keepSourceCopy)
                ArenaPreparer.stampAdditionalCopies(session.getPlugin(), world, file, resolvedGrid, count);
            else
                ArenaPreparer.stampCopies(session.getPlugin(), world, file, resolvedGrid, count);
            if (sizeWriter != null) sizeWriter.accept(session.getTarget(), size);
        } catch (Exception e) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.arena-generation.failed-to-generate-venue") + e.getMessage());
        }

        copyCountWriter.accept(session.getTarget(), count);
        session.getTarget().config().markPrepareWorldBuilt();
        Location dest = resolvedGrid.origin(0).toLocation(world);
        Bukkit.getScheduler().runTask(session.getPlugin(), () -> player.teleport(dest));
        session.setWorldConfirmed(true);
        session.setStamped(true);
        return Utils.formatAdminSuccess(keepSourceCopy
                ? GuiConfig.text("map-editor.steps.arena-generation.the-total-number-of-venues-has-been-set-to") + count + GuiConfig.text("map-editor.steps.arena-generation.the-original-image-number-0-was-retained-and-generated")
                    + Math.max(0, count - 1) + GuiConfig.text("map-editor.steps.arena-generation.copies")
                : GuiConfig.text("map-editor.steps.arena-generation.generated") + count + GuiConfig.text("map-editor.steps.arena-generation.site-please-verify-and-publish-after-completing-the-location"));
    }
}
