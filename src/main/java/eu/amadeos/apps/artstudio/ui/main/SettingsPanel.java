package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The recipe on the right: seed and shape, the palette with its colour fields, the four sliders, paper grain
 * and whether new pictures keep their colours. Every change goes straight to the presenter.
 */
public final class SettingsPanel extends JPanel {
    static final int SWATCH = 22;

    private final Messages msg;
    private final JTextField seed = new JTextField(9);
    private final JComboBox<Aspect> aspect = new JComboBox<>(Aspect.values());
    private final JComboBox<String> palette = new JComboBox<>();
    private final Swatches swatches;
    private final JSlider density = slider("density");
    private final JSlider scale = slider("scale");
    private final JSlider chaos = slider("chaos");
    private final JSlider line = slider("line");
    private final JLabel lineLabel;
    private final JCheckBox paper;
    private final JCheckBox keepColors;
    private boolean updating;
    private Recipe shown;

    public SettingsPanel(MainPresenter presenter, ArtActions actions, Messages msg) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setName("settings");
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        c.insets = new Insets(2, 0, 2, 0);

        add(section(msg.get("settings.group.picture")), c);
        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0;
        add(label("settings.seed", seed), c);
        c.gridx = 1;
        c.weightx = 1;
        JPanel seedRow = new JPanel(new BorderLayout(4, 0));
        seed.setName("seed");
        seedRow.add(seed, BorderLayout.CENTER);
        JButton random = new JButton(actions.randomSeed);
        random.setName("random-seed");
        random.setText(msg.get("settings.random"));
        random.setFocusable(false);
        seedRow.add(random, BorderLayout.EAST);
        add(seedRow, c);
        seed.addActionListener(e -> commitSeed(presenter));
        seed.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitSeed(presenter);
            }
        });
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;
        add(label("settings.aspect", aspect), c);
        c.gridx = 1;
        c.weightx = 1;
        aspect.setName("aspect");
        aspect.setRenderer(named(v -> msg.get(((Aspect) v).key())));
        aspect.addActionListener(e -> {
            if (!updating && aspect.getSelectedItem() instanceof Aspect a) presenter.setAspect(a);
        });
        add(aspect, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        add(section(msg.get("settings.group.colors")), c);
        c.gridy++;
        palette.setName("palette");
        palette.setRenderer(named(v -> msg.get("palette." + v)));
        palette.addActionListener(e -> {
            if (updating || !(palette.getSelectedItem() instanceof String id)) return;
            if (id.equals(Palette.DESKTOP)) presenter.useDesktopColors();
            else Palette.builtIn(id).ifPresent(presenter::setPalette);
        });
        add(palette, c);
        c.gridy++;
        swatches = new Swatches(presenter::editSwatch);
        add(swatches, c);
        c.gridy++;
        add(hint(msg.get("settings.colors.hint")), c);

        c.gridy++;
        add(section(msg.get("settings.group.shape")), c);
        c.gridwidth = 1;
        lineLabel = new JLabel();
        JSlider[] sliders = {density, scale, chaos, line};
        String[] keys = {"settings.density", "settings.scale", "settings.chaos", "settings.line"};
        IntConsumer[] targets = {presenter::setDensity, presenter::setScale, presenter::setChaos, presenter::setLine};
        for (int i = 0; i < sliders.length; i++) {
            c.gridy++;
            c.gridx = 0;
            c.weightx = 0;
            JLabel l = i == 3 ? lineLabel : new JLabel();
            Mnemonics.apply(l, msg.get(keys[i]));
            l.setLabelFor(sliders[i]);
            add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            JSlider s = sliders[i];
            IntConsumer target = targets[i];
            s.addChangeListener(e -> {
                if (!updating) target.accept(s.getValue());
            });
            add(s, c);
        }
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        paper = Mnemonics.apply(new JCheckBox(), msg.get("settings.paper"));
        paper.setName("paper");
        paper.addActionListener(e -> {
            if (!updating) presenter.setPaper(paper.isSelected());
        });
        add(paper, c);
        c.gridy++;
        keepColors = Mnemonics.apply(new JCheckBox(), msg.get("settings.keep-colors"));
        keepColors.setName("keep-colors");
        keepColors.addActionListener(e -> {
            if (!updating) presenter.setKeepColors(keepColors.isSelected());
        });
        add(keepColors, c);
        c.gridy++;
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    private void commitSeed(MainPresenter presenter) {
        if (updating || shown == null) return;
        if (seed.getText().strip().equals(Long.toString(shown.seed()))) return;
        if (!presenter.setSeed(seed.getText())) seed.setText(Long.toString(shown.seed()));
    }

    /** Shows the recipe without telling the presenter. */
    public void show(Recipe recipe, boolean keep) {
        EdtExecutor.requireEdt();
        updating = true;
        try {
            shown = recipe;
            if (!seed.isFocusOwner() || !seed.getText().strip().equals(Long.toString(recipe.seed()))) {
                seed.setText(Long.toString(recipe.seed()));
            }
            aspect.setSelectedItem(recipe.aspect());
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (Palette p : Palette.BUILT_IN) model.addElement(p.id());
            model.addElement(Palette.DESKTOP);
            String id = Palette.BUILT_IN.contains(recipe.palette()) || recipe.palette().id().equals(Palette.DESKTOP)
                    ? recipe.palette().id() : Palette.CUSTOM;
            if (id.equals(Palette.CUSTOM)) model.addElement(Palette.CUSTOM);
            model.setSelectedItem(id);
            palette.setModel(model);
            swatches.show(recipe.palette().swatches());
            density.setValue(recipe.density());
            scale.setValue(recipe.scale());
            chaos.setValue(recipe.chaos());
            line.setValue(recipe.line());
            Mnemonics.apply(lineLabel, msg.get(recipe.style().drawsLines() ? "settings.line" : "settings.edges"));
            paper.setSelected(recipe.paper());
            keepColors.setSelected(keep);
        } finally {
            updating = false;
        }
    }

    // ---- parts, for tests

    JTextField seedField() {
        return seed;
    }

    JComboBox<Aspect> aspectBox() {
        return aspect;
    }

    JComboBox<String> paletteBox() {
        return palette;
    }

    JSlider densitySlider() {
        return density;
    }

    JSlider lineSlider() {
        return line;
    }

    JCheckBox paperBox() {
        return paper;
    }

    Swatches swatches() {
        return swatches;
    }

    // ---- helpers

    private static JSlider slider(String name) {
        JSlider s = new JSlider(Recipe.MIN, Recipe.MAX, 50);
        s.setName(name);
        s.setPreferredSize(new Dimension(150, s.getPreferredSize().height));
        s.setMinimumSize(new Dimension(100, s.getPreferredSize().height));
        return s;
    }

    private JLabel label(String key, JComponent target) {
        JLabel l = Mnemonics.apply(new JLabel(), msg.get(key));
        l.setLabelFor(target);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        return l;
    }

    private static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, AmadeUI.theme().shadow())));
        return l;
    }

    private static JLabel hint(String text) {
        // A fixed width lets the label wrap and grow downwards, whatever the language.
        JLabel l = new JLabel("<html><div style='width:230px'>" + text + "</div></html>");
        l.setFont(l.getFont().deriveFont(Font.PLAIN, l.getFont().getSize2D() - 1));
        l.setForeground(AmadeUI.theme().textInactive());
        l.setMinimumSize(l.getPreferredSize());
        return l;
    }

    private static DefaultListCellRenderer named(java.util.function.Function<Object, String> name) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value != null) setText(name.apply(value));
                return this;
            }
        };
    }

    /** The colour fields: paper, ink and the colours; a click asks for a new colour. */
    static final class Swatches extends JPanel {
        private List<Color> colors = List.of();

        Swatches(IntConsumer onClick) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setName("swatches");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension size = new Dimension(10 * (SWATCH + 4), SWATCH + 4);
            setPreferredSize(size);
            setMinimumSize(size);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getX(), e.getY());
                    if (i >= 0) onClick.accept(i);
                }
            });
        }

        void show(List<Color> swatches) {
            colors = List.copyOf(swatches);
            setToolTipText(null);
            repaint();
        }

        int indexAt(int x, int y) {
            if (y < 2 || y > 2 + SWATCH) return -1;
            int i = x / (SWATCH + 4);
            return i < colors.size() && x % (SWATCH + 4) < SWATCH ? i : -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < colors.size(); i++) {
                int x = i * (SWATCH + 4);
                g.setColor(colors.get(i));
                g.fillRoundRect(x, 2, SWATCH, SWATCH, 6, 6);
                g.setColor(AmadeUI.theme().shadow());
                g.drawRoundRect(x, 2, SWATCH, SWATCH, 6, 6);
            }
            g.dispose();
        }
    }
}
