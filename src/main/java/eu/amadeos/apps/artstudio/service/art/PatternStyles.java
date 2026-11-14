package eu.amadeos.apps.artstudio.service.art;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * The styles of patterns built step by step: wheel curves, labyrinth, growth, triangles and halftone. Everything is
 * placed in units, so the picture is the same at every size.
 */
final class PatternStyles {
    /** Growth stops after this many steps of its wandering particles, however small the figure still is. */
    static final long MAX_STEPS = 60_000_000L;

    private PatternStyles() {
    }

    // ---------------------------------------------------------------- wheel curves

    /** Wheels rolling inside and outside of wheels, drawn with a pen: flowers of fine lines. */
    static void wheels(Sketch s) {
        double cx = s.width / 2.0, cy = s.height / 2.0;
        double size = Math.min(s.width, s.height) * (0.36 + 0.12 * s.scale);
        int layers = 2 + (int) Math.round(4 * s.density);
        for (int l = 0; l < layers; l++) {
            s.checkCancelled();
            int big = 40 + s.random.nextInt(80);
            int small = 5 + s.random.nextInt((int) (12 + 60 * s.chaos));
            if (small == big) small++;
            boolean outside = s.random.nextDouble() < 0.3 * s.chaos;
            double pen = s.random.nextDouble(0.5, 1.6) * small;
            int g = gcd(big, small);
            double turns = 2 * Math.PI * small / g;
            double rr = outside ? big + small : big - small;
            double extent = Math.abs(rr) + pen;
            double k = size * (1 - 0.1 * l) / extent;
            int steps = Math.min(40000, 300 * small / g + 2000);
            Path2D path = new Path2D.Double();
            for (int i = 0; i <= steps; i++) {
                double t = turns * i / steps;
                double x, y;
                if (outside) {
                    x = rr * Math.cos(t) - pen * Math.cos(rr / small * t);
                    y = rr * Math.sin(t) - pen * Math.sin(rr / small * t);
                } else {
                    x = rr * Math.cos(t) + pen * Math.cos(rr / small * t);
                    y = rr * Math.sin(t) - pen * Math.sin(rr / small * t);
                }
                if (i == 0) path.moveTo(cx + x * k, cy + y * k);
                else path.lineTo(cx + x * k, cy + y * k);
            }
            s.g.setStroke(s.stroke(0.4 + 1.6 * s.line));
            s.g.setColor(Sketch.alpha(s.color(l), 200));
            s.g.draw(path);
        }
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    // ------------------------------------------------------------ labyrinth

    /** A maze grown at random; its paths are coloured by how far they are from the middle. */
    static void labyrinth(Sketch s) {
        double cell = (10 + 30 * s.scale) * s.unit;
        int cols = Math.max(2, (int) Math.ceil(s.width / cell)), rows = Math.max(2, (int) Math.ceil(s.height / cell));
        double cw = (double) s.width / cols, ch = (double) s.height / rows;
        int n = cols * rows;
        boolean[] east = new boolean[n], south = new boolean[n]; // open passages
        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        int start = (rows / 2) * cols + cols / 2;
        int[] stack = new int[n];
        int top = 0;
        stack[top++] = start;
        dist[start] = 0;
        int[] options = new int[4];
        long visits = 0;
        while (top > 0) {
            if ((++visits & 0xFFFF) == 1) s.checkCancelled();
            int c = stack[top - 1];
            int x = c % cols, y = c / cols, k = 0;
            if (x > 0 && dist[c - 1] < 0) options[k++] = c - 1;
            if (x < cols - 1 && dist[c + 1] < 0) options[k++] = c + 1;
            if (y > 0 && dist[c - cols] < 0) options[k++] = c - cols;
            if (y < rows - 1 && dist[c + cols] < 0) options[k++] = c + cols;
            if (k == 0) {
                top--;
                continue;
            }
            // Chaos: low keeps going straight on more often, high turns more.
            int next = options[s.random.nextInt(k)];
            if (k > 1 && top > 1 && s.random.nextDouble() > s.chaos) {
                int prev = stack[top - 2];
                int straight = c + (c - prev);
                for (int i = 0; i < k; i++) if (options[i] == straight) next = straight;
            }
            if (next == c + 1) east[c] = true;
            else if (next == c - 1) east[next] = true;
            else if (next == c + cols) south[c] = true;
            else south[next] = true;
            dist[next] = dist[c] + 1;
            stack[top++] = next;
        }
        int max = 1;
        for (int d : dist) max = Math.max(max, d);
        int[] lut = s.ramp(false);
        double wall = (0.08 + 0.3 * s.line);
        for (int c = 0; c < n; c++) {
            if (c % 2000 == 0) s.checkCancelled();
            double t = (double) dist[c] / max;
            t = (t * (1 + 2 * s.density)) % 1.0;
            s.g.setColor(new Color(lut[(int) (Math.abs(1 - 2 * t) * 1023)]));
            int x = c % cols, y = c / cols;
            s.g.fill(new Rectangle2D.Double(x * cw, y * ch, cw + 0.5, ch + 0.5));
        }
        s.g.setColor(s.palette.ink());
        s.g.setStroke(new java.awt.BasicStroke((float) Math.max(1, wall * Math.min(cw, ch)), java.awt.BasicStroke.CAP_SQUARE, java.awt.BasicStroke.JOIN_MITER));
        Path2D walls = new Path2D.Double();
        for (int c = 0; c < n; c++) {
            int x = c % cols, y = c / cols;
            if (x < cols - 1 && !east[c]) { walls.moveTo((x + 1) * cw, y * ch); walls.lineTo((x + 1) * cw, (y + 1) * ch); }
            if (y < rows - 1 && !south[c]) { walls.moveTo(x * cw, (y + 1) * ch); walls.lineTo((x + 1) * cw, (y + 1) * ch); }
        }
        s.g.draw(walls);
    }

    // ------------------------------------------------------------ growth

    /** Particles wander until they touch the growing figure and stick: frost, coral or lightning. */
    static void growth(Sketch s) {
        double cell = (2.6 + 3.0 * s.scale) * s.unit;
        int gw = (int) Math.ceil(s.width / cell), gh = (int) Math.ceil(s.height / cell);
        int[] parent = new int[gw * gh];
        Arrays.fill(parent, -2);
        List<Integer> stuck = new ArrayList<>();
        int seeds = 1 + (int) (3 * s.chaos);
        int[] sx = new int[seeds], sy = new int[seeds];
        double[] radius = new double[seeds];
        for (int i = 0; i < seeds; i++) {
            sx[i] = seeds == 1 ? gw / 2 : (int) (gw * (0.2 + 0.6 * s.random.nextDouble()));
            sy[i] = seeds == 1 ? gh / 2 : (int) (gh * (0.2 + 0.6 * s.random.nextDouble()));
            parent[sy[i] * gw + sx[i]] = -1;
            stuck.add(sy[i] * gw + sx[i]);
            radius[i] = 1;
        }
        // Grow until enough particles are stuck; a figure that reaches the edge stops growing.
        double edge = Math.min(gw, gh) * 0.48;
        int goal = (int) ((0.12 + 0.3 * s.density) * gw * gh);
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};
        double stick = 1 - 0.85 * s.density;
        long walked = 0;
        long launches = 0;
        while (stuck.size() < goal && walked < MAX_STEPS) {
            if (launches++ % 256 == 0) s.checkCancelled();
            int k0 = s.random.nextInt(seeds);
            if (radius[k0] >= edge) {
                boolean all = true;
                for (double r : radius) all &= r >= edge;
                if (all) break;
                continue;
            }
            double ang = s.random.nextDouble(2 * Math.PI);
            double r0 = radius[k0] + 4;
            int x = sx[k0] + (int) Math.round(Math.cos(ang) * r0), y = sy[k0] + (int) Math.round(Math.sin(ang) * r0);
            double kill = (radius[k0] + 12) * (radius[k0] + 12);
            while (true) {
                walked++;
                if (x < 1 || y < 1 || x >= gw - 1 || y >= gh - 1) break;
                double ddx = x - sx[k0], ddy = y - sy[k0];
                if (ddx * ddx + ddy * ddy > kill) break;
                int i = y * gw + x;
                int touch = -1;
                for (int k = 0; k < 8 && touch < 0; k++) {
                    int j = (y + dy[k]) * gw + x + dx[k];
                    if (parent[j] != -2) touch = j;
                }
                // With more density a particle often slips past before it sticks: thicker, bushier figures.
                if (touch >= 0 && parent[i] == -2 && s.random.nextDouble() < stick) {
                    parent[i] = touch;
                    stuck.add(i);
                    // The figure this particle joined grows.
                    int best = 0;
                    double bd = Double.MAX_VALUE;
                    for (int q = 0; q < seeds; q++) {
                        double d2 = Math.pow(x - sx[q], 2) + Math.pow(y - sy[q], 2);
                        if (d2 < bd) { bd = d2; best = q; }
                    }
                    radius[best] = Math.max(radius[best], Math.sqrt(bd));
                    break;
                }
                int k = s.random.nextInt(4);
                x += dx[k];
                y += dy[k];
            }
        }
        int[] lut = s.ramp(false);
        s.g.setStroke(new java.awt.BasicStroke((float) (cell * (0.35 + 0.6 * s.line)), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        int total = stuck.size();
        for (int idx = 0; idx < total; idx++) {
            if (idx % 2000 == 0) s.checkCancelled();
            int i = stuck.get(idx);
            int pnt = parent[i];
            double x = (i % gw + 0.5) * cell, y = (i / gw + 0.5) * cell;
            s.g.setColor(new Color(lut[(int) (1023.0 * Math.sqrt((double) idx / total))]));
            if (pnt < 0) {
                s.g.fill(new Ellipse2D.Double(x - cell, y - cell, 2 * cell, 2 * cell));
            } else {
                s.g.draw(new Line2D.Double((pnt % gw + 0.5) * cell, (pnt / gw + 0.5) * cell, x, y));
            }
        }
    }

    // ------------------------------------------------------------ triangles

    /** Triangles of a shaken grid, each in the colour of a soft gradient beneath it, like a folded paper. */
    static void triangles(Sketch s) {
        double size = (25 + 70 * s.scale) * s.unit * (1.3 - 0.6 * s.density);
        int cols = (int) Math.ceil(s.width / size) + 1, rows = (int) Math.ceil(s.height / (size * 0.866)) + 1;
        double[][] px = new double[rows + 1][cols + 2], py = new double[rows + 1][cols + 2];
        double jitter = 0.45 * s.chaos;
        for (int r = 0; r <= rows; r++) for (int c = 0; c <= cols + 1; c++) {
            double x = (c - 0.5 + (r % 2) * 0.5) * size, y = (r - 0.5) * size * 0.866;
            px[r][c] = x + (2 * s.random.nextDouble() - 1) * jitter * size;
            py[r][c] = y + (2 * s.random.nextDouble() - 1) * jitter * size;
        }
        int[] lut = s.ramp(false);
        double freq = 0.0015 + 0.002 * (1 - s.scale);
        java.awt.Color ink = s.palette.ink();
        s.g.setStroke(s.stroke(0.3 + 1.0 * s.line));
        for (int r = 0; r < rows; r++) {
            s.checkCancelled();
            for (int c = 0; c <= cols; c++) {
                int o = r % 2;
                double[][] tris = o == 0 ? new double[][]{
                        {px[r][c], py[r][c], px[r][c + 1], py[r][c + 1], px[r + 1][c], py[r + 1][c]},
                        {px[r][c + 1], py[r][c + 1], px[r + 1][c], py[r + 1][c], px[r + 1][c + 1], py[r + 1][c + 1]}}
                        : new double[][]{
                        {px[r][c], py[r][c], px[r][c + 1], py[r][c + 1], px[r + 1][c + 1], py[r + 1][c + 1]},
                        {px[r][c], py[r][c], px[r + 1][c], py[r + 1][c], px[r + 1][c + 1], py[r + 1][c + 1]}};
                for (double[] t : tris) {
                    double mx = (t[0] + t[2] + t[4]) / 3, my = (t[1] + t[3] + t[5]) / 3;
                    double v = 0.5 + 0.5 * s.noise.fractal(mx / s.unit * freq, my / s.unit * freq, 2);
                    v = Math.min(1, Math.max(0, v + s.random.nextDouble(-0.06, 0.06)));
                    Color col = new Color(lut[(int) (v * 1023)]);
                    // Every face catches the light a little differently, like folded paper.
                    double shade = 0.92 + 0.16 * s.random.nextDouble();
                    col = new Color((int) Math.min(255, col.getRed() * shade), (int) Math.min(255, col.getGreen() * shade), (int) Math.min(255, col.getBlue() * shade));
                    Path2D tri = new Path2D.Double();
                    tri.moveTo(t[0], t[1]);
                    tri.lineTo(t[2], t[3]);
                    tri.lineTo(t[4], t[5]);
                    tri.closePath();
                    s.g.setColor(col);
                    s.g.fill(tri);
                    s.g.setColor(Sketch.alpha(Sketch.mix(col, ink, 0.25), 120));
                    s.g.draw(tri);
                }
            }
        }
    }

    // ------------------------------------------------------------ halftone

    /** Dots in rows at an angle, larger where a soft picture of noise is darker, as in old print; two inks. */
    static void halftone(Sketch s) {
        double pitch = (5 + 14 * s.scale) * s.unit;
        double freq = 0.0012 + 0.003 * (1 - s.density);
        double ox2 = s.random.nextDouble(50), oy2 = s.random.nextDouble(50);
        List<Color> cols = s.palette.colors();
        Color[] inks = {cols.get(s.random.nextInt(cols.size())), cols.get(s.random.nextInt(cols.size())), s.palette.ink()};
        double[] angles = {Math.toRadians(15), Math.toRadians(75), Math.toRadians(45)};
        int layers = s.chaos < 0.33 ? 1 : s.chaos < 0.66 ? 2 : 3;
        double diag = Math.hypot(s.width, s.height);
        for (int l = 0; l < layers; l++) {
            int li = layers == 1 ? 2 : l;
            double a = angles[li], ca = Math.cos(a), sa = Math.sin(a);
            s.g.setColor(Sketch.alpha(inks[li], layers == 1 ? 255 : 210));
            int n = (int) (diag / pitch) + 2;
            for (int i = -n; i <= n; i++) {
                if (i % 20 == 0) s.checkCancelled();
                for (int j = -n; j <= n; j++) {
                    double x = s.width / 2.0 + (i * ca - j * sa) * pitch;
                    double y = s.height / 2.0 + (i * sa + j * ca) * pitch;
                    if (x < -pitch || y < -pitch || x > s.width + pitch || y > s.height + pitch) continue;
                    double v = 0.5 + 0.5 * s.noise.fractal(x / s.unit * freq + l * ox2, y / s.unit * freq + l * oy2, 3);
                    v = Math.pow(Math.min(1, Math.max(0, v * 1.15 - 0.05)), 1.3);
                    double r = pitch * 0.62 * Math.sqrt(v) * (0.6 + 0.6 * s.line);
                    if (r < 0.3) continue;
                    s.g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                }
            }
        }
    }
}
