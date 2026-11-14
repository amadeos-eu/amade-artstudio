package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.ToolButtons;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.border.BevelBorder;

/** New picture; back, forward; variations; open, save, export, backdrop; full screen. */
public final class MainToolBar extends JToolBar {
    public MainToolBar(ArtActions actions) {
        EdtExecutor.requireEdt();
        setFloatable(false);
        setRollover(false);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED),
                BorderFactory.createEmptyBorder(2, 3, 2, 3)));
        JButton newPicture = ToolButtons.button(actions.newPicture);
        newPicture.setName("tool.new-picture");
        add(newPicture);
        addSeparator();
        add(ToolButtons.button(actions.back));
        add(ToolButtons.button(actions.forward));
        addSeparator();
        add(ToolButtons.toggle(actions.variations));
        addSeparator();
        add(ToolButtons.button(actions.open));
        add(ToolButtons.button(actions.savePicture));
        add(ToolButtons.button(actions.exportPicture));
        add(ToolButtons.button(actions.setBackdrop));
        addSeparator();
        add(ToolButtons.toggle(actions.fullScreen));
    }
}
