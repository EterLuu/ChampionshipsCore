package ink.ziip.championshipscore.api.game.craftingfeast.model;

/** The n-th guest of the shared sequence; {@code profile} is null for ordinary villagers. */
public record FeastGuest(int ticket, FeastRequest request, boolean vip, FeastGuestProfile profile) {
    public FeastGuest {
        if (ticket < 1
                || request == null
                || (profile != null && vip != profile.entity().equals("VILLAGER")))
            throw new IllegalArgumentException("Invalid Crafting Feast guest");
    }

    public boolean isSpecial() {
        return profile != null && !vip;
    }

    /** Base points before tips: VIP and special guests add their configured bonus. */
    public int basePoints(int vipBonusPercent) {
        int bonus = vip ? vipBonusPercent : isSpecial() ? profile.bonusPercent() : 0;
        return request.points() * (100 + bonus) / 100;
    }
}
