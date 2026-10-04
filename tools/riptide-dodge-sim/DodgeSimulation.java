import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeSchedule;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeSchedule.*;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 20 Hz movement-only simulation; controllers never see unspawned attacks. */
public final class DodgeSimulation {
    private static final double RADIUS = (RiptideDodgeSchedule.COLLISION_WIDTH + .6D) / 2;
    private static final int DURATION = RiptideDodgeSchedule.DURATION_TICKS;
    private static final double[] DX = {0, 1, -1, 0, 0, .7071067812, .7071067812, -.7071067812, -.7071067812};
    private static final double[] DZ = {0, 0, 0, 1, -1, .7071067812, -.7071067812, .7071067812, -.7071067812};
    private record Controller(String name, double speed, int reaction, int horizon, int decisionPeriod,
                              int deckScanPeriod, int skyScanPeriod) { }
    private static final Controller[] CONTROLLERS = {
        new Controller("stationary", 0, 0, 0, 1, 0, 0),
        new Controller("cautious", 3.2 / 20, 8, 8, 4, 18, 40),
        new Controller("walking", 4.3 / 20, 6, 12, 3, 12, 32),
        new Controller("responsive", 5.6 / 20, 4, 16, 2, 6, 16),
        new Controller("omnivision", 4.3 / 20, 6, 12, 3, 0, 0)
    };
    private record Outcome(boolean survived, double distance, int movingTicks, int switches, int closeTicks) { }
    private record Attack(double x, double z, double vx, double vz, int start, int end, int spawned, boolean falling) { }
    private record Trace(int tick, double x, double z) { }

    public static void main(String[] args) throws Exception {
        selfTest();
        if (args.length > 0 && args[0].equals("--self-test")) { System.out.println("simulation self-tests passed"); return; }
        int seeds = args.length == 0 ? 300 : Integer.parseInt(args[0]);
        Path output = Path.of(args.length < 2 ? "/tmp/riptide-dodge-results.csv" : args[1]);
        String mode = args.length < 3 ? "sweep" : args[2];
        int firstSeed = args.length < 4 ? 0 : Integer.parseInt(args[3]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (mode.equals("calibrate")) { calibrate(output,seeds,firstSeed); return; }
        if (mode.equals("validate")) {
            validate(output,seeds,firstSeed,args.length>4 && args[4].equals("default"),args.length>5 ? args[5] : ""); return;
        }
        if (mode.equals("fine")) { fineTune(output,seeds,firstSeed); return; }
        if (mode.equals("replay")) { writeReplay(output, firstSeed); return; }
        if (!mode.equals("sweep") && !mode.equals("focused")) throw new IllegalArgumentException("unknown simulation mode: "+mode);
        try (var csv = new PrintWriter(Files.newBufferedWriter(output))) {
            csv.println("width,length,min_interval,max_interval,cap,mob,controller,seeds,first_seed,pass_rate,spawn_mean,active_mean,active_peak,distance_mean,moving_fraction,turns_mean,close_fraction");
            int[][] intervals = mode.equals("sweep") ? new int[][]{{18,30},{14,22},{10,18},{8,14},{6,10},{4,8},{2,4}}
                    : new int[][]{{18,30},{8,14},{6,12},{6,10},{4,8},{2,4},{0,0}};
            int[] caps = mode.equals("sweep") ? new int[]{3,4,5,6,8,12}
                    : new int[]{6,8};
            int[][] decks = mode.equals("sweep") ? new int[][]{{7,9}} : new int[][]{{7,9},{5,7},{9,7},{15,9},{3,3}};
            for (var deck : decks) for (var interval : intervals) for (int cap : caps) {
                if (interval[0] == 0 && cap != caps[0]) continue;
                Settings settings = interval[0] == 0 ? null : new Settings(interval[0], interval[1], cap);
                int effectiveCap = settings == null ? 0 : settings.maximumActive();
                for (Mob mob : Mob.values()) {
                    double[] won = new double[CONTROLLERS.length], distance = won.clone(), moving = won.clone(),
                            turns = won.clone(), close = won.clone();
                    double spawnCount = 0, activeCount = 0;
                    int peak = 0;
                    for (int seed = firstSeed; seed < firstSeed + seeds; seed++) {
                        List<Spawn> spawns = settings == null ? List.of() : RiptideDodgeSchedule.generate(mob.name(), seed, deck[0], deck[1], settings);
                        Attack[][] frames = settings == null ? legacyFrames(mob, seed, deck[0], deck[1]) : frames(spawns);
                        spawnCount += settings == null ? legacyCount(mob, deck[0]) * 3 : spawns.size();
                        for (int tick = 0; tick < DURATION; tick++) {
                            int active = frames[tick].length;
                            activeCount += active;
                            peak = Math.max(peak, active);
                        }
                        // Half the trials start centrally, half start at a seeded interior position.
                        Random start = new Random(seed ^ 0x61c8864680b583ebL);
                        double x = seed % 2 == 0 ? 0 : (start.nextDouble() - .5) * (deck[0] - 1);
                        double z = seed % 2 == 0 ? 0 : (start.nextDouble() - .5) * (deck[1] - 1);
                        for (int i = 0; i < CONTROLLERS.length; i++) {
                            var result = simulate(frames, deck[0], deck[1], CONTROLLERS[i], x, z, null, seed);
                            won[i] += result.survived ? 1 : 0; distance[i] += result.distance;
                            moving[i] += result.movingTicks; turns[i] += result.switches; close[i] += result.closeTicks;
                        }
                    }
                    for (int i = 0; i < CONTROLLERS.length; i++)
                        csv.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%s,%s,%d,%d,%.5f,%.2f,%.2f,%d,%.2f,%.4f,%.2f,%.4f%n",
                                deck[0], deck[1], interval[0], interval[1], effectiveCap, mob, CONTROLLERS[i].name,
                                seeds, firstSeed, won[i] / seeds, spawnCount / seeds, activeCount / (seeds * DURATION), peak,
                                distance[i] / seeds, moving[i] / (seeds * DURATION), turns[i] / seeds, close[i] / (seeds * DURATION));
                }
                csv.flush();
                System.out.printf("deck=%dx%d interval=%d-%d cap=%d done%n", deck[0], deck[1], interval[0], interval[1], effectiveCap);
            }
        }
        System.out.println(output.toAbsolutePath());
    }

