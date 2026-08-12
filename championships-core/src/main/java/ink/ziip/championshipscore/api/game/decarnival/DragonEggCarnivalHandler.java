package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Setter
public class DragonEggCarnivalHandler extends BaseListener {
    private DragonEggCarnivalArea dragonEggCarnivalArea;
    private final Map<UUID, UUID> crystalAttackers = new HashMap<>();

    protected DragonEggCarnivalHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) player.removeScoreboardTag("final");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (dragonEggCarnivalArea.notAreaPlayer(player)) return;
        if (dragonEggCarnivalArea.notInArea(player.getLocation())) return;
        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player) || dragonEggCarnivalArea.isIntroductionPhase()) return;

        Location location = player.getLocation();
        if (!dragonEggCarnivalArea.notInArea(location)) return;
        GameStageEnum stage = dragonEggCarnivalArea.getGameStageEnum();
        if (stage == GameStageEnum.PREPARATION || stage == GameStageEnum.COUNTDOWN) {
            dragonEggCarnivalArea.teleportPlayerToSpawnLocation(player);
            return;
        }
        if (stage != GameStageEnum.PROGRESS) return;
        if (dragonEggCarnivalArea.isManagedSpectator(player)) {
            if (location.getY() <= -64D) player.teleport(dragonEggCarnivalArea.getSpectatorSpawnLocation());
            return;
        }

        dragonEggCarnivalArea.teleportPlayerToSpawnLocation(player);
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (team != null) {
            dragonEggCarnivalArea.sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_OUT_OF_BORDER
                    .replace("%player%", Utils.formatPlayerName(player)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) return;
        if (dragonEggCarnivalArea.notInArea(crystal.getLocation())) return;
        Player player = causingPlayer(event);
        if (player == null) player = crystalChainAttacker(event);
        if (player == null || dragonEggCarnivalArea.notAreaPlayer(player)) return;
        crystalAttackers.put(crystal.getUniqueId(), player.getUniqueId());
        Player creditedPlayer = player;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!crystal.isValid() || crystal.isDead()) {
                dragonEggCarnivalArea.recordCrystalDestroyed(creditedPlayer, crystal.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDamaged(EntityDamageEvent event) {
        EnderDragon dragon = switch (event.getEntity()) {
            case EnderDragon direct -> direct;
            case EnderDragonPart part -> part.getParent();
            default -> null;
        };
        if (dragon == null || dragonEggCarnivalArea.getGameStageEnum() != GameStageEnum.PROGRESS) return;
        if (dragonEggCarnivalArea.notInArea(dragon.getLocation())) return;
        Player player = causingPlayer(event);
        if (player == null) player = crystalChainAttacker(event);
        if (player == null || dragonEggCarnivalArea.notAreaPlayer(player)) return;
        dragonEggCarnivalArea.recordDragonDamage(player, event.getFinalDamage(), dragon.getMaxHealth());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) return;
        dragonEggCarnivalArea.recordAdvancement(player, event.getAdvancement().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamConcretePlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (dragonEggCarnivalArea.notAreaPlayer(player)) return;
        Material type = event.getItemInHand().getType();
        if (!type.name().endsWith("_CONCRETE")) return;
        dragonEggCarnivalArea.replenishTeamConcrete(player, event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnterPortal(PlayerPortalEvent event) {
        if (!dragonEggCarnivalArea.notAreaPlayer(event.getPlayer())
                || dragonEggCarnivalArea.isManagedSpectator(event.getPlayer())) event.setCancelled(true);
    }

    void resetMatchState() {
        crystalAttackers.clear();
    }

    private static Player causingPlayer(EntityDamageEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) return player;
        Entity direct = event.getDamageSource().getDirectEntity();
        if (direct instanceof Player player) return player;
        if (direct instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private Player crystalChainAttacker(EntityDamageEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        Entity direct = event.getDamageSource().getDirectEntity();
        UUID attackerId = causing instanceof EnderCrystal crystal ? crystalAttackers.get(crystal.getUniqueId())
                : direct instanceof EnderCrystal crystal ? crystalAttackers.get(crystal.getUniqueId()) : null;
        return attackerId == null ? null : Bukkit.getPlayer(attackerId);
    }
}
