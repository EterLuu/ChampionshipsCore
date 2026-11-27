package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.BasinLoot;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.util.glow.GlowingEntities;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.util.*;

final class BasinTools {
    private record Owned(UUID owner, int arena, int expires) {}

    private record Shot(BasinLoot kind, Owned lifetime) {}

    private static final NamespacedKey SHRUNK =
            new NamespacedKey("championshipscore", "basin_shrink");
    private static final NamespacedKey SCANS =
            new NamespacedKey("championshipscore", "basin_scans");
    private final HotyCodyDuskyTeamArea area;
    private final Map<UUID, Integer> shrunk = new HashMap<>();
    private final BasinPrisons prisons = new BasinPrisons();
    private final BasinHorn horn;

    private record Reveal(UUID viewer, UUID target) {}

    private final GlowingEntities glow;
    private final Map<Reveal, Integer> reveals = new HashMap<>();
    private final Set<UUID> armedTotems = new HashSet<>();
    private int tick;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final Map<UUID, Owned> decoys = new HashMap<>();
    private final BasinSnowSmoke smoke = new BasinSnowSmoke();
    private int second;

    BasinTools(HotyCodyDuskyTeamArea area, GlowingEntities glow) {
        this.glow = glow;
        this.area = area;
        this.horn = new BasinHorn(this::hornTargets);
    }

    private Owned owned(Player player, int duration) {
        return new Owned(
                player.getUniqueId(), area.arenaOf(player.getUniqueId()), tick + duration * 20);
    }

    private boolean active(Owned object) {
        Player owner = Bukkit.getPlayer(object.owner());
        return tick < object.expires()
                && owner != null
                && area.canUseBasinItem(owner)
                && object.arena() == area.arenaOf(owner.getUniqueId());
    }

    private void reveal(Player viewer, Player target, int duration) {
        if (viewer.equals(target)) return;
        var key = new Reveal(viewer.getUniqueId(), target.getUniqueId());
        if (!reveals.containsKey(key)) glow.setGlowing(target, viewer);
        reveals.merge(key, tick + duration, Math::max);
    }

    private void clearReveals(java.util.function.Predicate<Reveal> remove) {
        reveals.keySet()
                .removeIf(
                        key -> {
                            if (!remove.test(key)) return false;
                            Player viewer = Bukkit.getPlayer(key.viewer()),
                                    target = Bukkit.getPlayer(key.target());
                            if (viewer != null && target != null) glow.unsetGlowing(target, viewer);
                            return true;
                        });
    }

    private boolean armTotem(Player p) {
        var inv = p.getInventory();
        boolean armed = armedTotems.add(p.getUniqueId());
        if (armed) {
            inv.setItemInMainHand(null);
            area.invalidatePlayerSidebar(p);
        }
        CoreMessages.sendActionBar(
                p,
                MessageConfig.codeText(
                        armed ? "hoty-items.totem-armed" : "hoty-items.totem-already"));
        return true;
    }

    boolean hasArmedTotem(UUID player) {
        return armedTotems.contains(player);
    }

    boolean spendProtection(UUID player) {
        return armedTotems.remove(player);
    }

    void revealForViewer(Player viewer, Player target, int durationTicks) {
        reveal(viewer, target, durationTicks);
    }

    void clearRevealsForViewer(UUID viewer) {
        clearReveals(key -> key.viewer().equals(viewer));
    }

    void tickEffects(int now) {
        tick = now;
        clearReveals(key -> now >= reveals.get(key));
        decoys.entrySet()
                .removeIf(
                        entry -> {
                            if (tick < entry.getValue().expires()) return false;
                            removeEntity(entry.getKey());
                            return true;
                        });
        prisons.tick(now);
        smoke.tick(now);
        horn.tick(now);
    }

    private List<LivingEntity> hornTargets(int arena) {
        var players = targets(arena);
        List<LivingEntity> targets = new ArrayList<>(players);
        for (Player player : players) {
            if (area.terrain().cloneModel(player.getUniqueId()) instanceof Mannequin model)
                targets.add(model);
        }
        for (var entry : decoys.entrySet()) {
            if (entry.getValue().arena() == arena
                    && Bukkit.getEntity(entry.getKey()) instanceof Mannequin decoy)
                targets.add(decoy);
        }
        return targets;
    }

