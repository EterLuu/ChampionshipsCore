package ink.ziip.championshipscore.configuration.config.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.laser.model.LaserGun;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ExtendWith(ConfigurationStateExtension.class)
class GuiConfigParsingTest {
    @Test
    void snapshotsWithOnlyBundledUseReferencesMatchCompleteDocumentsIncludingSharedBingoItems() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var partial = new YamlConfiguration();
        partial.setDefaults(defaults);
        int compared = 0;
        for (String key : defaults.getKeys(true)) {
            if (!key.endsWith(".use")) continue;
            String path = key.substring(0, key.length() - ".use".length());
            for (String state : new String[] {null, "selected", "idle", "locked", "completed"}) {
                assertEquals(
                        GuiConfig.itemTemplate(defaults, path, state),
                        GuiConfig.itemTemplate(partial, path, state),
                        path + " / " + state);
                compared++;
            }
        }
        assertTrue(compared > 0);
    }

    @Test
    void equipmentMaterialConstraintsPreserveWeaponsButAllowCosmeticMenuAndUtilityChanges() {
        var doc =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        for (LaserGun gun : LaserGun.values()) {
            String path = "laser.equipment.guns." + gun.id();
            doc.set(path + ".material", "DIAMOND");
            assertTrue(GuiConfig.validate(doc).stream().anyMatch(error -> error.contains(path)));
            doc.set(path + ".material", gun.material().name());
            doc.set(path + ".states.selected.material", "DIAMOND");
            assertTrue(GuiConfig.validate(doc).stream().anyMatch(error -> error.contains(path)));
            doc.set(path + ".states.selected.material", null);
            doc.set("laser.menus.gun-selection.items.guns." + gun.id() + ".material", "DIAMOND");
        }
        doc.set("laser.equipment.ammo.material", "ARROW");
        assertTrue(
                GuiConfig.validate(doc).stream().anyMatch(error -> error.contains("ammunition")));
        doc.set("laser.equipment.ammo.material", "SPECTRAL_ARROW");
        doc.set("laser.equipment.laserbox.grenade.material", "EMERALD");
        assertTrue(GuiConfig.validate(doc).isEmpty());
    }

    @Test
    void equippedWeaponTemplatesInheritFullListsAndKeepExplicitEmptyOverrides() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var doc = new YamlConfiguration();
        doc.setDefaults(defaults);
        String path = "laser.equipment.guns.rifle";
        doc.set("buttons.laser-gun.lore", List.of("%name%", "one", "two", "three", "four", "five"));
        assertEquals(6, GuiConfig.itemTemplate(doc, path, "idle").lore().size());
        doc.set(path + ".lore", List.of());
        doc.set(path + ".title", "");
        assertEquals(List.of(), GuiConfig.itemTemplate(doc, path, "idle").lore());
        assertEquals("", GuiConfig.itemTemplate(doc, path, "idle").title());
        doc.set(path + ".states.selected.lore", List.of("chosen only"));
        assertEquals(List.of("chosen only"), GuiConfig.itemTemplate(doc, path, "selected").lore());
        doc.set(path + ".lore", null);
        assertEquals(6, GuiConfig.itemTemplate(doc, path, "idle").lore().size());
        assertTrue(GuiConfig.validate(doc).isEmpty());
    }

    @Test
    void teamEmptyOverlaysCannotCoverControlsAndCenteredLayoutsAreValidatedForEveryList() {
        var doc =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(GuiConfig.validate(doc).isEmpty());
        for (String name :
                List.of(
                        "overview",
                        "members",
                        "add-player",
                        "known-player",
                        "quick-assign",
                        "target-team")) {
            String path = "teams.menus." + name;
            int original = doc.getInt(path + ".layout.overlay.empty");
            doc.set(path + ".layout.overlay.empty", doc.getInt(path + ".items.close.slot"));
            assertTrue(
                    GuiConfig.validate(doc).stream()
                            .anyMatch(error -> error.contains(path + ".layout.overlay.empty")),
                    name);
            doc.set(path + ".layout.overlay.empty", original);
            doc.set(path + ".layout.content", null);
            doc.set(path + ".layout.content.1", List.of(22));
            doc.set(path + ".layout.content.2", List.of(20, 24));
        }
        assertTrue(GuiConfig.validate(doc).isEmpty());
    }

    @Test
    void taskStyleValidationRejectsRevealingMasksAndInvalidFullSummary() {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        String root = "games.bingo.menus.card";
        assertTrue(GuiConfig.validate(document).isEmpty());
        document.set(root + ".items.task.states.hidden.title", "secret 100%");
        assertTrue(GuiConfig.validate(document).isEmpty());
        document.set(root + ".items.task.states.hidden.title", "%description%");
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("states.hidden")));
        document.set(root + ".items.task.states.hidden.title", "?");
        document.set(root + ".task-presentation.completion-summary", "not a list");
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("completion-summary")));
        document.set(root + ".task-presentation.completion-summary", List.of());
        document.set(root + ".items.task.states.locked.material", "TASK");
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("states.locked")));
    }

    @Test
    void frozenItemTemplatesUseTheSameLocalAndSharedStateInheritanceAndKeepFullLore()
            throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var active = GuiConfig.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, document);
        String path = "games.bingo.menus.card.items.info";
        document.set("buttons.frozen-info.material", "EMERALD");
        document.set("buttons.frozen-info.title", "%title%");
        document.set("buttons.frozen-info.lore", List.of("first", "%win-hint%", "last"));
        document.set("buttons.frozen-info.states.selected.glint", true);
        document.set(path, null);
        document.set(path + ".use", "frozen-info");
        var template = GuiConfig.itemTemplate(path, "selected");
        assertEquals("EMERALD", template.material());
        assertTrue(template.glint());
        assertEquals(List.of("first", "%win-hint%", "last"), template.lore());
        document.set(path + ".material", "DIAMOND");
        document.set(path + ".states.selected.lore", List.of());
        var customized = GuiConfig.itemTemplate(path, "selected");
        assertEquals("DIAMOND", customized.material());
        assertTrue(customized.lore().isEmpty());
        assertEquals(3, template.lore().size());
    }

    @Test
    void bingoCardsRequireEveryCellAndKeepSharedInfoOutsideActualTaskSlots() {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(GuiConfig.validate(document).isEmpty());
        String root = "games.bingo.menus.card";
        document.set(root + ".screens.x3.layout.content", List.of(3, 4));
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("exactly 9")));
        document.set(root + ".screens.x3.layout.content", List.of(3, 4, 5, 12, 13, 14, 21, 22, 23));
        document.set(root + ".items.info.slot", 3);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("info must fit")));
        document.set(root + ".screens.x3.layout.footer.info", 0);
        // A child override fixes only that screen: widths4/5 still have a collision with info3.
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(
                                error -> error.contains("x4") && error.contains("info must fit")));
        document.set(root + ".items.info.slot", 0);
        assertTrue(GuiConfig.validate(document).isEmpty());
    }

    @Test
    void spectatorSelectorsValidateActualControlAndEmptyOverlayPriorities() {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(GuiConfig.validate(document).isEmpty());
        for (String screen :
                List.of("venue-selector", "sub-arena-selector", "build-mart-selector")) {
            String menu = "spectator.menus." + screen;
            document.set(menu + ".layout.footer.next", 49);
            assertTrue(
                    GuiConfig.validate(document).stream()
                            .anyMatch(
                                    error -> error.contains(menu) && error.contains("share slot")));
            document.set(menu + ".layout.footer.next", 2);
            assertTrue(GuiConfig.validate(document).isEmpty());
        }
        String menu = "spectator.menus.venue-selector";
        document.set(menu + ".layout.overlay.empty", 49);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains(menu) && error.contains("empty")));
        document.set(menu + ".layout.overlay.empty", 22);
        assertTrue(GuiConfig.validate(document).isEmpty());
    }

    @Test
    void bingoVoteChecksAllPhaseLayoutsAndSeparateOptionNavigation() {
        var config =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(GuiConfig.validate(config).isEmpty());
        for (String phase : List.of("mode", "difficulty", "lines", "genesis")) {
            String menu = "daily.menus.bingo-vote.screens." + phase;
            config.set(menu + ".layout.content", List.of(16));
            assertTrue(GuiConfig.validate(config).isEmpty());
            config.set(menu + ".layout.content", List.of(19, 20, 21));
            assertTrue(
                    GuiConfig.validate(config).stream()
                            .anyMatch(
                                    error ->
                                            error.contains(menu)
                                                    && error.contains("content slot")));
            config.set(menu + ".layout.content", List.of(16));
        }
    }

    @Test
    void laserSelectionValidatesSparseCapacityAndInheritsCompleteSelectedLore() throws Exception {
        String menu = "laser.menus.gun-selection";
        var config =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        config.set(menu + ".layout.content", List.of(16));
        assertTrue(GuiConfig.validate(config).isEmpty());
        activate(config);
        assertEquals(4, GuiConfig.itemLoreTemplate(menu + ".items.guns.rifle", "idle").size());
        assertEquals(5, GuiConfig.itemLoreTemplate(menu + ".items.guns.rifle", "selected").size());
        config.set(menu + ".items.guns.rifle.states.selected.lore", List.of());
        assertTrue(GuiConfig.itemLoreTemplate(menu + ".items.guns.rifle", "selected").isEmpty());
        config.set(menu + ".layout.content", List.of(18, 22, 26));
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("content slot")));
    }

    @Test
    void prepareToolbarRejectsMainInventorySlotsAndDuplicateControlsWithoutWindowLayout() {
        String menu = "map-editor.menus.prepare-toolbar";
        var config =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertFalse(config.isSet(menu + ".size"));
        assertFalse(config.isSet(menu + ".layout"));
        for (int slot : List.of(-1, 9, 36, 40, 53)) {
            config.set(menu + ".items.status.slot", slot);
            assertTrue(
                    GuiConfig.validate(config).stream()
                            .anyMatch(
                                    error ->
                                            error.contains(menu + ".items.status.slot")
                                                    && error.contains("0 and 8")));
        }
        config.set(menu + ".items.status.slot", 1);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains(menu) && error.contains("share slot")));
        config.set(menu + ".items.teleport.slot", 0);
        assertTrue(GuiConfig.validate(config).isEmpty());
    }

    @Test
    void votingNavigationAndEmptyOverlayValidateWithoutConsumingCandidateCapacity() {
        var config =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(GuiConfig.validate(config).isEmpty());
        String menu = "voting.menus.ballot";
        config.set(menu + ".layout.content", List.of(22));
        assertTrue(GuiConfig.validate(config).isEmpty());
        config.set(menu + ".items.empty.slot", 49);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("empty") && error.contains("controls")));
        config.set(menu + ".layout.overlay.empty", 21);
        assertTrue(GuiConfig.validate(config).isEmpty());
        config.set(menu + ".layout.overlay.empty", 49);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("overlay.empty")));
        config.set(menu + ".layout.overlay.empty", null);
        config.set(menu + ".items.empty.slot", 22);
        config.set(menu + ".layout.content", List.of(45, 48, 49, 53));
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("content slot")));
    }

    @Test
    void voteBarRejectsInvalidLengthAndNonTextGlyphsBeforeReload() {
        String base = "voting.menus.ballot.presentation";
        for (Object value : List.of(0, -1, 65, "8")) {
            var config =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile());
            config.set(base + ".bar-length", value);
            assertTrue(
                    GuiConfig.validate(config).stream()
                            .anyMatch(error -> error.contains("bar-length")));
        }
        var config =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        config.set(base + ".bar-filled", 1);
        config.set(base + ".bar-empty", List.of("."));
        assertEquals(
                2,
                GuiConfig.validate(config).stream()
                        .filter(error -> error.contains("bar-"))
                        .count());
    }

    @Test
    void guiPlaceholdersRetainPlayerAndStepFormatting() throws Exception {
        var document = new YamlConfiguration();
        document.set("test.title", "&7<%player%&7> &f%step%");
        activate(document);
        var step = LegacyText.component("&#123abc&l设置出生点");
        var title = GuiConfig.component("test.title", Map.of("player", "&aAlice", "step", step));
        assertEquals("<Alice> 设置出生点", PlainTextComponentSerializer.plainText().serialize(title));
        String encoded = LegacyText.serialize(title);
        assertTrue(encoded.contains("§aAlice"), encoded);
        assertTrue(encoded.contains("§x§1§2§3§a§b§c§l设置出生点"), encoded);
    }

    @Test
    void configuredItalicsRenderWhileUnstyledItemsKeepNormalText() throws Exception {
        var document = new YamlConfiguration();
        document.set("test.italic", "&o说明");
        document.set("test.normal", "说明");
        activate(document);
        assertEquals(
                net.kyori.adventure.text.format.TextDecoration.State.TRUE,
                GuiConfig.component("test.italic")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
        assertEquals(
                net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                GuiConfig.component("test.normal")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
    }

    @Test
    void laserBoxBallotHasItsNameInEveryState() throws Exception {
        var bundled =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        String path = "voting.menus.ballot.items.games.laserbox";
        assertEquals("CROSSBOW", bundled.getString(path + ".material"));
        // Use the fallback material because Paper's material registry needs a running server.
        bundled.set(path + ".material", null);
        activate(bundled);
        var fallback =
                new GuiConfig.ItemSpec(
                        -1,
                        org.bukkit.Material.CROSSBOW,
                        net.kyori.adventure.text.Component.empty(),
                        List.of(),
                        false);
        var plain = PlainTextComponentSerializer.plainText();
        for (String state : List.of("available", "selected", "leading")) {
            var item =
                    GuiConfig.item(
                            path,
                            state,
                            Map.of("bar", "■■□□□□□□", "votes", 2, "percentage", 25),
                            fallback);
            assertTrue(plain.serialize(item.title()).contains("激光方盒"), state);
            assertEquals(org.bukkit.Material.CROSSBOW, item.material());
            assertFalse(item.lore().isEmpty());
            assertTrue(
                    item.lore().stream()
                            .map(plain::serialize)
                            .anyMatch(line -> line.contains("2票")));
            assertEquals(state.equals("selected"), item.glint());
        }
    }

    @Test
    void everyPrepareStepRendersDescriptionStateAndActionInsteadOfConfigurationKeys()
            throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        activate(document);
        String item = "map-editor.menus.step-list.items.step";
        var plain = PlainTextComponentSerializer.plainText();
        for (StepCaptureType type : StepCaptureType.values()) {
            String actionPath =
                    item
                            + ".actions."
                            + type.name().toLowerCase(Locale.ROOT).replace('_', '-')
                            + ".title";
            assertTrue(document.isString(actionPath), actionPath);
            String action = GuiConfig.text(actionPath);
            var rendered =
                    GuiConfig.lines(
                            item + ".lore",
                            Map.of(
                                    "number",
                                    1,
                                    "title",
                                    "测试步骤",
                                    "description",
                                    "设置目标位置",
                                    "state",
                                    "待设置",
                                    "action",
                                    action));
            assertEquals(
                    List.of("设置目标位置", "待设置", action),
                    rendered.stream().map(LegacyText::component).map(plain::serialize).toList(),
                    type.name());
            assertFalse(action.contains("%"), action);
            assertFalse(action.startsWith("map-editor."), action);
        }
    }

    @Test
    void invalidMenuFieldsReportTheirPaths() {
        var document = new YamlConfiguration();
        document.set("daily.menus.lobby-screen.size", 27);
        document.set("daily.menus.lobby-screen.layout.content", List.of(11, 11, 99));
        document.set("daily.menus.lobby-screen.items.close.slot", 28);
        document.set("daily.menus.lobby-screen.items.close.material", "NOT_A_MATERIAL");
        document.set("daily.menus.lobby-screen.items.close.use", "missing");
        List<String> errors = GuiConfig.validate(document);
        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.contains("layout.content")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.slot")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.material")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("items.close.use")));
    }

    @Test
    void partialOverridesUseBundledMenuBoundsAndButtonTemplates() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.set("example.menus.list.size", 27);
        defaults.set("buttons.close.title", "Close");
        var document = new YamlConfiguration();
        document.setDefaults(defaults);
        document.set("example.menus.list.items.close.use", "close");
        document.set("example.menus.list.items.close.slot", 26);
        assertEquals(List.of(), GuiConfig.validate(document));
        activate(document);
        assertEquals(
                "Close",
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                GuiConfig.item("example.menus.list.items.close", Map.of())
                                        .title()));

        document.set("example.menus.list.items.close.slot", 27);
        assertEquals(1, GuiConfig.validate(document).size());
    }

    @Test
    void shrinkingAMenuValidatesInheritedSlotsAndRejectsInvalidFooterAndGlintTypes() {
        var defaults = new YamlConfiguration();
        defaults.set("example.menus.list.size", 54);
        defaults.set("example.menus.list.layout.content", List.of(0, 26));
        defaults.set("example.menus.list.items.close.slot", 49);
        var document = new YamlConfiguration();
        document.setDefaults(defaults);
        document.set("example.menus.list.size", 27);
        document.set("example.menus.list.layout.footer.next", 27);
        document.set("example.menus.list.layout.footer.previous", "0");
        document.set("example.menus.list.items.close.states.selected.glint", "true");
        var errors = GuiConfig.validate(document);
        assertEquals(5, errors.size(), errors.toString());
        for (String field : List.of("items.close.slot", "footer.next", "footer.previous", "glint"))
            assertTrue(errors.stream().anyMatch(error -> error.contains(field)), field);
    }

    @Test
    void guiArgumentsAreInsertedOnceAndRetainFormatting() throws Exception {
        var document = new YamlConfiguration();
        document.set("test.title", "&7%player% / %state%");
        activate(document);
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("player", LegacyText.component("&a%state% $1\\name"));
        values.put("state", "ready");
        assertEquals(
                "%state% $1\\name / ready",
                LegacyText.plainText(GuiConfig.text("test.title", values)));
    }

    @Test
    void menuAndControlReadersKeepSlotsInsideTheInventory() throws Exception {
        var document = new YamlConfiguration();
        document.set("daily.menus.lobby-screen.size", 18);
        document.set("daily.menus.lobby-screen.layout.content", List.of(-1, 11, 11, 90));
        document.set("daily.menus.lobby-screen.items.close.slot", 90);
        activate(document);

        GuiConfig.MenuSpec menu =
                GuiConfig.menu("daily.menus.lobby-screen", 27, "Lobby", List.of(11, 13));
        assertEquals(27, menu.size());
        assertEquals(List.of(11), menu.contentSlots());
        assertEquals(22, ConfiguredGui.slot("daily.menus.lobby-screen.items.close", 22));
        assertFalse(
                GuiConfig.slots("daily.menus.lobby-screen.layout.content", List.of()).contains(90));
    }

    @Test
    void unusablePageContentIsRejectedButAnExplicitEmptyBorderIsPreserved() throws Exception {
        var document = new YamlConfiguration();
        document.set("map-editor.menus.step-list.size", 27);
        document.set("map-editor.menus.step-list.layout.content", List.of(3));
        document.set("map-editor.menus.step-list.items.next.slot", 3);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("outside fixed controls")));
        document.set("map-editor.menus.step-list.layout.content", List.of());
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(error -> error.contains("outside fixed controls")));
        document.set("map-editor.menus.step-list.layout.content", List.of(4));
        document.set("map-editor.menus.step-list.layout.border", List.of());
        assertEquals(List.of(), GuiConfig.validate(document));
        activate(document);
        assertEquals(
                List.of(),
                GuiConfig.slots("map-editor.menus.step-list.layout.border", List.of(18, 19)));
    }

    @Test
    void sharedItemOverridesAreValidatedAgainstTheirActualWindowBounds() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        String family = "map-editor.menus.list-editor";
        config.set(family + ".items.view.slot", 10);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("screens.manage.layout.footer.view")));
        config.set(family + ".screens.manage.layout.footer.view", 2);
        assertEquals(List.of(), GuiConfig.validate(config));
        config.set(family + ".items.next.slot", 20);
        config.set(family + ".screens.entries.layout.content", List.of(20));
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(error -> error.contains("outside fixed controls")));
        config.set(family + ".screens.entries.layout.footer.next", 5);
        assertEquals(List.of(), GuiConfig.validate(config));
    }

    @Test
    void simultaneousControlsCannotShareASlotButMutuallyExclusiveNavigationCan() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        assertEquals(List.of(), GuiConfig.validate(config));
        config.set("map-editor.menus.list-editor.items.delete.slot", 4);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(
                                error ->
                                        error.contains("screens.edit controls")
                                                && error.contains("share slot 4")));
        config.set("map-editor.menus.list-editor.screens.edit.layout.footer.delete", 7);
        assertEquals(List.of(), GuiConfig.validate(config));
    }

    @Test
    void centeredContentValidatesCountsSlotsAndInheritedControls() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        String path = "daily.menus.statistics-screen.screens.detail.layout.content";
        assertEquals(List.of(), GuiConfig.validate(config));
        config.set(path + ".3", List.of(1, 2));
        assertTrue(GuiConfig.validate(config).stream().anyMatch(e -> e.contains(path + ".3")));
        config.set(path + ".3", List.of(1, 1, 54));
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(e -> e.contains("duplicate or invalid")));
        config.set(path, List.of(49));
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(e -> e.contains("must leave at least")));
        config.set(path, List.of(16, 10));
        assertEquals(List.of(), GuiConfig.validate(config));
    }

    @Test
    void rawLoreTemplatesRetainStateAndSharedButtonInheritance() throws Exception {
        var document = new YamlConfiguration();
        document.set("buttons.record.lore", List.of("base", "%records%"));
        document.set("buttons.record.states.ranked.lore", List.of("ranked", "%records%"));
        document.set("sample.items.card.use", "record");
        activate(document);
        assertEquals(
                List.of("base", "%records%"),
                GuiConfig.itemLoreTemplate("sample.items.card", null));
        assertEquals(
                List.of("ranked", "%records%"),
                GuiConfig.itemLoreTemplate("sample.items.card", "ranked"));
        document.set("sample.items.card.lore", List.of("custom", "%records%", "extra"));
        assertEquals(
                List.of("custom", "%records%", "extra"),
                GuiConfig.itemLoreTemplate("sample.items.card", "ranked"));
        document.set("sample.items.card.states.ranked.lore", List.of());
        assertEquals(List.of(), GuiConfig.itemLoreTemplate("sample.items.card", "ranked"));
    }

    @Test
    void riptideWindowBoundsAndOverlaysAreCheckedWithoutRejectingExclusiveControls() {
        var defaults =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        var config = new YamlConfiguration();
        config.setDefaults(defaults);
        String base = "map-editor.games.riptide-rush.menus.course-editor";
        assertEquals(List.of(), GuiConfig.validate(config));
        config.set(base + ".items.save-building.slot", 28);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(e -> e.contains("workshop.layout.footer.save-building")));
        config.set(base + ".items.save-building.slot", null);
        config.set(base + ".preview.layout.overlay.invalid", 46);
        assertTrue(
                GuiConfig.validate(config).stream()
                        .anyMatch(e -> e.contains("overlaps a fixed control")));
    }

    private static void activate(YamlConfiguration document) throws ReflectiveOperationException {
        Field field = GuiConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, document);
    }

    @Test
    void dailyLobbyOverlayCannotCoverAFixedControlAndMapLimitMustBeNumeric() throws Exception {
        var document =
                YamlConfiguration.loadConfiguration(
                        java.nio.file.Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(
                GuiConfig.validate(document).isEmpty(),
                () -> GuiConfig.validate(document).toString());
        document.set("daily.menus.party-screen.layout.overlay.empty", 45);
        assertTrue(
                GuiConfig.validate(document).stream().anyMatch(e -> e.contains("overlay.empty")));
        document.set("daily.menus.game-selection-screen.presentation.map-preview-limit", -1);
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(e -> e.contains("map-preview-limit")));
        document.set("daily.menus.game-selection-screen.presentation.map-preview-limit", "3");
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(e -> e.contains("map-preview-limit")));
    }

    @Test
    void mapListCapacityAndEmptyOverlayAreValidatedAgainstItsActualControls() {
        var document =
                YamlConfiguration.loadConfiguration(Path.of("src/main/resources/gui.yml").toFile());
        assertTrue(
                GuiConfig.validate(document).isEmpty(),
                () -> GuiConfig.validate(document).toString());
        document.set("map-editor.menus.area-list.layout.content", List.of(47, 53));
        assertTrue(
                GuiConfig.validate(document).stream()
                        .anyMatch(e -> e.contains("area-list.layout.content")));
        document.set("map-editor.menus.area-list.layout.overlay.empty", 47);
        assertTrue(
                GuiConfig.validate(document).stream().anyMatch(e -> e.contains("overlay.empty")));
    }
}
