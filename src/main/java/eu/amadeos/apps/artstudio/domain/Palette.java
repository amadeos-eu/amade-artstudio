package eu.amadeos.apps.artstudio.domain;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The colours of a picture: the paper, the ink (lines, grout, the darkest tone) and the colours the styles pick
 * from. A recipe keeps all of them, so that a picture looks the same on every computer, also with colours
 * someone changed by hand.
 *
 * @param id     "autumn", "ocean", ... for the built-in palettes, {@link #DESKTOP} or {@link #CUSTOM}
 * @param paper  the background
 * @param ink    lines, grout and the darkest tone
 * @param colors two to eight colours
 */
public record Palette(String id, Color paper, Color ink, List<Color> colors) {
    public static final String CUSTOM = "custom";
    public static final String DESKTOP = "desktop";
    public static final int MIN_COLORS = 2;
    public static final int MAX_COLORS = 8;

    public Palette {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(ink, "ink");
        colors = List.copyOf(colors);
        if (colors.size() < MIN_COLORS || colors.size() > MAX_COLORS) {
            throw new IllegalArgumentException("A palette has " + MIN_COLORS + " to " + MAX_COLORS + " colours");
        }
    }

    public static final Palette AUTUMN = of("autumn", 0xF3EBDD, 0x2B2118,
            0xC8553D, 0xF28F3B, 0xFFD5C2, 0x588B8B, 0x2D3047, 0x93B7BE);
    public static final Palette OCEAN = of("ocean", 0xEEF3F1, 0x0B1D2A,
            0x05668D, 0x028090, 0x00A896, 0x02C39A, 0xF0F3BD, 0x1B3A4B);
    public static final Palette PASTEL = of("pastel", 0xFBF8F3, 0x3D3B40,
            0xF6BD60, 0xF7EDE2, 0xF5CAC3, 0x84A59D, 0xF28482, 0xA5B4CF);
    public static final Palette PRIMARY = of("primary", 0xF4F1EA, 0x111111,
            0xD62828, 0x1D4ED8, 0xF7C600, 0xF4F1EA, 0xF4F1EA);
    public static final Palette NIGHT = of("night", 0x0E1020, 0xE8E6F0,
            0x7F5AF0, 0x2CB67D, 0xFF8906, 0xE53170, 0x72757E, 0x16C1D9);
    public static final Palette EARTH = of("earth", 0xEDE6D6, 0x2E2A24,
            0x6B4226, 0xA0522D, 0xC2A878, 0x5B6B47, 0x8C9A6B, 0x3D405B);

    /** The palettes that come with Art Studio, in the order of the list. */
    public static final List<Palette> BUILT_IN = List.of(AUTUMN, OCEAN, PASTEL, PRIMARY, NIGHT, EARTH);

    private static Palette of(String id, int paper, int ink, int... colors) {
        List<Color> list = new ArrayList<>();
        for (int c : colors) list.add(new Color(c));
        return new Palette(id, new Color(paper), new Color(ink), list);
    }

    public static Optional<Palette> builtIn(String id) {
        return BUILT_IN.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The key of the name in the messages. */
    public String key() {
        return "palette." + (BUILT_IN.contains(this) || id.equals(DESKTOP) ? id : CUSTOM);
    }

    /** Paper, ink and the colours: what the colour fields show, in this order. */
    public List<Color> swatches() {
        List<Color> all = new ArrayList<>();
        all.add(paper);
        all.add(ink);
        all.addAll(colors);
        return all;
    }

    /** The palette with swatch {@code index} (0 paper, 1 ink, then the colours) changed; it is custom then. */
    public Palette withSwatch(int index, Color color) {
        Objects.requireNonNull(color, "color");
        if (index == 0) return new Palette(CUSTOM, color, ink, colors);
        if (index == 1) return new Palette(CUSTOM, paper, color, colors);
        List<Color> list = new ArrayList<>(colors);
        list.set(index - 2, color);
        return new Palette(CUSTOM, paper, ink, list);
    }

    /** The darker of paper and ink: the background of the styles that draw light lines on dark. */
    public Color dark() {
        return luminance(paper) <= luminance(ink) ? paper : ink;
    }

    /** The lighter of paper and ink. */
    public Color light() {
        return luminance(paper) <= luminance(ink) ? ink : paper;
    }

    static double luminance(Color c) {
        return 0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue();
    }

    /** "#F3EBDD #2B2118 #C8553D ...": paper, ink and the colours. */
    public String colorText() {
        StringBuilder text = new StringBuilder();
        for (Color c : swatches()) {
            if (!text.isEmpty()) text.append(' ');
            text.append(hex(c));
        }
        return text.toString();
    }

    /**
     * The palette from {@link #colorText()}.
     *
     * @throws IllegalArgumentException if a colour cannot be read or there are too few or too many
     */
    public static Palette parse(String id, String colorText) {
        String[] parts = colorText.strip().split("[\\s,]+");
        if (parts.length < 2 + MIN_COLORS) throw new IllegalArgumentException("Too few colours: " + colorText);
        List<Color> colors = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) colors.add(color(parts[i]));
        Palette palette = new Palette(id == null || id.isBlank() ? CUSTOM : id.strip(), color(parts[0]),
                color(parts[1]), colors);
        // A built-in name with other colours is custom.
        Optional<Palette> builtIn = builtIn(palette.id());
        if (builtIn.isPresent() && !builtIn.get().equals(palette)) {
            return new Palette(CUSTOM, palette.paper(), palette.ink(), palette.colors());
        }
        return palette;
    }

    public static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    static Color color(String text) {
        if (!text.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Not a colour: " + text);
        return new Color(Integer.parseInt(text.substring(1), 16));
    }
}
