package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxArea extends BaseTeamArea {
    private final ConcurrentHashMap<UUID, BBWeaponKitEnum> playerWeaponKit = new ConcurrentHashMap<>();
    @Getter
    private final BattleBoxConfig battleBoxConfig;
    @Getter
    private int timer;
    private int startGamePreparationTaskId;
    private int startGameProgressTaskId;
    private int woolCheckerTaskId;

    protected void resetGame() {
        resetRegionBlocks();
        cleanDroppedItems();

        rightChampionshipTeam = null;
        leftChampionshipTeam = null;

        playerPoints.clear();
        playerWeaponKit.clear();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    public BattleBoxArea(ChampionshipsCore championshipsCore, BattleBoxConfig battleBoxConfig) {
        super(championshipsCore);
        this.battleBoxConfig = battleBoxConfig;
        battleBoxConfig.initializeConfiguration(plugin.getFolder());
        BattleBoxHandler battleBoxHandler = new BattleBoxHandler(plugin, this);
        battleBoxHandler.register();
        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        rightChampionshipTeam.teleportAllPlayers(battleBoxConfig.getRightPreSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(battleBoxConfig.getLeftPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        setHealthForAllGamePlayers(20);

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_START_PREPARATION_TITLE, MessageConfig.BATTLE_BOX_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                scheduler.cancelTask(startGamePreparationTaskId);
            }

            timer--;
        }, 0, 20L);
    }

    protected void startGameProgress() {
        resetRegionBlocks();
        summonPotions();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE);

        timer = battleBoxConfig.getTimer() + 5;

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        rightChampionshipTeam.teleportAllPlayers(battleBoxConfig.getRightSpawnPoint());
        leftChampionshipTeam.teleportAllPlayers(battleBoxConfig.getLeftSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        setHealthForAllGamePlayers(20);
        clearEffectsForAllGamePlayers();

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {

            if (timer > battleBoxConfig.getTimer()) {
                String countDown = MessageConfig.BATTLE_BOX_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - battleBoxConfig.getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 0.5F);
            }

            if (timer == battleBoxConfig.getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.BATTLE_BOX_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                scheduler.cancelTask(startGameProgressTaskId);
            }

            timer--;
        }, 0, 20L);

        woolCheckerTaskId = scheduler.scheduleSyncRepeatingTask(plugin, () -> {
            if (timer == -1) {
                changeLevelForAllGamePlayers(0);
                endGame();
                scheduler.cancelTask(woolCheckerTaskId);
            }

            HashMap<Material, Integer> blockCount = countBlocksInRegion();
            if (getGameStageEnum() == GameStageEnum.PROGRESS) {
                int rightTeamWool = blockCount.getOrDefault(rightChampionshipTeam.getWool().getType(), 0);
                int leftTeamWool = blockCount.getOrDefault(leftChampionshipTeam.getWool().getType(), 0);
                if (rightTeamWool == 9 || leftTeamWool == 9) {
                    endGameInForm();
                }
            }
        }, 0, 1L);
    }

    protected void endGameInForm() {
        setGameStageEnum(GameStageEnum.STOPPING);
        cleanInventoryForAllGamePlayers();
        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        changeGameModelForAllGamePlayers(GameMode.SPECTATOR);
    }

    protected void endGame() {
        scheduler.cancelTask(startGamePreparationTaskId);
        scheduler.cancelTask(startGameProgressTaskId);
        scheduler.cancelTask(woolCheckerTaskId);

        calculatePoints();

        cleanInventoryForAllGamePlayers();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        rightChampionshipTeam.teleportAllPlayers(getLobbyLocation());
        leftChampionshipTeam.teleportAllPlayers(getLobbyLocation());

        setHealthForAllGamePlayers(20);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {
        HashMap<Material, Integer> blockCount = countBlocksInRegion();
        int rightTeamWool = blockCount.getOrDefault(rightChampionshipTeam.getWool().getType(), 0);
        int leftTeamWool = blockCount.getOrDefault(leftChampionshipTeam.getWool().getType(), 0);

        String message = MessageConfig.BATTLE_BOX_WIN;

        if (rightTeamWool > leftTeamWool) {
            for (UUID uuid : rightChampionshipTeam.getMembers()) {
                addPlayerPoints(uuid, 40);
            }

            message = message.replace("%team%", rightChampionshipTeam.getColoredName());
        } else if (leftTeamWool > rightTeamWool) {
            for (UUID uuid : leftChampionshipTeam.getMembers()) {
                addPlayerPoints(uuid, 40);
            }

            message = message.replace("%team%", leftChampionshipTeam.getColoredName());
        } else {
            for (UUID uuid : rightChampionshipTeam.getMembers()) {
                addPlayerPoints(uuid, 15);
            }
            for (UUID uuid : leftChampionshipTeam.getMembers()) {
                addPlayerPoints(uuid, 15);
            }

            message = MessageConfig.BATTLE_BOX_DRAW;
        }

        addPlayerPointsToDatabase(GameTypeEnum.BattleBox);

        sendMessageToAllGamePlayersInActionbarAndMessage(message);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);

        message = MessageConfig.BATTLE_BOX_SHOW_POINTS
                .replace("team", rightChampionshipTeam.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(rightChampionshipTeam)))
                .replace("%rival%", leftChampionshipTeam.getColorName())
                .replace("%rival_points%", String.valueOf(getTeamPoints(leftChampionshipTeam)));

        sendMessageToAllGamePlayersInActionbarAndMessage(message);

    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {

            ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
            Player killer = player.getKiller();
            if (playerChampionshipTeam != null && killer != null) {
                UUID uuid = killer.getUniqueId();
                playerPoints.put(uuid, playerPoints.getOrDefault(uuid, 0) + 15);
                event.setDeathMessage(null);

                String message = MessageConfig.BATTLE_BOX_KILL_PLAYER
                        .replace("%player%", playerChampionshipTeam.getColoredColor() + player.getName())
                        .replace("%killer%", killer.getName());

                killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

                sendMessageToAllGamePlayers(message);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                event.getEntity().spigot().respawn();
                event.getEntity().teleport(getSpectatorSpawnLocation());
                event.getEntity().setGameMode(GameMode.SPECTATOR);
            }
        }.runTask(plugin);

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }
        ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);

        if (playerChampionshipTeam != null) {
            String message = MessageConfig.BATTLE_BOX_PLAYER_LEAVE
                    .replace("%player%", playerChampionshipTeam.getColoredColor() + player.getName());

            sendMessageToAllGamePlayers(message);
        }
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            teleportPlayerToPreSpawnLocation(player);
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {
            player.teleport(getSpectatorSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
        }

        player.teleport(getSpectatorSpawnLocation());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            if (championshipTeam.equals(rightChampionshipTeam)) {
                player.teleport(battleBoxConfig.getRightPreSpawnPoint());
                player.setGameMode(GameMode.ADVENTURE);
                return;
            }
            if (championshipTeam.equals(leftChampionshipTeam)) {
                player.teleport(battleBoxConfig.getLeftPreSpawnPoint());
                player.setGameMode(GameMode.ADVENTURE);
                return;
            }
        }

        player.teleport(battleBoxConfig.getSpectatorSpawnPoint());
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void giveItemToAllGamePlayers() {
        for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
            setWeaponKit(player);
        }
        for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
            setWeaponKit(player);
        }
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

    private void summonPotions() {
        for (String stringLocation : battleBoxConfig.getPotionSpawnPoints()) {
            Location location = Utils.getLocation(stringLocation);
            World world = location.getWorld();
            ItemStack item = new ItemStack(Material.SPLASH_POTION);
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            if (potionMeta != null) {
                potionMeta.setBasePotionType(PotionType.STRONG_HARMING);
                item.setItemMeta(potionMeta);
                Item itemDropped;
                if (world != null) {
                    itemDropped = world.dropItem(location, item);
                    itemDropped.setGlowing(true);
                }
            }
        }
    }

    private void cleanDroppedItems() {
        Vector pos1 = battleBoxConfig.getAreaPos1();
        Vector pos2 = battleBoxConfig.getAreaPos2();
        World world = battleBoxConfig.getRightSpawnPoint().getWorld();
        if (world != null) {
            world.getNearbyEntities(new BoundingBox(
                            pos1.getX(),
                            pos1.getY(),
                            pos1.getZ(),
                            pos2.getX(),
                            pos2.getY(),
                            pos2.getZ()))
                    .forEach(entity -> {
                        if (entity instanceof Item) {
                            entity.remove();
                        }
                    });
        }
    }

    private HashMap<Material, Integer> countBlocksInRegion() {
        World world = battleBoxConfig.getLeftPreSpawnPoint().getWorld();
        Vector pos1 = battleBoxConfig.getWoolPos1();
        Vector pos2 = battleBoxConfig.getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        HashMap<Material, Integer> blockCount = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();

                    blockCount.put(material, blockCount.getOrDefault(material, 0) + 1);
                }
            }
        }

        return blockCount;
    }

    private void resetRegionBlocks() {
        World world = battleBoxConfig.getLeftPreSpawnPoint().getWorld();
        Vector pos1 = battleBoxConfig.getWoolPos1();
        Vector pos2 = battleBoxConfig.getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.BLACK_WOOL);
                    BlockState blockState = block.getState();
                    blockState.setType(Material.BLACK_WOOL);
                    blockState.update();
                }
            }
        }
    }

    public boolean notInArea(Location location) {
        return !location.toVector().isInAABB(battleBoxConfig.getAreaPos1(), battleBoxConfig.getAreaPos2());
    }

    @Override
    public Location getSpectatorSpawnLocation() {
        return getBattleBoxConfig().getSpectatorSpawnPoint();
    }

    @Override
    public String getAreaName() {
        return battleBoxConfig.getAreaName();
    }
}
