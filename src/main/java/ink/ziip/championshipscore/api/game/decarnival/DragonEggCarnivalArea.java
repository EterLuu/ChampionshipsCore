package ink.ziip.championshipscore.api.game.decarnival;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DragonEggCarnivalArea extends BaseTeamArea {
    @Getter
    private int timer;
    private BukkitTask startGamePreparationTask;
    private BukkitTask startGameProgressTask;
    private int round;
    @Getter
    private int rightTeamPoints;
    @Getter
    private int leftTeamPoints;
    private Block dragonEgg;

    public DragonEggCarnivalArea(ChampionshipsCore plugin, DragonEggCarnivalConfig dragonEggCarnivalConfig, boolean firstTime, String areaName) {
        super(plugin, GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalHandler(plugin), dragonEggCarnivalConfig);

        getGameHandler().setDragonEggCarnivalArea(this);
        dragonEggCarnivalConfig.setAreaName(areaName);

        if (!firstTime) {
            loadMap(World.Environment.THE_END);
            getGameHandler().register();
            setGameStageEnum(GameStageEnum.WAITING);
        }
    }

    @Override
    public void resetArea() {
        round = 0;
        rightTeamPoints = 0;
        leftTeamPoints = 0;
        dragonEgg = null;

        startGamePreparationTask = null;
        startGameProgressTask = null;

        loadMap(World.Environment.THE_END);
    }

    @Override
    public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
        if (rightChampionshipTeam != null)
            teleportPlayersToRightSpawnPoints(rightChampionshipTeam);
        if (leftChampionshipTeam != null)
            teleportPlayersToLeftSpawnPoints(leftChampionshipTeam);
        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_START_PREPARATION_SUBTITLE);

        timer = 10;
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

    protected void startGameProgress() {
        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_SUBTITLE);

        timer = -3;

        dragonEgg = getGameConfig().getDragonEggSpawnPoint().getBlock();
        dragonEgg.setType(Material.DRAGON_EGG, true);

        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);
        if (rightChampionshipTeam != null) {
            teleportPlayersToRightSpawnPoints(rightChampionshipTeam);
            addFinalTagToGamePlayers(rightChampionshipTeam);
        }
        if (leftChampionshipTeam != null) {
            teleportPlayersToLeftSpawnPoints(leftChampionshipTeam);
            addFinalTagToGamePlayers(leftChampionshipTeam);
        }
        changeGameModelForAllGamePlayers(GameMode.SURVIVAL);

        resetPlayerHealthFoodEffectLevelInventory();

        giveItemToAllGamePlayers();

        setGameStageEnum(GameStageEnum.PROGRESS);

        startGameProgressTask = scheduler.runTaskTimer(plugin, () -> {

            if (timer < 0) {
                String countDown = MessageConfig.DRAGON_EGG_CARNIVAL_COUNT_DOWN
                        .replace("%time%", String.valueOf(-timer));
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SOON_SUBTITLE, countDown);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 0F);
                changeLevelForAllGamePlayers(-timer);
            } else {
                changeLevelForAllGamePlayers(timer);
            }

            if (timer == 0) {
                sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START);
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_START_SUBTITLE);
                playSoundToAllGamePlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1, 12F);
            }

            if (timer % 10 == 0) {
                giveRandomKitToTeamMembers(rightChampionshipTeam);
                giveRandomKitToTeamMembers(leftChampionshipTeam);
            }


            if (timer == 80 || timer == 90 | timer == 95) {
                String message = MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON.replace("%time%", String.valueOf(100 - timer));
                sendMessageToAllGamePlayers(message);
                sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWN_SOON_SUBTITLE);
            }

            if (timer == 100) {
                dragonEgg.setType(Material.AIR, true);
                sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_DRAGON_EGG_SPAWNED);
                Location location = getGameConfig().getDragonSpawnPoint();
                World world = location.getWorld();
                if (world != null) {
                    EnderDragon dragon = (EnderDragon) world.spawnEntity(location, EntityType.ENDER_DRAGON);
                    dragon.setHealth(60);
                    dragon.setPhase(EnderDragon.Phase.LEAVE_PORTAL);
                }
                giveDragonItemToAllGamePlayers();
            }

            sendActionBarToAllGameSpectators(MessageConfig.DRAGON_EGG_CARNIVAL_ACTION_BAR_COUNT_DOWN.replace("%time%", String.valueOf(timer)));

            timer++;
        }, 0, 20L);
    }

    protected synchronized void endGameInForm(ChampionshipTeam championshipTeam) {
        if (gameStageEnum == GameStageEnum.STOPPING)
            return;

        if (rightChampionshipTeam != null) {
            removeFinalTagToGamePlayers(rightChampionshipTeam);
        }
        if (leftChampionshipTeam != null) {
            removeFinalTagToGamePlayers(leftChampionshipTeam);
        }

        teleportAllPlayers(getLobbyLocation());

        setGameStageEnum(GameStageEnum.STOPPING);

        cleanInventoryForAllGamePlayers();

        changeGameModelForAllGamePlayers(GameMode.SPECTATOR);

        round++;

        if (startGameProgressTask != null)
            startGameProgressTask.cancel();

        if (championshipTeam.equals(rightChampionshipTeam))
            rightTeamPoints += 1;
        if (championshipTeam.equals(leftChampionshipTeam))
            leftTeamPoints += 1;

        if (rightTeamPoints == 3 || leftTeamPoints == 3) {
            endGame();
        } else {
            loadMap(World.Environment.THE_END);
            sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART);
            sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_RESTART_SUBTITLE);

            timer = 10;
            scheduler.runTaskTimer(plugin, (task) -> {
                if (timer == 0) {
                    startGameProgress();
                    task.cancel();
                }

                changeLevelForAllGamePlayers(timer);

                timer--;
            }, 0, 20L);
        }
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

        if (rightChampionshipTeam != null) {
            removeFinalTagToGamePlayers(rightChampionshipTeam);
        }
        if (leftChampionshipTeam != null) {
            removeFinalTagToGamePlayers(leftChampionshipTeam);
        }

        sendMessageToAllGamePlayersInActionbarAndMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END);
        sendTitleToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_TITLE, MessageConfig.DRAGON_EGG_CARNIVAL_GAME_END_SUBTITLE);

        setGameStageEnum(GameStageEnum.END);

        teleportAllPlayers(getLobbyLocation());

        changeGameModelForAllGamePlayers(GameMode.ADVENTURE);

        resetPlayerHealthFoodEffectLevelInventory();

        calculatePoints();

        Bukkit.getPluginManager().callEvent(new TeamGameEndEvent(rightChampionshipTeam, leftChampionshipTeam, this));

        resetGame();
    }

    protected void calculatePoints() {
        if (rightTeamPoints >= 3) {
            if (rightChampionshipTeam != null) {
                Utils.sendMessageToAllPlayers(MessageConfig.DRAGON_EGG_CARNIVAL_WIN.replace("%team%", rightChampionshipTeam.getColoredName()));
                giveGoldenHelmetToTeamPlayers(rightChampionshipTeam);
            }
        } else if (leftTeamPoints >= 3) {
            if (leftChampionshipTeam != null) {
                Utils.sendMessageToAllPlayers(MessageConfig.DRAGON_EGG_CARNIVAL_WIN.replace("%team%", leftChampionshipTeam.getColoredName()));
                giveGoldenHelmetToTeamPlayers(leftChampionshipTeam);
            }
        }
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION) {
            scheduler.runTask(plugin, () -> {
                event.getEntity().spigot().respawn();
                teleportPlayerToSpawnLocation(event.getEntity());
                event.getEntity().setGameMode(GameMode.ADVENTURE);
            });

            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        scheduler.runTask(plugin, () -> {
            event.getEntity().spigot().respawn();
            teleportPlayerToSpawnLocation(event.getEntity());
            event.getEntity().setGameMode(GameMode.SURVIVAL);
        });

        if (getGameStageEnum() == GameStageEnum.PROGRESS) {

            Player assailant = player.getKiller();
            EntityDamageEvent entityDamageEvent = player.getLastDamageCause();

            if (assailant != null) {
                ChampionshipTeam playerTeam = plugin.getTeamManager().getTeamByPlayer(player);
                ChampionshipTeam assailantTeam = plugin.getTeamManager().getTeamByPlayer(assailant);

                if (playerTeam == null || assailantTeam == null)
                    return;

                if (playerTeam.equals(assailantTeam)) {
                    return;
                }

                String message = MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER;

                if (entityDamageEvent != null) {
                    EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                    if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                        message = MessageConfig.DRAGON_EGG_CARNIVAL_KILL_PLAYER_BY_VOID;
                    }
                }

                message = message
                        .replace("%player%", playerTeam.getColoredColor() + player.getName())
                        .replace("%killer%", assailantTeam.getColoredColor() + assailant.getName());

                sendMessageToAllGamePlayers(message);

            } else {

                String message = MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH;

                if (entityDamageEvent != null) {
                    EntityDamageEvent.DamageCause damageCause = entityDamageEvent.getCause();
                    if (damageCause == EntityDamageEvent.DamageCause.VOID) {
                        message = MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_DEATH_BY_VOID;
                    }
                }

                message = message.replace("%player%", player.getName());
                sendMessageToAllGamePlayers(message);
            }
        }
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

        sendMessageToAllGamePlayers(MessageConfig.DRAGON_EGG_CARNIVAL_PLAYER_LEAVE.replace("%player%", player.getName()));
    }

    @Override
    public void handlePlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (notAreaPlayer(player)) {
            return;
        }

        if (getGameStageEnum() == GameStageEnum.PREPARATION || getGameStageEnum() == GameStageEnum.PROGRESS) {
            teleportPlayerToSpawnLocation(player);
            return;
        }
        if (getGameStageEnum() == GameStageEnum.STOPPING || getGameStageEnum() == GameStageEnum.WAITING || getGameStageEnum() == GameStageEnum.END) {
            player.getInventory().clear();
            player.teleport(CCConfig.LOBBY_LOCATION);
            ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
            championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                player.setGameMode(GameMode.ADVENTURE);
            });
            return;
        }

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void teleportPlayersToRightSpawnPoints(ChampionshipTeam championshipTeam) {
        Iterator<String> stringIterator = getGameConfig().getRightSpawnPoints().iterator();
        for (Player player : championshipTeam.getOnlinePlayers()) {
            if (!stringIterator.hasNext())
                stringIterator = getGameConfig().getRightSpawnPoints().iterator();
            player.teleport(Utils.getLocation(stringIterator.next()));
        }
    }

    private void teleportPlayersToLeftSpawnPoints(ChampionshipTeam championshipTeam) {
        Iterator<String> stringIterator = getGameConfig().getLeftSpawnPoints().iterator();
        for (Player player : championshipTeam.getOnlinePlayers()) {
            if (!stringIterator.hasNext())
                stringIterator = getGameConfig().getLeftSpawnPoints().iterator();
            player.teleport(Utils.getLocation(stringIterator.next()));
        }
    }

    private void giveItemToAllGamePlayers() {
        if (rightChampionshipTeam != null)
            for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
                giveItemToPlayer(player);
                giveEffectToPlayer(player);
            }
        if (leftChampionshipTeam != null)
            for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
                giveItemToPlayer(player);
                giveEffectToPlayer(player);
            }
    }

    private void giveDragonItemToAllGamePlayers() {
        if (rightChampionshipTeam != null)
            for (Player player : rightChampionshipTeam.getOnlinePlayers()) {
                giveDragonPhaseItemToPlayer(player);
            }
        if (leftChampionshipTeam != null)
            for (Player player : leftChampionshipTeam.getOnlinePlayers()) {
                giveDragonPhaseItemToPlayer(player);
            }
        clearEffectsForAllGamePlayers();
    }

    public void teleportPlayerToSpawnLocation(Player player) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(player);
        if (championshipTeam != null) {
            if (championshipTeam.equals(rightChampionshipTeam)) {
                player.teleport(Utils.getLocation(getGameConfig().getRightSpawnPoints().get(0)));
                if (getGameStageEnum() == GameStageEnum.PREPARATION) {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.ADVENTURE);
                    });
                } else {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.SURVIVAL);
                    });
                }
                return;
            }
            if (championshipTeam.equals(leftChampionshipTeam)) {
                player.teleport(Utils.getLocation(getGameConfig().getLeftSpawnPoints().get(0)));
                if (getGameStageEnum() == GameStageEnum.PREPARATION) {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.ADVENTURE);
                    });
                } else {
                    ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
                    championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
                        player.setGameMode(GameMode.SURVIVAL);
                    });
                }
                return;
            }
        }

        player.teleport(getSpectatorSpawnLocation());
        ChampionshipsCore championshipsCore = ChampionshipsCore.getInstance();
        championshipsCore.getServer().getScheduler().runTask(championshipsCore, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private void giveEffectToPlayer(Player player) {
        PotionEffect potionEffect = new PotionEffect(PotionEffectType.HEALTH_BOOST, PotionEffect.INFINITE_DURATION, 1);
        player.addPotionEffect(potionEffect);
    }

    private void giveGoldenHelmetToTeamPlayers(ChampionshipTeam championshipTeam) {
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        for (Player player : championshipTeam.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            inventory.setHelmet(helmet.clone());
        }
    }

    private void giveRandomKitToTeamMembers(ChampionshipTeam championshipTeam) {
        for (Player player : championshipTeam.getOnlinePlayers()) {
            giveRandomKitToPlayer(player);
        }
    }

    private void giveRandomKitToPlayer(Player player) {
        List<ItemStack> kits = new ArrayList<>(getGameConfig().getKits());

        try {
            player.getInventory().addItem(kits.get(ThreadLocalRandom.current().nextInt(kits.size())).clone());

            player.sendMessage(MessageConfig.DRAGON_EGG_CARNIVAL_GAIN_KIT);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void giveDragonPhaseItemToPlayer(Player player) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemStack bow = new ItemStack(Material.BOW);
        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemStack cookedBeef = new ItemStack(Material.COOKED_BEEF);
        cookedBeef.setAmount(64);

        bow.addEnchantment(Enchantment.INFINITY, 1);
        bow.addEnchantment(Enchantment.POWER, 2);

        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        inventory.addItem(sword, bow, bucket, arrow, cookedBeef);
    }

    private void addFinalTagToGamePlayers(ChampionshipTeam championshipTeam) {
        if (championshipTeam != null) {
            for (Player player : championshipTeam.getOnlinePlayers()) {
                player.addScoreboardTag("final");
            }
        }
    }

    private void removeFinalTagToGamePlayers(ChampionshipTeam championshipTeam) {
        if (championshipTeam != null) {
            for (Player player : championshipTeam.getOnlinePlayers()) {
                player.removeScoreboardTag("final");
            }
        }
    }

    private void giveItemToPlayer(Player player) {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        helmet.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        leggings.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        leggings.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        boots.addUnsafeEnchantment(Enchantment.PROTECTION, 5);
        boots.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);

        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
        pickaxe.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);

        ItemStack torch = new ItemStack(Material.TORCH);
        torch.setAmount(64);

        ItemStack cobweb = new ItemStack(Material.COBWEB);
        cobweb.setAmount(5);

        ItemStack cookedBeef = new ItemStack(Material.COOKED_BEEF);
        cookedBeef.setAmount(64);

        ItemStack stick = new ItemStack(Material.STICK);
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 5);

        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(helmet.clone());
        inventory.setChestplate(elytra.clone());
        inventory.setLeggings(leggings.clone());
        inventory.setBoots(boots.clone());
        inventory.addItem(pickaxe.clone());
        inventory.addItem(torch.clone());
        inventory.addItem(bucket.clone());
        inventory.addItem(cobweb.clone());
        inventory.addItem(cookedBeef.clone());
        inventory.addItem(stick.clone());
    }

    public String getWorldName() {
        return "decarnival_" + gameConfig.getAreaName();
    }

    @Override
    public DragonEggCarnivalConfig getGameConfig() {
        return (DragonEggCarnivalConfig) gameConfig;
    }

    @Override
    public DragonEggCarnivalHandler getGameHandler() {
        return (DragonEggCarnivalHandler) gameHandler;
    }
}
