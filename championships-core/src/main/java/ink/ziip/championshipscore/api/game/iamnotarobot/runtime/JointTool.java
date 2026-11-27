package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The pick uses normal iron-pickaxe speed and extends reach while held in the main hand. */
final class JointTool {

    private final RobotProps props;
    private ItemStack pick;
    private ItemStack block;

    JointTool(RobotProps props) {
        this.props = props;
    }

    boolean tool(ItemStack item) {
        return props.owns(item, RobotProps.Kind.JOINT_PICK);
    }

    boolean block(ItemStack item) {
        return props.owns(item, RobotProps.Kind.JOINT_BLOCK);
    }

    private ItemStack create() {
        var pick = new ItemStack(Material.IRON_PICKAXE);
        pick.editMeta(
                meta -> {
                    meta.setUnbreakable(true);
                    meta.displayName(LegacyText.component(RobotArea.text("joint-tool")));
                    meta.lore(List.of(LegacyText.component(RobotArea.text("joint-tool-lore"))));
                    meta.addAttributeModifier(
                            Attribute.BLOCK_INTERACTION_RANGE,
                            new AttributeModifier(
                                    new NamespacedKey("championshipscore", "joint_reach"),
                                    2.5,
                                    AttributeModifier.Operation.ADD_NUMBER,
                                    EquipmentSlotGroup.MAINHAND));
                });
        pick.setData(
                DataComponentTypes.CAN_BREAK,
                ItemAdventurePredicate.itemAdventurePredicate(
                        List.of(
                                BlockPredicate.predicate()
                                        .blocks(
                                                RegistrySet.keySetFromValues(
                                                        RegistryKey.BLOCK,
                                                        List.of(
                                                                Material.GRAY_CONCRETE
                                                                        .asBlockType())))
                                        .build())));
        return pick;
    }

    void give(Player player) {
        if (pick == null) pick = create();
        if (block == null) {
            block = new ItemStack(Material.GRAY_CONCRETE);
            block.editMeta(
                    meta -> {
                        meta.displayName(LegacyText.component(RobotArea.text("joint-block")));
                        meta.lore(
                                List.of(LegacyText.component(RobotArea.text("joint-block-lore"))));
                    });
        }
        props.give(player, RobotProps.Kind.JOINT_PICK, pick, RobotProps.SlotPolicy.HELD);
        props.give(player, RobotProps.Kind.JOINT_BLOCK, block, RobotProps.SlotPolicy.HOTBAR);
    }

    void remove(Player player) {
        props.recover(player, RobotProps.Kind.JOINT_BLOCK);
        props.recover(player, RobotProps.Kind.JOINT_PICK);
    }
}
