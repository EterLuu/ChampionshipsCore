package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

@Getter
@Setter
public class ParkourTagHandler extends BaseListener {
    private ParkourTagArea parkourTagArea;

    public ParkourTagHandler(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChooseChaser(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        if (parkourTagArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
            Block block = event.getClickedBlock();
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (block != null) {
                    if (block.getType() == Material.BIRCH_WALL_SIGN) {
                        UUID uuid = player.getUniqueId();
                        if (plugin.getGameManager().getParkourTagManager().canBeChaser(uuid)) {
                            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(uuid);
                            if (championshipTeam != null) {
                                ChampionshipTeam rightChampionshipTeam = parkourTagArea.getRightChampionshipTeam();
                                ChampionshipTeam leftChampionshipTeam = parkourTagArea.getLeftChampionshipTeam();

                                String message = MessageConfig.PARKOUR_TAG_BECOME_CHASER;

                                if (championshipTeam.equals(rightChampionshipTeam)) {
                                    parkourTagArea.setRightAreaChaser(uuid);

                                    rightChampionshipTeam.sendMessageToAll(message
                                            .replace("%player%", player.getName())
                                            .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - plugin.getGameManager().getParkourTagManager().getChaserTimes(uuid) - 1)));
                                }
                                if (championshipTeam.equals(leftChampionshipTeam)) {
                                    parkourTagArea.setLeftAreaChaser(uuid);

                                    leftChampionshipTeam.sendMessageToAll(message
                                            .replace("%player%", player.getName())
                                            .replace("%times%", String.valueOf(CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES - plugin.getGameManager().getParkourTagManager().getChaserTimes(uuid) - 1)));
                                }
                            }
                        } else {
                            player.sendMessage(MessageConfig.PARKOUR_TAG_BECOME_CHASER_FAILED);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block != null && block.getType() != Material.BELL)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (parkourTagArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (parkourTagArea.notInArea(location)) {
                return;
            }

            if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
                event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerUseClock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        if (parkourTagArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (parkourTagArea.getTimer() >= parkourTagArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {

            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            ItemStack offHandItem = player.getInventory().getItemInOffHand();

            if (mainHandItem.getType() == Material.CLOCK || offHandItem.getType() == Material.CLOCK) {

                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                if (championshipTeam != null) {

                    if (plugin.getGameManager().getParkourTagManager().canUseClock(championshipTeam)) {
                        Player chaser = null;

                        if (championshipTeam.equals(parkourTagArea.getRightChampionshipTeam())) {
                            chaser = parkourTagArea.getLeftAreaChaserPlayer();
                            for (Player leftEscapee : parkourTagArea.getLeftAreaEscapees()) {
                                leftEscapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK);
                            }
                            parkourTagArea.setLeftAreaEscapeesClockCoolDown();
                        }

                        if (championshipTeam.equals(parkourTagArea.getLeftChampionshipTeam())) {
                            chaser = parkourTagArea.getRightAreaChaserPlayer();
                            for (Player rightEscapee : parkourTagArea.getRightAreaEscapees()) {
                                rightEscapee.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK);
                            }
                            parkourTagArea.setRightAreaEscapeesClockCoolDown();
                        }

                        if (chaser != null) {
                            chaser.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
                        }

                        plugin.getGameManager().getParkourTagManager().setClockUsedTimes(championshipTeam);
                    } else {

                        player.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_CLOCK_FAILED);
                    }
                }
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerUseFeather(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        if (parkourTagArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (parkourTagArea.getTimer() >= parkourTagArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            ItemStack offHandItem = player.getInventory().getItemInOffHand();
            if (mainHandItem.getType() == Material.POTION || offHandItem.getType() == Material.POTION) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                player.sendMessage(MessageConfig.PARKOUR_TAG_KITS_USE_FEATHER);
                mainHandItem.setAmount(0);
                offHandItem.setAmount(0);
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Player assailant) {
                if (parkourTagArea.notAreaPlayer(player)) {
                    return;
                }

                Location location = player.getLocation();
                if (parkourTagArea.notInArea(location)) {
                    return;
                }

                if (parkourTagArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                    event.setCancelled(true);
                    return;
                }

                if (parkourTagArea.getRightAreaEscapees().contains(assailant) || parkourTagArea.getLeftAreaEscapees().contains(assailant)) {
                    event.setCancelled(true);
                    return;
                }

                ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

                if (playerTeam == null || assailantTeam == null)
                    return;

                if (playerTeam.equals(assailantTeam)) {
                    return;
                }

                if (assailant.getUniqueId().equals(parkourTagArea.getLeftAreaChaser()) || assailant.getUniqueId().equals(parkourTagArea.getRightAreaChaser())) {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.SPECTATOR);
                    });
                    assailant.playSound(assailant, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

                    // Add 6 points to chaser
                    parkourTagArea.addPlayerPoints(assailant.getUniqueId(), 6);

                    String message = MessageConfig.PARKOUR_TAG_CATCH_PLAYER
                            .replace("%player%", playerTeam.getColoredColor() + player.getName())
                            .replace("%chaser%", assailantTeam.getColoredColor() + assailant.getName());

                    parkourTagArea.sendMessageToPlayerAreaPlayers(assailant, message);

                    parkourTagArea.getPlayerSurviveTimes().put(player.getUniqueId(), parkourTagArea.getGameConfig().getTimer() - parkourTagArea.getTimer());
                    parkourTagArea.updateTeamSurviveTimes();
                    return;
                }

                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (parkourTagArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (parkourTagArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            if (parkourTagArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                player.teleport(parkourTagArea.getSpectatorSpawnLocation());
            }
            if (parkourTagArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    if (location.getY() < -64) {
                        player.teleport(parkourTagArea.getSpectatorSpawnLocation());
                    }
                    return;
                }
            }
            return;
        }

        if (parkourTagArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        if (parkourTagArea.getTimer() >= parkourTagArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChangeSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (parkourTagArea.notAreaPlayer(player)) {
            return;
        }
        Location location = player.getLocation();
        if (parkourTagArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }
}
