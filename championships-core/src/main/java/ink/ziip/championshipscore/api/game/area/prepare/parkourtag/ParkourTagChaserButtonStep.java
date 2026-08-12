package ink.ziip.championshipscore.api.game.area.prepare.parkourtag;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Captures a wall button and creates its glowing instruction sign in every stamped PKT copy. */
final class ParkourTagChaserButtonStep extends PrepareStep {
    private static final int TARGET_DISTANCE = 8;

    private final Function<SetupTarget, Location> getter;
    private final BiConsumer<SetupTarget, Location> setter;

    ParkourTagChaserButtonStep(@NotNull String key, @NotNull Component name,
                               @NotNull Component description,
                               @NotNull Function<SetupTarget, Location> getter,
                               @NotNull BiConsumer<SetupTarget, Location> setter) {
        super(key, name, description, Material.POLISHED_BLACKSTONE_BUTTON, StepCaptureType.STAND_AND_RUN);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        Location configured = getter.apply(session.getTarget());
        if (configured == null || configured.getWorld() == null) return false;
        ParkourTagConfig config = (ParkourTagConfig) session.getTarget().config();
        return resolveCopies(config, configured).stream().allMatch(ParkourTagChaserButtonStep::isCompleteButton);
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        Block source = player.getTargetBlockExact(TARGET_DISTANCE);
        if (source == null || !isWallButton(source)) {
            return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-001"));
        }

        ParkourTagConfig config = (ParkourTagConfig) session.getTarget().config();
        Location sourceLocation = source.getLocation();
        List<Block> buttons = resolveCopies(config, sourceLocation);
        if (buttons.size() != Math.max(1, config.getCopyCount())) {
            return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-002"));
        }
        for (int index = 0; index < buttons.size(); index++) {
            Block button = buttons.get(index);
            if (index > 0 && !canReplaceWithButton(button)) {
                return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-003") + index
                        + GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-004"));
            }
            BlockFace facing = ((Switch) source.getBlockData()).getFacing();
            if (!button.getRelative(facing.getOppositeFace()).getType().isSolid()) {
                return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-003") + index
                        + GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-005"));
            }
            Block label = button.getRelative(BlockFace.DOWN);
            if (!label.getType().isAir() && label.getType() != Material.BIRCH_WALL_SIGN) {
                return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-003") + index
                        + GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-006"));
            }
            if (!label.getRelative(facing.getOppositeFace()).getType().isSolid()) {
                return Utils.formatAdminError(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-003") + index
                        + GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-007"));
            }
        }

        for (int index = 0; index < buttons.size(); index++) {
            Block button = buttons.get(index);
            if (index > 0) button.setBlockData(source.getBlockData().clone(), false);
            createLabel(button);
        }
        setter.accept(session.getTarget(), sourceLocation);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-008") + buttons.size()
                + GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-009"));
    }

    private static @NotNull List<Block> resolveCopies(@NotNull ParkourTagConfig config,
                                                       @NotNull Location source) {
        List<Block> blocks = new ArrayList<>();
        World world = source.getWorld();
        if (world == null) world = Bukkit.getWorld(config.getWorldName());
        if (world == null) return blocks;
        int count = Math.max(1, config.getCopyCount());
        for (int index = 0; index < count; index++) {
            Location transformed = config.getCopyGrid().transform(index).apply(source);
            if (transformed != null) {
                transformed.setWorld(world);
                blocks.add(transformed.getBlock());
            }
        }
        return blocks;
    }

    private static boolean isCompleteButton(@NotNull Block button) {
        if (!isWallButton(button)) return false;
        Block label = button.getRelative(BlockFace.DOWN);
        if (label.getType() != Material.BIRCH_WALL_SIGN
                || !(label.getBlockData() instanceof WallSign wallSign)) return false;
        if (wallSign.getFacing() != ((Switch) button.getBlockData()).getFacing()) return false;
        return label.getState() instanceof Sign sign && sign.getSide(Side.FRONT).isGlowingText();
    }

    private static boolean isWallButton(@NotNull Block block) {
        return Tag.BUTTONS.isTagged(block.getType())
                && block.getBlockData() instanceof Switch button
                && button.getAttachedFace() == FaceAttachable.AttachedFace.WALL;
    }

    private static boolean canReplaceWithButton(@NotNull Block block) {
        return block.getType().isAir() || Tag.BUTTONS.isTagged(block.getType())
                || block.getType().name().endsWith("_WALL_SIGN");
    }

    private static void createLabel(@NotNull Block button) {
        Switch buttonData = (Switch) button.getBlockData();
        Block label = button.getRelative(BlockFace.DOWN);
        label.setType(Material.BIRCH_WALL_SIGN, false);
        WallSign signData = (WallSign) label.getBlockData();
        signData.setFacing(buttonData.getFacing());
        label.setBlockData(signData, false);

        Sign sign = (Sign) label.getState();
        var front = sign.getSide(Side.FRONT);
        front.line(0, Component.empty());
        front.line(1, Component.text(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-010"), NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD));
        front.line(2, Component.text(GuiConfig.text("prepare-parkourtag-parkourtagchaserbuttonstep.text-011"), NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD));
        front.line(3, Component.empty());
        front.setColor(DyeColor.PURPLE);
        front.setGlowingText(true);
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
