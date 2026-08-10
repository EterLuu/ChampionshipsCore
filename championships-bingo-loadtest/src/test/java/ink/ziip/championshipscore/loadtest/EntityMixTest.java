package ink.ziip.championshipscore.loadtest;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMixTest {
    @Test
    void stressMixUsesVanillaLandCapRatioWithoutVillagers() {
        assertFalse(ChunkStressController.MONSTER_TYPES.contains(EntityType.VILLAGER));
        assertFalse(ChunkStressController.CREATURE_TYPES.contains(EntityType.VILLAGER));

        int monsters = 0;
        int creatures = 0;
        for (int index = 0; index < 800; index++) {
            EntityType type = ChunkStressController.entityTypeFor(index);
            if (ChunkStressController.MONSTER_TYPES.contains(type)) monsters++;
            if (ChunkStressController.CREATURE_TYPES.contains(type)) creatures++;
        }

        assertEquals(700, monsters);
        assertEquals(100, creatures);
        assertTrue(monsters + creatures == 800);
    }
}
