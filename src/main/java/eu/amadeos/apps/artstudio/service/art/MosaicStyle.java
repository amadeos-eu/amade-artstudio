package eu.amadeos.apps.artstudio.service.art;

import java.awt.image.BufferedImage;

/**
 * Voronoi cells with grout: every pixel takes the colour of the nearest of some hundred random points, slightly
 * darker away from it; where the nearest and the second nearest point are almost equally far, there is grout.
 * The points are sorted into buckets, so that a poster with 35 million pixels looks at a few points per pixel.
 */
final class MosaicStyle {
    private MosaicStyle() {
    }

    static void paint(Sketch s) {
        int count = Math.max(24, (int) ((60 + 340 * s.density) * s.area()));
        double[] px = new double[count];
        double[] py = new double[count];
        int[] color = new int[count];
        for (int i = 0; i < count; i++) {
            px[i] = s.random.nextDouble() * s.width;
            py[i] = s.random.nextDouble() * s.height;
            color[i] = s.pick().getRGB() & 0xFFFFFF;
        }
        // Buckets of about two points each.
        double cell = Math.sqrt((double) s.width * s.height / count) * 1.4;
        int columns = Math.max(1, (int) Math.ceil(s.width / cell));
        int rows = Math.max(1, (int) Math.ceil(s.height / cell));
        int[][] buckets = buckets(px, py, cell, columns, rows);

        int grout = s.palette.ink().getRGB() & 0xFFFFFF;
        double gap = (0.8 + 3 * s.line) * s.unit;
        double fade = 60 * s.unit;
        BufferedImage image = s.image;
        int[] row = new int[s.width];
        for (int y = 0; y < s.height; y++) {
            if (y % 64 == 0) s.checkCancelled();
            int by = Math.min(rows - 1, (int) (y / cell));
            for (int x = 0; x < s.width; x++) {
                int bx = Math.min(columns - 1, (int) (x / cell));
                double d1 = Double.MAX_VALUE;
                double d2 = Double.MAX_VALUE;
                int best = 0;
                // Look further out until two points are found and no closer one can hide farther away.
                for (int ring = 1; ring <= Math.max(columns, rows); ring++) {
                    for (int cy = by - ring; cy <= by + ring; cy++) {
                        if (cy < 0 || cy >= rows) continue;
                        for (int cx = bx - ring; cx <= bx + ring; cx++) {
                            if (cx < 0 || cx >= columns) continue;
                            boolean inner = Math.abs(cy - by) < ring && Math.abs(cx - bx) < ring;
                            if (inner && ring > 1) continue;
                            for (int i : buckets[cy * columns + cx]) {
                                double dx = x - px[i];
                                double dy = y - py[i];
                                double d = dx * dx + dy * dy;
                                if (d < d1) {
                                    d2 = d1;
                                    d1 = d;
                                    best = i;
                                } else if (d < d2) {
                                    d2 = d;
                                }
                            }
                        }
                    }
                    // Points in rings not yet searched are at least ring cells away.
                    double reach = ring * cell;
                    if (d2 < reach * reach) break;
                }
                double edge = Math.sqrt(d2) - Math.sqrt(d1);
                row[x] = edge < gap ? grout : shade(color[best], Math.sqrt(d1) / fade);
            }
            // The renderer draws on TYPE_INT_RGB: one int per pixel, as the row is.
            image.getRaster().setDataElements(0, y, s.width, 1, row);
        }
    }

    private static int[][] buckets(double[] px, double[] py, double cell, int columns, int rows) {
        int[] sizes = new int[columns * rows];
        int[] where = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int bx = Math.min(columns - 1, (int) (px[i] / cell));
            int by = Math.min(rows - 1, (int) (py[i] / cell));
            where[i] = by * columns + bx;
            sizes[where[i]]++;
        }
        int[][] buckets = new int[columns * rows][];
        for (int b = 0; b < buckets.length; b++) buckets[b] = new int[sizes[b]];
        int[] fill = new int[columns * rows];
        for (int i = 0; i < px.length; i++) buckets[where[i]][fill[where[i]]++] = i;
        return buckets;
    }

    static int shade(int rgb, double distance) {
        double f = 1 - Math.min(0.18, distance * 0.12);
        int r = (int) (((rgb >> 16) & 255) * f);
        int g = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
