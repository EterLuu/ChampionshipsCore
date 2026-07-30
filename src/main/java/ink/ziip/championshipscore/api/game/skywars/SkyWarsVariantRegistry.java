package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.model.GameLifecycleSettings;
import ink.ziip.championshipscore.api.game.config.model.GamePresentationSettings;
import ink.ziip.championshipscore.api.game.config.model.GameVariantRegistry;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Loads reusable SkyWars rules profiles from {@code skywars/variants/*.yml}. */
public final class SkyWarsVariantRegistry implements GameVariantRegistry<SkyWarsConfig, SkyWarsVariant> {
    private static final String INLINE = "inline";
    private final ChampionshipsCore plugin;
    private final Map<String, SkyWarsVariant> variants = new HashMap<>();

    public SkyWarsVariantRegistry(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        variants.clear();
        Path directory = plugin.getFolder().resolve("skywars").resolve("variants");
        try {
            Files.createDirectories(directory);
            Path bundledDefault = directory.resolve("default.yml");
            if (Files.notExists(bundledDefault)) {
                try (InputStream input = plugin.getResource("skywars/variants/default.yml")) {
                    if (input != null) Files.copy(input, bundledDefault, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (var files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".yml"))
                        .sorted().forEach(this::loadVariant);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    Utils.formatModuleLog("SkyWarsVariant", "加载", "无法加载变体配置"), exception);
        }
    }

    @Override
    public @NotNull SkyWarsVariant resolve(@NotNull SkyWarsConfig mapConfig) {
        String id = mapConfig.getVariantId();
        if (id == null || id.isBlank() || INLINE.equalsIgnoreCase(id)) {
            return mapConfig.resolveInlineVariant();
        }
        SkyWarsVariant variant = variants.get(id.toLowerCase());
        if (variant != null) return variant;
        plugin.getLogger().warning(Utils.formatGameLog(ink.ziip.championshipscore.api.object.game.GameTypeEnum.SkyWars,
                mapConfig.getAreaName(), "配置", "变体", "找不到 variant=" + id + "，回退到 inline"));
        return mapConfig.resolveInlineVariant();
    }

    private void loadVariant(Path path) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            String fallbackId = path.getFileName().toString().replaceFirst("\\.yml$", "");
            String id = yaml.getString("id", fallbackId);
            SkyWarsBoundaryRules boundary = new SkyWarsBoundaryRules(
                    yaml.getInt("mechanics.boundary.default-height"),
                    yaml.getInt("mechanics.boundary.middle-height"),
                    yaml.getInt("mechanics.boundary.lowest-height"),
                    yaml.getInt("mechanics.boundary.radius"),
                    yaml.getInt("mechanics.boundary.enable-at-remaining-seconds"),
                    yaml.getStringList("mechanics.boundary.shrink-schedule"));
            Integer ghast = yaml.contains("mechanics.spawn-happy-ghast-at-remaining-seconds")
                    ? yaml.getInt("mechanics.spawn-happy-ghast-at-remaining-seconds") : null;
            SkyWarsVariant variant = new SkyWarsVariant(id,
                    new GameLifecycleSettings(yaml.getInt("lifecycle.preparation-seconds", 10),
                            yaml.getInt("lifecycle.countdown-seconds", 5),
                            yaml.getInt("lifecycle.duration-seconds")),
                    new GamePresentationSettings(readRuleSections(yaml)),
                    new SkyWarsRules(yaml.getBoolean("mechanics.glass-cage", true), boundary,
                            yaml.getInt("mechanics.disable-health-regain-at-remaining-seconds"), ghast),
                    new SkyWarsScoring(yaml.getInt("scoring.kill"), yaml.getInt("scoring.survive"),
                            yaml.getInt("scoring.player-elimination-survival"),
                            yaml.getInt("scoring.team-elimination-survival")));
            validateVariant(variant);
            String key = id.toLowerCase();
            if (variants.putIfAbsent(key, variant) != null) {
                throw new IllegalArgumentException("duplicate variant id: " + id);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    Utils.formatModuleLog("SkyWarsVariant", "加载", "文件=" + path.getFileName()), exception);
        }
    }

    private static void validateVariant(SkyWarsVariant variant) {
        if (variant.id().isBlank()) throw new IllegalArgumentException("variant id is blank");

        int duration = variant.lifecycle().durationSeconds();
        if (duration <= 0) throw new IllegalArgumentException("lifecycle.duration-seconds must be positive");

        SkyWarsBoundaryRules boundary = variant.rules().boundary();
        if (boundary.radius() <= 0) throw new IllegalArgumentException("boundary.radius must be positive");
        if (boundary.defaultHeight() < boundary.middleHeight()
                || boundary.middleHeight() < boundary.lowestHeight()) {
            throw new IllegalArgumentException("boundary heights must satisfy default >= middle >= lowest");
        }
        if (boundary.enableAtRemainingSeconds() <= 0
                || boundary.enableAtRemainingSeconds() > duration) {
            throw new IllegalArgumentException("boundary enable time must be within the game duration");
        }
        validateRemainingTime("disable-health-regain", variant.rules().disableHealthRegainAtRemainingSeconds(), duration);
        Integer ghast = variant.rules().spawnHappyGhastAtRemainingSeconds();
        if (ghast != null) validateRemainingTime("spawn-happy-ghast", ghast, duration);

        for (String setting : boundary.shrinkSchedule()) {
            String[] parts = setting.split(":");
            if (parts.length != 4) throw new IllegalArgumentException("invalid shrink schedule: " + setting);
            try {
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                int radius = Integer.parseInt(parts[2]);
                if (start > duration || start <= end || end < 0 || radius < 0) {
                    throw new IllegalArgumentException("invalid shrink schedule: " + setting);
                }
                Integer.parseInt(parts[3]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid shrink schedule: " + setting, exception);
            }
        }

        SkyWarsScoring scoring = variant.scoring();
        if (scoring.kill() < 0 || scoring.survive() < 0
                || scoring.playerEliminationSurvival() < 0 || scoring.teamEliminationSurvival() < 0) {
            throw new IllegalArgumentException("scoring values must be non-negative");
        }
    }

    private static void validateRemainingTime(String name, int value, int duration) {
        if (value < 0 || value > duration) {
            throw new IllegalArgumentException(name + " remaining time must be within the game duration");
        }
    }

    private static List<List<String>> readRuleSections(YamlConfiguration yaml) {
        List<List<String>> result = new ArrayList<>();
        List<?> sections = yaml.getList("presentation.rules", List.of());
        for (Object section : sections) {
            if (!(section instanceof List<?> lines)) continue;
            List<String> translated = new ArrayList<>();
            for (Object line : lines) {
                if (line != null) translated.add(Utils.translateColorCodes(String.valueOf(line)));
            }
            if (!translated.isEmpty()) result.add(List.copyOf(translated));
        }
        return List.copyOf(result);
    }
}
