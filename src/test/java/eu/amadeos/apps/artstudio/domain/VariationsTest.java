package eu.amadeos.apps.artstudio.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariationsTest {
    @Test
    void nineWithTheRecipeInTheMiddle() {
        List<Recipe> all = Variations.around(RecipeTest.FLOW);
        assertEquals(Variations.COUNT, all.size());
        assertEquals(RecipeTest.FLOW, all.get(Variations.MIDDLE));
        Set<Long> seeds = new HashSet<>();
        for (Recipe r : all) {
            assertEquals(Style.FLOW, r.style());
            assertEquals(RecipeTest.FLOW.aspect(), r.aspect());
            assertTrue(Math.abs(r.density() - RecipeTest.FLOW.density()) <= 15);
            assertTrue(Math.abs(r.chaos() - RecipeTest.FLOW.chaos()) <= 10);
            seeds.add(r.seed());
        }
        assertEquals(Variations.COUNT, seeds.size(), "every relative has a seed of its own");
    }

    @Test
    void theSamePictureHasTheSameRelatives() {
        assertEquals(Variations.around(RecipeTest.FLOW), Variations.around(RecipeTest.FLOW));
        assertTrue(!Variations.around(RecipeTest.FLOW).equals(Variations.around(RecipeTest.FLOW.withSeed(1))));
    }

    @Test
    void ownColoursAreKept() {
        Recipe own = RecipeTest.FLOW.withPalette(Palette.AUTUMN.withSwatch(0, java.awt.Color.PINK));
        for (long seed = 0; seed < 20; seed++) {
            for (Recipe r : Variations.around(own.withSeed(seed))) assertEquals(own.palette(), r.palette());
        }
    }
}
