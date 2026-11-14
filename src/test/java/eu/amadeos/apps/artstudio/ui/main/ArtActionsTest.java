package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.QueueExecutor;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.Backdrops;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;
import eu.amadeos.apps.artstudio.service.render.RenderService;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtActionsTest {
    @TempDir
    Path dir;

    private final QueueExecutor executor = new QueueExecutor();
    private final FakeView view = new FakeView();

    private MainPresenter presenter() {
        return new MainPresenter(view, MainPresenterTest.MSG, null,
                new ArtStudioPreferences(dir.resolve("p.properties")),
                new RenderService(new ArtRenderer(), executor, executor), new PictureFiles(),
                new Backdrops(dir.resolve("c"), dir.resolve("b"), () -> 0), executor, executor,
                () -> DesktopTheme.WARM_MOTIF, () -> 7);
    }

    @Test
    void everyKeyDoesOneThing() {
        ArtActions actions = new ArtActions(presenter(), MainPresenterTest.MSG);
        Map<KeyStroke, Object> keys = new HashMap<>();
        for (Action a : actions.all()) {
            assertNotNull(a.getValue(Action.SHORT_DESCRIPTION));
            KeyStroke key = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
            if (key != null) assertNull(keys.put(key, a.getValue(Action.ACTION_COMMAND_KEY)), key.toString());
        }
        for (ArtActions.KeyBinding b : actions.extraKeys()) {
            assertNull(keys.put(b.key(), b.action().getValue(Action.ACTION_COMMAND_KEY)), b.key().toString());
        }
        assertTrue(keys.size() >= 18);
    }

    @Test
    void disabledActionsDoNothing() {
        MainPresenter presenter = presenter();
        presenter.start(List.of());
        executor.runAll();
        ArtActions actions = new ArtActions(presenter, MainPresenterTest.MSG);
        actions.update(new MainView.Commands(false, false, false, false, false, false, false));
        assertFalse(actions.copyPicture.isEnabled());
        actions.copyPicture.actionPerformed(null);
        assertNull(view.copiedPicture);
        actions.update(new MainView.Commands(false, true, false, false, true, false, false));
        assertEquals(Boolean.TRUE, actions.variations.getValue(Action.SELECTED_KEY));
        actions.copyPicture.actionPerformed(null);
        assertNotNull(view.copiedPicture);
        actions.copyRecipe.actionPerformed(null);
        assertEquals(Recipe.parse(view.copiedText), presenter.recipe());
    }
}
