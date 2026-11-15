package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.worker.support.WorkerPresentationFixture;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.map.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.*;

class WorkerCardsTest {
    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void normalCardsCreateOneViewPerTeamAndOneAggregateView(int width) {
        var f = new Fixture(width, BingoRemix.NONE);
        f.cards.createViews(null);
        assertEquals(Arrays.asList("1:null", "2:null", "null:null"), f.targets);
        f.assertViewsConfigured();
        f.cards.createViews(null);
        assertEquals(3, f.views.size());
    }

    @Test
    void differentialViewsAreOnlyCreatedForPlayingParticipants() {
        var f = new Fixture(3, BingoRemix.DIFFERENTIAL);
        f.cards.createViews(null);
        assertEquals(5, f.targets.size());
        for (int i = 0; i < 2; i++)
            assertEquals(
                    (i + 1) + ":" + f.manifest.participants().get(i).uuid(), f.targets.get(i + 3));
        f.assertViewsConfigured();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5})
    void partiallyCreatedViewsAreRolledBackAndRetryBuildsTheWholeCardSet(int failureAt) {
        var f = new Fixture(3, BingoRemix.DIFFERENTIAL);
        f.failAt = failureAt;
        assertThrows(IllegalStateException.class, () -> f.cards.createViews(null));
        assertTrue(f.views.stream().allMatch(view -> view.renderers.isEmpty()));
        int previous = f.views.size();
        f.failAt = -1;
        f.cards.createViews(null);
        assertEquals(previous + 5, f.views.size());
        assertTrue(
                f.views.subList(previous, f.views.size()).stream()
                        .allMatch(view -> view.renderers.size() == 1));
        f.cards.createViews(null);
        assertEquals(previous + 5, f.views.size());
    }

    @Test
    void titlesUseTheFrozenTeamAndTemplate() {
        var f = new Fixture(3, BingoRemix.NONE);
        assertEquals(
                "Card Team1",
                PlainTextComponentSerializer.plainText().serialize(f.cards.cardTitle(1)));
    }

    static final class Fixture {
        final MatchManifest manifest;
        final WorkerCards cards;
        final List<View> views = new ArrayList<>();
        final List<String> targets = new ArrayList<>();
        int failAt = -1;

        Fixture(int width, BingoRemix remix) {
            manifest = WorkerPresentationFixture.manifest(width, remix, Map.of());
            cards =
                    new WorkerCards(
                            new NamespacedKey("test", "bingo_card"),
                            manifest,
                            4,
                            (team, player) -> {
                                if (views.size() == failAt)
                                    throw new IllegalStateException("renderer failure");
                                targets.add(team + ":" + player);
                                return renderer();
                            },
                            world -> {
                                var view = new View();
                                views.add(view);
                                return view.proxy;
                            });
        }

        void assertViewsConfigured() {
            for (var view : views) {
                assertEquals(MapView.Scale.NORMAL, view.scale);
                assertFalse(view.tracking);
                assertFalse(view.unlimited);
                assertEquals(1, view.renderers.size());
                assertNotSame(view.original, view.renderers.getFirst());
            }
        }
    }

    private static MapRenderer renderer() {
        return new MapRenderer(false) {
            public void render(MapView view, MapCanvas canvas, Player player) {}
        };
    }

    private static final class View {
        final MapRenderer original = renderer();
        final List<MapRenderer> renderers = new ArrayList<>(List.of(original));
        MapView.Scale scale;
        boolean tracking = true, unlimited = true;
        final MapView proxy =
                (MapView)
                        Proxy.newProxyInstance(
                                MapView.class.getClassLoader(),
                                new Class<?>[] {MapView.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "hashCode" -> System.identityHashCode(p);
                                            case "equals" -> p == a[0];
                                            case "setScale" -> {
                                                scale = (MapView.Scale) a[0];
                                                yield null;
                                            }
                                            case "setTrackingPosition" -> {
                                                tracking = (Boolean) a[0];
                                                yield null;
                                            }
                                            case "setUnlimitedTracking" -> {
                                                unlimited = (Boolean) a[0];
                                                yield null;
                                            }
                                            case "getRenderers" -> new ArrayList<>(renderers);
                                            case "removeRenderer" -> renderers.remove(a[0]);
                                            case "addRenderer" -> {
                                                renderers.add((MapRenderer) a[0]);
                                                yield null;
                                            }
                                            default -> throw new AssertionError(m.getName());
                                        });
    }
}
