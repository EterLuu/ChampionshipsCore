package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.game.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class BattleBoxHandler extends BaseListener {
    private BattleBoxArea battleBoxArea;

    public BattleBoxHandler(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChooseWeaponKit(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        Block block = event.getClickedBlock();

        if (battleBoxArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (block != null) {
                    if (block.getType() == Material.BIRCH_WALL_SIGN) {
                        Sign sign = (Sign) block.getState();
                        String kit = ChatColor.stripColor(sign.getSide(Side.FRONT).getLine(0));
                        BBWeaponKitEnum type = getBbWeaponKitEnum(kit);
                        if (type != null) {
                            if (battleBoxArea.setPlayerWeaponKit(player, type)) {
                                player.sendMessage(MessageConfig.BATTLE_BOX_KIT_CHOOSE.replace("%kit%", type.toString()));
                            } else {
                                player.sendMessage(MessageConfig.BATTLE_BOX_KIT_ALREADY_CHOOSE.replace("%kit%", type.toString()));
                            }
                        }
                    }
                }
            }
            event.setCancelled(true);
        }
    }

    @Nullable
    private static BBWeaponKitEnum getBbWeaponKitEnum(String kit) {
        BBWeaponKitEnum type = null;
        if (kit.contains(BBWeaponKitEnum.PUNCH.toString())) {
            type = BBWeaponKitEnum.PUNCH;
        }
        if (kit.contains(BBWeaponKitEnum.KNOCK_BACK.toString())) {
            type = BBWeaponKitEnum.KNOCK_BACK;
        }
        if (kit.contains(BBWeaponKitEnum.JUMP.toString())) {
            type = BBWeaponKitEnum.JUMP;
        }
        if (kit.contains(BBWeaponKitEnum.PULL.toString())) {
            type = BBWeaponKitEnum.PULL;
        }
        return type;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItems(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (battleBoxArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (battleBoxArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (battleBoxArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (battleBoxArea.notInArea(location)) {
                return;
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (!event.getBlock().getLocation().toVector().isInAABB(battleBoxArea.getGameConfig().getWoolPos1(), battleBoxArea.getGameConfig().getWoolPos2())) {
            event.setCancelled(true);
        }

        event.getItemInHand().setAmount(64);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (!event.getBlock().getLocation().toVector().isInAABB(battleBoxArea.getGameConfig().getWoolPos1(), battleBoxArea.getGameConfig().getWoolPos2())) {
            event.setCancelled(true);
        }

        event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            if (battleBoxArea.getGameStageEnum() == GameStageEnum.PREPARATION) {
                player.teleport(battleBoxArea.getGameConfig().getSpectatorSpawnPoint());
            }
            if (battleBoxArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.teleport(battleBoxArea.getGameConfig().getSpectatorSpawnPoint());
                }
            }
            return;
        }

        if (battleBoxArea.getGameStageEnum() == GameStageEnum.PROGRESS) {
            if (battleBoxArea.getTimer() >= battleBoxArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (battleBoxArea.getTimer() >= battleBoxArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (battleBoxArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (battleBoxArea.notInArea(location)) {
                return;
            }

            if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
                event.setCancelled(true);
                return;
            }

            if (battleBoxArea.getTimer() >= battleBoxArea.getGameConfig().getTimer()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        if (battleBoxArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            event.setCancelled(true);
            return;
        }

        if (battleBoxArea.getTimer() >= battleBoxArea.getGameConfig().getTimer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (battleBoxArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (battleBoxArea.notInArea(location)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (battleBoxArea.notAreaPlayer(player)) {
                return;
            }

            Location location = player.getLocation();
            if (battleBoxArea.notInArea(location)) {
                return;
            }

            if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
                event.setCancelled(true);
        }
    }
}
