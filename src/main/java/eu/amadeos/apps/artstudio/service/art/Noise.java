package eu.amadeos.apps.artstudio.service.art;

import java.util.SplittableRandom;

/** Smooth 2D gradient noise from a seed: values from about -1 to 1 that change slowly from point to point. */
public final class Noise {
    private final int[] perm = new int[512];

    public Noise(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    public double at(double x, double y) {
        double fx = Math.floor(x);
        double fy = Math.floor(y);
        int xi = (int) fx & 255;
        int yi = (int) fy & 255;
        double xf = x - fx;
        double yf = y - fy;
        double u = fade(xf);
        double v = fade(yf);
        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];
        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v) * 1.4;
    }

    /** Several octaves added up: rougher, like mountains. */
    public double fractal(double x, double y, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * at(x * f, y * f);
            f *= 2.1;
            amplitude *= 0.5;
        }
        return sum;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }
}