    /** Falling mobs only become dangerous while their native vertical box overlaps a standing player. */
    private static Attack[][] frames(List<Spawn> spawns) {
        Attack[][] result = new Attack[DURATION][];
        for (int tick = 0; tick < DURATION; tick++) {
            List<Attack> frame = new ArrayList<>();
            for (Spawn spawn : spawns) if (spawn.activeAt(tick)) {
                Position position = spawn.positionAt(tick - spawn.tick());
                int dangerStart = spawn.direction() == Direction.DOWN
                        ? spawn.tick() + (int)Math.ceil((1.2 + 8.5 - 1.8) / spawn.speed()) : spawn.tick();
                int dangerEnd = spawn.direction() == Direction.DOWN
                        ? spawn.tick() + (int)Math.ceil((1.2 + 8.5 + 1.7) / spawn.speed())
                        : spawn.tick() + spawn.lifetimeTicks();
                frame.add(new Attack(position.x(), position.z(), spawn.direction().x * spawn.speed(),
                        spawn.direction().z * spawn.speed(), dangerStart, dangerEnd, spawn.tick(),spawn.direction()==Direction.DOWN));
            }
            result[tick] = frame.toArray(Attack[]::new);
        }
        return result;
    }

    /** Frozen copy of the pre-refactor three-wave model, including its lack of per-mob cleanup. */
    private static Attack[][] legacyFrames(Mob mob, long seed, int width, int length) {
        record OldSpawn(int tick, double x, double z, double vx, double vz, int start, int end) { }
        List<OldSpawn> attacks = new ArrayList<>();
        Random random = new Random(seed);
        int count = legacyCount(mob, width);
        for (int wave = 0; wave < 3; wave++) {
            Direction[] directions = switch (mob) {
                case ZOMBIE -> new Direction[]{random.nextBoolean() ? Direction.NORTH : Direction.SOUTH};
                case HUSK -> new Direction[]{random.nextBoolean() ? Direction.EAST : Direction.WEST};
                case SKELETON -> new Direction[]{Direction.values()[random.nextInt(4)]};
                case SPIDER -> random.nextBoolean() ? new Direction[]{Direction.NORTH, Direction.SOUTH} : new Direction[]{Direction.EAST, Direction.WEST};
                case CREEPER -> new Direction[]{Direction.DOWN};
            };
            int[] emitted = new int[directions.length], counts = new int[directions.length];
            for (int i = 0; i < count; i++) counts[i % directions.length]++;
            double speed = switch (mob) {
                case ZOMBIE -> new double[]{.16,.21,.26}[wave];
                case HUSK -> new double[]{.14,.18,.23}[wave];
                case SKELETON -> new double[]{.12,.17,.22}[wave];
                case SPIDER -> new double[]{.19,.24,.30}[wave];
                case CREEPER -> new double[]{.10,.17,.27}[wave];
            };
            for (int i = 0; i < count; i++) {
                int index = i % directions.length;
                Direction direction = directions[index];
                double slot = directions.length == 2 ? (index == 0 ? .25 : .75) : .5;
                double span = direction.x != 0 ? length : width;
                double lane = ((emitted[index]++ + slot) / counts[index] - .5) * Math.max(1, span - 1);
                double x = lane, z = 0;
                switch (direction) {
                    case NORTH -> z = RiptideDodgeSchedule.spawnDistance(length);
                    case SOUTH -> z = -RiptideDodgeSchedule.spawnDistance(length);
                    case EAST -> { x = -RiptideDodgeSchedule.spawnDistance(width); z = lane; }
                    case WEST -> { x = RiptideDodgeSchedule.spawnDistance(width); z = lane; }
                    case DOWN -> { }
                }
                int tick = wave * 60 + (int)Math.round(i * 59D / (count - 1));
                int start = direction == Direction.DOWN ? tick + (int)Math.ceil((9.7 - 1.8) / speed) : tick;
                int end = direction == Direction.DOWN ? tick + (int)Math.ceil((9.7 + 1.7) / speed) : DURATION;
                attacks.add(new OldSpawn(tick, x, z, direction.x * speed, direction.z * speed, start, end));
            }
        }
        Attack[][] frames = new Attack[DURATION][];
        for (int tick = 0; tick < DURATION; tick++) {
            List<Attack> frame = new ArrayList<>();
            for (var spawn : attacks) if (tick >= spawn.tick)
                frame.add(new Attack(spawn.x + spawn.vx * (tick - spawn.tick), spawn.z + spawn.vz * (tick - spawn.tick),
                        spawn.vx, spawn.vz, spawn.start, spawn.end, spawn.tick, mob==Mob.CREEPER));
            frames[tick] = frame.toArray(Attack[]::new);
        }
        return frames;
    }
    private static int legacyCount(Mob mob, int width) {
        return switch (mob) { case SPIDER -> Math.max(8, width / 2 + 2); case CREEPER -> 15; default -> 7; };
    }

