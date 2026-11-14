package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** The last pictures as a row of small buttons below the picture; a click goes back to one. */
public final class HistoryStrip extends JPanel {
    private static final BufferedImage BLANK = new BufferedImage(MainPresenter.THUMB_WIDTH, MainPresenter.THUMB_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);

    private final JPanel row = new JPanel();
    private final JScrollPane scroll;
    private final IntConsumer onPick;
    private final List<JButton> buttons = new ArrayList<>();

    public HistoryStrip(String title, IntConsumer onPick) {
        super(new BorderLayout(6, 0));
        EdtExecutor.requireEdt();
        this.onPick = onPick;
        setName("history");
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JLabel label = new JLabel(title);
        label.setForeground(AmadeUI.theme().textInactive());
        add(label, BorderLayout.WEST);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        scroll = new JScrollPane(row, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        scroll.setPreferredSize(new Dimension(200, MainPresenter.THUMB_HEIGHT + 26));
        add(scroll, BorderLayout.CENTER);
    }

    public void show(List<BufferedImage> thumbnails, int current) {
        EdtExecutor.requireEdt();
        row.removeAll();
        buttons.clear();
        for (int i = 0; i < thumbnails.size(); i++) {
            BufferedImage t = thumbnails.get(i);
            JButton b = new JButton(new ImageIcon(t != null ? t : BLANK));
            b.setName("history-" + i);
            b.setFocusable(false);
            int index = i;
            b.addActionListener(e -> onPick.accept(index));
            b.setBorder(i == current
                    ? BorderFactory.createLineBorder(AmadeUI.theme().selectionBackground(), 3)
                    : BorderFactory.createEmptyBorder(3, 3, 3, 3));
            b.setContentAreaFilled(false);
            buttons.add(b);
            row.add(b);
            row.add(Box.createHorizontalStrut(4));
        }
        row.revalidate();
        row.repaint();
        if (current >= 0 && current < buttons.size()) {
            JButton c = buttons.get(current);
            SwingUtilities.invokeLater(() -> row.scrollRectToVisible(c.getBounds()));
        }
    }

    /** The buttons, for tests. */
    List<JButton> buttons() {
        return buttons;
    }
}
