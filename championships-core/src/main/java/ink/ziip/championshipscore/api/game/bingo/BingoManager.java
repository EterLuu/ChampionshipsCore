package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.gui.CardItemListener;
import ink.ziip.championshipscore.api.game.bingo.gui.CardMenuListener;
import ink.ziip.championshipscore.platform.bukkit.bingo.map.TaskImageAtlas;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoExecutionMode;
import ink.ziip.championshipscore.api.game.bingo.task.TaskGenerator;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TagFilterLoader;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolLoader;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSource;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSpec;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TierlistLoader;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.util.world.WorldManager;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the bingo areas and the one-time bingo subsystem init: localisation, the card pool / tier list /
 * tag filters, the map-render image atlas, and the global GUI + portal listeners. Per-area configs live
 * in {@code plugin/bingo/areas/*.yml}; the rest of the bingo data (config.yml, lang, cards, tags,
 * tierlists) lives directly under {@code plugin/bingo/}.
 */
public class BingoManager extends BaseGameInstanceManager<BingoArea> {
    private MessageService messageService;
    private CardItemListener cardItemListener;
    private BingoCompassListener compassListener;
    private final List<Listener> globalListeners = new ArrayList<>();
    private final Map<String, BingoConfig> remoteAreaConfigs = new ConcurrentHashMap<>();
    private volatile boolean taskPoolReady;
    private BingoExecutionMode configuredExecutionMode;

    public BingoManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        taskPoolReady = false;
        configuredExecutionMode = parseExecutionMode(CCConfig.BINGO_EXECUTION_MODE);
        File bingoDir = new File(plugin.getDataFolder(), "bingo");
        bingoDir.mkdirs();
        YamlConfiguration config = loadGlobalConfig(bingoDir);
        if (config == null) return;

        // Localisation must exist before any area renders task names.
        messageService = new MessageService(plugin, config.getString("prefix", ""), config.getString("locale", "zh_CN"));

        boolean remote = remoteExecutionConfigured();
        if (!remote) {
            boolean worldsReady = loadBingoWorld(WorldManager.BINGO_OVERWORLD, World.Environment.NORMAL);
            worldsReady &= loadBingoWorld(WorldManager.BINGO_NETHER, World.Environment.NETHER);
            worldsReady &= loadBingoWorld(WorldManager.BINGO_END, World.Environment.THE_END);
            if (!worldsReady) {
                plugin.getLogger().severe(Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "世界",
                        "世界加载失败，游戏未注册"));
                return;
            }
        }
        if (!remote) {
            // Global GUI + portal listeners are execution-plane features and remain local-only.
            registerGlobal(new CardMenuListener());
            cardItemListener = new CardItemListener(plugin);
            cardItemListener.register();
            compassListener = new BingoCompassListener(plugin);
            compassListener.register();
            registerGlobal(new PortalListener("bingo"));
        }

        // Register map instances before deferring the expensive card-pool/image initialization. This
        // makes the area visible to commands and schedules as soon as the persistent worlds are ready.
        if (remote) loadRemoteAreaConfigs(new File(bingoDir, "areas"));
        else loadAreas(new File(bingoDir, "areas"));

        // Defer pool/atlas initialization to the first tick, when advancements, recipes and the map
        // palette are all available.
        new PlatformScheduler(plugin).runGlobal(() -> {
            taskPoolReady = applyContentConfiguration(config);
            if (!remote) TaskImageAtlas.ensureLoaded();
        });
    }

    /** Reloads the Bingo language and objective sources after active matches have been reset. */
    public CompletionStage<Boolean> reloadContentConfiguration() {
        if (messageService == null) return CompletableFuture.completedFuture(true);
        taskPoolReady = false;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                YamlConfiguration config = loadGlobalConfig(new File(plugin.getDataFolder(), "bingo"));
                result.complete(config != null && applyContentConfiguration(config));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, Utils.formatGameLog(GameTypeEnum.Bingo, "-",
                        "重载", "内容", "Bingo 内容配置重载失败"), failure);
                result.complete(false);
            }
        });
        return result;
    }

    private boolean applyContentConfiguration(@NotNull YamlConfiguration config) {
        MessageService service = messageService;
        if (service == null) return false;
        service.reload(config.getString("prefix", ""), config.getString("locale", "zh_CN"));
        TierlistLoader.load(plugin, config.getString("cards.tierlist", "default"));
        TagFilterLoader.load(plugin, config);
        String selected = config.getString("cards.selected", "default");
        TaskPoolSpec spec = TaskPoolLoader.load(plugin, selected);
        TaskPoolSource.set(spec, selected);
        TaskGenerator.setDifficultyWeights(readDifficultyWeights(config));
        TaskGenerator.setKitFilter(BingoStarterKit::trivialises);
        boolean ready = !spec.isEmpty();
        taskPoolReady = ready;
        return ready;
    }

    private void loadRemoteAreaConfigs(File areasFolder) {
        areasFolder.mkdirs();
        String[] areaList = areasFolder.list((directory, name) -> name.toLowerCase().endsWith(".yml"));
        if (areaList == null) return;
        for (String file : areaList) {
            String name = file.substring(0, file.length() - 4);
            BingoConfig config = new BingoConfig(plugin, name);
            config.initializeConfiguration(plugin.getFolder());
            remoteAreaConfigs.put(name, config);
        }
    }

    public boolean remoteExecutionConfigured() {
        BingoExecutionMode configured = configuredExecutionMode;
        if (configured != null) return configured == BingoExecutionMode.REMOTE;
        return parseExecutionMode(CCConfig.BINGO_EXECUTION_MODE) == BingoExecutionMode.REMOTE;
    }

    private static BingoExecutionMode parseExecutionMode(String value) {
        try {
            return BingoExecutionMode.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ignored) {
            return BingoExecutionMode.LOCAL;
        }
    }

    public boolean isTaskPoolReady() {
        return taskPoolReady;
    }

    public BingoConfig getRemoteConfig(String area) {
        return remoteAreaConfigs.get(area);
    }

    @Override
    public List<String> getAreaNameList() {
        if (!remoteExecutionConfigured()) return super.getAreaNameList();
        return new ArrayList<>(remoteAreaConfigs.keySet());
    }

    private void loadAreas(File areasFolder) {
        areasFolder.mkdirs();
        String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (areaList == null) return;
        for (String file : areaList) {
            String name = file.substring(0, file.length() - 4);
            areas.put(name, new BingoArea(plugin, new BingoConfig(plugin, name)));
        }
    }

    private @Nullable YamlConfiguration loadGlobalConfig(File bingoDir) {
        File configFile = new File(bingoDir, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("bingo/config.yml")) {
                if (in != null) Files.copy(in, configFile.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "配置",
                        "无法写出 bingo/config.yml | " + e.getMessage()));
            }
        }
        if (!configFile.isFile()) return null;
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(configFile);
            return config;
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "配置",
                    "无法解析 bingo/config.yml"), failure);
            return null;
        }
    }

    /**
     * Reads {@code cards.difficulty-weights} (EASY,MEDIUM,ADVANCED,HARD,VERY_HARD) from the bingo
     * config, defaulting to {@code [3,5,2,1,0]} (3:5:2:1 with VERY_HARD excluded).
     */
    private int[] readDifficultyWeights(YamlConfiguration config) {
        List<Integer> list = config.getIntegerList("cards.difficulty-weights");
        if (list == null || list.isEmpty()) {
            return new int[]{3, 5, 2, 1, 0};
        }
        int[] weights = new int[list.size()];
        for (int i = 0; i < list.size(); i++) weights[i] = list.get(i);
        return weights;
    }

    private void registerGlobal(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        globalListeners.add(listener);
    }

    @Override
    public void unload() {
        taskPoolReady = false;
        configuredExecutionMode = null;
        for (BingoArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.abortAndReset();
            }
        }
        for (Listener listener : globalListeners) {
            HandlerList.unregisterAll(listener);
        }
        globalListeners.clear();
        if (cardItemListener != null) {
            cardItemListener.unRegister();
            cardItemListener = null;
        }
        if (compassListener != null) {
            compassListener.unRegister();
            compassListener = null;
        }
        if (messageService != null) {
            messageService.close();
            messageService = null;
        }
        clearAreas();
        remoteAreaConfigs.clear();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name) || remoteAreaConfigs.containsKey(name))
            return false;

        BingoConfig bingoConfig = new BingoConfig(plugin, name);
        bingoConfig.initializeConfiguration(plugin.getFolder());
        bingoConfig.setAreaName(name);
        bingoConfig.saveOptions();

        if (remoteExecutionConfigured()) {
            return remoteAreaConfigs.putIfAbsent(name, bingoConfig) == null;
        }
        BingoArea bingoArea = areas.putIfAbsent(name, new BingoArea(plugin, bingoConfig));
        return bingoArea == null;
    }

    @Override
    public @Nullable BaseGameConfig getMapConfig(@NotNull String name) {
        return remoteExecutionConfigured() ? remoteAreaConfigs.get(name) : super.getMapConfig(name);
    }

    @Override
    public synchronized boolean canRenameArea(@NotNull String name) {
        return remoteExecutionConfigured() ? remoteAreaConfigs.containsKey(name) : super.canRenameArea(name);
    }

    @Override
    public synchronized boolean detachAreaForRename(@NotNull String name) {
        if (!remoteExecutionConfigured()) return super.detachAreaForRename(name);
        return remoteAreaConfigs.remove(name) != null;
    }

    @Override
    public synchronized boolean forceDetachAreaAfterFailedRename(@NotNull String name) {
        if (!remoteExecutionConfigured()) return super.forceDetachAreaAfterFailedRename(name);
        remoteAreaConfigs.remove(name);
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(@NotNull String name, @NotNull String worldName) {
        if (!remoteExecutionConfigured()) return super.loadAreaAfterRename(name, worldName);
        if (remoteAreaConfigs.containsKey(name)) return false;
        BingoConfig config = new BingoConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        remoteAreaConfigs.put(name, config);
        return true;
    }
}