    private static Outcome simulate(Attack[][] frames, int width, int length, Controller c, double x, double z) {
        return simulate(frames, width, length, c, x, z, null, 0);
    }
    private static Outcome simulate(Attack[][] frames, int width, int length, Controller c, double x, double z, List<Trace> trace,int seed) {
        double boundX = width / 2D - .3, boundZ = length / 2D - .3, distance = 0;
        int direction = 0, moving = 0, turns = 0, close = 0;
        for (int tick = 0; tick < DURATION; tick++) {
            if (c.speed > 0 && tick % c.decisionPeriod == 0) {
                // Notice new attacks after a reaction delay. Extrapolate noticed motion to now;
                // the controller cannot use the current frame to discover future/new attacks.
                Attack[] noticed = frames[Math.max(0, tick - c.reaction)];
                int chosen = choose(noticed, tick, c, x, z, boundX, boundZ, direction,seed);
                if (chosen != direction) turns++;
                direction = chosen;
            }
            double nextX = clamp(x + DX[direction] * c.speed, boundX), nextZ = clamp(z + DZ[direction] * c.speed, boundZ);
            if (trace != null) trace.add(new Trace(tick, nextX, nextZ));
            double travelled = Math.hypot(nextX - x, nextZ - z);
            distance += travelled;
            if (travelled > 1e-6) moving++;
            boolean near = false;
            for (Attack a : frames[tick]) {
                if (tick < a.start || tick >= a.end) continue;
                // Relative-motion segment test catches contacts between ticks, including crossings.
                if (intersects(x - a.x, z - a.z, nextX - a.x - a.vx, nextZ - a.z - a.vz, RADIUS))
                    return new Outcome(false, distance, moving, turns, close);
                if (Math.abs(nextX - a.x) < 1.4 && Math.abs(nextZ - a.z) < 1.4) near = true;
            }
            if (near) close++;
            x = nextX; z = nextZ;
        }
        return new Outcome(true, distance, moving, turns, close);
    }

