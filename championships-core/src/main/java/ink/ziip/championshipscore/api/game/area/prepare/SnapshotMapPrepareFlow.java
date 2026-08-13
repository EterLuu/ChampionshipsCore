package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Common prepare behavior for one independently editable map world. The world itself is the source of
 * truth while editing; publish snapshots it into the map store. These maps must not expose schematic
 * capture or copy-stamping steps, which are only meaningful for replicated arena layouts.
 */
public abstract class SnapshotMapPrepareFlow extends PrepareFlowDefinition {
    private final World.Environment environment;

    protected SnapshotMapPrepareFlow(@NotNull World.Environment environment) {
        this.environment = environment;
    }

    @Override
    public @NotNull String worldName(@NotNull SetupTarget target) {
        return target.worldName();
    }

    @Override
    public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }

    @Override
    public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        Location spawn = GameSpawnResolver.resolve(target.config());
        if (spawn != null) return spawn;
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }

    @Override
    public @NotNull String editorLocationName(@NotNull SetupTarget target) {
        return GuiConfig.text("map-editor.common.snapshot.edit-venue");
    }

    @Override
    public @NotNull CompletableFuture<Boolean> publish(@NotNull PrepareSession session) {
        return session.getTarget().saveMap(environment);
    }
}
