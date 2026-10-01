package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.*;

/** Re-times authored wall sequences inside their existing slots. Never scans custom building paths. */
final class RiptideWallGroups {
    private RiptideWallGroups() { }

    static boolean uniqueWalls(List<RiptideCoursePlan.Level> levels) {
        var ids = new HashSet<String>();
        var designs = new HashSet<String>();
        for (var l : levels) {
            if (l.type() == RiptideLevelType.PASS && !l.isSideSweep()
                    && (!ids.add(l.template().id()) || !designs.add(l.template().designKey(l.variant())))) return false;
            for (var w : l.sideWalls())
                if (!ids.add(w.template().id()) || !designs.add(w.template().designKey(w.variant()))) return false;
        }
        return true;
    }

    static boolean distinctPassages(RiptideCoursePlan.Level a, RiptideCoursePlan.Level b,
                                     RiptideCourseGeometry g) {
        if (a.isSideSweep() && b.isSideSweep() && !a.sideWalls().isEmpty() && !b.sideWalls().isEmpty()) {
            var first = sidePassages(a.sideWalls().getLast());
            var second = sidePassages(b.sideWalls().getFirst());
            return disjoint(first, second);
        }
        if (a.type() != RiptideLevelType.PASS || b.type() != RiptideLevelType.PASS
                || a.isSideSweep() || b.isSideSweep()) return true;
        if (a.template().designKey(a.variant()).equals(b.template().designKey(b.variant()))) return false;
        var first = RiptideFinalStage.passages(a, g);
        var second = RiptideFinalStage.passages(b, g);
        // Unknown custom routes remain playable as isolated walls, without guessing their exits.
        return disjoint(first, second);
    }

    private static boolean disjoint(List<RiptideHitwCatalog.Passage> first, List<RiptideHitwCatalog.Passage> second) {
        return !first.isEmpty() && !second.isEmpty() && first.stream().noneMatch(p -> second.stream()
                .anyMatch(q -> Math.abs(p.lateral() - q.lateral()) < .75));
    }

    static List<RiptideHitwCatalog.Passage> sidePassages(RiptideCoursePlan.SideWall wall) {
        return RiptideHitwCatalog.passages(wall.template(), wall.mirrored() ^ wall.direction() < 0);
    }

    static double lateralSpeed(double boatSpeed, boolean speedEffect) {
        double sprint = speedEffect ? 6.72 : 5.6;
        return Math.max(.5, Math.min(3.5, Math.sqrt(Math.max(0,sprint*sprint-boatSpeed*boatSpeed))*.9));
    }

    static double requiredSeconds(RiptideCoursePlan.Level first, RiptideCoursePlan.Level second,
                                  double boatSpeed, boolean speedEffect) {
        var a = RiptideHitwCatalog.passages(first.template(), first.mirrored());
        var b = RiptideHitwCatalog.passages(second.template(), second.mirrored());
        if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY;
        // From EVERY supported exit there must be a reachable next entrance. Release crouch, move,
        // then perform the next jump/crouch; allow time for the player's body to clear the first wall.
        return a.stream().mapToDouble(p -> b.stream().mapToDouble(q ->
                Math.abs(p.lateral()-q.lateral()) / lateralSpeed(boatSpeed,speedEffect) + .65
                        + (p.crouch() || q.crouch() ? 1D : 0) + (q.sill()>0 ? .3 : 0))
                .min().orElseThrow()).max().orElseThrow();
    }

    static int overlap(RiptideCoursePlan.Level a, RiptideCoursePlan.Level b) {
        var next = RiptideHitwCatalog.passages(b.template(), b.mirrored());
        return (int)RiptideHitwCatalog.passages(a.template(), a.mirrored()).stream()
                .filter(p -> next.stream().anyMatch(q -> Math.abs(p.lateral()-q.lateral()) < .75
                        && p.sill()==q.sill() && p.crouch()==q.crouch())).count();
    }

