package eu.amadeos.apps.artstudio.service.art;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The styles of solid, three-dimensional shapes: cubes, spheres, wireframe landscape, tube knot and sculpture.
 * Light always comes from the top left.
 */
final class SolidStyles {
    private SolidStyles() {
    }

    // ------------------------------------------------------------ cubes

    /** Columns of cubes on an isometric grid, as high as a noise landscape says; lit from the top left. */
    static void cubes(Sketch s) {
        double size = (14 + 30 * s.scale) * s.unit;
        double hw = size * 0.866, hh = size * 0.5;
        double freq = 0.04 + 0.08 * (1 - s.scale);
        int maxH = 1 + (int) Math.round(2 + 7 * s.density);
        int n = (int) Math.ceil((s.width + s.height * 2) / size) + maxH + 2;
        double cx = s.width / 2.0, cy = s.height / 2.0 - n * hh;
        List<Color> cols = s.palette.colors();
        for (int sum = 0; sum <= 2 * n; sum++) {
            s.checkCancelled();
            for (int i = Math.max(0, sum - n); i <= Math.min(n, sum); i++) {
                int j = sum - i;
                double bx = cx + (i - j) * hw, by = cy + (i + j) * hh;
                if (bx < -2 * hw || bx > s.width + 2 * hw || by < -maxH * size || by > s.height + 3 * size) continue;
                double v = Math.min(0.999, Math.max(0, 0.5 + 0.5 * s.noise.fractal(i * freq, j * freq, 2)));
                int h = Math.max(0, (int) Math.round(v * maxH * (0.6 + 0.8 * s.chaos) - maxH * 0.3 * s.chaos));
                if (s.random.nextDouble() < 0.15 * s.chaos) h += 1 + s.random.nextInt(3);
                Color base = cols.get(Math.min(cols.size() - 1, (int) (v * cols.size())));
                double top = by - h * size;
                // Left and right sides, then the top.
                Path2D left = new Path2D.Double();
                left.moveTo(bx - hw, by); left.lineTo(bx, by + hh); left.lineTo(bx, top + hh); left.lineTo(bx - hw, top); left.closePath();
                Path2D right = new Path2D.Double();
                right.moveTo(bx + hw, by); right.lineTo(bx, by + hh); right.lineTo(bx, top + hh); right.lineTo(bx + hw, top); right.closePath();
                Path2D lid = new Path2D.Double();
                lid.moveTo(bx, top - hh); lid.lineTo(bx + hw, top); lid.lineTo(bx, top + hh); lid.lineTo(bx - hw, top); lid.closePath();
                s.g.setColor(Sketch.shade(base, 0.78));
                s.g.fill(left);
                s.g.setColor(Sketch.shade(base, 0.55));
                s.g.fill(right);
                s.g.setColor(Sketch.shade(base, 1.12));
                s.g.fill(lid);
                if (s.line > 0.05) {
                    s.g.setStroke(s.stroke(0.15 + 0.9 * s.line));
                    s.g.setColor(Sketch.alpha(s.palette.ink(), 90));
                    // Edges of the single cubes on the sides.
                    for (int k = 1; k < h; k++) {
                        double y = by - k * size;
                        s.g.draw(new java.awt.geom.Line2D.Double(bx - hw, y, bx, y + hh));
                        s.g.draw(new java.awt.geom.Line2D.Double(bx, y + hh, bx + hw, y));
                    }
                    s.g.draw(left);
                    s.g.draw(right);
                    s.g.draw(lid);
                }
            }
        }
    }

    // ------------------------------------------------------------ spheres

