package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.api.game.bingo.card.CardSize;

/**
 * Display preferences that affect how tasks render on a card.
 */
public record CardDisplayInfo(CardSize size,
                              TaskDisplayMode advancementDisplay,
                              TaskDisplayMode statisticDisplay,
                              boolean allowViewingOtherCards,
                              boolean locksTasks) {

    public CardDisplayInfo(CardSize size, TaskDisplayMode advancementDisplay,
                           TaskDisplayMode statisticDisplay, boolean allowViewingOtherCards) {
        this(size, advancementDisplay, statisticDisplay, allowViewingOtherCards, false);
    }

    public static final CardDisplayInfo DEFAULT = new CardDisplayInfo(
            CardSize.X5,
            TaskDisplayMode.UNIQUE_TASK_ITEMS,
            TaskDisplayMode.UNIQUE_TASK_ITEMS,
            false,
            false);
}
