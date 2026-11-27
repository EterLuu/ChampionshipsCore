package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import com.destroystokyo.paper.profile.ProfileProperty;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.NativeTeamOverlay;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.NativeTeamService;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Team;

import java.util.*;

final class BasinAppearance implements AutoCloseable {
    final BasinHotbar hotbar = new BasinHotbar();

    // Minecraft Heads #45953 (Cod), published by SNESBoi.
    private static final String SKIN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGZlZWZmNGI3ZmNmY2U2OGIwZjc0ZGYwZGIwYWQwYzAxZjczMDFkMGM2ZDg5MzY5OWI0MDJiZDUwYmIzNzZiMCJ9fX0=";
    private static final Component FISH = Component.text("[🐟] ", NamedTextColor.YELLOW);
    private static final org.bukkit.NamespacedKey KIT =
            new org.bukkit.NamespacedKey("championshipscore", "hoty_uniform");
    private final String uniformId = UUID.randomUUID().toString();
    private final NativeTeamService teams = NativeTeamService.mainScoreboard();
    private final NativeTeamOverlay overlay = teams.newOverlay();
    private final Map<Team, Team> badges = new HashMap<>();

    void mark(Player p, ChampionshipTeam team) {
        ItemStack cap = new ItemStack(Material.PLAYER_HEAD);
        // The costume must transmit a waypoint like The Purple's space helmet.
        cap.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        cap.setData(
                DataComponentTypes.PROFILE,
                ResolvableProfile.resolvableProfile()
                        .addProperty(new ProfileProperty("textures", SKIN)));
        cap.setData(
                DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(EquipmentSlot.HEAD)
                        .cameraOverlay(Key.key("minecraft:misc/powder_snow_outline"))
                        .swappable(false));
        label(cap);
        p.getInventory().setHelmet(cap);

        Team original = teams.scoreboard().getEntryTeam(p.getName());
        Team badge =
                badges.computeIfAbsent(
                        original,
                        base -> {
                            String id =
                                    "cod"
                                            + UUID.randomUUID()
                                                    .toString()
                                                    .replace("-", "")
                                                    .substring(0, 12);
                            Team t = teams.scoreboard().registerNewTeam(id);
                            overlay.own(t);
                            t.prefix(FISH.append(base == null ? Component.empty() : base.prefix()));
                            if (base != null) {
                                t.suffix(base.suffix());
                                t.setAllowFriendlyFire(base.allowFriendlyFire());
                                t.setCanSeeFriendlyInvisibles(base.canSeeFriendlyInvisibles());
                                for (var option : Team.Option.values())
                                    t.setOption(option, base.getOption(option));
                            }
                            t.color(NamedTextColor.nearestTo(teamColor(team)));
                            return t;
                        });
        overlay.assign(p.getUniqueId(), p.getName(), badge);
    }

    static TextColor teamColor(ChampionshipTeam team) {
        if (team == null) return NamedTextColor.WHITE;
        String code = team.getColorCode();
        if (code.matches("[0-9A-Fa-f]{6}")) return TextColor.fromHexString("#" + code);
        var configured = LegacyText.component(code + "x").color();
        return configured != null
                ? configured
                : NativeTeamService.resolveNamedColor(team.getColorName(), code);
    }

    void release(UUID player) {
        overlay.release(player);
    }

    public void close() {
        overlay.close();
        badges.clear();
    }

    void teamKit(Player player, ChampionshipTeam team) {
        if (team == null) return;
        Color dye = Color.fromRGB(teamColor(team).value());
        var helmet = team.getHelmet();
        var boots = team.getBoots();
        helmet.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(dye));
        boots.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(dye));
        label(helmet);
        label(boots);
        player.getInventory().setHelmet(helmet);
        player.getInventory().setBoots(boots);
    }

    private void label(ItemStack piece) {
        piece.editPersistentDataContainer(
                data -> data.set(KIT, org.bukkit.persistence.PersistentDataType.STRING, uniformId));
    }

    void clearKit(Player p) {
        hotbar.clear(p);
        var inv = p.getInventory();
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.FEET)) {
            var item = inv.getItem(slot);
            if (item != null
                    && uniformId.equals(
                            item.getPersistentDataContainer()
                                    .get(KIT, org.bukkit.persistence.PersistentDataType.STRING)))
                inv.setItem(slot, null);
        }
    }

    static void copy(Player source, Mannequin model, ChampionshipTeam team, boolean marked) {
        model.setProfile(ResolvableProfile.resolvableProfile(source.getPlayerProfile()));
        var name = Component.text(source.getName(), teamColor(team));
        model.customName(marked ? FISH.append(name) : name);
        model.setCustomNameVisible(true);
        model.setDescription(null);
        model.setCanPickupItems(false);
        var inventory = source.getInventory();
        var gear = model.getEquipment();
        for (var slot :
                List.of(
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET,
                        EquipmentSlot.HAND,
                        EquipmentSlot.OFF_HAND)) {
            var item = inventory.getItem(slot);
            gear.setItem(slot, item == null ? null : item.clone());
        }
    }
}
