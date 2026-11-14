package eu.amadeos.apps.artstudio.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Close relatives of a recipe: another seed, a little more or less density and size, now and then another
 * palette. The relatives depend on the recipe only, so the same picture always has the same relatives.
 */
public final class Variations {
    /** Pictures in the grid; the current one is in the middle. */
    public static final int COUNT = 9;
    public static final int MIDDLE = 4;

    private Variations() {
    }

    /** Nine recipes: the given one at {@link #MIDDLE}, eight relatives around it. */
    public static List<Recipe> around(Recipe recipe) {
        SplittableRandom random = new SplittableRandom(recipe.toLine().hashCode() * 31L + recipe.seed());
        List<Recipe> all = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            if (i == MIDDLE) {
                all.add(recipe);
                continue;
            }
            long seed = random.nextLong(Recipe.MAX_SEED + 1);
            Recipe r = recipe.withSeed(seed)
                    .withDensity(recipe.density() + random.nextInt(-15, 16))
                    .withScale(recipe.scale() + random.nextInt(-15, 16))
                    .withChaos(recipe.chaos() + random.nextInt(-10, 11));
            // About one in four in other colours, unless the colours were made by hand.
            if (random.nextInt(4) == 0 && !recipe.palette().id().equals(Palette.CUSTOM)) {
                r = r.withPalette(Palette.BUILT_IN.get(random.nextInt(Palette.BUILT_IN.size())));
            }
            all.add(r);
        }
        return all;
    }
}
