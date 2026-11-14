package eu.amadeos.apps.artstudio.domain;

import java.awt.Dimension;
import java.util.Locale;
import java.util.Optional;

/** The shape of the picture: width to height. */
public enum Aspect {
    SCREEN(16, 10), WIDE(16, 9), SQUARE(1, 1), PORTRAIT(1, Math.sqrt(2)), LANDSCAPE(Math.sqrt(2), 1);

    private final double width;
    private final double height;

    Aspect(double width, double height) {
        this.width = width;
        this.height = height;
    }

    /** Width divided by height. */
    public double ratio() {
        return width / height;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "aspect." + id();
    }

    /** The largest picture of this shape that fits into {@code maxWidth} × {@code maxHeight}, at least 1 × 1. */
    public Dimension fit(int maxWidth, int maxHeight) {
        int maxW = Math.max(1, maxWidth);
        int maxH = Math.max(1, maxHeight);
        int w = maxW;
        int h = (int) Math.round(w / ratio());
        if (h > maxH) {
            h = maxH;
            w = (int) Math.round(h * ratio());
        }
        return new Dimension(Math.min(maxW, Math.max(1, w)), Math.min(maxH, Math.max(1, h)));
    }

    public static Optional<Aspect> ofId(String id) {
        if (id == null) return Optional.empty();
        for (Aspect a : values()) {
            if (a.id().equals(id.strip().toLowerCase(Locale.ROOT))) return Optional.of(a);
        }
        return Optional.empty();
    }
}