    void tick() {
        second++;
        for (UUID id : List.copyOf(shrunk.keySet())) if (second >= shrunk.get(id)) restoreSize(id);
        shots.entrySet().removeIf(entry -> expire(entry.getKey(), entry.getValue().lifetime()));
        decoys.entrySet().removeIf(entry -> expire(entry.getKey(), entry.getValue()));
    }

    private List<Player> targets(int arena) {
        return area.getParticipantUniqueIds().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> area.arenaOf(p.getUniqueId()) == arena && area.canUseBasinItem(p))
                .toList();
    }

    private boolean expire(UUID id, Owned owned) {
        if (active(owned) && Bukkit.getEntity(id) != null) return false;
        removeEntity(id);
        return true;
    }

    private static void removeEntity(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    void forget(UUID id) {
        clearReveals(key -> key.viewer().equals(id) || key.target().equals(id));
        armedTotems.remove(id);
        restoreSize(id);
        prisons.release(id);
        horn.forget(id);
        decoys.entrySet()
                .removeIf(
                        e -> {
                            if (!e.getValue().owner().equals(id)) return false;
                            removeEntity(e.getKey());
                            return true;
                        });
        shots.entrySet()
                .removeIf(
                        e -> {
                            if (!e.getValue().lifetime().owner().equals(id)) return false;
                            removeEntity(e.getKey());
                            return true;
                        });
    }

    void close() {
        for (UUID id : List.copyOf(shrunk.keySet())) restoreSize(id);
        clearReveals(key -> true);
        armedTotems.clear();
        prisons.close();
        horn.close();
        shots.keySet().forEach(BasinTools::removeEntity);
        shots.clear();
        decoys.keySet().forEach(BasinTools::removeEntity);
        decoys.clear();
        smoke.close();
    }

    boolean isJailed(Player p) {
        return prisons.contains(p) && area.canUseBasinItem(p);
    }

    boolean useKey(Player attacker, Entity clicked) {
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (BasinLoot.of(item) != BasinLoot.KEY
                || !(clicked instanceof Player victim)
                || attacker.equals(victim)) return false;
        if (!area.activeWave()
                || !area.canUseBasinItem(attacker)
                || !area.canUseBasinItem(victim)
                || area.arenaOf(attacker.getUniqueId()) != area.arenaOf(victim.getUniqueId())
                || attacker.getWorld() != victim.getWorld()
                || !attacker.canSee(victim)
                || !attacker.hasLineOfSight(victim)
                || attacker.getEyeLocation().distanceSquared(victim.getEyeLocation()) > 25)
            return true;
        prisons.place(victim, tick);
        victim.getWorld()
                .spawnParticle(
                        Particle.DUST,
                        victim.getLocation().add(0, 1, 0),
                        24,
                        0.4,
                        0.8,
                        0.4,
                        0,
                        BasinParticles.COD);
        consume(attacker, item);
        return true;
    }

    boolean isDecoy(Entity entity) {
        return decoys.containsKey(entity.getUniqueId());
    }

    boolean decoyHit(Player attacker, Entity entity) {
        Owned decoy = decoys.get(entity.getUniqueId());
        if (decoy == null) return false;
        if (active(decoy)
                && area.canUseBasinItem(attacker)
                && area.arenaOf(attacker.getUniqueId()) == decoy.arena()
                && area.holdsFish(attacker)) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
            decoys.remove(entity.getUniqueId());
            entity.remove();
        }
        return true;
    }

    boolean use(Player player, ItemStack stack) {
        BasinLoot kind = BasinLoot.of(stack);
        if (kind == null) return false;
        if (!area.canUseBasinItem(player)) return true;
        if (!area.activeWave()
                && (kind == BasinLoot.CLOCK
                        || kind == BasinLoot.COMPASS
                        || kind == BasinLoot.KEY
                        || kind == BasinLoot.DECOY
                        || kind == BasinLoot.HORN
                        || kind == BasinLoot.TRIDENT
                        || kind == BasinLoot.BAIT)) return true;
        if (isJailed(player)
                && (kind == BasinLoot.CHORUS || kind == BasinLoot.PEARL || kind == BasinLoot.WIND))
            return true;
        if (player.hasCooldown(stack)) return true;
        if (kind == BasinLoot.HORN && stack.getType() == Material.GOAT_HORN) {
            ItemStack hornStack = kind.stack();
            hornStack.setAmount(stack.getAmount());
            player.getInventory().setItemInMainHand(hornStack);
            player.startUsingItem(EquipmentSlot.HAND);
            return true;
        }
        int arena = area.arenaOf(player.getUniqueId());
        Location at = player.getLocation();
        switch (kind) {
            case VEST -> {
                area.wearVest(player);
                return true;
            }
            case TRIDENT, BAIT, MUSHROOM, SMOKE, CHORUS, HORN -> {
                return false; // Native charge, throw or consume animation.
            }
            case TOTEM -> {
                return armTotem(player);
            }
            case KEY -> {
                useKey(player, player.getTargetEntity(4));
                return true;
            }
            case CLOCK -> {
                if (!area.freezeClock(player)) return true;
            }
            case COMPASS -> {
                Player target =
                        targets(arena).stream()
                                .filter(t -> area.holdsFish(t) != area.holdsFish(player))
                                .min(
                                        Comparator.comparingDouble(
                                                t -> t.getLocation().distanceSquared(at)))
                                .orElse(null);
                var meta = (CompassMeta) stack.getItemMeta();
                int scans =
                        meta.getPersistentDataContainer()
                                        .getOrDefault(SCANS, PersistentDataType.INTEGER, 0)
                                + 1;
                if (scans > 4) {
                    consume(player, stack);
                    return true;
                }
                if (target == null) return true;
                meta.setLodestone(target.getLocation());
                meta.setLodestoneTracked(false);
                meta.getPersistentDataContainer().set(SCANS, PersistentDataType.INTEGER, scans);
                stack.setItemMeta(meta);
                reveal(player, target, 40);
                player.playSound(player, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1, 1);
                player.setCooldown(stack, kind.cooldownTicks());
                if (scans == 4) consume(player, stack);
                else player.getInventory().setItemInMainHand(stack);
                CoreMessages.sendActionBar(
                        player,
                        MessageConfig.codeText(
                                "hoty-items.compass-result", target.getName(), 4 - scans));
                return true;
            }
            case DECOY -> {
                Mannequin doublePlayer =
                        at.getWorld()
                                .spawn(
                                        at,
                                        Mannequin.class,
                                        e -> {
                                            e.setPersistent(false);
                                            e.setAI(false);
                                            e.setImmovable(true);
                                            e.setSilent(true);
                                            e.setRemoveWhenFarAway(false);
                                            e.setInvulnerable(true);
                                            area.copyAppearance(player, e);
                                        });
                decoys.put(doublePlayer.getUniqueId(), owned(player, 15));
            }
            case PEARL, WIND -> {
                Snowball projectile = player.launchProjectile(Snowball.class);
                projectile.setPersistent(false);
                projectile.setItem(stack.asOne());
                shots.put(projectile.getUniqueId(), new Shot(kind, owned(player, 10)));
            }
        }
        if (kind.cooldownTicks() > 0) player.setCooldown(stack, kind.cooldownTicks());
        consume(player, stack);
        return true;
    }

    void finishConsumption(Player player, BasinLoot kind) {
        if (kind == BasinLoot.HORN) {
            horn.blow(player.getUniqueId(), area.arenaOf(player.getUniqueId()), tick);
        } else if (kind == BasinLoot.BAIT) {
            restoreSize(player.getUniqueId());
            clearReveals(key -> key.target().equals(player.getUniqueId()));
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.SPEED);
            area.shedFish(player);
            player.playSound(player, Sound.BLOCK_BELL_RESONATE, 1, 1.5f);
        } else if (kind == BasinLoot.MUSHROOM) {
            var scale = player.getAttribute(Attribute.SCALE);
            if (scale == null) return;
            if (scale.getModifier(SHRUNK) == null)
                scale.addTransientModifier(
                        new AttributeModifier(
                                SHRUNK, -0.5, AttributeModifier.Operation.ADD_NUMBER));
            shrunk.put(player.getUniqueId(), second + 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
        }
    }

    void conceal(Player player) {
        smoke.conceal(player);
    }

    void trackThrown(Player owner, Projectile projectile, BasinLoot kind) {
        if (projectile instanceof Trident trident)
            trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        projectile.setPersistent(false);
        shots.put(projectile.getUniqueId(), new Shot(kind, owned(owner, 60)));
    }

    private static void consume(Player player, ItemStack stack) {
        if (stack.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInMainHand(stack);
        }
    }

    boolean isGameTrident(Entity entity) {
        return entity instanceof Trident && shots.containsKey(entity.getUniqueId());
    }

    boolean hit(
            Projectile projectile,
            Entity target,
            org.bukkit.block.Block block,
            org.bukkit.block.BlockFace face) {
        Shot shot = shots.get(projectile.getUniqueId());
        if (shot == null) return false;
        if (shot.kind() == BasinLoot.TRIDENT && target != null) {
            if (target instanceof Player victim && active(shot.lifetime())) {
                Player owner = Bukkit.getPlayer(shot.lifetime().owner());
                if (owner != null) area.passFish(owner, victim, projectile.getLocation());
            }
            // Vanilla marks the trident as spent on impact and lets it fall until it hits a block.
            return false;
        }
        shots.remove(projectile.getUniqueId());
        Location at = projectile.getLocation();
        if (block != null && face != null)
            at = block.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
        projectile.remove();
        if (shot.kind() == BasinLoot.TRIDENT
                || !active(shot.lifetime())
                || !area.withinArena(shot.lifetime().arena(), at)) return true;
        Player owner = Bukkit.getPlayer(shot.lifetime().owner());
        if (owner == null) return true;
        switch (shot.kind()) {
            case PEARL -> {
                if (!isJailed(owner)) land(owner, at);
            }
            case SMOKE -> {
                smoke.land(
                        at.getBlock(),
                        tick,
                        b -> area.withinArena(shot.lifetime().arena(), b.getLocation()));
                at.getWorld().playSound(at, Sound.BLOCK_POWDER_SNOW_PLACE, 1, 0.7f);
            }
            case WIND -> {
                at.getWorld().spawnParticle(Particle.GUST, at, 1);
                at.getWorld()
                        .spawnParticle(
                                Particle.DUST, at, 12, 0.6, 0.4, 0.6, 0, BasinParticles.SPRAY);
                for (Player p : targets(shot.lifetime().arena())) {
                    Vector away = p.getLocation().toVector().subtract(at.toVector());
                    if (away.lengthSquared() > 16 || isJailed(p)) continue;
                    away.setY(0);
                    if (away.lengthSquared() > 0.01) away.normalize().multiply(0.9);
                    p.setVelocity(away.setY(0.85));
                }
            }
            default ->
                    throw new IllegalStateException("Unexpected Basin projectile " + shot.kind());
        }
        return true;
    }

    private boolean land(Player p, Location candidate) {
        Location dest = candidate.toBlockLocation().add(0.5, 0, 0.5);
        for (int i = 0; i < 14; i++, dest.subtract(0, 1, 0)) {
            if (!area.insideBasin(area.arenaOf(p.getUniqueId()), dest)) return false;
            if (!dest.getBlock().isPassable()
                    || !dest.clone().add(0, 1, 0).getBlock().isPassable()
                    || !dest.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            dest.setDirection(p.getLocation().getDirection());
            return p.teleport(dest);
        }
        return false;
    }

    private void restoreSize(UUID id) {
        if (shrunk.remove(id) == null) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.getAttribute(Attribute.SCALE) != null)
            player.getAttribute(Attribute.SCALE).removeModifier(SHRUNK);
    }
}
