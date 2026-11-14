package eu.amadeos.apps.artstudio.ui.icons;

import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The toolbar glyphs only Art Studio needs; open, save, copy, export and full screen come from
 * {@link StandardGlyph}. Highlights are in the theme's selection colour.
 */
public enum ArtGlyph implements ToolIcon.Glyph {
    NEW_PICTURE, PICTURE, BACK, FORWARD, VARIATIONS, BACKDROP;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case NEW_PICTURE -> dice(pen);
            case PICTURE -> picture(pen, 1.5, 2.5, 13, 11);
            case BACK -> arrow(pen);
            case FORWARD -> {
                pen.mirror();
                arrow(pen);
            }
            case VARIATIONS -> grid(pen);
            case BACKDROP -> monitor(pen);
        }
    }

    /** A die showing five: a new picture by chance. */
    private static void dice(ToolIcon.Pen pen) {
        pen.fillAndDraw(new RoundRectangle2D.Double(2, 2, 12, 12, 4, 4), pen.paper());
        double[][] dots = {{5, 5}, {11, 5}, {8, 8}, {5, 11}, {11, 11}};
        for (double[] d : dots) pen.fill(new Ellipse2D.Double(d[0] - 1.3, d[1] - 1.3, 2.6, 2.6), pen.ink());
        pen.fill(new Ellipse2D.Double(8 - 1.3, 8 - 1.3, 2.6, 2.6), pen.light());
    }

    /** A framed picture: sun and mountains. */
    private static void picture(ToolIcon.Pen pen, double x, double y, double w, double h) {
        pen.fillAndDraw(new Rectangle2D.Double(x, y, w, h), pen.paper());
        pen.fill(new Ellipse2D.Double(x + w - 5, y + 1.5, 3, 3), pen.light());
        Path2D hills = new Path2D.Double();
        hills.moveTo(x + 0.5, y + h - 0.5);
        hills.lineTo(x + w * 0.35, y + h * 0.4);
        hills.lineTo(x + w * 0.55, y + h * 0.7);
        hills.lineTo(x + w * 0.72, y + h * 0.5);
        hills.lineTo(x + w - 0.5, y + h - 0.5);
        hills.closePath();
        pen.fill(hills, pen.ink());
    }

    /** Back points left; forward is its mirror image. */
    private static void arrow(ToolIcon.Pen pen) {
        Path2D shape = new Path2D.Double();
        shape.moveTo(1.5, 8);
        shape.lineTo(7.5, 2);
        shape.lineTo(7.5, 5.5);
        shape.lineTo(14.5, 5.5);
        shape.lineTo(14.5, 10.5);
        shape.lineTo(7.5, 10.5);
        shape.lineTo(7.5, 14);
        shape.closePath();
        pen.fillAndDraw(shape, pen.paper());
    }

    /** Three by three small pictures, the middle one highlighted. */
    private static void grid(ToolIcon.Pen pen) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Rectangle2D cell = new Rectangle2D.Double(1.5 + column * 4.5, 1.5 + row * 4.5, 3.5, 3.5);
                pen.fill(cell, row == 1 && column == 1 ? pen.light() : pen.ink());
            }
        }
    }

    /** A screen on a stand with a small picture on it. */
    private static void monitor(ToolIcon.Pen pen) {
        pen.fillAndDraw(new Rectangle2D.Double(1.5, 2, 13, 9.5), pen.paper());
        pen.fill(new Ellipse2D.Double(10, 3.5, 2.5, 2.5), pen.light());
        Path2D hills = new Path2D.Double();
        hills.moveTo(2.5, 10.5);
        hills.lineTo(6, 6.5);
        hills.lineTo(8.5, 9);
        hills.lineTo(10.5, 7.5);
        hills.lineTo(13.5, 10.5);
        hills.closePath();
        pen.fill(hills, pen.ink());
        pen.line(8, 11.5, 8, 13.5);
        pen.line(5, 14, 11, 14);
    }
}
