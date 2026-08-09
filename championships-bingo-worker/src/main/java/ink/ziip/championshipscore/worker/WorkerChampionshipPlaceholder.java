package ink.ziip.championshipscore.worker;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Worker-side subset of Core's {@code %cc_*%} expansion. */
final class WorkerChampionshipPlaceholder extends PlaceholderExpansion {
    private final Plugin plugin;
    private final WorkerMatchRegistry registry;

    WorkerChampionshipPlaceholder(Plugin plugin, WorkerMatchRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Eter Lu";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;
        return registry.resolveChampionshipPlaceholder(player.getUniqueId(), params);
    }
}
