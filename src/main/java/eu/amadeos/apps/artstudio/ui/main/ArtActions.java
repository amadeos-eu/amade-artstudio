package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolIcon;
import eu.amadeos.commons.swing.ToolIcon.Glyph;
import eu.amadeos.apps.artstudio.ui.icons.ArtGlyph;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * One action per command, shared by the menus and the toolbar, so that both are enabled and disabled
 * together. Texts come from the messages ({@code &} marks the mnemonic), icons from {@link ToolIcon}.
 */
public final class ArtActions {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;

    public final Action newPicture;
    public final Action open;
    public final Action saveRecipe;
    public final Action savePicture;
    public final Action exportPicture;
    public final Action setBackdrop;
    public final Action exit;
    public final Action back;
    public final Action forward;
    public final Action copyPicture;
    public final Action copyRecipe;
    public final Action pasteRecipe;
    public final Action randomSeed;
    public final Action variations;
    public final Action desktopColors;
    public final Action fullScreen;
    public final Action stop;
    /** Escape, without a menu entry: leaves full screen or the variations, else stops the export. */
    public final Action escape;

    private final Messages msg;

    public ArtActions(MainPresenter presenter, Messages msg) {
        this.msg = msg;
        newPicture = action("menu.picture.new", ArtGlyph.NEW_PICTURE, KeyEvent.VK_N, CTRL, presenter::newPicture);
        open = action("menu.file.open", StandardGlyph.OPEN, KeyEvent.VK_O, CTRL, presenter::openFile);
        saveRecipe = action("menu.file.save-recipe", StandardGlyph.SAVE, KeyEvent.VK_S, CTRL | SHIFT,
                presenter::saveRecipe);
        savePicture = action("menu.file.save-picture", ArtGlyph.PICTURE, KeyEvent.VK_S, CTRL, presenter::savePicture);
        exportPicture = action("menu.file.export", StandardGlyph.EXPORT, KeyEvent.VK_E, CTRL,
                presenter::exportPicture);
        setBackdrop = action("menu.file.backdrop", ArtGlyph.BACKDROP, KeyEvent.VK_B, CTRL, presenter::setAsBackdrop);
        exit = action("menu.file.exit", null, KeyEvent.VK_Q, CTRL, presenter::exit);
        back = action("menu.edit.back", ArtGlyph.BACK, KeyEvent.VK_LEFT, ALT, presenter::goBack);
        forward = action("menu.edit.forward", ArtGlyph.FORWARD, KeyEvent.VK_RIGHT, ALT, presenter::goForward);
        copyPicture = action("menu.edit.copy-picture", StandardGlyph.COPY, KeyEvent.VK_C, CTRL,
                presenter::copyPicture);
        copyRecipe = action("menu.edit.copy-recipe", null, KeyEvent.VK_C, CTRL | SHIFT, presenter::copyRecipe);
        pasteRecipe = action("menu.edit.paste-recipe", null, KeyEvent.VK_V, CTRL, presenter::pasteRecipe);
        randomSeed = action("menu.picture.random-seed", null, KeyEvent.VK_R, CTRL, presenter::randomSeed);
        variations = action("menu.picture.variations", ArtGlyph.VARIATIONS, KeyEvent.VK_G, CTRL,
                presenter::toggleVariations);
        desktopColors = action("menu.picture.desktop-colors", null, 0, 0, presenter::useDesktopColors);
        fullScreen = action("menu.view.full-screen", StandardGlyph.FULL_SCREEN, KeyEvent.VK_F11, 0,
                presenter::toggleFullScreen);
        stop = action("menu.view.stop", null, 0, 0, presenter::stop);
        escape = new AbstractAction("escape") {
            @Override
            public void actionPerformed(ActionEvent e) {
                presenter.escape();
            }
        };
        escape.putValue(Action.ACTION_COMMAND_KEY, "escape");
        update(MainView.Commands.NONE);
    }

    /** All actions with a menu entry, for tests and key bindings. */
    public List<Action> all() {
        return List.of(newPicture, open, saveRecipe, savePicture, exportPicture, setBackdrop, exit, back, forward,
                copyPicture, copyRecipe, pasteRecipe, randomSeed, variations, desktopColors, fullScreen, stop);
    }

    /** Further keys besides the accelerators: Escape, and Ctrl+Z and Ctrl+Y for back and forward. */
    public List<KeyBinding> extraKeys() {
        return List.of(new KeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), escape),
                new KeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL), back),
                new KeyBinding(KeyStroke.getKeyStroke(KeyEvent.VK_Y, CTRL), forward));
    }

    public record KeyBinding(KeyStroke key, Action action) {
    }

    public void update(MainView.Commands c) {
        savePicture.setEnabled(c.hasPicture());
        copyPicture.setEnabled(c.hasPicture());
        exportPicture.setEnabled(!c.exporting());
        back.setEnabled(c.canGoBack());
        forward.setEnabled(c.canGoForward());
        variations.putValue(Action.SELECTED_KEY, c.variations());
        fullScreen.putValue(Action.SELECTED_KEY, c.fullScreen());
        stop.setEnabled(c.exporting());
    }

    private Action action(String key, Glyph glyph, int keyCode, int modifiers, Runnable command) {
        Mnemonics.Text text = Mnemonics.parse(msg.get(key));
        Action action = new AbstractAction(text.text()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) command.run();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, key);
        if (text.index() >= 0) {
            action.putValue(Action.MNEMONIC_KEY, text.keyCode());
            action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        }
        String tip = msg.has(key + ".tip") ? msg.get(key + ".tip") : text.text();
        action.putValue(Action.SHORT_DESCRIPTION, tip);
        if (keyCode != 0) action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(keyCode, modifiers));
        if (glyph != null) {
            action.putValue(Action.SMALL_ICON, new ToolIcon(glyph, 16));
            action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        }
        return action;
    }
}
