package ink.ziip.championshipscore.api.game.buildmart.gui;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marker holder for the hub blueprint-library menu, so the click listener can recognise it and route the
 * selection back to the right area/team.
 */
@Getter
public final class BlueprintMenuHolder implements InventoryHolder {
    private final BuildMartArea area;
    private final ChampionshipTeam team;
    private Inventory inventory;

    /**
     * The action currently awaiting a second (confirming) click, as {@code "SUBMIT:N0"} / {@code "REFRESH:N1"},
     * or {@code null} when nothing is armed. Set on the first click, cleared once confirmed or cancelled.
     */
    @Setter
    @Nullable
    private String armed;

    public BlueprintMenuHolder(BuildMartArea area, ChampionshipTeam team) {
        this.area = area;
        this.team = team;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
