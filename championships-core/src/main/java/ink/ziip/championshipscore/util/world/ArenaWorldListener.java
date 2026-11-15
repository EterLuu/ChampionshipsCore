package ink.ziip.championshipscore.util.world;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.logging.LogText;

import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Prevents environmental mobs from entering managed arenas through spawning or saved chunks.
 * Plugin-spawned mobs remain available after chunk reloads as well as on their initial spawn.
 * DragonEggCarnival's vanilla dragon is retained; its world explicitly enables other enemies
 * through the world's monster spawn flag when preparing the fight.
 */
public final class ArenaWorldListener extends BaseListener {
    private final WorldManager worldManager;

    ArenaWorldListener(ChampionshipsCore plugin, WorldManager worldManager) {
        super(plugin);
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (!worldManager.isArenaWorld(world)) return;
        if (isUnwantedMob(event.getEntity(), event.getSpawnReason(), world.getAllowMonsters()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        removeLeftovers(event.getWorld(), event.getEntities());
    }

    /** Also called after world registration to cover entities loaded before createWorld returns. */
    void removeLeftovers(World world, Collection<Entity> entities) {
        if (!worldManager.isArenaWorld(world)) return;
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : entities)
            if (isUnwantedMob(entity, entity.getEntitySpawnReason(), world.getAllowMonsters()))
                leftovers.add(entity);
        if (leftovers.isEmpty()) return;
        leftovers.forEach(Entity::remove);
        plugin.getLogger()
                .log(
                        Level.INFO,
                        LogText.formatModuleLog(
                                "WorldManager",
                                MessageConfig.codeText("arena-world-listener.on-entities-load-1"),
                                MessageConfig.codeText(
                                        "arena-world-listener.on-entities-load-2",
                                        world.getName(),
                                        leftovers.size())));
    }

    static boolean isUnwantedMob(
            Entity entity, CreatureSpawnEvent.SpawnReason reason, boolean allowMonsters) {
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || entity instanceof EnderDragon)
            return false;
        return entity instanceof Animals
                || entity instanceof WaterMob
                || entity instanceof Ambient
                || (entity instanceof Enemy && !allowMonsters);
    }
}
