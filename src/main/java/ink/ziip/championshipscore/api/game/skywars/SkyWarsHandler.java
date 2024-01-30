package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@Getter
@Setter
public class SkyWarsHandler extends BaseListener {
    private SkyWarsTeamArea skyWarsArea;

    protected SkyWarsHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            ItemStack item = event.getItem();
            if (item != null && block != null) {
                if (event.getItem().getType() == Material.CREEPER_SPAWN_EGG) {
                    event.setCancelled(true);
                    int amount = item.getAmount() - 1;
                    if (amount <= 0)
                        amount = 0;
                    item.setAmount(amount);
                    Creeper creeper = (Creeper) block.getWorld().spawnEntity(block.getLocation().add(0, 1, 0), EntityType.CREEPER);
                    creeper.setAI(true);
                    creeper.setCustomName(player.getName());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }

            if (event.getDamager() instanceof Creeper creeper) {
                Player spawner = Bukkit.getPlayer(creeper.getName());
                if (spawner == null)
                    return;
                if (skyWarsArea.notAreaPlayer(spawner))
                    return;
                if (player.getHealth() <= event.getDamage() && !skyWarsArea.getDeathPlayer().contains(player.getUniqueId())) {
                    skyWarsArea.addDeathPlayer(player);
                    String message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_CREEPER;
                    ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                    ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(spawner);
                    if (playerTeam == null || assailantTeam == null)
                        return;
                    message = message
                            .replace("%player%", playerTeam.getColoredColor() + player.getName())
                            .replace("%killer%", assailantTeam.getColoredColor() + spawner.getName());
                    skyWarsArea.sendMessageToAllGamePlayers(message);

                    if (!playerTeam.equals(assailantTeam)) {
                        skyWarsArea.addPlayerPoints(spawner.getUniqueId(), 40);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedDamageByBlock(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedDamageByBlock(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (skyWarsArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (skyWarsArea.notInArea(location)) {
                return;
            }

            if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlockPlaced();
        if (block.getType().toString().endsWith("_CONCRETE")) {
            event.getItemInHand().setAmount(64);
        }

        if (block.getType() == Material.TNT) {
            block.setType(Material.AIR, true);
            TNTPrimed tntPrimed = (TNTPrimed) block.getWorld().spawnEntity(block.getLocation(), EntityType.PRIMED_TNT);
            tntPrimed.setSource(player);
            return;
        }

        skyWarsArea.getBlockStates().add(event.getBlockPlaced().getState());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        BlockState blockState = event.getBlock().getState();
        if (!skyWarsArea.getBlockStates().contains(blockState)) {
            event.setCancelled(true);
            return;
        }

        skyWarsArea.getBlockStates().remove(blockState);
        event.getBlock().getDrops().clear();
    }

//    @EventHandler(priority = EventPriority.LOWEST)
//    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
//        if (event.getEntity() instanceof Player player) {
//            if (skyWarsArea.notAreaPlayer(player)) {
//                return;
//            }
//
//            Location location = player.getLocation();
//            if (skyWarsArea.notInArea(location)) {
//                return;
//            }
//
//            if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
//                return;
//            }
//
//            if (skyWarsArea.getTimer() <= skyWarsArea.getGameConfig().getTimeDisableHealthRegain())
//                event.setCancelled(true);
//        }
//    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                player.teleport(skyWarsArea.getSpectatorSpawnLocation());
            }
            if (skyWarsArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() < -64) {
                        player.teleport(skyWarsArea.getSpectatorSpawnLocation());
                    }
                    return;
                } else {
                    UUID uuid = player.getUniqueId();
                    if (!skyWarsArea.getDeathPlayer().contains(uuid)) {
                        Player assailant = player.getKiller();

                        if (assailant != null) {
                            ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                            ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

                            if (playerTeam == null || assailantTeam == null)
                                return;

                            if (playerTeam.equals(assailantTeam)) {
                                return;
                            }

                            String message = MessageConfig.SKY_WARS_KILL_PLAYER_BY_VOID;

                            message = message
                                    .replace("%player%", playerTeam.getColoredColor() + player.getName())
                                    .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

                            skyWarsArea.sendMessageToAllGamePlayers(message);
                            skyWarsArea.addPlayerPoints(assailant.getUniqueId(), 40);

                            skyWarsArea.addDeathPlayer(player);
                        } else {

                            String message = MessageConfig.SKY_WARS_PLAYER_DEATH_BY_VOID;

                            message = message.replace("%player%", player.getName());
                            skyWarsArea.sendMessageToAllGamePlayers(message);
                            skyWarsArea.addDeathPlayer(player);
                        }
                    }
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
            return;
        }

        if (skyWarsArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (skyWarsArea.getTimer() >= skyWarsArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null)
            return;

        if (world.getName().equals(skyWarsArea.getWorldName()))
            event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        World world = event.getBlock().getWorld();

        if (world.getName().equals(skyWarsArea.getWorldName()))
            event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (skyWarsArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (skyWarsArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }
}
