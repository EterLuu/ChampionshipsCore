package ink.ziip.championshipscore.util.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Arena worlds already disable natural spawning, but passive mobs saved inside an imported map (or
 * bred during a run) still appear once their chunks load. Blocks every non-plugin animal, ambient
 * and water-mob spawn in managed arena worlds and removes such leftovers whenever their chunk
 * entities load. Plugin-spawned mobs ({@code CUSTOM}) such as the TGTTOS chickens stay untouched,
 * as do monsters - DragonEggCarnival deliberately enables those for its fight.
 */
public final class ArenaWorldListener extends BaseListener {
    private final WorldManager worldManager;

    ArenaWorldListener(ChampionshipsCore plugin, WorldManager worldManager) {
        super(plugin);
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM)
            return;
        if (!worldManager.isArenaWorld(event.getLocation().getWorld()))
            return;
        if (isLeftoverAnimal(event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!worldManager.isArenaWorld(event.getWorld()))
            return;
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : event.getEntities())
            if (isLeftoverAnimal(entity))
                leftovers.add(entity);
        if (leftovers.isEmpty())
            return;
        leftovers.forEach(Entity::remove);
        plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("WorldManager", "清场",
                "小游戏世界=" + event.getWorld().getName() + " 移除地图残留动物=" + leftovers.size()));
    }

    private boolean isLeftoverAnimal(Entity entity) {
        return entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient;
    }
}
