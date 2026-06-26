package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.bingo.gui.CardItemListener;
import ink.ziip.championshipscore.api.game.bingo.gui.CardMenuListener;
import ink.ziip.championshipscore.api.game.bingo.gui.TaskImageAtlas;
import ink.ziip.championshipscore.api.game.bingo.task.TaskGenerator;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TagFilterLoader;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolLoader;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSource;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSpec;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TierlistLoader;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.game.bingo.world.BingoWorldManager;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the bingo areas and the one-time bingo subsystem init: localisation, the card pool / tier list /
 * tag filters, the map-render image atlas, and the global GUI + portal listeners. Per-area configs live
 * in {@code plugin/bingo/areas/*.yml}; the rest of the bingo data (config.yml, lang, cards, tags,
 * tierlists) lives directly under {@code plugin/bingo/}.
 */
public class BingoManager extends BaseAreaManager<BingoArea> {
    private MessageService messageService;
    private CardItemListener cardItemListener;
    private BingoWorldManager bingoWorldManager;
    private final List<Listener> globalListeners = new ArrayList<>();

    public BingoManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        File bingoDir = new File(plugin.getDataFolder(), "bingo");
        bingoDir.mkdirs();
        YamlConfiguration config = loadGlobalConfig(bingoDir);

        // Localisation must exist before any area renders task names.
        messageService = new MessageService(plugin, config.getString("prefix", ""), config.getString("locale", "zh_CN"));

        // Global GUI + portal listeners, registered once for all areas.
        registerGlobal(new CardMenuListener());
        cardItemListener = new CardItemListener(plugin);
        cardItemListener.register();
        registerGlobal(new PortalListener("bingo"));

        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        // Defer pool/atlas init and area scan to the first tick, when advancements, recipes and the map
        // palette are all available.
        scheduler.runTask(plugin, task -> {
            // Bingo is whole-world exploration: ensure the persistent survival worlds (overworld +
            // nether + end, normal terrain) exist, creating them once if missing.
            bingoWorldManager = new BingoWorldManager(plugin);
            bingoWorldManager.ensureWorlds();

            TierlistLoader.load(plugin, config.getString("cards.tierlist", "default"));
            TagFilterLoader.load(plugin, config);
            String selected = config.getString("cards.selected", "default");
            TaskPoolSpec spec = TaskPoolLoader.load(plugin, selected);
            TaskPoolSource.set(spec, selected);
            // Fixed difficulty distribution EASY:MEDIUM:ADVANCED:HARD:VERY_HARD; weight 0 excludes a
            // tier. Default [3,5,2,1,0] = 3:5:2:1 with VERY_HARD excluded.
            TaskGenerator.setDifficultyWeights(readDifficultyWeights(config));
            TaskImageAtlas.ensureLoaded();

            File areasFolder = new File(bingoDir, "areas");
            areasFolder.mkdirs();
            String[] areaList = areasFolder.list((d, n) -> n.toLowerCase().endsWith(".yml"));
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new BingoArea(plugin, new BingoConfig(plugin, name)));
                }
            }
        });
    }

    private YamlConfiguration loadGlobalConfig(File bingoDir) {
        File configFile = new File(bingoDir, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("bingo/config.yml")) {
                if (in != null) Files.copy(in, configFile.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning("[Bingo] 无法写出 bingo/config.yml: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(configFile);
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
        for (BingoArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
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
        clearAreas();
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        BingoConfig bingoConfig = new BingoConfig(plugin, name);
        bingoConfig.initializeConfiguration(plugin.getFolder());
        bingoConfig.setAreaName(name);
        bingoConfig.saveOptions();

        BingoArea bingoArea = areas.putIfAbsent(name, new BingoArea(plugin, bingoConfig));

        return bingoArea == null;
    }
}
