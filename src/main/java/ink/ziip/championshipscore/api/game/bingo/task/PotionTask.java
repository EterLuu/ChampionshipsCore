package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Collect {@code count} of a specific <em>effect</em> potion — e.g. a Potion of Strength, a Splash
 * Potion of Poison, a Lingering Potion of Swiftness. Distinct from a plain {@link ItemTask} for the
 * {@code POTION} material (which any potion satisfies): completion requires the held potion's base
 * effect to match, regardless of its potency/duration (strong/long variants all count).
 *
 * <p>The effect is stored as its lower-case vanilla key ({@code strength}, {@code night_vision}, …) so
 * the task stays version-agnostic; it drives both the localized name (via the vanilla
 * {@code item.minecraft.*.effect.*} translation keys) and the per-effect map sprite.
 */
public record PotionTask(Form form, String effect, int count, Dimension dimension) implements TaskData {

    /** The three potion item forms, each carrying its material and the atlas/translation infix. */
    public enum Form {
        NORMAL(Material.POTION, "potion"),
        SPLASH(Material.SPLASH_POTION, "splash_potion"),
        LINGERING(Material.LINGERING_POTION, "lingering_potion");

        public final Material material;
        /** Shared by the map-atlas key ({@code <infix>/<effect>}) and the vanilla translation key. */
        public final String infix;

        Form(Material material, String infix) {
            this.material = material;
            this.infix = infix;
        }

        public static @Nullable Form parse(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "normal", "potion", "drink", "drinkable" -> NORMAL;
                case "splash", "splash_potion" -> SPLASH;
                case "lingering", "lingering_potion" -> LINGERING;
                default -> null;
            };
        }
    }

    /**
     * Every brewable potion type a card can fairly ask for. Includes the ominous effects
     * (oozing/weaving/wind_charging/infestation) and the brewable bases (mundane/thick/awkward — they
     * share the no-effect default appearance). Excludes only the un-brewable water bottle and the
     * creative/command-only luck. Used to expand a {@code "*"} effect in the card pool.
     */
    public static final List<String> BREWABLE = List.of(
            "night_vision", "invisibility", "leaping", "fire_resistance", "swiftness", "slowness",
            "water_breathing", "healing", "harming", "poison", "regeneration", "strength", "weakness",
            "turtle_master", "slow_falling",
            "oozing", "weaving", "wind_charging", "infestation",
            "mundane", "thick", "awkward");

    public PotionTask {
        if (form == null) form = Form.NORMAL;
        effect = effect == null ? "" : effect.trim().toLowerCase(Locale.ROOT);
        count = Math.clamp(count, 1, 64);
        dimension = dimension == null ? Dimension.OVERWORLD : dimension;
    }

    /** The base {@link PotionType} (no strong/long modifier), or {@code null} if the effect is unknown. */
    public @Nullable PotionType potionType() {
        try {
            return PotionType.valueOf(effect.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public TaskType getType() {
        // Rides the ITEM type: filtered/enabled alongside item-collection tasks, but completed via its
        // own potion-aware scan (see BingoArea/BingoRound), not the material-only item path.
        return TaskType.ITEM;
    }

    @Override
    public String objectiveId() {
        return "potion:" + form.name().toLowerCase(Locale.ROOT) + ":" + effect;
    }

    @Override
    public Component getName() {
        // Vanilla potion display keys, e.g. item.minecraft.splash_potion.effect.strength → "投掷型力量药水".
        return Component.text().color(NamedTextColor.YELLOW)
                .append(Component.translatable("item.minecraft." + form.infix + ".effect." + effect))
                .build();
    }

    @Override
    public Component[] getItemDescription() {
        return new Component[]{MessageService.global().component("task.collect_potion")};
    }

    @Override
    public Component getChatDescription() {
        return Component.text().append(getItemDescription()).build();
    }

    @Override
    public boolean shouldItemGlow() {
        return false;
    }

    @Override
    public Material getDisplayMaterial(CardDisplayInfo context) {
        return form.material;
    }

    @Override
    public int getRequiredAmount() {
        return count;
    }

    @Override
    public TaskData setRequiredAmount(int newAmount) {
        return new PotionTask(form, effect, newAmount, dimension);
    }

    @Override
    public boolean isTaskEqual(TaskData other) {
        return other instanceof PotionTask p && form == p.form && effect.equals(p.effect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PotionTask p)) return false;
        return form == p.form && effect.equals(p.effect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(form, effect);
    }
}
