package eu.amadeos.apps.artstudio.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of picture Art Studio draws. Each is named after what one sees, not after an artist or a school.
 * New styles are added at the end: the position takes part in the random numbers, so moving a style would change
 * its pictures. The window shows them by {@link Group} ({@link #shown()}).
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
    ROSETTE,
    /** Millions of points of a chaotic map, piled up into fine loops. */
    ATTRACTOR,
    /** Two substances that spread and react: spots, corals or a maze. */
    REACTION,
    /** Noise bent by noise, like marbled paper. */
    MARBLE,
    /** Height bands with contour lines, like a hiking map. */
    CONTOURS,
    /** Coloured clouds of gas and stars on a dark sky. */
    SKY,
    /** Wheels rolling inside and outside wheels, drawn with a pen. */
    WHEELS,
    /** A maze, its paths coloured by their distance from the middle. */
    LABYRINTH,
    /** Wandering particles that stick to a growing figure: frost or coral. */
    GROWTH,
    /** Triangles of a shaken grid over a soft gradient. */
    TRIANGLES,
    /** Dots in rows at an angle, as in old print. */
    HALFTONE,
    /** Columns of cubes on an isometric grid. */
    CUBES,
    /** Shiny balls with soft shadows. */
    SPHERES,
    /** A landscape of lines in perspective, behind it a low sun. */
    WIREFRAME,
    /** A tube tied into a knot in space. */
    KNOT,
    /** Spheres and rings melted into one smooth sculpture. */
    SCULPTURE;

    /** The groups of the style list. */
    public enum Group {
        LINES, AREAS, NATURE, SOLID;

        public String key() {
            return "style.group." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The group the style is listed in. */
    public Group group() {
        return switch (this) {
            case FLOW, WAVES, TILES, ROSETTE, ATTRACTOR, WHEELS -> Group.LINES;
            case WATERCOLOUR, MOSAIC, CIRCLES, GRID, SHAPES, DOTS, TRIANGLES, HALFTONE, LABYRINTH -> Group.AREAS;
            case PLANTS, LANDSCAPE, MARBLE, CONTOURS, SKY, REACTION, GROWTH -> Group.NATURE;
            case CUBES, SPHERES, WIREFRAME, KNOT, SCULPTURE -> Group.SOLID;
        };
    }

    /** All styles in the order the window lists them: by group, within a group in the order of the enum. */
    public static List<Style> shown() {
        List<Style> all = new ArrayList<>(List.of(values()));
        all.sort(Comparator.comparing(Style::group));
        return List.copyOf(all);
    }

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
            case FLOW, PLANTS, WAVES, TILES, ROSETTE, WHEELS, LABYRINTH, GROWTH, CONTOURS, WIREFRAME -> true;
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