    /** Shiny balls, lit from the top left, with soft shadows; the far ones first. */
    static void spheres(Sketch s) {
        Color bg1 = s.palette.paper(), bg2 = Sketch.mix(s.palette.paper(), s.palette.ink(), 0.18);
        s.g.setPaint(new GradientPaint(0, 0, bg1, 0, s.height, bg2));
        s.g.fillRect(0, 0, s.width, s.height);
        int count = (int) ((25 + 140 * s.density) * s.area());
        record Ball(double x, double y, double r, Color c) { }
        List<Ball> balls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double r = (8 + 70 * Math.pow(s.random.nextDouble(), 2.5 - 1.5 * s.chaos) * (0.5 + s.scale)) * s.unit;
            balls.add(new Ball(s.random.nextDouble() * s.width, s.random.nextDouble() * s.height, r, s.pick()));
        }
        balls.sort(Comparator.comparingDouble(b -> b.y + b.r));
        for (Ball b : balls) {
            s.checkCancelled();
            double sh = b.r * 0.35;
            s.g.setPaint(new RadialGradientPaint(new Point2D.Double(b.x + sh, b.y + sh * 1.4), (float) (b.r * 1.15),
                    new float[]{0f, 1f}, new Color[]{new Color(0, 0, 0, 70), new Color(0, 0, 0, 0)}));
            s.g.fill(new Ellipse2D.Double(b.x + sh - b.r * 1.15, b.y + sh * 1.4 - b.r * 1.15, b.r * 2.3, b.r * 2.3));
            Point2D focus = new Point2D.Double(b.x - b.r * 0.38, b.y - b.r * 0.42);
            double gloss = 0.35 + 0.6 * s.line;
            s.g.setPaint(new RadialGradientPaint(new Point2D.Double(b.x, b.y), (float) b.r, focus,
                    new float[]{0f, (float) (0.12 * gloss), 0.55f, 1f},
                    new Color[]{Sketch.mix(b.c, Color.WHITE, 0.85 * gloss), Sketch.shade(b.c, 1.15), b.c, Sketch.shade(b.c, 0.45)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
            s.g.fill(new Ellipse2D.Double(b.x - b.r, b.y - b.r, 2 * b.r, 2 * b.r));
        }
    }

    // ------------------------------------------------------------ wireframe landscape

    /** A landscape of lines in perspective, row after row towards the viewer, behind it a low sun. */
    static void wireframe(Sketch s) {
        double horizon = s.height * (0.38 + 0.12 * s.random.nextDouble());
        Color sky = s.palette.dark(), sky2 = Sketch.mix(s.palette.dark(), s.color(1), 0.45);
        s.g.setPaint(new GradientPaint(0, 0, sky, 0, (float) horizon, sky2));
        s.g.fillRect(0, 0, s.width, (int) Math.ceil(horizon) + 1);
        // The sun.
        double sr = s.height * (0.14 + 0.08 * s.scale);
        double sx = s.width * s.random.nextDouble(0.3, 0.7);
        s.g.setPaint(new GradientPaint(0, (float) (horizon - sr), s.color(0), 0, (float) horizon, s.color(2)));
        s.g.fill(new Ellipse2D.Double(sx - sr, horizon - sr * 1.1, 2 * sr, 2 * sr));
        int rows = 40 + (int) (70 * s.density);
        int cols = 40 + (int) (60 * s.density);
        double f = s.height * 0.42, camH = 1.6;
        double width3d = 44;
        double amp = 1.5 + 5 * s.chaos;
        Color ground = s.palette.dark();
        int[] lut = s.ramp(false);
        s.g.setStroke(s.stroke(0.35 + 1.4 * s.line));
        double[] prevX = null, prevY = null;
        for (int r = rows; r >= 0; r--) {
            s.checkCancelled();
            double z = 1.2 + r * 0.32;
            double[] xs = new double[cols + 1], ys = new double[cols + 1];
            Path2D line = new Path2D.Double();
            Path2D fill = new Path2D.Double();
            for (int c = 0; c <= cols; c++) {
                double x3 = (c / (double) cols - 0.5) * width3d;
                double flat = Math.min(1, Math.abs(x3) / (width3d * 0.12));
                double hgt = amp * flat * Math.max(0, s.noise.fractal(x3 * 0.15, z * 0.15, 3) + 0.25);
                xs[c] = s.width / 2.0 + x3 * f / z;
                ys[c] = horizon + (camH - hgt) * f / z;
                if (c == 0) { line.moveTo(xs[c], ys[c]); fill.moveTo(xs[c], ys[c]); } else { line.lineTo(xs[c], ys[c]); fill.lineTo(xs[c], ys[c]); }
            }
            fill.lineTo(xs[cols], s.height * 3);
            fill.lineTo(xs[0], s.height * 3);
            fill.closePath();
            s.g.setColor(ground);
            s.g.fill(fill);
            double t = 1 - (double) r / rows;
            Color c = new Color(lut[(int) (t * 1023)]);
            s.g.setColor(c);
            if (prevX != null) {
                Path2D colsPath = new Path2D.Double();
                for (int k = 0; k <= cols; k++) { colsPath.moveTo(prevX[k], prevY[k]); colsPath.lineTo(xs[k], ys[k]); }
                s.g.draw(colsPath);
            }
            s.g.draw(line);
            prevX = xs;
            prevY = ys;
        }
    }

    // ------------------------------------------------------------ knot

    /** A tube tied into a knot, turned in space and shaded like a soft plastic hose. */
    static void knot(Sketch s) {
        int p = 2 + s.random.nextInt(3), q = 3 + s.random.nextInt(4);
        if (gcd(p, q) != 1) q++;
        if (gcd(p, q) != 1) q++;
        double ax = s.random.nextDouble(0.3, 1.2), ay = s.random.nextDouble(-0.6, 0.6);
        double size = Math.min(s.width, s.height) * (0.12 + 0.05 * s.scale);
        double tube = size * (0.10 + 0.22 * s.line);
        int steps = (int) (2500 + 2500 * s.density);
        double[][] pts = new double[steps][4];
        double wobble = 0.5 * s.chaos;
        for (int i = 0; i < steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double r = 2 + Math.cos(q * t) + wobble * Math.sin(5 * t);
            double x = r * Math.cos(p * t), y = r * Math.sin(p * t), z = -Math.sin(q * t) * 1.2;
            double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
            double x2 = x * Math.cos(ay) + z1 * Math.sin(ay), z2 = -x * Math.sin(ay) + z1 * Math.cos(ay);
            pts[i] = new double[]{x2, y1, z2, i, 0, 0};
        }
        // The direction of the tube on the screen, for shading across it.
        for (int i = 0; i < steps; i++) {
            double[] a = pts[(i + steps - 1) % steps], b = pts[(i + 1) % steps];
            double dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy);
            if (l < 1e-9) { dx = 1; dy = 0; l = 1; }
            pts[i][4] = -dy / l;
            pts[i][5] = dx / l;
        }
        Arrays.sort(pts, Comparator.comparingDouble(a -> a[2]));
        int[] lut = s.ramp(false);
        double cx = s.width / 2.0, cy = s.height / 2.0;
        for (int k = 0; k < steps; k++) {
            if (k % 500 == 0) s.checkCancelled();
            double[] a = pts[k];
            double persp = 1 + a[2] * 0.06;
            double x = cx + a[0] * size * persp, y = cy + a[1] * size * persp, r = tube * persp;
            Color c = new Color(lut[(int) (1023 * Math.abs(2 * (a[3] / steps) - 1))]);
            double light = 0.92 + 0.05 * a[2];
            double nx = a[4], ny = a[5];
            if (nx + ny > 0) { nx = -nx; ny = -ny; } // the light side faces the top left
            s.g.setPaint(new java.awt.LinearGradientPaint(new Point2D.Double(x + nx * r, y + ny * r),
                    new Point2D.Double(x - nx * r, y - ny * r), new float[]{0f, 0.3f, 0.65f, 1f},
                    new Color[]{Sketch.shade(c, 0.75 * light), Sketch.shade(c, 1.18 * light), Sketch.shade(c, 0.85 * light), Sketch.shade(c, 0.35 * light)}));
            s.g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }


    // ---------------------------------------------------------------- sculpture

    /**
     * Balls and rings melted into one smooth sculpture on a floor, drawn by marching along the ray of every pixel
     * through a distance field, with soft shadows. The rows run on all cores.
     */
    static void sculpture(Sketch s) {
        int parts = 3 + (int) (5 * s.density);
        // Per part: ring (1) or ball (0), x, y, z, radius, tube radius, cos and sin of the ring's tilt.
        double[][] prim = new double[parts][8];
        for (int i = 0; i < parts; i++) {
            boolean ring = s.random.nextDouble() < 0.25 + 0.4 * s.chaos;
            double size = (0.4 + 0.4 * s.random.nextDouble()) * (0.75 + 0.5 * s.scale);
            double x = s.random.nextDouble(-0.9, 0.9);
            double y = size + s.random.nextDouble(0, 0.9);
            double z = s.random.nextDouble(-0.5, 0.5);
            double tilt = s.random.nextDouble(Math.PI);
            prim[i] = new double[]{ring ? 1 : 0, x, y, z, size, size * 0.3, Math.cos(tilt), Math.sin(tilt)};
        }
        s.checkCancelled();
        Scene scene = new Scene(prim, 0.15 + 0.5 * (1 - s.chaos));
        int floor = s.palette.paper().getRGB() & 0xFFFFFF;
        int paper = floor;
        int skyTop = Sketch.mixRgb(paper, s.palette.ink().getRGB() & 0xFFFFFF, 0.12);
        int[] ramp = s.ramp(false);
        int[] out = s.pixels();
        double camX = 0;
        double camY = 1.5;
        double camZ = -4.2;
        double fov = 1.0 / Math.min(s.width, s.height);
        double ll = Math.sqrt(0.36 + 0.81 + 0.25);
        double lx = -0.6 / ll;
        double ly = 0.9 / ll;
        double lz = -0.5 / ll;
        double gloss = 0.3 + 0.6 * s.line;
        double horizon = Math.max(1, s.height / 2.0 - 0.1 / fov);
        s.eachRow(py -> {
            for (int px = 0; px < s.width; px++) {
                double dx = (px - s.width / 2.0) * fov;
                double dy = -(py - s.height / 2.0) * fov - 0.1;
                double dl = Math.sqrt(dx * dx + dy * dy + 1);
                dx /= dl;
                dy /= dl;
                double dz = 1 / dl;
                double t = 0;
                int hit = -1;
                double d = 1;
                for (int it = 0; it < 100 && t < 25; it++) {
                    double x = camX + dx * t;
                    double y = camY + dy * t;
                    double z = camZ + dz * t;
                    d = scene.distance(x, y, z);
                    if (y < d && y < 0.001) {
                        hit = 1;
                        break;
                    }
                    if (d < 0.001) {
                        hit = 0;
                        break;
                    }
                    t += Math.min(d, Math.max(y, 0.002));
                }
                // A ray that grazes the surface runs out of steps close to it: that is the edge of the sculpture.
                if (hit < 0 && d < 0.02 && t < 25) hit = 0;
                int rgb;
                if (hit < 0) {
                    // The sky lightens down to the horizon; below it, the floor is lost in the haze.
                    rgb = Sketch.mixRgb(skyTop, paper, Math.min(1, py / horizon));
                } else {
                    double x = camX + dx * t;
                    double y = camY + dy * t;
                    double z = camZ + dz * t;
                    double nx = 0;
                    double ny = 1;
                    double nz = 0;
                    if (hit == 0) {
                        double e = 0.002;
                        nx = scene.distance(x + e, y, z) - scene.distance(x - e, y, z);
                        ny = scene.distance(x, y + e, z) - scene.distance(x, y - e, z);
                        nz = scene.distance(x, y, z + e) - scene.distance(x, y, z - e);
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (nl > 0) {
                            nx /= nl;
                            ny /= nl;
                            nz /= nl;
                        }
                    }
                    double diffuse = Math.max(0, nx * lx + ny * ly + nz * lz);
                    double shadow = scene.softShadow(x, y, z, lx, ly, lz);
                    // On the floor, near the sculpture is darker: the light hardly reaches there.
                    double occlusion = hit == 0 ? 1 : Math.min(1, 0.55 + 0.45 * scene.distance(x, 0.4, z) / 0.4);
                    double light = (0.35 + 0.65 * diffuse * shadow) * occlusion;
                    int base = hit == 1 ? floor : Sketch.at(ramp, 0.5 + 0.5 * Math.sin(y * 1.3 + x * 0.7));
                    int c = shadeRgb(base, light * 1.05);
                    if (hit == 0) {
                        double k = 2 * (dx * nx + dy * ny + dz * nz);
                        double spec = Math.pow(Math.max(0, (dx - k * nx) * lx + (dy - k * ny) * ly + (dz - k * nz) * lz), 30);
                        c = Sketch.mixRgb(c, 0xFFFFFF, Math.min(1, spec * shadow * gloss));
                    }
                    rgb = Sketch.mixRgb(c, paper, Math.min(1, Math.max(0, (t - 8) / 14)));
                }
                out[py * s.width + px] = rgb;
            }
        });
    }

    /** {@link Sketch#shade} on packed RGB values. */
    private static int shadeRgb(int rgb, double f) {
        return f >= 1 ? Sketch.mixRgb(rgb, 0xFFFFFF, Math.min(1, f - 1)) : Sketch.mixRgb(0, rgb, Math.max(0, f));
    }

    /** The sculpture as a distance field: how far a point is from its surface. */
    private record Scene(double[][] prim, double blend) {
        double distance(double px, double py, double pz) {
            double d = 1e9;
            for (double[] q : prim) {
                double x = px - q[1];
                double y = py - q[2];
                double z = pz - q[3];
                double e;
                if (q[0] == 0) {
                    e = Math.sqrt(x * x + y * y + z * z) - q[4];
                } else {
                    double rx = x * q[6] - y * q[7];
                    double ry = x * q[7] + y * q[6];
                    double a = Math.sqrt(rx * rx + z * z) - q[4];
                    e = Math.sqrt(a * a + ry * ry) - q[5];
                }
                // A smooth minimum: the parts melt into each other.
                double h = Math.max(blend - Math.abs(d - e), 0) / blend;
                d = Math.min(d, e) - h * h * blend * 0.25;
            }
            return d;
        }

        /** 1 in full light, down to 0.2 in the shadow of the sculpture, soft at the edges. */
        double softShadow(double x, double y, double z, double lx, double ly, double lz) {
            double res = 1;
            double t = 0.03;
            for (int i = 0; i < 24 && t < 10; i++) {
                double h = distance(x + lx * t, y + ly * t, z + lz * t);
                if (h < 0.001) return 0.2;
                res = Math.min(res, 8 * h / t);
                t += Math.max(0.04, h);
            }
            return 0.2 + 0.8 * Math.max(0, res);
        }
    }
}
