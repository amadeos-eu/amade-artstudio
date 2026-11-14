package eu.amadeos.apps.artstudio.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Everything a picture depends on. The same recipe gives the same picture on every computer, and at every size
 * the same composition: positions and sizes are measured in a unit that grows with the picture.
 *
 * @param style   what kind of picture
 * @param seed    the random numbers; every seed is another picture
 * @param palette the colours
 * @param density how much there is: lines, cells, circles, layers (0 to 100)
 * @param scale   how large the parts are (0 to 100)
 * @param chaos   how irregular it gets (0 to 100)
 * @param line    the width of lines and grout (0 to 100)
 * @param paper   a fine grain like paper over the picture
 * @param aspect  the shape of the picture
 */
public record Recipe(Style style, long seed, Palette palette, int density, int scale, int chaos, int line,
                     boolean paper, Aspect aspect) {
    public static final int MIN = 0;
    public static final int MAX = 100;
    /** The first line of a recipe file. */
    public static final String HEADER = "# amadeOS Art Studio recipe";
    /** The largest seed; seeds are typed in, so they stay short. */
    public static final long MAX_SEED = 999_999_999L;

    public Recipe {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(aspect, "aspect");
        if (seed < 0 || seed > MAX_SEED) throw new IllegalArgumentException("Seed out of range: " + seed);
        density = clamp(density);
        scale = clamp(scale);
        chaos = clamp(chaos);
        line = clamp(line);
    }

    /** A recipe with the sliders in the middle, paper grain on and the shape of a screen. */
    public static Recipe of(Style style, long seed, Palette palette) {
        return new Recipe(style, seed, palette, 50, 50, 50, 50, true, Aspect.SCREEN);
    }

    private static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    public Recipe withStyle(Style s) {
        return new Recipe(s, seed, palette, density, scale, chaos, line, paper, aspect);
    }

    public Recipe withSeed(long s) {
        return new Recipe(style, s, palette, density, scale, chaos, line, paper, aspect);
    }

    public Recipe withPalette(Palette p) {
        return new Recipe(style, seed, p, density, scale, chaos, line, paper, aspect);
    }

    public Recipe withDensity(int v) {
        return new Recipe(style, seed, palette, v, scale, chaos, line, paper, aspect);
    }

    public Recipe withScale(int v) {
        return new Recipe(style, seed, palette, density, v, chaos, line, paper, aspect);
    }

    public Recipe withChaos(int v) {
        return new Recipe(style, seed, palette, density, scale, v, line, paper, aspect);
    }

    public Recipe withLine(int v) {
        return new Recipe(style, seed, palette, density, scale, chaos, v, paper, aspect);
    }

    public Recipe withPaper(boolean p) {
        return new Recipe(style, seed, palette, density, scale, chaos, line, p, aspect);
    }

    public Recipe withAspect(Aspect a) {
        return new Recipe(style, seed, palette, density, scale, chaos, line, paper, a);
    }

    /** The recipe as text, one {@code key=value} per line; {@link #parse} reads it back. */
    public String toText() {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Map.Entry<String, String> e : values().entrySet()) {
            text.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return text.toString();
    }

    /** The recipe on one line, for the PNG text chunk and the clipboard: {@code style=flow; seed=48213; ...} */
    public String toLine() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> e : values().entrySet()) {
            if (!text.isEmpty()) text.append("; ");
            text.append(e.getKey()).append('=').append(e.getValue());
        }
        return text.toString();
    }

    private Map<String, String> values() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("style", style.id());
        v.put("seed", Long.toString(seed));
        v.put("palette", palette.id());
        v.put("colors", palette.colorText());
        v.put("density", Integer.toString(density));
        v.put("scale", Integer.toString(scale));
        v.put("chaos", Integer.toString(chaos));
        v.put("line", Integer.toString(line));
        v.put("paper", Boolean.toString(paper));
        v.put("aspect", aspect.id());
        return v;
    }

    /**
     * Reads a recipe written by {@link #toText()} or {@link #toLine()}. Missing sliders take their middle
     * value, a missing shape the screen's; style, seed and colours must be there.
     *
     * @throws IllegalArgumentException if the text is not a recipe
     */
    public static Recipe parse(String text) {
        Map<String, String> v = new LinkedHashMap<>();
        for (String part : text.split("[\\n;]")) {
            String p = part.strip();
            if (p.isEmpty() || p.startsWith("#")) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            v.put(p.substring(0, eq).strip(), p.substring(eq + 1).strip());
        }
        Style style = Style.ofId(v.get("style"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown or missing style: " + v.get("style")));
        long seed;
        try {
            seed = Long.parseLong(v.getOrDefault("seed", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Missing or wrong seed: " + v.get("seed"));
        }
        String colors = v.get("colors");
        Palette palette = colors != null ? Palette.parse(v.get("palette"), colors)
                : Palette.builtIn(v.getOrDefault("palette", ""))
                .orElseThrow(() -> new IllegalArgumentException("Missing colours"));
        return new Recipe(style, seed, palette, number(v, "density"), number(v, "scale"), number(v, "chaos"),
                number(v, "line"), !"false".equals(v.get("paper")),
                Aspect.ofId(v.get("aspect")).orElse(Aspect.SCREEN));
    }

    private static int number(Map<String, String> v, String key) {
        try {
            return Integer.parseInt(v.getOrDefault(key, "50"));
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /** "flow-48213", for file names. */
    public String fileName() {
        return style.id() + "-" + seed;
    }
}
