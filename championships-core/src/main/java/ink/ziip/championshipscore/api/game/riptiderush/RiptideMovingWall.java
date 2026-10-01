package ink.ziip.championshipscore.api.game.riptiderush;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.bukkit.Material;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Compiles runtime slime walls while retaining the saved building's openings and partial shapes. */
final class RiptideMovingWall {
    record Cell(int x, int y, int z, BaseBlock block) { }
    private RiptideMovingWall() { }

    static AffineTransform transform(RiptideCourseGeometry g, int direction) {
        return transform(g, direction, false);
    }

    static AffineTransform transform(RiptideCourseGeometry g, int direction, boolean mirrored) {
        int across = mirrored ? -direction : direction;
        // Local +Z is the direction through the original wall. Point it towards the raft.
        return new AffineTransform(
                across*g.stepX(), 0, -direction*g.stepZ(), 0,
                0, 1, 0, 0,
                across*g.stepZ(), 0, direction*g.stepX(), 0);
    }

    static List<Cell> compile(RiptideCourseGeometry g, RiptideCoursePlan.SideWall wall, Material obstacle) {
        var result=new ArrayList<Cell>();
        var transform=transform(g,wall.direction(),wall.mirrored());
        var blueprint=wall.template().blueprint();
        if(blueprint!=null) {
            try(var reader=BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(
                        new ByteArrayInputStream(Base64.getDecoder().decode(blueprint.schematic())));
                var clipboard=reader.read()) {
                var dimensions=clipboard.getDimensions();
                if(dimensions.x()!=blueprint.width() || dimensions.y()!=blueprint.height()
                        || (dimensions.z()!=7 && dimensions.z()!=RiptideWorkshop.BUILDING_EXTENT*2+1))
                    throw new IllegalArgumentException("侧墙建筑尺寸与保存信息不符");
                for(var position:clipboard.getRegion()) {
                    var block=clipboard.getFullBlock(position);
                    if(block.getBlockType().getMaterial().isAir())continue;
                    var relative=position.subtract(clipboard.getOrigin());
                    add(result,transform,relative,block);
                }
            } catch(IOException failure) { throw new IllegalStateException("读取侧墙建筑失败",failure); }
        } else {
            var level=new RiptideCoursePlan.Level(0,0,wall.template(),wall.variant(),wall.opening(),false,0);
            for(var cell:RiptideCourseGenerator.passBlocks(g.halfLength(),level,obstacle))
                add(result,transform,BlockVector3.at(cell.lateral(),cell.y(),cell.forward()),
                        BukkitAdapter.adapt(cell.material().createBlockData()).toBaseBlock());
        }
        result = new ArrayList<>(fitStoppedDeck(g, result));
        if(result.isEmpty())throw new IllegalArgumentException("侧墙建筑在停船甲板范围内不能为空");
        // Animate the leading cells first: a trailing cell's temporary piston must not erase
        // a moving entity that was already created for a cell in front of it.
        result.sort(leadingFirst(-wall.direction()*g.stepZ(),wall.direction()*g.stepX()));
        return List.copyOf(result);
    }

    /** Rotation may make a saved 15-block wall longer than the stopped deck. */
    static List<Cell> fitStoppedDeck(RiptideCourseGeometry geometry, List<Cell> cells) {
        return cells.stream().filter(cell -> Math.abs(cell.x() * geometry.stepX() + cell.z() * geometry.stepZ())
                <= geometry.halfLength()).toList();
    }

    static Comparator<Cell> leadingFirst(int dx, int dz) {
        return Comparator.comparingInt((Cell cell) -> cell.x()*dx+cell.z()*dz).reversed();
    }

    private static void add(List<Cell> cells, AffineTransform transform, BlockVector3 position, BaseBlock block) {
        var offset=transform.apply(position.toVector3()).toBlockPoint();
        if(block.getBlockType().getMaterial().isFullCube())
            block=BukkitAdapter.adapt(Material.SLIME_BLOCK.createBlockData()).toBaseBlock();
        cells.add(new Cell(offset.x(),offset.y(),offset.z(),BlockTransformExtent.transform(block,transform)));
    }
}
