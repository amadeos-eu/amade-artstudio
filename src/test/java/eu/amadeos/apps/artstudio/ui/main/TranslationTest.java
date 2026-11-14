package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.bootstrap.ArtStudioModule;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(ArtStudioModule.class, ArtStudioModule.MESSAGES));
    }

    @Test
    void everyNameHasAText() {
        List<String> keys = new ArrayList<>();
        for (Style s : Style.values()) {
            keys.add(s.key());
        }
        for (Style.Group g : Style.Group.values()) keys.add(g.key());
        for (Palette p : Palette.BUILT_IN) keys.add(p.key());
        keys.add("palette." + Palette.CUSTOM);
        keys.add("palette." + Palette.DESKTOP);
        for (Aspect a : Aspect.values()) keys.add(a.key());
        List<String> missing = new ArrayList<>();
        for (Language language : Language.values()) {
            Messages messages = Messages.load(ArtStudioModule.class, ArtStudioModule.MESSAGES, language.locale());
            for (String key : keys) {
                if (messages.get(key).startsWith("!")) missing.add(language.code() + " " + key);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void textsAreFormattedInTheLanguage() {
        Messages german = Messages.load(ArtStudioModule.class, ArtStudioModule.MESSAGES, Locale.GERMANY);
        assertEquals("1,50 s", german.format("time.seconds", 1.5));
        String drawn = german.format("status.drawn", "Fluss", 123456789L, 1920, 1200, "0,30 s");
        assertEquals(true, drawn.contains("123456789") && drawn.contains("1920 × 1200"), drawn);
        Messages french = Messages.load(ArtStudioModule.class, ArtStudioModule.MESSAGES, Locale.FRANCE);
        assertEquals(false, french.format("error.open", "a.artrecipe", "b").contains("{"));
    }

    /** Within the menu bar and within each menu, no two entries share a mnemonic letter. */
    @Test
    void mnemonicsAreUniquePerMenu() {
        Map<String, List<String>> menus = Map.of(
                "bar", List.of("menu.file", "menu.edit", "menu.picture", "menu.view", "menu.options", "menu.help"),
                "file", List.of("menu.file.open", "menu.file.save-recipe", "menu.file.save-picture",
                        "menu.file.export", "menu.file.backdrop", "menu.file.exit"),
                "edit", List.of("menu.edit.back", "menu.edit.forward", "menu.edit.copy-picture",
                        "menu.edit.copy-recipe", "menu.edit.paste-recipe"),
                "picture", List.of("menu.picture.new", "menu.picture.random-seed", "menu.picture.variations",
                        "menu.picture.style", "menu.picture.palette", "menu.picture.aspect"),
                "view", List.of("menu.view.full-screen", "menu.view.stop"));
        Map<String, String> clashes = new HashMap<>();
        for (Language language : Language.values()) {
            Messages messages = Messages.load(ArtStudioModule.class, ArtStudioModule.MESSAGES, language.locale());
            for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
                Map<Character, String> seen = new HashMap<>();
                for (String key : menu.getValue()) {
                    Mnemonics.Text t = Mnemonics.parse(messages.get(key));
                    if (t.index() < 0) {
                        clashes.put(language.code() + " " + key, "no mnemonic");
                        continue;
                    }
                    char letter = Character.toLowerCase(t.text().charAt(t.index()));
                    String other = seen.put(letter, key);
                    if (other != null) clashes.put(language.code() + " " + key, other);
                }
            }
        }
        assertEquals(Map.of(), clashes);
    }
}
