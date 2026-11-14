package eu.amadeos.apps.artstudio.service.art;

import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;

/**
 * Draws a recipe as a picture of any size. Plain Java2D on the calling thread, no network, no files: the
 * same recipe gives the same picture, and at another size the same composition.
 */
public final class ArtRenderer {
    /** The largest picture: an A2 poster at 300 dpi has 35 million pixels. */
    public static final long MAX_PIXELS = 50_000_000L;

    /** Draws the picture; it cannot be stopped. */
    public BufferedImage render(Recipe recipe, int width, int height) {
        return render(recipe, width, height, () -> false);
    }

    /**
     * Draws the picture.
     *
     * @param cancelled asked now and then; true stops the drawing
     * @throws java.util.concurrent.CancellationException if {@code cancelled} said so
     * @throws IllegalArgumentException                     if the size is empty or too large
     */
    public BufferedImage render(Recipe recipe, int width, int height, BooleanSupplier cancelled) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Picture size out of range: " + width + " × " + height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(recipe.palette().paper());
            g.fillRect(0, 0, width, height);
            Sketch sketch = new Sketch(image, g, recipe, cancelled);
            painter(recipe.style()).paint(sketch);
        } finally {
            g.dispose();
        }
        if (recipe.paper() && recipe.style() != Style.MOSAIC) grain(image, recipe.seed(), cancelled);
        return image;
    }

    /** The painter of a style. */
    static StylePainter painter(Style style) {
        return switch (style) {
            case FLOW -> LineStyles::flow;
            case WATERCOLOUR -> AreaStyles::watercolour;
            case MOSAIC -> MosaicStyle::paint;
            case CIRCLES -> AreaStyles::circles;
            case GRID -> AreaStyles::grid;
            case SHAPES -> AreaStyles::shapes;
            case PLANTS -> LineStyles::plants;
            case LANDSCAPE -> AreaStyles::landscape;
            case WAVES -> LineStyles::waves;
            case TILES -> LineStyles::tiles;
            case DOTS -> AreaStyles::dots;
            case ROSETTE -> LineStyles::rosette;
            case ATTRACTOR -> FieldStyles::attractor;
            case REACTION -> FieldStyles::reaction;
            case MARBLE -> FieldStyles::marble;
            case CONTOURS -> FieldStyles::contours;
            case SKY -> FieldStyles::sky;
            case WHEELS -> PatternStyles::wheels;
            case LABYRINTH -> PatternStyles::labyrinth;
            case GROWTH -> PatternStyles::growth;
            case TRIANGLES -> PatternStyles::triangles;
            case HALFTONE -> PatternStyles::halftone;
            case CUBES -> SolidStyles::cubes;
            case SPHERES -> SolidStyles::spheres;
            case WIREFRAME -> SolidStyles::wireframe;
            case KNOT -> SolidStyles::knot;
            case SCULPTURE -> SolidStyles::sculpture;
        };
    }

    /** A fine grain like paper: every pixel a little lighter or darker. */
    static void grain(BufferedImage image, long seed, BooleanSupplier cancelled) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        SplittableRandom random = new SplittableRandom(seed ^ 0x5DEECE66DL);
        for (int i = 0; i < pixels.length; i++) {
            if ((i & 0xFFFFF) == 0 && cancelled.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("Picture no longer needed");
            }
            int rgb = pixels[i];
            int d = random.nextInt(13) - 6;
            int r = clamp(((rgb >> 16) & 255) + d);
            int g = clamp(((rgb >> 8) & 255) + d);
            int b = clamp((rgb & 255) + d);
            pixels[i] = (r << 16) | (g << 8) | b;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
