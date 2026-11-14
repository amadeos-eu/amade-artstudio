package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.artstudio.domain.Style;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The styles with a small picture each, in groups with a heading; choosing one draws the picture in that style.
 */
public final class StyleList extends JList<Style> {
    private final Map<Style, ImageIcon> thumbnails = new EnumMap<>(Style.class);
    private boolean updating;

    public StyleList(Messages msg, Consumer<Style> onSelect) {
        super(Style.shown().toArray(new Style[0]));
        EdtExecutor.requireEdt();
        setName("styles");
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setVisibleRowCount(12);
        JLabel heading = new JLabel();
        heading.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                Style style = (Style) value;
                label.setText(msg.get(style.key()));
                ImageIcon icon = thumbnails.get(style);
                label.setIcon(icon != null ? icon : EMPTY);
                label.setIconTextGap(8);
                label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 8));
                if (!startsGroup(index)) return label;
                // The first style of a group carries the group's heading above it.
                heading.setText(msg.get(style.group().key()));
                heading.setFont(list.getFont().deriveFont(java.awt.Font.BOLD));
                heading.setForeground(list.getForeground());
                cell.removeAll();
                cell.add(heading, BorderLayout.NORTH);
                cell.add(label, BorderLayout.CENTER);
                return cell;
            }
        });
        addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && getSelectedValue() != null) onSelect.accept(getSelectedValue());
        });
    }

    /** Whether the style at {@code index} is the first of its group. */
    boolean startsGroup(int index) {
        return index == 0 || getModel().getElementAt(index - 1).group() != getModel().getElementAt(index).group();
    }

    private static final ImageIcon EMPTY = new ImageIcon(new BufferedImage(MainPresenter.STYLE_THUMB_WIDTH,
            MainPresenter.STYLE_THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB));

    /** Selects the style without telling anyone. */
    public void show(Style style) {
        updating = true;
        try {
            setSelectedValue(style, true);
        } finally {
            updating = false;
        }
    }

    public void setThumbnail(Style style, BufferedImage thumbnail) {
        thumbnails.put(style, new ImageIcon(thumbnail));
        repaint();
    }
}