    private static RiptideCoursePlan.Level copy(RiptideCoursePlan.Level l, int step, boolean mirror,
                                               int group, int beat, String rhythm, int sweep) {
        return new RiptideCoursePlan.Level(l.number(),step,l.template(),l.variant(),l.opening(),mirror,
                l.contentSeed(),group,beat,rhythm,sweep);
    }

    static List<RiptideCoursePlan.Level> arrange(RiptideRushConfig c, RiptideCourseGeometry g,
                                                List<RiptideCoursePlan.Level> input, Random random,
                                                int sideSweepQuota) {
        var out = new ArrayList<>(input);
        if (c.isWallGroups()) for (int start=0;start<out.size();) {
            if (!eligible(out.get(start)) || (out.get(start).step()<g.totalSteps()*.20 || out.get(start).step()>=g.totalSteps()*.8)) {start++;continue;}
            int end=start+1;
            int limit=out.get(start).step()<g.totalSteps()*.40 ? 2 : 3;
            while(end<out.size() && end-start<limit && eligible(out.get(end)) && out.get(end).step()<g.totalSteps()*.8) end++;
            if(end-start<2){start=end;continue;}
            String rhythm = end-start==3 ? (random.nextBoolean() ? "TIGHTEN" : "DOUBLE_REST") : "DOUBLE";
            var candidate=new ArrayList<>(out);
            var first=out.get(start);
            candidate.set(start,copy(first,first.step(),first.mirrored(),first.number(),1,rhythm,0));
            for(int j=start+1;j<end;j++) {
                var previous=candidate.get(j-1);var l=out.get(j);
                var mirror=copy(l,l.step(),!l.mirrored(),first.number(),j-start+1,rhythm,0);
                if(overlap(previous,mirror)<overlap(previous,l))l=mirror;
                double speed=RiptideCoursePlanner.speedAt(c,g,out.get(end-1).step());
                double seconds=Math.max(1.1,requiredSeconds(previous,l,speed,previous.step()>=g.totalSteps()*.8));
                if(rhythm.equals("TIGHTEN") && j==start+1 || rhythm.equals("DOUBLE_REST") && j==start+2) seconds+=.55;
                // Maximum speed through this interval also covers an acceleration boundary.
                int gap=(int)Math.ceil(seconds*speed)+1;
                candidate.set(j,copy(l,previous.step()+gap,l.mirrored(),first.number(),j-start+1,rhythm,0));
            }
            boolean valid=candidate.get(end-1).step()<=out.get(end-1).step();
            valid &= candidate.subList(start,end).stream().map(l -> l.template().designKey(l.variant())).distinct().count()==end-start;
            for(int j=Math.max(1,start);j<Math.min(out.size(),end+1);j++)
                valid &= RiptideCoursePlanner.safeTransition(c,g,candidate.get(j-1),candidate.get(j));
            for(int j=start+1;j<end;j++) valid &= overlap(candidate.get(j-1),candidate.get(j))==0;
            // Keep difficulty bands even when a later wall has moved closer to the group leader.
            for(int j=start;j<end;j++) valid &= RiptideDifficulty.allowsPass(candidate.get(j).template().difficulty(),candidate.get(j).step(),g.totalSteps());
            if(valid)out=candidate;
            start=end;
        }
        // Side challenges consume COLOR_FLOOR slots; their physical walls share the PASS pool.
        // Each physical wall consumes an entry from the same enabled PASS pool.
        int sweeps=0;
        var uses=new HashMap<String,Integer>();
        for(var level:out)uses.merge(level.template().usageKey(),1,Integer::sum);
        for(int i=0;i<out.size() && sweeps<sideSweepQuota;i++) {
            var l=out.get(i);
            if(l.type()!=RiptideLevelType.COLOR_FLOOR || g.stoppedStep(l.step())<g.totalSteps()*.4)continue;
            var trialUses=new HashMap<>(uses);
            trialUses.merge(l.template().usageKey(),-1,Integer::sum);
            var walls=selectSideWalls(c,g,g.stoppedStep(l.step()),trialUses,random);
            if(walls.isEmpty())continue;
            var candidate=new RiptideCoursePlan.Level(l.number(),l.step(),l.template(),l.variant(),l.opening(),
                    l.mirrored(),l.contentSeed(),0,0,"SIDE",walls.getFirst().direction(),walls);
            if(i>0 && !RiptideCoursePlanner.safeTransition(c,g,out.get(i-1),candidate))continue;
            if(i+1<out.size() && !RiptideCoursePlanner.safeTransition(c,g,candidate,out.get(i+1)))continue;
            out.set(i,candidate);uses=trialUses;sweeps++;
        }
        return sweeps == sideSweepQuota ? List.copyOf(out) : null;
    }

