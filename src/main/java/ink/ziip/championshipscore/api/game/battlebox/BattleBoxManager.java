package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.game.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxManager extends BaseAreaManager<BattleBoxArea> {
    private final ConcurrentHashMap<UUID, BBWeaponKitEnum> playerWeaponKit = new ConcurrentHashMap<>();

    public BattleBoxManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "battlebox");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new BattleBoxArea(plugin, new BattleBoxConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (BattleBoxArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGame();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        BattleBoxArea battleBoxArea = areas.putIfAbsent(name, new BattleBoxArea(plugin, new BattleBoxConfig(plugin, name)));

        return battleBoxArea == null;
    }

    public boolean setPlayerWeaponKit(@NotNull Player player, @NotNull BBWeaponKitEnum type) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (championshipTeam == null)
            return false;
        for (UUID uuid : championshipTeam.getMembers()) {
            if (playerWeaponKit.get(uuid) == type) {
                return uuid.equals(player.getUniqueId());
            }
        }
        playerWeaponKit.put(player.getUniqueId(), type);
        return true;
    }

    public BBWeaponKitEnum getPlayerWeaponKit(@NotNull Player player) {
        BBWeaponKitEnum bbWeaponKitEnum = playerWeaponKit.get(player.getUniqueId());
        if (bbWeaponKitEnum != null) {
            return bbWeaponKitEnum;
        }

        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        List<BBWeaponKitEnum> kits = new ArrayList<>(List.of(BBWeaponKitEnum.values()));
        if (championshipTeam != null) {
            for (UUID uuid : championshipTeam.getMembers()) {
                BBWeaponKitEnum selected = playerWeaponKit.get(uuid);
                if (selected != null) {
                    kits.remove(selected);
                }
            }
            BBWeaponKitEnum selected = kits.iterator().next();
            if (selected != null) {
                playerWeaponKit.put(player.getUniqueId(), selected);
                return selected;
            } else {
                return BBWeaponKitEnum.getRandomEnum();
            }
        }

        return null;
    }

    public void setWeaponKit(@NotNull Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam == null)
            return;

        player.getInventory().clear();
        PlayerInventory inventory = player.getInventory();
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        ItemStack bow = new ItemStack(Material.BOW);
        ItemStack arrows = new ItemStack(Material.ARROW);
        arrows.setAmount(10);

        inventory.addItem(sword);
        inventory.addItem(bow);
        inventory.addItem(arrows);

        BBWeaponKitEnum type = getPlayerWeaponKit(player);

        if (type == BBWeaponKitEnum.PUNCH) {
            ItemStack crossbow = new ItemStack(Material.CROSSBOW);
            crossbow.addEnchantment(Enchantment.QUICK_CHARGE, 1);
            inventory.addItem(crossbow);
        }
        if (type == BBWeaponKitEnum.KNOCK_BACK) {
            ItemStack axe = new ItemStack(Material.WOODEN_AXE);
            axe.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            inventory.addItem(axe);
        }
        if (type == BBWeaponKitEnum.JUMP) {
            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                PotionEffect potionEffect = new PotionEffect(PotionEffectType.JUMP, 600, 1);
                potionMeta.addCustomEffect(potionEffect, true);
                potion.setItemMeta(potionMeta);
            }
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.PULL) {
            ItemStack moreArrows = new ItemStack(Material.ARROW);
            moreArrows.setAmount(8);
            inventory.addItem(moreArrows);
        }

        inventory.addItem(new ItemStack(Material.SHEARS));

        inventory.addItem(championshipTeam.getWool());

        inventory.setBoots(championshipTeam.getBoots());

        inventory.setHelmet(championshipTeam.getHelmet());
    }
}
