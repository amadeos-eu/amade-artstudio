package eu.amadeos.apps.artstudio.domain;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteTest {
    @Test
    void builtInPalettes() {
        assertEquals(6, Palette.BUILT_IN.size());
        Set<String> ids = new HashSet<>();
        for (Palette p : Palette.BUILT_IN) {
            assertTrue(ids.add(p.id()));
            assertEquals(p, Palette.builtIn(p.id()).orElseThrow());
            assertEquals("palette." + p.id(), p.key());
            assertEquals(p, Palette.parse(p.id(), p.colorText()));
        }
        assertTrue(Palette.builtIn("rainbow").isEmpty());
    }

    @Test
    void swatchesArePaperInkAndColours() {
        List<Color> s = Palette.NIGHT.swatches();
        assertEquals(Palette.NIGHT.paper(), s.get(0));
        assertEquals(Palette.NIGHT.ink(), s.get(1));
        assertEquals(Palette.NIGHT.colors(), s.subList(2, s.size()));
    }

    @Test
    void changingAColourMakesItOwnColours() {
        Palette p = Palette.OCEAN.withSwatch(0, Color.WHITE);
        assertEquals(Palette.CUSTOM, p.id());
        assertEquals("palette.custom", p.key());
        assertEquals(Color.WHITE, p.paper());
        assertEquals(Color.RED, Palette.OCEAN.withSwatch(1, Color.RED).ink());
        assertEquals(Color.RED, Palette.OCEAN.withSwatch(7, Color.RED).colors().get(5));
        // A built-in name with other colours is read as own colours.
        assertEquals(Palette.CUSTOM, Palette.parse("ocean", p.colorText()).id());
    }

    @Test
    void darkAndLight() {
        assertEquals(Palette.NIGHT.paper(), Palette.NIGHT.dark());
        assertEquals(Palette.NIGHT.ink(), Palette.NIGHT.light());
        assertEquals(Palette.AUTUMN.ink(), Palette.AUTUMN.dark());
        assertEquals(Palette.AUTUMN.paper(), Palette.AUTUMN.light());
    }

    @Test
    void wrongPalettes() {
        assertThrows(IllegalArgumentException.class, () -> new Palette("x", Color.WHITE, Color.BLACK, List.of(Color.RED)));
        assertThrows(IllegalArgumentException.class, () -> Palette.parse("x", "#FFFFFF #000000 #FF0000"));
        assertThrows(IllegalArgumentException.class, () -> Palette.parse("x", "#FFFFFF #000000 red blue"));
        assertEquals("#0A0B0C", Palette.hex(new Color(10, 11, 12)));
        assertEquals(Palette.CUSTOM, Palette.parse(" ", "#FFFFFF #000000 #FF0000 #00FF00").id());
    }

    @Test
    void aspects() {
        assertEquals(1.6, Aspect.SCREEN.ratio(), 1e-9);
        assertEquals(new Dimension(800, 500), Aspect.SCREEN.fit(800, 900));
        assertEquals(new Dimension(800, 500), Aspect.SCREEN.fit(1000, 500));
        assertEquals(new Dimension(300, 300), Aspect.SQUARE.fit(500, 300));
        Dimension portrait = Aspect.PORTRAIT.fit(1000, 1000);
        assertEquals(1000, portrait.height);
        assertEquals(707, portrait.width);
        assertEquals(new Dimension(1, 1), Aspect.WIDE.fit(0, 0));
        for (Aspect a : Aspect.values()) assertEquals(a, Aspect.ofId(a.id()).orElseThrow());
        assertTrue(Aspect.ofId("round").isEmpty());
        assertNotEquals(Aspect.PORTRAIT.ratio(), Aspect.LANDSCAPE.ratio());
    }
}
