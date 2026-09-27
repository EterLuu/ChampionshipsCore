package ink.ziip.championshipscore.api.game.frostbite;

import org.bukkit.Material;

/** Original power-ups, with the two ally rescues adapted for solo arenas. */
public enum FrostbiteItem {
    AVALANCHE(Material.SNOWBALL, "雪崩", "右键连射五枚雪球，落地留下减速雪区"),
    AXE(Material.IRON_AXE, "冰镐", "满蓄力近战立即击杀敌人；保温可抵挡"),
    BLAZE(Material.CAMPFIRE, "回归营火", "右键放置营火；被冻结后按 F 返回；持续30秒，可被敌人打破"),
    BOW(Material.BOW, "冰雪狙击弓", "三发冰箭，命中冻结敌人"),
    CROSSBOW(Material.CROSSBOW, "暖流救援弩", "右键发射暖流；4秒内留在落点附近可自动解冻一次"),
    EXPLOSION(Material.TNT, "西伯利亚雪爆", "右键冻结6格内敌人，也会冻结自己"),
    GLOW(Material.GLOW_INK_SAC, "追踪光点", "右键使最近敌人发光8秒"),
    HOT_ROD(Material.BLAZE_ROD, "暖焰棒", "右键获得3.5秒保温"),
    ICICLE(Material.PRISMARINE_SHARD, "冰锥", "近战成功冻结敌人后换取两件不同道具"),
    INVIS(Material.FERMENTED_SPIDER_EYE, "隐形药剂", "右键隐形10秒；攻击会解除隐形"),
    MYSTERY(Material.ENDER_CHEST, "神秘补给箱", "右键放置，提供三次补给；每张地图最多一个"),
    PHOENIX(Material.TOTEM_OF_UNDYING, "凤凰余烬", "持有时被冻结会自动解冻一次，但清空道具且不获得保温"),
    SPEED(Material.SUGAR, "疾速", "右键获得12秒速度 III"),
    TURTLE(Material.TURTLE_SCUTE, "冰龟地雷", "右键放置；敌人踩中或20秒后爆炸，冻结3格内敌人"),
    WHOABALL(Material.SNOWBALL, "霜爆雪球", "右键投出，落点5格内敌人被冻结");
    public final Material material;
    public final String title;
    public final String description;
    FrostbiteItem(Material material, String title, String description) {
        this.material = material; this.title = title; this.description = description;
    }
}
