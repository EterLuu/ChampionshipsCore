package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.SideEffectSet;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

/** Shared physical side walls, with final-stage color answers returned as one batch per wall. */
final class RiptidePassRun implements AutoCloseable {
    private final RiptideCourseGeometry geometry;
    private final List<RiptideCoursePlan.Level> sweeps;
    private final List<RiptideCoursePlan.Level> levels;
    private final List<org.bukkit.block.BlockState> blocks=new ArrayList<>();
    private final List<org.bukkit.block.Block> pistons=new ArrayList<>();
    private final RiptideRushConfig config;
    private double speed;
    private RiptideSideSweep.Frame rendered;
    private List<List<RiptideMovingWall.Cell>> buildings=List.of();
    private int next;
    private int tick;
    private RiptideCoursePlan.Level active;
    private RiptideColorFloorPlatform answerFloor;
    private final Set<Player> titleRecipients = new HashSet<>();
    record Answer(UUID player, int level, int beat, RiptideQuestion question, boolean correct) { }

    RiptidePassRun(RiptideCourseGeometry geometry,List<RiptideCoursePlan.Level> levels,RiptideRushConfig config) {
        this.geometry=geometry;this.levels=List.copyOf(levels);this.config=config;
        sweeps=levels.stream().filter(RiptideCoursePlan.Level::isSideSweep).toList();
    }

    boolean active(){return active!=null;}
    boolean sideMathActive() { return active != null && active.sideMath(); }

    /** The next forward step at which a side sweep starts, or {@code Integer.MAX_VALUE}. */
    int nextStartStep() {
        return active != null || next >= sweeps.size() ? Integer.MAX_VALUE : geometry.stoppedStep(sweeps.get(next).step());
    }

    /** Suppress a normal math preview while its title could overlap the upcoming side sweep. */
    boolean startsWithin(int completedSteps, double speed) {
        int step = nextStartStep();
        if (step == Integer.MAX_VALUE) return false;
        int titleTicks = 12;
        int blocks = Math.max(1, (int) Math.ceil(Math.max(0D, speed) * titleTicks / 20D));
        return step - completedSteps <= blocks;
    }

    boolean beginReached(int step) {
        if(active!=null)return true;
        if(next>=sweeps.size() || step<geometry.stoppedStep(sweeps.get(next).step()))return false;
        active=sweeps.get(next++);tick=0;
        speed=RiptideCoursePlanner.speedAt(config,geometry,geometry.stoppedStep(active.step()));
        Material obstacle=Material.matchMaterial(config.getObstacleMaterial());
        if(obstacle==null || !obstacle.isBlock() || obstacle.isAir())throw new IllegalArgumentException("invalid obstacle material");
        buildings=active.sideWalls().stream().map(w -> RiptideMovingWall.compile(geometry,w,obstacle)).toList();
        RiptideNativePiston.prepare();
        if (active.sideMath()) {
            answerFloor = new RiptideColorFloorPlatform(geometry, geometry.stoppedStep(active.step()));
            answerFloor.paint(RiptideSideMath.floor(geometry));
        }
        render(RiptideSideSweep.frame(0,active.sideWalls(),geometry.halfWidth(),speed));
        return true;
    }

    List<Answer> tick(Collection<Player> players) {
        if(active==null)return List.of();
        for(var block:pistons)RiptideNativePiston.settle(block);
        var answers = new ArrayList<Answer>();
        var frame=RiptideSideSweep.frame(tick,active.sideWalls(),geometry.halfWidth(),speed);
        render(frame);
        var question = active.sideMath() ? RiptideSideMath.question(active, frame.beat(), config.getMinimumOperand(), config.getMaximumOperand()) : null;
        if(question != null ? frame.localTick()%10==0 : tick==0) {
            String warning = Utils.translateColorCodes(MessageConfig.RIPTIDE_RUSH_SWEEP_TITLE);
            String title = warning, subtitle = "";
            if (question != null) {
                var display = RiptideQuestionDisplay.of(question, active.number());
                int remaining = RiptideSideSweep.beatTicks(geometry.halfWidth(), speed, active.sideWalls().get(frame.beat()-1)) - frame.localTick();
                title = display.title();
                subtitle = (tick < RiptideSideSweep.WARNING_TICKS ? warning + " §f• " : "") + display.subtitle()
                        + " §f• " + String.format(Locale.ROOT, "%.1f秒后判题", remaining / 20D);
            }
            for(var player:players) {
                titleRecipients.add(player);
                player.sendTitle(title, subtitle, 0, question == null ? RiptideSideSweep.WARNING_TICKS : 11, 0);
            }
        }
        if(frame.localTick()==0) for(var player:players)
            player.playSound(player.getLocation(),"block.note_block.hat",1F,frame.beat()==1?1F:1.5F);
        if (question != null && frame.localTick() + 1 == RiptideSideSweep.beatTicks(geometry.halfWidth(), speed, active.sideWalls().get(frame.beat()-1))) {
            for (var player : players) answers.add(new Answer(player.getUniqueId(), active.number(), frame.beat(), question,
                    RiptideSideMath.matches(player.getLocation(), geometry, geometry.stoppedStep(active.step()), question)));
        }
        tick++;
        if(tick==RiptideSideSweep.totalTicks(geometry.halfWidth(),speed,active.sideWalls())){
            close();
        }
        return List.copyOf(answers);
    }

    private void render(RiptideSideSweep.Frame frame) {
        if(rendered!=null && rendered.beat()==frame.beat() && rendered.lateral()==frame.lateral())return;
        var world=geometry.centerAt(active.step()).getWorld();
        int delta = rendered != null && rendered.beat() == frame.beat() ? frame.lateral() - rendered.lateral() : 0;
        var facing = delta == 0 ? null : RiptideNativePiston.facing(delta * geometry.stepZ(), -delta * geometry.stepX());
        restoreBlocks();
        var adapted=BukkitAdapter.adapt(world);
        try {
            for(var cell:buildings.get(frame.beat()-1)) {
                int x=geometry.blockX(geometry.stoppedStep(active.step()),frame.lateral())+cell.x();
                int y=geometry.floorY()+cell.y();
                int z=geometry.blockZ(geometry.stoppedStep(active.step()),frame.lateral())+cell.z();
                var block=world.getBlockAt(x,y,z);
                blocks.add(block.getState());
                if (facing != null) {
                    pistons.add(block);
                    RiptideNativePiston.move(block, BukkitAdapter.adapt(cell.block()), facing);
                } else {
                    block.setBlockData(BukkitAdapter.adapt(cell.block()),false);
                }
                if(facing == null && cell.block().getNbt()!=null) {
                    if(!adapted.setBlock(BlockVector3.at(x,y,z),cell.block(),SideEffectSet.none()))
                        throw new IllegalStateException("放置侧墙方块实体失败");
                    block.getState().update(true,false);
                }
            }
            rendered=frame;
        } catch(RuntimeException failure){restoreBlocks();throw failure;}
    }

    private void restoreBlocks(){
        // Remove ticking entities first so they cannot finish by replacing restored scenery with slime.
        for(var block:pistons)RiptideNativePiston.remove(block);
        pistons.clear();
        for(var block:blocks)block.update(true,false);
        blocks.clear();rendered=null;
    }
    @Override public void close(){
        restoreBlocks();
        if (answerFloor != null) { answerFloor.restore(); answerFloor = null; }
        titleRecipients.forEach(Player::resetTitle); titleRecipients.clear();
        active=null;buildings=List.of();
    }
}
