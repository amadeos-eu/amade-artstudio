package eu.amadeos.apps.artstudio.service.art;

import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtRendererTest {
    private final ArtRenderer renderer = new ArtRenderer();

    static Recipe recipe(Style style, long seed) {
        return Recipe.of(style, seed, Palette.BUILT_IN.get(style.ordinal() % Palette.BUILT_IN.size()));
    }

    static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @ParameterizedTest
    @EnumSource(Style.class)
    void everyStyleDrawsAPictureAndAlwaysTheSame(Style style) {
        BufferedImage a = renderer.render(recipe(style, 123), 240, 150);
        assertEquals(240, a.getWidth());
        assertEquals(150, a.getHeight());
        Set<Integer> colours = new HashSet<>();
        for (int p : pixels(a)) colours.add(p);
        assertTrue(colours.size() > 20, style + " drew only " + colours.size() + " colours");
        assertArrayEquals(pixels(a), pixels(renderer.render(recipe(style, 123), 240, 150)), style + " differs");
        assertFalse(java.util.Arrays.equals(pixels(a), pixels(renderer.render(recipe(style, 124), 240, 150))),
                style + " does not depend on the seed");
    }

    @Test
    void everyStyleWorksInEveryShapeAndAtTinySizes() {
        for (Style style : Style.values()) {
            for (Aspect aspect : Aspect.values()) {
                Recipe r = recipe(style, 9).withAspect(aspect);
                renderer.render(r, aspect.fit(90, 90).width, aspect.fit(90, 90).height);
            }
            renderer.render(recipe(style, 9), 1, 1);
            renderer.render(recipe(style, 9).withDensity(100).withScale(0).withChaos(100).withLine(100), 64, 40);
            renderer.render(recipe(style, 9).withDensity(0).withScale(100).withChaos(0).withLine(0), 64, 40);
        }
    }

    @Test
    void paperGrainChangesEveryPixelALittle() {
        Recipe grid = recipe(Style.GRID, 5);
        int[] with = pixels(renderer.render(grid, 100, 60));
        int[] without = pixels(renderer.render(grid.withPaper(false), 100, 60));
        int changed = 0;
        for (int i = 0; i < with.length; i++) {
            int d = Math.abs(((with[i] >> 8) & 255) - ((without[i] >> 8) & 255));
            assertTrue(d <= 6, "grain is at most 6 levels");
            if (d > 0) changed++;
        }
        assertTrue(changed > with.length / 2);
    }

    /** At four times the size the picture is the same composition: scaled down, it matches the small one. */
    @Test
    void largerPicturesKeepTheComposition() {
        for (Style style : new Style[]{Style.GRID, Style.SHAPES, Style.LANDSCAPE, Style.MOSAIC, Style.CIRCLES}) {
            Recipe r = recipe(style, 77).withPaper(false);
            BufferedImage small = renderer.render(r, 160, 100);
            BufferedImage large = renderer.render(r, 640, 400);
            BufferedImage down = new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = down.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(large, 0, 0, 160, 100, null);
            g.dispose();
            int[] a = pixels(small);
            int[] b = pixels(down);
            long diff = 0;
            for (int i = 0; i < a.length; i++) diff += Math.abs(((a[i] >> 8) & 255) - ((b[i] >> 8) & 255));
            double mean = (double) diff / a.length;
            assertTrue(mean < 30, style + " differs by " + mean + " on average");
        }
    }

    @Test
    void sizesOutOfRange() {
        Recipe r = recipe(Style.FLOW, 1);
        assertThrows(IllegalArgumentException.class, () -> renderer.render(r, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(r, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(r, 10_000, 10_000));
    }

    @Test
    void drawingStopsWhenNoLongerWanted() {
        for (Style style : Style.values()) {
            AtomicInteger asked = new AtomicInteger();
            assertThrows(CancellationException.class,
                    () -> renderer.render(recipe(style, 3), 400, 250, () -> asked.incrementAndGet() > 0), style::name);
        }
    }

    @Test
    void noiseIsSmoothAndRepeatable() {
        Noise a = new Noise(1);
        Noise b = new Noise(1);
        double min = 0;
        double max = 0;
        for (int i = 0; i < 2000; i++) {
            double x = i * 0.137;
            double y = i * 0.071;
            double v = a.at(x, y);
            assertEquals(v, b.at(x, y));
            assertTrue(Math.abs(v - a.at(x + 0.001, y)) < 0.01, "smooth");
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min > -1.5 && max < 1.5);
        assertTrue(max - min > 1, "uses its range");
        assertTrue(new Noise(2).at(3.3, 4.4) != a.at(3.3, 4.4));
    }

    @Test
    void mosaicShadesTowardsTheEdges() {
        assertEquals(0xFF8040, MosaicStyle.shade(0xFF8040, 0));
        int far = MosaicStyle.shade(0xFF8040, 100);
        assertTrue(((far >> 16) & 255) < 255 && ((far >> 16) & 255) >= (int) (255 * 0.82));
    }
}