    /** Receding-horizon choice of one of eight normalized headings or stay; no teleporting. */
    private static int choose(Attack[] noticed, int tick, Controller c, double x, double z,
                              double boundX, double boundZ, int previous,int seed) {
        List<Attack> visible=new ArrayList<>();
        for(Attack a:noticed) {
            int period=a.falling ? c.skyScanPeriod : c.deckScanPeriod;
            int phase=Math.floorMod(seed*11,Math.max(1,period));
            int observation=period==0 ? a.spawned : a.spawned+Math.floorMod(-a.spawned-phase,period);
            if(tick >= observation+c.reaction) visible.add(a);
        }
        if(visible.isEmpty()) return 0;
        double best = -Double.MAX_VALUE;
        int choice = 0;
        for (int direction = 0; direction < DX.length; direction++) {
            double px = x, pz = z, score = direction == 0 ? 0 : -.1;
            if (direction != previous) score -= .015;
            for (int ahead = 0; ahead < c.horizon; ahead++) {
                double nx = clamp(px + DX[direction] * c.speed, boundX), nz = clamp(pz + DZ[direction] * c.speed, boundZ);
                for (Attack a : visible) {
                    int future = tick + ahead;
                    if (future < a.start || future >= a.end) continue;
                    double ax = a.x + a.vx * (c.reaction + ahead), az = a.z + a.vz * (c.reaction + ahead);
                    if (intersects(px - ax, pz - az, nx - ax - a.vx, nz - az - a.vz, RADIUS + .15))
                        score -= 1000D * (c.horizon - ahead) / c.horizon;
                    else {
                        double clearance = Math.max(Math.abs(nx - ax), Math.abs(nz - az));
                        if (clearance < 1.35) score -= (1.35 - clearance) * .1;
                    }
                }
                px = nx; pz = nz;
            }
            if (score > best) { best = score; choice = direction; }
        }
        return choice;
    }

    private static double clamp(double value, double bound) { return Math.max(-bound, Math.min(bound, value)); }
    private static boolean intersects(double x0, double z0, double x1, double z1, double radius) {
        double first = 0, last = 1;
        double dx = x1 - x0, dz = z1 - z0;
        if (Math.abs(dx) < 1e-12) { if (Math.abs(x0) >= radius) return false; }
        else {
            double a = (-radius - x0) / dx, b = (radius - x0) / dx;
            first = Math.max(first, Math.min(a,b)); last = Math.min(last, Math.max(a,b));
        }
        if (Math.abs(dz) < 1e-12) { if (Math.abs(z0) >= radius) return false; }
        else {
            double a = (-radius - z0) / dz, b = (radius - z0) / dz;
            first = Math.max(first, Math.min(a,b)); last = Math.min(last, Math.max(a,b));
        }
        return first <= last;
    }

