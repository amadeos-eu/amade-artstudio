package eu.amadeos.apps.artstudio.service.art;

import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * What a style draws with: the picture and its graphics, the random numbers and the noise of the seed, the
 * colours and the sliders as fractions from 0 to 1.
 * <p>
 * Styles measure in {@link #unit}: a 600th of the shorter side. A style that places things in units and draws
 * as many of them as the sliders and the shape ask (never as many as there are pixels) draws the same picture
 * at every size.
 */
public final class Sketch {
    /** The width of a 16:10 picture in units; {@link #area()} is 1 for it. */
    static final double REFERENCE_WIDTH = 960;
    static final double REFERENCE_HEIGHT = 600;

    public final BufferedImage image;
    public final Graphics2D g;
    public final SplittableRandom random;
    public final Noise noise;
    public final Palette palette;
    public final int width;
    public final int height;
    /** A 600th of the shorter side, in pixels. */
    public final double unit;
    public final double density;
    public final double scale;
    public final double chaos;
    public final double line;
    private final BooleanSupplier cancelled;

    Sketch(BufferedImage image, Graphics2D g, Recipe recipe, BooleanSupplier cancelled) {
        this.image = image;
        this.g = g;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.unit = Math.min(width, height) / 600.0;
        this.palette = recipe.palette();
        this.random = new SplittableRandom(recipe.seed() * 0x9E3779B97F4A7C15L + recipe.style().ordinal());
        this.noise = new Noise(recipe.seed() + 17L * recipe.style().ordinal());
        this.density = recipe.density() / 100.0;
        this.scale = recipe.scale() / 100.0;
        this.chaos = recipe.chaos() / 100.0;
        this.line = recipe.line() / 100.0;
        this.cancelled = cancelled;
    }

    /** Stops the drawing when the picture is no longer wanted. */
    public void checkCancelled() {
        if (cancelled.getAsBoolean()) throw new CancellationException("Picture no longer needed");
    }

    /** The area in units² compared with a 16:10 picture: depends on the shape only, never on the pixels. */
    public double area() {
        return (width / unit) * (height / unit) / (REFERENCE_WIDTH * REFERENCE_HEIGHT);
    }

    /** One of the palette's colours. */
    public Color pick() {
        return palette.colors().get(random.nextInt(palette.colors().size()));
    }

    /** Colour {@code i} of the palette, counting round. */
    public Color color(int i) {
        return palette.colors().get(Math.floorMod(i, palette.colors().size()));
    }

    /** A normally distributed random number with standard deviation 0.5. */
    public double gauss() {
        return Math.sqrt(-2 * Math.log(1 - random.nextDouble())) * Math.cos(2 * Math.PI * random.nextDouble()) * 0.5;
    }

    /** A round stroke {@code units} wide. */
    public BasicStroke stroke(double units) {
        return new BasicStroke((float) Math.max(0.3, units * unit), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color mix(Color a, Color b, double f) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * f),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    /** A colour between black (0), the colour (1) and white (2). */
    public static Color shade(Color c, double f) {
        if (f >= 1) return mix(c, Color.WHITE, Math.min(1, f - 1));
        return mix(Color.BLACK, c, Math.max(0, f));
    }

    /** {@link #mix} on packed RGB values. */
    public static int mixRgb(int a, int b, double f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** 0 to 1 with a soft start and end. */
    public static double smooth(double t) {
        double c = Math.min(1, Math.max(0, t));
        return c * c * (3 - 2 * c);
    }

    /** A value of a grid between its points; the grid wraps around. */
    public static double bilinear(double[] grid, int w, int h, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        int xa = Math.floorMod(x0, w);
        int xb = Math.floorMod(x0 + 1, w);
        int ya = Math.floorMod(y0, h);
        int yb = Math.floorMod(y0 + 1, h);
        return (grid[ya * w + xa] * (1 - fx) + grid[ya * w + xb] * fx) * (1 - fy)
                + (grid[yb * w + xa] * (1 - fx) + grid[yb * w + xb] * fx) * fy;
    }

    /**
     * 1024 colours from paper over the palette's colours to ink ({@code withPaperAndInk}), or through the colours
     * only, for values from 0 to 1.
     */
    public int[] ramp(boolean withPaperAndInk) {
        java.util.List<Color> stops = new java.util.ArrayList<>();
        if (withPaperAndInk) stops.add(palette.paper());
        stops.addAll(palette.colors());
        if (withPaperAndInk) stops.add(palette.ink());
        int[] lut = new int[1024];
        for (int i = 0; i < lut.length; i++) {
            double t = i / 1023.0 * (stops.size() - 1);
            int k = Math.min(stops.size() - 2, (int) t);
            lut[i] = mix(stops.get(k), stops.get(k + 1), t - k).getRGB() & 0xFFFFFF;
        }
        return lut;
    }

    /** The colour of the ramp at {@code t} from 0 to 1. */
    public static int at(int[] ramp, double t) {
        return ramp[(int) Math.round(Math.min(1, Math.max(0, t)) * (ramp.length - 1))];
    }

    /** The pixels of the picture, row by row, as RGB. */
    public int[] pixels() {
        return ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * Calls {@code row} for every row of the picture, on all processor cores. Only for work that depends on
     * nothing but the row: the random numbers must not be used in it.
     */
    public void eachRow(java.util.function.IntConsumer row) {
        java.util.stream.IntStream.range(0, height).parallel().forEach(y -> {
            if (y % 16 == 0) checkCancelled();
            row.accept(y);
        });
    }
}
