package ink.ziip.championshipscore.api.team.gui;

import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.api.gui.GuiMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.NativeTeamService;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Complete team-menu templates; only native identity and dye data come from runtime objects. */
final class TeamMenuItems {
    private static final Set<String> NAVIGATION = Set.of("previous", "next", "page", "close");

    private TeamMenuItems() {}

    static ItemStack item(MenuId menu, String id, String state, Map<String, ?> values) {
        GuiConfig.ItemSpec spec;
        if (NAVIGATION.contains(id)) {
            var fallback = GuiConfig.item("buttons." + id, state, values);
            fallback = GuiConfig.item("teams.buttons." + id, state, values, fallback);
            spec = GuiConfig.item(menu.item(id), state, values, fallback);
        } else spec = GuiConfig.item(menu.item(id), state, values);
        return GuiMenu.item(spec.material(), spec.title(), spec.lore(), spec.glint());
    }

    static ItemStack team(
            MenuId menu, String id, String state, Map<String, ?> values, String color) {
        Material material = Material.matchMaterial(color.toUpperCase(Locale.ROOT) + "_WOOL");
        if (material == null) return item(menu, id, state, values);
        return ConfiguredGui.item(
                menu.item(id),
                state,
                values,
                GuiMenu.item(material, Component.empty(), List.of(), false));
    }

    static ItemStack player(
            MenuId menu, String id, String state, Map<String, ?> values, UUID uuid, String name) {
        var base = GuiMenu.playerHead(uuid, Component.empty(), List.of(), false);
        if (base.getItemMeta() instanceof SkullMeta skull) {
            skull.setPlayerProfile(Bukkit.createProfile(uuid, name));
            base.setItemMeta(skull);
        }
        return ConfiguredGui.item(menu.item(id), state, values, base);
    }

    static String colorState(String color) {
        // Existing resource identifiers are persistent configuration API, not display labels.
        String key = color.toLowerCase(Locale.ROOT);
        return key.equals("lime") ? "yellow-green" : key.replace('_', '-');
    }

    static Component colorLabel(String color) {
        return GuiConfig.component(
                MenuId.TEAMS_COLOR_PICKER.item("color")
                        + ".states."
                        + colorState(color)
                        + ".title");
    }

    static String colorHex(String color) {
        return String.format(
                Locale.ROOT,
                "#%06X",
                DyeColor.valueOf(color.toUpperCase(Locale.ROOT)).getColor().asRGB());
    }

    static ItemStack color(String color, String occupiedBy) {
        MenuId menu = MenuId.TEAMS_COLOR_PICKER;
        String availability = menu.item(occupiedBy == null ? "available" : "in-use");
        var values = new java.util.HashMap<String, Object>();
        values.put("label", colorLabel(color));
        values.put("name", color);
        values.put("hex", colorHex(color));
        values.put("team", occupiedBy == null ? "" : occupiedBy);
        var nativeBase =
                new GuiConfig.ItemSpec(
                        -1,
                        Material.valueOf(color.toUpperCase(Locale.ROOT) + "_WOOL"),
                        Component.empty(),
                        List.of(),
                        false);
        var status = GuiConfig.item(availability, null, values, nativeBase);
        List<Component> rows = status.lore();
        values.put("availability", Component.join(JoinConfiguration.newlines(), rows));
        var spec = GuiConfig.item(menu.item("color"), colorState(color), values, status);
        return GuiMenu.item(
                spec.material(),
                spec.title(),
                GuiItemLore.expand(
                        GuiConfig.itemLoreTemplate(menu.item("color"), colorState(color)),
                        values,
                        "%availability%",
                        rows),
                spec.glint());
    }

    static Component teamName(ChampionshipTeam team) {
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return LegacyText.component(
                team.getName(),
                color == null ? NativeTeamService.resolveNamedColor(team.getColorName()) : color);
    }

    static Component title(MenuId menu, Map<String, ?> values) {
        return GuiConfig.component(menu.path() + ".title", values);
    }
}
