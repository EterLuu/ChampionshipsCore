package ink.ziip.championshipscore.api.game.frostbite;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/** Four spatially isolated solo arenas under one atomic CC round and settlement owner. */
public final class FrostbiteArea extends BaseMultiTeamGameInstance {
    private record Shot(UUID owner, FrostbiteItem item, int arena, int expires) {}
    private static final class Prop {
        final UUID owner; final FrostbiteItem type; final int arena; final Location location; final ArmorStand entity;
        final int expires; int next; int charges;
        Prop(UUID owner, FrostbiteItem type, int arena, Location location, ArmorStand entity, int expires, int charges) {
            this.owner=owner; this.type=type; this.arena=arena; this.location=location; this.entity=entity;
            this.expires=expires; this.charges=charges;
        }
    }
    private record Zone(UUID owner, FrostbiteItem type, int arena, Location center, int until) {}
    private record Burst(UUID owner, int next, int remaining) {}
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final List<Prop> props = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();
    private final Map<UUID, Location> freezeLocations = new HashMap<>();
    private final Map<UUID, Integer> meleeCooldown = new HashMap<>();
    private final Map<UUID, Integer> useCooldown = new HashMap<>();
    private final Map<UUID, Integer> invisibleUntil = new HashMap<>();
    private final Map<UUID, Boolean> collisionBefore = new HashMap<>();
    private final List<ItemDisplay> pickupDisplays = new ArrayList<>();
    private List<Location> pickups = List.of();
    private int[] pickupReady = new int[0];
    private final Random random = new Random();
    private final NamespacedKey itemKey;
    private FrostbiteRound round;
    private BukkitTask tickTask;
    private BukkitTask timerTask;
    private int tick;
    private int timer;
    private boolean ending;
    private boolean ownTeleport;

