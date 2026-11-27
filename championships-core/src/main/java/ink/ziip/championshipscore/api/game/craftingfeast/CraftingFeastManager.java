package ink.ziip.championshipscore.api.game.craftingfeast;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.game.craftingfeast.mechanics.FeastVanillaRecipes;
import ink.ziip.championshipscore.api.game.craftingfeast.runtime.CraftingFeastArea;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class CraftingFeastManager extends BaseGameInstanceManager<CraftingFeastArea> {
    public CraftingFeastManager(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public void load() {
        deferMapLoad(
                () -> {
                    File folder = new File(plugin.getDataFolder(), "craftingfeast/areas");
                    loadMapDefinitions(folder, this::loadDefinition);
                });
    }

    private void loadDefinition(String name, File file) {
        String worldName = YamlConfiguration.loadConfiguration(file).getString("world-name", "");
        if (!worldName.isBlank() && !loadArenaWorld(worldName)) return;
        CraftingFeastConfig config = new CraftingFeastConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        if (config.isPreparePublished()) {
            try {
                // isPrepareReady() hides the reason; name any configuration or recipe gap once.
                config.validate();
                FeastVanillaRecipes.validate(config);
            } catch (RuntimeException failure) {
                plugin.getLogger()
                        .warning(
                                "Crafting Feast map "
                                        + name
                                        + " cannot start: "
                                        + failure.getMessage());
            }
        }
        CraftingFeastArea area = new CraftingFeastArea(plugin, config);
        registerMapInstances(name, List.of(area));
        area.initialize();
    }

    @Override
    public boolean addArea(String name) {
        return addArea(name, "");
    }

    @Override
    public boolean addArea(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        String targetWorld = worldName == null ? "" : worldName.trim();
        if (!targetWorld.isEmpty() && !loadArenaWorld(targetWorld)) return false;
        CraftingFeastConfig config = new CraftingFeastConfig(plugin, name);
        config.initializeConfiguration(plugin.getFolder());
        config.setAreaName(name);
        config.bindConfiguredWorld(targetWorld);
        config.beginPrepareDraft();
        CraftingFeastArea area = new CraftingFeastArea(plugin, config);
        registerMapInstances(name, List.of(area));
        area.initialize();
        return true;
    }

    @Override
    public synchronized boolean loadAreaAfterRename(String name, String worldName) {
        if (areas.containsKey(name)) return false;
        loadDefinition(
                name, new File(plugin.getDataFolder(), "craftingfeast/areas/" + name + ".yml"));
        return areas.containsKey(name);
    }
}
