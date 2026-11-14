package eu.amadeos.apps.artstudio.service.art;

import java.awt.Color;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.List;

/**
 * The styles that colour every pixel from a field of values: attractor, reaction patterns, marble, contour lines
 * and night sky. Fields are calculated in units or on grids of their own size, so the picture is the same at
 * every size; the pixel loops run on all cores.
 */
final class FieldStyles {
    /** The attractor and its blur work on at most this many cells; larger pictures are scaled up from them. */
    static final int MAX_CELLS = 2_500_000;

    private FieldStyles() {
    }

    // ---------------------------------------------------------------- attractor

    /**
     * A chaotic map of two sine formulas, x' = sin(a·y) + c·cos(a·x), y' = sin(b·x) + d·cos(b·y): millions of
     * points pile up into fine, cloudy loops. How often a cell is hit decides its colour.
     */
    static void attractor(Sketch s) {
        double a = 0;
        double b = 0;
        double c = 0;
        double d = 0;
        // Parameters whose orbit fills a good part of its box instead of settling on a few points.
        for (int attempt = 0; attempt < 200; attempt++) {
            a = s.random.nextDouble(-2.2, 2.2);
            b = s.random.nextDouble(-2.2, 2.2);
            c = s.random.nextDouble(-1.6, 1.6) * (0.4 + s.chaos);
            d = s.random.nextDouble(-1.6, 1.6) * (0.4 + s.chaos);
            if (spread(a, b, c, d) > 0.35) break;
        }
        s.checkCancelled();
        int step = (int) Math.ceil(Math.sqrt((double) s.width * s.height / MAX_CELLS));
        int w = Math.max(1, s.width / step);
        int h = Math.max(1, s.height / step);
        double size = Math.min(w / (2 * (1 + Math.abs(c))), h / (2 * (1 + Math.abs(d)))) * (0.8 + 0.4 * s.scale);
        int[] count = new int[w * h];
        long n = (long) ((6 + 24 * s.density) * w * h);
        double x = 0.1;
        double y = 0.1;
        for (long i = 0; i < n; i++) {
            if ((i & 0xFFFFF) == 0) s.checkCancelled();
            double nx = Math.sin(a * y) + c * Math.cos(a * x);
            double ny = Math.sin(b * x) + d * Math.cos(b * y);
            x = nx;
            y = ny;
            int px = (int) (w / 2.0 + x * size);
            int py = (int) (h / 2.0 + y * size);
            if (px >= 0 && py >= 0 && px < w && py < h) count[py * w + px]++;
        }
        // A small blur against the grain of single points, then a logarithmic scale against the mean.
        double[] soft = new double[w * h];
        double top = 0;
        double sum = 0;
        int used = 0;
        for (int gy = 0; gy < h; gy++) {
            for (int gx = 0; gx < w; gx++) {
                double total = 0;
                int weights = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = gx + dx;
                        int yy = gy + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int weight = (dx == 0 ? 2 : 1) * (dy == 0 ? 2 : 1);
                        total += count[yy * w + xx] * weight;
                        weights += weight;
                    }
                }
                double v = total / weights;
                soft[gy * w + gx] = v;
                top = Math.max(top, v);
                if (v > 0) {
                    sum += v;
                    used++;
                }
            }
        }
        double mean = Math.max(1e-9, sum / Math.max(1, used));
        double scaleTop = Math.log1p(Math.max(top, 1e-9) / mean * 3);
        double gamma = 0.6 + 0.6 * (1 - s.line);
        int[] ramp = s.ramp(true);
        int[] out = s.pixels();
        int sw = s.width;
        int gridW = w;
        int gridH = h;
        s.eachRow(py -> {
            double gy = Math.min(gridH - 1.0, (py + 0.5) * gridH / s.height - 0.5);
            for (int px = 0; px < sw; px++) {
                double gx = Math.min(gridW - 1.0, (px + 0.5) * gridW / sw - 0.5);
                double v = step == 1 ? soft[py * gridW + px] : clampedBilinear(soft, gridW, gridH, gx, gy);
                if (v <= 0) continue;
                double t = Math.log1p(v / mean * 3) / scaleTop;
                out[py * sw + px] = Sketch.at(ramp, Math.pow(t, gamma));
            }
        });
    }

    /** How much of its box an orbit visits, 0 to 1, counted on a coarse grid. */
    private static double spread(double a, double b, double c, double d) {
        boolean[] seen = new boolean[64 * 64];
        double x = 0.1;
        double y = 0.1;
        int hits = 0;
        double mx = 1 + Math.abs(c);
        double my = 1 + Math.abs(d);
        for (int i = 0; i < 20000; i++) {
            double nx = Math.sin(a * y) + c * Math.cos(a * x);
            double ny = Math.sin(b * x) + d * Math.cos(b * y);
            x = nx;
            y = ny;
            int gx = (int) ((x / mx + 1) * 32);
            int gy = (int) ((y / my + 1) * 32);
            if (gx >= 0 && gy >= 0 && gx < 64 && gy < 64 && !seen[gy * 64 + gx]) {
                seen[gy * 64 + gx] = true;
                hits++;
            }
        }
        return hits / 4096.0;
    }

    /** Like {@link Sketch#bilinear}, but the grid ends at its edges instead of wrapping around. */
    static double clampedBilinear(double[] grid, int w, int h, double x, double y) {
        double cx = Math.max(0, Math.min(w - 1.0, x));
        double cy = Math.max(0, Math.min(h - 1.0, y));
        int x0 = Math.min(w - 1, (int) cx);
        int y0 = Math.min(h - 1, (int) cy);
        int x1 = Math.min(w - 1, x0 + 1);
        int y1 = Math.min(h - 1, y0 + 1);
        double fx = cx - x0;
        double fy = cy - y0;
        return (grid[y0 * w + x0] * (1 - fx) + grid[y0 * w + x1] * fx) * (1 - fy)
                + (grid[y1 * w + x0] * (1 - fx) + grid[y1 * w + x1] * fx) * fy;
    }

    // ---------------------------------------------------------------- reaction patterns

    /** Feed and kill rates of two substances that give spots, corals, a maze, worms and fine dots. */
    private static final double[][] REACTIONS = {{0.0367, 0.0649}, {0.0545, 0.062}, {0.029, 0.057}, {0.039, 0.058},
            {0.025, 0.06}};

    /**
     * Two substances that spread and react (the Gray-Scott model): from a few drops grow spots, corals or a
     * maze, depending on Chaos. Calculated on a grid of its own, which wraps around, then scaled to the picture.
     */
    static void reaction(Sketch s) {
        double shape = (double) s.width / s.height;
        int gw = Math.max(40, (int) Math.round(240 * Math.sqrt(s.area()) * Math.sqrt(shape / 1.6) * (1.3 - 0.6 * s.scale)));
        int gh = Math.max(30, (int) Math.round(gw / shape));
        double[] u = new double[gw * gh];
        double[] v = new double[gw * gh];
        double[] u2 = new double[gw * gh];
        double[] v2 = new double[gw * gh];
        Arrays.fill(u, 1);
        int drops = 6 + (int) (20 * s.density);
        for (int k = 0; k < drops; k++) {
            int cx = s.random.nextInt(gw);
            int cy = s.random.nextInt(gh);
            int r = 2 + s.random.nextInt(4);
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    int i = Math.floorMod(cy + y, gh) * gw + Math.floorMod(cx + x, gw);
                    u[i] = 0.5;
                    v[i] = 0.25 + 0.05 * s.random.nextDouble();
                }
            }
        }
        double[] rates = REACTIONS[(int) Math.round(s.chaos * (REACTIONS.length - 1))];
        double feed = rates[0];
        double kill = rates[1];
        double du = 0.16;
        double dv = 0.08;
        for (int t = 0; t < 5000; t++) {
            if (t % 100 == 0) s.checkCancelled();
            for (int y = 0; y < gh; y++) {
                int up = (y == 0 ? gh - 1 : y - 1) * gw;
                int down = (y == gh - 1 ? 0 : y + 1) * gw;
                int row = y * gw;
                for (int x = 0; x < gw; x++) {
                    int left = x == 0 ? gw - 1 : x - 1;
                    int right = x == gw - 1 ? 0 : x + 1;
                    int i = row + x;
                    double lu = u[row + left] + u[row + right] + u[up + x] + u[down + x] - 4 * u[i];
                    double lv = v[row + left] + v[row + right] + v[up + x] + v[down + x] - 4 * v[i];
                    double uvv = u[i] * v[i] * v[i];
                    u2[i] = u[i] + du * lu - uvv + feed * (1 - u[i]);
                    v2[i] = v[i] + dv * lv + uvv - (feed + kill) * v[i];
                }
            }
            double[] swap = u;
            u = u2;
            u2 = swap;
            swap = v;
            v = v2;
            v2 = swap;
        }
        int[] ramp = s.ramp(true);
        int[] out = s.pixels();
        double[] field = v;
        double edge = 0.25 + 0.15 * s.line;
        s.eachRow(py -> {
            double gy = (py + 0.5) * gh / s.height - 0.5;
            for (int px = 0; px < s.width; px++) {
                double gx = (px + 0.5) * gw / s.width - 0.5;
                double value = Sketch.bilinear(field, gw, gh, gx, gy);
                out[py * s.width + px] = Sketch.at(ramp, Sketch.smooth((value - 0.05) / edge));
            }
        });
    }

    // ---------------------------------------------------------------- marble

    /** Noise that is bent by noise, twice, in bands of the palette, with fine dark veins: marbled paper. */
    static void marble(Sketch s) {
        int[] ramp = s.ramp(false);
        int[] out = s.pixels();
        int ink = s.palette.ink().getRGB() & 0xFFFFFF;
        double freq = 0.0012 + 0.0025 * (1 - s.scale);
        double warp = 0.6 + 1.6 * s.chaos;
        double bands = 0.8 + 1.6 * s.density;
        double vein = 0.02 + 0.03 * s.line;
        Noise noise = s.noise;
        s.eachRow(py -> {
            for (int px = 0; px < s.width; px++) {
                double x = px / s.unit * freq;
                double y = py / s.unit * freq;
                double qx = noise.at(x * 0.5, y * 0.5);
                double qy = noise.at(x * 0.5 + 5.2, y * 0.5 + 1.3);
                double rx = noise.fractal(x * 0.5 + warp * qx + 1.7, y * 0.5 + warp * qy + 9.2, 2);
                double ry = noise.fractal(x * 0.5 + warp * qx + 8.3, y * 0.5 + warp * qy + 2.8, 2);
                double value = noise.fractal(x + warp * rx, y + warp * ry, 3);
                double t = (value * bands + 10) % 1.0;
                int c = Sketch.at(ramp, 0.5 - 0.5 * Math.cos(t * Math.PI * 2));
                double distance = Math.abs(rx - ry);
                if (distance < vein) c = Sketch.mixRgb(c, ink, 0.55 * (1 - distance / vein));
                out[py * s.width + px] = c;
            }
        });
    }

    // ---------------------------------------------------------------- contour lines

    /** The heights of a noise landscape as coloured bands with contour lines, every fifth one thicker: a map. */
    static void contours(Sketch s) {
        double step = 3 * s.unit;
        int gw = (int) Math.ceil(s.width / step) + 2;
        int gh = (int) Math.ceil(s.height / step) + 2;
        double freq = 0.002 + 0.004 * (1 - s.scale);
        int octaves = 3 + (int) Math.round(3 * s.chaos);
        double[] height = new double[gw * gh];
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                double v = s.noise.fractal(x * 3 * freq, y * 3 * freq, octaves);
                height[y * gw + x] = v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        s.checkCancelled();
        double low = min;
        double range = Math.max(1e-9, max - min);
        int levels = 8 + (int) (22 * s.density);
        List<Color> colors = s.palette.colors();
        int[] bandColors = new int[levels];
        for (int b = 0; b < levels; b++) {
            bandColors[b] = Sketch.mix(s.palette.paper(), colors.get(b * colors.size() / levels),
                    0.25 + 0.5 * b / levels).getRGB() & 0xFFFFFF;
        }
        int[] out = s.pixels();
        s.eachRow(py -> {
            for (int px = 0; px < s.width; px++) {
                double v = (FieldStyles.clampedBilinear(height, gw, gh, px / step, py / step) - low) / range;
                out[py * s.width + px] = bandColors[Math.min(levels - 1, Math.max(0, (int) (v * levels)))];
            }
        });
        // The lines between the bands, by marching squares on the grid.
        s.g.setColor(Sketch.alpha(s.palette.ink(), 170));
        double[] points = new double[8];
        for (int level = 1; level < levels; level++) {
            s.checkCancelled();
            double iso = min + range * level / levels;
            s.g.setStroke(s.stroke((level % 5 == 0 ? 1.6 : 0.6) * (0.5 + 1.5 * s.line)));
            Path2D path = new Path2D.Double();
            for (int y = 0; y < gh - 1; y++) {
                for (int x = 0; x < gw - 1; x++) {
                    double a = height[y * gw + x];
                    double b = height[y * gw + x + 1];
                    double c = height[(y + 1) * gw + x + 1];
                    double d = height[(y + 1) * gw + x];
                    int n = 0;
                    if ((a < iso) != (b < iso)) {
                        points[n++] = x + (iso - a) / (b - a);
                        points[n++] = y;
                    }
                    if ((b < iso) != (c < iso)) {
                        points[n++] = x + 1;
                        points[n++] = y + (iso - b) / (c - b);
                    }
                    if ((d < iso) != (c < iso)) {
                        points[n++] = x + (iso - d) / (c - d);
                        points[n++] = y + 1;
                    }
                    if ((a < iso) != (d < iso)) {
                        points[n++] = x;
                        points[n++] = y + (iso - a) / (d - a);
                    }
                    for (int k = 0; k + 3 < n; k += 4) {
                        path.moveTo(points[k] * step, points[k + 1] * step);
                        path.lineTo(points[k + 2] * step, points[k + 3] * step);
                    }
                }
            }
            s.g.draw(path);
        }
    }

    // ---------------------------------------------------------------- night sky

    /** Clouds of gas in two colours of the palette, many stars, and a few bright ones that glow. */
    static void sky(Sketch s) {
        Color deep = Sketch.mix(s.palette.dark(), Color.BLACK, 0.55);
        double freq = 0.002 + 0.003 * (1 - s.scale);
        Color first = s.pick();
        Color second = s.pick();
        double ox = s.random.nextDouble(100);
        double oy = s.random.nextDouble(100);
        double strength = 0.75 * (0.5 + 0.5 * s.chaos);
        int[] out = s.pixels();
        Noise noise = s.noise;
        s.eachRow(py -> {
            for (int px = 0; px < s.width; px++) {
                double x = px / s.unit * freq + ox;
                double y = py / s.unit * freq + oy;
                double gas = Sketch.smooth((noise.fractal(x, y, 5) + 0.35) * (0.7 + 0.6 * s.density));
                double gas2 = Sketch.smooth(noise.fractal(x * 1.7 + 3, y * 1.7 - 2, 4) * 1.6);
                Color c = Sketch.mix(deep, first, strength * gas);
                c = Sketch.mix(c, second, 0.5 * gas * gas2);
                out[py * s.width + px] = c.getRGB() & 0xFFFFFF;
            }
        });
        int stars = (int) ((1500 + 5000 * s.density) * s.area());
        double starSize = 0.6 + 0.8 * s.line;
        for (int i = 0; i < stars; i++) {
            if (i % 500 == 0) s.checkCancelled();
            double x = s.random.nextDouble() * s.width;
            double y = s.random.nextDouble() * s.height;
            double r = (Math.pow(s.random.nextDouble(), 6) * 1.6 + 0.25) * s.unit * starSize;
            int alpha = 120 + s.random.nextInt(136);
            Color tint = Sketch.mix(Color.WHITE, s.pick(), 0.25 * s.random.nextDouble());
            s.g.setColor(Sketch.alpha(tint, alpha));
            s.g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        int bright = 4 + (int) (10 * s.density * s.area());
        s.g.setStroke(s.stroke(0.5));
        for (int i = 0; i < bright; i++) {
            double x = s.random.nextDouble() * s.width;
            double y = s.random.nextDouble() * s.height;
            double r = (12 + 22 * s.random.nextDouble()) * s.unit;
            Color tint = Sketch.mix(Color.WHITE, s.pick(), 0.3);
            s.g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) r, new float[]{0f, 0.08f, 1f},
                    new Color[]{Color.WHITE, Sketch.alpha(tint, 140), Sketch.alpha(tint, 0)}));
            s.g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            s.g.setColor(Sketch.alpha(Color.WHITE, 120));
            double spike = r * 1.3;
            s.g.draw(new Line2D.Double(x - spike, y, x + spike, y));
            s.g.draw(new Line2D.Double(x, y - spike, x, y + spike));
        }
    }
}
