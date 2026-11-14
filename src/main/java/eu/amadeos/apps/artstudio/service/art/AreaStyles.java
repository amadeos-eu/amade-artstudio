package eu.amadeos.apps.artstudio.service.art;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** The styles that fill areas: watercolour, circles, colour grid, shapes, landscape and dots. */
final class AreaStyles {
    private AreaStyles() {
    }

    /**
     * Blots of colour: a rough polygon is deformed again and again (each edge gets a displaced midpoint), and
     * three dozen further deformations of it are laid on top of each other, each almost transparent.
     */
    static void watercolour(Sketch s) {
        int blots = (int) Math.round((4 + 5 * s.density) * Math.sqrt(s.area()));
        for (int b = 0; b < blots; b++) {
            s.checkCancelled();
            Color color = s.color(b);
            double cx = s.random.nextDouble(0.1, 0.9) * s.width;
            double cy = s.random.nextDouble(0.15, 0.85) * s.height;
            double radius = (90 + 120 * s.scale) * s.unit * s.random.nextDouble(0.7, 1.3);
            List<double[]> base = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                base.add(new double[]{cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                        s.random.nextDouble(0.5, 1.5)});
            }
            double variance = 0.8 + s.chaos;
            base = deform(s, base, 3, variance);
            s.g.setColor(Sketch.alpha(color, 9));
            for (int layer = 0; layer < 36; layer++) {
                List<double[]> polygon = deform(s, base, 3, variance);
                Path2D path = new Path2D.Double();
                path.moveTo(polygon.getFirst()[0], polygon.getFirst()[1]);
                for (double[] p : polygon) path.lineTo(p[0], p[1]);
                path.closePath();
                s.g.fill(path);
            }
        }
    }

    private static List<double[]> deform(Sketch s, List<double[]> polygon, int depth, double variance) {
        for (int d = 0; d < depth; d++) {
            List<double[]> out = new ArrayList<>(polygon.size() * 2);
            for (int i = 0; i < polygon.size(); i++) {
                double[] a = polygon.get(i);
                double[] b = polygon.get((i + 1) % polygon.size());
                out.add(a);
                double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double mx = (a[0] + b[0]) / 2 + s.gauss() * length * 0.25 * a[2] * variance;
                double my = (a[1] + b[1]) / 2 + s.gauss() * length * 0.25 * a[2] * variance;
                out.add(new double[]{mx, my, Math.max(0.1, (a[2] + b[2]) / 2 * s.random.nextDouble(0.7, 1.3))});
            }
            polygon = out;
        }
        return polygon;
    }

    /** Circles packed as tightly as chance allows; many of the large ones get rings inside. */
    static void circles(Sketch s) {
        List<double[]> circles = new ArrayList<>();
        double min = (2 + 4 * (1 - s.density)) * s.unit;
        double max = (40 + 90 * s.scale) * s.unit;
        double gap = (1 + 3 * s.line) * s.unit;
        int tries = (int) (40_000 * s.area());
        int limit = (int) (1600 * s.area());
        for (int t = 0; t < tries && circles.size() < limit; t++) {
            if (t % 2000 == 0) s.checkCancelled();
            double x = s.random.nextDouble() * s.width;
            double y = s.random.nextDouble() * s.height;
            double radius = max;
            boolean free = true;
            for (double[] c : circles) {
                double d = Math.hypot(x - c[0], y - c[1]) - c[2] - gap;
                if (d < min) {
                    free = false;
                    break;
                }
                radius = Math.min(radius, d);
            }
            if (free) circles.add(new double[]{x, y, radius});
        }
        for (double[] c : circles) {
            s.g.setColor(s.pick());
            s.g.fill(new Ellipse2D.Double(c[0] - c[2], c[1] - c[2], 2 * c[2], 2 * c[2]));
            if (c[2] > 12 * s.unit && s.random.nextDouble() < 0.5 + 0.5 * s.chaos) {
                double r = c[2];
                while ((r *= s.random.nextDouble(0.45, 0.75)) > 3 * s.unit) {
                    s.g.setColor(s.pick());
                    s.g.fill(new Ellipse2D.Double(c[0] - r, c[1] - r, 2 * r, 2 * r));
                }
            }
        }
    }

    /** The picture split again and again into rectangles, with thick ink lines; some fields are coloured. */
    static void grid(Sketch s) {
        List<Rectangle2D> fields = new ArrayList<>();
        fields.add(new Rectangle2D.Double(0, 0, s.width, s.height));
        int target = 6 + (int) (14 * s.density);
        double min = (40 + 60 * s.scale) * s.unit;
        for (int i = 0; i < target * 4 && fields.size() < target; i++) {
            Rectangle2D a = fields.get(s.random.nextInt(fields.size()));
            boolean vertical = a.getWidth() > a.getHeight() ? s.random.nextDouble() < 0.75
                    : s.random.nextDouble() < 0.25;
            double length = vertical ? a.getWidth() : a.getHeight();
            if (length < 2 * min) continue;
            double cut = s.random.nextDouble(min, length - min);
            fields.remove(a);
            if (vertical) {
                fields.add(new Rectangle2D.Double(a.getX(), a.getY(), cut, a.getHeight()));
                fields.add(new Rectangle2D.Double(a.getX() + cut, a.getY(), a.getWidth() - cut, a.getHeight()));
            } else {
                fields.add(new Rectangle2D.Double(a.getX(), a.getY(), a.getWidth(), cut));
                fields.add(new Rectangle2D.Double(a.getX(), a.getY() + cut, a.getWidth(), a.getHeight() - cut));
            }
        }
        for (Rectangle2D a : fields) {
            Color c = s.pick();
            s.g.setColor(s.random.nextDouble() < 0.35 + 0.3 * s.chaos ? c : s.palette.paper());
            s.g.fill(a);
        }
        s.g.setColor(s.palette.ink());
        s.g.setStroke(new java.awt.BasicStroke((float) ((4 + 10 * s.line) * s.unit)));
        for (Rectangle2D a : fields) s.g.draw(a);
        s.g.draw(new Rectangle2D.Double(0, 0, s.width, s.height));
    }

    /** A grid of square tiles, each with a simple figure: quarter or half circle, dot, triangle, bars. */
    static void shapes(Sketch s) {
        int columns = 3 + (int) (5 * s.density);
        double size = (double) s.width / columns;
        int rows = (int) Math.ceil(s.height / size);
        for (int row = 0; row < rows; row++) {
            s.checkCancelled();
            for (int column = 0; column < columns; column++) {
                double x = column * size;
                double y = row * size;
                Color background = s.pick();
                Color figure = s.pick();
                for (int i = 0; i < 8 && figure.equals(background); i++) figure = s.pick();
                s.g.setColor(background);
                s.g.fill(new Rectangle2D.Double(x, y, size + 1, size + 1));
                s.g.setColor(figure);
                int kind = s.random.nextInt(6);
                int turn = s.random.nextInt(4);
                Color third = s.pick();
                AffineTransform old = s.g.getTransform();
                s.g.rotate(turn * Math.PI / 2, x + size / 2, y + size / 2);
                switch (kind) {
                    case 0 -> s.g.fill(new Arc2D.Double(x - size, y - size, 2 * size, 2 * size, 270, 90, Arc2D.PIE));
                    case 1 -> s.g.fill(new Arc2D.Double(x, y - size / 2, size, size, 180, 180, Arc2D.PIE));
                    case 2 -> s.g.fill(new Ellipse2D.Double(x + size * .2, y + size * .2, size * .6, size * .6));
                    case 3 -> {
                        Path2D t = new Path2D.Double();
                        t.moveTo(x, y);
                        t.lineTo(x + size, y);
                        t.lineTo(x, y + size);
                        t.closePath();
                        s.g.fill(t);
                    }
                    case 4 -> {
                        s.g.fill(new Rectangle2D.Double(x, y, size / 2, size));
                        s.g.setColor(third);
                        s.g.fill(new Ellipse2D.Double(x + size * .3, y + size * .3, size * .4, size * .4));
                    }
                    default -> {
                        for (int k = 0; k < 3; k++) {
                            double q = size * (1 - k * 0.33);
                            s.g.setColor(k % 2 == 0 ? figure : background);
                            s.g.fill(new Arc2D.Double(x - q, y + size - q, 2 * q, 2 * q, 0, 90, Arc2D.PIE));
                        }
                    }
                }
                s.g.setTransform(old);
            }
        }
    }

    /** Mountain ridges from noise, lighter and hazier towards the back, with a sun or moon. */
    static void landscape(Sketch s) {
        Color sky = s.palette.paper();
        Color far = Sketch.mix(s.color(5), sky, 0.25);
        Color near = Sketch.mix(s.palette.dark(), Color.BLACK, 0.35);
        s.g.setPaint(new GradientPaint(0, 0, Sketch.mix(sky, s.color(1), 0.25), 0, s.height * 0.7f, sky));
        s.g.fillRect(0, 0, s.width, s.height);
        double sx = s.random.nextDouble(0.2, 0.8) * s.width;
        double sy = s.random.nextDouble(0.15, 0.35) * s.height;
        double sr = (40 + 40 * s.scale) * s.unit;
        s.g.setColor(s.color(0));
        s.g.fill(new Ellipse2D.Double(sx - sr, sy - sr, 2 * sr, 2 * sr));
        int layers = 4 + (int) (5 * s.density);
        int step = Math.max(1, (int) Math.round(s.unit));
        for (int l = 0; l < layers; l++) {
            s.checkCancelled();
            double t = (double) l / (layers - 1);
            double base = s.height * (0.38 + 0.55 * t);
            double amplitude = (60 + 90 * s.chaos) * s.unit * (1.2 - 0.6 * t);
            Path2D ridge = new Path2D.Double();
            ridge.moveTo(0, s.height);
            for (int x = 0; x <= s.width + step; x += step) {
                double v = s.noise.fractal(x / s.unit * 0.004 + l * 13.7, l * 7.1, 5);
                ridge.lineTo(x, base - v * amplitude);
            }
            ridge.lineTo(s.width, s.height);
            ridge.closePath();
            s.g.setColor(Sketch.mix(far, near, t));
            s.g.fill(ridge);
        }
    }

    /** Dots whose density and colour follow noise, like a picture painted with the tip of the brush. */
    static void dots(Sketch s) {
        int count = (int) ((30_000 + 60_000 * s.density) * s.area());
        double frequency = 0.003 + 0.006 * (1 - s.scale);
        int colors = s.palette.colors().size();
        for (int i = 0; i < count; i++) {
            if (i % 5000 == 0) s.checkCancelled();
            double x = s.random.nextDouble() * s.width;
            double y = s.random.nextDouble() * s.height;
            double keep = s.random.nextDouble();
            double v = (s.noise.at(x / s.unit * frequency, y / s.unit * frequency) + 1) / 2;
            if (keep > v * v * 2.2) continue;
            int index = Math.max(0, Math.min(colors - 1, (int) (v * colors * (0.8 + 0.4 * s.chaos))));
            double d = (2.5 + 5 * s.line * v) * s.unit;
            s.g.setColor(Sketch.alpha(s.palette.colors().get(index), 220));
            s.g.fill(new Ellipse2D.Double(x - d / 2, y - d / 2, d, d));
        }
    }
}
