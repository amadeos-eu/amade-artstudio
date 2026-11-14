package eu.amadeos.apps.artstudio.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of picture Art Studio draws. Each is named after what one sees, not after an artist or a school.
 * The order is the order of the list in the window.
 */
public enum Style {
    /** Lines that follow a noise field. */
    FLOW,
    /** Shapes deformed again and again and laid down in many translucent layers. */
    WATERCOLOUR,
    /** Voronoi cells with grout between them. */
    MOSAIC,
    /** Circle packing, large circles with rings. */
    CIRCLES,
    /** A rectangle split again and again, thick lines, some fields coloured. */
    GRID,
    /** A grid of quarter circles, half circles and triangles. */
    SHAPES,
    /** Plants grown from L-systems. */
    PLANTS,
    /** Mountain ridges from noise, hazy towards the back. */
    LANDSCAPE,
    /** Stacked lines that swing out in the middle. */
    WAVES,
    /** Tiles with quarter arcs that join to paths. */
    TILES,
    /** Dots whose density and colour follow noise. */
    DOTS,
    /** Damped oscillations, as a pendulum would draw them. */
    ROSETTE;

    /** The name in recipes and files: "flow", "watercolour", ... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The key of the name in the messages. */
    public String key() {
        return "style." + id();
    }

    /** Whether the style draws lines on paper (Line width means the lines then). */
    public boolean drawsLines() {
        return switch (this) {
            case FLOW, PLANTS, WAVES, TILES, ROSETTE -> true;
            default -> false;
        };
    }

    public static Optional<Style> ofId(String id) {
        if (id == null) return Optional.empty();
        for (Style s : values()) {
            if (s.id().equals(id.strip().toLowerCase(Locale.ROOT))) return Optional.of(s);
        }
        return Optional.empty();
    }
}
