package eu.amadeos.apps.artstudio.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PictureViewTest {
    private static BufferedImage paint(PictureView view) {
        BufferedImage canvas = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = canvas.createGraphics();
        view.paint(g);
        g.dispose();
        return canvas;
    }

    @Test
    void nineCellsWithoutOverlap() {
        PictureView view = new PictureView();
        view.setSize(316, 216);
        assertEquals(-1, view.cellAt(50, 50), "no cells without variations");
        view.setVariations(Arrays.asList(new BufferedImage[9]));
        assertTrue(view.showsVariations());
        for (int i = 0; i < 9; i++) {
            Rectangle cell = view.cell(i);
            assertEquals(i, view.cellAt((int) cell.getCenterX(), (int) cell.getCenterY()));
            for (int j = i + 1; j < 9; j++) assertFalse(cell.intersects(view.cell(j)));
            assertTrue(new Rectangle(0, 0, 316, 216).contains(cell));
        }
        assertEquals(-1, view.cellAt(1, 1), "the margin belongs to no cell");
    }

    @Test
    void aPictureReplacesTheVariations() {
        PictureView view = new PictureView();
        view.setSize(200, 120);
        view.setVariations(new ArrayList<>(Arrays.asList(new BufferedImage[9])));
        BufferedImage picture = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        view.setPicture(picture);
        assertSame(picture, view.picture());
        assertFalse(view.showsVariations());
    }

    @Test
    void paintsInEveryState() {
        PictureView view = new PictureView();
        view.setSize(200, 120);
        view.setPlaceholder("Drawing...");
        paint(view);
        BufferedImage red = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 60; y++) for (int x = 0; x < 100; x++) red.setRGB(x, y, 0xFF0000);
        view.setPicture(red);
        assertEquals(0xFF0000, paint(view).getRGB(100, 60) & 0xFFFFFF, "the picture is in the middle");
        view.setBare(true);
        assertEquals(0x000000, paint(view).getRGB(1, 1) & 0xFFFFFF, "full screen is black around it");
        List<BufferedImage> some = new ArrayList<>(Arrays.asList(new BufferedImage[9]));
        some.set(0, red);
        view.setVariations(some);
        paint(view);
    }
}
