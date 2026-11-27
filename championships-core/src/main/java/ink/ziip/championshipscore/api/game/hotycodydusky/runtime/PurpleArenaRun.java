package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;

import java.util.*;

final class PurpleArenaRun {
    enum Ability {
        SPEED(Material.SUGAR),
        INVISIBLE(Material.PHANTOM_MEMBRANE),
        DARKNESS(Material.INK_SAC),
        CORRUPTION(Material.BLUE_DYE),
        REACH(Material.BLAZE_ROD),
        CLONE(Material.RABBIT_HIDE);

        final Material icon;
        final String messageKey;

        Ability(Material icon) {
            this.icon = icon;
            messageKey = "hoty-items.skill-" + name().toLowerCase(Locale.ROOT);
        }

        String title() {
            return MessageConfig.codeText(messageKey);
        }
    }

    private record Clone(UUID body, UUID model, UUID owner, int expires) {}

    private static final int SKILL_COOLDOWN_TICKS = 300;
    private static final NamespacedKey REACH = new NamespacedKey("championshipscore", "hoty_reach");
    private final HotyCodyDuskyTeamArea area;
    private final PurpleCorruption corruption;
    private final BasinFootprints tracks;
    private final Map<Integer, PurpleLift> lifts = new HashMap<>();
    private final Map<Integer, PurpleRope> ropes = new HashMap<>();
    private final Map<UUID, Ability> selections = new HashMap<>();
    private final Map<UUID, Integer> cooldown = new HashMap<>(),
            reach = new HashMap<>(),
            spread = new HashMap<>();
    private final Set<UUID> invisible = new HashSet<>();
    private final Map<UUID, Clone> clones = new HashMap<>();

    private int now;

    PurpleArenaRun(HotyCodyDuskyTeamArea area, ChampionshipsCore plugin) {
        this.area = area;
        corruption = new PurpleCorruption(area);
        tracks = new BasinFootprints(plugin);
    }

    Ability selected(Player player) {
        return selected(player.getUniqueId());
    }

    Ability selected(UUID id) {
        return selections.getOrDefault(id, Ability.SPEED);
    }

    boolean isSkillReady(UUID id) {
        return cooldown.getOrDefault(id, 0) <= now;
    }

    boolean cycle(Player p) {
        if (!area.canUseBasinItem(p)) return false;
        Ability[] abilities = Ability.values();
        UUID id = p.getUniqueId();
        Ability selected = abilities[(selected(p).ordinal() + 1) % abilities.length];
        selections.put(id, selected);
        if (area.holdsFish(p)) area.showSkill(p, selected);
        area.invalidatePlayerSidebar(p);
        hint(p, "hoty-items.ability", selected.title());
        return true;
    }

    private static void hint(Player p, String key, Object... args) {
        CoreMessages.sendActionBar(p, MessageConfig.codeText(key, args));
    }

