package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** File, Edit, Picture, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private final Messages msg;
    private final Map<Style, JRadioButtonMenuItem> styles = new EnumMap<>(Style.class);
    private final Map<Aspect, JRadioButtonMenuItem> aspects = new EnumMap<>(Aspect.class);
    private final Map<String, JRadioButtonMenuItem> palettes = new java.util.HashMap<>();
    private final ButtonGroup paletteGroup = new ButtonGroup();

    public MainMenuBar(MainPresenter presenter, ArtActions actions, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item(actions.open));
        file.add(item(actions.saveRecipe));
        file.addSeparator();
        file.add(item(actions.savePicture));
        file.add(item(actions.exportPicture));
        file.add(item(actions.setBackdrop));
        file.addSeparator();
        file.add(item(actions.exit));

        JMenu edit = menu("menu.edit");
        edit.add(item(actions.back));
        edit.add(item(actions.forward));
        edit.addSeparator();
        edit.add(item(actions.copyPicture));
        edit.add(item(actions.copyRecipe));
        edit.add(item(actions.pasteRecipe));

        JMenu picture = menu("menu.picture");
        picture.add(item(actions.newPicture));
        picture.add(item(actions.randomSeed));
        JCheckBoxMenuItem variations = new JCheckBoxMenuItem(actions.variations);
        variations.setName("menu.picture.variations");
        variations.setIcon(null);
        picture.add(variations);
        picture.addSeparator();
        JMenu styleMenu = Mnemonics.apply(new JMenu(), msg.get("menu.picture.style"));
        ButtonGroup styleGroup = new ButtonGroup();
        int n = 1;
        for (Style s : Style.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(msg.get(s.key()));
            item.setName(s.key());
            if (n <= 9) item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, InputEvent.ALT_DOWN_MASK));
            n++;
            item.addActionListener(e -> presenter.selectStyle(s));
            styleGroup.add(item);
            styleMenu.add(item);
            styles.put(s, item);
        }
        picture.add(styleMenu);
        JMenu paletteMenu = Mnemonics.apply(new JMenu(), msg.get("menu.picture.palette"));
        for (Palette p : Palette.BUILT_IN) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(msg.get(p.key()));
            item.setName(p.key());
            item.addActionListener(e -> presenter.setPalette(p));
            paletteGroup.add(item);
            paletteMenu.add(item);
            palettes.put(p.id(), item);
        }
        paletteMenu.addSeparator();
        JRadioButtonMenuItem desktop = new JRadioButtonMenuItem(actions.desktopColors);
        desktop.setName("menu.picture.desktop-colors");
        paletteGroup.add(desktop);
        paletteMenu.add(desktop);
        palettes.put(Palette.DESKTOP, desktop);
        picture.add(paletteMenu);
        JMenu aspectMenu = Mnemonics.apply(new JMenu(), msg.get("menu.picture.aspect"));
        ButtonGroup aspectGroup = new ButtonGroup();
        for (Aspect a : Aspect.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(msg.get(a.key()));
            item.setName(a.key());
            item.addActionListener(e -> presenter.setAspect(a));
            aspectGroup.add(item);
            aspectMenu.add(item);
            aspects.put(a, item);
        }
        picture.add(aspectMenu);

        JMenu view = menu("menu.view");
        for (Bar bar : Bar.values()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(msg.get(barKey(bar)), presenter.isBarVisible(bar));
            item.setName(barKey(bar));
            item.addActionListener(e -> presenter.setBarVisible(bar, item.isSelected()));
            view.add(item);
        }
        view.addSeparator();
        JCheckBoxMenuItem fullScreen = new JCheckBoxMenuItem(actions.fullScreen);
        fullScreen.setName("menu.view.full-screen");
        fullScreen.setIcon(null);
        view.add(fullScreen);
        view.add(item(actions.stop));

        JMenu options = menu("menu.options");
        if (locales != null) options.add(languageMenu(presenter, locales));

        JMenu help = menu("menu.help");
        JMenuItem about = new JMenuItem(msg.get("menu.help.about"));
        about.setName("menu.help.about");
        about.addActionListener(e -> presenter.about());
        help.add(about);
    }

    /** Ticks the style, palette and shape of the recipe. */
    public void show(Recipe recipe) {
        EdtExecutor.requireEdt();
        styles.get(recipe.style()).setSelected(true);
        aspects.get(recipe.aspect()).setSelected(true);
        JRadioButtonMenuItem p = Palette.BUILT_IN.contains(recipe.palette())
                || recipe.palette().id().equals(Palette.DESKTOP) ? palettes.get(recipe.palette().id()) : null;
        if (p != null) p.setSelected(true);
        else paletteGroup.clearSelection();
    }

    static String barKey(Bar bar) {
        return "menu.view." + bar.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = new JMenu(msg.get("menu.options.language"));
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private static JMenuItem item(Action action) {
        JMenuItem item = new JMenuItem(action);
        item.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        item.setIcon(null);
        return item;
    }
}