    static List<RiptideCoursePlan.SideWall> selectSideWalls(RiptideRushConfig c, RiptideCourseGeometry g,
                                                           int step, Map<String,Integer> uses, Random random) {
        var result=new ArrayList<RiptideCoursePlan.SideWall>();
        var pool=c.resolvePool();
        for(int beat=0;beat<RiptideSideSweep.wallCount(step,g.totalSteps());beat++) {
            var candidates=new ArrayList<>(pool.stream().filter(t -> t.enabled() && t.type()==RiptideLevelType.PASS
                    && RiptideDifficulty.allowsSide(t.difficulty(),step,g.totalSteps()) && uses.getOrDefault(t.usageKey(),0)<t.maxUses()
                    && (!RiptideHitwCatalog.isOriginal(t) || RiptideHitwCatalog.passages(t,false).stream()
                            .anyMatch(p -> Math.abs(p.lateral())<=g.halfLength()-.3))).toList());
            var selectedDesigns=result.stream().map(w -> w.template().designKey(w.variant())).toList();
            candidates.removeIf(t -> selectedDesigns.contains(t.designKey(t.variant())));
            candidates.removeIf(t -> sideMirrors(t, result).isEmpty());
            if(candidates.isEmpty())return List.of();
            double draw=random.nextDouble()*candidates.stream().mapToDouble(t -> t.weight()/(1D+uses.getOrDefault(t.id(),0))).sum();
            var selected=candidates.getLast();
            for(var candidate:candidates) {
                draw-=candidate.weight()/(1D+uses.getOrDefault(candidate.id(),0));
                if(draw<0){selected=candidate;break;}
            }
            String variant=selected.variant();
            if(variant.equals("AUTO")) {
                var variants=RiptideLevelTemplate.variants(RiptideLevelType.PASS);
                variant=variants.get(1+random.nextInt(variants.size()-1));
            }
            int opening=random.nextInt(g.raftLength()-2)-g.halfLength()+1;
            int extent=selected.blueprint()!=null ? selected.blueprint().width()/2 : g.halfLength()+1;
            var orientations = sideMirrors(selected, result);
            boolean mirrored = orientations.get(random.nextInt(orientations.size()));
            int direction = random.nextBoolean() ? 1 : -1;
            result.add(new RiptideCoursePlan.SideWall(selected,variant,opening,direction,extent,mirrored ^ direction < 0));
            uses.merge(selected.usageKey(),1,Integer::sum);
        }
        return List.copyOf(result);
    }

    private static List<Boolean> sideMirrors(RiptideLevelTemplate template, List<RiptideCoursePlan.SideWall> selected) {
        if (selected.isEmpty()) return List.of(false, true);
        var previous = selected.getLast();
        var exits = sidePassages(previous);
        if (exits.isEmpty()) return List.of();
        return List.of(false, true).stream().filter(mirror -> {
            var entrances = RiptideHitwCatalog.passages(template, mirror);
            return disjoint(exits, entrances);
        }).toList();
    }

    private static boolean eligible(RiptideCoursePlan.Level l) {
        return l.type()==RiptideLevelType.PASS && RiptideHitwCatalog.isOriginal(l.template());
    }
}