    private static void selfTest() {
        require(intersects(-2, 0, 2, 0, RADIUS), "between-tick collision");
        require(!intersects(-2, 2, 2, 2, RADIUS), "clear path");
        Attack[][] corridor = new Attack[DURATION][];
        for (int tick = 0; tick < DURATION; tick++)
            corridor[tick] = tick < 60 ? new Attack[]{new Attack(0, 5 - tick * .25, 0, -.25, 0, 60,0,false)} : new Attack[0];
        require(!simulate(corridor, 7, 9, CONTROLLERS[0], 0, 0).survived, "stationary player must be hit");
        require(simulate(corridor, 7, 9, CONTROLLERS[2], 0, 0).survived, "moving player can dodge");
        Attack[][] empty = new Attack[DURATION][];
        Arrays.setAll(empty, tick -> new Attack[0]);
        var still = simulate(empty, 7, 9, CONTROLLERS[2], 0, 0);
        require(still.survived && still.distance == 0, "safe player stays still");
        Attack[][] surprise = new Attack[DURATION][];
        Arrays.setAll(surprise, tick -> tick < 10 ? new Attack[0] : new Attack[]{new Attack(0, 0, 0, 0, 10, DURATION,10,false)});
        var caught = simulate(surprise, 7, 9, CONTROLLERS[2], 0, 0);
        require(!caught.survived && caught.distance == 0, "controller cannot see unspawned attacks");
        for (Mob mob : Mob.values()) for (int seed = 0; seed < 50; seed++) {
            var schedule = RiptideDodgeSchedule.generate(mob.name(), seed, 7, 9, new Settings(6,12,6));
            int last = -100;
            for (Spawn spawn : schedule) {
                require(spawn.tick() - last >= 6, "only one spawn per interval");
                last = spawn.tick();
            }
            for (int tick = 0; tick < DURATION; tick++) {
                int count = 0;
                for (Spawn spawn : schedule) if (spawn.activeAt(tick)) count++;
                require(count <= 6, "active cap");
            }
        }
    }
    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private static void writeReplay(Path output, int firstSeed) throws Exception {
        try (var json = new PrintWriter(Files.newBufferedWriter(output))) {
            json.print("{\"width\":7,\"length\":9,\"duration\":180,\"cases\":[");
            boolean firstCase = true;
            for (int stage : new int[]{0,2,4}) for (Mob mob : Mob.values()) {
                int[] seeds = {-1,-1};
                for (int seed=firstSeed;seed<firstSeed+200;seed++) {
                    var spawns=RiptideDodgeSchedule.generate(mob.name(),seed,7,9,stage);
                    var result=simulate(frames(spawns),7,9,controllerForStage(CONTROLLERS[2],stage),start(seed,7),start(seed,9),null,seed);
                    int slot=result.survived ? 0 : 1;
                    if(seeds[slot]<0) seeds[slot]=seed;
                    if(seeds[0]>=0 && seeds[1]>=0) break;
                }
                for (int seed : seeds) {
                    if (seed<0) continue;
                    if (!firstCase) json.print(','); firstCase=false;
                    var spawns=RiptideDodgeSchedule.generate(mob.name(),seed,7,9,stage);
                    json.printf(Locale.ROOT,"{\"mob\":\"%s\",\"stage\":%d,\"seed\":%d,\"spawns\":[",mob,stage,seed);
                    for (int i=0;i<spawns.size();i++) {
                        if(i>0) json.print(','); var s=spawns.get(i); var p=s.positionAt(0);
                        json.printf(Locale.ROOT,"{\"tick\":%d,\"end\":%d,\"x\":%.5f,\"y\":%.5f,\"z\":%.5f,\"vx\":%.5f,\"vy\":%.5f,\"vz\":%.5f}",
                                s.tick(),s.tick()+s.lifetimeTicks(),p.x(),p.y(),p.z(),s.direction().x*s.speed(),
                                s.direction().y*s.speed(),s.direction().z*s.speed());
                    }
                    json.print("],\"players\":[");
                    for (int i=0;i<CONTROLLERS.length;i++) {
                        if(i>0) json.print(','); List<Trace> trace=new ArrayList<>();
                        var result=simulate(frames(spawns),7,9,controllerForStage(CONTROLLERS[i],stage),start(seed,7),start(seed,9),trace,seed);
                        json.printf(Locale.ROOT,"{\"name\":\"%s\",\"survived\":%s,\"distance\":%.3f,\"trace\":[",CONTROLLERS[i].name,result.survived,result.distance);
                        for(int j=0;j<trace.size();j++) {
                            if(j>0) json.print(','); var t=trace.get(j);
                            json.printf(Locale.ROOT,"[%.5f,%.5f]",t.x,t.z);
                        }
                        json.print("]}");
                    }
                    json.print("]}");
                }
            }
            json.print("]}");
        }
        System.out.println(output.toAbsolutePath());
    }

