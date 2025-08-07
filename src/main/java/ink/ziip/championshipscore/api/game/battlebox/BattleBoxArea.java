package ink.ziip.championshipscore.api.game.battlebox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.battlebox.BBWeaponKitEnum;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxArea extends BaseTeamArea {
    private final ConcurrentHashMap<UUID, BBWeaponKitEnum> playerWeaponKit = new ConcurrentHashMap<>();
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private BukkitTask woolCheckerTask;

    @Override
    public void resetArea() {
        resetRegionBlocks(Material.WHITE_WOOL);
        cleanDroppedItems();
        playerWeaponKit.clear();

        startGamePreparationTask = null;
        startGameProgressTask = null;
        woolCheckerTask = null;
    }

    public BattleBoxArea(ChampionshipsCore plugin, BattleBoxConfig battleBoxConfig) {
        super(plugin, GameTypeEnum.BattleBox, new BattleBoxHandler(plugin), battleBoxConfig);

        getGameConfig().initializeConfiguration(plugin.getFolder());
        getGameHandler().setBattleBoxArea(this);

        getGameHandler().register();

        setGameStageEnum(GameStageEnum.WAITING);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.teleportAllPlayers(getGameConfig().getRightPreSpawnPoint());
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.teleportAllPlayers(getGameConfig().getLeftPreSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        changeCenterWool();

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_START_PREPARATION_TITLE, MessageConfig.BATTLE_BOX_START_PREPARATION_SUBTITLE);

        timer = 20;
        startGamePreparationTask = scheduler.runTaskTimer(plugin, () -> {
            changeLevelForAllGamePlayers(timer);

            if (timer == 0) {
                startGameProgress();
                if (startGamePreparationTask != null)
                    startGamePreparationTask.cancel();
            }

            timer--;
        }, 0, 20L);
    }

    private void changeCenterWool() {
        Material material = Material.WHITE_WOOL;
        if (leftChampionshipTeam != null && rightChampionshipTeam != null && leftChampionshipTeam.getWool().getType() != material && rightChampionshipTeam.getWool().getType() != material) {
            resetRegionBlocks(material);
            return;
        }

        for (String color : Utils.getColorNames()) {
            if (leftChampionshipTeam != null && rightChampionshipTeam != null && !color.equalsIgnoreCase(rightChampionshipTeam.getColorName()) && !color.equalsIgnoreCase(leftChampionshipTeam.getColorName())) {
                material = Material.getMaterial(color.toUpperCase() + "_WOOL");
                if (material != null) {
                    break;
                }
            }
        }
        resetRegionBlocks(material);
    }

    protected void startGameProgress() {
        summonPotions();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE);

        timer = getGameConfig().getTimer() + 5;

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        if (rightChampionshipTeam != null)
            rightChampionshipTeam.teleportAllPlayers(getGameConfig().getRightSpawnPoint());
        if (leftChampionshipTeam != null)
            leftChampionshipTeam.teleportAllPlayers(getGameConfig().getLeftSpawnPoint());
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer > getGameConfig().getTimer()) {
                String countDown = MessageConfig.BATTLE_BOX_COUNT_DOWN
                        .replace("%time%", String.valueOf(timer - getGameConfig().getTimer()));
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
            }

            if (timer == getGameConfig().getTimer()) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_START_TITLE, MessageConfig.BATTLE_BOX_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            changeLevelForAllGamePlayers(timer);
            sendActionBarToAllGameSpectators(MessageConfig.BATTLE_BOX_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            if (timer == 0) {
                if (startGameProgressTask != null)
                    startGameProgressTask.cancel();
            }

            timer--;
        }, 0, 20L);

        woolCheckerTask = scheduler.runTaskTimer(plugin, () -> {
            if (timer == -1) {
                changeLevelForAllGamePlayers(0);
                endGame();
                if (woolCheckerTask != null)
                    woolCheckerTask.cancel();
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

    @Override
    public Location getSpectatorSpawnLocation() {
        return gameConfig.getSpectatorSpawnPoint();
    }

    @Override
    public void endGame() {
        if (getGameStageEnum() == GameStageEnum.WAITING)
            return;

        if (startGamePreparationTask != null)
            startGamePreparationTask.cancel();
        if (startGameProgressTask != null)
            startGameProgressTask.cancel();
        if (woolCheckerTask != null)
            woolCheckerTask.cancel();

        calculatePoints();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.BATTLE_BOX_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, MessageConfig.BATTLE_BOX_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(getLobbyLocation());

        resetPlayerHealthFoodEffectLevelInventory();

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {
        HashMap<Material, Integer> blockCount = countBlocksInRegion();
        int rightTeamWool = 0;
        int leftTeamWool = 0;
        if (rightChampionshipTeam != null)
            rightTeamWool = blockCount.getOrDefault(rightChampionshipTeam.getWool().getType(), 0);
        if (leftChampionshipTeam != null)
            leftTeamWool = blockCount.getOrDefault(leftChampionshipTeam.getWool().getType(), 0);

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

        addPlayerPointsToDatabase();

        sendMessageToAllGamePlayersInActionbarAndMessage(message);
        sendTitleToAllGamePlayers(MessageConfig.BATTLE_BOX_GAME_END_TITLE, message);

        message = MessageConfig.BATTLE_BOX_SHOW_POINTS
                .replace("%team%", rightChampionshipTeam.getColoredName())
                .replace("%team_points%", String.valueOf(getTeamPoints(rightChampionshipTeam)))
                .replace("%rival%", leftChampionshipTeam.getColoredName())
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

            Player killer = player.getKiller();
            if (killer != null) {
                ChampionshipTeam playerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam killerChampionshipTeam = plugin.getTeamManager().getTeamByPlayer(killer);
                if (playerChampionshipTeam != null && killerChampionshipTeam != null) {
                    UUID uuid = killer.getUniqueId();
                    addPlayerPoints(uuid, 15);
                    event.setDeathMessage(null);

                    String message = MessageConfig.BATTLE_BOX_KILL_PLAYER
                            .replace("%player%", playerChampionshipTeam.getColoredColor() + player.getName())
                            .replace("%killer%", killerChampionshipTeam.getColoredColor() + killer.getName());

                    killer.playSound(killer, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1F);

                    sendMessageToAllGamePlayers(message);

                    plugin.getGameApiClient().sendGameEvent(GameTypeEnum.BattleBox, killer, killerChampionshipTeam, "Kill", player.getName());
                }
            }
        }

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            event.getEntity().teleport(getSpectatorSpawnLocation());
            event.getEntity().setGameMode(GameMode.SPECTATOR);
        });

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @Override
    public void handlePlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() != GameStageEnum.PROGRESS) {
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
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.SPECTATOR);
            });
        }

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void teleportPlayerToPreSpawnLocation(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            if (championshipTeam.equals(rightChampionshipTeam)) {
                player.teleport(getGameConfig().getRightPreSpawnPoint());
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                });
                return;
            }
            if (championshipTeam.equals(leftChampionshipTeam)) {
                player.teleport(getGameConfig().getLeftPreSpawnPoint());
                ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                    player.setGameMode(GameMode.ADVENTURE);
                });
                return;
            }
        }

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void giveItemToAllGamePlayers() {
        if (rightChampionshipTeam != null)
            for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
                setWeaponKit(player);
            }
        if (leftChampionshipTeam != null)
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

    public BBWeaponKitEnum getPlayerCurrentWeaponKit(@NotNull Player player) {
        return playerWeaponKit.get(player.getUniqueId());
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
        arrows.setAmount(8);

        inventory.addItem(sword);
        inventory.addItem(bow);
        inventory.addItem(arrows);

        BBWeaponKitEnum type = getPlayerWeaponKit(player);

        if (type == BBWeaponKitEnum.ARMOR) {
            ItemStack item = new ItemStack(Material.GOLDEN_LEGGINGS);
            inventory.addItem(item);
        }
        if (type == BBWeaponKitEnum.SPEED) {
            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                PotionEffect potionEffect = new PotionEffect(PotionEffectType.SPEED, 600, 1);
                potionMeta.addCustomEffect(potionEffect, true);
                potion.setItemMeta(potionMeta);
            }
            inventory.addItem(potion);
        }
        if (type == BBWeaponKitEnum.HEAL) {
            ItemStack potion = new ItemStack(Material.SPLASH_POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            if (potionMeta != null) {
                PotionEffect potionEffect = new PotionEffect(PotionEffectType.INSTANT_HEALTH, 600, 2);
                potionMeta.addCustomEffect(potionEffect, true);
                potion.setItemMeta(potionMeta);
            }
            potion.setAmount(2);
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
        for (String stringLocation : getGameConfig().getPotionSpawnPoints()) {
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

    private HashMap<Material, Integer> countBlocksInRegion() {
        World world = getGameConfig().getLeftPreSpawnPoint().getWorld();
        Vector pos1 = getGameConfig().getWoolPos1();
        Vector pos2 = getGameConfig().getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        HashMap<Material, Integer> blockCount = new HashMap<>();

        if (world == null)
            return blockCount;

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

    private void resetRegionBlocks(Material material) {
        World world = getGameConfig().getLeftPreSpawnPoint().getWorld();
        Vector pos1 = getGameConfig().getWoolPos1();
        Vector pos2 = getGameConfig().getWoolPos2();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        if (world == null)
            return;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(material);
                    BlockState blockState = block.getState();
                    blockState.setType(material);
                    blockState.update();
                }
            }
        }
    }

    @Override
    public BattleBoxConfig getGameConfig() {
        return (BattleBoxConfig) gameConfig;
    }

    @Override
    public BattleBoxHandler getGameHandler() {
        return (BattleBoxHandler) gameHandler;
    }

    @Override
    public String getWorldName() {
        return "battlebox";
    }
}
