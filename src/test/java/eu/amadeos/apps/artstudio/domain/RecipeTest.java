package eu.amadeos.apps.artstudio.domain;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeTest {
    static final Recipe FLOW = new Recipe(Style.FLOW, 48213, Palette.AUTUMN, 55, 40, 65, 30, true, Aspect.SCREEN);

    @Test
    void textRoundTrip() {
        String text = FLOW.toText();
        assertTrue(text.startsWith(Recipe.HEADER + "\n"));
        assertTrue(text.contains("style=flow\n"));
        assertTrue(text.contains("seed=48213\n"));
        assertTrue(text.contains("colors=#F3EBDD #2B2118 #C8553D"));
        assertEquals(FLOW, Recipe.parse(text));
    }

    @Test
    void lineRoundTrip() {
        String line = FLOW.toLine();
        assertFalse(line.contains("\n"));
        assertTrue(line.startsWith("style=flow; seed=48213; palette=autumn; "));
        assertEquals(FLOW, Recipe.parse(line));
    }

    @Test
    void ownColoursSurvive() {
        Recipe own = FLOW.withPalette(Palette.AUTUMN.withSwatch(3, new Color(0x123456)));
        Recipe back = Recipe.parse(own.toText());
        assertEquals(own, back);
        assertEquals(Palette.CUSTOM, back.palette().id());
        assertEquals(new Color(0x123456), back.palette().colors().get(1));
    }

    @Test
    void missingValuesTakeTheirDefaults() {
        Recipe r = Recipe.parse("style=dots\nseed=7\npalette=night");
        assertEquals(Recipe.of(Style.DOTS, 7, Palette.NIGHT), r);
        Recipe odd = Recipe.parse("style=grid; seed=1; palette=earth; density=999; scale=-5; chaos=x; paper=false; "
                + "aspect=nonsense");
        assertEquals(100, odd.density());
        assertEquals(0, odd.scale());
        assertEquals(50, odd.chaos());
        assertFalse(odd.paper());
        assertEquals(Aspect.SCREEN, odd.aspect());
    }

    @Test
    void notARecipe() {
        assertThrows(IllegalArgumentException.class, () -> Recipe.parse("hello"));
        assertThrows(IllegalArgumentException.class, () -> Recipe.parse("style=hexagons; seed=1; palette=night"));
        assertThrows(IllegalArgumentException.class, () -> Recipe.parse("style=flow; seed=abc; palette=night"));
        assertThrows(IllegalArgumentException.class, () -> Recipe.parse("style=flow; seed=1"));
        assertThrows(IllegalArgumentException.class, () -> Recipe.parse("style=flow; seed=1; colors=#FFFFFF"));
    }

    @Test
    void seedsAreShortAndNotNegative() {
        assertThrows(IllegalArgumentException.class, () -> FLOW.withSeed(-1));
        assertThrows(IllegalArgumentException.class, () -> FLOW.withSeed(Recipe.MAX_SEED + 1));
        assertEquals(Recipe.MAX_SEED, FLOW.withSeed(Recipe.MAX_SEED).seed());
    }

    @Test
    void withersChangeOneThing() {
        assertEquals(Style.MOSAIC, FLOW.withStyle(Style.MOSAIC).style());
        assertEquals(1, FLOW.withDensity(1).density());
        assertEquals(2, FLOW.withScale(2).scale());
        assertEquals(3, FLOW.withChaos(3).chaos());
        assertEquals(4, FLOW.withLine(4).line());
        assertFalse(FLOW.withPaper(false).paper());
        assertEquals(Aspect.SQUARE, FLOW.withAspect(Aspect.SQUARE).aspect());
        assertEquals(Palette.OCEAN, FLOW.withPalette(Palette.OCEAN).palette());
        assertEquals(FLOW, FLOW.withStyle(Style.GRID).withStyle(Style.FLOW));
        assertEquals("flow-48213", FLOW.fileName());
    }

    @Test
    void stylesHaveIdsAndKeys() {
        for (Style s : Style.values()) {
            assertEquals(s, Style.ofId(s.id()).orElseThrow());
            assertEquals("style." + s.id(), s.key());
        }
        assertEquals(Style.WAVES, Style.ofId(" WAVES ").orElseThrow());
        assertTrue(Style.ofId("hexagons").isEmpty());
        assertTrue(Style.ofId(null).isEmpty());
        assertEquals(List.of(Style.FLOW, Style.PLANTS, Style.WAVES, Style.TILES, Style.ROSETTE, Style.CONTOURS,
                        Style.WHEELS, Style.LABYRINTH, Style.GROWTH, Style.WIREFRAME),
                List.of(Style.values()).stream().filter(Style::drawsLines).toList());
    }

    @Test
    void newStylesComeAfterTheOldOnesSoOldRecipesKeepTheirPictures() {
        // The random numbers of a style depend on its place in the list: the first twelve must stay where they are.
        assertEquals(List.of(Style.FLOW, Style.WATERCOLOUR, Style.MOSAIC, Style.CIRCLES, Style.GRID, Style.SHAPES,
                Style.PLANTS, Style.LANDSCAPE, Style.WAVES, Style.TILES, Style.DOTS, Style.ROSETTE),
                List.of(Style.values()).subList(0, 12));
    }

    @Test
    void theStyleListShowsEveryStyleOnceSortedByGroup() {
        List<Style> shown = Style.shown();
        assertEquals(Style.values().length, shown.size());
        assertEquals(Set.of(Style.values()), Set.copyOf(shown));
        for (int i = 1; i < shown.size(); i++) {
            assertTrue(shown.get(i - 1).group().compareTo(shown.get(i).group()) <= 0, shown.get(i).name());
        }
        for (Style.Group group : Style.Group.values()) {
            assertTrue(shown.stream().filter(s -> s.group() == group).count() >= 5, group.name());
            assertEquals("style.group." + group.name().toLowerCase(java.util.Locale.ROOT), group.key());
        }
        assertEquals(Style.Group.SOLID, Style.SCULPTURE.group());
        assertEquals(Style.Group.NATURE, Style.SKY.group());
    }
}