    public FrostbiteArea(ChampionshipsCore plugin, FrostbiteConfig config, boolean firstTime, String name) {
        super(plugin, GameTypeEnum.FrostbiteFrenzy, new FrostbiteHandler(plugin), config);
        itemKey = new NamespacedKey(plugin, "frostbite_item");
        getGameHandler().setArea(this);
        config.setAreaName(name);
        if (firstTime) { getGameHandler().register(); setGameStageEnum(GameStageEnum.WAITING); }
    }
    public void preloadMap() { loadPublishedMapOrDraft(World.Environment.NORMAL); }
    @Override public boolean tryStartGame(List<ChampionshipTeam> teams) {
        if (!validRoster(teams)) return false;
        return super.tryStartGame(teams);
    }
    @Override public boolean tryStartGame(List<ChampionshipTeam> teams, List<UUID> players) {
        if (!validRoster(teams) || players == null || !new HashSet<>(players).equals(
                teams.stream().flatMap(t -> t.getMembers().stream()).collect(java.util.stream.Collectors.toSet()))) return false;
        return super.tryStartGame(teams, players);
    }
    private boolean validRoster(List<ChampionshipTeam> teams) {
        if (teams == null || teams.size() < 2 || teams.size() > 16 || teams.stream().anyMatch(t -> t == null || t.getMembers().size() != 4)) {
            logGame(Level.WARNING, "参赛", "霜冻狂潮需要2–16支队伍，每队恰好4人"); return false;
        }
        return true;
    }
    @Override protected Collection<Location> getStartPreloadLocations() {
        List<Location> locations = new ArrayList<>();
        for (int a=0; a<4; a++) {
            locations.addAll(getGameConfig().spawns(a));
            for (String p : getGameConfig().getItemPoints()) locations.add(getGameConfig().point(p,a));
        }
        return locations;
    }
    @Override public void startGamePreparation() {
        setGameStageEnum(GameStageEnum.PREPARATION);
        startGameIntroduction(this::prepareRound);
    }
    private void prepareRound() {
        try {
            getGameConfig().validate();
            int index = isEventRun() ? plugin.getScheduleManager().getFrostbiteScheduleManager().getSubRound()-1 : 0;
            var ordered = gameTeams.stream().sorted(Comparator.comparingInt(ChampionshipTeam::getId)).toList();
            round = new FrostbiteRound(ordered.stream().map(t -> t.getMembers().stream().sorted().toList()).toList(), index);
            for (UUID id : round.seats().keySet()) if (Bukkit.getPlayer(id) == null)
                throw new IllegalStateException("开局需要全部参赛者在线");
            tick=0; ending=false;
            resetPlayerHealthFoodEffectLevelInventory();
            changeGameModelForAllGamePlayers(GameMode.ADVENTURE);
            for (UUID id : round.seats().keySet()) {
                Player player = Bukkit.getPlayer(id);
                collisionBefore.put(id, player.isCollidable()); player.setCollidable(false);
                spawn(player, false);
                player.sendMessage(LegacyText.component("&b霜冻狂潮 &f第 " + (round.seats().get(id).arena()+1)
                        + " 场地｜冻结5秒后失温，击杀得分；四轮累加队伍成绩。"));
            }
            createPickups();
            startFinalCountdown("霜冻狂潮", "&b霜冻狂潮", "&f冻结敌人，争夺补给！", this::begin);
        } catch (RuntimeException failure) {
            logGame(Level.SEVERE, "准备", failure.getMessage()); abortAndReset();
        }
    }
    private void begin() {
        round.seats().keySet().forEach(id -> round.heat(id, getGameConfig().getHeatSeconds()*20));
        tickTask=scheduler.runTaskTimer(plugin,this::tickRound,1,1);
        timerTask=startRemainingTimer(getGameConfig().getTimer(), remaining -> {
            timer=remaining;
            updateGameTimerBossBar("&b霜冻狂潮 &f" + remaining/60 + ":" + String.format(java.util.Locale.ROOT,"%02d",remaining%60),remaining,getGameConfig().getTimer());
        },this::endGame);
    }
    public boolean participant(Player player) { return !notAreaPlayer(player) && getGameStageEnum()!=GameStageEnum.WAITING; }
    public boolean playing(Player player) { return getGameStageEnum()==GameStageEnum.PROGRESS && round!=null && round.active(player.getUniqueId()) && player.getGameMode()!=GameMode.SPECTATOR; }
    private boolean acting(Player player) { return playing(player) && round.canAct(player.getUniqueId()); }
    private int arena(UUID id) { return round.seats().get(id).arena(); }
    private List<Player> opponents(UUID id, Location point, double radius) {
        return round.seats().keySet().stream().filter(other -> round.enemies(id,other)).map(Bukkit::getPlayer)
                .filter(Objects::nonNull).filter(this::playing).filter(p -> p.getWorld().equals(point.getWorld()) && p.getLocation().distanceSquared(point)<=radius*radius).toList();
    }
    private void tickRound() {
        if (getGameStageEnum()!=GameStageEnum.PROGRESS || round==null) return;
        tick++;
        for (UUID id : round.expired(tick)) { award(round.die(id)); respawn(id); }
        for (UUID id : round.seats().keySet()) {
            if (!round.active(id)) continue;
            Player p=Bukkit.getPlayer(id);
            if (p==null) { award(round.leave(id)); cleanupPlayer(id); continue; }
            if (!getGameConfig().contains(p.getLocation(),arena(id)) || p.getLocation().getY()<getGameConfig().getArenaMin().getY()+1
                    || p.isInWater() || p.isInLava()) {
                award(round.die(id)); respawn(id); continue;
            }
            if (invisibleUntil.getOrDefault(id, Integer.MAX_VALUE)<=tick) reveal(p);
            if (tick%5==0) {
                var frozen=round.freezeState(id);
                if (frozen!=null) {
                    p.setVelocity(new Vector()); p.setFreezeTicks(130);
                    p.sendActionBar(LegacyText.component("&b被冻结 &f"+String.format(java.util.Locale.ROOT,"%.1f",(frozen.until()-tick)/20D)+"秒"
                            +(camp(id)!=null?" &6按 F 返回营火":"")));
                } else {
                    p.sendActionBar(LegacyText.component("&f击杀 &b"+round.kills(id)+" &7｜ &f场地 "+(arena(id)+1)
                            +(round.heated(id,tick)?" &6保温中":"")));
                    collect(p);
                }
            }
        }
        for (var it=shots.entrySet().iterator();it.hasNext();) {
            var entry=it.next(); Entity entity=Bukkit.getEntity(entry.getKey());
            if (entity==null || !entity.isValid() || entry.getValue().expires<=tick) { if(entity!=null)entity.remove(); it.remove(); }
        }
        for (Burst burst : List.copyOf(bursts)) if (burst.next<=tick) {
            bursts.remove(burst); Player p=Bukkit.getPlayer(burst.owner);
            if (p!=null && acting(p)) {
                launch(p,FrostbiteItem.AVALANCHE,p.getLocation().getDirection().multiply(.8));
                if (burst.remaining>1) bursts.add(new Burst(burst.owner,tick+3,burst.remaining-1));
            }
        }
        tickProps();
        for (Zone zone : List.copyOf(zones)) {
            if (zone.until<=tick) {zones.remove(zone);continue;}
            if (tick%5!=0) continue;
            if (zone.type==FrostbiteItem.AVALANCHE) {
                for(Player p:opponents(zone.owner,zone.center,1.7)) if(acting(p))p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,10,2,true,false));
            } else {
                Player owner=Bukkit.getPlayer(zone.owner);
                if(owner!=null && playing(owner) && round.freezeState(zone.owner)!=null && owner.getWorld().equals(zone.center.getWorld())
                        && owner.getLocation().distanceSquared(zone.center)<=16) {thaw(owner);zones.remove(zone);}
            }
            if(tick%10==0) zone.center.getWorld().spawnParticle(zone.type==FrostbiteItem.AVALANCHE?Particle.SNOWFLAKE:Particle.FLAME,zone.center,4,.8,.2,.8,0);
        }
        if(tick%20==0) for(int i=0;i<pickups.size();i++) if(pickupReady[i]>0 && pickupReady[i]<=tick) {
            pickupDisplays.get(i).setItemStack(new ItemStack(Material.GOLD_BLOCK));pickupReady[i]=0;
        }
        if(round.seats().keySet().stream().noneMatch(round::active)) endGame();
    }
    private void award(UUID killer) { if(killer!=null) addPlayerPoints(killer,getGameConfig().getPointsPerKill()); }
    private void respawn(UUID id) {
        Player p=Bukkit.getPlayer(id); if(p==null)return;
        clearCombat(p); removeOwnedProps(id); spawn(p,true);
        p.sendTitle("&b重新出发".replace('&','§'),"失温后快速重生，短暂保温保护",0,20,5);
    }
    private void spawn(Player p, boolean heat) {
        int a=arena(p.getUniqueId()); List<Location> candidates=new ArrayList<>(getGameConfig().spawns(a)); Collections.shuffle(candidates,random);
        List<Location> enemies=round.seats().keySet().stream().filter(id->round.enemies(p.getUniqueId(),id))
                .map(Bukkit::getPlayer).filter(Objects::nonNull).filter(other->getGameConfig().contains(other.getLocation(),a)).map(Player::getLocation).toList();
        Location selected=null;double best=-1;
        for(Location candidate:candidates) {
            if(!safe(candidate)) continue;
            double nearest=enemies.stream().mapToDouble(e->e.distanceSquared(candidate)).min().orElse(1E9);
            // Sequential spawns must also spread out before all players have entered this arena.
            if(nearest>best) {best=nearest;selected=candidate;}
        }
        if(selected==null) throw new IllegalStateException("场地没有安全重生点");
        teleport(p,selected);p.setVelocity(new Vector());p.setFallDistance(0);p.setHealth(20);p.setFoodLevel(20);
        p.setGameMode(GameMode.ADVENTURE);
        if(heat)round.heat(p.getUniqueId(),tick+getGameConfig().getHeatSeconds()*20);
    }
    private boolean safe(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0,1,0).getBlock().isPassable()
                && !l.clone().subtract(0,.15,0).getBlock().isPassable() && !l.getBlock().isLiquid();
    }
    private void teleport(Player p,Location l) { ownTeleport=true; try{p.teleport(l);}finally{ownTeleport=false;} }
    public void move(PlayerMoveEvent e) {
        if(!playing(e.getPlayer()) || e.getTo()==null)return;
        Location frozen=freezeLocations.get(e.getPlayer().getUniqueId());
        if(frozen!=null) {Location to=frozen.clone();to.setYaw(e.getTo().getYaw());to.setPitch(e.getTo().getPitch());e.setTo(to);}
    }
    public void teleportEvent(PlayerTeleportEvent e) { if(playing(e.getPlayer()) && !ownTeleport)e.setCancelled(true); }
    public void melee(Player attacker,Player victim) {
        if(!acting(attacker) || !playing(victim) || !round.enemies(attacker.getUniqueId(),victim.getUniqueId()))return;
        UUID id=attacker.getUniqueId(); if(meleeCooldown.getOrDefault(id,0)>tick)return;
        FrostbiteItem item=item(attacker.getInventory().getItemInMainHand());
        if(item==FrostbiteItem.AXE && attacker.getAttackCooldown()<.9F)return;
        meleeCooldown.put(id,tick+8);reveal(attacker);
        if(item==FrostbiteItem.AXE) {
            UUID killer=round.instantKill(id,victim.getUniqueId(),tick);
            if(killer!=null){ consume(attacker);award(killer);respawn(victim.getUniqueId()); }
        } else if(freeze(id,victim) && item==FrostbiteItem.ICICLE) {
            consume(attacker); giveRandom(attacker,null);giveRandom(attacker,item(attacker.getInventory().getItem(0)));
        }
    }
    private boolean freeze(UUID attacker,Player victim) {
        if(!playing(victim) || !round.freeze(attacker,victim.getUniqueId(),tick,getGameConfig().getFreezeSeconds()*20))return false;
        boolean phoenix=has(victim,FrostbiteItem.PHOENIX);
        clearCombat(victim);
        if(phoenix) {round.thaw(victim.getUniqueId());victim.sendMessage(LegacyText.component("&6凤凰余烬已消耗，抵挡了一次冻结。"));return true;}
        freezeLocations.put(victim.getUniqueId(),victim.getLocation());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,PotionEffect.INFINITE_DURATION,255,true,false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,PotionEffect.INFINITE_DURATION,255,true,false));
        victim.setVelocity(new Vector());victim.setFreezeTicks(130);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE,victim.getLocation().add(0,1,0),25,.4,.7,.4,.02);
        victim.playSound(victim.getLocation(),Sound.BLOCK_GLASS_BREAK,1,.7F);
        return true;
    }
    private void thaw(Player p) {round.thaw(p.getUniqueId());freezeLocations.remove(p.getUniqueId());p.setFreezeTicks(0);
        p.removePotionEffect(PotionEffectType.SLOWNESS);p.removePotionEffect(PotionEffectType.WEAKNESS);}
    private void clearCombat(Player p) {
        freezeLocations.remove(p.getUniqueId());p.setFreezeTicks(0);p.getInventory().clear();p.setFireTicks(0);
        for(PotionEffect effect:p.getActivePotionEffects())p.removePotionEffect(effect.getType());
        reveal(p);bursts.removeIf(b->b.owner.equals(p.getUniqueId()));
    }
    private void reveal(Player p) { invisibleUntil.remove(p.getUniqueId());p.removePotionEffect(PotionEffectType.INVISIBILITY); }
    public FrostbiteItem item(ItemStack stack) {
        if(stack==null || !stack.hasItemMeta())return null;
        String name=stack.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);
        try{return name==null?null:FrostbiteItem.valueOf(name);}catch(IllegalArgumentException ignored){return null;}
    }
    private boolean has(Player p,FrostbiteItem item) {for(ItemStack s:p.getInventory().getStorageContents())if(item(s)==item)return true;return false;}
    private int itemCount(Player p) {int n=0;for(ItemStack s:p.getInventory().getStorageContents())if(item(s)!=null)n++;return n;}
    private ItemStack stack(FrostbiteItem type) {
        ItemStack stack=new ItemStack(type.material);stack.editMeta(meta->{meta.displayName(LegacyText.component("&b"+type.title));
            meta.lore(List.of(LegacyText.component("&7"+type.description)));meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,type.name());});return stack;
    }
    private void consume(Player p) {p.getInventory().setItemInMainHand(null);}
    private void giveRandom(Player p,FrostbiteItem exclude) {
        if(itemCount(p)>=2)return;
        List<FrostbiteItem> pool=Arrays.stream(FrostbiteItem.values()).filter(i->i!=exclude && i!=FrostbiteItem.ICICLE && (exclude==null || i!=FrostbiteItem.MYSTERY) &&
                (i!=FrostbiteItem.MYSTERY || props.stream().noneMatch(prop->prop.arena==arena(p.getUniqueId()) && prop.type==FrostbiteItem.MYSTERY))).toList();
        FrostbiteItem type=pool.get(random.nextInt(pool.size()));
        // Icicles cannot recursively generate themselves or boxes; regular pickups add them separately.
        give(p,type);
    }
    private void give(Player p,FrostbiteItem type) {
        int slot=p.getInventory().getItem(0)==null?0:1;
        p.getInventory().setItem(slot,stack(type));
        if(type==FrostbiteItem.BOW)p.getInventory().setItem(8,new ItemStack(Material.ARROW,3));
        p.sendMessage(LegacyText.component("&b"+type.title+" &7"+type.description));
        p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.5F,1.5F);
    }
    public void use(Player p) {
        if(!acting(p) || useCooldown.getOrDefault(p.getUniqueId(),0)>tick)return;
        FrostbiteItem type=item(p.getInventory().getItemInMainHand()); if(type==null)return;
        if(type==FrostbiteItem.BOW || type==FrostbiteItem.AXE || type==FrostbiteItem.ICICLE || type==FrostbiteItem.PHOENIX)return;
        if((type==FrostbiteItem.BLAZE || type==FrostbiteItem.MYSTERY || type==FrostbiteItem.TURTLE) && !p.isOnGround())return;
        if(type==FrostbiteItem.MYSTERY && props.stream().anyMatch(prop->prop.arena==arena(p.getUniqueId()) && prop.type==type))return;
        useCooldown.put(p.getUniqueId(),tick+5);consume(p);
        switch(type) {
            case HOT_ROD -> round.heat(p.getUniqueId(),tick+70);
            case SPEED -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,240,2,true,false));
            case INVIS -> {p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,200,0,true,false));invisibleUntil.put(p.getUniqueId(),tick+200);}
            case GLOW -> opponents(p.getUniqueId(),p.getLocation(),512).stream().min(Comparator.comparingDouble(o->o.getLocation().distanceSquared(p.getLocation())))
                    .ifPresent(other->other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,160,0,true,false)));
            case BLAZE,MYSTERY,TURTLE -> place(p,type);
            case AVALANCHE -> bursts.add(new Burst(p.getUniqueId(),tick+1,5));
            case WHOABALL,CROSSBOW -> launch(p,type,p.getLocation().getDirection().multiply(type==FrostbiteItem.CROSSBOW?2:1.4));
            case EXPLOSION -> {
                for(Player enemy:opponents(p.getUniqueId(),p.getLocation(),6))freeze(p.getUniqueId(),enemy);
                round.selfFreeze(p.getUniqueId(),tick,getGameConfig().getFreezeSeconds()*20);clearCombat(p);
                freezeLocations.put(p.getUniqueId(),p.getLocation());
            }
            default -> { }
        }
    }
    private void launch(Player p,FrostbiteItem type,Vector velocity) {
        Snowball ball=p.launchProjectile(Snowball.class,velocity);shots.put(ball.getUniqueId(),new Shot(p.getUniqueId(),type,arena(p.getUniqueId()),tick+100));
    }
    public void shoot(EntityShootBowEvent e) {
        if(!(e.getEntity() instanceof Player p) || !participant(p))return;
        if(!acting(p) || item(e.getBow())!=FrostbiteItem.BOW){e.setCancelled(true);return;}
        shots.put(e.getProjectile().getUniqueId(),new Shot(p.getUniqueId(),FrostbiteItem.BOW,arena(p.getUniqueId()),tick+100));
        if(e.getProjectile() instanceof AbstractArrow arrow)arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        if(p.getInventory().getItem(8)==null || p.getInventory().getItem(8).getAmount()<=1)consume(p);
        reveal(p);
    }
    public boolean ownedProjectile(Entity entity) {return shots.containsKey(entity.getUniqueId());}
    public void hit(ProjectileHitEvent e) {
        Shot shot=shots.remove(e.getEntity().getUniqueId());if(shot==null)return;
        e.setCancelled(true); Location l=e.getEntity().getLocation();e.getEntity().remove();
        if(getGameStageEnum()!=GameStageEnum.PROGRESS || !round.active(shot.owner) || !getGameConfig().contains(l,shot.arena))return;
        switch(shot.item) {
            case BOW -> {if(e.getHitEntity() instanceof Player p)freeze(shot.owner,p);}
            case WHOABALL -> {for(Player p:opponents(shot.owner,l,5))freeze(shot.owner,p);}
            case AVALANCHE -> zones.add(new Zone(shot.owner,shot.item,shot.arena,l,tick+400));
            case CROSSBOW -> zones.add(new Zone(shot.owner,shot.item,shot.arena,l,tick+80));
            default -> { }
        }
        l.getWorld().spawnParticle(Particle.SNOWFLAKE,l,15,.5,.5,.5,.02);
    }
    private void place(Player p,FrostbiteItem type) {
        if(type==FrostbiteItem.BLAZE) {Prop old=camp(p.getUniqueId());if(old!=null)remove(old);}
        Location location=p.getLocation();
        ArmorStand stand=location.getWorld().spawn(location,ArmorStand.class,s->{s.setVisible(false);s.setGravity(false);s.setSmall(true);
            s.setInvulnerable(false);s.setPersistent(false);s.getEquipment().setHelmet(new ItemStack(type.material));
            s.customName(LegacyText.component("&b"+type.title));s.setCustomNameVisible(true);});
        props.add(new Prop(p.getUniqueId(),type,arena(p.getUniqueId()),location,stand,tick+(type==FrostbiteItem.BLAZE?600:400),type==FrostbiteItem.MYSTERY?3:1));
    }
    private Prop camp(UUID owner) {return props.stream().filter(p->p.owner.equals(owner)&&p.type==FrostbiteItem.BLAZE).findFirst().orElse(null);}
    private void remove(Prop p) {p.entity.remove();props.remove(p);}
    private void removeOwnedProps(UUID id) {for(Prop p:List.copyOf(props))if(p.owner.equals(id))remove(p);}
    public boolean prop(Entity entity) {return props.stream().anyMatch(p->p.entity.getUniqueId().equals(entity.getUniqueId()));}
    public void strikeProp(Player player,Entity entity) {
        if(!acting(player))return;
        for(Prop p:List.copyOf(props))if(p.entity.getUniqueId().equals(entity.getUniqueId()) && round.enemies(player.getUniqueId(),p.owner)) {
            if(p.type==FrostbiteItem.BLAZE || p.type==FrostbiteItem.MYSTERY)remove(p);
        }
    }
    public void returnCamp(Player p) {
        if(!playing(p) || round.freezeState(p.getUniqueId())==null)return;
        Prop camp=camp(p.getUniqueId());if(camp==null)return;
        Location location=camp.location.clone();remove(camp);thaw(p);teleport(p,location);p.setFallDistance(0);
    }
    private void tickProps() {
        for(Prop p:List.copyOf(props)) {
            if(p.type==FrostbiteItem.TURTLE && (tick>=p.expires || !opponents(p.owner,p.location,1).isEmpty())) {
                for(Player enemy:opponents(p.owner,p.location,3))freeze(p.owner,enemy);remove(p);continue;
            }
            if(tick>=p.expires || !p.entity.isValid()){remove(p);continue;}
            if(p.type==FrostbiteItem.MYSTERY && tick>=p.next) {
                for(UUID id:round.seats().keySet()) {Player player=Bukkit.getPlayer(id);
                    if(player!=null && acting(player) && arena(id)==p.arena && player.getLocation().distanceSquared(p.location)<2.25 && itemCount(player)==0) {
                        giveRandom(player,null);p.next=tick+30;if(--p.charges==0)remove(p);break;
                    }
                }
            }
        }
    }
    private void createPickups() {
        List<Location> points=new ArrayList<>();
        for(int a=0;a<4;a++)for(String text:getGameConfig().getItemPoints())points.add(getGameConfig().point(text,a));
        pickups=List.copyOf(points);pickupReady=new int[points.size()];
        for(Location l:points)pickupDisplays.add(l.getWorld().spawn(l.clone().add(0,.6,0),ItemDisplay.class,d->{d.setItemStack(new ItemStack(Material.GOLD_BLOCK));
            d.setPersistent(false);d.customName(LegacyText.component("&e? 补给"));d.setCustomNameVisible(true);}));
    }
    private void collect(Player p) {
        if(itemCount(p)>0)return;
        int a=arena(p.getUniqueId()),count=getGameConfig().getItemPoints().size();
        for(int i=a*count;i<(a+1)*count;i++)if(pickupReady[i]<=tick && p.getLocation().distanceSquared(pickups.get(i))<=2.25) {
            pickupReady[i]=tick+getGameConfig().getItemRespawnSeconds()*20;
            if(random.nextInt(15)==0)give(p,FrostbiteItem.ICICLE);else giveRandom(p,null);
            pickupDisplays.get(i).setItemStack(new ItemStack(Material.AIR));return;
        }
    }
    @Override public synchronized void endGame() {
        if(ending || getGameStageEnum()==GameStageEnum.WAITING || getGameStageEnum()==GameStageEnum.END)return;
        ending=true;
        if(getGameStageEnum()!=GameStageEnum.PROGRESS) {
            stopRuntime();setGameStageEnum(GameStageEnum.END);beginPostGameSettlement();completePostGame(false);return;
        }
        for(UUID id:round.expired(tick)){award(round.die(id));}
        stopRuntime();
        if(isSettlementAllowed()) {sendMessageToAllGamePlayers(getTeamPointsRank());addPlayerPointsToDatabase();}
        setGameStageEnum(GameStageEnum.END);
        announceGameEnd("&b本轮结束","&f击杀积分已汇入队伍成绩");
        beginPostGameSettlement();changeGameModelForAllGamePlayers(GameMode.ADVENTURE);resetPlayerHealthFoodEffectLevelInventory();
        publishGameEndEvent(new SingleGameEndEvent(this,List.copyOf(gameTeams)));finishPostGameAfterEndEvent();
    }
    private void cleanupPlayer(UUID id) {
        Player p=Bukkit.getPlayer(id);
        if(p!=null){clearCombat(p);Boolean previous=collisionBefore.remove(id);if(previous!=null)p.setCollidable(previous);}
        else {freezeLocations.remove(id);invisibleUntil.remove(id);collisionBefore.remove(id);}
        removeOwnedProps(id);meleeCooldown.remove(id);useCooldown.remove(id);
        zones.removeIf(z->z.owner.equals(id));bursts.removeIf(b->b.owner.equals(id));
    }
    private void stopRuntime() {
        if(tickTask!=null)tickTask.cancel();if(timerTask!=null)timerTask.cancel();tickTask=null;timerTask=null;
        for(UUID id:List.copyOf(gamePlayers))cleanupPlayer(id);
        for(UUID id:shots.keySet()){Entity entity=Bukkit.getEntity(id);if(entity!=null)entity.remove();}shots.clear();
        for(Prop p:List.copyOf(props))remove(p);zones.clear();bursts.clear();
        pickupDisplays.forEach(Entity::remove);pickupDisplays.clear();pickups=List.of();pickupReady=new int[0];
        freezeLocations.clear();meleeCooldown.clear();useCooldown.clear();invisibleUntil.clear();
    }
    @Override public void endGameFinally() {
        stopRuntime();ownTeleport=true;
        try {super.endGameFinally();} finally {ownTeleport=false;}
    }
    @Override public void resetArea() {stopRuntime();round=null;ending=false;timer=0;preloadMap();}
    @Override public void dispose() {stopRuntime();super.dispose();}
    @Override public java.util.concurrent.CompletableFuture<Boolean> abortAndReset() {
        if(Bukkit.isPrimaryThread() && isEventRun())plugin.getScheduleManager().endGameSchedule(GameTypeEnum.FrostbiteFrenzy);
        return super.abortAndReset();
    }
    @Override public void handlePlayerQuit(@NotNull PlayerQuitEvent e) {if(playing(e.getPlayer()))award(round.leave(e.getPlayer().getUniqueId()));cleanupPlayer(e.getPlayer().getUniqueId());}
    @Override public void handlePlayerJoin(@NotNull PlayerJoinEvent e) {
        Player p=e.getPlayer();if(notAreaPlayer(p))return;
        if(round!=null && getGameStageEnum()==GameStageEnum.PROGRESS) {
            award(round.leave(p.getUniqueId()));cleanupPlayer(p.getUniqueId());teleport(p,getSpectatorSpawnLocation());p.setGameMode(GameMode.SPECTATOR);
        } else {teleport(p,getSpectatorSpawnLocation());p.setGameMode(GameMode.ADVENTURE);}
    }
    @Override public void handlePlayerDeath(@NotNull PlayerDeathEvent e) {
        e.getDrops().clear();e.setDroppedExp(0);e.setKeepInventory(true);e.setKeepLevel(true);
        if(playing(e.getEntity())){award(round.leave(e.getEntity().getUniqueId()));cleanupPlayer(e.getEntity().getUniqueId());}
        // Native death is exceptional (commands/other plugins); respawn as spectator, never a free re-entry.
    }
    public void respawnEvent(PlayerRespawnEvent e) {
        if(!participant(e.getPlayer()))return;
        e.setRespawnLocation(getSpectatorSpawnLocation());e.getPlayer().setGameMode(GameMode.SPECTATOR);
    }
    @Override public Location getSpectatorSpawnLocation() {
        Location l=getGameConfig().getSpectatorSpawnPoint();
        if(l!=null){l=l.clone();l.setWorld(Bukkit.getWorld(getWorldName()));return l;}
        World world=Bukkit.getWorld(getWorldName());return world==null?CCConfig.LOBBY_LOCATION:world.getSpawnLocation();
    }
    @Override protected Collection<Player> getOnlineParticipantSpectators() {return gamePlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).filter(p->p.getGameMode()==GameMode.SPECTATOR).toList();}
    @Override public int getTimer(){return timer;}
    @Override public FrostbiteConfig getGameConfig(){return (FrostbiteConfig)gameConfig;}
    @Override public FrostbiteHandler getGameHandler(){return (FrostbiteHandler)gameHandler;}
    @Override public String getWorldName(){return gameConfig.getConfiguredWorld();}
}
