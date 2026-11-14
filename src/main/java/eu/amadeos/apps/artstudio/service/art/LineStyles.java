package eu.amadeos.apps.artstudio.service.art;

import java.awt.Color;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** The styles that draw lines: flow, plants, wave lines, tiles and rosette. */
final class LineStyles {
    private LineStyles() {
    }

    /** Lines that follow the angle of a noise field, like hair or wood grain. */
    static void flow(Sketch s) {
        int count = (int) ((900 + 3500 * s.density) * s.area());
        double frequency = 0.0015 + 0.004 * s.scale;
        double turns = Math.PI * (2 + 3 * s.chaos);
        s.g.setStroke(s.stroke(0.6 + 2.2 * s.line));
        for (int i = 0; i < count; i++) {
            if (i % 200 == 0) s.checkCancelled();
            double x = s.random.nextDouble(-0.05, 1.05) * s.width;
            double y = s.random.nextDouble(-0.05, 1.05) * s.height;
            Path2D path = new Path2D.Double();
            path.moveTo(x, y);
            int steps = 30 + s.random.nextInt(90);
            for (int k = 0; k < steps; k++) {
                double a = s.noise.at(x / s.unit * frequency, y / s.unit * frequency) * turns;
                x += Math.cos(a) * 2 * s.unit;
                y += Math.sin(a) * 2 * s.unit;
                path.lineTo(x, y);
            }
            s.g.setColor(Sketch.alpha(s.pick(), 150));
            s.g.draw(path);
        }
    }

    /** Trees and shrubs grown from an L-system, on a meadow under a soft sky. */
    static void plants(Sketch s) {
        Color top = s.palette.paper();
        Color bottom = Sketch.mix(s.palette.paper(), s.color(3), 0.35);
        s.g.setPaint(new java.awt.GradientPaint(0, 0, top, 0, s.height, bottom));
        s.g.fillRect(0, 0, s.width, s.height);
        s.g.setColor(Sketch.mix(s.palette.ink(), s.palette.paper(), 0.2));
        s.g.fill(new Rectangle2D.Double(0, s.height * 0.9, s.width, s.height * 0.1));
        int plants = Math.max(1, (int) Math.round((3 + 6 * s.density) * s.area()));
        String rule = "FF+[+F-F-F]-[-F+F+F]";
        String grown = "F";
        for (int d = 0; d < 4; d++) grown = grown.replace("F", rule);
        s.g.setStroke(s.stroke(0.5 + 1.5 * s.line));
        for (int i = 0; i < plants; i++) {
            s.checkCancelled();
            double x = (i + 0.5 + s.random.nextDouble(-0.3, 0.3)) * s.width / plants;
            double length = (3.6 + 3.5 * s.scale) * s.unit * s.random.nextDouble(0.7, 1.2);
            double angle = Math.toRadians(18 + 14 * s.chaos * s.random.nextDouble());
            double heading = -Math.PI / 2 + s.random.nextDouble(-0.15, 0.15);
            s.g.setColor(Sketch.alpha(s.color(i), 200));
            s.g.draw(turtle(s, grown, x, s.height * 0.9, heading, length, angle));
        }
    }

    private static Path2D turtle(Sketch s, String commands, double x, double y, double heading, double length,
                                 double angle) {
        double[] stack = new double[3 * 64];
        int top = 0;
        double a = heading;
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        for (int i = 0; i < commands.length(); i++) {
            switch (commands.charAt(i)) {
                case 'F' -> {
                    x += Math.cos(a) * length;
                    y += Math.sin(a) * length;
                    path.lineTo(x, y);
                }
                case '+' -> a += angle + s.random.nextDouble(-0.05, 0.05);
                case '-' -> a -= angle + s.random.nextDouble(-0.05, 0.05);
                case '[' -> {
                    stack[top++] = x;
                    stack[top++] = y;
                    stack[top++] = a;
                }
                case ']' -> {
                    a = stack[--top];
                    y = stack[--top];
                    x = stack[--top];
                    path.moveTo(x, y);
                }
                default -> {
                }
            }
        }
        return path;
    }

