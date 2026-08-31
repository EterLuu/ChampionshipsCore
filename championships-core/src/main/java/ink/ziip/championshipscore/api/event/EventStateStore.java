package ink.ziip.championshipscore.api.event;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class EventStateStore {
    public record ActiveEvent(@NotNull String id, @NotNull String slug,
                              @NotNull String title, boolean archived,
                              @NotNull List<EventGame> games,
                              @NotNull List<Double> roundMultipliers) {
        public boolean allows(@NotNull GameTypeEnum game) {
            return games.stream().anyMatch(configured -> configured.type() == game);
        }

    }

    public record EventGame(@NotNull GameTypeEnum type, @NotNull String variantKey, @NotNull String label) {
    }

    private final ChampionshipsCore plugin;
    private final File file;

    public EventStateStore(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "active-event.yml");
    }

    public synchronized void save(@NotNull EventTeamImport.Event event) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("event.id", event.id());
        yaml.set("event.slug", event.slug());
        yaml.set("event.title", event.title());
        yaml.set("event.archived", false);
        List<Map<String, Object>> games = new ArrayList<>();
        for (EventTeamImport.Game game : event.games()) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("key", game.key());
            stored.put("variant-key", game.variantKey());
            stored.put("label", game.label());
            games.add(stored);
        }
        yaml.set("event.games", games);
        yaml.set("event.round-multipliers", event.roundMultipliers());
        yaml.save(file);
    }

    public synchronized @Nullable ActiveEvent load() {
        if (!file.isFile()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("event.id");
        String slug = yaml.getString("event.slug");
        String title = yaml.getString("event.title");
        if (id == null || slug == null || title == null || slug.isBlank()) return null;
        List<EventGame> games = new ArrayList<>();
        for (Map<?, ?> stored : yaml.getMapList("event.games")) {
            Object rawKey = stored.get("key");
            Object rawLabel = stored.get("label");
            Object rawVariant = stored.get("variant-key");
            if (!(rawKey instanceof String key) || !(rawLabel instanceof String label)
                    || !(rawVariant instanceof String variantKey)) return null;
            GameTypeEnum type = GameTypeEnum.fromCommand(key);
            if (type == null || variantKey.isBlank()) return null;
            games.add(new EventGame(type, variantKey, label));
        }
        List<Double> roundMultipliers = yaml.getDoubleList("event.round-multipliers");
        if (games.isEmpty() || roundMultipliers.size() < games.size()
                || roundMultipliers.stream().anyMatch(value -> !Double.isFinite(value) || value < 0D || value > 100D))
            return null;
        return new ActiveEvent(id, slug, title, yaml.getBoolean("event.archived", false),
                List.copyOf(games), List.copyOf(roundMultipliers));
    }

    public synchronized boolean markArchived() {
        ActiveEvent active = load();
        if (active == null) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("event.archived", true);
        try {
            yaml.save(file);
            return true;
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Unable to persist archived event state", failure);
            return false;
        }
    }
}
