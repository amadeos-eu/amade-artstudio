package eu.amadeos.apps.artstudio.ui.view;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The picture in the middle of the window, on a darker desk; or the grid of nine variations, of which a click
 * picks one. A picture that does not fit (while the window is resized) is scaled down.
 */
public final class PictureView extends JComponent {
    /** Space around the picture and between the variations. */
    public static final int MARGIN = 16;

    private BufferedImage picture;
    private List<BufferedImage> variations;
    private int hover = -1;
    private String placeholder = "";
    private boolean bare;
    private IntConsumer onPick = i -> { };

    public PictureView() {
        setPreferredSize(new Dimension(820, 540));
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (variations == null || e.getButton() != MouseEvent.BUTTON1) return;
                int cell = cellAt(e.getX(), e.getY());
                if (cell >= 0) onPick.accept(cell);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int cell = variations == null ? -1 : cellAt(e.getX(), e.getY());
                if (cell != hover) {
                    hover = cell;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hover >= 0) {
                    hover = -1;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setPlaceholder(String text) {
        placeholder = text;
        repaint();
    }

    /** Called with the index of a variation that was clicked. */
    public void setOnPick(IntConsumer onPick) {
        this.onPick = onPick;
    }

    /** Full screen: black around the picture, no shadow. */
    public void setBare(boolean bare) {
        this.bare = bare;
        repaint();
    }

    public void setPicture(BufferedImage picture) {
        this.picture = picture;
        this.variations = null;
        hover = -1;
        repaint();
    }

    public BufferedImage picture() {
        return picture;
    }

    public void setVariations(List<BufferedImage> pictures) {
        this.variations = new ArrayList<>(pictures);
        repaint();
    }

    public boolean showsVariations() {
        return variations != null;
    }

    /** The cell of the variations grid at a point, or -1. */
    public int cellAt(int x, int y) {
        if (variations == null) return -1;
        for (int i = 0; i < variations.size(); i++) {
            if (cell(i).contains(x, y)) return i;
        }
        return -1;
    }

    /** Where variation {@code i} goes: three by three equal cells with a margin. */
    public Rectangle cell(int i) {
        int w = (getWidth() - 4 * MARGIN) / 3;
        int h = (getHeight() - 4 * MARGIN) / 3;
        return new Rectangle(MARGIN + (i % 3) * (w + MARGIN), MARGIN + (i / 3) * (h + MARGIN), Math.max(1, w),
                Math.max(1, h));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            DesktopTheme theme = AmadeUI.theme();
            g.setColor(bare ? Color.BLACK : desk(theme));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (variations != null) {
                for (int i = 0; i < variations.size(); i++) paintCell(g, i, theme);
            } else if (picture != null) {
                Rectangle r = fit(picture, new Rectangle(0, 0, getWidth(), getHeight()), bare ? 0 : MARGIN);
                if (!bare) shadow(g, r);
                g.drawImage(picture, r.x, r.y, r.width, r.height, null);
            } else {
                text(g, placeholder, theme, new Rectangle(0, 0, getWidth(), getHeight()));
            }
        } finally {
            g.dispose();
        }
    }

    private void paintCell(Graphics2D g, int i, DesktopTheme theme) {
        Rectangle cell = cell(i);
        BufferedImage p = variations.get(i);
        if (p == null) {
            g.setColor(new Color(128, 128, 128, 40));
            g.fill(cell);
            return;
        }
        Rectangle r = fit(p, cell, 0);
        shadow(g, r);
        g.drawImage(p, r.x, r.y, r.width, r.height, null);
        boolean middle = i == variations.size() / 2;
        if (middle || i == hover) {
            g.setColor(middle ? theme.selectionBackground() : new Color(255, 255, 255, 160));
            g.setStroke(new java.awt.BasicStroke(middle ? 4f : 2f));
            g.drawRect(r.x - 2, r.y - 2, r.width + 3, r.height + 3);
        }
    }

    /** The largest rectangle of the picture's shape inside {@code area}, centred, never larger than the picture. */
    static Rectangle fit(BufferedImage p, Rectangle area, int margin) {
        int aw = Math.max(1, area.width - 2 * margin);
        int ah = Math.max(1, area.height - 2 * margin);
        double f = Math.min(1, Math.min((double) aw / p.getWidth(), (double) ah / p.getHeight()));
        int w = Math.max(1, (int) Math.round(p.getWidth() * f));
        int h = Math.max(1, (int) Math.round(p.getHeight() * f));
        return new Rectangle(area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h);
    }

    private static void shadow(Graphics2D g, Rectangle r) {
        for (int i = 1; i <= 4; i++) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fillRect(r.x + i, r.y + i + 1, r.width, r.height);
        }
    }

    private void text(Graphics2D g, String text, DesktopTheme theme, Rectangle area) {
        if (text == null || text.isEmpty()) return;
        g.setColor(theme.textInactive());
        g.setFont(getFont() != null ? getFont() : g.getFont());
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, area.x + (area.width - fm.stringWidth(text)) / 2,
                area.y + (area.height + fm.getAscent()) / 2);
    }

    /** A little darker than the window, so that light and dark pictures both stand out. */
    static Color desk(DesktopTheme theme) {
        Color base = theme.base();
        float f = theme.isDark() ? 0.65f : 0.82f;
        return new Color(Math.round(base.getRed() * f), Math.round(base.getGreen() * f), Math.round(base.getBlue() * f));
    }
}