    /** Stacked lines on dark ground that swing out in the middle; each hides the ones behind it. */
    static void waves(Sketch s) {
        Color dark = s.palette.dark();
        Color light = s.palette.light();
        s.g.setColor(dark);
        s.g.fillRect(0, 0, s.width, s.height);
        int lines = 30 + (int) (50 * s.density);
        double top = s.height * 0.15;
        double step = s.height * 0.75 / lines;
        double left = s.width * 0.15;
        double right = s.width * 0.85;
        s.g.setStroke(s.stroke(0.8 + 1.6 * s.line));
        for (int l = 0; l < lines; l++) {
            if (l % 10 == 0) s.checkCancelled();
            double y0 = top + l * step;
            Path2D path = new Path2D.Double();
            path.moveTo(left, y0);
            for (double x = left; x <= right; x += 2 * s.unit) {
                double t = (x - left) / (right - left);
                double envelope = Math.pow(Math.max(0, Math.sin(Math.PI * t)), 4 - 2 * s.chaos);
                double v = Math.abs(s.noise.at(x / s.unit * 0.02 * (0.5 + s.scale), l * 0.35))
                        + 0.3 * s.noise.at(x / s.unit * 0.08, l);
                path.lineTo(x, y0 - envelope * v * step * (2.5 + 5 * s.scale));
            }
            Path2D cover = new Path2D.Double(path);
            cover.lineTo(right, y0 + step * 3);
            cover.lineTo(left, y0 + step * 3);
            cover.closePath();
            s.g.setColor(dark);
            s.g.fill(cover);
            s.g.setColor(l % 9 == 4 ? s.color(0) : light);
            s.g.draw(path);
        }
    }

    /** Square tiles with two quarter arcs each, turned at random, so that the arcs join to winding paths. */
    static void tiles(Sketch s) {
        double size = (24 + 70 * s.scale) * s.unit;
        java.awt.BasicStroke stroke = new java.awt.BasicStroke((float) (size * (0.12 + 0.3 * s.line)),
                java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_ROUND);
        s.g.setStroke(stroke);
        int columns = (int) Math.ceil(s.width / size);
        int rows = (int) Math.ceil(s.height / size);
        for (int row = 0; row < rows; row++) {
            s.checkCancelled();
            for (int column = 0; column < columns; column++) {
                double x = column * size;
                double y = row * size;
                boolean flip = s.random.nextDouble() < 0.5;
                boolean random = s.random.nextDouble() < 0.2 + 0.6 * s.chaos;
                Color c = s.pick();
                s.g.setColor(random ? c : s.color((column + row) % 2));
                if (flip) {
                    s.g.draw(new Arc2D.Double(x - size / 2, y - size / 2, size, size, 270, 90, Arc2D.OPEN));
                    s.g.draw(new Arc2D.Double(x + size / 2, y + size / 2, size, size, 90, 90, Arc2D.OPEN));
                } else {
                    s.g.draw(new Arc2D.Double(x + size / 2, y - size / 2, size, size, 180, 90, Arc2D.OPEN));
                    s.g.draw(new Arc2D.Double(x - size / 2, y + size / 2, size, size, 0, 90, Arc2D.OPEN));
                }
            }
        }
    }

    /** Damped oscillations in two directions, as a pendulum with a pen would draw them, on dark ground. */
    static void rosette(Sketch s) {
        s.g.setColor(s.palette.dark());
        s.g.fillRect(0, 0, s.width, s.height);
        double cx = s.width / 2.0;
        double cy = s.height / 2.0;
        double radius = Math.min(s.width, s.height) * 0.46;
        int curves = 2 + (int) (4 * s.density);
        s.g.setStroke(s.stroke(0.6 + 1.6 * s.line));
        for (int c = 0; c < curves; c++) {
            s.checkCancelled();
            double f1 = 2 + s.random.nextInt(4);
            double f2 = f1 + s.random.nextDouble(-0.02, 0.02) * (1 + 3 * s.chaos) + (s.random.nextInt(3) - 1);
            double f3 = f1 + s.random.nextDouble(-0.01, 0.01);
            double f4 = f2 + s.random.nextDouble(-0.01, 0.01);
            double p1 = s.random.nextDouble(2 * Math.PI);
            double p2 = s.random.nextDouble(2 * Math.PI);
            double damping = 0.0004 + 0.0008 * (1 - s.scale);
            Path2D path = new Path2D.Double();
            for (int i = 0; i < 9000; i++) {
                double t = i * 0.02;
                double e = Math.exp(-damping * i);
                double x = cx + radius * e * 0.5 * (Math.sin(f1 * t + p1) + Math.sin(f3 * t));
                double y = cy + radius * e * 0.5 * (Math.sin(f2 * t + p2) + Math.sin(f4 * t + 1.3));
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            s.g.setColor(Sketch.alpha(s.color(c), 190));
            s.g.draw(path);
        }
    }
}