    private static Controller controllerForStage(Controller c,int stage) {
        return stage==4 ? new Controller(c.name,c.speed*1.2,c.reaction,c.horizon,c.decisionPeriod,c.deckScanPeriod,c.skyScanPeriod) : c;
    }
    private static double start(int seed,int span) {
        Random random = new Random(seed ^ 0x61c8864680b583ebL);
        double x = (random.nextDouble()-.5)*(7-1), z=(random.nextDouble()-.5)*(9-1);
        return seed%2==0 ? 0 : span==7 ? x : z;
    }

    private static void calibrate(Path output,int seeds,int firstSeed) throws Exception {
        double[] factors={.85,1.05,1.25,1.45,1.65};
        int[][] intervals={{1,1},{1,2},{2,3},{2,4},{3,5},{4,6},{6,10},{8,12},{10,16},{14,22}};
        int[] caps={6,8,10,12,16,24,32};
        try(var csv=new PrintWriter(Files.newBufferedWriter(output))) {
            csv.println("stage,speed_multiplier,min_interval,max_interval,cap,mob,seeds,first_seed,pass_rate,spawn_mean,active_mean,distance_mean");
            for(int stage=0;stage<5;stage++) for(Mob mob:Mob.values()) {
                Controller c=CONTROLLERS[2];
                // The existing game grants Speed I after 80% of the course.
                if(stage==4) c=new Controller(c.name,c.speed*1.2,c.reaction,c.horizon,c.decisionPeriod,c.deckScanPeriod,c.skyScanPeriod);
                for(int[] interval:intervals) for(int cap:caps) {
                    var settings=new Settings(interval[0],interval[1],cap,factors[stage]);
                    double won=0,spawned=0,active=0,distance=0;
                    for(int seed=firstSeed;seed<firstSeed+seeds;seed++) {
                        var schedule=RiptideDodgeSchedule.generate(mob.name(),seed,7,9,settings);
                        var frames=frames(schedule);
                        var outcome=simulate(frames,7,9,c,start(seed,7),start(seed,9),null,seed);
                        won+=outcome.survived?1:0; distance+=outcome.distance; spawned+=schedule.size();
                        for(var frame:frames) active+=frame.length;
                    }
                    csv.printf(Locale.ROOT,"%d,%.2f,%d,%d,%d,%s,%d,%d,%.5f,%.2f,%.2f,%.2f%n",stage,factors[stage],
                            interval[0],interval[1],cap,mob,seeds,firstSeed,won/seeds,spawned/seeds,active/(seeds*DURATION),distance/seeds);
                }
                csv.flush(); System.out.printf("stage=%d mob=%s calibrated%n",stage,mob);
            }
        }
    }