    boolean activate(Player player) {
        if (!area.holdsFish(player) || !area.canUseBasinItem(player) || !area.activeWave())
            return false;
        UUID id = player.getUniqueId();
        int remaining = cooldown.getOrDefault(id, 0) - now;
        if (remaining > 0) {
            hint(player, "hoty-items.cooldown", (remaining + 19) / 20);
            return true;
        }
        Ability ability = selected(player);
        cooldown.put(id, now + SKILL_COOLDOWN_TICKS);
        area.invalidatePlayerSidebar(player);
        player.playSound(player, Sound.BLOCK_CHORUS_FLOWER_GROW, 1, 2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2));
        switch (ability) {
            case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 3));
            case INVISIBLE -> {
                invisible.add(id);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 400, 0));
            }
            case DARKNESS ->
                    nearbyRunners(player, 20, 5)
                            .forEach(
                                    target ->
                                            target.addPotionEffect(
                                                    new PotionEffect(
                                                            PotionEffectType.DARKNESS, 200, 0)));
            case CORRUPTION -> {
                splashAhead(player);
                spread.put(id, now + 100);
            }
            case REACH -> {
                AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
                if (range != null && range.getModifier(REACH) == null) {
                    range.addTransientModifier(
                            new AttributeModifier(
                                    REACH, 3, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                    reach.put(id, now + 200);
                }
            }
            case CLONE -> {
                Zombie body =
                        player.getWorld()
                                .spawn(
                                        player.getLocation(),
                                        Zombie.class,
                                        e -> {
                                            e.setPersistent(false);
                                            e.setShouldBurnInDay(false);
                                            e.setCanPickupItems(false);
                                            e.setAdult();
                                            e.setSilent(true);
                                            e.setInvulnerable(true);
                                            Bukkit.getMobGoals().removeAllGoals(e);
                                            e.addPotionEffect(
                                                    new PotionEffect(
                                                            PotionEffectType.INVISIBILITY,
                                                            -1,
                                                            0,
                                                            false,
                                                            false));
                                            e.setCollidable(false);
                                        });
                try {
                    Mannequin model =
                            player.getWorld()
                                    .spawn(
                                            player.getLocation(),
                                            Mannequin.class,
                                            e -> {
                                                e.setPersistent(false);
                                                e.setImmovable(true);
                                                e.setInvulnerable(true);
                                                e.setCollidable(false);
                                                area.copyAppearance(player, e);
                                            });
                    clones.put(
                            id, new Clone(body.getUniqueId(), model.getUniqueId(), id, now + 250));
                } catch (RuntimeException failure) {
                    body.remove();
                    throw failure;
                }
            }
        }
        return true;
    }

    private void splashAhead(Player holder) {
        UUID source = holder.getUniqueId();
        var targets = new ArrayList<>(nearbyRunners(holder, 100, 2));
        targets.add(holder);
        for (Player target : targets) {
            Location at = target.getLocation();
            corruption.spread(
                    at.add(at.getDirection().multiply(2)),
                    area.arenaOf(source),
                    source,
                    2,
                    10,
                    now);
        }
    }

    void splashOnCapture(Player player) {
        corruption.spread(
                player.getLocation(), area.arenaOf(player.getUniqueId()), null, 3, 15, now);
    }

    private List<Player> nearbyRunners(Player source, double range, int limit) {
        int arena = area.arenaOf(source.getUniqueId());
        Location at = source.getLocation();
        return area.getParticipantUniqueIds().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(
                        p ->
                                area.arenaOf(p.getUniqueId()) == arena
                                        && !area.holdsFish(p)
                                        && area.canUseBasinItem(p)
                                        && p.getLocation().distanceSquared(at) <= range * range)
                .sorted(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(at)))
                .limit(limit)
                .toList();
    }

    boolean recentlyClickedRope(Player p) {
        PurpleRope rope = ropes.get(area.arenaOf(p.getUniqueId()));
        return rope != null && rope.recentlyClicked(p.getUniqueId(), now);
    }

    boolean handleRope(Player p, Entity target, boolean right) {
        PurpleRope rope = ropes.get(area.arenaOf(p.getUniqueId()));
        return rope != null && rope.click(p, target, right, now);
    }

    private Location origin(int index) {
        return area.getGameConfig()
                .getCopyGrid()
                .origin(index)
                .toLocation(Bukkit.getWorld(area.getWorldName()));
    }

    private List<Player> arenaPlayers(int arena) {
        List<Player> players = new ArrayList<>();
        for (UUID id : area.getParticipantUniqueIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null
                    && area.arenaOf(id) == arena
                    && !area.getDeathPlayer().contains(id)
                    && area.isGameplayParticipant(p)
                    && area.isInsideAssignedArena(p)) players.add(p);
        }
        return players;
    }

    boolean pressLift(Player p, Entity target) {
        if (!target.getScoreboardTags().contains("elevator.interaction")
                || !area.canUseBasinItem(p)
                || !target.getWorld().equals(p.getWorld())
                || target.getLocation().distanceSquared(p.getLocation()) > 36) return false;
        int arena = area.arenaOf(p.getUniqueId());
        var lift = lifts.get(arena);
        return lift != null && lift.press(target, arenaPlayers(arena), now);
    }

    void dismount(UUID id) {
        int arena = area.arenaOf(id);
        PurpleLift lift = lifts.get(arena);
        if (lift != null) lift.dismount(id, now);
        PurpleRope rope = ropes.get(arena);
        if (rope != null) rope.dismount(id);
    }

    void tick(int ticks) {
        now = ticks;
        var config = area.getGameConfig();
        Map<Integer, List<Player>> groups = new HashMap<>();
        for (UUID id : area.getParticipantUniqueIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null
                    && !area.getDeathPlayer().contains(id)
                    && area.isGameplayParticipant(p)
                    && area.isInsideAssignedArena(p))
                groups.computeIfAbsent(area.arenaOf(id), k -> new ArrayList<>()).add(p);
        }
        groups.values().removeIf(players -> players.size() < 2);
        if (now % 5 == 0 && area.basinItems() != null)
            groups.values().forEach(players -> players.forEach(area.basinItems().tools::conceal));
        if (now % 20 == 0) {
            var marks = new BasinParticleFrame();
            for (var players : groups.values())
                for (Player player : players)
                    if (area.holdsFish(player)
                            && !player.hasPotionEffect(PotionEffectType.INVISIBILITY))
                        marks.add(player.getLocation().add(0, 1.25, 0));
            marks.show(
                    24,
                    2,
                    (viewer, at) ->
                            viewer.spawnParticle(
                                    Particle.DUST, at, 4, .28, .5, .28, 0, BasinParticles.COD));
        }
        tracks.tick(now, groups);
        for (Clone clone : clones.values()) {
            Player owner = Bukkit.getPlayer(clone.owner());
            if (owner != null
                    && area.holdsFish(owner)
                    && area.canUseBasinItem(owner)
                    && Bukkit.getEntity(clone.body()) instanceof Zombie body)
                tracks.cloneStep(body, owner, now);
        }
        var caught = new ArrayList<UUID>();
        for (var entry : groups.entrySet()) {
            int arena = entry.getKey();
            if (config.getEscapeRope() != null)
                ropes.computeIfAbsent(
                                arena,
                                k ->
                                        new PurpleRope(
                                                area,
                                                origin(k),
                                                config.getEscapeRope(),
                                                config.getQuicksand()))
                        .tick(ticks, entry.getValue(), caught);
            if (ticks % 20 == 0 && area.activeWave() && !config.getMeteorSpawns().isEmpty()) {
                var items = area.basinItems();
                corruption.launch(arena, items == null ? List.of() : items.occupied(arena), now);
            }
            if (config.getElevatorBase() != null)
                lifts.computeIfAbsent(
                                arena, k -> new PurpleLift(origin(k).add(config.getElevatorBase())))
                        .tick(entry.getValue(), ticks);
        }
        if (!caught.isEmpty()) area.capture(caught, true);
        for (UUID id : List.copyOf(cooldown.keySet())) {
            if (now >= cooldown.get(id)) {
                cooldown.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) area.invalidatePlayerSidebar(p);
            }
        }
        for (UUID id : List.copyOf(reach.keySet())) {
            if (now >= reach.get(id)) removeReach(id);
        }
        for (UUID id : List.copyOf(invisible)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                invisible.remove(id);
            }
        }
        if (now % 5 == 0) {
            for (UUID id : List.copyOf(spread.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (now > spread.get(id)
                        || p == null
                        || !area.holdsFish(p)
                        || !area.canUseBasinItem(p)) {
                    spread.remove(id);
                    continue;
                }
                splashAhead(p);
            }
            for (Clone clone : List.copyOf(clones.values())) tickClone(clone);
        }
        corruption.tick(now, groups);
    }

    private void tickClone(Clone clone) {
        Player owner = Bukkit.getPlayer(clone.owner());
        Entity body = Bukkit.getEntity(clone.body());
        if (!(body instanceof Zombie zombie)
                || now >= clone.expires()
                || owner == null
                || !area.holdsFish(owner)
                || !area.canUseBasinItem(owner)) {
            removeClone(clone.owner());
            return;
        }
        Entity model = Bukkit.getEntity(clone.model());
        if (model == null) {
            removeClone(clone.owner());
            return;
        }
        Location at = zombie.getLocation();
        model.teleport(at);
        Player target =
                nearbyRunners(owner, 180, Integer.MAX_VALUE).stream()
                        .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(at)))
                        .orElse(null);
        if (target == null) return;
        zombie.getPathfinder().moveTo(target, 1.3);
        if (target.getLocation().distanceSquared(at) <= 3 && zombie.hasLineOfSight(target)) {
            area.passFish(owner, target, at);
        }
    }

    Entity cloneModel(UUID owner) {
        Clone clone = clones.get(owner);
        return clone == null ? null : Bukkit.getEntity(clone.model());
    }

    private void removeClone(UUID id) {
        Clone clone = clones.remove(id);
        if (clone != null) {
            tracks.forget(clone.body());
            Entity body = Bukkit.getEntity(clone.body());
            if (body != null) body.remove();
            Entity model = Bukkit.getEntity(clone.model());
            if (model != null) model.remove();
        }
    }

    private void removeReach(UUID id) {
        if (reach.remove(id) == null) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) != null)
            p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).removeModifier(REACH);
    }

    void releaseSource(Player p) {
        UUID id = p.getUniqueId();
        removeReach(id);
        spread.remove(id);
        if (invisible.remove(id)) p.removePotionEffect(PotionEffectType.INVISIBILITY);
        removeClone(id);
    }

    void clearExposure(UUID id) {
        corruption.forget(id);
    }

    void startWave() {
        cooldown.clear();
    }

    void forget(UUID id) {
        dismount(id);
        ropes.values().forEach(rope -> rope.forget(id));
        tracks.forget(id);
        corruption.forget(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) releaseSource(p);
    }

    void close() {
        for (UUID id : area.getParticipantUniqueIds()) forget(id);
        corruption.close();
        tracks.close();
        ropes.values().forEach(PurpleRope::close);
        ropes.clear();
        lifts.values().forEach(PurpleLift::close);
        lifts.clear();
    }
}
