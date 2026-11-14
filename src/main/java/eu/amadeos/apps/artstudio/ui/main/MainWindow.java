package eu.amadeos.apps.artstudio.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.ImageSelection;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.artstudio.bootstrap.AmadeArtStudioApplication;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.ui.view.PictureView;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The main window: menu bar and toolbar, the styles on the left, the picture and its history in the middle,
 * the recipe on the right and a status bar.
 */
public final class MainWindow extends JFrame implements MainView {
    /** Drawing for a new window size waits until the resizing pauses this long. */
    static final int RESIZE_DELAY = 200;

    private final Messages msg;
    private final LocaleSettings locales;
    private final PictureView picture = new PictureView();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JPanel statusBar;
    private final JPanel centre = new JPanel(new BorderLayout());
    private final Timer resizeTimer;
    private boolean fullScreen;
    private Rectangle normalBounds;
    private int normalState;
    private StyleList styleList;
    private JScrollPane stylePane;
    private JScrollPane settingsPane;
    private SettingsPanel settingsPanel;
    private HistoryStrip historyStrip;
    private MainMenuBar menuBar;
    private MainToolBar toolBar;
    private ArtActions actions;
    private MainPresenter presenter;
    private File lastDirectory;

    @Inject
    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        picture.setName("picture");
        picture.setPlaceholder(msg.get("placeholder"));
        centre.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        centre.add(picture, BorderLayout.CENTER);
        add(centre, BorderLayout.CENTER);
        statusBar = statusBar();
        add(statusBar, BorderLayout.SOUTH);
        java.net.URL icon = getClass().getResource("/icons/amade-artstudio.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
        resizeTimer = new Timer(RESIZE_DELAY, e -> {
            if (presenter != null) presenter.canvasResized(picture.getWidth(), picture.getHeight());
        });
        resizeTimer.setRepeats(false);
        picture.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
        setPreferredSize(new Dimension(1280, 800));
        pack();
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        actions = new ArtActions(presenter, msg);
        menuBar = new MainMenuBar(presenter, actions, msg, locales);
        setJMenuBar(menuBar);
        toolBar = new MainToolBar(actions);
        add(toolBar, BorderLayout.NORTH);
        styleList = new StyleList(msg, presenter::selectStyle);
        stylePane = new JScrollPane(styleList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(stylePane, BorderLayout.WEST);
        settingsPanel = new SettingsPanel(presenter, actions, msg);
        settingsPane = new JScrollPane(settingsPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        settingsPane.setBorder(BorderFactory.createEmptyBorder());
        settingsPane.getVerticalScrollBar().setUnitIncrement(16);
        add(settingsPane, BorderLayout.EAST);
        historyStrip = new HistoryStrip(msg.get("history"), presenter::goToHistory);
        centre.add(historyStrip, BorderLayout.SOUTH);
        picture.setOnPick(presenter::pickVariation);
        // The desktop palette follows the colour theme.
        AmadeUI.style(picture, (c, theme) -> {
            if (presenter != null) presenter.themeChanged();
        });
        for (Action a : actions.all()) {
            KeyStroke key = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
            if (key != null) bindKey(key, a);
        }
        for (ArtActions.KeyBinding binding : actions.extraKeys()) bindKey(binding.key(), binding.action());
        for (Bar bar : Bar.values()) showBar(bar, presenter.isBarVisible(bar));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        updateCommands(Commands.NONE);
        pack();
        setLocationRelativeTo(null);
    }

    private void bindKey(KeyStroke key, Action action) {
        String name = action.getValue(Action.ACTION_COMMAND_KEY) + "@" + key;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getRootPane().getActionMap().put(name, action);
    }

    // ---- parts, for tests

    public PictureView pictureView() {
        return picture;
    }

    public ArtActions actions() {
        return actions;
    }

    SettingsPanel settingsPanel() {
        return settingsPanel;
    }

    StyleList styleList() {
        return styleList;
    }

    HistoryStrip historyStrip() {
        return historyStrip;
    }

    String statusText() {
        return status.getText();
    }

    JProgressBar progressBar() {
        return progress;
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showTitle(String title) {
        EdtExecutor.requireEdt();
        setTitle(title);
    }

    @Override
    public void showRecipe(Recipe recipe, boolean keepColors) {
        EdtExecutor.requireEdt();
        styleList.show(recipe.style());
        settingsPanel.show(recipe, keepColors);
        menuBar.show(recipe);
    }

    @Override
    public void showStyleThumbnail(Style style, BufferedImage thumbnail) {
        EdtExecutor.requireEdt();
        styleList.setThumbnail(style, thumbnail);
    }

    @Override
    public void showPicture(BufferedImage image) {
        EdtExecutor.requireEdt();
        picture.setPicture(image);
    }

    @Override
    public void showVariations(List<BufferedImage> pictures) {
        EdtExecutor.requireEdt();
        picture.setVariations(pictures);
    }

    @Override
    public void showHistory(List<BufferedImage> thumbnails, int current) {
        EdtExecutor.requireEdt();
        historyStrip.show(thumbnails, current);
    }

    @Override
    public void showStatus(String text) {
        EdtExecutor.requireEdt();
        status.setText(text == null || text.isEmpty() ? " " : text);
    }

    @Override
    public void showBusy(boolean busy) {
        EdtExecutor.requireEdt();
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
    }

    @Override
    public void updateCommands(Commands commands) {
        EdtExecutor.requireEdt();
        if (actions != null) actions.update(commands);
    }

    @Override
    public Dimension canvasSize() {
        EdtExecutor.requireEdt();
        if (picture.getWidth() > 0 && picture.getHeight() > 0) return picture.getSize();
        return picture.getPreferredSize();
    }

    @Override
    public Dimension screenSize() {
        EdtExecutor.requireEdt();
        Rectangle bounds = getGraphicsConfiguration().getBounds();
        java.awt.geom.AffineTransform scale = getGraphicsConfiguration().getDefaultTransform();
        return new Dimension((int) Math.round(bounds.width * scale.getScaleX()),
                (int) Math.round(bounds.height * scale.getScaleY()));
    }

    @Override
    public Optional<Path> chooseOpenPath() {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory : picturesDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                msg.format("filter.open", "*." + PictureFiles.RECIPE_EXTENSION + ", *.png"),
                PictureFiles.RECIPE_EXTENSION, "png");
        chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filter);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        lastDirectory = chooser.getCurrentDirectory();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<Path> chooseRecipeSavePath(String suggestedName) {
        return chooseSavePath(suggestedName, PictureFiles.RECIPE_EXTENSION,
                msg.format("filter.recipe", "*." + PictureFiles.RECIPE_EXTENSION));
    }

    @Override
    public Optional<Path> choosePictureSavePath(String suggestedName) {
        return chooseSavePath(suggestedName, "png", msg.format("filter.png", "*.png"));
    }

    private Optional<Path> chooseSavePath(String suggestedName, String extension, String description) {
        EdtExecutor.requireEdt();
        JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory : picturesDirectory());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(description, extension);
        chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filter);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        while (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            if (!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
                path = path.resolveSibling(path.getFileName() + "." + extension);
            }
            lastDirectory = chooser.getCurrentDirectory();
            if (!Files.exists(path) || confirm(msg.format("dialog.overwrite.message", path.getFileName()),
                    msg.get("dialog.overwrite.title"))) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    /** The pictures folder from the XDG user directories if it exists, else the home folder. */
    private static File picturesDirectory() {
        XdgDirectories xdg = XdgDirectories.system();
        Path pictures = xdg.pictures();
        return (Files.isDirectory(pictures) ? pictures : xdg.home()).toFile();
    }

    /** A size offered for export. */
    record ExportPreset(String label, int width, int height) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** The sizes offered for export: the screen, the usual screen sizes and paper at 300 dpi in both directions. */
    List<ExportPreset> exportPresets(Aspect aspect) {
        Dimension screen = screenSize();
        List<ExportPreset> presets = new ArrayList<>();
        presets.add(new ExportPreset(msg.format("export.preset.screen", screen.width, screen.height), screen.width,
                screen.height));
        presets.add(new ExportPreset("1920 × 1080 (Full HD)", 1920, 1080));
        presets.add(new ExportPreset("2560 × 1440 (QHD)", 2560, 1440));
        presets.add(new ExportPreset("3840 × 2160 (4K UHD)", 3840, 2160));
        boolean portrait = aspect == Aspect.PORTRAIT;
        int[][] paper = {{2480, 3508}, {3508, 4961}, {4961, 7016}};
        String[] names = {"A4", "A3", "A2"};
        for (int i = 0; i < paper.length; i++) {
            int w = portrait ? paper[i][0] : paper[i][1];
            int h = portrait ? paper[i][1] : paper[i][0];
            presets.add(new ExportPreset(msg.format(portrait ? "export.preset.portrait" : "export.preset.landscape",
                    names[i], w, h), w, h));
        }
        presets.add(new ExportPreset(msg.format("export.preset.square", 4096, 4096), 4096, 4096));
        return presets;
    }

    @Override
    public Optional<Dimension> chooseExportSize(Dimension suggested, Aspect aspect) {
        EdtExecutor.requireEdt();
        JComboBox<ExportPreset> presets = new JComboBox<>(exportPresets(aspect).toArray(ExportPreset[]::new));
        JSpinner width = new JSpinner(new SpinnerNumberModel(suggested.width, 16,
                ArtStudioPreferences.MAX_EXPORT_SIDE, 16));
        JSpinner height = new JSpinner(new SpinnerNumberModel(suggested.height, 16,
                ArtStudioPreferences.MAX_EXPORT_SIDE, 16));
        presets.setSelectedIndex(-1);
        presets.addActionListener(e -> {
            if (presets.getSelectedItem() instanceof ExportPreset p) {
                width.setValue(p.width());
                height.setValue(p.height());
            }
        });
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 6);
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel(msg.get("export.preset")), c);
        c.gridx = 1;
        panel.add(presets, c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel(msg.get("export.width")), c);
        c.gridx = 1;
        panel.add(width, c);
        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel(msg.get("export.height")), c);
        c.gridx = 1;
        panel.add(height, c);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        JLabel note = new JLabel("<html><div style='width:330px'>" + msg.get("export.note") + "</div></html>");
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(note, c);
        while (true) {
            int answer = JOptionPane.showConfirmDialog(this, panel, msg.get("dialog.export.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return Optional.empty();
            int w = (Integer) width.getValue();
            int h = (Integer) height.getValue();
            if ((long) w * h <= eu.amadeos.apps.artstudio.service.art.ArtRenderer.MAX_PIXELS) {
                return Optional.of(new Dimension(w, h));
            }
            showError(msg.format("error.export-size", eu.amadeos.apps.artstudio.service.art.ArtRenderer.MAX_PIXELS
                    / 1_000_000));
        }
    }

    @Override
    public Optional<Color> chooseColor(String title, Color initial) {
        EdtExecutor.requireEdt();
        return Optional.ofNullable(JColorChooser.showDialog(this, title, initial));
    }

    @Override
    public void copyToClipboard(BufferedImage image) {
        EdtExecutor.requireEdt();
        ImageSelection.copy(image);
    }

    @Override
    public void copyToClipboard(String text) {
        EdtExecutor.requireEdt();
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    @Override
    public Optional<String> clipboardText() {
        EdtExecutor.requireEdt();
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String s ? Optional.of(s) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void showBar(Bar bar, boolean visible) {
        EdtExecutor.requireEdt();
        if (fullScreen) return;
        switch (bar) {
            case TOOLBAR -> toolBar.setVisible(visible);
            case STYLES -> stylePane.setVisible(visible);
            case SETTINGS -> settingsPane.setVisible(visible);
            case HISTORY -> historyStrip.setVisible(visible);
            case STATUS_BAR -> statusBar.setVisible(visible);
        }
        revalidate();
    }

    @Override
    public void setFullScreen(boolean on) {
        EdtExecutor.requireEdt();
        if (on == fullScreen) return;
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        if (on) {
            normalBounds = getBounds();
            normalState = getExtendedState();
            fullScreen = true;
            setJMenuBar(null);
            for (JComponent c : new JComponent[]{toolBar, stylePane, settingsPane, historyStrip, statusBar}) {
                c.setVisible(false);
            }
            centre.setBorder(null);
            picture.setBare(true);
            super.dispose();
            setUndecorated(true);
            if (device.isFullScreenSupported()) {
                device.setFullScreenWindow(this);
            } else {
                setExtendedState(NORMAL);
                setBounds(getGraphicsConfiguration().getBounds());
            }
            setVisible(true);
        } else {
            if (device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
            super.dispose();
            fullScreen = false;
            setUndecorated(false);
            setJMenuBar(menuBar);
            centre.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            picture.setBare(false);
            for (Bar bar : Bar.values()) showBar(bar, presenter.isBarVisible(bar));
            if (normalBounds != null) setBounds(normalBounds);
            setExtendedState(normalState);
            setVisible(true);
        }
        revalidate();
        picture.requestFocusInWindow();
    }

    boolean isFullScreen() {
        return fullScreen;
    }

    @Override
    public boolean confirm(String message, String title) {
        EdtExecutor.requireEdt();
        return JOptionPane.showConfirmDialog(this, plain(message), title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override
    public void showError(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this, plain(message), getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        EdtExecutor.requireEdt();
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-artstudio " + AmadeArtStudioApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void dispose() {
        if (fullScreen) {
            GraphicsDevice device = getGraphicsConfiguration().getDevice();
            if (device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
        }
        resizeTimer.stop();
        super.dispose();
    }

    /** A text component, so that messages (with file names and errors) are not read as Swing HTML. */
    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(message.length() > 200);
        text.setWrapStyleWord(true);
        if (message.length() > 200) text.setColumns(50);
        return text;
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        status.setName("status");
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.add(status);
        progress.setName("progress");
        progress.setPreferredSize(new Dimension(120, 14));
        progress.setVisible(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        right.add(progress);
        // Keeps the height while the progress bar is hidden, so that the picture does not change size.
        right.add(Box.createVerticalStrut(progress.getPreferredSize().height));
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }
}