    private static void validate(Path output,int seeds,int firstSeed,boolean defaultOnly,String filter) throws Exception {
        try(var csv=new PrintWriter(Files.newBufferedWriter(output))) {
            csv.println("width,length,stage,min_interval,max_interval,cap,mob,controller,seeds,first_seed,pass_rate,spawn_mean,active_mean,active_peak,distance_mean,moving_fraction,turns_mean,close_fraction,speed");
            int[][] decks=defaultOnly ? new int[][]{{7,9}} : new int[][]{{7,9},{5,7},{9,7},{15,9},{3,3}};
            for(int[] deck:decks) for(int stage=0;stage<5;stage++) for(Mob mob:Mob.values()) {
                if(!filter.isEmpty() && !filter.equals(stage+":"+mob.name())) continue;
                Settings settings=RiptideDodgeSchedule.settingsFor(deck[0],deck[1],stage,mob);
                double[] wins=new double[CONTROLLERS.length],dist=wins.clone(),moving=wins.clone(),turns=wins.clone(),close=wins.clone();
                double spawned=0,active=0; int peak=0;
                for(int seed=firstSeed;seed<firstSeed+seeds;seed++) {
                    var schedule=RiptideDodgeSchedule.generate(mob.name(),seed,deck[0],deck[1],stage);
                    var frames=frames(schedule);
                    spawned+=schedule.size();
                    for(var frame:frames) { active+=frame.length; peak=Math.max(peak,frame.length); }
                    Random start=new Random(seed ^ 0x61c8864680b583ebL);
                    double x=seed%2==0?0:(start.nextDouble()-.5)*(deck[0]-1);
                    double z=seed%2==0?0:(start.nextDouble()-.5)*(deck[1]-1);
                    for(int i=0;i<CONTROLLERS.length;i++) {
                        Controller c=CONTROLLERS[i];
                        if(stage==4) c=new Controller(c.name,c.speed*1.2,c.reaction,c.horizon,c.decisionPeriod,c.deckScanPeriod,c.skyScanPeriod);
                        var outcome=simulate(frames,deck[0],deck[1],c,x,z,null,seed);
                        wins[i]+=outcome.survived?1:0;dist[i]+=outcome.distance;moving[i]+=outcome.movingTicks;turns[i]+=outcome.switches;close[i]+=outcome.closeTicks;
                    }
                }
                for(int i=0;i<CONTROLLERS.length;i++) csv.printf(Locale.ROOT,"%d,%d,%d,%d,%d,%d,%s,%s,%d,%d,%.5f,%.2f,%.2f,%d,%.2f,%.4f,%.2f,%.4f,%.4f%n",
                        deck[0],deck[1],stage,settings.minimumInterval(),settings.maximumInterval(),settings.maximumActive(),mob,CONTROLLERS[i].name,
                        seeds,firstSeed,wins[i]/seeds,spawned/seeds,active/(seeds*DURATION),peak,dist[i]/seeds,moving[i]/(seeds*DURATION),turns[i]/seeds,close[i]/(seeds*DURATION),
                        RiptideDodgeSchedule.speed(mob)*settings.speedMultiplier());
                csv.flush();
                System.out.printf("validated deck=%dx%d phase=%d mob=%s%n",deck[0],deck[1],stage,mob);
            }
        }
    }

    private static void fineTune(Path output,int seeds,int firstSeed) throws Exception {
        try(var csv=new PrintWriter(Files.newBufferedWriter(output))) {
            csv.println("stage,speed_multiplier,min_interval,max_interval,cap,mob,seeds,first_seed,pass_rate,spawn_mean,active_mean,distance_mean");
            for(int stage=0;stage<5;stage++) for(Mob mob:Mob.values()) {
                Settings base=RiptideDodgeSchedule.settingsFor(7,9,stage,mob);
                Controller c=CONTROLLERS[2];
                if(stage==4) c=new Controller(c.name,c.speed*1.2,c.reaction,c.horizon,c.decisionPeriod,c.deckScanPeriod,c.skyScanPeriod);
                for(int dl=-1;dl<=1;dl++) for(int dh=-1;dh<=1;dh++) for(int dc=-1;dc<=1;dc++) {
                    int lo=base.minimumInterval()+dl,hi=base.maximumInterval()+dh,cap=base.maximumActive()+dc;
                    if(lo<2||hi<lo||cap<2)continue;
                    Settings settings=new Settings(lo,hi,cap,base.speedMultiplier());
                    double won=0,spawned=0,active=0,distance=0;
                    for(int seed=firstSeed;seed<firstSeed+seeds;seed++) {
                        var schedule=RiptideDodgeSchedule.generate(mob.name(),seed,7,9,settings);
                        var frames=frames(schedule);
                        var outcome=simulate(frames,7,9,c,start(seed,7),start(seed,9),null,seed);
                        won+=outcome.survived?1:0; distance+=outcome.distance; spawned+=schedule.size();
                        for(var frame:frames) active+=frame.length;
                    }
                    csv.printf(Locale.ROOT,"%d,%.2f,%d,%d,%d,%s,%d,%d,%.5f,%.2f,%.2f,%.2f%n",stage,base.speedMultiplier(),lo,hi,cap,mob,seeds,firstSeed,
                            won/seeds,spawned/seeds,active/(seeds*DURATION),distance/seeds);
                }
                csv.flush();System.out.printf("refined phase=%d mob=%s%n",stage,mob);
            }
        }
    }
}
